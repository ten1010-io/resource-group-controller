package io.ten1010.groupcontroller.controller;

import io.ten1010.groupcontroller.core.ApiResourceKind;
import io.ten1010.groupcontroller.core.ApiResourceKinds;

import java.util.Set;

public final class ControllerSupport {

    private static final Set<ApiResourceKind> SUPPORTED_CONTROLLERS_OF_CRON_JOB = Set.of();
    private static final Set<ApiResourceKind> SUPPORTED_CONTROLLERS_OF_DAEMON_SET = Set.of();
    private static final Set<ApiResourceKind> SUPPORTED_CONTROLLERS_OF_DEPLOYMENT = Set.of();
    private static final Set<ApiResourceKind> SUPPORTED_CONTROLLERS_OF_JOB = Set.of(ApiResourceKinds.CRON_JOB);
    private static final Set<ApiResourceKind> SUPPORTED_CONTROLLERS_OF_POD = Set.of(
            ApiResourceKinds.DAEMON_SET,
            ApiResourceKinds.JOB,
            ApiResourceKinds.REPLICA_SET,
            ApiResourceKinds.REPLICATION_CONTROLLER,
            ApiResourceKinds.STATEFUL_SET,
            ApiResourceKinds.NODE);
    private static final Set<ApiResourceKind> SUPPORTED_CONTROLLERS_OF_REPLICA_SET = Set.of(ApiResourceKinds.DEPLOYMENT);
    private static final Set<ApiResourceKind> SUPPORTED_CONTROLLERS_OF_REPLICATION_CONTROLLER = Set.of();
    private static final Set<ApiResourceKind> SUPPORTED_CONTROLLERS_OF_STATEFUL_SET = Set.of();

    public static boolean isSupportedControllerOfCronJob(ApiResourceKind controllerKind) {
        return SUPPORTED_CONTROLLERS_OF_CRON_JOB.contains(controllerKind);
    }

    public static boolean isSupportedControllerOfDaemonSet(ApiResourceKind controllerKind) {
        return SUPPORTED_CONTROLLERS_OF_DAEMON_SET.contains(controllerKind);
    }

    public static boolean isSupportedControllerOfDeployment(ApiResourceKind controllerKind) {
        return SUPPORTED_CONTROLLERS_OF_DEPLOYMENT.contains(controllerKind);
    }

    public static boolean isSupportedControllerOfJob(ApiResourceKind controllerKind) {
        return SUPPORTED_CONTROLLERS_OF_JOB.contains(controllerKind);
    }

    public static boolean isSupportedControllerOfPod(ApiResourceKind controllerKind) {
        return SUPPORTED_CONTROLLERS_OF_POD.contains(controllerKind);
    }

    public static boolean isSupportedControllerOfReplicaSet(ApiResourceKind controllerKind) {
        return SUPPORTED_CONTROLLERS_OF_REPLICA_SET.contains(controllerKind);
    }

    public static boolean isSupportedControllerOfReplicationController(ApiResourceKind controllerKind) {
        return SUPPORTED_CONTROLLERS_OF_REPLICATION_CONTROLLER.contains(controllerKind);
    }

    public static boolean isSupportedControllerOfStatefulSet(ApiResourceKind controllerKind) {
        return SUPPORTED_CONTROLLERS_OF_STATEFUL_SET.contains(controllerKind);
    }

    private ControllerSupport() {
        throw new UnsupportedOperationException();
    }

}
