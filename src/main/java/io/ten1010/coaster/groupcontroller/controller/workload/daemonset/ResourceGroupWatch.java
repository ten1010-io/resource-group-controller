package io.ten1010.coaster.groupcontroller.controller.workload.daemonset;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.ten1010.coaster.groupcontroller.model.DaemonSetReference;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ResourceGroupWatch implements ControllerWatch<V1ResourceGroup> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1ResourceGroup> {

        private static List<DaemonSetReference> getDaemonSets(V1ResourceGroup obj) {
            if (obj.getSpec() == null || obj.getSpec().getExceptions() == null) {
                return new ArrayList<>();
            }

            return obj.getSpec().getExceptions().getDaemonSets();
        }

        private static Set<DaemonSetReference> getAddedOrDeletedDaemonSets(List<DaemonSetReference> oldDaemonSets, List<DaemonSetReference> newDaemonSets) {
            Set<DaemonSetReference> deleted = new HashSet<>(oldDaemonSets);
            deleted.removeAll(newDaemonSets);
            Set<DaemonSetReference> added = new HashSet<>(newDaemonSets);
            added.removeAll(oldDaemonSets);
            deleted.addAll(added);

            return deleted;
        }

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1ResourceGroup obj) {
            Set<Request> requests = getDaemonSets(obj).stream()
                    .map(e -> new Request(e.getNamespace(), e.getName()))
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        @Override
        public void onUpdate(V1ResourceGroup oldObj, V1ResourceGroup newObj) {
            Set<Request> requests = getAddedOrDeletedDaemonSets(getDaemonSets(oldObj), getDaemonSets(newObj)).stream()
                    .map(e -> new Request(e.getNamespace(), e.getName()))
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        @Override
        public void onDelete(V1ResourceGroup obj, boolean deletedFinalStateUnknown) {
            Set<Request> requests = getDaemonSets(obj).stream()
                    .map(e -> new Request(e.getNamespace(), e.getName()))
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

    }

    private WorkQueue<Request> queue;

    public ResourceGroupWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1ResourceGroup> getResourceClass() {
        return V1ResourceGroup.class;
    }

    @Override
    public ResourceEventHandler<V1ResourceGroup> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
