package io.ten1010.coaster.groupcontroller.controller.cluster.role;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.model.V1Beta2ResourceGroup;
import io.ten1010.coaster.groupcontroller.model.V1Beta2ResourceGroupSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

class RoleReconcilerTest {

    Indexer<V1Namespace> namespaceIndexer;
    Indexer<V1Beta2ResourceGroup> groupIndexer;
    Indexer<V1Role> roleIndexer;
    RbacAuthorizationV1Api rbacAuthorizationV1Api;

    @BeforeEach
    void setUp() {
        this.namespaceIndexer = Mockito.mock(Indexer.class);
        this.groupIndexer = Mockito.mock(Indexer.class);
        this.roleIndexer = Mockito.mock(Indexer.class);
        this.rbacAuthorizationV1Api = Mockito.mock(RbacAuthorizationV1Api.class);
    }

    @Test
    void should_create_the_role() {
        V1Beta2ResourceGroup group1 = new V1Beta2ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        meta1.setUid("group1-uid");
        group1.setMetadata(meta1);
        V1Beta2ResourceGroupSpec spec1 = new V1Beta2ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1Namespace ns1 = new V1Namespace();
        V1ObjectMeta nsMeta1 = new V1ObjectMeta();
        nsMeta1.setName("ns1");
        ns1.setMetadata(nsMeta1);

        Mockito.doReturn(group1).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(ns1).when(this.namespaceIndexer).getByKey("ns1");
        Mockito.doReturn(null).when(this.roleIndexer).getByKey(KeyUtil.buildKey("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        RoleReconciler roleReconciler = new RoleReconciler(this.namespaceIndexer, this.groupIndexer, this.roleIndexer, this.rbacAuthorizationV1Api);
        roleReconciler.reconcile(new Request("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).createNamespacedRole(
                    Mockito.eq("ns1"),
                    Mockito.argThat(role -> {
                        if (!role.getMetadata().getNamespace().equals("ns1")) {
                            return false;
                        }
                        return role.getMetadata().getName().equals("resource-group-controller.resource-group.ten1010.io:group1");
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

    @Test
    void given_role_has_empty_rules_then_should_update_the_role() {
        V1Beta2ResourceGroup group1 = new V1Beta2ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        meta1.setUid("group1-uid");
        group1.setMetadata(meta1);
        V1Beta2ResourceGroupSpec spec1 = new V1Beta2ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);
        V1Namespace ns1 = new V1Namespace();
        V1ObjectMeta nsMeta1 = new V1ObjectMeta();
        nsMeta1.setName("ns1");
        ns1.setMetadata(nsMeta1);
        List<V1PolicyRule> rule1 = List.of(new V1PolicyRuleBuilder().withApiGroups()
                .withResources()
                .withVerbs()
                .build());
        V1RoleBuilder builder = new V1RoleBuilder();
        V1Role role1 = builder.withNewMetadata()
                .withNamespace("ns1")
                .withName("resource-group-controller.resource-group.ten1010.io:group1")
                .withOwnerReferences()
                .endMetadata()
                .withRules(rule1)
                .build();

        Mockito.doReturn(group1).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(ns1).when(this.namespaceIndexer).getByKey("ns1");
        Mockito.doReturn(role1).when(this.roleIndexer).getByKey(KeyUtil.buildKey("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));

        RoleReconciler roleReconciler = new RoleReconciler(this.namespaceIndexer, this.groupIndexer, this.roleIndexer, this.rbacAuthorizationV1Api);
        roleReconciler.reconcile(new Request("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).replaceNamespacedRole(
                    Mockito.eq("resource-group-controller.resource-group.ten1010.io:group1"),
                    Mockito.eq("ns1"),
                    Mockito.argThat(role -> {
                        if (!role.getMetadata().getNamespace().equals("ns1")) {
                            return false;
                        }
                        V1PolicyRule coreApiRule = new V1PolicyRuleBuilder().withApiGroups("")
                                .withResources(
                                        "pods",
                                        "services",
                                        "configmaps",
                                        "secrets",
                                        "persistentvolumeclaims",
                                        "serviceaccounts",
                                        "limitranges",
                                        "events",
                                        "replicationcontrollers")
                                .withVerbs("*")
                                .build();
                        V1PolicyRule eventApiRule = new V1PolicyRuleBuilder().withApiGroups("events.k8s.io")
                                .withResources("events")
                                .withVerbs("*")
                                .build();
                        V1PolicyRule batchApiRule = new V1PolicyRuleBuilder().withApiGroups("batch")
                                .withResources("jobs", "cronjobs")
                                .withVerbs("*")
                                .build();
                        V1PolicyRule appsApiRule = new V1PolicyRuleBuilder().withApiGroups("apps")
                                .withResources("deployments", "statefulsets", "daemonsets", "replicasets")
                                .withVerbs("*")
                                .build();
                        V1PolicyRule autoscalingApiRule = new V1PolicyRuleBuilder().withApiGroups("autoscaling")
                                .withResources("horizontalpodautoscalers")
                                .withVerbs("*")
                                .build();
                        V1PolicyRule poddisrubptionbudgetApiRule = new V1PolicyRuleBuilder().withApiGroups("policy")
                                .withResources("poddisruptionbudgets")
                                .withVerbs("*")
                                .build();
                        List<V1PolicyRule> rules = List.of(coreApiRule, eventApiRule, batchApiRule, appsApiRule, autoscalingApiRule, poddisrubptionbudgetApiRule);
                        if (!Set.copyOf(role.getRules()).equals(Set.copyOf(rules))) {
                            return false;
                        }
                        return role.getMetadata().getName().equals("resource-group-controller.resource-group.ten1010.io:group1");
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

    @Test
    void given_role_has_group_which_not_exists_then_delete_the_role() {
        V1Namespace ns1 = new V1Namespace();
        V1ObjectMeta nsMeta1 = new V1ObjectMeta();
        nsMeta1.setName("ns1");
        ns1.setMetadata(nsMeta1);

        List<V1PolicyRule> rule1 = List.of(new V1PolicyRuleBuilder().withApiGroups()
                .withResources()
                .withVerbs()
                .build());
        V1RoleBuilder builder = new V1RoleBuilder();
        V1Role role1 = builder.withNewMetadata()
                .withNamespace("ns1")
                .withName("resource-group-controller.resource-group.ten1010.io:group1")
                .withOwnerReferences()
                .endMetadata()
                .withRules(rule1)
                .build();

        Mockito.doReturn(null).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(ns1).when(this.namespaceIndexer).getByKey("ns1");
        Mockito.doReturn(role1).when(this.roleIndexer).getByKey(KeyUtil.buildKey("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        RoleReconciler roleReconciler = new RoleReconciler(this.namespaceIndexer, this.groupIndexer, this.roleIndexer, this.rbacAuthorizationV1Api);
        roleReconciler.reconcile(new Request("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).deleteNamespacedRole(
                    Mockito.eq("resource-group-controller.resource-group.ten1010.io:group1"),
                    Mockito.eq("ns1"),
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
    void given_role_has_namespace_which_group_does_not_have_then_delete_the_role() {
        V1Beta2ResourceGroup group1 = new V1Beta2ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        meta1.setUid("group1-uid");
        group1.setMetadata(meta1);
        V1Beta2ResourceGroupSpec spec1 = new V1Beta2ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        group1.setSpec(spec1);

        V1Namespace ns1 = new V1Namespace();
        V1ObjectMeta nsMeta1 = new V1ObjectMeta();
        nsMeta1.setName("ns1");
        ns1.setMetadata(nsMeta1);
        V1Namespace ns2 = new V1Namespace();
        V1ObjectMeta nsMeta2 = new V1ObjectMeta();
        nsMeta1.setName("ns2");
        ns1.setMetadata(nsMeta2);

        List<V1PolicyRule> rule1 = List.of(new V1PolicyRuleBuilder().withApiGroups()
                .withResources()
                .withVerbs()
                .build());
        V1RoleBuilder builder = new V1RoleBuilder();
        V1Role role1 = builder.withNewMetadata()
                .withNamespace("ns2")
                .withName("resource-group-controller.resource-group.ten1010.io:group1")
                .withOwnerReferences()
                .endMetadata()
                .withRules(rule1)
                .build();

        Mockito.doReturn(group1).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(ns1).when(this.namespaceIndexer).getByKey("ns1");
        Mockito.doReturn(ns2).when(this.namespaceIndexer).getByKey("ns2");
        Mockito.doReturn(role1).when(this.roleIndexer).getByKey(KeyUtil.buildKey("ns2", "resource-group-controller.resource-group.ten1010.io:group1"));
        RoleReconciler roleReconciler = new RoleReconciler(this.namespaceIndexer, this.groupIndexer, this.roleIndexer, this.rbacAuthorizationV1Api);
        roleReconciler.reconcile(new Request("ns2", "resource-group-controller.resource-group.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).deleteNamespacedRole(
                    Mockito.eq("resource-group-controller.resource-group.ten1010.io:group1"),
                    Mockito.eq("ns2"),
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

}
