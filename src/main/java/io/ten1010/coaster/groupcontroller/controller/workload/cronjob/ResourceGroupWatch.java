package io.ten1010.coaster.groupcontroller.controller.workload.cronjob;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.ten1010.coaster.groupcontroller.controller.EventHandlerUtil;
import io.ten1010.coaster.groupcontroller.core.IndexNames;
import io.ten1010.coaster.groupcontroller.core.ResourceGroupUtil;
import io.ten1010.coaster.groupcontroller.model.V1Beta2ResourceGroup;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResourceGroupWatch implements ControllerWatch<V1Beta2ResourceGroup> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1Beta2ResourceGroup> {

        private WorkQueue<Request> queue;
        private Indexer<V1CronJob> cronJobIndexer;

        public EventHandler(WorkQueue<Request> queue, Indexer<V1CronJob> cronJobIndexer) {
            this.queue = queue;
            this.cronJobIndexer = cronJobIndexer;
        }

        @Override
        public void onAdd(V1Beta2ResourceGroup obj) {
            Set<Request> requests = ResourceGroupUtil.getNamespaces(obj).stream()
                    .flatMap(this::resolveToCronJobs)
                    .map(EventHandlerUtil::resolveNamespacedObjectToRequest)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        @Override
        public void onUpdate(V1Beta2ResourceGroup oldObj, V1Beta2ResourceGroup newObj) {
            Set<Request> requests = EventHandlerUtil.getAddedOrDeletedNamespaces(
                            ResourceGroupUtil.getNamespaces(oldObj),
                            ResourceGroupUtil.getNamespaces(newObj))
                    .stream()
                    .flatMap(this::resolveToCronJobs)
                    .map(EventHandlerUtil::resolveNamespacedObjectToRequest)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        @Override
        public void onDelete(V1Beta2ResourceGroup obj, boolean deletedFinalStateUnknown) {
            Set<Request> requests = ResourceGroupUtil.getNamespaces(obj).stream()
                    .flatMap(this::resolveToCronJobs)
                    .map(EventHandlerUtil::resolveNamespacedObjectToRequest)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        private Stream<V1CronJob> resolveToCronJobs(String namespaceName) {
            List<V1CronJob> cronJobs = this.cronJobIndexer.byIndex(IndexNames.BY_NAMESPACE_NAME_TO_CRON_JOB_OBJECT, namespaceName);
            return cronJobs.stream();
        }

    }

    private WorkQueue<Request> queue;
    private Indexer<V1CronJob> cronJobIndexer;

    public ResourceGroupWatch(WorkQueue<Request> queue, Indexer<V1CronJob> cronJobIndexer) {
        this.queue = queue;
        this.cronJobIndexer = cronJobIndexer;
    }

    @Override
    public Class<V1Beta2ResourceGroup> getResourceClass() {
        return V1Beta2ResourceGroup.class;
    }

    @Override
    public ResourceEventHandler<V1Beta2ResourceGroup> getResourceEventHandler() {
        return new EventHandler(this.queue, this.cronJobIndexer);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
