package io.ten1010.coaster.groupcontroller.controller.workload.cronjob;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.core.K8sApis;

public class CronJobControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1CronJob> cronJobIndexer;
    private Reconciliation reconciliation;
    private K8sApis k8sApis;

    public CronJobControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1CronJob> cronJobIndexer,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        this.informerFactory = informerFactory;
        this.cronJobIndexer = cronJobIndexer;
        this.reconciliation = reconciliation;
        this.k8sApis = k8sApis;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .withName("cron-job-controller")
                .withWorkerCount(1)
                .watch(workQueue -> new ResourceGroupWatch(workQueue, this.cronJobIndexer))
                .watch(CronJobWatch::new)
                .withReconciler(new CronJobReconciler(this.cronJobIndexer, this.reconciliation, this.k8sApis.getBatchV1Api()))
                .build();
    }

}
