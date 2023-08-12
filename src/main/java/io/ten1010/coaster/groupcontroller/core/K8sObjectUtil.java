package io.ten1010.coaster.groupcontroller.core;

import io.kubernetes.client.common.KubernetesObject;

import java.util.Objects;

public final class K8sObjectUtil {

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

    private K8sObjectUtil() {
        throw new UnsupportedOperationException();
    }

}
