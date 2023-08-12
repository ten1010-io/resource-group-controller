package io.ten1010.coaster.groupcontroller.core;

import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Toleration;

import java.util.*;

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

    private PodUtil() {
        throw new UnsupportedOperationException();
    }

}
