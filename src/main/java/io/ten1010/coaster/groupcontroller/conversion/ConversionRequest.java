package io.ten1010.coaster.groupcontroller.conversion;

import lombok.Data;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ConversionRequest {

    @Nullable
    private String uid;

    @Nullable
    private String desiredAPIVersion;

    private List<Map<String,Object>> objects = new ArrayList<>();

}
