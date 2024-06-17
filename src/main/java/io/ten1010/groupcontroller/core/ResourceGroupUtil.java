package io.ten1010.groupcontroller.core;

import io.ten1010.groupcontroller.model.V1Beta1K8sObjectReference;
import io.ten1010.groupcontroller.model.V1Beta1ResourceGroup;

import java.util.ArrayList;
import java.util.List;

public final class ResourceGroupUtil {

    public static List<String> getNodes(V1Beta1ResourceGroup obj) {
        if (obj.getSpec() == null) {
            return new ArrayList<>();
        }
        return obj.getSpec().getNodes();
    }

    public static List<String> getNamespaces(V1Beta1ResourceGroup obj) {
        if (obj.getSpec() == null) {
            return new ArrayList<>();
        }
        return obj.getSpec().getNamespaces();
    }

    public static List<V1Beta1K8sObjectReference> getDaemonSets(V1Beta1ResourceGroup obj) {
        if (obj.getSpec() == null || obj.getSpec().getDaemonSet() == null) {
            return new ArrayList<>();
        }
        return obj.getSpec().getDaemonSet().getDaemonSets();
    }

    private ResourceGroupUtil() {
        throw new UnsupportedOperationException();
    }

}
