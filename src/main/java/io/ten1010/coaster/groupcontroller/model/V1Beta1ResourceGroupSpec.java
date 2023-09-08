package io.ten1010.coaster.groupcontroller.model;

import io.kubernetes.client.openapi.models.V1Subject;
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
public class V1Beta1ResourceGroupSpec {

    private List<String> nodes;
    private List<String> namespaces;
    @Nullable
    private V1Beta1DaemonSet daemonSet;
    private List<V1Subject> subjects;

    public V1Beta1ResourceGroupSpec() {
        this.nodes = new ArrayList<>();
        this.namespaces = new ArrayList<>();
        this.subjects = new ArrayList<>();
    }

}
