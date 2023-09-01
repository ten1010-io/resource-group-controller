package io.ten1010.coaster.groupcontroller.core;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1Toleration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class StatefulSetUtil {

    public static List<V1Toleration> getTolerations(V1StatefulSet statefulSet) {
        if (statefulSet.getSpec() == null ||
                statefulSet.getSpec().getTemplate() == null ||
                statefulSet.getSpec().getTemplate().getSpec() == null ||
                statefulSet.getSpec().getTemplate().getSpec().getTolerations() == null) {
            return new ArrayList<>();
        }

        return statefulSet.getSpec().getTemplate().getSpec().getTolerations();
    }

    public static Optional<V1Affinity> getAffinity(V1StatefulSet statefulSet) {
        Objects.requireNonNull(statefulSet.getSpec());
        Objects.requireNonNull(statefulSet.getSpec().getTemplate());
        Objects.requireNonNull(statefulSet.getSpec().getTemplate().getSpec());
        V1Affinity affinity = statefulSet.getSpec().getTemplate().getSpec().getAffinity();
        if (affinity == null) {
            return Optional.empty();
        }
        return Optional.of(affinity);
    }

    private StatefulSetUtil() {
        throw new UnsupportedOperationException();
    }

}
