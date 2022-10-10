package io.ten1010.coaster.groupcontroller.mutating;

import lombok.*;

@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class V1AdmissionReviewResponse {

    public static final String PATCH_TYPE_JSON_PATCH = "JSONPatch";

    @NoArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    public static class Status {

        private Integer code;
        private String message;

    }

    private String uid;
    private Boolean allowed;
    private Status status;
    private String patchType;
    private String patch;

}
