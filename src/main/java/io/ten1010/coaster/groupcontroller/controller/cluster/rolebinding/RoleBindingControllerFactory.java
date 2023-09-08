package io.ten1010.coaster.groupcontroller.controller.cluster.rolebinding;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.ten1010.coaster.groupcontroller.core.K8sApis;
import io.ten1010.coaster.groupcontroller.model.V1Beta1ResourceGroup;

public class RoleBindingControllerFactory {

    private SharedInformerFactory informerFactory;
    private Indexer<V1Namespace> namespaceIndexer;
    private Indexer<V1Beta1ResourceGroup> groupIndexer;
    private Indexer<V1RoleBinding> roleBindingIndexer;
    private Indexer<V1Role> roleIndexer;
    private K8sApis k8sApis;

    public RoleBindingControllerFactory(
            SharedInformerFactory informerFactory,
            Indexer<V1Namespace> namespaceIndexer,
            Indexer<V1Beta1ResourceGroup> groupIndexer,
            Indexer<V1RoleBinding> roleBindingIndexer,
            Indexer<V1Role> roleIndexer,
            K8sApis k8sApis) {
        this.informerFactory = informerFactory;
        this.namespaceIndexer = namespaceIndexer;
        this.groupIndexer = groupIndexer;
        this.roleBindingIndexer = roleBindingIndexer;
        this.roleIndexer = roleIndexer;
        this.k8sApis = k8sApis;
    }

    public Controller create() {
        return ControllerBuilder.defaultBuilder(this.informerFactory)
                .withName("role-binding-controller")
                .withWorkerCount(1)
                .watch(workQueue -> new ResourceGroupWatch(workQueue))
                .watch(workQueue -> new RoleBindingWatch(workQueue))
                .watch(workQueue -> new NamespaceWatch(workQueue, this.groupIndexer))
                .withReconciler(new RoleBindingReconciler(
                        this.namespaceIndexer,
                        this.groupIndexer,
                        this.roleBindingIndexer,
                        this.roleIndexer,
                        this.k8sApis.getRbacAuthorizationV1Api()))
                .build();
    }

}
