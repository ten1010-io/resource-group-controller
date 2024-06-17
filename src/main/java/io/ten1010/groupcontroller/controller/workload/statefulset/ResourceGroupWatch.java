package io.ten1010.groupcontroller.controller.workload.statefulset;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.ten1010.groupcontroller.controller.EventHandlerUtil;
import io.ten1010.groupcontroller.core.IndexNames;
import io.ten1010.groupcontroller.core.ResourceGroupUtil;
import io.ten1010.groupcontroller.model.V1Beta1ResourceGroup;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResourceGroupWatch implements ControllerWatch<V1Beta1ResourceGroup> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1Beta1ResourceGroup> {

        private WorkQueue<Request> queue;
        private Indexer<V1StatefulSet> statefulSetIndexer;

        public EventHandler(WorkQueue<Request> queue, Indexer<V1StatefulSet> statefulSetIndexer) {
            this.queue = queue;
            this.statefulSetIndexer = statefulSetIndexer;
        }

        @Override
        public void onAdd(V1Beta1ResourceGroup obj) {
            Set<Request> requests = ResourceGroupUtil.getNamespaces(obj).stream()
                    .flatMap(this::resolveToStatefulSets)
                    .map(EventHandlerUtil::resolveNamespacedObjectToRequest)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        @Override
        public void onUpdate(V1Beta1ResourceGroup oldObj, V1Beta1ResourceGroup newObj) {
            Set<Request> requests = EventHandlerUtil.getAddedOrDeletedNamespaces(
                            ResourceGroupUtil.getNamespaces(oldObj),
                            ResourceGroupUtil.getNamespaces(newObj))
                    .stream()
                    .flatMap(this::resolveToStatefulSets)
                    .map(EventHandlerUtil::resolveNamespacedObjectToRequest)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        @Override
        public void onDelete(V1Beta1ResourceGroup obj, boolean deletedFinalStateUnknown) {
            Set<Request> requests = ResourceGroupUtil.getNamespaces(obj).stream()
                    .flatMap(this::resolveToStatefulSets)
                    .map(EventHandlerUtil::resolveNamespacedObjectToRequest)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        private Stream<V1StatefulSet> resolveToStatefulSets(String namespaceName) {
            List<V1StatefulSet> statefulSets = this.statefulSetIndexer.byIndex(IndexNames.BY_NAMESPACE_NAME_TO_STATEFUL_SET_OBJECT, namespaceName);
            return statefulSets.stream();
        }

    }

    private WorkQueue<Request> queue;
    private Indexer<V1StatefulSet> statefulSetIndexer;

    public ResourceGroupWatch(WorkQueue<Request> queue, Indexer<V1StatefulSet> statefulSetIndexer) {
        this.queue = queue;
        this.statefulSetIndexer = statefulSetIndexer;
    }

    @Override
    public Class<V1Beta1ResourceGroup> getResourceClass() {
        return V1Beta1ResourceGroup.class;
    }

    @Override
    public ResourceEventHandler<V1Beta1ResourceGroup> getResourceEventHandler() {
        return new EventHandler(this.queue, this.statefulSetIndexer);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
