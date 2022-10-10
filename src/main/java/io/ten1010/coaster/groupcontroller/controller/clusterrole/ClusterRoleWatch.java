package io.ten1010.coaster.groupcontroller.controller.clusterrole;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import org.javatuples.Pair;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;

public class ClusterRoleWatch implements ControllerWatch<V1ClusterRole> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1ClusterRole> {

        private static Request buildRequest(V1ClusterRole obj) {
            V1ObjectMeta meta = obj.getMetadata();
            Objects.requireNonNull(meta);

            return new Request(meta.getNamespace(), meta.getName());
        }

        private static String getName(V1ClusterRole obj) {
            Objects.requireNonNull(obj.getMetadata());
            Objects.requireNonNull(obj.getMetadata().getName());

            return obj.getMetadata().getName();
        }

        private WorkQueue<Request> queue;
        private ClusterRoleNameUtil clusterRoleNameUtil;

        public EventHandler(WorkQueue<Request> queue, ClusterRoleNameUtil clusterRoleNameUtil) {
            this.queue = queue;
            this.clusterRoleNameUtil = clusterRoleNameUtil;
        }

        @Override
        public void onAdd(V1ClusterRole obj) {
            Pair<Boolean, Matcher> result = this.clusterRoleNameUtil.checkResourceGroupClusterRoleNameFormat(getName(obj));
            if (!result.getValue0()) {
                return;
            }

            this.queue.add(buildRequest(obj));
        }

        @Override
        public void onUpdate(V1ClusterRole oldObj, V1ClusterRole newObj) {
            Pair<Boolean, Matcher> result = this.clusterRoleNameUtil.checkResourceGroupClusterRoleNameFormat(getName(newObj));
            if (!result.getValue0()) {
                return;
            }

            this.queue.add(buildRequest(newObj));
        }

        @Override
        public void onDelete(V1ClusterRole obj, boolean deletedFinalStateUnknown) {
            Pair<Boolean, Matcher> result = this.clusterRoleNameUtil.checkResourceGroupClusterRoleNameFormat(getName(obj));
            if (!result.getValue0()) {
                return;
            }

            this.queue.add(buildRequest(obj));
        }

    }

    private WorkQueue<Request> queue;
    private ClusterRoleNameUtil clusterRoleNameUtil;

    public ClusterRoleWatch(WorkQueue<Request> queue, ClusterRoleNameUtil clusterRoleNameUtil) {
        this.queue = queue;
        this.clusterRoleNameUtil = clusterRoleNameUtil;
    }

    @Override
    public Class<V1ClusterRole> getResourceClass() {
        return V1ClusterRole.class;
    }

    @Override
    public ResourceEventHandler<V1ClusterRole> getResourceEventHandler() {
        return new EventHandler(this.queue, this.clusterRoleNameUtil);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
