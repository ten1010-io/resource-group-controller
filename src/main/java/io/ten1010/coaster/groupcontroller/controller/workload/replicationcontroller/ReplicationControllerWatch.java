package io.ten1010.coaster.groupcontroller.controller.workload.replicationcontroller;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1ReplicationController;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.coaster.groupcontroller.controller.EventHandlerUtil;
import io.ten1010.coaster.groupcontroller.core.ReplicationControllerUtil;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class ReplicationControllerWatch implements ControllerWatch<V1ReplicationController> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1ReplicationController> {

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1ReplicationController obj) {
            this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(obj));
        }

        @Override
        public void onUpdate(V1ReplicationController oldObj, V1ReplicationController newObj) {
            Optional<V1Affinity> oldAffinity = ReplicationControllerUtil.getAffinity(oldObj);
            Optional<V1Affinity> newAffinity = ReplicationControllerUtil.getAffinity(newObj);
            if (!oldAffinity.equals(newAffinity)) {
                this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(newObj));
                return;
            }
            List<V1Toleration> oldTolerations = ReplicationControllerUtil.getTolerations(oldObj);
            List<V1Toleration> newTolerations = ReplicationControllerUtil.getTolerations(newObj);
            if (!oldTolerations.equals(newTolerations)) {
                this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(newObj));
                return;
            }
        }

        @Override
        public void onDelete(V1ReplicationController obj, boolean deletedFinalStateUnknown) {
        }

    }

    private WorkQueue<Request> queue;

    public ReplicationControllerWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1ReplicationController> getResourceClass() {
        return V1ReplicationController.class;
    }

    @Override
    public ResourceEventHandler<V1ReplicationController> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
