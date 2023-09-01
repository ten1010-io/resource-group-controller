package io.ten1010.coaster.groupcontroller.configuration;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.ten1010.coaster.groupcontroller.core.K8sApis;
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
    public K8sApis k8sApis(ApiClient apiClient) {
        return new K8sApis(apiClient);
    }

}
