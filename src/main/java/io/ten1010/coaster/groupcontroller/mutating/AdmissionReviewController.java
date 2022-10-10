package io.ten1010.coaster.groupcontroller.mutating;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/admissionreviews")
public class AdmissionReviewController {

    private AdmissionReviewService reviewService;

    public AdmissionReviewController(AdmissionReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<V1AdmissionReview> create(@RequestBody V1AdmissionReview webDto) {
        log.debug("Create request received. input object :\n{}", webDto.toString());
        Objects.requireNonNull(webDto.getRequest());
        V1AdmissionReviewResponse response = this.reviewService.review(webDto.getRequest());
        webDto.setResponse(response);
        log.debug("Response to create request :\n{}", webDto.toString());

        return ResponseEntity.ok(webDto);
    }

}
