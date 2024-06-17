package io.ten1010.groupcontroller.controller.workload.job;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1Job;
import io.ten1010.groupcontroller.controller.EventHandlerUtil;

import java.time.Duration;

public class JobWatch implements ControllerWatch<V1Job> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1Job> {

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1Job obj) {
            this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(obj));
        }

        @Override
        public void onUpdate(V1Job oldObj, V1Job newObj) {
        }

        @Override
        public void onDelete(V1Job obj, boolean deletedFinalStateUnknown) {
        }

    }

    private WorkQueue<Request> queue;

    public JobWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1Job> getResourceClass() {
        return V1Job.class;
    }

    @Override
    public ResourceEventHandler<V1Job> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
