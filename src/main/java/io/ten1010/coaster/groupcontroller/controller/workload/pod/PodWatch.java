package io.ten1010.coaster.groupcontroller.controller.workload.pod;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1Pod;
import io.ten1010.coaster.groupcontroller.controller.EventHandlerUtil;

import java.time.Duration;

public class PodWatch implements ControllerWatch<V1Pod> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1Pod> {

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1Pod obj) {
            this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(obj));
        }

        @Override
        public void onUpdate(V1Pod oldObj, V1Pod newObj) {
        }

        @Override
        public void onDelete(V1Pod obj, boolean deletedFinalStateUnknown) {
        }

    }

    private WorkQueue<Request> queue;

    public PodWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1Pod> getResourceClass() {
        return V1Pod.class;
    }

    @Override
    public ResourceEventHandler<V1Pod> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
