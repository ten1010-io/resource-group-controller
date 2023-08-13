package io.ten1010.coaster.groupcontroller.core;

import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PodUtil {

    public static boolean isDaemonSetPod(V1Pod pod) {
        if (pod.getMetadata() == null || pod.getMetadata().getOwnerReferences() == null) {
            return false;
        }
        List<V1OwnerReference> references = pod.getMetadata().getOwnerReferences();
        Optional<V1OwnerReference> daemonSetRefOpt = references.stream()
                .filter(e -> {
                    if (!e.getKind().equals("DaemonSet")) {
                        return false;
                    }
                    if (!e.getApiVersion().equals("apps/v1")) {
                        return false;
                    }
                    return e.getController();
                })
                .findAny();
        return daemonSetRefOpt.isPresent();
    }

    public static V1OwnerReference getDaemonSetOwnerReference(V1Pod pod) {
        Objects.requireNonNull(pod.getMetadata());
        Objects.requireNonNull(pod.getMetadata().getOwnerReferences());
        return pod.getMetadata().getOwnerReferences().stream()
                .filter(e -> {
                    if (!e.getKind().equals("DaemonSet")) {
                        return false;
                    }
                    if (!e.getApiVersion().equals("apps/v1")) {
                        return false;
                    }
                    return e.getController();
                })
                .findAny()
                .orElseThrow();
    }

    private PodUtil() {
        throw new UnsupportedOperationException();
    }

}
