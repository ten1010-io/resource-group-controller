package io.ten1010.coaster.groupcontroller.controller.pod;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.coaster.groupcontroller.controller.GroupResolver;
import io.ten1010.coaster.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.coaster.groupcontroller.controller.ReconcilerUtil;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Slf4j
public class PodReconciler implements Reconciler {

    public static final Duration INVALID_STATE_REQUEUE_DURATION = Duration.ofSeconds(60);
    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    private KubernetesApiReconcileExceptionHandlingTemplate template;
    private Indexer<V1Pod> podIndexer;
    private GroupResolver groupResolver;
    private CoreV1Api coreV1Api;
    private EventRecorder eventRecorder;

    public PodReconciler(Indexer<V1Pod> podIndexer, GroupResolver groupResolver, CoreV1Api coreV1Api, EventRecorder eventRecorder) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.podIndexer = podIndexer;
        this.groupResolver = groupResolver;
        this.coreV1Api = coreV1Api;
        this.eventRecorder = eventRecorder;
    }

    @Override
    public Result reconcile(Request request) {
        return this.template.execute(() -> {
            String podKey = KeyUtil.buildKey(request.getNamespace(), request.getName());
            V1Pod pod = this.podIndexer.getByKey(podKey);
            if (pod == null) {
                log.debug("Pod [{}] not founded while reconciling", podKey);
                return new Result(false);
            }
            log.debug("Pod [{}] founded while reconciling\n{}", podKey, pod.toString());

            List<V1ResourceGroup> groups;
            try {
                groups = this.groupResolver.resolve(pod);
            } catch (GroupResolver.NamespaceConflictException e) {
                ReconcilerUtil.issueWarningEvents(e, this.eventRecorder);
                return new Result(true, INVALID_STATE_REQUEUE_DURATION);
            }

            List<V1Toleration> allTolerations = ReconcilerUtil.getTolerations(pod);
            List<V1Toleration> reconciledTolerations = ReconcilerUtil.reconcileTolerations(allTolerations, groups);
            if (Set.of(allTolerations).equals(Set.of(reconciledTolerations))) {
                return new Result(false);
            }
            deletePod(ReconcilerUtil.getNamespace(pod), ReconcilerUtil.getName(pod));
            return new Result(false);
        }, request);
    }

    private void deletePod(String namespace, String name) throws ApiException {
        this.coreV1Api.deleteNamespacedPod(
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
