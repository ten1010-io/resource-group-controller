package io.ten1010.coaster.groupcontroller.controller.workload.pod;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Affinity;
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
import java.util.Optional;

@Slf4j
public class PodReconciler implements Reconciler {

    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    private KubernetesApiReconcileExceptionHandlingTemplate template;
    private Indexer<V1Pod> podIndexer;
    private GroupResolver groupResolver;
    private CoreV1Api coreV1Api;

    public PodReconciler(Indexer<V1Pod> podIndexer, GroupResolver groupResolver, CoreV1Api coreV1Api) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.podIndexer = podIndexer;
        this.groupResolver = groupResolver;
        this.coreV1Api = coreV1Api;
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
            log.debug("Pod [{}] founded while reconciling\n{}", podKey, pod);

            List<V1ResourceGroup> groups = this.groupResolver.resolve(pod);
            log.debug("GroupResolver resolve Pod [{}] to [{}]", podKey, groups);
            List<V1Toleration> reconciledTolerations = Reconciliation.reconcileTolerations(pod, groups);
            log.debug("Tolerations [{}] of pod [{}] reconciled to Tolerations [{}]", PodUtil.getTolerations(pod), podKey, reconciledTolerations);
            if (!new HashSet<>(PodUtil.getTolerations(pod)).equals(new HashSet<>(reconciledTolerations))) {
                deletePod(K8sObjectUtil.getNamespace(pod), K8sObjectUtil.getName(pod));
                log.debug("Pod [{}] deleted while reconciling because of tolerations", podKey);
                return new Result(false);
            }
            Optional<V1Affinity> reconciledAffinity = Reconciliation.reconcileAffinity(pod, groups);
            log.debug("Affinity [{}] of pod [{}] reconciled to Affinity [{}]", PodUtil.getAffinity(pod), podKey, reconciledAffinity);
            if (reconciledAffinity.isEmpty() && PodUtil.getAffinity(pod).isPresent()) {
                deletePod(K8sObjectUtil.getNamespace(pod), K8sObjectUtil.getName(pod));
                log.debug("Pod [{}] deleted while reconciling because of affinity", podKey);
                return new Result(false);
            }
            if (reconciledAffinity.isPresent() && !reconciledAffinity.get().equals(PodUtil.getAffinity(pod).orElse(null))) {
                deletePod(K8sObjectUtil.getNamespace(pod), K8sObjectUtil.getName(pod));
                log.debug("Pod [{}] deleted while reconciling because of affinity", podKey);
                return new Result(false);
            }
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
