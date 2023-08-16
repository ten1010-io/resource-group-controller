package io.ten1010.coaster.groupcontroller.configuration;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerManager;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.extended.event.legacy.LegacyEventBroadcaster;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.ten1010.coaster.groupcontroller.controller.GroupResolver;
import io.ten1010.coaster.groupcontroller.controller.SharedInformerFactoryFactory;
import io.ten1010.coaster.groupcontroller.controller.clusterrole.ClusterRoleControllerFactory;
import io.ten1010.coaster.groupcontroller.controller.clusterrolebinding.ClusterRoleBindingControllerFactory;
import io.ten1010.coaster.groupcontroller.controller.daemonset.DaemonSetControllerFactory;
import io.ten1010.coaster.groupcontroller.controller.node.NodeControllerFactory;
import io.ten1010.coaster.groupcontroller.controller.pod.PodControllerFactory;
import io.ten1010.coaster.groupcontroller.controller.role.RoleControllerFactory;
import io.ten1010.coaster.groupcontroller.controller.rolebinding.RoleBindingControllerFactory;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroupList;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Configuration
public class ControllerConfiguration {

    public static final String COMPONENT_NAME = "resource-group-controller";

    @Profile("enable-controller-runner")
    @Bean
    public CommandLineRunner controllerRunner(ControllerManager controllerManager) {
        return args -> controllerManager.run();
    }

    @Bean
    public ControllerManager controllerManager(
            SharedInformerFactory sharedInformerFactory,
            Controller podController,
            Controller nodeController,
            Controller roleController,
            Controller roleBindingController,
            Controller clusterRoleController,
            Controller clusterRoleBindingController,
            Controller daemonSetController) {
        return ControllerBuilder.controllerManagerBuilder(sharedInformerFactory)
                .addController(podController)
                .addController(nodeController)
                .addController(roleController)
                .addController(roleBindingController)
                .addController(clusterRoleController)
                .addController(clusterRoleBindingController)
                .addController(daemonSetController)
                .build();
    }

    @Bean
    public SharedInformerFactory sharedInformerFactory(
            ApiClient apiClient,
            GenericKubernetesApi<V1ResourceGroup, V1ResourceGroupList> groupApi,
            GenericKubernetesApi<V1Pod, V1PodList> podApi,
            GenericKubernetesApi<V1Node, V1NodeList> nodeApi,
            GenericKubernetesApi<V1Role, V1RoleList> roleApi,
            GenericKubernetesApi<V1RoleBinding, V1RoleBindingList> roleBindingApi,
            GenericKubernetesApi<V1ClusterRole, V1ClusterRoleList> clusterRoleApi,
            GenericKubernetesApi<V1ClusterRoleBinding, V1ClusterRoleBindingList> clusterRoleBindingApi,
            GenericKubernetesApi<V1Namespace, V1NamespaceList> namespaceApi,
            GenericKubernetesApi<V1DaemonSet, V1DaemonSetList> daemonSetApi) {
        return new SharedInformerFactoryFactory(
                apiClient,
                groupApi,
                podApi,
                nodeApi,
                roleApi,
                roleBindingApi,
                clusterRoleApi,
                clusterRoleBindingApi,
                namespaceApi,
                daemonSetApi)
                .create();
    }

    @Bean
    public Controller podController(
            SharedInformerFactory sharedInformerFactory,
            GroupResolver groupResolver,
            CoreV1Api coreV1Api) {
        Indexer<V1Pod> podIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Pod.class)
                .getIndexer();

