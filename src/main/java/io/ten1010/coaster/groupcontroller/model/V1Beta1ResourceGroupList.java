package io.ten1010.coaster.groupcontroller.model;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.V1ListMeta;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode
@ToString
public class V1Beta1ResourceGroupList implements KubernetesListObject {

    @Nullable
    private String apiVersion;
    @Nullable
    private String kind;
    @Nullable
    private V1ListMeta metadata;
    private List<V1Beta1ResourceGroup> items;

    public V1Beta1ResourceGroupList() {
        this.items = new ArrayList<>();
    }

}
