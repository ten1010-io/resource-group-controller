package io.ten1010.coaster.groupcontroller.controller.workload.job;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.controller.Reconciliation;
import io.ten1010.coaster.groupcontroller.core.IndexNames;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.core.Labels;
import io.ten1010.coaster.groupcontroller.core.Taints;
import io.ten1010.coaster.groupcontroller.model.V1Beta1ResourceGroup;
import io.ten1010.coaster.groupcontroller.model.V1Beta1ResourceGroupSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

public class JobReconcilerTest {

    Indexer<V1Job> jobIndexer;
    Indexer<V1Beta1ResourceGroup> groupIndexer;
    Reconciliation reconciliation;
    BatchV1Api batchV1Api;

    @BeforeEach
    void setUp() {
        this.jobIndexer = Mockito.mock(Indexer.class);
        this.groupIndexer = Mockito.mock(Indexer.class);
        this.reconciliation = new Reconciliation(this.groupIndexer);
        this.batchV1Api = Mockito.mock(BatchV1Api.class);
    }

    @Test
    void given_job_does_not_have_toleration_and_affinity_when_it_belongs_to_the_resource_group_then_should_delete_job() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1Job job1 = new V1Job();
        V1ObjectMeta jobMeta1 = new V1ObjectMeta();
        jobMeta1.setNamespace("ns1");
        jobMeta1.setName("job1");
        job1.setMetadata(jobMeta1);
        V1JobSpec jobSpec1 = new V1JobSpec();
        V1PodTemplateSpec podTemplateSpec1 = new V1PodTemplateSpec();
        V1PodSpec podSpec1 = new V1PodSpec();
        podTemplateSpec1.setSpec(podSpec1);
        jobSpec1.setTemplate(podTemplateSpec1);
        job1.setSpec(jobSpec1);
        Mockito.doReturn(List.of(group1))
                .when(this.groupIndexer)
                .byIndex(IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, "ns1");
        Mockito.doReturn(job1).when(this.jobIndexer).getByKey(KeyUtil.buildKey("ns1", "job1"));
        JobReconciler jobReconciler = new JobReconciler(this.jobIndexer, this.reconciliation, this.batchV1Api);
        jobReconciler.reconcile(new Request("ns1", "job1"));
        try {
            Mockito.verify(this.batchV1Api).deleteNamespacedJob(
                    Mockito.eq("job1"),
                    Mockito.eq("ns1"),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null));
            Mockito.verifyNoMoreInteractions(this.batchV1Api);
        } catch (ApiException e) {
            Assertions.fail();
        }
    }

    @Test
    void given_job_does_not_have_toleration_when_it_belongs_to_the_resource_group_then_should_delete_job() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1Job job1 = new V1Job();
        V1ObjectMeta jobMeta1 = new V1ObjectMeta();
        jobMeta1.setNamespace("ns1");
        jobMeta1.setName("job1");
        job1.setMetadata(jobMeta1);
        V1JobSpec jobSpec1 = new V1JobSpec();
        V1PodTemplateSpec podTemplateSpec1 = new V1PodTemplateSpec();
        V1PodSpec podSpec1 = new V1PodSpec();
        V1Affinity affinity1 = new V1AffinityBuilder().withNodeAffinity(
                new V1NodeAffinityBuilder().withRequiredDuringSchedulingIgnoredDuringExecution(
                        new V1NodeSelectorBuilder().withNodeSelectorTerms(
                                new V1NodeSelectorTermBuilder().withMatchExpressions(
                                        new V1NodeSelectorRequirementBuilder()
                                                .withKey(Labels.KEY_RESOURCE_GROUP_EXCLUSIVE)
                                                .withOperator("In")
                                                .withValues("group1")
                                                .build()
                                ).build()
                        ).build()
                ).build()
        ).build();
        podSpec1.setAffinity(affinity1);
        podTemplateSpec1.setSpec(podSpec1);
        jobSpec1.setTemplate(podTemplateSpec1);
        job1.setSpec(jobSpec1);
        Mockito.doReturn(List.of(group1))
                .when(this.groupIndexer)
                .byIndex(IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, "ns1");
        Mockito.doReturn(job1).when(this.jobIndexer).getByKey(KeyUtil.buildKey("ns1", "job1"));
        JobReconciler jobReconciler = new JobReconciler(this.jobIndexer, this.reconciliation, this.batchV1Api);
        jobReconciler.reconcile(new Request("ns1", "job1"));
        try {
            Mockito.verify(this.batchV1Api).deleteNamespacedJob(
                    Mockito.eq("job1"),
                    Mockito.eq("ns1"),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null));
            Mockito.verifyNoMoreInteractions(this.batchV1Api);
        } catch (ApiException e) {
            Assertions.fail();
        }
    }

    @Test
    void given_job_does_not_have_affinity_when_it_belongs_to_the_resource_group_then_should_delete_job() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1Job job1 = new V1Job();
        V1ObjectMeta jobMeta1 = new V1ObjectMeta();
        jobMeta1.setNamespace("ns1");
        jobMeta1.setName("job1");
        job1.setMetadata(jobMeta1);
        V1JobSpec jobSpec1 = new V1JobSpec();
        V1PodTemplateSpec podTemplateSpec1 = new V1PodTemplateSpec();
        V1PodSpec podSpec1 = new V1PodSpec();
        V1TolerationBuilder tolerationBuilder = new V1TolerationBuilder()
                .withKey(Taints.KEY_RESOURCE_GROUP_EXCLUSIVE)
                .withValue("group1")
                .withOperator("Equal");
        podSpec1.setTolerations(List.of(tolerationBuilder.withEffect("NoSchedule").build()));
        podTemplateSpec1.setSpec(podSpec1);
        jobSpec1.setTemplate(podTemplateSpec1);
        job1.setSpec(jobSpec1);
        Mockito.doReturn(List.of(group1))
                .when(this.groupIndexer)
                .byIndex(IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, "ns1");
        Mockito.doReturn(job1).when(this.jobIndexer).getByKey(KeyUtil.buildKey("ns1", "job1"));
        JobReconciler jobReconciler = new JobReconciler(this.jobIndexer, this.reconciliation, this.batchV1Api);
        jobReconciler.reconcile(new Request("ns1", "job1"));
        try {
            Mockito.verify(this.batchV1Api).deleteNamespacedJob(
                    Mockito.eq("job1"),
                    Mockito.eq("ns1"),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null));
            Mockito.verifyNoMoreInteractions(this.batchV1Api);
        } catch (ApiException e) {
            Assertions.fail();
        }
    }

    @Test
    void given_job_has_unsupported_controller_then_should_delete_job() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1Job job1 = new V1Job();
        V1ObjectMeta jobMeta1 = new V1ObjectMeta();
        jobMeta1.setNamespace("ns1");
        jobMeta1.setName("job1");
        job1.setMetadata(jobMeta1);
        V1JobSpec jobSpec1 = new V1JobSpec();
        V1PodTemplateSpec podTemplateSpec1 = new V1PodTemplateSpec();
        V1PodSpec podSpec1 = new V1PodSpec();
        V1TolerationBuilder tolerationBuilder = new V1TolerationBuilder()
                .withKey(Taints.KEY_RESOURCE_GROUP_EXCLUSIVE)
                .withValue("group1")
                .withOperator("Equal");
        podSpec1.setTolerations(List.of(tolerationBuilder.withEffect("NoSchedule").build()));
        V1Affinity affinity1 = new V1AffinityBuilder().withNodeAffinity(
                new V1NodeAffinityBuilder().withRequiredDuringSchedulingIgnoredDuringExecution(
                        new V1NodeSelectorBuilder().withNodeSelectorTerms(
                                new V1NodeSelectorTermBuilder().withMatchExpressions(
                                        new V1NodeSelectorRequirementBuilder()
                                                .withKey(Labels.KEY_RESOURCE_GROUP_EXCLUSIVE)
                                                .withOperator("In")
                                                .withValues("group1")
                                                .build()
                                ).build()
                        ).build()
                ).build()
        ).build();
        podSpec1.setAffinity(affinity1);
        podTemplateSpec1.setSpec(podSpec1);
        jobSpec1.setTemplate(podTemplateSpec1);
        job1.setSpec(jobSpec1);
        V1OwnerReference ownerReference1 = new V1OwnerReference();
        ownerReference1.setApiVersion("v1");
        ownerReference1.setKind("dummy");
        ownerReference1.setController(true);
        job1.getMetadata().setOwnerReferences(List.of(ownerReference1));
        Mockito.doReturn(List.of(group1))
                .when(this.groupIndexer)
                .byIndex(IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, "ns1");
        Mockito.doReturn(job1).when(this.jobIndexer).getByKey(KeyUtil.buildKey("ns1", "job1"));
        JobReconciler jobReconciler = new JobReconciler(this.jobIndexer, this.reconciliation, this.batchV1Api);
        jobReconciler.reconcile(new Request("ns1", "job1"));
        try {
            Mockito.verify(this.batchV1Api).deleteNamespacedJob(
                    Mockito.eq("job1"),
                    Mockito.eq("ns1"),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null));
            Mockito.verifyNoMoreInteractions(this.batchV1Api);
        } catch (ApiException e) {
            Assertions.fail();
        }
    }

}
