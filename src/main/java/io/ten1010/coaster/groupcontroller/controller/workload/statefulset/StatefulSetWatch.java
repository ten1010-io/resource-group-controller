package io.ten1010.coaster.groupcontroller.controller.workload.statefulset;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.coaster.groupcontroller.controller.EventHandlerUtil;
import io.ten1010.coaster.groupcontroller.core.StatefulSetUtil;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class StatefulSetWatch implements ControllerWatch<V1StatefulSet> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1StatefulSet> {

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1StatefulSet obj) {
            this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(obj));
        }

        @Override
        public void onUpdate(V1StatefulSet oldObj, V1StatefulSet newObj) {
            Optional<V1Affinity> oldAffinity = StatefulSetUtil.getAffinity(oldObj);
            Optional<V1Affinity> newAffinity = StatefulSetUtil.getAffinity(newObj);
            if (!oldAffinity.equals(newAffinity)) {
                this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(newObj));
                return;
            }
            List<V1Toleration> oldTolerations = StatefulSetUtil.getTolerations(oldObj);
            List<V1Toleration> newTolerations = StatefulSetUtil.getTolerations(newObj);
            if (!oldTolerations.equals(newTolerations)) {
                this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(newObj));
                return;
            }
        }

        @Override
        public void onDelete(V1StatefulSet obj, boolean deletedFinalStateUnknown) {
        }

    }

    private WorkQueue<Request> queue;

    public StatefulSetWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1StatefulSet> getResourceClass() {
        return V1StatefulSet.class;
    }

    @Override
    public ResourceEventHandler<V1StatefulSet> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
