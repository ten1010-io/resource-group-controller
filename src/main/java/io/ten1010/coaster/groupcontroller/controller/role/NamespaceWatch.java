package io.ten1010.coaster.groupcontroller.controller.role;

import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.event.EventType;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.ten1010.coaster.groupcontroller.controller.ReconcilerUtil;
import io.ten1010.coaster.groupcontroller.core.EventConstants;
import io.ten1010.coaster.groupcontroller.core.IndexNameConstants;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

import java.time.Duration;
import java.util.List;

public class NamespaceWatch implements ControllerWatch<V1Namespace> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1Namespace> {

        private static final String MSG_NAMESPACE_BELONGS_TO_MULTIPLE_GROUPS = "Namespace [%s] belongs to multiple groups";

        private WorkQueue<Request> queue;
        private Indexer<V1ResourceGroup> groupIndexer;
        private RoleNameUtil roleNameUtil;
        private EventRecorder eventRecorder;

        public EventHandler(
                WorkQueue<Request> queue,
                Indexer<V1ResourceGroup> groupIndexer,
                RoleNameUtil roleNameUtil,
                EventRecorder eventRecorder) {
            this.queue = queue;
            this.groupIndexer = groupIndexer;
            this.roleNameUtil = roleNameUtil;
            this.eventRecorder = eventRecorder;
        }

        @Override
        public void onAdd(V1Namespace obj) {
            List<V1ResourceGroup> groups = this.groupIndexer.byIndex(IndexNameConstants.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, ReconcilerUtil.getName(obj));
            if (groups.size() == 0) {
                return;
            }
            if (groups.size() > 1) {
                for (V1ResourceGroup g : groups) {
                    this.eventRecorder.event(
                            g,
                            EventType.Warning,
                            EventConstants.REASON_NAMESPACE_CONFLICT, MSG_NAMESPACE_BELONGS_TO_MULTIPLE_GROUPS,
                            ReconcilerUtil.getName(obj));
                }
            }
            String roleName = this.roleNameUtil.buildRoleName(ReconcilerUtil.getName(groups.get(0)));
            Request request = new Request(ReconcilerUtil.getName(obj), roleName);
            this.queue.add(request);
        }

        @Override
        public void onUpdate(V1Namespace oldObj, V1Namespace newObj) {
        }

        @Override
        public void onDelete(V1Namespace obj, boolean deletedFinalStateUnknown) {
        }

    }

    private WorkQueue<Request> queue;
    private Indexer<V1ResourceGroup> groupIndexer;
    private RoleNameUtil roleNameUtil;
    private EventRecorder eventRecorder;

    public NamespaceWatch(
            WorkQueue<Request> queue,
            Indexer<V1ResourceGroup> groupIndexer,
            RoleNameUtil roleNameUtil,
            EventRecorder eventRecorder) {
        this.queue = queue;
        this.groupIndexer = groupIndexer;
        this.roleNameUtil = roleNameUtil;
        this.eventRecorder = eventRecorder;
    }

    @Override
    public Class<V1Namespace> getResourceClass() {
        return V1Namespace.class;
    }

    @Override
    public ResourceEventHandler<V1Namespace> getResourceEventHandler() {
        return new EventHandler(this.queue, this.groupIndexer, this.roleNameUtil, this.eventRecorder);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
