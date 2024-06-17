package io.ten1010.groupcontroller.controller.workload.replicationcontroller;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1ReplicationController;
import io.kubernetes.client.openapi.models.V1ReplicationControllerBuilder;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.groupcontroller.controller.ControllerSupport;
import io.ten1010.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.groupcontroller.controller.Reconciliation;
import io.ten1010.groupcontroller.core.ApiResourceKind;
import io.ten1010.groupcontroller.core.K8sObjectUtil;
import io.ten1010.groupcontroller.core.KeyUtil;
import io.ten1010.groupcontroller.core.ReplicationControllerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
public class ReplicationControllerReconciler implements Reconciler {

    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    private KubernetesApiReconcileExceptionHandlingTemplate template;
    private Indexer<V1ReplicationController> replicationControllerIndexer;
    private Reconciliation reconciliation;
    private CoreV1Api coreV1Api;

    public ReplicationControllerReconciler(Indexer<V1ReplicationController> replicationControllerIndexer, Reconciliation reconciliation, CoreV1Api coreV1Api) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.replicationControllerIndexer = replicationControllerIndexer;
        this.reconciliation = reconciliation;
        this.coreV1Api = coreV1Api;
    }

    @Override
    public Result reconcile(Request request) {
        return this.template.execute(
                () -> {
                    String replicationControllerKey = KeyUtil.buildKey(request.getNamespace(), request.getName());
                    V1ReplicationController replicationController = this.replicationControllerIndexer.getByKey(replicationControllerKey);
                    if (replicationController == null) {
                        log.debug("ReplicationController [{}] not founded while reconciling", replicationControllerKey);
                        return new Result(false);
                    }
                    log.debug("ReplicationController [{}] founded while reconciling\n{}", replicationControllerKey, replicationController);

                    if (!K8sObjectUtil.isControlled(replicationController)) {
                        boolean updated = false;
                        Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledReplicationControllerAffinity(replicationController);
                        log.debug("Affinity [{}] of ReplicationController [{}] reconciled to Affinity [{}]",
                                ReplicationControllerUtil.getAffinity(replicationController),
                                replicationControllerKey,
                                reconciledAffinity.orElse(null));
                        if (!ReplicationControllerUtil.getAffinity(replicationController).equals(reconciledAffinity)) {
                            updated = true;
                            log.debug("ReplicationController [{}] updated while reconciling because of affinity", replicationControllerKey);
                        }
                        List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledReplicationControllerTolerations(replicationController);
                        log.debug("Tolerations [{}] of ReplicationController [{}] reconciled to Tolerations [{}]",
                                ReplicationControllerUtil.getTolerations(replicationController),
                                replicationControllerKey,
                                reconciledTolerations);
                        if (!new HashSet<>(ReplicationControllerUtil.getTolerations(replicationController)).equals(new HashSet<>(reconciledTolerations))) {
                            updated = true;
                            log.debug("ReplicationController [{}] updated while reconciling because of tolerations", replicationControllerKey);
                        }
                        if (updated) {
                            updateReplicationController(replicationController, reconciledAffinity.orElse(null), reconciledTolerations);
                        }
                        return new Result(false);
                    }
                    ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(replicationController));
                    if (ControllerSupport.isSupportedControllerOfReplicationController(controllerKind)) {
                        return new Result(false);
                    }
                    deleteReplicationController(K8sObjectUtil.getNamespace(replicationController), K8sObjectUtil.getName(replicationController));
                    log.debug("ReplicationController [{}] deleted while reconciling because its controller [{}] is unsupported", controllerKind, replicationControllerKey);
                    return new Result(false);
                },
                request);
    }

    private void updateReplicationController(V1ReplicationController target, @Nullable V1Affinity affinity, List<V1Toleration> tolerations) throws ApiException {
        V1ReplicationController updated = new V1ReplicationControllerBuilder(target)
                .editSpec()
                .editTemplate()
                .editSpec()
                .withAffinity(affinity)
                .withTolerations(tolerations)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        this.coreV1Api.replaceNamespacedReplicationController(
                K8sObjectUtil.getName(updated),
                K8sObjectUtil.getNamespace(updated),
                updated,
                null,
                null,
                null,
                null);
    }

    private void deleteReplicationController(String namespace, String name) throws ApiException {
        this.coreV1Api.deleteNamespacedReplicationController(
                name,
                namespace,
                null,
                null,
                null,
                null,
                null,
                null);
    }

}
