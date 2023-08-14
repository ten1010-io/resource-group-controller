package io.ten1010.coaster.groupcontroller.controller.daemonset;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.ten1010.coaster.groupcontroller.controller.GroupResolver;

public class DaemonSetControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1DaemonSet> daemonSetIndexer;
    private GroupResolver groupResolver;
    private AppsV1Api appsV1Api;

    public DaemonSetControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1DaemonSet> daemonSetIndexer,
            GroupResolver groupResolver,
            AppsV1Api appsV1Api) {
        this.informerFactory = informerFactory;
        this.daemonSetIndexer = daemonSetIndexer;
        this.groupResolver = groupResolver;
        this.appsV1Api = appsV1Api;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .watch(ResourceGroupWatch::new)
                .watch(DaemonSetWatch::new)
                .withReconciler(new DaemonSetReconciler(this.daemonSetIndexer, this.groupResolver, this.appsV1Api))
                .build();
    }

}
