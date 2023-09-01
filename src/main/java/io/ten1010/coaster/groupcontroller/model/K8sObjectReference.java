package io.ten1010.coaster.groupcontroller.model;

import lombok.*;
import org.springframework.lang.Nullable;

@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class K8sObjectReference {

    @Nullable
    private String namespace;
    @Nullable
    private String name;

}
