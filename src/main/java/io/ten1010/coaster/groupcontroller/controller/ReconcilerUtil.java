package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.core.LabelConstants;
import io.ten1010.coaster.groupcontroller.core.TaintConstants;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

import java.util.*;
import java.util.stream.Collectors;

public class ReconcilerUtil {

    public static String getName(KubernetesObject object) {
        Objects.requireNonNull(object.getMetadata());
        Objects.requireNonNull(object.getMetadata().getName());
        return object.getMetadata().getName();
    }

    public static String getNamespace(KubernetesObject object) {
        Objects.requireNonNull(object.getMetadata());
        Objects.requireNonNull(object.getMetadata().getNamespace());
        return object.getMetadata().getNamespace();
    }

    public static List<V1Toleration> getTolerations(V1Pod pod) {
        Objects.requireNonNull(pod.getSpec());
        List<V1Toleration> tolerations = pod.getSpec().getTolerations();
        return tolerations == null ? new ArrayList<>() : tolerations;
    }

    public static V1Affinity getAffinity(V1Pod pod) {
        Objects.requireNonNull(pod.getSpec());
        V1Affinity affinity = pod.getSpec().getAffinity();
        return affinity == null ? new V1AffinityBuilder().build() : affinity;
    }

    public static boolean isResourceGroupExclusiveToleration(V1Toleration toleration) {
        if (toleration.getKey() == null) {
            return false;
        }

        return toleration.getKey().equals(TaintConstants.KEY_RESOURCE_GROUP_EXCLUSIVE);
    }

    public static boolean isResourceGroupExclusiveNodeSelectorRequirement(V1NodeSelectorRequirement requirement) {
        if (requirement == null || requirement.getKey() == null) {
            return false;
        }

        return requirement.getKey().equals(LabelConstants.KEY_RESOURCE_GROUP_EXCLUSIVE);
    }

    public static List<V1Toleration> buildResourceGroupExclusiveTolerations(V1ResourceGroup group) {
        V1TolerationBuilder baseBuilder = new V1TolerationBuilder()
                .withKey(TaintConstants.KEY_RESOURCE_GROUP_EXCLUSIVE)
                .withValue(getName(group))
                .withOperator("Equal");
        V1Toleration noSchedule = baseBuilder
                .withEffect("NoSchedule")
                .build();
        V1Toleration noExecute = baseBuilder
                .withEffect("NoExecute")
                .build();

        return List.of(noSchedule, noExecute);
    }

    public static List<V1Toleration> buildResourceGroupExclusiveTolerations(Collection<V1ResourceGroup> groups) {
        return groups.stream()
                .flatMap(e -> buildResourceGroupExclusiveTolerations(e).stream())
                .collect(Collectors.toList());
    }

    public static List<V1NodeSelectorRequirement> buildResourceGroupExclusiveNodeSelectorRequirements(Collection<V1ResourceGroup> groups) {
        V1NodeSelectorRequirementBuilder builder = new V1NodeSelectorRequirementBuilder()
                .withKey(LabelConstants.KEY_RESOURCE_GROUP_EXCLUSIVE)
                .withOperator("In")
                .withValues(groups.stream()
                        .map(ReconcilerUtil::getName)
                        .distinct().collect(Collectors.toList()));

        return List.of(builder.build());
    }

    public static V1NodeSelectorTerm buildResourceGroupExclusiveRequiredSchedulingTerm(Collection<V1ResourceGroup> groups) {
        return new V1NodeSelectorTermBuilder()
                .withMatchExpressions(buildResourceGroupExclusiveNodeSelectorRequirements(groups))
                .build();
    }

    public static List<V1Toleration> reconcileTolerations(Collection<V1Toleration> tolerations, Collection<V1ResourceGroup> groups) {
        List<V1Toleration> built = tolerations.stream()
                .filter(e -> !isResourceGroupExclusiveToleration(e))
                .collect(Collectors.toList());
        built.addAll(buildResourceGroupExclusiveTolerations(groups));

        return built;
    }

    public static V1Affinity reconcileAffinity(V1Affinity affinity, Collection<V1ResourceGroup> groups) {
        if(groups.isEmpty()) {
            return affinity;
        }
        V1Affinity built = cloneAndInitializeIfNull(affinity);
        built.getNodeAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .setNodeSelectorTerms(extractResourceGroupNonExclusiveNodeSelectorTerms(built));
        built.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution().getNodeSelectorTerms()
                .add(buildResourceGroupExclusiveRequiredSchedulingTerm(groups));

        return built;
    }

    private static V1Affinity cloneAndInitializeIfNull(V1Affinity affinity) {
        V1Affinity built = new V1AffinityBuilder(affinity).build();
        if(built.getNodeAffinity() == null) {
            built.setNodeAffinity(new V1NodeAffinityBuilder().build());
        }
        if(built.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution() == null) {
            built.getNodeAffinity().setRequiredDuringSchedulingIgnoredDuringExecution(new V1NodeSelectorBuilder().build());
        }
        if(built.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution().getNodeSelectorTerms() == null) {
            built.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution().setNodeSelectorTerms(new ArrayList<>());
        }

        return built;
    }

    private static List<V1NodeSelectorTerm> extractResourceGroupNonExclusiveNodeSelectorTerms(V1Affinity affinity) {
        return affinity.getNodeAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .getNodeSelectorTerms()
                .stream()
                .filter(term -> term.getMatchExpressions() != null)
                .filter(term -> term.getMatchExpressions()
                        .stream()
                        .noneMatch(ReconcilerUtil::isResourceGroupExclusiveNodeSelectorRequirement))
                .collect(Collectors.toList());
    }

}
