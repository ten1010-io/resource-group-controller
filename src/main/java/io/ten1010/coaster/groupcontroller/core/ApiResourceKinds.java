package io.ten1010.coaster.groupcontroller.core;

public final class ApiResourceKinds {

    public static final ApiResourceKind CRON_JOB = new ApiResourceKind("batch", "CronJob");
    public static final ApiResourceKind DAEMON_SET = new ApiResourceKind("apps", "DaemonSet");
    public static final ApiResourceKind DEPLOYMENT = new ApiResourceKind("apps", "Deployment");
    public static final ApiResourceKind JOB = new ApiResourceKind("batch", "Job");
    public static final ApiResourceKind POD = new ApiResourceKind("", "Pod");
    public static final ApiResourceKind REPLICA_SET = new ApiResourceKind("apps", "ReplicaSet");
    public static final ApiResourceKind REPLICATION_CONTROLLER = new ApiResourceKind("", "ReplicationController");
    public static final ApiResourceKind STATEFUL_SET = new ApiResourceKind("apps", "StatefulSet");

    public static final ApiResourceKind NODE = new ApiResourceKind("", "Node");

    private ApiResourceKinds() {
        throw new UnsupportedOperationException();
    }

}
