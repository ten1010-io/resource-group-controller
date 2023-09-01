package io.ten1010.coaster.groupcontroller.controller.workload.replicaset;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ReplicaSetBuilder;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.coaster.groupcontroller.controller.ControllerSupport;
import io.ten1010.coaster.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.core.ApiResourceKind;
import io.ten1010.coaster.groupcontroller.core.K8sObjectUtil;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.core.ReplicaSetUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
public class ReplicaSetReconciler implements Reconciler {

    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    private KubernetesApiReconcileExceptionHandlingTemplate template;
    private Indexer<V1ReplicaSet> replicaSetIndexer;
    private Reconciliation reconciliation;
    private AppsV1Api appsV1Api;

    public ReplicaSetReconciler(Indexer<V1ReplicaSet> replicaSetIndexer, Reconciliation reconciliation, AppsV1Api appsV1Api) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.replicaSetIndexer = replicaSetIndexer;
        this.reconciliation = reconciliation;
        this.appsV1Api = appsV1Api;
    }

    @Override
    public Result reconcile(Request request) {
        return this.template.execute(
                () -> {
                    String replicaSetKey = KeyUtil.buildKey(request.getNamespace(), request.getName());
                    V1ReplicaSet replicaSet = this.replicaSetIndexer.getByKey(replicaSetKey);
                    if (replicaSet == null) {
                        log.debug("ReplicaSet [{}] not founded while reconciling", replicaSetKey);
                        return new Result(false);
                    }
                    log.debug("ReplicaSet [{}] founded while reconciling\n{}", replicaSetKey, replicaSet);

                    if (!K8sObjectUtil.isControlled(replicaSet)) {
                        boolean updated = false;
                        Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledReplicaSetAffinity(replicaSet);
                        log.debug("Affinity [{}] of replicaSet [{}] reconciled to Affinity [{}]",
                                ReplicaSetUtil.getAffinity(replicaSet),
                                replicaSetKey,
                                reconciledAffinity.orElse(null));
                        if (!ReplicaSetUtil.getAffinity(replicaSet).equals(reconciledAffinity)) {
                            updated = true;
                            log.debug("ReplicaSet [{}] updated while reconciling because of affinity", replicaSetKey);
                        }
                        List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledReplicaSetTolerations(replicaSet);
                        log.debug("Tolerations [{}] of replicaSet [{}] reconciled to Tolerations [{}]",
                                ReplicaSetUtil.getTolerations(replicaSet),
                                replicaSetKey,
                                reconciledTolerations);
                        if (!new HashSet<>(ReplicaSetUtil.getTolerations(replicaSet)).equals(new HashSet<>(reconciledTolerations))) {
                            updated = true;
                            log.debug("ReplicaSet [{}] updated while reconciling because of tolerations", replicaSetKey);
                        }
                        if (updated) {
                            updateReplicaSet(replicaSet, reconciledAffinity.orElse(null), reconciledTolerations);
                        }
                        return new Result(false);
                    }
                    ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(replicaSet));
                    if (ControllerSupport.isSupportedControllerOfReplicaSet(controllerKind)) {
                        return new Result(false);
                    }
                    deleteReplicaSet(K8sObjectUtil.getNamespace(replicaSet), K8sObjectUtil.getName(replicaSet));
                    log.debug("ReplicaSet [{}] deleted while reconciling because its controller [{}] is unsupported", controllerKind, replicaSetKey);
                    return new Result(false);
                },
                request);
    }

    private void updateReplicaSet(V1ReplicaSet target, @Nullable V1Affinity affinity, List<V1Toleration> tolerations) throws ApiException {
        V1ReplicaSet updated = new V1ReplicaSetBuilder(target)
                .editSpec()
                .editTemplate()
                .editSpec()
                .withAffinity(affinity)
                .withTolerations(tolerations)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        this.appsV1Api.replaceNamespacedReplicaSet(
                K8sObjectUtil.getName(updated),
                K8sObjectUtil.getNamespace(updated),
                updated,
                null,
                null,
                null,
                null);
    }

    private void deleteReplicaSet(String namespace, String name) throws ApiException {
        this.appsV1Api.deleteNamespacedReplicaSet(
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
