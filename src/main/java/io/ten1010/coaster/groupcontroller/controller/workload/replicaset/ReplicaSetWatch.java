package io.ten1010.coaster.groupcontroller.controller.workload.replicaset;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.coaster.groupcontroller.controller.EventHandlerUtil;
import io.ten1010.coaster.groupcontroller.core.ReplicaSetUtil;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class ReplicaSetWatch implements ControllerWatch<V1ReplicaSet> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1ReplicaSet> {

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1ReplicaSet obj) {
            this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(obj));
        }

        @Override
        public void onUpdate(V1ReplicaSet oldObj, V1ReplicaSet newObj) {
            Optional<V1Affinity> oldAffinity = ReplicaSetUtil.getAffinity(oldObj);
            Optional<V1Affinity> newAffinity = ReplicaSetUtil.getAffinity(newObj);
            if (!oldAffinity.equals(newAffinity)) {
                this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(newObj));
                return;
            }
            List<V1Toleration> oldTolerations = ReplicaSetUtil.getTolerations(oldObj);
            List<V1Toleration> newTolerations = ReplicaSetUtil.getTolerations(newObj);
            if (!oldTolerations.equals(newTolerations)) {
                this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(newObj));
                return;
            }
        }

        @Override
        public void onDelete(V1ReplicaSet obj, boolean deletedFinalStateUnknown) {
        }

    }

    private WorkQueue<Request> queue;

    public ReplicaSetWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1ReplicaSet> getResourceClass() {
        return V1ReplicaSet.class;
    }

    @Override
    public ResourceEventHandler<V1ReplicaSet> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
