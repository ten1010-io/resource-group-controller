package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.core.IndexNameConstants;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.model.DaemonSetReference;
import io.ten1010.coaster.groupcontroller.model.Exceptions;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroupSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

class GroupResolverTest {

    Indexer<V1ResourceGroup> groupIndexer;

    @BeforeEach
    void setUp() {
        this.groupIndexer = Mockito.mock(Indexer.class);
    }

    @Test
    void should_return_group_containing_namespace_of_pod() {
        V1ResourceGroup group1 = new V1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1ResourceGroupSpec spec1 = new V1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        Mockito.doReturn(List.of(group1)).when(this.groupIndexer).byIndex(IndexNameConstants.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, "ns1");
        GroupResolver groupResolver = new GroupResolver(this.groupIndexer);
        V1Pod pod1 = new V1PodBuilder()
                .withNewMetadata()
                .withNamespace("ns1")
                .withName("pod1")
                .endMetadata()
                .build();
        V1Pod pod2 = new V1PodBuilder()
                .withNewMetadata()
                .withNamespace("ns2")
                .withName("pod2")
                .endMetadata()
                .build();

        try {
            Assertions.assertEquals(List.of(group1), groupResolver.resolve(pod1));
            Assertions.assertEquals(List.of(), groupResolver.resolve(pod2));
        } catch (GroupResolver.NamespaceConflictException e) {
            Assertions.fail();
        }
    }

    @Test
    void should_throw_NamespaceConflictException() {
        V1ResourceGroup group1 = new V1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1ResourceGroupSpec spec1 = new V1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1ResourceGroup group2 = new V1ResourceGroup();
        V1ObjectMeta meta2 = new V1ObjectMeta();
        meta2.setName("group2");
        group2.setMetadata(meta2);
        V1ResourceGroupSpec spec2 = new V1ResourceGroupSpec();
        spec2.setNamespaces(List.of("ns1"));
        group2.setSpec(spec2);

        Mockito.doReturn(List.of(group1, group2)).when(this.groupIndexer).byIndex(IndexNameConstants.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, "ns1");
        GroupResolver groupResolver = new GroupResolver(this.groupIndexer);
        V1Pod pod1 = new V1PodBuilder()
                .withNewMetadata()
                .withNamespace("ns1")
                .withName("pod1")
                .endMetadata()
                .build();
        V1DaemonSet ds1 = new V1DaemonSetBuilder()
                .withNewMetadata()
                .withNamespace("ns1")
                .withName("ds1")
                .endMetadata()
                .build();

        Assertions.assertThrows(GroupResolver.NamespaceConflictException.class, () -> groupResolver.resolve(pod1));
        Assertions.assertThrows(GroupResolver.NamespaceConflictException.class, () -> groupResolver.resolve(ds1));
    }

    @Test
    void should_return_groups_containing_daemon_set() {
        V1ResourceGroup group1 = new V1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        group1.setMetadata(meta1);
        V1ResourceGroupSpec spec1 = new V1ResourceGroupSpec();
        group1.setSpec(spec1);
        DaemonSetReference dsRef1 = new DaemonSetReference();
        dsRef1.setNamespace("ns1");
        dsRef1.setName("ds1");
        Exceptions exceptions1 = new Exceptions();
        exceptions1.setDaemonSets(List.of(dsRef1));
        spec1.setExceptions(exceptions1);

        V1ResourceGroup group2 = new V1ResourceGroup();
        V1ObjectMeta meta2 = new V1ObjectMeta();
        meta2.setName("group2");
        group2.setMetadata(meta2);
        V1ResourceGroupSpec spec2 = new V1ResourceGroupSpec();
        group2.setSpec(spec2);
        DaemonSetReference dsRef2 = new DaemonSetReference();
        dsRef2.setNamespace("ns1");
        dsRef2.setName("ds1");
        Exceptions exceptions2 = new Exceptions();
        exceptions2.setDaemonSets(List.of(dsRef2));
        spec2.setExceptions(exceptions2);

        Mockito.doReturn(List.of(group1, group2)).when(this.groupIndexer)
                .byIndex(IndexNameConstants.BY_DAEMON_SET_KEY_TO_GROUP_OBJECT, KeyUtil.buildKey("ns1", "ds1"));
        GroupResolver groupResolver = new GroupResolver(this.groupIndexer);
        V1DaemonSet ds1 = new V1DaemonSetBuilder()
                .withNewMetadata()
                .withNamespace("ns1")
                .withName("ds1")
                .endMetadata()
                .build();

        try {
            Assertions.assertEquals(List.of(group1, group2), groupResolver.resolve(ds1));
        } catch (GroupResolver.NamespaceConflictException e) {
            Assertions.fail();
        }
    }

}
