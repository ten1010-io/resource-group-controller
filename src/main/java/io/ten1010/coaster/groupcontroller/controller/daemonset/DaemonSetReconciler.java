package io.ten1010.coaster.groupcontroller.controller.daemonset;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1DaemonSetBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.coaster.groupcontroller.controller.GroupResolver;
import io.ten1010.coaster.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.coaster.groupcontroller.controller.ReconcilerUtil;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
public class DaemonSetReconciler implements Reconciler {

    public static final Duration INVALID_STATE_REQUEUE_DURATION = Duration.ofSeconds(60);
    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    static List<V1Toleration> getTolerations(V1DaemonSet daemonSet) {
        if (daemonSet.getSpec() == null ||
                daemonSet.getSpec().getTemplate() == null ||
                daemonSet.getSpec().getTemplate().getSpec() == null ||
                daemonSet.getSpec().getTemplate().getSpec().getTolerations() == null) {
            return new ArrayList<>();
        }

        return daemonSet.getSpec().getTemplate().getSpec().getTolerations();
    }

    private KubernetesApiReconcileExceptionHandlingTemplate template;
    private Indexer<V1DaemonSet> daemonSetIndexer;
    private GroupResolver groupResolver;
    private AppsV1Api appsV1Api;
    private EventRecorder eventRecorder;

    public DaemonSetReconciler(Indexer<V1DaemonSet> daemonSetIndexer, GroupResolver groupResolver, AppsV1Api appsV1Api, EventRecorder eventRecorder) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.daemonSetIndexer = daemonSetIndexer;
        this.groupResolver = groupResolver;
        this.appsV1Api = appsV1Api;
        this.eventRecorder = eventRecorder;
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
                    log.debug("v [{}] founded while reconciling\n{}", daemonSetKey, daemonSet.toString());

                    List<V1ResourceGroup> groups;
                    groups = this.groupResolver.resolve(daemonSet);

                    List<V1Toleration> tolerations = getTolerations(daemonSet);
                    List<V1Toleration> reconciledTolerations = ReconcilerUtil.reconcileTolerations(tolerations, groups);
                    if (tolerations.equals(reconciledTolerations)) {
                        return new Result(false);
                    }
                    updateDaemonSet(daemonSet, reconciledTolerations);
                    return new Result(false);
                },
                request);
    }

    private void updateDaemonSet(V1DaemonSet target, List<V1Toleration> tolerations) throws ApiException {
        V1DaemonSetBuilder builder = new V1DaemonSetBuilder(target);
        V1DaemonSet updated = builder
                .editSpec()
                .editTemplate()
                .editSpec()
                .withTolerations(tolerations)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        V1ObjectMeta meta = target.getMetadata();
        Objects.requireNonNull(meta);
        Objects.requireNonNull(meta.getNamespace());
        Objects.requireNonNull(meta.getName());
        this.appsV1Api.replaceNamespacedDaemonSet(meta.getName(), meta.getNamespace(), updated, null, null, null, null);
    }

}
