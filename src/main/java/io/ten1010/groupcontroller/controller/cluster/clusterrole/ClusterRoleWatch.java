package io.ten1010.groupcontroller.controller.cluster.clusterrole;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

import java.time.Duration;
import java.util.Objects;

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

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1ClusterRole obj) {
            if (!ResourceGroupClusterRoleName.isResourceGroupClusterRoleName(getName(obj))) {
                return;
            }

            this.queue.add(buildRequest(obj));
        }

        @Override
        public void onUpdate(V1ClusterRole oldObj, V1ClusterRole newObj) {
            if (!ResourceGroupClusterRoleName.isResourceGroupClusterRoleName(getName(newObj))) {
                return;
            }

            this.queue.add(buildRequest(newObj));
        }

        @Override
        public void onDelete(V1ClusterRole obj, boolean deletedFinalStateUnknown) {
            if (!ResourceGroupClusterRoleName.isResourceGroupClusterRoleName(getName(obj))) {
                return;
            }

            this.queue.add(buildRequest(obj));
        }

    }

    private WorkQueue<Request> queue;

    public ClusterRoleWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1ClusterRole> getResourceClass() {
        return V1ClusterRole.class;
    }

    @Override
    public ResourceEventHandler<V1ClusterRole> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
