package io.ten1010.groupcontroller.core;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Toleration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JobUtil {

    public static List<V1Toleration> getTolerations(V1Job job) {
        if (job.getSpec() == null ||
                job.getSpec().getTemplate() == null ||
                job.getSpec().getTemplate().getSpec() == null ||
                job.getSpec().getTemplate().getSpec().getTolerations() == null) {
            return new ArrayList<>();
        }

        return job.getSpec().getTemplate().getSpec().getTolerations();
    }

    public static Optional<V1Affinity> getAffinity(V1Job job) {
        Objects.requireNonNull(job.getSpec());
        Objects.requireNonNull(job.getSpec().getTemplate());
        Objects.requireNonNull(job.getSpec().getTemplate().getSpec());
        V1Affinity affinity = job.getSpec().getTemplate().getSpec().getAffinity();
        if (affinity == null) {
            return Optional.empty();
        }
        return Optional.of(affinity);
    }

    private JobUtil() {
        throw new UnsupportedOperationException();
    }

}
