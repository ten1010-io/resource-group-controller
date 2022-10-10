package io.ten1010.coaster.groupcontroller.configuration;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroupList;
import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.FileReader;
import java.io.IOException;

@Configuration
@EnableConfigurationProperties({KubernetesConfiguration.KubernetesClientProperties.class})
public class KubernetesConfiguration {

    @ConfigurationProperties(prefix = "app.kubernetes.client")
    @NoArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    public static class KubernetesClientProperties {

        private boolean verifySsl = true;
        private String kubeconfigPath = "$HOME/.kube/config";

    }

    @Profile("in-cluster-kubeconfig")
    @Bean
    public ApiClient inClusterApiClient(KubernetesClientProperties kubernetesClientConfig) throws IOException {
        return ClientBuilder
                .cluster()
                .setVerifyingSsl(kubernetesClientConfig.isVerifySsl())
                .build();
    }

    @Profile("file-kubeconfig")
    @Bean
    public ApiClient fileApiClient(KubernetesClientProperties kubernetesClientConfig) throws IOException {
        return ClientBuilder
                .kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubernetesClientConfig.getKubeconfigPath())))
                .setVerifyingSsl(kubernetesClientConfig.isVerifySsl())
                .build();
    }

    @Bean
    public CoreV1Api coreV1Api(ApiClient apiClient) {
        return new CoreV1Api(apiClient);
    }

    @Bean
    public RbacAuthorizationV1Api rbacAuthorizationV1Api(ApiClient apiClient) {
        return new RbacAuthorizationV1Api(apiClient);
    }

    @Bean
    public AppsV1Api appsV1Api(ApiClient apiClient) {
        return new AppsV1Api(apiClient);
    }

    @Bean
    public GenericKubernetesApi<V1ResourceGroup, V1ResourceGroupList> resourceGroupApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(
                V1ResourceGroup.class,
                V1ResourceGroupList.class,
                "ten1010.io",
                "v1",
                "resourcegroups",
                apiClient);
    }

    @Bean
    public GenericKubernetesApi<V1Pod, V1PodList> podApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(
                V1Pod.class,
                V1PodList.class,
                "",
                "v1",
                "pods",
                apiClient);
    }

    @Bean
    public GenericKubernetesApi<V1Node, V1NodeList> nodeApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(
                V1Node.class,
                V1NodeList.class,
                "",
                "v1",
                "nodes",
                apiClient);
    }

    @Bean
    public GenericKubernetesApi<V1Role, V1RoleList> roleApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(
                V1Role.class,
                V1RoleList.class,
                "rbac.authorization.k8s.io",
                "v1",
                "roles",
                apiClient);
    }

    @Bean
    public GenericKubernetesApi<V1RoleBinding, V1RoleBindingList> roleBindingApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(
                V1RoleBinding.class,
                V1RoleBindingList.class,
                "rbac.authorization.k8s.io",
                "v1",
                "rolebindings",
                apiClient);
    }

    @Bean
    public GenericKubernetesApi<V1ClusterRole, V1ClusterRoleList> clusterRoleApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(
                V1ClusterRole.class,
                V1ClusterRoleList.class,
                "rbac.authorization.k8s.io",
                "v1",
                "clusterroles",
                apiClient);
    }

    @Bean
    public GenericKubernetesApi<V1ClusterRoleBinding, V1ClusterRoleBindingList> clusterRoleBindingApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(
                V1ClusterRoleBinding.class,
                V1ClusterRoleBindingList.class,
                "rbac.authorization.k8s.io",
                "v1",
                "clusterrolebindings",
                apiClient);
    }

    @Bean
    public GenericKubernetesApi<V1Namespace, V1NamespaceList> namespaceApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(
                V1Namespace.class,
                V1NamespaceList.class,
                "",
                "v1",
                "namespaces",
                apiClient);
    }

    @Bean
    public GenericKubernetesApi<V1DaemonSet, V1DaemonSetList> daemonSetApi(ApiClient apiClient) {
        return new GenericKubernetesApi<>(
                V1DaemonSet.class,
                V1DaemonSetList.class,
                "apps",
                "v1",
                "daemonsets",
                apiClient);
    }

}
