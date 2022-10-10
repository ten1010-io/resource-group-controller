package io.ten1010.coaster.groupcontroller.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Exceptions {

    private List<DaemonSetReference> daemonSets;

    public Exceptions() {
        this.daemonSets = new ArrayList<>();
    }

    public List<DaemonSetReference> getDaemonSets() {
        return daemonSets;
    }

    public void setDaemonSets(List<DaemonSetReference> daemonSets) {
        this.daemonSets = daemonSets;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Exceptions that = (Exceptions) o;
        return Objects.equals(this.daemonSets, that.daemonSets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.daemonSets);
    }

    @Override
    public String toString() {
        return "Exceptions{" +
                "daemonSets=" + this.daemonSets +
                '}';
    }

}
