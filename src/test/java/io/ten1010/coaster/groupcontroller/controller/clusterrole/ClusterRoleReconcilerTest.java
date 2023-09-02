package io.ten1010.coaster.groupcontroller.controller.clusterrole;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.coaster.groupcontroller.controller.cluster.clusterrole.ClusterRoleReconciler;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroupSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ClusterRoleReconcilerTest {

    Indexer<V1Namespace> namespaceIndexer;
    Indexer<V1ResourceGroup> groupIndexer;
    Indexer<V1ClusterRole> clusterRoleIndexer;
    RbacAuthorizationV1Api rbacAuthorizationV1Api;

    @BeforeEach
    void setUp() {
        this.namespaceIndexer = Mockito.mock(Indexer.class);
        this.groupIndexer = Mockito.mock(Indexer.class);
        this.clusterRoleIndexer = Mockito.mock(Indexer.class);
        this.rbacAuthorizationV1Api = Mockito.mock(RbacAuthorizationV1Api.class);
    }

    @Test
    void should_create_the_cluster_role() {
        V1ResourceGroup group1 = new V1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        meta1.setUid("group1-uid");
        group1.setMetadata(meta1);
        V1ResourceGroupSpec spec1 = new V1ResourceGroupSpec();
        group1.setSpec(spec1);

        Mockito.doReturn(group1).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(null).when(this.clusterRoleIndexer).getByKey(KeyUtil.buildKey("resource-group-controller.ten1010.io:group1"));
        ClusterRoleReconciler clusterRoleReconciler = new ClusterRoleReconciler(this.groupIndexer, this.clusterRoleIndexer, this.rbacAuthorizationV1Api);
        clusterRoleReconciler.reconcile(new Request("resource-group-controller.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).createClusterRole(
                    Mockito.argThat(clusterRole -> clusterRole.getMetadata().getName().equals("resource-group-controller.ten1010.io:group1")),
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
