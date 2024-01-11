package io.ten1010.coaster.groupcontroller.controller.workload.cronjob;

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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CronJobReconcilerTest {

    Indexer<V1CronJob> cronJobIndexer;
    Indexer<V1Beta1ResourceGroup> groupIndexer;
    Reconciliation reconciliation;
    BatchV1Api batchV1Api;

    @BeforeEach
    void setUp() {
        this.cronJobIndexer = Mockito.mock(Indexer.class);
        this.groupIndexer = Mockito.mock(Indexer.class);
        this.reconciliation = new Reconciliation(this.groupIndexer);
        this.batchV1Api = Mockito.mock(BatchV1Api.class);
    }

    @Test
    void given_cronjob_does_not_have_toleration_and_affinity_when_it_belongs_to_the_resource_group_then_should_update_cronjob() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1CronJob cronJob1 = new V1CronJob();
        V1ObjectMeta cronJobMeta1 = new V1ObjectMeta();
        cronJobMeta1.setNamespace("ns1");
        cronJobMeta1.setName("cronjob1");
        cronJob1.setMetadata(cronJobMeta1);
        V1CronJobSpec cronJobSpec1 = new V1CronJobSpec();
        V1JobTemplateSpec jobTemplateSpec1 = new V1JobTemplateSpec();
        V1ObjectMeta jobMeta1 = new V1ObjectMeta();
        jobMeta1.setNamespace("ns1");
        jobMeta1.setName("cj1");
        jobTemplateSpec1.setMetadata(jobMeta1);
        V1JobSpec jobSpec1 = new V1JobSpec();
        V1PodTemplateSpec podTemplateSpec1 = new V1PodTemplateSpec();
        V1PodSpec podSpec1 = new V1PodSpec();
        podTemplateSpec1.setSpec(podSpec1);
        jobSpec1.setTemplate(podTemplateSpec1);
        jobTemplateSpec1.setSpec(jobSpec1);
        cronJobSpec1.setJobTemplate(jobTemplateSpec1);
        cronJob1.setSpec(cronJobSpec1);
        Mockito.doReturn(List.of(group1))
                .when(this.groupIndexer)
                .byIndex(IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, "ns1");
        Mockito.doReturn(cronJob1).when(this.cronJobIndexer).getByKey(KeyUtil.buildKey("ns1", "cronjob1"));
        CronJobReconciler cronJobReconciler = new CronJobReconciler(this.cronJobIndexer, this.reconciliation, this.batchV1Api);
        cronJobReconciler.reconcile(new Request("ns1", "cronjob1"));
        try {
            Mockito.verify(this.batchV1Api).replaceNamespacedCronJob(
                    Mockito.eq("cronjob1"),
                    Mockito.eq("ns1"),
                    Mockito.argThat(cronJob -> {
                        List<String> tolerationValues = cronJob.getSpec().getJobTemplate().getSpec()
                                .getTemplate().getSpec().getTolerations().stream()
                                .filter(e -> {
                                    if (e.getKey() == null) {
                                        return false;
                                    }
                                    return e.getKey().equals(Taints.KEY_RESOURCE_GROUP_EXCLUSIVE);
                                })
                                .map(V1Toleration::getValue)
                                .collect(Collectors.toList());
                        Set<String> valueSet = new HashSet<>(tolerationValues);
                        if (!valueSet.equals(Set.of("group1"))) {
                            return false;
                        }
                        Set<String> affinityValues = cronJob.getSpec().getJobTemplate().getSpec()
                                .getTemplate().getSpec().getAffinity().getNodeAffinity()
                                .getRequiredDuringSchedulingIgnoredDuringExecution().getNodeSelectorTerms().stream()
                                .flatMap(nst -> nst.getMatchExpressions().stream())
                                .filter(nsr -> nsr.getKey().equals(Labels.KEY_RESOURCE_GROUP_EXCLUSIVE))
                                .flatMap(nsr -> nsr.getValues().stream())
                                .collect(Collectors.toSet());
                        if (!affinityValues.equals(Set.of("group1"))) {
                            return false;
                        }
                        return true;
                    }),
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
    void given_cronjob_does_not_have_toleration_when_it_belongs_to_the_resource_group_then_should_update_cronjob() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1CronJob cronJob1 = new V1CronJob();
        V1ObjectMeta cronJobMeta1 = new V1ObjectMeta();
        cronJobMeta1.setNamespace("ns1");
        cronJobMeta1.setName("cronjob1");
        cronJob1.setMetadata(cronJobMeta1);
        V1CronJobSpec cronJobSpec1 = new V1CronJobSpec();
        V1JobTemplateSpec jobTemplateSpec1 = new V1JobTemplateSpec();
        V1ObjectMeta jobMeta1 = new V1ObjectMeta();
        jobMeta1.setNamespace("ns1");
        jobMeta1.setName("cj1");
        jobTemplateSpec1.setMetadata(jobMeta1);
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
        jobTemplateSpec1.setSpec(jobSpec1);
        cronJobSpec1.setJobTemplate(jobTemplateSpec1);
        cronJob1.setSpec(cronJobSpec1);
        Mockito.doReturn(List.of(group1))
                .when(this.groupIndexer)
                .byIndex(IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, "ns1");
        Mockito.doReturn(cronJob1).when(this.cronJobIndexer).getByKey(KeyUtil.buildKey("ns1", "cronjob1"));
        CronJobReconciler cronJobReconciler = new CronJobReconciler(this.cronJobIndexer, this.reconciliation, this.batchV1Api);
        cronJobReconciler.reconcile(new Request("ns1", "cronjob1"));
        try {
            Mockito.verify(this.batchV1Api).replaceNamespacedCronJob(
                    Mockito.eq("cronjob1"),
                    Mockito.eq("ns1"),
                    Mockito.argThat(cronJob -> {
                        List<String> tolerationValues = cronJob.getSpec().getJobTemplate().getSpec()
                                .getTemplate().getSpec().getTolerations().stream()
                                .filter(e -> {
                                    if (e.getKey() == null) {
                                        return false;
                                    }
                                    return e.getKey().equals(Taints.KEY_RESOURCE_GROUP_EXCLUSIVE);
                                })
                                .map(V1Toleration::getValue)
                                .collect(Collectors.toList());
                        Set<String> valueSet = new HashSet<>(tolerationValues);
                        if (!valueSet.equals(Set.of("group1"))) {
                            return false;
                        }
                        Set<String> affinityValues = cronJob.getSpec().getJobTemplate().getSpec()
                                .getTemplate().getSpec().getAffinity().getNodeAffinity()
                                .getRequiredDuringSchedulingIgnoredDuringExecution().getNodeSelectorTerms().stream()
                                .flatMap(nst -> nst.getMatchExpressions().stream())
                                .filter(nsr -> nsr.getKey().equals(Labels.KEY_RESOURCE_GROUP_EXCLUSIVE))
                                .flatMap(nsr -> nsr.getValues().stream())
                                .collect(Collectors.toSet());
                        if (!affinityValues.equals(Set.of("group1"))) {
                            return false;
                        }
                        return true;
                    }),
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
    void given_cronjob_does_not_have_affinity_when_it_belongs_to_the_resource_group_then_should_update_cronjob() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1CronJob cronJob1 = new V1CronJob();
        V1ObjectMeta cronJobMeta1 = new V1ObjectMeta();
        cronJobMeta1.setNamespace("ns1");
        cronJobMeta1.setName("cronjob1");
        cronJob1.setMetadata(cronJobMeta1);
        V1CronJobSpec cronJobSpec1 = new V1CronJobSpec();
        V1JobTemplateSpec jobTemplateSpec1 = new V1JobTemplateSpec();
        V1ObjectMeta jobMeta1 = new V1ObjectMeta();
        jobMeta1.setNamespace("ns1");
        jobMeta1.setName("cj1");
        jobTemplateSpec1.setMetadata(jobMeta1);
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
        jobTemplateSpec1.setSpec(jobSpec1);
        cronJobSpec1.setJobTemplate(jobTemplateSpec1);
        cronJob1.setSpec(cronJobSpec1);
        Mockito.doReturn(List.of(group1))
                .when(this.groupIndexer)
                .byIndex(IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, "ns1");
        Mockito.doReturn(cronJob1).when(this.cronJobIndexer).getByKey(KeyUtil.buildKey("ns1", "cronjob1"));
        CronJobReconciler cronJobReconciler = new CronJobReconciler(this.cronJobIndexer, this.reconciliation, this.batchV1Api);
        cronJobReconciler.reconcile(new Request("ns1", "cronjob1"));
        try {
            Mockito.verify(this.batchV1Api).replaceNamespacedCronJob(
                    Mockito.eq("cronjob1"),
                    Mockito.eq("ns1"),
                    Mockito.argThat(cronJob -> {
                        List<String> tolerationValues = cronJob.getSpec().getJobTemplate().getSpec()
                                .getTemplate().getSpec().getTolerations().stream()
                                .filter(e -> {
                                    if (e.getKey() == null) {
                                        return false;
                                    }
                                    return e.getKey().equals(Taints.KEY_RESOURCE_GROUP_EXCLUSIVE);
                                })
                                .map(V1Toleration::getValue)
                                .collect(Collectors.toList());
                        Set<String> valueSet = new HashSet<>(tolerationValues);
                        if (!valueSet.equals(Set.of("group1"))) {
                            return false;
                        }
                        Set<String> affinityValues = cronJob.getSpec().getJobTemplate().getSpec()
                                .getTemplate().getSpec().getAffinity().getNodeAffinity()
                                .getRequiredDuringSchedulingIgnoredDuringExecution().getNodeSelectorTerms().stream()
                                .flatMap(nst -> nst.getMatchExpressions().stream())
                                .filter(nsr -> nsr.getKey().equals(Labels.KEY_RESOURCE_GROUP_EXCLUSIVE))
                                .flatMap(nsr -> nsr.getValues().stream())
                                .collect(Collectors.toSet());
                        if (!affinityValues.equals(Set.of("group1"))) {
                            return false;
                        }
                        return true;
                    }),
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
    void given_cronjob_has_unsupported_controller_then_should_delete_cronjob() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1CronJob cronJob1 = new V1CronJob();
        V1ObjectMeta cronJobMeta1 = new V1ObjectMeta();
        cronJobMeta1.setNamespace("ns1");
        cronJobMeta1.setName("cronjob1");
        cronJob1.setMetadata(cronJobMeta1);
        V1CronJobSpec cronJobSpec1 = new V1CronJobSpec();
        V1JobTemplateSpec jobTemplateSpec1 = new V1JobTemplateSpec();
        V1ObjectMeta jobMeta1 = new V1ObjectMeta();
        jobMeta1.setNamespace("ns1");
        jobMeta1.setName("cj1");
        jobTemplateSpec1.setMetadata(jobMeta1);
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
        jobTemplateSpec1.setSpec(jobSpec1);
        cronJobSpec1.setJobTemplate(jobTemplateSpec1);
        cronJob1.setSpec(cronJobSpec1);
        V1OwnerReference ownerReference1 = new V1OwnerReference();
        ownerReference1.setApiVersion("v1");
        ownerReference1.setKind("dummy");
        ownerReference1.setController(true);
        cronJob1.getMetadata().setOwnerReferences(List.of(ownerReference1));
        Mockito.doReturn(List.of(group1))
                .when(this.groupIndexer)
                .byIndex(IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, "ns1");
        Mockito.doReturn(cronJob1).when(this.cronJobIndexer).getByKey(KeyUtil.buildKey("ns1", "cronjob1"));
        CronJobReconciler cronJobReconciler = new CronJobReconciler(this.cronJobIndexer, this.reconciliation, this.batchV1Api);
        cronJobReconciler.reconcile(new Request("ns1", "cronjob1"));
        try {
            Mockito.verify(this.batchV1Api).deleteNamespacedCronJob(
                    Mockito.eq("cronjob1"),
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
