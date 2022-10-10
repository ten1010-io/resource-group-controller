package io.ten1010.coaster.groupcontroller.controller.role;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Role;
import org.javatuples.Pair;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;

public class RoleWatch implements ControllerWatch<V1Role> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1Role> {

        private static Request buildRequest(V1Role obj) {
            V1ObjectMeta meta = obj.getMetadata();
            Objects.requireNonNull(meta);

            return new Request(meta.getNamespace(), meta.getName());
        }

        private static String getName(V1Role obj) {
            Objects.requireNonNull(obj.getMetadata());
            Objects.requireNonNull(obj.getMetadata().getName());

            return obj.getMetadata().getName();
        }

        private WorkQueue<Request> queue;
        private RoleNameUtil roleNameUtil;

        public EventHandler(WorkQueue<Request> queue, RoleNameUtil roleNameUtil) {
            this.queue = queue;
            this.roleNameUtil = roleNameUtil;
        }

        @Override
        public void onAdd(V1Role obj) {
            Pair<Boolean, Matcher> result = this.roleNameUtil.checkResourceGroupRoleNameFormat(getName(obj));
            if (!result.getValue0()) {
                return;
            }

            this.queue.add(buildRequest(obj));
        }

        @Override
        public void onUpdate(V1Role oldObj, V1Role newObj) {
            Pair<Boolean, Matcher> result = this.roleNameUtil.checkResourceGroupRoleNameFormat(getName(newObj));
            if (!result.getValue0()) {
                return;
            }

            this.queue.add(buildRequest(newObj));
        }

        @Override
        public void onDelete(V1Role obj, boolean deletedFinalStateUnknown) {
            Pair<Boolean, Matcher> result = this.roleNameUtil.checkResourceGroupRoleNameFormat(getName(obj));
            if (!result.getValue0()) {
                return;
            }

            this.queue.add(buildRequest(obj));
        }

    }

    private WorkQueue<Request> queue;
    private RoleNameUtil roleNameUtil;

    public RoleWatch(WorkQueue<Request> queue, RoleNameUtil roleNameUtil) {
        this.queue = queue;
        this.roleNameUtil = roleNameUtil;
    }

    @Override
    public Class<V1Role> getResourceClass() {
        return V1Role.class;
    }

    @Override
    public ResourceEventHandler<V1Role> getResourceEventHandler() {
        return new EventHandler(this.queue, this.roleNameUtil);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
