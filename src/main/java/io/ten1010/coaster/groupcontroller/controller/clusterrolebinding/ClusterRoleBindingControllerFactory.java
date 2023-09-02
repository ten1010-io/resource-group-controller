package io.ten1010.coaster.groupcontroller.controller.clusterrolebinding;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

public class ClusterRoleBindingControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1ResourceGroup> groupIndexer;
    private Indexer<V1ClusterRoleBinding> clusterRoleBindingIndexer;
    private Indexer<V1ClusterRole> clusterRoleIndexer;
    private RbacAuthorizationV1Api rbacAuthorizationV1Api;

    public ClusterRoleBindingControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1ResourceGroup> groupIndexer,
            Indexer<V1ClusterRoleBinding> clusterRoleBindingIndexer,
            Indexer<V1ClusterRole> clusterRoleIndexer,
            RbacAuthorizationV1Api rbacAuthorizationV1Api) {
        this.informerFactory = informerFactory;
        this.groupIndexer = groupIndexer;
        this.clusterRoleBindingIndexer = clusterRoleBindingIndexer;
        this.clusterRoleIndexer = clusterRoleIndexer;
        this.rbacAuthorizationV1Api = rbacAuthorizationV1Api;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .withName("cluster-role-binding-controller")
                .withWorkerCount(1)
                .watch(ResourceGroupWatch::new)
                .watch(ClusterRoleBindingWatch::new)
                .withReconciler(new ClusterRoleBindingReconciler(
                        this.groupIndexer,
                        this.clusterRoleBindingIndexer,
                        this.clusterRoleIndexer,
                        this.rbacAuthorizationV1Api))
                .build();
    }

}
