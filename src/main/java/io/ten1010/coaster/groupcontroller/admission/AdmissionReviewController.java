package io.ten1010.coaster.groupcontroller.admission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@Slf4j
@RestController
public class AdmissionReviewController {

    private AdmissionReviewService reviewService;

    public AdmissionReviewController(AdmissionReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping(value = "/mutate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<V1AdmissionReview> create(@RequestBody V1AdmissionReview webDto) {
        Objects.requireNonNull(webDto.getRequest());
        log.debug("{} request received. input object :\n{}", webDto.getRequest().getOperation(), webDto.toString());
        V1AdmissionReviewResponse response = this.reviewService.mutate(webDto.getRequest());
        webDto.setResponse(response);
        log.debug("Response to {} request :\n{}", webDto.getRequest().getOperation(), webDto.toString());

        return ResponseEntity.ok(webDto);
    }

    @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<V1AdmissionReview> verify(@RequestBody V1AdmissionReview webDto) {
        Objects.requireNonNull(webDto.getRequest());
        log.debug("{} ResourceGroup request received. input object :\n{}", webDto.getRequest().getOperation(), webDto.toString());
        V1AdmissionReviewResponse response = this.reviewService.validate(webDto.getRequest());
        webDto.setResponse(response);
        log.debug("Response to {} ResourceGroup request :\n{}", webDto.getRequest().getOperation(), webDto.toString());

        return ResponseEntity.ok(webDto);
    }

}
