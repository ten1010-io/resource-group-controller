package io.ten1010.groupcontroller.core;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1ReplicationController;
import io.kubernetes.client.openapi.models.V1Toleration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ReplicationControllerUtil {

    public static List<V1Toleration> getTolerations(V1ReplicationController replicationController) {
        if (replicationController.getSpec() == null ||
                replicationController.getSpec().getTemplate() == null ||
                replicationController.getSpec().getTemplate().getSpec() == null ||
                replicationController.getSpec().getTemplate().getSpec().getTolerations() == null) {
            return new ArrayList<>();
        }

        return replicationController.getSpec().getTemplate().getSpec().getTolerations();
    }

    public static Optional<V1Affinity> getAffinity(V1ReplicationController replicationController) {
        Objects.requireNonNull(replicationController.getSpec());
        Objects.requireNonNull(replicationController.getSpec().getTemplate());
        Objects.requireNonNull(replicationController.getSpec().getTemplate().getSpec());
        V1Affinity affinity = replicationController.getSpec().getTemplate().getSpec().getAffinity();
        if (affinity == null) {
            return Optional.empty();
        }
        return Optional.of(affinity);
    }

    private ReplicationControllerUtil() {
        throw new UnsupportedOperationException();
    }

}
