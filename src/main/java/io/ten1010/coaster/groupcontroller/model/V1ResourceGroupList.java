package io.ten1010.coaster.groupcontroller.model;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.V1ListMeta;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class V1ResourceGroupList implements KubernetesListObject {

    private String apiVersion;
    private String kind;
    private V1ListMeta metadata;
    private List<V1ResourceGroup> items;

    public V1ResourceGroupList() {
        this.items = new ArrayList<>();
    }

    @Nullable
    @Override
    public String getApiVersion() {
        return apiVersion;
    }

    public V1ResourceGroupList setApiVersion(@Nullable String apiVersion) {
        this.apiVersion = apiVersion;
        return this;
    }

    @Nullable
    @Override
    public String getKind() {
        return kind;
    }

    public V1ResourceGroupList setKind(@Nullable String kind) {
        this.kind = kind;
        return this;
    }

    @Nullable
    @Override
    public V1ListMeta getMetadata() {
        return metadata;
    }

    public V1ResourceGroupList setMetadata(@Nullable V1ListMeta metadata) {
        this.metadata = metadata;
        return this;
    }

    @Override
    public List<V1ResourceGroup> getItems() {
        return items;
    }

    public V1ResourceGroupList setItems(List<V1ResourceGroup> items) {
        this.items = items;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        V1ResourceGroupList that = (V1ResourceGroupList) o;
        return Objects.equals(this.apiVersion, that.apiVersion) &&
                Objects.equals(this.kind, that.kind) &&
                Objects.equals(this.metadata, that.metadata) &&
                Objects.equals(this.items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.apiVersion, this.kind, this.metadata, this.items);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class V1ResourceGroupList {\n");
        sb.append("    apiVersion: ").append(toIndentedString(this.apiVersion)).append("\'\n");
        sb.append("    kind: ").append(toIndentedString(this.kind)).append("\'\n");
        sb.append("    metadata: ").append(toIndentedString(this.metadata)).append("\n");
        sb.append("    items: ").append(toIndentedString(this.items)).append("\n");
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
