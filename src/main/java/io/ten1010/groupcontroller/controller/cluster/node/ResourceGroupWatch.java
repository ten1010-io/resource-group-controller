package io.ten1010.groupcontroller.controller.cluster.node;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.ten1010.groupcontroller.model.V1Beta1ResourceGroup;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ResourceGroupWatch implements ControllerWatch<V1Beta1ResourceGroup> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1Beta1ResourceGroup> {

        private static List<String> getNodes(V1Beta1ResourceGroup obj) {
            if (obj.getSpec() == null) {
                return new ArrayList<>();
            }
            return obj.getSpec().getNodes();
        }

        private static Set<String> getAddedOrDeletedNodes(List<String> oldNodes, List<String> newNodes) {
            Set<String> deleted = new HashSet<>(oldNodes);
            deleted.removeAll(newNodes);
            Set<String> added = new HashSet<>(newNodes);
            added.removeAll(oldNodes);
            deleted.addAll(added);

            return deleted;
        }

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1Beta1ResourceGroup obj) {
            Set<Request> requests = getNodes(obj).stream()
                    .map(Request::new)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        @Override
        public void onUpdate(V1Beta1ResourceGroup oldObj, V1Beta1ResourceGroup newObj) {
            Set<Request> requests = getAddedOrDeletedNodes(getNodes(oldObj), getNodes(newObj)).stream()
                    .map(Request::new)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        @Override
        public void onDelete(V1Beta1ResourceGroup obj, boolean deletedFinalStateUnknown) {
            Set<Request> requests = getNodes(obj).stream()
                    .map(Request::new)
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

    }

    private WorkQueue<Request> queue;

    public ResourceGroupWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1Beta1ResourceGroup> getResourceClass() {
        return V1Beta1ResourceGroup.class;
    }

    @Override
    public ResourceEventHandler<V1Beta1ResourceGroup> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
