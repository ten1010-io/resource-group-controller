package io.ten1010.groupcontroller.controller.workload.daemonset;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.groupcontroller.controller.Reconciliation;
import io.ten1010.groupcontroller.core.IndexNames;
import io.ten1010.groupcontroller.core.KeyUtil;
import io.ten1010.groupcontroller.core.Taints;
import io.ten1010.groupcontroller.model.V1Beta1DaemonSet;
import io.ten1010.groupcontroller.model.V1Beta1K8sObjectReference;
import io.ten1010.groupcontroller.model.V1Beta1ResourceGroup;
import io.ten1010.groupcontroller.model.V1Beta1ResourceGroupSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class DaemonSetReconcilerTest {

    Indexer<V1Beta1ResourceGroup> groupIndexer;
    Reconciliation reconciliation;
    Indexer<V1DaemonSet> daemonSetIndexer;
    AppsV1Api appsV1Api;

    @BeforeEach
    void setUp() {
        this.groupIndexer = Mockito.mock(Indexer.class);
        this.reconciliation = new Reconciliation(this.groupIndexer);
        this.daemonSetIndexer = Mockito.mock(Indexer.class);
        this.appsV1Api = Mockito.mock(AppsV1Api.class);
    }

    @Test
    void should_patch_tolerations_of_the_daemon_set() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        group1.setSpec(spec1);
        V1Beta1K8sObjectReference dsRef1 = new V1Beta1K8sObjectReference();
        dsRef1.setNamespace("ns1");
        dsRef1.setName("ds1");
        V1Beta1DaemonSet daemonSet1 = new V1Beta1DaemonSet();
        daemonSet1.setDaemonSets(List.of(dsRef1));
        spec1.setDaemonSet(daemonSet1);

        V1Beta1ResourceGroup group2 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta2 = new V1ObjectMeta();
        meta2.setName("group2");
        group2.setMetadata(meta2);
        V1Beta1ResourceGroupSpec spec2 = new V1Beta1ResourceGroupSpec();
        group2.setSpec(spec2);
        V1Beta1K8sObjectReference dsRef2 = new V1Beta1K8sObjectReference();
        dsRef2.setNamespace("ns1");
        dsRef2.setName("ds1");
        V1Beta1DaemonSet daemonSet2 = new V1Beta1DaemonSet();
        daemonSet2.setDaemonSets(List.of(dsRef2));
        spec2.setDaemonSet(daemonSet2);

        V1DaemonSet ds1 = new V1DaemonSet();
        V1ObjectMeta dsMeta1 = new V1ObjectMeta();
        dsMeta1.setNamespace("ns1");
        dsMeta1.setName("ds1");
        ds1.setMetadata(dsMeta1);
        V1DaemonSetSpec dsSpec1 = new V1DaemonSetSpec();
        V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec();
        V1PodSpec podSpec = new V1PodSpec();
        podSpec.setTolerations(new ArrayList<>());
        podTemplateSpec.setSpec(podSpec);
        dsSpec1.setTemplate(podTemplateSpec);
        ds1.setSpec(dsSpec1);

        Mockito.doReturn(List.of(group1, group2))
                .when(this.groupIndexer)
                .byIndex(IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, "ns1");
        Mockito.doReturn(ds1).when(this.daemonSetIndexer).getByKey(KeyUtil.buildKey("ns1", "ds1"));
        DaemonSetReconciler daemonSetReconciler = new DaemonSetReconciler(this.daemonSetIndexer, this.reconciliation, this.appsV1Api);
        daemonSetReconciler.reconcile(new Request("ns1", "ds1"));
        try {
            Mockito.verify(this.appsV1Api).replaceNamespacedDaemonSet(
                    Mockito.eq("ds1"),
                    Mockito.eq("ns1"),
                    Mockito.argThat(daemonSet -> {
                        List<String> tolerationValues = daemonSet.getSpec().getTemplate().getSpec().getTolerations().stream()
                                .filter(e -> {
                                    if (e.getKey() == null) {
                                        return false;
                                    }
                                    return e.getKey().equals(Taints.KEY_RESOURCE_GROUP_EXCLUSIVE);
                                })
                                .map(V1Toleration::getValue)
                                .collect(Collectors.toList());
                        if (tolerationValues.size() != 2) {
                            return false;
                        }
                        Set<String> valueSet = new HashSet<>(tolerationValues);
                        return valueSet.equals(Set.of("group1", "group2"));
                    }),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null));
            Mockito.verifyNoMoreInteractions(this.appsV1Api);
        } catch (ApiException e) {
            Assertions.fail();
        }
    }

}
