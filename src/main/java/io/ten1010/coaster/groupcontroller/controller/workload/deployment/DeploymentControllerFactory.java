package io.ten1010.coaster.groupcontroller.controller.workload.deployment;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.core.K8sApis;

public class DeploymentControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1Deployment> deploymentIndexer;
    private Reconciliation reconciliation;
    private K8sApis k8sApis;

    public DeploymentControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1Deployment> deploymentIndexer,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        this.informerFactory = informerFactory;
        this.deploymentIndexer = deploymentIndexer;
        this.reconciliation = reconciliation;
        this.k8sApis = k8sApis;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .withName("deployment-controller")
                .withWorkerCount(1)
                .watch(workQueue -> new ResourceGroupWatch(workQueue, this.deploymentIndexer))
                .watch(DeploymentWatch::new)
                .withReconciler(new DeploymentReconciler(this.deploymentIndexer, this.reconciliation, this.k8sApis.getAppsV1Api()))
                .build();
    }

}
