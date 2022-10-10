package io.ten1010.coaster.groupcontroller.controller.role;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1Role;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

public class RoleControllerFactory {

    private SharedInformerFactory informerFactory;
    private RoleNameUtil roleNameUtil;
    private Indexer<V1Namespace> namespaceIndexer;
    private Indexer<V1ResourceGroup> groupIndexer;
    private Indexer<V1Role> roleIndexer;
    private RbacAuthorizationV1Api rbacAuthorizationV1Api;
    private EventRecorder eventRecorder;

    public RoleControllerFactory(
            SharedInformerFactory informerFactory,
            RoleNameUtil roleNameUtil,
            Indexer<V1Namespace> namespaceIndexer,
            Indexer<V1ResourceGroup> groupIndexer,
            Indexer<V1Role> roleIndexer,
            RbacAuthorizationV1Api rbacAuthorizationV1Api,
            EventRecorder eventRecorder) {
        this.informerFactory = informerFactory;
        this.roleNameUtil = roleNameUtil;
        this.namespaceIndexer = namespaceIndexer;
        this.groupIndexer = groupIndexer;
        this.roleIndexer = roleIndexer;
        this.rbacAuthorizationV1Api = rbacAuthorizationV1Api;
        this.eventRecorder = eventRecorder;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .watch(workQueue -> new ResourceGroupWatch(workQueue, this.roleNameUtil))
                .watch(workQueue -> new RoleWatch(workQueue, this.roleNameUtil))
                .watch(workQueue -> new NamespaceWatch(workQueue, this.groupIndexer, this.roleNameUtil, this.eventRecorder))
                .withReconciler(new RoleReconciler(this.namespaceIndexer, this.groupIndexer, this.roleNameUtil, this.roleIndexer, this.rbacAuthorizationV1Api))
                .build();
    }

}
