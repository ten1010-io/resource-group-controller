package io.ten1010.coaster.groupcontroller.controller.workload.cronjob;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1CronJobBuilder;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.coaster.groupcontroller.controller.ControllerSupport;
import io.ten1010.coaster.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.core.ApiResourceKind;
import io.ten1010.coaster.groupcontroller.core.CronJobUtil;
import io.ten1010.coaster.groupcontroller.core.K8sObjectUtil;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
public class CronJobReconciler implements Reconciler {

    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    private KubernetesApiReconcileExceptionHandlingTemplate template;
    private Indexer<V1CronJob> cronJobIndexer;
    private Reconciliation reconciliation;
    private BatchV1Api batchV1Api;

    public CronJobReconciler(Indexer<V1CronJob> cronJobIndexer, Reconciliation reconciliation, BatchV1Api batchV1Api) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.cronJobIndexer = cronJobIndexer;
        this.reconciliation = reconciliation;
        this.batchV1Api = batchV1Api;
    }

    @Override
    public Result reconcile(Request request) {
        return this.template.execute(
                () -> {
                    String cronJobKey = KeyUtil.buildKey(request.getNamespace(), request.getName());
                    V1CronJob cronJob = this.cronJobIndexer.getByKey(cronJobKey);
                    if (cronJob == null) {
                        log.debug("CronJob [{}] not founded while reconciling", cronJobKey);
                        return new Result(false);
                    }
                    log.debug("CronJob [{}] founded while reconciling\n{}", cronJobKey, cronJob);

                    if (!K8sObjectUtil.isControlled(cronJob)) {
                        boolean updated = false;
                        Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledCronJobAffinity(cronJob);
                        log.debug("Affinity [{}] of cronJob [{}] reconciled to Affinity [{}]",
                                CronJobUtil.getAffinity(cronJob),
                                cronJobKey,
                                reconciledAffinity.orElse(null));
                        if (!CronJobUtil.getAffinity(cronJob).equals(reconciledAffinity)) {
                            updated = true;
                            log.debug("CronJob [{}] updated while reconciling because of affinity", cronJobKey);
                        }
                        List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledCronJobTolerations(cronJob);
                        log.debug("Tolerations [{}] of cronJob [{}] reconciled to Tolerations [{}]",
                                CronJobUtil.getTolerations(cronJob),
                                cronJobKey,
                                reconciledTolerations);
                        if (!new HashSet<>(CronJobUtil.getTolerations(cronJob)).equals(new HashSet<>(reconciledTolerations))) {
                            updated = true;
                            log.debug("CronJob [{}] updated while reconciling because of tolerations", cronJobKey);
                        }
                        if (updated) {
                            updateCronJob(cronJob, reconciledAffinity.orElse(null), reconciledTolerations);
                        }
                        return new Result(false);
                    }
                    ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(cronJob));
                    if (ControllerSupport.isSupportedControllerOfCronJob(controllerKind)) {
                        return new Result(false);
                    }
                    deleteCronJob(K8sObjectUtil.getNamespace(cronJob), K8sObjectUtil.getName(cronJob));
                    log.debug("CronJob [{}] deleted while reconciling because its controller [{}] is unsupported", controllerKind, cronJobKey);
                    return new Result(false);
                },
                request);
    }

    private void updateCronJob(V1CronJob target, @Nullable V1Affinity affinity, List<V1Toleration> tolerations) throws ApiException {
        V1CronJob updated = new V1CronJobBuilder(target)
                .editSpec()
                .editJobTemplate()
                .editSpec()
                .editTemplate()
                .editSpec()
                .withAffinity(affinity)
                .withTolerations(tolerations)
                .endSpec()
                .endTemplate()
                .endSpec()
                .endJobTemplate()
                .endSpec()
                .build();
        this.batchV1Api.replaceNamespacedCronJob(
                K8sObjectUtil.getName(updated),
                K8sObjectUtil.getNamespace(updated),
                updated,
                null,
                null,
                null,
                null);
    }

    private void deleteCronJob(String namespace, String name) throws ApiException {
        this.batchV1Api.deleteNamespacedCronJob(
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
