package io.ten1010.groupcontroller.core;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Toleration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DaemonSetUtil {

    public static List<V1Toleration> getTolerations(V1DaemonSet daemonSet) {
        if (daemonSet.getSpec() == null ||
                daemonSet.getSpec().getTemplate() == null ||
                daemonSet.getSpec().getTemplate().getSpec() == null ||
                daemonSet.getSpec().getTemplate().getSpec().getTolerations() == null) {
            return new ArrayList<>();
        }

        return daemonSet.getSpec().getTemplate().getSpec().getTolerations();
    }

    public static Optional<V1Affinity> getAffinity(V1DaemonSet daemonSet) {
        Objects.requireNonNull(daemonSet.getSpec());
        Objects.requireNonNull(daemonSet.getSpec().getTemplate());
        Objects.requireNonNull(daemonSet.getSpec().getTemplate().getSpec());
        V1Affinity affinity = daemonSet.getSpec().getTemplate().getSpec().getAffinity();
        if (affinity == null) {
            return Optional.empty();
        }
        return Optional.of(affinity);
    }

    private DaemonSetUtil() {
        throw new UnsupportedOperationException();
    }

}
