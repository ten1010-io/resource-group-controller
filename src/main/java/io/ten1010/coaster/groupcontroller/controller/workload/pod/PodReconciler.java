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
import io.ten1010.coaster.groupcontroller.controller.ControllerSupport;
import io.ten1010.coaster.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.core.ApiResourceKind;
import io.ten1010.coaster.groupcontroller.core.K8sObjectUtil;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.core.PodUtil;
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
    private Reconciliation reconciliation;
    private CoreV1Api coreV1Api;

    public PodReconciler(Indexer<V1Pod> podIndexer, Reconciliation reconciliation, CoreV1Api coreV1Api) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.podIndexer = podIndexer;
        this.reconciliation = reconciliation;
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

            if (!K8sObjectUtil.isControlled(pod)) {
                Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledPodAffinity(pod);
                log.debug("Affinity [{}] of pod [{}] reconciled to Affinity [{}]", PodUtil.getAffinity(pod), podKey, reconciledAffinity.orElse(null));
                if (!PodUtil.getAffinity(pod).equals(reconciledAffinity)) {
                    deletePod(K8sObjectUtil.getNamespace(pod), K8sObjectUtil.getName(pod));
                    log.debug("Pod [{}] deleted while reconciling because of affinity", podKey);
                    return new Result(false);
                }
                List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledPodTolerations(pod);
                log.debug("Tolerations [{}] of pod [{}] reconciled to Tolerations [{}]", PodUtil.getTolerations(pod), podKey, reconciledTolerations);
                if (!new HashSet<>(PodUtil.getTolerations(pod)).equals(new HashSet<>(reconciledTolerations))) {
                    deletePod(K8sObjectUtil.getNamespace(pod), K8sObjectUtil.getName(pod));
                    log.debug("Pod [{}] deleted while reconciling because of tolerations", podKey);
                    return new Result(false);
                }
                return new Result(false);
            }
            ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(pod));
            if (ControllerSupport.isSupportedControllerOfPod(controllerKind)) {
                return new Result(false);
            }
            deletePod(K8sObjectUtil.getNamespace(pod), K8sObjectUtil.getName(pod));
            log.debug("Pod [{}] deleted while reconciling because its controller [{}] is unsupported", controllerKind, podKey);
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
