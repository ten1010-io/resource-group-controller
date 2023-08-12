package io.ten1010.coaster.groupcontroller.controller.clusterrolebinding;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.coaster.groupcontroller.core.K8sObjectUtil;

import java.time.Duration;
import java.util.Objects;

public class ClusterRoleBindingWatch implements ControllerWatch<V1ClusterRoleBinding> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1ClusterRoleBinding> {

        private static Request buildRequest(V1ClusterRoleBinding obj) {
            V1ObjectMeta meta = obj.getMetadata();
            Objects.requireNonNull(meta);

            return new Request(meta.getName());
        }

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1ClusterRoleBinding obj) {
            if (!ResourceGroupClusterRoleBindingName.isResourceGroupClusterRoleBindingName(K8sObjectUtil.getName(obj))) {
                return;
            }

            this.queue.add(buildRequest(obj));
        }

        @Override
        public void onUpdate(V1ClusterRoleBinding oldObj, V1ClusterRoleBinding newObj) {
            if (!ResourceGroupClusterRoleBindingName.isResourceGroupClusterRoleBindingName(K8sObjectUtil.getName(newObj))) {
                return;
            }

            this.queue.add(buildRequest(newObj));
        }

        @Override
        public void onDelete(V1ClusterRoleBinding obj, boolean deletedFinalStateUnknown) {
            if (!ResourceGroupClusterRoleBindingName.isResourceGroupClusterRoleBindingName(K8sObjectUtil.getName(obj))) {
                return;
            }

            this.queue.add(buildRequest(obj));
        }

    }

    private WorkQueue<Request> queue;

    public ClusterRoleBindingWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1ClusterRoleBinding> getResourceClass() {
        return V1ClusterRoleBinding.class;
    }

    @Override
    public ResourceEventHandler<V1ClusterRoleBinding> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
