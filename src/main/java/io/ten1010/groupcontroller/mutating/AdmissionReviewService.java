package io.ten1010.groupcontroller.mutating;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.groupcontroller.controller.ControllerSupport;
import io.ten1010.groupcontroller.controller.Reconciliation;
import io.ten1010.groupcontroller.core.ApiResourceKind;
import io.ten1010.groupcontroller.core.ApiResourceKinds;
import io.ten1010.groupcontroller.core.K8sObjectUtil;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class AdmissionReviewService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JavaTimeModule());
    }

    private static V1AdmissionReviewResponse buildAllowResponse(String uid) {
        V1AdmissionReviewResponse response = new V1AdmissionReviewResponse();
        response.setUid(uid);
        response.setAllowed(true);
        return response;
    }

    private static V1AdmissionReviewResponse buildRejectResponse(String uid, int code, String reason) {
        V1AdmissionReviewResponse.Status status = new V1AdmissionReviewResponse.Status();
        status.setCode(code);
        status.setMessage(reason);
        V1AdmissionReviewResponse response = new V1AdmissionReviewResponse();
        response.setUid(uid);
        response.setAllowed(false);
        response.setStatus(status);
        return response;
    }

    private static V1AdmissionReviewResponse buildJsonPatchResponse(String uid, ReplaceJsonPatchElement... jsonPatch) {
        String patch = buildPatchString(List.of(jsonPatch));
        V1AdmissionReviewResponse response = new V1AdmissionReviewResponse();
        response.setUid(uid);
        response.setAllowed(true);
        response.setPatchType(V1AdmissionReviewResponse.PATCH_TYPE_JSON_PATCH);
        response.setPatch(patch);
        return response;
    }

    private static String buildPatchString(List<ReplaceJsonPatchElement> jsonPatch) {
        String json;
        try {
            json = MAPPER.writeValueAsString(jsonPatch);
        } catch (JsonProcessingException e) {
            throw new RuntimeException();
        }

        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static V1CronJob getCronJob(JsonNode json) {
        try {
            return MAPPER.treeToValue(json, V1CronJob.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static V1DaemonSet getDaemonSet(JsonNode json) {
        try {
            return MAPPER.treeToValue(json, V1DaemonSet.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static V1Job getJob(JsonNode json) {
        try {
            return MAPPER.treeToValue(json, V1Job.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static V1Deployment getDeployment(JsonNode json) {
        try {
            return MAPPER.treeToValue(json, V1Deployment.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static V1Pod getPod(JsonNode json) {
        try {
            return MAPPER.treeToValue(json, V1Pod.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static V1ReplicaSet getReplicaSet(JsonNode json) {
        try {
            return MAPPER.treeToValue(json, V1ReplicaSet.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static V1ReplicationController getReplicationController(JsonNode json) {
        try {
            return MAPPER.treeToValue(json, V1ReplicationController.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static V1StatefulSet getStatefulSet(JsonNode json) {
        try {
            return MAPPER.treeToValue(json, V1StatefulSet.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static ApiResourceKind toApiResourceKind(V1AdmissionReviewRequest.Kind kind) {
        return new ApiResourceKind(kind.getGroup(), kind.getKind());
    }

    private Reconciliation reconciliation;

    public AdmissionReviewService(Reconciliation reconciliation) {
        this.reconciliation = reconciliation;
    }

    public V1AdmissionReviewResponse review(V1AdmissionReviewRequest request) {
        Objects.requireNonNull(request.getKind());
        if (ApiResourceKinds.CRON_JOB.equals(toApiResourceKind(request.getKind()))) {
            return handleCronJob(request);
        }
        if (ApiResourceKinds.DAEMON_SET.equals(toApiResourceKind(request.getKind()))) {
            return handleDaemonSet(request);
        }
        if (ApiResourceKinds.DEPLOYMENT.equals(toApiResourceKind(request.getKind()))) {
            return handleDeployment(request);
        }
        if (ApiResourceKinds.JOB.equals(toApiResourceKind(request.getKind()))) {
            return handleJob(request);
        }
        if (ApiResourceKinds.POD.equals(toApiResourceKind(request.getKind()))) {
            return handlePod(request);
        }
        if (ApiResourceKinds.REPLICA_SET.equals(toApiResourceKind(request.getKind()))) {
            return handleReplicaSet(request);
        }
        if (ApiResourceKinds.REPLICATION_CONTROLLER.equals(toApiResourceKind(request.getKind()))) {
            return handleReplicationController(request);
        }
        if (ApiResourceKinds.STATEFUL_SET.equals(toApiResourceKind(request.getKind()))) {
            return handleStatefulSet(request);
        }
        throw new IllegalArgumentException(String.format("Unsupported kind [%s]", request.getKind()));
    }

    private V1AdmissionReviewResponse handleCronJob(V1AdmissionReviewRequest request) {
        Objects.requireNonNull(request.getUid());
        Objects.requireNonNull(request.getObject());
        V1CronJob cronJob = getCronJob(request.getObject());
        if (!K8sObjectUtil.isControlled(cronJob)) {
            Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledCronJobAffinity(cronJob);
            List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledCronJobTolerations(cronJob);
            return buildJsonPatchResponse(
                    request.getUid(),
                    new ReplaceJsonPatchElement("/spec/jobTemplate/spec/template/spec/affinity", MAPPER.valueToTree(reconciledAffinity.orElse(null))),
                    new ReplaceJsonPatchElement("/spec/jobTemplate/spec/template/spec/tolerations", MAPPER.valueToTree(reconciledTolerations)));
        }
        ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(cronJob));
        if (ControllerSupport.isSupportedControllerOfCronJob(controllerKind)) {
            return buildAllowResponse(request.getUid());
        }
        return buildRejectResponse(request.getUid(), 400, String.format("Unsupported controller kind [%s] of CronJob", controllerKind));
    }

    private V1AdmissionReviewResponse handleDaemonSet(V1AdmissionReviewRequest request) {
        Objects.requireNonNull(request.getUid());
        Objects.requireNonNull(request.getObject());
        V1DaemonSet daemonSet = getDaemonSet(request.getObject());
        if (!K8sObjectUtil.isControlled(daemonSet)) {
            Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledDaemonSetAffinity(daemonSet);
            List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledDaemonSetTolerations(daemonSet);
            return buildJsonPatchResponse(
                    request.getUid(),
                    new ReplaceJsonPatchElement("/spec/template/spec/affinity", MAPPER.valueToTree(reconciledAffinity.orElse(null))),
                    new ReplaceJsonPatchElement("/spec/template/spec/tolerations", MAPPER.valueToTree(reconciledTolerations)));
        }
        ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(daemonSet));
        if (ControllerSupport.isSupportedControllerOfDaemonSet(controllerKind)) {
            return buildAllowResponse(request.getUid());
        }
        return buildRejectResponse(request.getUid(), 400, String.format("Unsupported controller kind [%s] of DaemonSet", controllerKind));
    }

    private V1AdmissionReviewResponse handleDeployment(V1AdmissionReviewRequest request) {
        Objects.requireNonNull(request.getUid());
        Objects.requireNonNull(request.getObject());
        V1Deployment deployment = getDeployment(request.getObject());
        if (!K8sObjectUtil.isControlled(deployment)) {
            Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledDeploymentAffinity(deployment);
            List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledDeploymentTolerations(deployment);
            return buildJsonPatchResponse(
                    request.getUid(),
                    new ReplaceJsonPatchElement("/spec/template/spec/affinity", MAPPER.valueToTree(reconciledAffinity.orElse(null))),
                    new ReplaceJsonPatchElement("/spec/template/spec/tolerations", MAPPER.valueToTree(reconciledTolerations)));
        }
        ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(deployment));
        if (ControllerSupport.isSupportedControllerOfDeployment(controllerKind)) {
            return buildAllowResponse(request.getUid());
        }
        return buildRejectResponse(request.getUid(), 400, String.format("Unsupported controller kind [%s] of Deployment", controllerKind));
    }

    private V1AdmissionReviewResponse handleJob(V1AdmissionReviewRequest request) {
        Objects.requireNonNull(request.getUid());
        Objects.requireNonNull(request.getObject());
        V1Job job = getJob(request.getObject());
        if (!K8sObjectUtil.isControlled(job)) {
            Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledJobAffinity(job);
            List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledJobTolerations(job);
            return buildJsonPatchResponse(
                    request.getUid(),
                    new ReplaceJsonPatchElement("/spec/template/spec/affinity", MAPPER.valueToTree(reconciledAffinity.orElse(null))),
                    new ReplaceJsonPatchElement("/spec/template/spec/tolerations", MAPPER.valueToTree(reconciledTolerations)));
        }
        ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(job));
        if (ControllerSupport.isSupportedControllerOfJob(controllerKind)) {
            return buildAllowResponse(request.getUid());
        }
        return buildRejectResponse(request.getUid(), 400, String.format("Unsupported controller kind [%s] of Job", controllerKind));
    }

    private V1AdmissionReviewResponse handlePod(V1AdmissionReviewRequest request) {
        Objects.requireNonNull(request.getUid());
        Objects.requireNonNull(request.getObject());
        V1Pod pod = getPod(request.getObject());
        if (!K8sObjectUtil.isControlled(pod)) {
            Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledPodAffinity(pod);
            List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledPodTolerations(pod);
            return buildJsonPatchResponse(
                    request.getUid(),
                    new ReplaceJsonPatchElement("/spec/affinity", MAPPER.valueToTree(reconciledAffinity.orElse(null))),
                    new ReplaceJsonPatchElement("/spec/tolerations", MAPPER.valueToTree(reconciledTolerations)));
        }
        ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(pod));
        if (ControllerSupport.isSupportedControllerOfPod(controllerKind)) {
            return buildAllowResponse(request.getUid());
        }
        return buildRejectResponse(request.getUid(), 400, String.format("Unsupported controller kind [%s] of Pod", controllerKind));
    }

    private V1AdmissionReviewResponse handleReplicaSet(V1AdmissionReviewRequest request) {
        Objects.requireNonNull(request.getUid());
        Objects.requireNonNull(request.getObject());
        V1ReplicaSet replicaSet = getReplicaSet(request.getObject());
        if (!K8sObjectUtil.isControlled(replicaSet)) {
            Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledReplicaSetAffinity(replicaSet);
            List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledReplicaSetTolerations(replicaSet);
            return buildJsonPatchResponse(
                    request.getUid(),
                    new ReplaceJsonPatchElement("/spec/template/spec/affinity", MAPPER.valueToTree(reconciledAffinity.orElse(null))),
                    new ReplaceJsonPatchElement("/spec/template/spec/tolerations", MAPPER.valueToTree(reconciledTolerations)));
        }
        ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(replicaSet));
        if (ControllerSupport.isSupportedControllerOfReplicaSet(controllerKind)) {
            return buildAllowResponse(request.getUid());
        }
        return buildRejectResponse(request.getUid(), 400, String.format("Unsupported controller kind [%s] of ReplicaSet", controllerKind));
    }

    private V1AdmissionReviewResponse handleReplicationController(V1AdmissionReviewRequest request) {
        Objects.requireNonNull(request.getUid());
        Objects.requireNonNull(request.getObject());
        V1ReplicationController replicationController = getReplicationController(request.getObject());
        if (!K8sObjectUtil.isControlled(replicationController)) {
            Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledReplicationControllerAffinity(replicationController);
            List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledReplicationControllerTolerations(replicationController);
            return buildJsonPatchResponse(
                    request.getUid(),
                    new ReplaceJsonPatchElement("/spec/template/spec/affinity", MAPPER.valueToTree(reconciledAffinity.orElse(null))),
                    new ReplaceJsonPatchElement("/spec/template/spec/tolerations", MAPPER.valueToTree(reconciledTolerations)));
        }
        ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(replicationController));
        if (ControllerSupport.isSupportedControllerOfReplicationController(controllerKind)) {
            return buildAllowResponse(request.getUid());
        }
        return buildRejectResponse(request.getUid(), 400, String.format("Unsupported controller kind [%s] of ReplicationController", controllerKind));
    }

    private V1AdmissionReviewResponse handleStatefulSet(V1AdmissionReviewRequest request) {
        Objects.requireNonNull(request.getUid());
        Objects.requireNonNull(request.getObject());
        V1StatefulSet statefulSet = getStatefulSet(request.getObject());
        if (!K8sObjectUtil.isControlled(statefulSet)) {
            Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledStatefulSetAffinity(statefulSet);
            List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledStatefulSetTolerations(statefulSet);
            return buildJsonPatchResponse(
                    request.getUid(),
                    new ReplaceJsonPatchElement("/spec/template/spec/affinity", MAPPER.valueToTree(reconciledAffinity.orElse(null))),
                    new ReplaceJsonPatchElement("/spec/template/spec/tolerations", MAPPER.valueToTree(reconciledTolerations)));
        }
        ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(statefulSet));
        if (ControllerSupport.isSupportedControllerOfStatefulSet(controllerKind)) {
            return buildAllowResponse(request.getUid());
        }
        return buildRejectResponse(request.getUid(), 400, String.format("Unsupported controller kind [%s] of StatefulSet", controllerKind));
    }

}
