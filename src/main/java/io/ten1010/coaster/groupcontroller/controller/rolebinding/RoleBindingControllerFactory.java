package io.ten1010.coaster.groupcontroller.controller.rolebinding;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

public class RoleBindingControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1Namespace> namespaceIndexer;
    private Indexer<V1ResourceGroup> groupIndexer;
    private Indexer<V1RoleBinding> roleBindingIndexer;
    private Indexer<V1Role> roleIndexer;
    private RbacAuthorizationV1Api rbacAuthorizationV1Api;
    private EventRecorder eventRecorder;

    public RoleBindingControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1Namespace> namespaceIndexer,
            Indexer<V1ResourceGroup> groupIndexer,
            Indexer<V1RoleBinding> roleBindingIndexer,
            Indexer<V1Role> roleIndexer,
            RbacAuthorizationV1Api rbacAuthorizationV1Api,
            EventRecorder eventRecorder) {
        this.informerFactory = informerFactory;
        this.namespaceIndexer = namespaceIndexer;
        this.groupIndexer = groupIndexer;
        this.roleBindingIndexer = roleBindingIndexer;
        this.roleIndexer = roleIndexer;
        this.rbacAuthorizationV1Api = rbacAuthorizationV1Api;
        this.eventRecorder = eventRecorder;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .watch(workQueue -> new ResourceGroupWatch(workQueue))
                .watch(workQueue -> new RoleBindingWatch(workQueue))
                .watch(workQueue -> new NamespaceWatch(workQueue, this.groupIndexer, this.eventRecorder))
                .withReconciler(new RoleBindingReconciler(
                        this.namespaceIndexer,
                        this.groupIndexer,
                        this.roleBindingIndexer,
                        this.roleIndexer,
                        this.rbacAuthorizationV1Api))
                .build();
    }

}
