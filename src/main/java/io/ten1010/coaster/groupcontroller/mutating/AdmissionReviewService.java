package io.ten1010.coaster.groupcontroller.mutating;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.coaster.groupcontroller.controller.GroupResolver;
import io.ten1010.coaster.groupcontroller.controller.ReconcilerUtil;
import io.ten1010.coaster.groupcontroller.core.PodUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class AdmissionReviewService {

    private ObjectMapper mapper;
    private GroupResolver groupResolver;

    public AdmissionReviewService(GroupResolver groupResolver) {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.groupResolver = groupResolver;
    }

    public V1AdmissionReviewResponse review(V1AdmissionReviewRequest request) {
        Objects.requireNonNull(request.getObject());
        Objects.requireNonNull(request.getNamespace());
        V1Pod pod = getPodFromJson(request.getObject());
        Objects.requireNonNull(pod.getMetadata());
        pod.getMetadata().setNamespace(request.getNamespace());

        List<V1ResourceGroup> groups;
        try {
            groups = this.groupResolver.resolve(pod);
        } catch (GroupResolver.NamespaceConflictException e) {
            V1AdmissionReviewResponse response = new V1AdmissionReviewResponse();
            response.setUid(request.getUid());
            response.setAllowed(false);

            V1AdmissionReviewResponse.Status status = new V1AdmissionReviewResponse.Status();
            status.setCode(HttpURLConnection.HTTP_CONFLICT);
            status.setMessage(String.format("Namespace [%s] belongs to multiple groups", ReconcilerUtil.getName(pod)));

            response.setStatus(status);

            return response;
        }

        Map<String, String> reconciledNodeSelector;
        if (PodUtil.isDaemonSetPod(pod)) {
            reconciledNodeSelector = ReconcilerUtil.getNodeSelector(pod);
        } else {
            if (groups.size() > 1) {
                throw new RuntimeException("More than 1 resource groups found for pod though it's not daemon set pod");
            }
            reconciledNodeSelector = ReconcilerUtil.reconcileNodeSelector(
                    ReconcilerUtil.getNodeSelector(pod),
                    groups.size() == 1 ? groups.get(0) : null);
        }

        List<V1Toleration> reconciledTolerations = ReconcilerUtil.reconcileTolerations(ReconcilerUtil.getTolerations(pod), groups);

        List<ReplaceJsonPatchElement> jsonPatch = List.of(
                buildReplaceJsonPatchElement(reconciledNodeSelector),
                buildReplaceJsonPatchElement(reconciledTolerations));
        String patch = buildPatchString(jsonPatch);

        V1AdmissionReviewResponse response = new V1AdmissionReviewResponse();
        response.setUid(request.getUid());
        response.setAllowed(true);
        response.setPatchType(V1AdmissionReviewResponse.PATCH_TYPE_JSON_PATCH);
        response.setPatch(patch);

        return response;
    }

    private ReplaceJsonPatchElement buildReplaceJsonPatchElement(Map<String, String> nodeSelector) {
        return new ReplaceJsonPatchElement("/spec/nodeSelector", this.mapper.valueToTree(nodeSelector));
    }

    private ReplaceJsonPatchElement buildReplaceJsonPatchElement(List<V1Toleration> tolerations) {
        return new ReplaceJsonPatchElement("/spec/tolerations", this.mapper.valueToTree(tolerations));
    }

    private String buildPatchString(List<ReplaceJsonPatchElement> jsonPatch) {
        String json;
        try {
            json = this.mapper.writeValueAsString(jsonPatch);
        } catch (JsonProcessingException e) {
            throw new RuntimeException();
        }

        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private V1Pod getPodFromJson(JsonNode podJson) {
        try {
            return this.mapper.treeToValue(podJson, V1Pod.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
