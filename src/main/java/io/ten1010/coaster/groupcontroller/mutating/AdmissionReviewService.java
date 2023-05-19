package io.ten1010.coaster.groupcontroller.mutating;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.controller.GroupResolver;
import io.ten1010.coaster.groupcontroller.controller.ReconcilerUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;

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

        List<V1ResourceGroup> groups = this.groupResolver.resolve(pod);
        Pair<Boolean, V1OwnerReference> result = GroupResolver.isDaemonSetPod(pod);
        V1Affinity reconciledAffinities;
        if (result.getValue0()) {
            reconciledAffinities = ReconcilerUtil.getAffinity(pod);
        } else {
            reconciledAffinities = ReconcilerUtil.reconcileAffinity(ReconcilerUtil.getAffinity(pod), groups);
        }

        List<V1Toleration> reconciledTolerations = ReconcilerUtil.reconcileTolerations(ReconcilerUtil.getTolerations(pod), groups);

        List<ReplaceJsonPatchElement> jsonPatch = List.of(
                buildReplaceJsonPatchElement(reconciledTolerations),
                buildReplaceJsonPatchElement(reconciledAffinities));
                String patch = buildPatchString(jsonPatch);

        V1AdmissionReviewResponse response = new V1AdmissionReviewResponse();
        response.setUid(request.getUid());
        response.setAllowed(true);
        response.setPatchType(V1AdmissionReviewResponse.PATCH_TYPE_JSON_PATCH);
        response.setPatch(patch);

        return response;
    }
    private ReplaceJsonPatchElement buildReplaceJsonPatchElement(V1Affinity affinity) {
        return new ReplaceJsonPatchElement("/spec/affinity", this.mapper.valueToTree(affinity));
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
