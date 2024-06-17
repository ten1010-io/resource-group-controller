package io.ten1010.groupcontroller.controller.cluster.rolebinding;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.ten1010.groupcontroller.core.IndexNames;
import io.ten1010.groupcontroller.core.K8sObjectUtil;
import io.ten1010.groupcontroller.model.V1Beta1ResourceGroup;

import java.time.Duration;
import java.util.List;

public class NamespaceWatch implements ControllerWatch<V1Namespace> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1Namespace> {

        private WorkQueue<Request> queue;
        private Indexer<V1Beta1ResourceGroup> groupIndexer;

        public EventHandler(
                WorkQueue<Request> queue,
                Indexer<V1Beta1ResourceGroup> groupIndexer) {
            this.queue = queue;
            this.groupIndexer = groupIndexer;
        }

        @Override
        public void onAdd(V1Namespace obj) {
            List<V1Beta1ResourceGroup> groups = this.groupIndexer.byIndex(IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getName(obj));
            groups.stream()
                    .map(group -> {
                        String bindingName = new ResourceGroupRoleBindingName(K8sObjectUtil.getName(group)).getName();
                        return new Request(K8sObjectUtil.getName(obj), bindingName);
                    })
                    .forEach(this.queue::add);
        }

        @Override
        public void onUpdate(V1Namespace oldObj, V1Namespace newObj) {
        }

        @Override
        public void onDelete(V1Namespace obj, boolean deletedFinalStateUnknown) {
        }

    }

    private WorkQueue<Request> queue;
    private Indexer<V1Beta1ResourceGroup> groupIndexer;

    public NamespaceWatch(
            WorkQueue<Request> queue,
            Indexer<V1Beta1ResourceGroup> groupIndexer) {
        this.queue = queue;
        this.groupIndexer = groupIndexer;
    }

    @Override
    public Class<V1Namespace> getResourceClass() {
        return V1Namespace.class;
    }

    @Override
    public ResourceEventHandler<V1Namespace> getResourceEventHandler() {
        return new EventHandler(this.queue, this.groupIndexer);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
