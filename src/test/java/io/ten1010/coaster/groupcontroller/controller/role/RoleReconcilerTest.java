package io.ten1010.coaster.groupcontroller.controller.role;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Role;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroupSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

class RoleReconcilerTest {

    Indexer<V1Namespace> namespaceIndexer;
    Indexer<V1ResourceGroup> groupIndexer;
    RoleNameUtil roleNameUtil;
    Indexer<V1Role> roleIndexer;
    RbacAuthorizationV1Api rbacAuthorizationV1Api;

    @BeforeEach
    void setUp() {
        this.namespaceIndexer = Mockito.mock(Indexer.class);
        this.groupIndexer = Mockito.mock(Indexer.class);
        this.roleNameUtil = new RoleNameUtil();
        this.roleIndexer = Mockito.mock(Indexer.class);
        this.rbacAuthorizationV1Api = Mockito.mock(RbacAuthorizationV1Api.class);
    }

    @Test
    void should_create_the_role() {
        V1ResourceGroup group1 = new V1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        meta1.setUid("group1-uid");
        group1.setMetadata(meta1);
        V1ResourceGroupSpec spec1 = new V1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1Namespace ns1 = new V1Namespace();
        V1ObjectMeta nsMeta1 = new V1ObjectMeta();
        nsMeta1.setName("ns1");
        ns1.setMetadata(nsMeta1);

        Mockito.doReturn(group1).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(ns1).when(this.namespaceIndexer).getByKey("ns1");
        Mockito.doReturn(null).when(this.roleIndexer).getByKey(KeyUtil.buildKey("ns1", "resource-group-controller.ten1010.io:group1"));
        RoleReconciler roleReconciler = new RoleReconciler(this.namespaceIndexer, this.groupIndexer, this.roleNameUtil, this.roleIndexer, this.rbacAuthorizationV1Api);
        roleReconciler.reconcile(new Request("ns1", "resource-group-controller.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).createNamespacedRole(
                    Mockito.eq("ns1"),
                    Mockito.argThat(role -> {
                        if (!role.getMetadata().getNamespace().equals("ns1")) {
                            return false;
                        }
                        return role.getMetadata().getName().equals("resource-group-controller.ten1010.io:group1");
                    }),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null));
        } catch (ApiException e) {
            Assertions.fail();
        }
        Mockito.verifyNoMoreInteractions(this.rbacAuthorizationV1Api);
    }

}
