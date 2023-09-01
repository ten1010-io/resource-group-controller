package io.ten1010.coaster.groupcontroller.core;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Toleration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PodUtil {

    public static List<V1Toleration> getTolerations(V1Pod pod) {
        Objects.requireNonNull(pod.getSpec());
        List<V1Toleration> tolerations = pod.getSpec().getTolerations();
        return tolerations == null ? new ArrayList<>() : tolerations;
    }

    public static Optional<V1Affinity> getAffinity(V1Pod pod) {
        Objects.requireNonNull(pod.getSpec());
        V1Affinity affinity = pod.getSpec().getAffinity();
        if (affinity == null) {
            return Optional.empty();
        }
        return Optional.of(affinity);
    }

    private PodUtil() {
        throw new UnsupportedOperationException();
    }

}
