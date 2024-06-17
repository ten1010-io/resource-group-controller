package io.ten1010.groupcontroller.controller.cluster.node;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Taint;

import java.time.Duration;
import java.util.*;

public class NodeWatch implements ControllerWatch<V1Node> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1Node> {

        private static Request buildRequest(V1Node obj) {
            V1ObjectMeta meta = obj.getMetadata();
            Objects.requireNonNull(meta);

            return new Request(meta.getNamespace(), meta.getName());
        }

        private static List<V1Taint> getTaints(V1Node obj) {
            Objects.requireNonNull(obj.getSpec());
            return obj.getSpec().getTaints() == null ? new ArrayList<>() : obj.getSpec().getTaints();
        }

        private static Map<String, String> getLabels(V1Node obj) {
            Objects.requireNonNull(obj.getMetadata());
            return obj.getMetadata().getLabels() == null ? new HashMap<>() : obj.getMetadata().getLabels();
        }

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1Node obj) {
            this.queue.add(buildRequest(obj));
        }

        @Override
        public void onUpdate(V1Node oldObj, V1Node newObj) {
            List<V1Taint> oldV1Taints = getTaints(oldObj);
            List<V1Taint> newV1Taints = getTaints(newObj);
            if (oldV1Taints.equals(newV1Taints)) {
                return;
            }
            Map<String, String> oldLabels = getLabels(oldObj);
            Map<String, String> newLabels = getLabels(newObj);
            if (oldLabels.equals(newLabels)) {
                return;
            }

            this.queue.add(buildRequest(newObj));
        }

        @Override
        public void onDelete(V1Node obj, boolean deletedFinalStateUnknown) {
        }

    }

    private WorkQueue<Request> queue;

    public NodeWatch(WorkQueue<Request> queue) {
        this.queue = queue;
    }

    @Override
    public Class<V1Node> getResourceClass() {
        return V1Node.class;
    }

    @Override
    public ResourceEventHandler<V1Node> getResourceEventHandler() {
        return new EventHandler(this.queue);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
