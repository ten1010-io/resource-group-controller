package io.ten1010.groupcontroller.mutating;

import io.kubernetes.client.common.KubernetesType;
import lombok.*;
import org.springframework.lang.Nullable;

@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class V1AdmissionReview implements KubernetesType {

    public static final String API_VERSION = "admission.k8s.io/v1";
    public static final String KIND = "AdmissionReview";

    public static V1AdmissionReview withDefaultApiVersionAndKind() {
        V1AdmissionReview obj = new V1AdmissionReview();
        obj.setApiVersion(API_VERSION);
        obj.setKind(KIND);

        return obj;
    }

    @Nullable
    private String apiVersion;
    @Nullable
    private String kind;
    @Nullable
    private V1AdmissionReviewRequest request;
    @Nullable
    private V1AdmissionReviewResponse response;

}
