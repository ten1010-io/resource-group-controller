package io.ten1010.coaster.groupcontroller.admission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.configuration.property.SchedulingProperties;
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.core.IndexNames;
import io.ten1010.coaster.groupcontroller.model.V1Beta1ResourceGroup;
import io.ten1010.coaster.groupcontroller.model.V1Beta1ResourceGroupSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

class AdmissionReviewServiceTest {

    Reconciliation reconciliation;
    Indexer<V1Node> nodeIndexer;
    Indexer<V1Beta1ResourceGroup> groupIndexer;
    SchedulingProperties schedulingProperties;

    @BeforeEach
    void setUp() {
        this.groupIndexer = Mockito.mock(Indexer.class);
        this.nodeIndexer = Mockito.mock(Indexer.class);
        this.schedulingProperties = Mockito.mock(SchedulingProperties.class);
        this.reconciliation = new Reconciliation(this.groupIndexer, this.schedulingProperties);
    }

    @Test
    void should_patch_affinity_and_tolerations() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
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

        Mockito.doReturn(List.of(group1))
                .when(this.groupIndexer)
                .byIndex(IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, "ns1");
        Mockito.doReturn(true).when(this.schedulingProperties).isSchedulingGroupNodeOnly();
        AdmissionReviewService admissionReviewService = new AdmissionReviewService(this.reconciliation, this.nodeIndexer, this.groupIndexer);
        V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
        request.setUid("dummy-uid");
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
        V1AdmissionReviewResponse response = admissionReviewService.mutate(request);
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

    @Test
    void given_request_when_node_conflict_occurred_then_should_deny_request() {
        V1Node node1 = new V1Node();
        V1ObjectMeta nodeMeta1 = new V1ObjectMeta();
        nodeMeta1.setName("node1");
        nodeMeta1.setLabels(new HashMap<>());
        node1.setMetadata(nodeMeta1);
        V1NodeSpec nodeSpec1 = new V1NodeSpec();
        nodeSpec1.setTaints(new ArrayList<>());
        node1.setSpec(nodeSpec1);

        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        spec1.setNodes(List.of(node1.getMetadata().getName()));
        spec1.setNamespaces(new ArrayList<>());
        spec1.setSubjects(new ArrayList<>());
        group1.setSpec(spec1);

        V1Beta1ResourceGroup group2 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta2 = new V1ObjectMeta();
        meta2.setName("group2");
        group2.setMetadata(meta2);
        V1Beta1ResourceGroupSpec spec2 = new V1Beta1ResourceGroupSpec();
        spec2.setNodes(List.of(node1.getMetadata().getName()));
        spec2.setSubjects(new ArrayList<>());
        spec2.setNamespaces(new ArrayList<>());
        group2.setSpec(spec2);

        Mockito.doReturn(node1).when(this.nodeIndexer).getByKey("node1");
        Mockito.doReturn(List.of(group1))
                .when(this.groupIndexer)
                .byIndex(IndexNames.BY_NODE_NAME_TO_GROUP_OBJECT, "node1");
        Mockito.doReturn(true).when(this.schedulingProperties).isSchedulingGroupNodeOnly();
        AdmissionReviewService admissionReviewService = new AdmissionReviewService(this.reconciliation, this.nodeIndexer, this.groupIndexer);
        V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
        V1AdmissionReviewRequest.Kind kind = new V1AdmissionReviewRequest.Kind();
        kind.setGroup("resource-group.ten1010.io");
        kind.setVersion("v1beta1");
        kind.setKind("ResourceGroup");
        request.setKind(kind);
        V1AdmissionReviewRequest.Resource resource = new V1AdmissionReviewRequest.Resource();
        resource.setGroup("resource-group.ten1010.io");
        resource.setVersion("v1beta1");
        resource.setResource("ResourceGroup");
        request.setResource(resource);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.valueToTree(group2);
        request.setObject(json);

        V1AdmissionReviewResponse response = admissionReviewService.validate(request);
        Assertions.assertFalse(response.getAllowed());
    }

}
