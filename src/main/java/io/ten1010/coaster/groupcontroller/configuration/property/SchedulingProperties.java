package io.ten1010.coaster.groupcontroller.configuration.property;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class SchedulingProperties {

    private boolean schedulingGroupNodeOnly = true;

    public SchedulingProperties(@DefaultValue("true") boolean schedulingGroupNodeOnly) {
        this.schedulingGroupNodeOnly = schedulingGroupNodeOnly;
    }

}
