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
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.core.K8sObjectUtil;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.core.PodUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;

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

    /**
     * Reconcile given pod based on {@link Request} to ensure that pods are running with correct tolerations based on Resource Group which pods belong.
     *
     * @param request the reconcile request, triggered by watch events
     * @return the result
     */
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
                GroupResolver.issueNamespaceConflictWarningEvents(e, this.eventRecorder);
                return new Result(true, INVALID_STATE_REQUEUE_DURATION);
            }

            List<V1Toleration> reconciledTolerations = Reconciliation.reconcileTolerations(pod, groups);
            if (new HashSet<>(PodUtil.getTolerations(pod)).equals(new HashSet<>(reconciledTolerations))) {
                return new Result(false);
            }
            deletePod(K8sObjectUtil.getNamespace(pod), K8sObjectUtil.getName(pod));
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
