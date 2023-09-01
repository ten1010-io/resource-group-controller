package io.ten1010.coaster.groupcontroller.controller.cluster.clusterrole;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.ten1010.coaster.groupcontroller.core.K8sApis;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

public class ClusterRoleControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1ResourceGroup> groupIndexer;
    private Indexer<V1ClusterRole> clusterRoleIndexer;
    private K8sApis k8sApis;

    public ClusterRoleControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1ResourceGroup> groupIndexer,
            Indexer<V1ClusterRole> clusterRoleIndexer,
            K8sApis k8sApis) {
        this.informerFactory = informerFactory;
        this.groupIndexer = groupIndexer;
        this.clusterRoleIndexer = clusterRoleIndexer;
        this.k8sApis = k8sApis;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .withName("cluster-role-controller")
                .withWorkerCount(1)
                .watch(workQueue -> new ResourceGroupWatch(workQueue))
                .watch(workQueue -> new ClusterRoleWatch(workQueue))
                .withReconciler(new ClusterRoleReconciler(this.groupIndexer, this.clusterRoleIndexer, this.k8sApis.getRbacAuthorizationV1Api()))
                .build();
    }

}
