package io.ten1010.coaster.groupcontroller.controller.clusterrole;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.ten1010.coaster.groupcontroller.controller.ReconcilerUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

import java.time.Duration;

public class ResourceGroupWatch implements ControllerWatch<V1ResourceGroup> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1ResourceGroup> {

        private WorkQueue<Request> queue;
        private ClusterRoleNameUtil clusterRoleNameUtil;

        public EventHandler(WorkQueue<Request> queue, ClusterRoleNameUtil clusterRoleNameUtil) {
            this.queue = queue;
            this.clusterRoleNameUtil = clusterRoleNameUtil;
        }

        @Override
        public void onAdd(V1ResourceGroup obj) {
            String groupName = ReconcilerUtil.getName(obj);
            this.queue.add(buildRequest(groupName));
        }

        @Override
        public void onUpdate(V1ResourceGroup oldObj, V1ResourceGroup newObj) {
            String groupName = ReconcilerUtil.getName(newObj);
            this.queue.add(buildRequest(groupName));
        }

        @Override
        public void onDelete(V1ResourceGroup obj, boolean deletedFinalStateUnknown) {
        }

        private Request buildRequest(String groupName) {
            return new Request(this.clusterRoleNameUtil.buildClusterRoleName(groupName));
        }

    }

    private WorkQueue<Request> queue;
    private ClusterRoleNameUtil clusterRoleNameUtil;

    public ResourceGroupWatch(WorkQueue<Request> queue, ClusterRoleNameUtil clusterRoleNameUtil) {
        this.queue = queue;
        this.clusterRoleNameUtil = clusterRoleNameUtil;
    }

    @Override
    public Class<V1ResourceGroup> getResourceClass() {
        return V1ResourceGroup.class;
    }

    @Override
    public ResourceEventHandler<V1ResourceGroup> getResourceEventHandler() {
        return new EventHandler(this.queue, this.clusterRoleNameUtil);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
