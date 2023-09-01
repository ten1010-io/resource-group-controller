package io.ten1010.coaster.groupcontroller.controller.workload.job;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.coaster.groupcontroller.controller.ControllerSupport;
import io.ten1010.coaster.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.core.ApiResourceKind;
import io.ten1010.coaster.groupcontroller.core.JobUtil;
import io.ten1010.coaster.groupcontroller.core.K8sObjectUtil;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
public class JobReconciler implements Reconciler {

    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    private KubernetesApiReconcileExceptionHandlingTemplate template;
    private Indexer<V1Job> jobIndexer;
    private Reconciliation reconciliation;
    private BatchV1Api batchV1Api;

    public JobReconciler(Indexer<V1Job> jobIndexer, Reconciliation reconciliation, BatchV1Api batchV1Api) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.jobIndexer = jobIndexer;
        this.reconciliation = reconciliation;
        this.batchV1Api = batchV1Api;
    }

    @Override
    public Result reconcile(Request request) {
        return this.template.execute(() -> {
            String jobKey = KeyUtil.buildKey(request.getNamespace(), request.getName());
            V1Job job = this.jobIndexer.getByKey(jobKey);
            if (job == null) {
                log.debug("Job [{}] not founded while reconciling", jobKey);
                return new Result(false);
            }
            log.debug("Job [{}] founded while reconciling\n{}", jobKey, job);

            if (!K8sObjectUtil.isControlled(job)) {
                Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledJobAffinity(job);
                log.debug("Affinity [{}] of job [{}] reconciled to Affinity [{}]", JobUtil.getAffinity(job), jobKey, reconciledAffinity.orElse(null));
                if (!JobUtil.getAffinity(job).equals(reconciledAffinity)) {
                    deleteJob(K8sObjectUtil.getNamespace(job), K8sObjectUtil.getName(job));
                    log.debug("Job [{}] deleted while reconciling because of affinity", jobKey);
                    return new Result(false);
                }
                List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledJobTolerations(job);
                log.debug("Tolerations [{}] of job [{}] reconciled to Tolerations [{}]", JobUtil.getTolerations(job), jobKey, reconciledTolerations);
                if (!new HashSet<>(JobUtil.getTolerations(job)).equals(new HashSet<>(reconciledTolerations))) {
                    deleteJob(K8sObjectUtil.getNamespace(job), K8sObjectUtil.getName(job));
                    log.debug("Job [{}] deleted while reconciling because of tolerations", jobKey);
                    return new Result(false);
                }
                return new Result(false);
            }
            ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(job));
            if (ControllerSupport.isSupportedControllerOfJob(controllerKind)) {
                return new Result(false);
            }
            deleteJob(K8sObjectUtil.getNamespace(job), K8sObjectUtil.getName(job));
            log.debug("Job [{}] deleted while reconciling because its controller [{}] is unsupported", controllerKind, jobKey);
            return new Result(false);
        }, request);
    }

    private void deleteJob(String namespace, String name) throws ApiException {
        this.batchV1Api.deleteNamespacedJob(
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
