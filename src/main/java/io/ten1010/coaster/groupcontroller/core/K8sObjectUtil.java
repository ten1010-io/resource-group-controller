package io.ten1010.coaster.groupcontroller.core;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1OwnerReference;

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

    public static boolean isControlled(KubernetesObject object) {
        if (object.getMetadata() == null || object.getMetadata().getOwnerReferences() == null) {
            return false;
        }
        return object.getMetadata().getOwnerReferences().stream()
                .anyMatch(e -> Boolean.TRUE.equals(e.getController()));
    }

    public static V1OwnerReference getControllerReference(KubernetesObject object) {
        Objects.requireNonNull(object.getMetadata());
        Objects.requireNonNull(object.getMetadata().getOwnerReferences());
        return object.getMetadata().getOwnerReferences().stream()
                .filter(e -> Boolean.TRUE.equals(e.getController()))
                .findAny()
                .orElseThrow();
    }

    public static ApiResourceKind getApiResourceKind(V1OwnerReference ref) {
        Objects.requireNonNull(ref.getApiVersion());
        Objects.requireNonNull(ref.getKind());
        return new ApiResourceKind(parseGroup(ref.getApiVersion()), ref.getKind());
    }

    private static String parseGroup(String apiVersion) {
        String[] tokens = apiVersion.split("/");
        if (tokens.length == 1) {
            return "";
        }
        if (tokens.length == 2) {
            return tokens[0];
        }
        throw new IllegalArgumentException(String.format("Invalid apiVersion [%s]", apiVersion));
    }

    private K8sObjectUtil() {
        throw new UnsupportedOperationException();
    }

}
