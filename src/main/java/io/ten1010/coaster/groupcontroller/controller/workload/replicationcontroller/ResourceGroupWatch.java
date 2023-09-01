package io.ten1010.coaster.groupcontroller.controller.workload.replicationcontroller;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1ReplicationController;
import io.ten1010.coaster.groupcontroller.controller.EventHandlerUtil;
import io.ten1010.coaster.groupcontroller.core.IndexNames;
import io.ten1010.coaster.groupcontroller.core.ResourceGroupUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResourceGroupWatch implements ControllerWatch<V1ResourceGroup> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1ResourceGroup> {

        private WorkQueue<Request> queue;
        private Indexer<V1ReplicationController> replicationControllerIndexer;

        public EventHandler(WorkQueue<Request> queue, Indexer<V1ReplicationController> replicationControllerIndexer) {
            this.queue = queue;
            this.replicationControllerIndexer = replicationControllerIndexer;
        }

        @Override
        public void onAdd(V1ResourceGroup obj) {
            Set<Request> requests = ResourceGroupUtil.getNamespaces(obj).stream()
                    .flatMap(this::resolveToReplicationControllers)
                    .map(EventHandlerUtil::resolveNamespacedObjectToRequest)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        @Override
        public void onUpdate(V1ResourceGroup oldObj, V1ResourceGroup newObj) {
            Set<Request> requests = EventHandlerUtil.getAddedOrDeletedNamespaces(
                            ResourceGroupUtil.getNamespaces(oldObj),
                            ResourceGroupUtil.getNamespaces(newObj))
                    .stream()
                    .flatMap(this::resolveToReplicationControllers)
                    .map(EventHandlerUtil::resolveNamespacedObjectToRequest)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        @Override
        public void onDelete(V1ResourceGroup obj, boolean deletedFinalStateUnknown) {
            Set<Request> requests = ResourceGroupUtil.getNamespaces(obj).stream()
                    .flatMap(this::resolveToReplicationControllers)
                    .map(EventHandlerUtil::resolveNamespacedObjectToRequest)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        private Stream<V1ReplicationController> resolveToReplicationControllers(String namespaceName) {
            List<V1ReplicationController> replicationControllers = this.replicationControllerIndexer.byIndex(IndexNames.BY_NAMESPACE_NAME_TO_REPLICATION_CONTROLLER_OBJECT, namespaceName);
            return replicationControllers.stream();
        }

    }

    private WorkQueue<Request> queue;
    private Indexer<V1ReplicationController> replicationControllerIndexer;

    public ResourceGroupWatch(WorkQueue<Request> queue, Indexer<V1ReplicationController> replicationControllerIndexer) {
        this.queue = queue;
        this.replicationControllerIndexer = replicationControllerIndexer;
    }

    @Override
    public Class<V1ResourceGroup> getResourceClass() {
        return V1ResourceGroup.class;
    }

    @Override
    public ResourceEventHandler<V1ResourceGroup> getResourceEventHandler() {
        return new EventHandler(this.queue, this.replicationControllerIndexer);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
