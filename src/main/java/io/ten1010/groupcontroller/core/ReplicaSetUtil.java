package io.ten1010.groupcontroller.core;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1Toleration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ReplicaSetUtil {

    public static List<V1Toleration> getTolerations(V1ReplicaSet replicaSet) {
        if (replicaSet.getSpec() == null ||
                replicaSet.getSpec().getTemplate() == null ||
                replicaSet.getSpec().getTemplate().getSpec() == null ||
                replicaSet.getSpec().getTemplate().getSpec().getTolerations() == null) {
            return new ArrayList<>();
        }

        return replicaSet.getSpec().getTemplate().getSpec().getTolerations();
    }

    public static Optional<V1Affinity> getAffinity(V1ReplicaSet replicaSet) {
        Objects.requireNonNull(replicaSet.getSpec());
        Objects.requireNonNull(replicaSet.getSpec().getTemplate());
        Objects.requireNonNull(replicaSet.getSpec().getTemplate().getSpec());
        V1Affinity affinity = replicaSet.getSpec().getTemplate().getSpec().getAffinity();
        if (affinity == null) {
            return Optional.empty();
        }
        return Optional.of(affinity);
    }

    private ReplicaSetUtil() {
        throw new UnsupportedOperationException();
    }

}
