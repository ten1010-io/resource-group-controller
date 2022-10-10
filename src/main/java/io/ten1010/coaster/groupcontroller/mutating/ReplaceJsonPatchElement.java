package io.ten1010.coaster.groupcontroller.mutating;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public class ReplaceJsonPatchElement {

    private String op;
    private String path;
    private JsonNode value;

    public ReplaceJsonPatchElement(String path, JsonNode value) {
        this.op = "replace";
        this.path = path;
        this.value = value;
    }

    public String getOp() {
        return this.op;
    }

    public String getPath() {
        return this.path;
    }

    public JsonNode getValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReplaceJsonPatchElement that = (ReplaceJsonPatchElement) o;
        return Objects.equals(this.op, that.op) &&
                Objects.equals(this.path, that.path) &&
                Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.op, this.path, this.value);
    }

    @Override
    public String toString() {
        return "ReplaceJsonPatch{" +
                "op='" + this.op + '\'' +
                ", path='" + this.path + '\'' +
                ", value=" + this.value +
                '}';
    }

}
