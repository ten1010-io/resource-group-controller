package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.event.EventType;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1TolerationBuilder;
import io.ten1010.coaster.groupcontroller.core.EventConstants;
import io.ten1010.coaster.groupcontroller.core.LabelConstants;
import io.ten1010.coaster.groupcontroller.core.TaintConstants;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class ReconcilerUtil {

    private static final String MSG_NAMESPACE_BELONGS_TO_MULTIPLE_GROUPS = "Namespace [%s] belongs to multiple groups";

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

    public static Map<String, String> getNodeSelector(V1Pod pod) {
        Objects.requireNonNull(pod.getSpec());
        Map<String, String> selector = pod.getSpec().getNodeSelector();
        return selector == null ? new HashMap<>() : selector;
    }

    public static boolean isResourceGroupExclusiveToleration(V1Toleration toleration) {
        if (toleration.getKey() == null) {
            return false;
        }

        return toleration.getKey().equals(TaintConstants.KEY_RESOURCE_GROUP_EXCLUSIVE);
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

    public static List<V1Toleration> reconcileTolerations(Collection<V1Toleration> tolerations, Collection<V1ResourceGroup> groups) {
        List<V1Toleration> built = tolerations.stream()
                .filter(e -> !isResourceGroupExclusiveToleration(e))
                .collect(Collectors.toList());
        built.addAll(buildResourceGroupExclusiveTolerations(groups));

        return built;
    }

    public static Map<String, String> reconcileNodeSelector(Map<String, String> nodeSelectors, @Nullable V1ResourceGroup group) {
        Map<String, String> built = new HashMap<>(nodeSelectors);
        built.remove(LabelConstants.KEY_RESOURCE_GROUP_EXCLUSIVE);
        if (group == null) {
            return built;
        }
        built.put(LabelConstants.KEY_RESOURCE_GROUP_EXCLUSIVE, getName(group));

        return built;
    }

    public static void issueWarningEvents(GroupResolver.NamespaceConflictException e, EventRecorder eventRecorder) {
        for (V1ResourceGroup g : e.getGroups()) {
            eventRecorder.event(
                    g,
                    EventType.Warning,
                    EventConstants.REASON_NAMESPACE_CONFLICT, MSG_NAMESPACE_BELONGS_TO_MULTIPLE_GROUPS,
                    e.getNamespace());
        }
    }

}
