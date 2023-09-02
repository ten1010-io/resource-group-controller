package io.ten1010.coaster.groupcontroller.controller.cluster.rolebinding;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.ten1010.coaster.groupcontroller.core.K8sObjectUtil;

import java.time.Duration;
import java.util.Objects;

public class RoleBindingWatch implements ControllerWatch<V1RoleBinding> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1RoleBinding> {

        private static Request buildRequest(V1RoleBinding obj) {
            V1ObjectMeta meta = obj.getMetadata();
            Objects.requireNonNull(meta);

            return new Request(meta.getNamespace(), meta.getName());
        }

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1RoleBinding obj) {
            if (!ResourceGroupRoleBindingName.isResourceGroupRoleBindingName(K8sObjectUtil.getName(obj))) {
                return;
            }

            this.queue.add(buildRequest(obj));
        }

        @Override
        public void onUpdate(V1RoleBinding oldObj, V1RoleBinding newObj) {
            if (!ResourceGroupRoleBindingName.isResourceGroupRoleBindingName(K8sObjectUtil.getName(newObj))) {
                return;
            }

            this.queue.add(buildRequest(newObj));
        }

        @Override
        public void onDelete(V1RoleBinding obj, boolean deletedFinalStateUnknown) {
            if (!ResourceGroupRoleBindingName.isResourceGroupRoleBindingName(K8sObjectUtil.getName(obj))) {
                return;
            }

            this.queue.add(buildRequest(obj));
        }

    }

    private WorkQueue<Request> queue;

    public RoleBindingWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1RoleBinding> getResourceClass() {
        return V1RoleBinding.class;
    }

    @Override
    public ResourceEventHandler<V1RoleBinding> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
