package io.ten1010.groupcontroller.configuration;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.groupcontroller.controller.cluster.clusterrole.ClusterRoleControllerFactory;
import io.ten1010.groupcontroller.controller.cluster.clusterrolebinding.ClusterRoleBindingControllerFactory;
import io.ten1010.groupcontroller.controller.cluster.node.NodeControllerFactory;
import io.ten1010.groupcontroller.controller.cluster.role.RoleControllerFactory;
import io.ten1010.groupcontroller.controller.cluster.rolebinding.RoleBindingControllerFactory;
import io.ten1010.groupcontroller.core.K8sApis;
import io.ten1010.groupcontroller.model.V1Beta1ResourceGroup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClusterControllerConfiguration {

    @Bean
    public Controller nodeController(
            SharedInformerFactory sharedInformerFactory,
            K8sApis k8sApis,
            EventRecorder eventRecorder) {
        Indexer<V1Node> nodeIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Node.class)
                .getIndexer();
        Indexer<V1Beta1ResourceGroup> groupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Beta1ResourceGroup.class)
                .getIndexer();
        return new NodeControllerFactory(sharedInformerFactory, nodeIndexer, groupIndexer, k8sApis, eventRecorder)
                .create();
    }

    @Bean
    public Controller roleController(
            SharedInformerFactory sharedInformerFactory,
            K8sApis k8sApis) {
        Indexer<V1Role> roleIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Role.class)
                .getIndexer();
        Indexer<V1Namespace> namespaceIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Namespace.class)
                .getIndexer();
        Indexer<V1Beta1ResourceGroup> groupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Beta1ResourceGroup.class)
                .getIndexer();
        return new RoleControllerFactory(
                sharedInformerFactory,
                namespaceIndexer,
                groupIndexer,
                roleIndexer,
                k8sApis).create();
    }

    @Bean
    public Controller roleBindingController(
            SharedInformerFactory sharedInformerFactory,
            K8sApis k8sApis) {
        Indexer<V1RoleBinding> roleBindingIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1RoleBinding.class)
                .getIndexer();
        Indexer<V1Role> roleIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Role.class)
                .getIndexer();
        Indexer<V1Namespace> namespaceIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Namespace.class)
                .getIndexer();
        Indexer<V1Beta1ResourceGroup> groupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Beta1ResourceGroup.class)
                .getIndexer();
        return new RoleBindingControllerFactory(
                sharedInformerFactory,
                namespaceIndexer,
                groupIndexer,
                roleBindingIndexer,
                roleIndexer,
                k8sApis).create();
    }

    @Bean
    public Controller clusterRoleController(
            SharedInformerFactory sharedInformerFactory,
            K8sApis k8sApis) {
        Indexer<V1ClusterRole> clusterRoleIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ClusterRole.class)
                .getIndexer();
        Indexer<V1Beta1ResourceGroup> groupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Beta1ResourceGroup.class)
                .getIndexer();
        return new ClusterRoleControllerFactory(
                sharedInformerFactory,
                groupIndexer,
                clusterRoleIndexer,
                k8sApis).create();
    }

    @Bean
    public Controller clusterRoleBindingController(
            SharedInformerFactory sharedInformerFactory,
            K8sApis k8sApis) {
        Indexer<V1ClusterRoleBinding> clusterRoleBindingIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ClusterRoleBinding.class)
                .getIndexer();
        Indexer<V1ClusterRole> clusterRoleIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ClusterRole.class)
                .getIndexer();
        Indexer<V1Beta1ResourceGroup> groupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Beta1ResourceGroup.class)
                .getIndexer();
        return new ClusterRoleBindingControllerFactory(
                sharedInformerFactory,
                groupIndexer,
                clusterRoleBindingIndexer,
                clusterRoleIndexer,
                k8sApis).create();
    }

}