        return new PodControllerFactory(sharedInformerFactory, podIndexer, groupResolver, coreV1Api)
                .create();
    }

    @Bean
    public Controller nodeController(
            SharedInformerFactory sharedInformerFactory,
            CoreV1Api coreV1Api,
            EventRecorder eventRecorder) {
        Indexer<V1Node> nodeIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Node.class)
                .getIndexer();
        Indexer<V1ResourceGroup> groupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ResourceGroup.class)
                .getIndexer();

        return new NodeControllerFactory(sharedInformerFactory, nodeIndexer, groupIndexer, coreV1Api, eventRecorder)
                .create();
    }

    @Bean
    public Controller roleController(
            SharedInformerFactory sharedInformerFactory,
            RbacAuthorizationV1Api rbacAuthorizationV1Api) {
        Indexer<V1Role> roleIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Role.class)
                .getIndexer();
        Indexer<V1Namespace> namespaceIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Namespace.class)
                .getIndexer();
        Indexer<V1ResourceGroup> groupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ResourceGroup.class)
                .getIndexer();

        return new RoleControllerFactory(
                sharedInformerFactory,
                namespaceIndexer,
                groupIndexer,
                roleIndexer,
                rbacAuthorizationV1Api).create();
    }

    @Bean
    public Controller roleBindingController(
            SharedInformerFactory sharedInformerFactory,
            RbacAuthorizationV1Api rbacAuthorizationV1Api) {
        Indexer<V1RoleBinding> roleBindingIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1RoleBinding.class)
                .getIndexer();
        Indexer<V1Role> roleIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Role.class)
                .getIndexer();
        Indexer<V1Namespace> namespaceIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Namespace.class)
                .getIndexer();
        Indexer<V1ResourceGroup> groupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ResourceGroup.class)
                .getIndexer();

        return new RoleBindingControllerFactory(
                sharedInformerFactory,
                namespaceIndexer,
                groupIndexer,
                roleBindingIndexer,
                roleIndexer,
                rbacAuthorizationV1Api).create();
    }

    @Bean
    public Controller clusterRoleController(
            SharedInformerFactory sharedInformerFactory,
            RbacAuthorizationV1Api rbacAuthorizationV1Api) {
        Indexer<V1ClusterRole> clusterRoleIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ClusterRole.class)
                .getIndexer();
        Indexer<V1ResourceGroup> groupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ResourceGroup.class)
                .getIndexer();

        return new ClusterRoleControllerFactory(
                sharedInformerFactory,
                groupIndexer,
                clusterRoleIndexer,
                rbacAuthorizationV1Api).create();
    }

    @Bean
    public Controller clusterRoleBindingController(
            SharedInformerFactory sharedInformerFactory,
            RbacAuthorizationV1Api rbacAuthorizationV1Api) {
        Indexer<V1ClusterRoleBinding> clusterRoleBindingIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ClusterRoleBinding.class)
                .getIndexer();
        Indexer<V1ClusterRole> clusterRoleIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ClusterRole.class)
                .getIndexer();
        Indexer<V1ResourceGroup> groupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ResourceGroup.class)
                .getIndexer();

        return new ClusterRoleBindingControllerFactory(
                sharedInformerFactory,
                groupIndexer,
                clusterRoleBindingIndexer,
                clusterRoleIndexer,
                rbacAuthorizationV1Api).create();
    }

    @Bean
    public Controller daemonSetController(
            SharedInformerFactory sharedInformerFactory,
            GroupResolver groupResolver,
            AppsV1Api appsV1Api) {
        Indexer<V1DaemonSet> daemonSetIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1DaemonSet.class)
                .getIndexer();

        return new DaemonSetControllerFactory(sharedInformerFactory, daemonSetIndexer, groupResolver, appsV1Api)
                .create();
    }

    @Bean
    public GroupResolver groupResolver(SharedInformerFactory sharedInformerFactory) {
        Indexer<V1ResourceGroup> groupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ResourceGroup.class)
                .getIndexer();

        return new GroupResolver(groupIndexer);
    }

    @Bean(initMethod = "startRecording", destroyMethod = "shutdown")
    public LegacyEventBroadcaster broadcaster(CoreV1Api coreV1Api) {
        return new LegacyEventBroadcaster(coreV1Api);
    }

    @Bean
    public EventRecorder eventRecorder(LegacyEventBroadcaster broadcaster) {
        V1EventSource eventSource = new V1EventSource();
        eventSource.host(getHostName()).component(COMPONENT_NAME);

        return broadcaster.newRecorder(eventSource);
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

}
