package io.ten1010.groupcontroller.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode
@ToString
public class V1Beta1DaemonSet {

    private List<V1Beta1K8sObjectReference> daemonSets;

    public V1Beta1DaemonSet() {
        this.daemonSets = new ArrayList<>();
    }

}
