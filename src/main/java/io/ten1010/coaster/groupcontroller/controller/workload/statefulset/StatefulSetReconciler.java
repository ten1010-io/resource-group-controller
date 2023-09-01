package io.ten1010.coaster.groupcontroller.controller.workload.statefulset;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1StatefulSetBuilder;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.coaster.groupcontroller.controller.ControllerSupport;
import io.ten1010.coaster.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.core.ApiResourceKind;
import io.ten1010.coaster.groupcontroller.core.K8sObjectUtil;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.core.StatefulSetUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
public class StatefulSetReconciler implements Reconciler {

    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    private KubernetesApiReconcileExceptionHandlingTemplate template;
    private Indexer<V1StatefulSet> statefulSetIndexer;
    private Reconciliation reconciliation;
    private AppsV1Api appsV1Api;

    public StatefulSetReconciler(Indexer<V1StatefulSet> statefulSetIndexer, Reconciliation reconciliation, AppsV1Api appsV1Api) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.statefulSetIndexer = statefulSetIndexer;
        this.reconciliation = reconciliation;
        this.appsV1Api = appsV1Api;
    }

    @Override
    public Result reconcile(Request request) {
        return this.template.execute(
                () -> {
                    String statefulSetKey = KeyUtil.buildKey(request.getNamespace(), request.getName());
                    V1StatefulSet statefulSet = this.statefulSetIndexer.getByKey(statefulSetKey);
                    if (statefulSet == null) {
                        log.debug("StatefulSet [{}] not founded while reconciling", statefulSetKey);
                        return new Result(false);
                    }
                    log.debug("StatefulSet [{}] founded while reconciling\n{}", statefulSetKey, statefulSet);

                    if (!K8sObjectUtil.isControlled(statefulSet)) {
                        boolean updated = false;
                        Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledStatefulSetAffinity(statefulSet);
                        log.debug("Affinity [{}] of StatefulSet [{}] reconciled to Affinity [{}]",
                                StatefulSetUtil.getAffinity(statefulSet),
                                statefulSetKey,
                                reconciledAffinity.orElse(null));
                        if (!StatefulSetUtil.getAffinity(statefulSet).equals(reconciledAffinity)) {
                            updated = true;
                            log.debug("StatefulSet [{}] updated while reconciling because of affinity", statefulSetKey);
                        }
                        List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledStatefulSetTolerations(statefulSet);
                        log.debug("Tolerations [{}] of StatefulSet [{}] reconciled to Tolerations [{}]",
                                StatefulSetUtil.getTolerations(statefulSet),
                                statefulSetKey,
                                reconciledTolerations);
                        if (!new HashSet<>(StatefulSetUtil.getTolerations(statefulSet)).equals(new HashSet<>(reconciledTolerations))) {
                            updated = true;
                            log.debug("StatefulSet [{}] updated while reconciling because of tolerations", statefulSetKey);
                        }
                        if (updated) {
                            updateStatefulSet(statefulSet, reconciledAffinity.orElse(null), reconciledTolerations);
                        }
                        return new Result(false);
                    }
                    ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(statefulSet));
                    if (ControllerSupport.isSupportedControllerOfStatefulSet(controllerKind)) {
                        return new Result(false);
                    }
                    deleteStatefulSet(K8sObjectUtil.getNamespace(statefulSet), K8sObjectUtil.getName(statefulSet));
                    log.debug("StatefulSet [{}] deleted while reconciling because its controller [{}] is unsupported", controllerKind, statefulSetKey);
                    return new Result(false);
                },
                request);
    }

    private void updateStatefulSet(V1StatefulSet target, @Nullable V1Affinity affinity, List<V1Toleration> tolerations) throws ApiException {
        V1StatefulSet updated = new V1StatefulSetBuilder(target)
                .editSpec()
                .editTemplate()
                .editSpec()
                .withAffinity(affinity)
                .withTolerations(tolerations)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        this.appsV1Api.replaceNamespacedStatefulSet(
                K8sObjectUtil.getName(updated),
                K8sObjectUtil.getNamespace(updated),
                updated,
                null,
                null,
                null,
                null);
    }

    private void deleteStatefulSet(String namespace, String name) throws ApiException {
        this.appsV1Api.deleteNamespacedStatefulSet(
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
