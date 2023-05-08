package io.ten1010.coaster.groupcontroller.model;

import org.springframework.lang.Nullable;
import java.util.Objects;

public class DaemonSetReference {

    private String namespace;
    private String name;

    private static String toIndentedString(@Nullable Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }

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
        StringBuilder sb = new StringBuilder();
        sb.append("class DaemonSetReference {\n");
        sb.append("    namespace: ").append(toIndentedString(this.namespace)).append("\n");
        sb.append("    name: ").append(toIndentedString(this.name)).append("\n");
        sb.append("}");
        return sb.toString();
    }

}
