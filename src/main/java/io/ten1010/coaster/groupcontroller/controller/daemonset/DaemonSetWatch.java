package io.ten1010.coaster.groupcontroller.controller.daemonset;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.coaster.groupcontroller.core.DaemonSetUtil;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class DaemonSetWatch implements ControllerWatch<V1DaemonSet> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1DaemonSet> {

        private static Request buildRequest(V1DaemonSet obj) {
            V1ObjectMeta meta = obj.getMetadata();
            Objects.requireNonNull(meta);

            return new Request(meta.getNamespace(), meta.getName());
        }

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1DaemonSet obj) {
            this.queue.add(buildRequest(obj));
        }

        @Override
        public void onUpdate(V1DaemonSet oldObj, V1DaemonSet newObj) {
            List<V1Toleration> oldTolerations = DaemonSetUtil.getTolerations(oldObj);
            List<V1Toleration> newTolerations = DaemonSetUtil.getTolerations(newObj);
            if (oldTolerations.equals(newTolerations)) {
                return;
            }

            this.queue.add(buildRequest(newObj));
        }

        @Override
        public void onDelete(V1DaemonSet obj, boolean deletedFinalStateUnknown) {
        }

    }

    private WorkQueue<Request> queue;

    public DaemonSetWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1DaemonSet> getResourceClass() {
        return V1DaemonSet.class;
    }

    @Override
    public ResourceEventHandler<V1DaemonSet> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
