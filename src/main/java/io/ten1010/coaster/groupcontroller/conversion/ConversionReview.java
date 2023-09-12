package io.ten1010.coaster.groupcontroller.conversion;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class ConversionReview {

    public static final String API_VERSION = "apiextensions.k8s.io/v1";
    public static final String KIND = "ConversionReview";

    @Nullable
    private String apiVersion;

    @Nullable
    private String kind;

    @Nullable
    private ConversionRequest request;

    @Nullable
    private ConversionResponse response;
}
