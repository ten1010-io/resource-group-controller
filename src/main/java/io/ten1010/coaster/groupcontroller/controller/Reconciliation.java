package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1TolerationBuilder;
import io.ten1010.coaster.groupcontroller.core.*;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public static Map<String, String> reconcileNodeSelector(V1Pod pod, List<V1ResourceGroup> groups) {
        if (PodUtil.isDaemonSetPod(pod)) {
            return new HashMap<>(PodUtil.getNodeSelector(pod));
        }
        if (groups.size() > 1) {
            throw new IllegalArgumentException();
        }
        if (groups.size() == 0) {
            return new HashMap<>(PodUtil.getNodeSelector(pod));
        }
        Map<String, String> selectors = new HashMap<>(PodUtil.getNodeSelector(pod));
        selectors.remove(LabelConstants.KEY_RESOURCE_GROUP_EXCLUSIVE);
        selectors.put(LabelConstants.KEY_RESOURCE_GROUP_EXCLUSIVE, K8sObjectUtil.getName(groups.get(0)));
        return selectors;
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

    private Reconciliation() {
        throw new UnsupportedOperationException();
    }

}
