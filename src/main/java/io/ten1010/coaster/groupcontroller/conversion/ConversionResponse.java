package io.ten1010.coaster.groupcontroller.conversion;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Value;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversionResponse {

    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Result {

        String status;

        @Nullable
        String message;

        public static Result success() {
            return new Result("Success", null);
        }

        public static Result fail(String message) {
            return new Result("Failed", message);
        }

        public Result(String status, @Nullable String message) {
            this.status = status;
            this.message = message;
        }
    }

    private String uid;

    private Result result;

    private List<Object> convertedObjects = new ArrayList<>();
}
