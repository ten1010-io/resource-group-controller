package io.ten1010.coaster.groupcontroller.controller.workload.pod;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Pod;
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.core.K8sApis;

public class PodControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1Pod> podIndexer;
    private Reconciliation reconciliation;
    private K8sApis k8sApis;

    public PodControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1Pod> podIndexer,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        this.informerFactory = informerFactory;
        this.podIndexer = podIndexer;
        this.reconciliation = reconciliation;
        this.k8sApis = k8sApis;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .withName("pod-controller")
                .withWorkerCount(1)
                .watch(workQueue -> new ResourceGroupWatch(workQueue, this.podIndexer))
                .watch(PodWatch::new)
                .withReconciler(new PodReconciler(this.podIndexer, this.reconciliation, this.k8sApis.getCoreV1Api()))
                .build();
    }

}
