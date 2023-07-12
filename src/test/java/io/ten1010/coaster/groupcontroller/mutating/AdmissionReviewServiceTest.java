package io.ten1010.coaster.groupcontroller.mutating;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.controller.GroupResolver;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroupSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

class AdmissionReviewServiceTest {

    GroupResolver groupResolver;

    @BeforeEach
    void setUp() {
        this.groupResolver = Mockito.mock(GroupResolver.class);
    }

    @Test
    void should_patch_nodeAffinities_and_tolerations() {
        V1ResourceGroup group1 = new V1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1ResourceGroupSpec spec1 = new V1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1Pod pod1 = new V1Pod();
        V1ObjectMeta podMeta1 = new V1ObjectMeta();
        podMeta1.setNamespace("ns1");
        podMeta1.setName("pod1");
        pod1.setMetadata(podMeta1);
        V1PodSpec podSpec1 = new V1PodSpec();
        podSpec1.setTolerations(new ArrayList<>());
        pod1.setSpec(podSpec1);

        Mockito.doReturn(List.of(group1)).when(this.groupResolver).resolve(pod1);
        AdmissionReviewService admissionReviewService = new AdmissionReviewService(this.groupResolver);
        V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
        V1AdmissionReviewRequest.Kind kind = new V1AdmissionReviewRequest.Kind();
        kind.setGroup("");
        kind.setVersion("v1");
        kind.setKind("Pod");
        request.setKind(kind);
        V1AdmissionReviewRequest.Resource resource = new V1AdmissionReviewRequest.Resource();
        resource.setGroup("");
        resource.setVersion("v1");
        resource.setResource("pods");
        request.setResource(resource);
        request.setNamespace("ns1");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.valueToTree(pod1);
        request.setObject(json);
        V1AdmissionReviewResponse response = admissionReviewService.review(request);
        Assertions.assertTrue(response.getAllowed());

        byte[] jsonBytes = Base64.getDecoder().decode(response.getPatch());
        String jsonStr = new String(jsonBytes, StandardCharsets.UTF_8);
        List<ObjectNode> patchJson = null;
        try {
            patchJson = mapper.readValue(jsonStr, new TypeReference<List<ObjectNode>>() {
            });
        } catch (JsonProcessingException e) {
            Assertions.fail(e);
        }
        String affinityPath = "/spec/affinity";
        String tolerationsPath = "/spec/tolerations";
        for (ObjectNode e : patchJson) {
            if ((!e.get("path").textValue().equals(affinityPath) && !e.get("path").textValue().equals(tolerationsPath))) {
                Assertions.fail();
            }
        }
    }

}
