package io.ten1010.coaster.groupcontroller.core;

import io.ten1010.coaster.groupcontroller.model.K8sObjectReference;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

import java.util.ArrayList;
import java.util.List;

public final class ResourceGroupUtil {

    public static List<String> getNodes(V1ResourceGroup obj) {
        if (obj.getSpec() == null) {
            return new ArrayList<>();
        }
        return obj.getSpec().getNodes();
    }

    public static List<String> getNamespaces(V1ResourceGroup obj) {
        if (obj.getSpec() == null) {
            return new ArrayList<>();
        }
        return obj.getSpec().getNamespaces();
    }

    public static List<K8sObjectReference> getDaemonSets(V1ResourceGroup obj) {
        if (obj.getSpec() == null || obj.getSpec().getExceptions() == null) {
            return new ArrayList<>();
        }
        return obj.getSpec().getExceptions().getDaemonSets();
    }

    private ResourceGroupUtil() {
        throw new UnsupportedOperationException();
    }

}
