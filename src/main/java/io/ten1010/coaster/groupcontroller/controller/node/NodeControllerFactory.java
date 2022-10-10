package io.ten1010.coaster.groupcontroller.controller.node;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Node;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

public class NodeControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1Node> nodeIndexer;
    private Indexer<V1ResourceGroup> groupIndexer;
    private CoreV1Api coreV1Api;
    private EventRecorder eventRecorder;

    public NodeControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1Node> nodeIndexer,
            Indexer<V1ResourceGroup> groupIndexer,
            CoreV1Api coreV1Api,
            EventRecorder eventRecorder) {
        this.informerFactory = informerFactory;
        this.nodeIndexer = nodeIndexer;
        this.groupIndexer = groupIndexer;
        this.coreV1Api = coreV1Api;
        this.eventRecorder = eventRecorder;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .watch(ResourceGroupWatch::new)
                .watch(NodeWatch::new)
                .withReconciler(new NodeReconciler(this.nodeIndexer, this.groupIndexer, this.coreV1Api, this.eventRecorder))
                .build();
    }

}
