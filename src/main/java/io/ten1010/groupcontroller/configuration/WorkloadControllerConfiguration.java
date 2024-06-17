package io.ten1010.groupcontroller.configuration;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.groupcontroller.controller.Reconciliation;
import io.ten1010.groupcontroller.controller.workload.cronjob.CronJobControllerFactory;
import io.ten1010.groupcontroller.controller.workload.daemonset.DaemonSetControllerFactory;
import io.ten1010.groupcontroller.controller.workload.deployment.DeploymentControllerFactory;
import io.ten1010.groupcontroller.controller.workload.job.JobControllerFactory;
import io.ten1010.groupcontroller.controller.workload.pod.PodControllerFactory;
import io.ten1010.groupcontroller.controller.workload.replicaset.ReplicaSetControllerFactory;
import io.ten1010.groupcontroller.controller.workload.replicationcontroller.ReplicationControllerControllerFactory;
import io.ten1010.groupcontroller.controller.workload.statefulset.StatefulSetControllerFactory;
import io.ten1010.groupcontroller.core.K8sApis;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkloadControllerConfiguration {

    @Bean
    public Controller cronJobController(
            SharedInformerFactory sharedInformerFactory,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        Indexer<V1CronJob> cronJobIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1CronJob.class)
                .getIndexer();
        return new CronJobControllerFactory(sharedInformerFactory, cronJobIndexer, reconciliation, k8sApis)
                .create();
    }

    @Bean
    public Controller daemonSetController(
            SharedInformerFactory sharedInformerFactory,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        Indexer<V1DaemonSet> daemonSetIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1DaemonSet.class)
                .getIndexer();
        return new DaemonSetControllerFactory(sharedInformerFactory, daemonSetIndexer, reconciliation, k8sApis)
                .create();
    }

    @Bean
    public Controller deploymentController(
            SharedInformerFactory sharedInformerFactory,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        Indexer<V1Deployment> deploymentIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Deployment.class)
                .getIndexer();
        return new DeploymentControllerFactory(sharedInformerFactory, deploymentIndexer, reconciliation, k8sApis)
                .create();
    }

    @Bean
    public Controller jobController(
            SharedInformerFactory sharedInformerFactory,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        Indexer<V1Job> jobIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Job.class)
                .getIndexer();
        return new JobControllerFactory(sharedInformerFactory, jobIndexer, reconciliation, k8sApis)
                .create();
    }

    @Bean
    public Controller podController(
            SharedInformerFactory sharedInformerFactory,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        Indexer<V1Pod> podIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Pod.class)
                .getIndexer();
        return new PodControllerFactory(sharedInformerFactory, podIndexer, reconciliation, k8sApis)
                .create();
    }

    @Bean
    public Controller replicaSetController(
            SharedInformerFactory sharedInformerFactory,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        Indexer<V1ReplicaSet> replicaSetIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ReplicaSet.class)
                .getIndexer();
        return new ReplicaSetControllerFactory(sharedInformerFactory, replicaSetIndexer, reconciliation, k8sApis)
                .create();
    }

    @Bean
    public Controller replicationControllerController(
            SharedInformerFactory sharedInformerFactory,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        Indexer<V1ReplicationController> replicationControllerIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ReplicationController.class)
                .getIndexer();
        return new ReplicationControllerControllerFactory(sharedInformerFactory, replicationControllerIndexer, reconciliation, k8sApis)
                .create();
    }

    @Bean
    public Controller statefulSetController(
            SharedInformerFactory sharedInformerFactory,
            Reconciliation reconciliation,
            K8sApis k8sApis) {
        Indexer<V1StatefulSet> statefulSetIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1StatefulSet.class)
                .getIndexer();
        return new StatefulSetControllerFactory(sharedInformerFactory, statefulSetIndexer, reconciliation, k8sApis)
                .create();
    }

}
