package io.ten1010.coaster.groupcontroller.model;

import lombok.*;
import org.springframework.lang.Nullable;

@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class DaemonSetReference {

    @Nullable
    private String namespace;
    @Nullable
    private String name;

}
