package io.ten1010.coaster.groupcontroller.model;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import org.springframework.lang.Nullable;

import java.util.Objects;

public class V1ResourceGroup implements KubernetesObject {

    public static final String API_VERSION = "ten1010.io/v1";
    public static final String KIND = "ResourceGroup";

    public static V1ResourceGroup withDefaultApiVersionAndKind() {
        return new V1ResourceGroup()
                .setApiVersion(API_VERSION)
                .setKind(KIND);
    }

    private String apiVersion;
    private String kind;
    private V1ObjectMeta metadata;
    private V1ResourceGroupSpec spec;
    private V1ResourceGroupStatus status;

    public V1ResourceGroup() {
    }

    @Nullable
    @Override
    public String getApiVersion() {
        return apiVersion;
    }

    public V1ResourceGroup setApiVersion(@Nullable String apiVersion) {
        this.apiVersion = apiVersion;
        return this;
    }

    @Nullable
    @Override
    public String getKind() {
        return kind;
    }

    public V1ResourceGroup setKind(@Nullable String kind) {
        this.kind = kind;
        return this;
    }

    @Nullable
    @Override
    public V1ObjectMeta getMetadata() {
        return metadata;
    }

    public V1ResourceGroup setMetadata(@Nullable V1ObjectMeta metadata) {
        this.metadata = metadata;
        return this;
    }

    @Nullable
    public V1ResourceGroupSpec getSpec() {
        return spec;
    }

    public V1ResourceGroup setSpec(@Nullable V1ResourceGroupSpec spec) {
        this.spec = spec;
        return this;
    }

    @Nullable
    public V1ResourceGroupStatus getStatus() {
        return status;
    }

    public V1ResourceGroup setStatus(@Nullable V1ResourceGroupStatus status) {
        this.status = status;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        V1ResourceGroup that = (V1ResourceGroup) o;
        return Objects.equals(this.apiVersion, that.apiVersion) &&
                Objects.equals(this.kind, that.kind) &&
                Objects.equals(this.metadata, that.metadata) &&
                Objects.equals(this.spec, that.spec) &&
                Objects.equals(this.status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.apiVersion, this.kind, this.metadata, this.spec, this.status);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class V1ResourceGroup {\n");
        sb.append("    apiVersion: '").append(toIndentedString(this.apiVersion)).append("\'\n");
        sb.append("    kind: '").append(toIndentedString(this.kind)).append("\'\n");
        sb.append("    metadata: ").append(toIndentedString(this.metadata)).append("\n");
        sb.append("    spec: ").append(toIndentedString(this.spec)).append("\n");
        sb.append("    status: ").append(toIndentedString(this.status)).append("\n");
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
