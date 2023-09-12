package io.ten1010.coaster.groupcontroller.model;

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
public class V1Beta2DaemonSet {

    private List<V1Beta2K8sObjectReference> daemonSets;

    private boolean allowAll;

    public V1Beta2DaemonSet() {
        this.daemonSets = new ArrayList<>();
    }

}
