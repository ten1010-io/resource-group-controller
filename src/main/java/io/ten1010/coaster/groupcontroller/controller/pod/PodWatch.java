package io.ten1010.coaster.groupcontroller.controller.pod;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Toleration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PodWatch implements ControllerWatch<V1Pod> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1Pod> {

        private static Request buildRequest(V1Pod obj) {
            V1ObjectMeta meta = obj.getMetadata();
            Objects.requireNonNull(meta);

            return new Request(meta.getNamespace(), meta.getName());
        }

        private static List<V1Toleration> getTolerations(V1Pod obj) {
            Objects.requireNonNull(obj.getSpec());
            return obj.getSpec().getTolerations() == null ? new ArrayList<>() : obj.getSpec().getTolerations();
        }

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1Pod obj) {
            this.queue.add(buildRequest(obj));
        }

        @Override
        public void onUpdate(V1Pod oldObj, V1Pod newObj) {
            List<V1Toleration> oldTolerations = getTolerations(oldObj);
            List<V1Toleration> newTolerations = getTolerations(newObj);
            if (!oldTolerations.equals(newTolerations)) {
                this.queue.add(buildRequest(newObj));
            }
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
