package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.core.*;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class Reconciliation {

    public static List<V1Toleration> reconcileTolerations(V1Pod pod, List<V1ResourceGroup> groups) {
        List<V1Toleration> tolerations = removeResourceGroupExclusiveTolerations(PodUtil.getTolerations(pod));
        tolerations.addAll(buildResourceGroupExclusiveTolerations(groups));
        return tolerations;
    }

    public static List<V1Toleration> reconcileTolerations(V1DaemonSet daemonSet, List<V1ResourceGroup> groups) {
        List<V1Toleration> tolerations = removeResourceGroupExclusiveTolerations(DaemonSetUtil.getTolerations(daemonSet));
        tolerations.addAll(buildResourceGroupExclusiveTolerations(groups));
        return tolerations;
    }

    public static Optional<V1Affinity> reconcileAffinity(V1Pod pod, List<V1ResourceGroup> groups) {
        if (PodUtil.isDaemonSetPod(pod)) {
            return PodUtil.getAffinity(pod);
        }
        Optional<V1Affinity> affinity = PodUtil.getAffinity(pod);
        List<V1NodeSelectorTerm> terms = new ArrayList<>();
        if (affinity.isEmpty()) {
            if (groups.isEmpty()) {
                return Optional.empty();
            }
            terms.addAll(buildResourceGroupExclusiveNodeSelectorTerms(groups));
            return Optional.of(new V1AffinityBuilder()
                    .withNewNodeAffinity()
                    .withNewRequiredDuringSchedulingIgnoredDuringExecution()
                    .withNodeSelectorTerms(terms)
                    .endRequiredDuringSchedulingIgnoredDuringExecution()
                    .endNodeAffinity()
                    .build());
        }
        V1Affinity clone = new V1AffinityBuilder(affinity.get()).build();
        terms.addAll(extractNonResourceGroupExclusiveNodeSelectorTerms(clone));
        terms.addAll(buildResourceGroupExclusiveNodeSelectorTerms(groups));
        if (clone.getNodeAffinity() == null) {
            V1NodeAffinity nodeAffinity = new V1NodeAffinityBuilder()
                    .withNewRequiredDuringSchedulingIgnoredDuringExecution()
                    .withNodeSelectorTerms(terms)
                    .endRequiredDuringSchedulingIgnoredDuringExecution()
                    .build();
            clone.setNodeAffinity(nodeAffinity);
            return Optional.of(clone);
        }
        if (clone.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution() == null) {
            V1NodeSelector selector = new V1NodeSelectorBuilder()
                    .withNodeSelectorTerms(terms)
                    .build();
            clone.getNodeAffinity().setRequiredDuringSchedulingIgnoredDuringExecution(selector);
            return Optional.of(clone);
        }
        clone.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution().setNodeSelectorTerms(terms);
        return Optional.of(clone);
    }

    private static List<V1Toleration> removeResourceGroupExclusiveTolerations(List<V1Toleration> tolerations) {
        return tolerations.stream()
                .filter(e -> !isResourceGroupExclusiveToleration(e))
                .collect(Collectors.toList());
    }

    private static List<V1Toleration> buildResourceGroupExclusiveTolerations(List<V1ResourceGroup> groups) {
        return groups.stream()
                .flatMap(e -> buildResourceGroupExclusiveTolerations(e).stream())
                .collect(Collectors.toList());
    }

    private static List<V1Toleration> buildResourceGroupExclusiveTolerations(V1ResourceGroup group) {
        V1TolerationBuilder baseBuilder = new V1TolerationBuilder()
                .withKey(TaintConstants.KEY_RESOURCE_GROUP_EXCLUSIVE)
                .withValue(K8sObjectUtil.getName(group))
                .withOperator("Equal");
        V1Toleration noSchedule = baseBuilder
                .withEffect("NoSchedule")
                .build();
        V1Toleration noExecute = baseBuilder
                .withEffect("NoExecute")
                .build();

        return List.of(noSchedule, noExecute);
    }

    private static boolean isResourceGroupExclusiveToleration(V1Toleration toleration) {
        if (toleration.getKey() == null) {
            return false;
        }
        return toleration.getKey().equals(TaintConstants.KEY_RESOURCE_GROUP_EXCLUSIVE);
    }

    private static List<V1NodeSelectorTerm> extractNonResourceGroupExclusiveNodeSelectorTerms(V1Affinity affinity) {
        if (affinity.getNodeAffinity() == null) {
            return List.of();
        }
        if (affinity.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution() == null) {
            return List.of();
        }
        return affinity.getNodeAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .getNodeSelectorTerms()
                .stream()
                .filter(term -> term.getMatchExpressions() != null)
                .filter(term -> term.getMatchExpressions()
                        .stream()
                        .noneMatch(Reconciliation::isResourceGroupExclusiveNodeSelectorRequirement))
                .collect(Collectors.toList());
    }

    private static List<V1NodeSelectorTerm> buildResourceGroupExclusiveNodeSelectorTerms(List<V1ResourceGroup> groups) {
        V1NodeSelectorRequirement requirement = new V1NodeSelectorRequirementBuilder()
                .withKey(LabelConstants.KEY_RESOURCE_GROUP_EXCLUSIVE)
                .withOperator("In")
                .withValues(groups.stream()
                        .map(K8sObjectUtil::getName)
                        .distinct()
                        .collect(Collectors.toList()))
                .build();
        V1NodeSelectorTerm term = new V1NodeSelectorTermBuilder()
                .addToMatchExpressions(requirement)
                .build();
        return List.of(term);
    }

    private static boolean isResourceGroupExclusiveNodeSelectorRequirement(V1NodeSelectorRequirement requirement) {
        if (requirement.getKey() == null) {
            return false;
        }
        return requirement.getKey().equals(LabelConstants.KEY_RESOURCE_GROUP_EXCLUSIVE);
    }

    private Reconciliation() {
        throw new UnsupportedOperationException();
    }

}
