package io.ten1010.groupcontroller.controller.workload.deployment;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.groupcontroller.controller.EventHandlerUtil;
import io.ten1010.groupcontroller.core.DeploymentUtil;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class DeploymentWatch implements ControllerWatch<V1Deployment> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1Deployment> {

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1Deployment obj) {
            this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(obj));
        }

        @Override
        public void onUpdate(V1Deployment oldObj, V1Deployment newObj) {
            Optional<V1Affinity> oldAffinity = DeploymentUtil.getAffinity(oldObj);
            Optional<V1Affinity> newAffinity = DeploymentUtil.getAffinity(newObj);
            if (!oldAffinity.equals(newAffinity)) {
                this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(newObj));
                return;
            }
            List<V1Toleration> oldTolerations = DeploymentUtil.getTolerations(oldObj);
            List<V1Toleration> newTolerations = DeploymentUtil.getTolerations(newObj);
            if (!oldTolerations.equals(newTolerations)) {
                this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(newObj));
                return;
            }
        }

        @Override
        public void onDelete(V1Deployment obj, boolean deletedFinalStateUnknown) {
        }

    }

    private WorkQueue<Request> queue;

    public DeploymentWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1Deployment> getResourceClass() {
        return V1Deployment.class;
    }

    @Override
    public ResourceEventHandler<V1Deployment> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
