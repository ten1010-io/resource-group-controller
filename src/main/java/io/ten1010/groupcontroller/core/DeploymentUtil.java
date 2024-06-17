package io.ten1010.groupcontroller.core;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Toleration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DeploymentUtil {

    public static List<V1Toleration> getTolerations(V1Deployment deployment) {
        if (deployment.getSpec() == null ||
                deployment.getSpec().getTemplate() == null ||
                deployment.getSpec().getTemplate().getSpec() == null ||
                deployment.getSpec().getTemplate().getSpec().getTolerations() == null) {
            return new ArrayList<>();
        }

        return deployment.getSpec().getTemplate().getSpec().getTolerations();
    }

    public static Optional<V1Affinity> getAffinity(V1Deployment deployment) {
        Objects.requireNonNull(deployment.getSpec());
        Objects.requireNonNull(deployment.getSpec().getTemplate());
        Objects.requireNonNull(deployment.getSpec().getTemplate().getSpec());
        V1Affinity affinity = deployment.getSpec().getTemplate().getSpec().getAffinity();
        if (affinity == null) {
            return Optional.empty();
        }
        return Optional.of(affinity);
    }

    private DeploymentUtil() {
        throw new UnsupportedOperationException();
    }

}
