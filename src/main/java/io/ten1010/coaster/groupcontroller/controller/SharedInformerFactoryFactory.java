package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.ten1010.coaster.groupcontroller.core.IndexNameConstants;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.model.DaemonSetReference;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroupList;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroupSpec;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SharedInformerFactoryFactory {

    public static long RESYNC_PERIOD_IN_MILLIS = 30000;

    private static Map<String, Function<V1ResourceGroup, List<String>>> byNamespaceNameToGroupObject() {
        Map<String, Function<V1ResourceGroup, List<String>>> indexers = new HashMap<>();
        indexers.put(IndexNameConstants.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, v1ResourceGroup -> {
            V1ResourceGroupSpec spec = v1ResourceGroup.getSpec();
            Objects.requireNonNull(spec);

            return spec.getNamespaces();
        });

        return indexers;
    }

    private static Map<String, Function<V1Pod, List<String>>> byNamespaceNameToPodObject() {
        Map<String, Function<V1Pod, List<String>>> indexers = new HashMap<>();
        indexers.put(IndexNameConstants.BY_NAMESPACE_NAME_TO_POD_OBJECT, v1Pod -> {
            V1ObjectMeta meta = v1Pod.getMetadata();
            Objects.requireNonNull(meta);
            Objects.requireNonNull(meta.getNamespace());

            return List.of(meta.getNamespace());
        });

        return indexers;
    }

    private static Map<String, Function<V1ResourceGroup, List<String>>> byNodeNameToGroupObject() {
        Map<String, Function<V1ResourceGroup, List<String>>> indexers = new HashMap<>();
        indexers.put(IndexNameConstants.BY_NODE_NAME_TO_GROUP_OBJECT, group -> {
            V1ResourceGroupSpec spec = group.getSpec();
            Objects.requireNonNull(spec);

            return spec.getNodes();
        });

        return indexers;
    }

    private static Map<String, Function<V1ResourceGroup, List<String>>> byDaemonSetKeyToGroupObject() {
        Map<String, Function<V1ResourceGroup, List<String>>> indexers = new HashMap<>();
        indexers.put(IndexNameConstants.BY_DAEMON_SET_KEY_TO_GROUP_OBJECT, group -> {
            V1ResourceGroupSpec spec = group.getSpec();
            Objects.requireNonNull(spec);
            if (spec.getExceptions() == null) {
                return new ArrayList<>();
            }
            List<DaemonSetReference> daemonSets = spec.getExceptions().getDaemonSets();

            return daemonSets.stream()
                    .filter(e -> e.getNamespace() != null && e.getName() != null)
                    .map(e -> KeyUtil.buildKey(e.getNamespace(), e.getName()))
                    .collect(Collectors.toList());
        });

        return indexers;
    }

    private ApiClient apiClient;
    private GenericKubernetesApi<V1ResourceGroup, V1ResourceGroupList> groupApi;
    private GenericKubernetesApi<V1Pod, V1PodList> podApi;
    private GenericKubernetesApi<V1Node, V1NodeList> nodeApi;
    private GenericKubernetesApi<V1Role, V1RoleList> roleApi;
    private GenericKubernetesApi<V1RoleBinding, V1RoleBindingList> roleBindingApi;
    private GenericKubernetesApi<V1ClusterRole, V1ClusterRoleList> clusterRoleApi;
    private GenericKubernetesApi<V1ClusterRoleBinding, V1ClusterRoleBindingList> clusterRoleBindingApi;
    private GenericKubernetesApi<V1Namespace, V1NamespaceList> namespaceApi;
    private GenericKubernetesApi<V1DaemonSet, V1DaemonSetList> daemonSetApi;

    public SharedInformerFactoryFactory(
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
        this.apiClient = apiClient;
        this.groupApi = groupApi;
        this.podApi = podApi;
        this.nodeApi = nodeApi;
        this.roleApi = roleApi;
        this.roleBindingApi = roleBindingApi;
        this.clusterRoleApi = clusterRoleApi;
        this.clusterRoleBindingApi = clusterRoleBindingApi;
        this.namespaceApi = namespaceApi;
        this.daemonSetApi = daemonSetApi;
    }

    public SharedInformerFactory create() {
        SharedInformerFactory informerFactory = new SharedInformerFactory(this.apiClient);
        SharedIndexInformer<V1ResourceGroup> groupInformer = informerFactory.sharedIndexInformerFor(this.groupApi, V1ResourceGroup.class, RESYNC_PERIOD_IN_MILLIS);
        groupInformer.addIndexers(byNamespaceNameToGroupObject());
        SharedIndexInformer<V1Pod> podInformer = informerFactory.sharedIndexInformerFor(this.podApi, V1Pod.class, RESYNC_PERIOD_IN_MILLIS);
        podInformer.addIndexers(byNamespaceNameToPodObject());
        SharedIndexInformer<V1Node> nodeInformer = informerFactory.sharedIndexInformerFor(this.nodeApi, V1Node.class, RESYNC_PERIOD_IN_MILLIS);
        groupInformer.addIndexers(byNodeNameToGroupObject());
        SharedIndexInformer<V1Role> roleInformer = informerFactory.sharedIndexInformerFor(this.roleApi, V1Role.class, RESYNC_PERIOD_IN_MILLIS);
        SharedIndexInformer<V1RoleBinding> roleBindingInformer = informerFactory.sharedIndexInformerFor(this.roleBindingApi, V1RoleBinding.class, RESYNC_PERIOD_IN_MILLIS);
        SharedIndexInformer<V1ClusterRole> clusterRoleInformer = informerFactory.sharedIndexInformerFor(this.clusterRoleApi, V1ClusterRole.class, RESYNC_PERIOD_IN_MILLIS);
        SharedIndexInformer<V1ClusterRoleBinding> clusterRoleBindingInformer = informerFactory.sharedIndexInformerFor(this.clusterRoleBindingApi, V1ClusterRoleBinding.class, RESYNC_PERIOD_IN_MILLIS);
        SharedIndexInformer<V1Namespace> namespaceInformer = informerFactory.sharedIndexInformerFor(this.namespaceApi, V1Namespace.class, RESYNC_PERIOD_IN_MILLIS);
        SharedIndexInformer<V1DaemonSet> daemonSetInformer = informerFactory.sharedIndexInformerFor(this.daemonSetApi, V1DaemonSet.class, RESYNC_PERIOD_IN_MILLIS);
        groupInformer.addIndexers(byDaemonSetKeyToGroupObject());

        return informerFactory;
    }

}
