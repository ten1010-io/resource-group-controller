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
public class Exceptions {

    private List<K8sObjectReference> daemonSets;

    public Exceptions() {
        this.daemonSets = new ArrayList<>();
    }

}
