package io.ten1010.coaster.groupcontroller.controller.clusterrole;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

public class ClusterRoleControllerFactory {

    private SharedInformerFactory informerFactory;
    private ClusterRoleNameUtil clusterRoleNameUtil;
    private Indexer<V1ResourceGroup> groupIndexer;
    private Indexer<V1ClusterRole> clusterRoleIndexer;
    private RbacAuthorizationV1Api rbacAuthorizationV1Api;

    public ClusterRoleControllerFactory(
            SharedInformerFactory informerFactory,
            ClusterRoleNameUtil clusterRoleNameUtil,
            Indexer<V1ResourceGroup> groupIndexer,
            Indexer<V1ClusterRole> clusterRoleIndexer,
            RbacAuthorizationV1Api rbacAuthorizationV1Api) {
        this.informerFactory = informerFactory;
        this.clusterRoleNameUtil = clusterRoleNameUtil;
        this.groupIndexer = groupIndexer;
        this.clusterRoleIndexer = clusterRoleIndexer;
        this.rbacAuthorizationV1Api = rbacAuthorizationV1Api;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .watch(workQueue -> new ResourceGroupWatch(workQueue, this.clusterRoleNameUtil))
                .watch(workQueue -> new ClusterRoleWatch(workQueue, this.clusterRoleNameUtil))
                .withReconciler(new ClusterRoleReconciler(this.clusterRoleNameUtil, this.groupIndexer, this.clusterRoleIndexer, this.rbacAuthorizationV1Api))
                .build();
    }

}
