package io.ten1010.coaster.groupcontroller.controller.clusterrolebinding;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.ten1010.coaster.groupcontroller.controller.clusterrole.ClusterRoleNameUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

public class ClusterRoleBindingControllerFactory {

    private SharedInformerFactory informerFactory;
    private ClusterRoleNameUtil clusterRoleNameUtil;
    private Indexer<V1ResourceGroup> groupIndexer;
    private Indexer<V1ClusterRoleBinding> clusterRoleBindingIndexer;
    private Indexer<V1ClusterRole> clusterRoleIndexer;
    private RbacAuthorizationV1Api rbacAuthorizationV1Api;

    public ClusterRoleBindingControllerFactory(
            SharedInformerFactory informerFactory,
            ClusterRoleNameUtil clusterRoleNameUtil,
            Indexer<V1ResourceGroup> groupIndexer,
            Indexer<V1ClusterRoleBinding> clusterRoleBindingIndexer,
            Indexer<V1ClusterRole> clusterRoleIndexer,
            RbacAuthorizationV1Api rbacAuthorizationV1Api) {
        this.informerFactory = informerFactory;
        this.clusterRoleNameUtil = clusterRoleNameUtil;
        this.groupIndexer = groupIndexer;
        this.clusterRoleBindingIndexer = clusterRoleBindingIndexer;
        this.clusterRoleIndexer = clusterRoleIndexer;
        this.rbacAuthorizationV1Api = rbacAuthorizationV1Api;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .watch(workQueue -> new ResourceGroupWatch(workQueue, this.clusterRoleNameUtil))
                .watch(workQueue -> new ClusterRoleBindingWatch(workQueue, this.clusterRoleNameUtil))
                .withReconciler(new ClusterRoleBindingReconciler(
                        this.clusterRoleNameUtil,
                        this.groupIndexer,
                        this.clusterRoleBindingIndexer,
                        this.clusterRoleIndexer,
                        this.rbacAuthorizationV1Api))
                .build();
    }

}
