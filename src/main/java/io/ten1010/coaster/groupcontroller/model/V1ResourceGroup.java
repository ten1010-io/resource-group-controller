package io.ten1010.coaster.groupcontroller.model;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import lombok.*;
import org.springframework.lang.Nullable;

@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class V1ResourceGroup implements KubernetesObject {

    public static final String API_VERSION = "ten1010.io/v1";
    public static final String KIND = "ResourceGroup";

    public static V1ResourceGroup withDefaultApiVersionAndKind() {
        V1ResourceGroup obj = new V1ResourceGroup();
        obj.setApiVersion(API_VERSION);
        obj.setKind(KIND);
        return obj;
    }

    @Nullable
    private String apiVersion;
    @Nullable
    private String kind;
    @Nullable
    private V1ObjectMeta metadata;
    @Nullable
    private V1ResourceGroupSpec spec;
    @Nullable
    private V1ResourceGroupStatus status;

}
