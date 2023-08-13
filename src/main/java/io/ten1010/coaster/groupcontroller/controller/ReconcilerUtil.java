package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.extended.event.EventType;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1TolerationBuilder;
import io.ten1010.coaster.groupcontroller.core.EventConstants;
import io.ten1010.coaster.groupcontroller.core.K8sObjectUtil;
import io.ten1010.coaster.groupcontroller.core.LabelConstants;
import io.ten1010.coaster.groupcontroller.core.TaintConstants;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReconcilerUtil {

    private static final String MSG_NAMESPACE_BELONGS_TO_MULTIPLE_GROUPS = "Namespace [%s] belongs to multiple groups";

    public static boolean isResourceGroupExclusiveToleration(V1Toleration toleration) {
        if (toleration.getKey() == null) {
            return false;
        }

        return toleration.getKey().equals(TaintConstants.KEY_RESOURCE_GROUP_EXCLUSIVE);
    }

    public static List<V1Toleration> buildResourceGroupExclusiveTolerations(V1ResourceGroup group) {
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
        built.put(LabelConstants.KEY_RESOURCE_GROUP_EXCLUSIVE, K8sObjectUtil.getName(group));

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
