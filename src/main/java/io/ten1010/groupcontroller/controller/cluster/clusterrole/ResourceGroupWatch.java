package io.ten1010.groupcontroller.controller.cluster.clusterrole;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.ten1010.groupcontroller.core.K8sObjectUtil;
import io.ten1010.groupcontroller.model.V1Beta1ResourceGroup;

import java.time.Duration;

public class ResourceGroupWatch implements ControllerWatch<V1Beta1ResourceGroup> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1Beta1ResourceGroup> {

        private static Request buildRequest(String groupName) {
            return new Request(new ResourceGroupClusterRoleName(groupName).getName());
        }

        private WorkQueue<Request> queue;

        public EventHandler(WorkQueue<Request> queue) {
            this.queue = queue;
        }

        @Override
        public void onAdd(V1Beta1ResourceGroup obj) {
            String groupName = K8sObjectUtil.getName(obj);
            this.queue.add(buildRequest(groupName));
        }

        @Override
        public void onUpdate(V1Beta1ResourceGroup oldObj, V1Beta1ResourceGroup newObj) {
            String groupName = K8sObjectUtil.getName(newObj);
            this.queue.add(buildRequest(groupName));
        }

        @Override
        public void onDelete(V1Beta1ResourceGroup obj, boolean deletedFinalStateUnknown) {
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
