package io.ten1010.coaster.groupcontroller.controller.workload.replicationcontroller;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1ReplicationController;
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.core.K8sApis;

public class ReplicationControllerControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1ReplicationController> replicationControllerIndexer;
    private Reconciliation reconciliation;
    private K8sApis k8sApis;

    public ReplicationControllerControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1ReplicationController> replicationControllerIndexer,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        this.informerFactory = informerFactory;
        this.replicationControllerIndexer = replicationControllerIndexer;
        this.reconciliation = reconciliation;
        this.k8sApis = k8sApis;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .withName("replication-controller-controller")
                .withWorkerCount(1)
                .watch(workQueue -> new ResourceGroupWatch(workQueue, this.replicationControllerIndexer))
                .watch(ReplicationControllerWatch::new)
                .withReconciler(new ReplicationControllerReconciler(this.replicationControllerIndexer, this.reconciliation, this.k8sApis.getCoreV1Api()))
                .build();
    }

}
