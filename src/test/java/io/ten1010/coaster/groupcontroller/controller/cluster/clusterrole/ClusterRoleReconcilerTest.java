package io.ten1010.coaster.groupcontroller.controller.cluster.clusterrole;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.model.V1Beta1ResourceGroup;
import io.ten1010.coaster.groupcontroller.model.V1Beta1ResourceGroupSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

class ClusterRoleReconcilerTest {

    Indexer<V1Namespace> namespaceIndexer;
    Indexer<V1Beta1ResourceGroup> groupIndexer;
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
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        meta1.setUid("group1-uid");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        group1.setSpec(spec1);

        Mockito.doReturn(group1).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(null).when(this.clusterRoleIndexer).getByKey(KeyUtil.buildKey("resource-group-controller.resource-group.ten1010.io:group1"));
        ClusterRoleReconciler clusterRoleReconciler = new ClusterRoleReconciler(this.groupIndexer, this.clusterRoleIndexer, this.rbacAuthorizationV1Api);
        clusterRoleReconciler.reconcile(new Request("resource-group-controller.resource-group.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).createClusterRole(
                    Mockito.argThat(clusterRole -> clusterRole.getMetadata().getName().equals("resource-group-controller.resource-group.ten1010.io:group1")),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null));
        } catch (ApiException e) {
            Assertions.fail();
        }
        Mockito.verifyNoMoreInteractions(this.rbacAuthorizationV1Api);
    }

    @Test
    void given_cluster_role_has_group_which_not_exist_then_delete_the_cluster_role() {
        V1ClusterRoleBuilder builder = new V1ClusterRoleBuilder();
        V1ClusterRole clusterRole1 = builder.withNewMetadata()
                .withName("group1")
                .withOwnerReferences()
                .endMetadata()
                .build();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        meta1.setUid("group1-uid");
        clusterRole1.setMetadata(meta1);

        Mockito.doReturn(null).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(clusterRole1).when(this.clusterRoleIndexer).getByKey("resource-group-controller.resource-group.ten1010.io:group1");
        ClusterRoleReconciler clusterRoleReconciler = new ClusterRoleReconciler(this.groupIndexer, this.clusterRoleIndexer, this.rbacAuthorizationV1Api);
        clusterRoleReconciler.reconcile(new Request("resource-group-controller.resource-group.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).deleteClusterRole(
                    Mockito.eq("resource-group-controller.resource-group.ten1010.io:group1"),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null)
            );
        } catch (ApiException e) {
            Assertions.fail();
        }
        Mockito.verifyNoMoreInteractions(this.rbacAuthorizationV1Api);
    }

    @Test
    void given_cluster_role_has_empty_rules_then_should_update_the_cluster_role() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        meta1.setUid("group1-uid");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        spec1.setNodes(List.of("node1"));
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1PolicyRule emptyRule = new V1PolicyRuleBuilder()
                .withResources()
                .withResourceNames()
                .withVerbs()
                .build();
        V1ClusterRoleBuilder builder = new V1ClusterRoleBuilder();
        V1ClusterRole clusterRole1 = builder.withNewMetadata()
                .withName("group1")
                .withUid("group1-uid")
                .withOwnerReferences()
                .endMetadata()
                .withRules(emptyRule)
                .build();
        V1ObjectMeta clusterRoleMeta1 = new V1ObjectMeta();
        clusterRoleMeta1.setName("group1");
        clusterRoleMeta1.setUid("group1-uid");
        clusterRole1.setMetadata(clusterRoleMeta1);

        Mockito.doReturn(group1).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(clusterRole1).when(this.clusterRoleIndexer).getByKey(KeyUtil.buildKey("resource-group-controller.resource-group.ten1010.io:group1"));
        ClusterRoleReconciler clusterRoleReconciler = new ClusterRoleReconciler(this.groupIndexer, this.clusterRoleIndexer, this.rbacAuthorizationV1Api);
        clusterRoleReconciler.reconcile(new Request("resource-group-controller.resource-group.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).replaceClusterRole(
                    Mockito.eq("group1"),
                    Mockito.argThat(clusterRole -> {
                        if (!clusterRole.getMetadata().getName().equals("group1")) {
                            return false;
                        }
                        if (!clusterRole.getMetadata().getUid().equals("group1-uid")) {
                            return false;
                        }
                        V1PolicyRule groupApiRule = new V1PolicyRuleBuilder().withApiGroups("resource-group.ten1010.io")
                                .withResources("resourcegroups")
                                .withResourceNames(List.of("group1"))
                                .withVerbs("get")
                                .build();
                        V1PolicyRule nodeApiRule = new V1PolicyRuleBuilder().withApiGroups("")
                                .withResources("nodes")
                                .withResourceNames(List.of("node1"))
                                .withVerbs("get")
                                .build();
                        V1PolicyRule namespaceApiRule = new V1PolicyRuleBuilder().withApiGroups("")
                                .withResources("namespaces")
                                .withResourceNames(List.of("ns1"))
                                .withVerbs("get")
                                .build();
                        List<V1PolicyRule> apiRules = List.of(groupApiRule, nodeApiRule, namespaceApiRule);
                        return clusterRole.getRules().equals(apiRules);
                    }),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null),
                    Mockito.eq(null)
            );
        } catch (ApiException e) {
            Assertions.fail();
        }
        Mockito.verifyNoMoreInteractions(this.rbacAuthorizationV1Api);
    }

}
