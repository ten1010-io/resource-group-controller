package io.ten1010.groupcontroller.controller.workload.job;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Job;
import io.ten1010.groupcontroller.controller.Reconciliation;
import io.ten1010.groupcontroller.core.K8sApis;

public class JobControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1Job> jobIndexer;
    private Reconciliation reconciliation;
    private K8sApis k8sApis;

    public JobControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1Job> jobIndexer,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        this.informerFactory = informerFactory;
        this.jobIndexer = jobIndexer;
        this.reconciliation = reconciliation;
        this.k8sApis = k8sApis;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .withName("job-controller")
                .withWorkerCount(1)
                .watch(workQueue -> new ResourceGroupWatch(workQueue, this.jobIndexer))
                .watch(JobWatch::new)
                .withReconciler(new JobReconciler(this.jobIndexer, this.reconciliation, this.k8sApis.getBatchV1Api()))
                .build();
    }

}
