package io.ten1010.coaster.groupcontroller.core;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1Toleration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CronJobUtil {

    public static List<V1Toleration> getTolerations(V1CronJob cronJob) {
        if (cronJob.getSpec() == null ||
                cronJob.getSpec().getJobTemplate() == null ||
                cronJob.getSpec().getJobTemplate().getSpec() == null ||
                cronJob.getSpec().getJobTemplate().getSpec().getTemplate() == null ||
                cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec() == null ||
                cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec().getTolerations() == null) {
            return new ArrayList<>();
        }

        return cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec().getTolerations();
    }

    public static Optional<V1Affinity> getAffinity(V1CronJob cronJob) {
        Objects.requireNonNull(cronJob.getSpec());
        Objects.requireNonNull(cronJob.getSpec().getJobTemplate());
        Objects.requireNonNull(cronJob.getSpec().getJobTemplate().getSpec());
        Objects.requireNonNull(cronJob.getSpec().getJobTemplate().getSpec().getTemplate());
        Objects.requireNonNull(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec());
        V1Affinity affinity = cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec().getAffinity();
        if (affinity == null) {
            return Optional.empty();
        }
        return Optional.of(affinity);
    }

    private CronJobUtil() {
        throw new UnsupportedOperationException();
    }

}
