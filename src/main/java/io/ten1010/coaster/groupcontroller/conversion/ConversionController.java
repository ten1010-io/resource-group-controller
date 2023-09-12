package io.ten1010.coaster.groupcontroller.conversion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ConversionController {

    private final ConversionService conversionService;

    @PostMapping(
            path = "/crdconvert",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ConversionReview convert(@RequestBody ConversionReview conversionReview) {
        log.debug("Conversion webhook request: \n{}", conversionReview);
        ConversionResponse response = conversionService.convert(Objects.requireNonNull(conversionReview.getRequest()));
        conversionReview.setResponse(response);
        log.debug("Conversion webhook response: \n{}", conversionReview);

        return conversionReview;
    }
}
