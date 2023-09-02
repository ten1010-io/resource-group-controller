package io.ten1010.coaster.groupcontroller.controller.workload.pod;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.ten1010.coaster.groupcontroller.core.IndexNameConstants;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResourceGroupWatch implements ControllerWatch<V1ResourceGroup> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1ResourceGroup> {

        private static List<String> getNamespaces(V1ResourceGroup obj) {
            if (obj.getSpec() == null) {
                return new ArrayList<>();
            }
            return obj.getSpec().getNamespaces();
        }

        private static Request resolveToRequest(V1Pod obj) {
            V1ObjectMeta meta = obj.getMetadata();
            Objects.requireNonNull(meta);

            return new Request(meta.getNamespace(), meta.getName());
        }

        private static Set<String> getAddedOrDeletedNamespaces(List<String> oldNamespaces, List<String> newNamespaces) {
            Set<String> deleted = new HashSet<>(oldNamespaces);
            deleted.removeAll(newNamespaces);
            Set<String> added = new HashSet<>(newNamespaces);
            added.removeAll(oldNamespaces);
            deleted.addAll(added);

            return deleted;
        }

        private WorkQueue<Request> queue;
        private Indexer<V1Pod> podIndexer;

        public EventHandler(WorkQueue<Request> queue, Indexer<V1Pod> podIndexer) {
            this.queue = queue;
            this.podIndexer = podIndexer;
        }

        @Override
        public void onAdd(V1ResourceGroup obj) {
            Set<Request> requests = getNamespaces(obj).stream()
                    .flatMap(this::resolveToPods)
                    .map(EventHandler::resolveToRequest)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        @Override
        public void onUpdate(V1ResourceGroup oldObj, V1ResourceGroup newObj) {
            Set<Request> requests = getAddedOrDeletedNamespaces(getNamespaces(oldObj), getNamespaces(newObj)).stream()
                    .flatMap(this::resolveToPods)
                    .map(EventHandler::resolveToRequest)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        @Override
        public void onDelete(V1ResourceGroup obj, boolean deletedFinalStateUnknown) {
            Set<Request> requests = getNamespaces(obj).stream()
                    .flatMap(this::resolveToPods)
                    .map(EventHandler::resolveToRequest)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        private Stream<V1Pod> resolveToPods(String namespaceName) {
            List<V1Pod> pods = this.podIndexer.byIndex(IndexNameConstants.BY_NAMESPACE_NAME_TO_POD_OBJECT, namespaceName);

            return pods.stream();
        }

    }

    private WorkQueue<Request> queue;
    private Indexer<V1Pod> podIndexer;

    public ResourceGroupWatch(WorkQueue<Request> queue, Indexer<V1Pod> podIndexer) {
        this.queue = queue;
        this.podIndexer = podIndexer;
    }

    @Override
    public Class<V1ResourceGroup> getResourceClass() {
        return V1ResourceGroup.class;
    }

    @Override
    public ResourceEventHandler<V1ResourceGroup> getResourceEventHandler() {
        return new EventHandler(this.queue, this.podIndexer);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
