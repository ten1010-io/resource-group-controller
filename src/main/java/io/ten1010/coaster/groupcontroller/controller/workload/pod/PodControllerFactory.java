package io.ten1010.coaster.groupcontroller.controller.workload.pod;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.ten1010.coaster.groupcontroller.controller.GroupResolver;

public class PodControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1Pod> podIndexer;
    private GroupResolver groupResolver;
    private CoreV1Api coreV1Api;

    public PodControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1Pod> podIndexer,
            GroupResolver groupResolver,
            CoreV1Api coreV1Api) {
        this.informerFactory = informerFactory;
        this.podIndexer = podIndexer;
        this.groupResolver = groupResolver;
        this.coreV1Api = coreV1Api;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .withName("pod-controller")
                .withWorkerCount(1)
                .watch(workQueue -> new ResourceGroupWatch(workQueue, this.podIndexer))
                .watch(PodWatch::new)
                .withReconciler(new PodReconciler(this.podIndexer, this.groupResolver, this.coreV1Api))
                .build();
    }

}
