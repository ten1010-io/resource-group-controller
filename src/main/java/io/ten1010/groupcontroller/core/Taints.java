package io.ten1010.groupcontroller.core;

public final class Taints {

    public static final String KEY_RESOURCE_GROUP_EXCLUSIVE = "resource-group.ten1010.io/exclusive";

    public static final String EFFECT_NO_SCHEDULE = "NoSchedule";
    public static final String EFFECT_NO_EXECUTE = "NoExecute";

    private Taints() {
        throw new UnsupportedOperationException();
    }

}
