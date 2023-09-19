package io.ten1010.coaster.groupcontroller.configuration;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Node;
import io.ten1010.coaster.groupcontroller.admission.AdmissionReviewService;
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.model.V1Beta1ResourceGroup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdmissionConfiguration {

    @Bean
    public AdmissionReviewService admissionReviewService(Reconciliation reconciliation, SharedInformerFactory sharedInformerFactory) {
        Indexer<V1Node> nodeIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Node.class)
                .getIndexer();
        Indexer<V1Beta1ResourceGroup> groupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Beta1ResourceGroup.class)
                .getIndexer();
        return new AdmissionReviewService(reconciliation, nodeIndexer, groupIndexer);
    }

}
