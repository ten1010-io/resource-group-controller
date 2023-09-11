package io.ten1010.coaster.groupcontroller.configuration.property;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration("kubernetesClientConfig")
@ConfigurationProperties(prefix = "app.kubernetes.client")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString

public class KubernetesClientProperties {

    private boolean verifySsl = true;
    private String kubeconfigPath = "$HOME/.kube/config";

}
