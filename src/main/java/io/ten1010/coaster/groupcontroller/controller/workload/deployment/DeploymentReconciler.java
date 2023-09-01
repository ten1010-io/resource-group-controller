package io.ten1010.coaster.groupcontroller.controller.workload.deployment;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentBuilder;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.coaster.groupcontroller.controller.ControllerSupport;
import io.ten1010.coaster.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.core.ApiResourceKind;
import io.ten1010.coaster.groupcontroller.core.DeploymentUtil;
import io.ten1010.coaster.groupcontroller.core.K8sObjectUtil;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
public class DeploymentReconciler implements Reconciler {

    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    private KubernetesApiReconcileExceptionHandlingTemplate template;
    private Indexer<V1Deployment> deploymentIndexer;
    private Reconciliation reconciliation;
    private AppsV1Api appsV1Api;

    public DeploymentReconciler(Indexer<V1Deployment> deploymentIndexer, Reconciliation reconciliation, AppsV1Api appsV1Api) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.deploymentIndexer = deploymentIndexer;
        this.reconciliation = reconciliation;
        this.appsV1Api = appsV1Api;
    }

    @Override
    public Result reconcile(Request request) {
        return this.template.execute(
                () -> {
                    String deploymentKey = KeyUtil.buildKey(request.getNamespace(), request.getName());
                    V1Deployment deployment = this.deploymentIndexer.getByKey(deploymentKey);
                    if (deployment == null) {
                        log.debug("Deployment [{}] not founded while reconciling", deploymentKey);
                        return new Result(false);
                    }
                    log.debug("Deployment [{}] founded while reconciling\n{}", deploymentKey, deployment);

                    if (!K8sObjectUtil.isControlled(deployment)) {
                        boolean updated = false;
                        Optional<V1Affinity> reconciledAffinity = this.reconciliation.reconcileUncontrolledDeploymentAffinity(deployment);
                        log.debug("Affinity [{}] of deployment [{}] reconciled to Affinity [{}]",
                                DeploymentUtil.getAffinity(deployment),
                                deploymentKey,
                                reconciledAffinity.orElse(null));
                        if (!DeploymentUtil.getAffinity(deployment).equals(reconciledAffinity)) {
                            updated = true;
                            log.debug("Deployment [{}] updated while reconciling because of affinity", deploymentKey);
                        }
                        List<V1Toleration> reconciledTolerations = this.reconciliation.reconcileUncontrolledDeploymentTolerations(deployment);
                        log.debug("Tolerations [{}] of deployment [{}] reconciled to Tolerations [{}]",
                                DeploymentUtil.getTolerations(deployment),
                                deploymentKey,
                                reconciledTolerations);
                        if (!new HashSet<>(DeploymentUtil.getTolerations(deployment)).equals(new HashSet<>(reconciledTolerations))) {
                            updated = true;
                            log.debug("Deployment [{}] updated while reconciling because of tolerations", deploymentKey);
                        }
                        if (updated) {
                            updateDeployment(deployment, reconciledAffinity.orElse(null), reconciledTolerations);
                        }
                        return new Result(false);
                    }
                    ApiResourceKind controllerKind = K8sObjectUtil.getApiResourceKind(K8sObjectUtil.getControllerReference(deployment));
                    if (ControllerSupport.isSupportedControllerOfDeployment(controllerKind)) {
                        return new Result(false);
                    }
                    deleteDeployment(K8sObjectUtil.getNamespace(deployment), K8sObjectUtil.getName(deployment));
                    log.debug("Deployment [{}] deleted while reconciling because its controller [{}] is unsupported", controllerKind, deploymentKey);
                    return new Result(false);
                },
                request);
    }

    private void updateDeployment(V1Deployment target, @Nullable V1Affinity affinity, List<V1Toleration> tolerations) throws ApiException {
        V1Deployment updated = new V1DeploymentBuilder(target)
                .editSpec()
                .editTemplate()
                .editSpec()
                .withAffinity(affinity)
                .withTolerations(tolerations)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        this.appsV1Api.replaceNamespacedDeployment(
                K8sObjectUtil.getName(updated),
                K8sObjectUtil.getNamespace(updated),
                updated,
                null,
                null,
                null,
                null);
    }

    private void deleteDeployment(String namespace, String name) throws ApiException {
        this.appsV1Api.deleteNamespacedDeployment(
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
