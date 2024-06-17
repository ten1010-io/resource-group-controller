package io.ten1010.groupcontroller.controller.workload.daemonset;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1DaemonSetBuilder;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.groupcontroller.controller.ControllerSupport;
import io.ten1010.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.groupcontroller.controller.Reconciliation;
import io.ten1010.groupcontroller.core.ApiResourceKind;
import io.ten1010.groupcontroller.core.DaemonSetUtil;
import io.ten1010.groupcontroller.core.K8sObjectUtil;
import io.ten1010.groupcontroller.core.KeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
public class DaemonSetReconciler implements Reconciler {

    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    private KubernetesApiReconcileExceptionHandlingTemplate template;
    private Indexer<V1DaemonSet> daemonSetIndexer;
    private Reconciliation reconciliation;
    private AppsV1Api appsV1Api;

    public DaemonSetReconciler(Indexer<V1DaemonSet> daemonSetIndexer, Reconciliation reconciliation, AppsV1Api appsV1Api) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.daemonSetIndexer = daemonSetIndexer;
        this.reconciliation = reconciliation;
        this.appsV1Api = appsV1Api;
    }

    @Override
    public Result reconcile(Request request) {
        return this.template.execute(
                () -> {
                    String daemonSetKey = KeyUtil.buildKey(request.getNamespace(), request.getName());
                    V1DaemonSet daemonSet = this.daemonSetIndexer.getByKey(daemonSetKey);
                    if (daemonSet == null) {
                        log.debug("DaemonSet [{}] not founded while reconciling", daemonSetKey);
                        return new Result(false);
                    }
                    log.debug("DaemonSet [{}] founded while reconciling\n{}", daemonSetKey, daemonSet);

                    if (!K8sObjectUtil.isControlled(daemonSet)) {
                        boolean updated = false;
                        Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledDaemonSetAffinity(daemonSet);
                        log.debug("Affinity [{}] of daemonSet [{}] reconciled to Affinity [{}]",
                                DaemonSetUtil.getAffinity(daemonSet),
                                daemonSetKey,
                                reconciledAffinity.orElse(null));
                        if (!DaemonSetUtil.getAffinity(daemonSet).equals(reconciledAffinity)) {
                            updated = true;
                            log.debug("DaemonSet [{}] updated while reconciling because of affinity", daemonSetKey);
                        }
                        List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledDaemonSetTolerations(daemonSet);
                        log.debug("Tolerations [{}] of daemonSet [{}] reconciled to Tolerations [{}]",
                                DaemonSetUtil.getTolerations(daemonSet),
                                daemonSetKey,
                                reconciledTolerations);
                        if (!new HashSet<>(DaemonSetUtil.getTolerations(daemonSet)).equals(new HashSet<>(reconciledTolerations))) {
                            updated = true;
                            log.debug("DaemonSet [{}] updated while reconciling because of tolerations", daemonSetKey);
                        }
                        if (updated) {
                            updateDaemonSet(daemonSet, reconciledAffinity.orElse(null), reconciledTolerations);
                        }
                        return new Result(false);
                    }
                    ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(daemonSet));
                    if (ControllerSupport.isSupportedControllerOfDaemonSet(controllerKind)) {
                        return new Result(false);
                    }
                    deleteDaemonSet(K8sObjectUtil.getNamespace(daemonSet), K8sObjectUtil.getName(daemonSet));
                    log.debug("DaemonSet [{}] deleted while reconciling because its controller [{}] is unsupported", controllerKind, daemonSetKey);
                    return new Result(false);
                },
                request);
    }

    private void updateDaemonSet(V1DaemonSet target, @Nullable V1Affinity affinity, List<V1Toleration> tolerations) throws ApiException {
        V1DaemonSet updated = new V1DaemonSetBuilder(target)
                .editSpec()
                .editTemplate()
                .editSpec()
                .withAffinity(affinity)
                .withTolerations(tolerations)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        this.appsV1Api.replaceNamespacedDaemonSet(
                K8sObjectUtil.getName(updated),
                K8sObjectUtil.getNamespace(updated),
                updated,
                null,
                null,
                null,
                null);
    }

    private void deleteDaemonSet(String namespace, String name) throws ApiException {
        this.appsV1Api.deleteNamespacedDaemonSet(
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
