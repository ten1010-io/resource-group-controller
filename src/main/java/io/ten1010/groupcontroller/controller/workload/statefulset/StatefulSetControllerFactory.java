package io.ten1010.groupcontroller.controller.workload.statefulset;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.ten1010.groupcontroller.controller.Reconciliation;
import io.ten1010.groupcontroller.core.K8sApis;

public class StatefulSetControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1StatefulSet> statefulSetIndexer;
    private Reconciliation reconciliation;
    private K8sApis k8sApis;

    public StatefulSetControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1StatefulSet> statefulSetIndexer,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        this.informerFactory = informerFactory;
        this.statefulSetIndexer = statefulSetIndexer;
        this.reconciliation = reconciliation;
        this.k8sApis = k8sApis;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .withName("stateful-set-controller")
                .withWorkerCount(1)
                .watch(workQueue -> new ResourceGroupWatch(workQueue, this.statefulSetIndexer))
                .watch(StatefulSetWatch::new)
                .withReconciler(new StatefulSetReconciler(this.statefulSetIndexer, this.reconciliation, this.k8sApis.getAppsV1Api()))
                .build();
    }

}
