package io.ten1010.groupcontroller.controller.workload.cronjob;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.groupcontroller.controller.EventHandlerUtil;
import io.ten1010.groupcontroller.core.CronJobUtil;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class CronJobWatch implements ControllerWatch<V1CronJob> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1CronJob> {

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1CronJob obj) {
            this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(obj));
        }

        @Override
        public void onUpdate(V1CronJob oldObj, V1CronJob newObj) {
            Optional<V1Affinity> oldAffinity = CronJobUtil.getAffinity(oldObj);
            Optional<V1Affinity> newAffinity = CronJobUtil.getAffinity(newObj);
            if (!oldAffinity.equals(newAffinity)) {
                this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(newObj));
                return;
            }
            List<V1Toleration> oldTolerations = CronJobUtil.getTolerations(oldObj);
            List<V1Toleration> newTolerations = CronJobUtil.getTolerations(newObj);
            if (!oldTolerations.equals(newTolerations)) {
                this.queue.add(EventHandlerUtil.resolveNamespacedObjectToRequest(newObj));
                return;
            }
        }

        @Override
        public void onDelete(V1CronJob obj, boolean deletedFinalStateUnknown) {
        }

    }

    private WorkQueue<Request> queue;

    public CronJobWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1CronJob> getResourceClass() {
        return V1CronJob.class;
    }

    @Override
    public ResourceEventHandler<V1CronJob> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
