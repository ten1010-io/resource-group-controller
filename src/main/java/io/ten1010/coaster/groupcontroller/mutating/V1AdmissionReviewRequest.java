package io.ten1010.coaster.groupcontroller.mutating;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class V1AdmissionReviewRequest {

    @NoArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    public static class Kind {

        @Nullable
        private String group;
        @Nullable
        private String version;
        @Nullable
        private String kind;

    }

    @NoArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    public static class Resource {

        @Nullable
        private String group;
        @Nullable
        private String version;
        @Nullable
        private String resource;

    }

    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    public static class UserInfo {

        @Nullable
        private String username;
        private List<String> groups;

        public UserInfo() {
            this.groups = new ArrayList<>();
        }

    }

    @Nullable
    private String uid;
    @Nullable
    private Kind kind;
    @Nullable
    private Resource resource;
    @Nullable
    private String name;
    @Nullable
    private String namespace;
    @Nullable
    private String operation;
    @Nullable
    private UserInfo userInfo;
    @Nullable
    private JsonNode object;

}
