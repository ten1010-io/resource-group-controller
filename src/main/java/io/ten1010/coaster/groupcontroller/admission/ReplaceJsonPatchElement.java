package io.ten1010.coaster.groupcontroller.admission;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public class ReplaceJsonPatchElement {

    private String op;
    private String path;
    private JsonNode value;

    public ReplaceJsonPatchElement(String path, JsonNode value) {
        this.op = "replace";
        this.path = path;
        this.value = value;
    }

}
