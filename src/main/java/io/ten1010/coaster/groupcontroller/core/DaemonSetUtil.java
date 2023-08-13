package io.ten1010.coaster.groupcontroller.core;

import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Toleration;

import java.util.ArrayList;
import java.util.List;

public class DaemonSetUtil {

    public static List<V1Toleration> getTolerations(V1DaemonSet daemonSet) {
        if (daemonSet.getSpec() == null ||
                daemonSet.getSpec().getTemplate() == null ||
                daemonSet.getSpec().getTemplate().getSpec() == null ||
                daemonSet.getSpec().getTemplate().getSpec().getTolerations() == null) {
            return new ArrayList<>();
        }

        return daemonSet.getSpec().getTemplate().getSpec().getTolerations();
    }

}
