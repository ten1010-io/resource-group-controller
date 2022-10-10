package io.ten1010.coaster.groupcontroller.model;

import org.springframework.lang.Nullable;

import java.util.Objects;

public class DaemonSetReference {

    private String namespace;
    private String name;

    public DaemonSetReference() {
    }

    @Nullable
    public String getNamespace() {
        return this.namespace;
    }

    public void setNamespace(@Nullable String namespace) {
        this.namespace = namespace;
    }

    @Nullable
    public String getName() {
        return this.name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DaemonSetReference that = (DaemonSetReference) o;
        return Objects.equals(this.namespace, that.namespace) &&
                Objects.equals(this.name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.namespace, this.name);
    }

    @Override
    public String toString() {
        return "DaemonSetReference{" +
                "namespace='" + this.namespace + '\'' +
                ", name='" + this.name + '\'' +
                '}';
    }

}
