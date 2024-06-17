package io.ten1010.groupcontroller.controller.workload.daemonset;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.ten1010.groupcontroller.controller.Reconciliation;
import io.ten1010.groupcontroller.core.K8sApis;

public class DaemonSetControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1DaemonSet> daemonSetIndexer;
    private Reconciliation reconciliation;
    private K8sApis k8sApis;

    public DaemonSetControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1DaemonSet> daemonSetIndexer,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        this.informerFactory = informerFactory;
        this.daemonSetIndexer = daemonSetIndexer;
        this.reconciliation = reconciliation;
        this.k8sApis = k8sApis;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .withName("daemon-set-controller")
                .withWorkerCount(1)
                .watch(workQueue -> new ResourceGroupWatch(workQueue, this.daemonSetIndexer))
                .watch(DaemonSetWatch::new)
                .withReconciler(new DaemonSetReconciler(this.daemonSetIndexer, this.reconciliation, this.k8sApis.getAppsV1Api()))
                .build();
    }

}
