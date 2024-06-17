package io.ten1010.groupcontroller.configuration;

import io.ten1010.groupcontroller.controller.Reconciliation;
import io.ten1010.groupcontroller.mutating.AdmissionReviewService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MutatingConfiguration {

    @Bean
    public AdmissionReviewService admissionReviewService(Reconciliation reconciliation) {
        return new AdmissionReviewService(reconciliation);
    }

}
