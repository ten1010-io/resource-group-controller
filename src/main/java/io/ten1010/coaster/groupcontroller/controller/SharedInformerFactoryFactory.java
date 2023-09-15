package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.core.*;
import io.ten1010.coaster.groupcontroller.model.V1Beta2DaemonSet;
import io.ten1010.coaster.groupcontroller.model.V1Beta2ResourceGroup;
import io.ten1010.coaster.groupcontroller.model.V1Beta2ResourceGroupSpec;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SharedInformerFactoryFactory {

    public static long RESYNC_PERIOD_IN_MILLIS = 30000;

    private static Map<String, Function<V1Beta2ResourceGroup, List<String>>> byNodeNameToGroupObject() {
        return Map.of(IndexNames.BY_NODE_NAME_TO_GROUP_OBJECT, ResourceGroupUtil::getNodes);
    }

    private static Map<String, Function<V1Beta2ResourceGroup, List<String>>> byNamespaceNameToGroupObject() {
        return Map.of(IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, ResourceGroupUtil::getNamespaces);
    }

    private static Map<String, Function<V1Beta2ResourceGroup, List<String>>> byDaemonSetKeyToGroupObject() {
        return Map.of(IndexNames.BY_DAEMON_SET_KEY_TO_GROUP_OBJECT, object -> ResourceGroupUtil.getDaemonSets(object).stream()
                .map(KeyUtil::getKey)
                .collect(Collectors.toList()));
    }

    private static Map<String, Function<V1Beta2ResourceGroup, List<String>>> byGroupAllowAllDaemonSetToGroupObject() {
        return Map.of(IndexNames.BY_GROUP_ALLOW_ALL_DAEMON_SET_TO_GROUP_OBJECT, group -> List.of(
                Optional.ofNullable(group.getSpec())
                        .map(V1Beta2ResourceGroupSpec::getDaemonSet)
                        .map(V1Beta2DaemonSet::isAllowAll)
                        .map(v -> Boolean.toString(v))
                        .orElseThrow()
        ));
    }

    private static Map<String, Function<V1CronJob, List<String>>> byNamespaceNameToCronJobObject() {
        return Map.of(IndexNames.BY_NAMESPACE_NAME_TO_CRON_JOB_OBJECT,
                object -> List.of(K8sObjectUtil.getNamespace(object)));
    }

    private static Map<String, Function<V1DaemonSet, List<String>>> byNamespaceNameToDaemonSetObject() {
        return Map.of(IndexNames.BY_NAMESPACE_NAME_TO_DAEMON_SET_OBJECT,
                object -> List.of(K8sObjectUtil.getNamespace(object)));
    }

    private static Map<String, Function<V1Deployment, List<String>>> byNamespaceNameToDeploymentObject() {
        return Map.of(IndexNames.BY_NAMESPACE_NAME_TO_DEPLOYMENT_OBJECT,
                object -> List.of(K8sObjectUtil.getNamespace(object)));
    }

    private static Map<String, Function<V1Job, List<String>>> byNamespaceNameToJobObject() {
        return Map.of(IndexNames.BY_NAMESPACE_NAME_TO_JOB_OBJECT,
                object -> List.of(K8sObjectUtil.getNamespace(object)));
    }

    private static Map<String, Function<V1Pod, List<String>>> byNamespaceNameToPodObject() {
        return Map.of(IndexNames.BY_NAMESPACE_NAME_TO_POD_OBJECT,
                object -> List.of(K8sObjectUtil.getNamespace(object)));
    }

    private static Map<String, Function<V1ReplicaSet, List<String>>> byNamespaceNameToReplicaSetObject() {
        return Map.of(IndexNames.BY_NAMESPACE_NAME_TO_REPLICA_SET_OBJECT,
                object -> List.of(K8sObjectUtil.getNamespace(object)));
    }

    private static Map<String, Function<V1ReplicationController, List<String>>> byNamespaceNameToReplicationControllerObject() {
        return Map.of(IndexNames.BY_NAMESPACE_NAME_TO_REPLICATION_CONTROLLER_OBJECT,
                object -> List.of(K8sObjectUtil.getNamespace(object)));
    }

    private static Map<String, Function<V1StatefulSet, List<String>>> byNamespaceNameToStatefulSetObject() {
        return Map.of(IndexNames.BY_NAMESPACE_NAME_TO_STATEFUL_SET_OBJECT,
                object -> List.of(K8sObjectUtil.getNamespace(object)));
    }

    private K8sApis k8sApis;

    public SharedInformerFactoryFactory(K8sApis k8sApis) {
        this.k8sApis = k8sApis;
    }

    public SharedInformerFactory create() {
        SharedInformerFactory informerFactory = new SharedInformerFactory(this.k8sApis.getApiClient());
        SharedIndexInformer<V1Beta2ResourceGroup> groupInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getResourceGroupApi(),
                V1Beta2ResourceGroup.class,
                RESYNC_PERIOD_IN_MILLIS);
        groupInformer.addIndexers(byNodeNameToGroupObject());
        groupInformer.addIndexers(byNamespaceNameToGroupObject());
        groupInformer.addIndexers(byDaemonSetKeyToGroupObject());
        groupInformer.addIndexers(byGroupAllowAllDaemonSetToGroupObject());

        SharedIndexInformer<V1CronJob> cronJobInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getCronJobApi(),
                V1CronJob.class,
                RESYNC_PERIOD_IN_MILLIS);
        cronJobInformer.addIndexers(byNamespaceNameToCronJobObject());
        SharedIndexInformer<V1DaemonSet> daemonSetInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getDaemonSetApi(),
                V1DaemonSet.class,
                RESYNC_PERIOD_IN_MILLIS);
        daemonSetInformer.addIndexers(byNamespaceNameToDaemonSetObject());
        SharedIndexInformer<V1Deployment> deploymentInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getDeploymentApi(),
                V1Deployment.class,
                RESYNC_PERIOD_IN_MILLIS);
        deploymentInformer.addIndexers(byNamespaceNameToDeploymentObject());
        SharedIndexInformer<V1Job> jobInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getJobApi(),
                V1Job.class,
                RESYNC_PERIOD_IN_MILLIS);
        jobInformer.addIndexers(byNamespaceNameToJobObject());
        SharedIndexInformer<V1Pod> podInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getPodApi(),
                V1Pod.class,
                RESYNC_PERIOD_IN_MILLIS);
        podInformer.addIndexers(byNamespaceNameToPodObject());
        SharedIndexInformer<V1ReplicaSet> replicaSetInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getReplicaSetApi(),
                V1ReplicaSet.class,
                RESYNC_PERIOD_IN_MILLIS);
        replicaSetInformer.addIndexers(byNamespaceNameToReplicaSetObject());
        SharedIndexInformer<V1ReplicationController> replicationControllerInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getReplicationControllerApi(),
                V1ReplicationController.class,
                RESYNC_PERIOD_IN_MILLIS);
        replicationControllerInformer.addIndexers(byNamespaceNameToReplicationControllerObject());
        SharedIndexInformer<V1StatefulSet> statefulSetInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getStatefulSetApi(),
                V1StatefulSet.class,
                RESYNC_PERIOD_IN_MILLIS);
        statefulSetInformer.addIndexers(byNamespaceNameToStatefulSetObject());

        SharedIndexInformer<V1Node> nodeInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getNodeApi(),
                V1Node.class,
                RESYNC_PERIOD_IN_MILLIS);
        SharedIndexInformer<V1Role> roleInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getRoleApi(),
                V1Role.class,
                RESYNC_PERIOD_IN_MILLIS);
        SharedIndexInformer<V1RoleBinding> roleBindingInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getRoleBindingApi(),
                V1RoleBinding.class,
                RESYNC_PERIOD_IN_MILLIS);
        SharedIndexInformer<V1ClusterRole> clusterRoleInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getClusterRoleApi(),
                V1ClusterRole.class,
                RESYNC_PERIOD_IN_MILLIS);
        SharedIndexInformer<V1ClusterRoleBinding> clusterRoleBindingInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getClusterRoleBindingApi(),
                V1ClusterRoleBinding.class,
                RESYNC_PERIOD_IN_MILLIS);
        SharedIndexInformer<V1Namespace> namespaceInformer = informerFactory.sharedIndexInformerFor(
                this.k8sApis.getNamespaceApi(),
                V1Namespace.class,
                RESYNC_PERIOD_IN_MILLIS);
        return informerFactory;
    }

}
