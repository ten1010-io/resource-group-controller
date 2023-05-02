package io.ten1010.coaster.groupcontroller.model;

import io.kubernetes.client.openapi.models.V1Subject;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class V1ResourceGroupSpec {

    private List<String> nodes;
    private List<String> namespaces;
    private Exceptions exceptions;
    private List<V1Subject> subjects;

    public V1ResourceGroupSpec() {
        this.nodes = new ArrayList<>();
        this.namespaces = new ArrayList<>();
        this.subjects = new ArrayList<>();
    }

    public List<String> getNodes() {
        return this.nodes;
    }

    public V1ResourceGroupSpec setNodes(List<String> nodes) {
        this.nodes = nodes;
        return this;
    }

    public List<String> getNamespaces() {
        return this.namespaces;
    }

    public V1ResourceGroupSpec setNamespaces(List<String> namespaces) {
        this.namespaces = namespaces;
        return this;
    }

    @Nullable
    public Exceptions getExceptions() {
        return exceptions;
    }

    public void setExceptions(@Nullable Exceptions exceptions) {
        this.exceptions = exceptions;
    }

    public List<V1Subject> getSubjects() {
        return this.subjects;
    }

    public V1ResourceGroupSpec setSubjects(List<V1Subject> subjects) {
        this.subjects = subjects;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        V1ResourceGroupSpec that = (V1ResourceGroupSpec) o;
        return Objects.equals(this.nodes, that.nodes) &&
                Objects.equals(this.namespaces, that.namespaces) &&
                Objects.equals(this.exceptions, that.exceptions) &&
                Objects.equals(this.subjects, that.subjects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.nodes, this.namespaces, this.exceptions, this.subjects);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class V1ResourceGroupSpec {\n");
        sb.append("    nodes: " + this.nodes + "\n");
        sb.append("    nodes: ").append(toIndentedString(this.nodes)).append("\n");
        sb.append("    namespaces: ").append(toIndentedString(this.namespaces)).append("\n");
        sb.append("    exceptions: ").append(toIndentedString(this.exceptions)).append("\n");
        sb.append("    subjects: ").append(toIndentedString(this.subjects)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    private String toIndentedString(java.lang.Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }

}
