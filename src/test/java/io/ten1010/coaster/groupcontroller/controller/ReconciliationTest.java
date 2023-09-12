package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.core.IndexNames;
import io.ten1010.coaster.groupcontroller.core.K8sObjectUtil;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.core.Taints;
import io.ten1010.coaster.groupcontroller.model.V1Beta2ResourceGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class ReconciliationTest {

    @InjectMocks
    Reconciliation reconciliation;

    @Mock
    Indexer<V1Beta2ResourceGroup> groupIndexer;

    @Test
    void should_return_tolerations_of_group_containing_daemon_set() {
        // given
        V1Beta2ResourceGroup group1 = new V1Beta2ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        Mockito.doReturn(List.of(group1)).when(groupIndexer).byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT,
                "ns1"
        );

        V1Beta2ResourceGroup group2 = new V1Beta2ResourceGroup();
        V1ObjectMeta meta2 = new V1ObjectMeta();
        meta2.setName("group2");
        group2.setMetadata(meta2);
        Mockito.doReturn(List.of(group2)).when(groupIndexer).byIndex(
                IndexNames.BY_DAEMON_SET_KEY_TO_GROUP_OBJECT,
                KeyUtil.buildKey("ns1", "ds1")
        );

        V1Beta2ResourceGroup group3 = new V1Beta2ResourceGroup();
        V1ObjectMeta meta3 = new V1ObjectMeta();
        meta3.setName("group3");
        group3.setMetadata(meta3);
        Mockito.doReturn(List.of(group3)).when(groupIndexer).byIndex(
                IndexNames.BY_GROUP_ALLOW_ALL_DAEMON_SET_TO_GROUP_OBJECT,
                "true"
        );

        // when
        V1DaemonSet ds1 = new V1DaemonSetBuilder()
                .withNewMetadata()
                .withNamespace("ns1")
                .withName("ds1")
                .endMetadata()
                .build();
        List<V1Toleration> result = reconciliation.reconcileUncontrolledDaemonSetTolerations(ds1);

        // then
        assertEquals(List.of(
                new V1TolerationBuilder()
                        .withKey(Taints.KEY_RESOURCE_GROUP_EXCLUSIVE)
                        .withValue(K8sObjectUtil.getName(group1))
                        .withOperator("Equal")
                        .withEffect(Taints.EFFECT_NO_SCHEDULE)
                        .build(),
                new V1TolerationBuilder()
                        .withKey(Taints.KEY_RESOURCE_GROUP_EXCLUSIVE)
                        .withValue(K8sObjectUtil.getName(group2))
                        .withOperator("Equal")
                        .withEffect(Taints.EFFECT_NO_SCHEDULE)
                        .build(),
                new V1TolerationBuilder()
                        .withKey(Taints.KEY_RESOURCE_GROUP_EXCLUSIVE)
                        .withValue(K8sObjectUtil.getName(group3))
                        .withOperator("Equal")
                        .withEffect(Taints.EFFECT_NO_SCHEDULE)
                        .build()
        ), result);
    }
}