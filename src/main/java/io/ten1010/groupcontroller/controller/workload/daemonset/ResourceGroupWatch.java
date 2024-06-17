package io.ten1010.groupcontroller.controller.workload.daemonset;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.ten1010.groupcontroller.controller.EventHandlerUtil;
import io.ten1010.groupcontroller.core.IndexNames;
import io.ten1010.groupcontroller.core.ResourceGroupUtil;
import io.ten1010.groupcontroller.model.V1Beta1K8sObjectReference;
import io.ten1010.groupcontroller.model.V1Beta1ResourceGroup;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResourceGroupWatch implements ControllerWatch<V1Beta1ResourceGroup> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1Beta1ResourceGroup> {

        private static Set<V1Beta1K8sObjectReference> getAddedOrDeletedDaemonSets(List<V1Beta1K8sObjectReference> oldDaemonSets, List<V1Beta1K8sObjectReference> newDaemonSets) {
            Set<V1Beta1K8sObjectReference> deleted = new HashSet<>(oldDaemonSets);
            newDaemonSets.forEach(deleted::remove);
            Set<V1Beta1K8sObjectReference> added = new HashSet<>(newDaemonSets);
            oldDaemonSets.forEach(added::remove);
            deleted.addAll(added);
            return deleted;
        }

        private WorkQueue<Request> queue;
        private Indexer<V1DaemonSet> daemonSetIndexer;

        public EventHandler(WorkQueue<Request> queue, Indexer<V1DaemonSet> daemonSetIndexer) {
            this.queue = queue;
            this.daemonSetIndexer = daemonSetIndexer;
        }

        @Override
        public void onAdd(V1Beta1ResourceGroup obj) {
            Set<Request> requestsFromNamespaces = ResourceGroupUtil.getNamespaces(obj).stream()
                    .flatMap(this::resolveToDaemonSet)
                    .map(EventHandlerUtil::resolveNamespacedObjectToRequest)
                    .collect(Collectors.toSet());
            Set<Request> requestsFromDaemonSets = ResourceGroupUtil.getDaemonSets(obj).stream()
                    .map(e -> new Request(e.getNamespace(), e.getName()))
                    .collect(Collectors.toSet());
            Set<Request> requests = new HashSet<>(requestsFromNamespaces);
            requests.addAll(requestsFromDaemonSets);
            requests.forEach(this.queue::add);
        }

        @Override
        public void onUpdate(V1Beta1ResourceGroup oldObj, V1Beta1ResourceGroup newObj) {
            Set<Request> requestsFromNamespaces = EventHandlerUtil.getAddedOrDeletedNamespaces(
                            ResourceGroupUtil.getNamespaces(oldObj),
                            ResourceGroupUtil.getNamespaces(newObj))
                    .stream()
                    .flatMap(this::resolveToDaemonSet)
                    .map(EventHandlerUtil::resolveNamespacedObjectToRequest)
                    .collect(Collectors.toSet());
            Set<Request> requestsFromDaemonSets = getAddedOrDeletedDaemonSets(
                    ResourceGroupUtil.getDaemonSets(oldObj),
                    ResourceGroupUtil.getDaemonSets(newObj))
                    .stream()
                    .map(e -> new Request(e.getNamespace(), e.getName()))
                    .collect(Collectors.toSet());
            Set<Request> requests = new HashSet<>(requestsFromNamespaces);
            requests.addAll(requestsFromDaemonSets);
            requests.forEach(this.queue::add);
        }

        @Override
        public void onDelete(V1Beta1ResourceGroup obj, boolean deletedFinalStateUnknown) {
            Set<Request> requestsFromNamespaces = ResourceGroupUtil.getNamespaces(obj).stream()
                    .flatMap(this::resolveToDaemonSet)
                    .map(EventHandlerUtil::resolveNamespacedObjectToRequest)
                    .collect(Collectors.toSet());
            Set<Request> requestsFromDaemonSets = ResourceGroupUtil.getDaemonSets(obj).stream()
                    .map(e -> new Request(e.getNamespace(), e.getName()))
                    .collect(Collectors.toSet());
            Set<Request> requests = new HashSet<>(requestsFromNamespaces);
            requests.addAll(requestsFromDaemonSets);
            requests.forEach(this.queue::add);
        }

        private Stream<V1DaemonSet> resolveToDaemonSet(String namespaceName) {
            List<V1DaemonSet> daemonSets = this.daemonSetIndexer.byIndex(IndexNames.BY_NAMESPACE_NAME_TO_DAEMON_SET_OBJECT, namespaceName);
            return daemonSets.stream();
        }

    }

    private WorkQueue<Request> queue;
    private Indexer<V1DaemonSet> daemonSetIndexer;

    public ResourceGroupWatch(WorkQueue<Request> queue, Indexer<V1DaemonSet> daemonSetIndexer) {
        this.queue = queue;
        this.daemonSetIndexer = daemonSetIndexer;
    }

    @Override
    public Class<V1Beta1ResourceGroup> getResourceClass() {
        return V1Beta1ResourceGroup.class;
    }

    @Override
    public ResourceEventHandler<V1Beta1ResourceGroup> getResourceEventHandler() {
        return new EventHandler(this.queue, this.daemonSetIndexer);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
