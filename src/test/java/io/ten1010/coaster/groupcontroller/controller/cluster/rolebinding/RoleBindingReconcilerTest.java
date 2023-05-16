package io.ten1010.coaster.groupcontroller.controller.cluster.rolebinding;

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

class RoleBindingReconcilerTest {

    Indexer<V1Namespace> namespaceIndexer;
    Indexer<V1Beta1ResourceGroup> groupIndexer;
    Indexer<V1RoleBinding> roleBindingIndexer;
    Indexer<V1Role> roleIndexer;
    RbacAuthorizationV1Api rbacAuthorizationV1Api;

    @BeforeEach
    void setUp() {
        this.namespaceIndexer = Mockito.mock(Indexer.class);
        this.groupIndexer = Mockito.mock(Indexer.class);
        this.roleBindingIndexer = Mockito.mock(Indexer.class);
        this.roleIndexer = Mockito.mock(Indexer.class);
        this.rbacAuthorizationV1Api = Mockito.mock(RbacAuthorizationV1Api.class);
    }

    @Test
    void should_create_the_role_binding() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        meta1.setUid("group1-uid");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        V1Subject subject = new V1Subject();
        subject.setApiGroup("rbac.authorization.k8s.io");
        subject.setKind("User");
        subject.setName("user1");
        spec1.setSubjects(List.of(subject));
        group1.setSpec(spec1);

        V1Namespace ns1 = new V1Namespace();
        V1ObjectMeta nsMeta1 = new V1ObjectMeta();
        nsMeta1.setName("ns1");
        ns1.setMetadata(nsMeta1);

        Mockito.doReturn(group1).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(ns1).when(this.namespaceIndexer).getByKey("ns1");
        Mockito.doReturn(null).when(this.roleBindingIndexer).getByKey(KeyUtil.buildKey("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        Mockito.doReturn(new V1Role()).when(this.roleIndexer).getByKey(KeyUtil.buildKey("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        RoleBindingReconciler roleBindingReconciler = new RoleBindingReconciler(
                this.namespaceIndexer,
                this.groupIndexer,
                this.roleBindingIndexer,
                this.roleIndexer,
                this.rbacAuthorizationV1Api);
        roleBindingReconciler.reconcile(new Request("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).createNamespacedRoleBinding(
                    Mockito.eq("ns1"),
                    Mockito.argThat(roleBinding -> {
                        if (!roleBinding.getMetadata().getNamespace().equals("ns1")) {
                            return false;
                        }
                        if (!roleBinding.getMetadata().getName().equals("resource-group-controller.resource-group.ten1010.io:group1")) {
                            return false;
                        }
                        V1RoleRef roleRef = new V1RoleRef();
                        roleRef.setApiGroup("rbac.authorization.k8s.io");
                        roleRef.setKind("Role");
                        roleRef.setName("resource-group-controller.resource-group.ten1010.io:group1");
                        if (!roleBinding.getRoleRef().equals(roleRef)) {
                            return false;
                        }
                        if (roleBinding.getSubjects() == null) {
                            return false;
                        }
                        return roleBinding.getSubjects().equals(List.of(subject));
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
    void given_role_binding_has_group_name_which_not_exist_then_delete_the_role_binding() {
        V1Namespace ns1 = new V1Namespace();
        V1ObjectMeta nsMeta1 = new V1ObjectMeta();
        nsMeta1.setName("ns1");
        ns1.setMetadata(nsMeta1);

        V1RoleBindingBuilder builder = new V1RoleBindingBuilder();
        V1RoleRef roleRef1 = new V1RoleRef();
        V1RoleBinding roleBinding1 = builder.withNewMetadata()
                .withNamespace("ns1")
                .withName("group1")
                .withOwnerReferences()
                .endMetadata()
                .withRoleRef(roleRef1)
                .withSubjects()
                .build();

        Mockito.doReturn(null).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(ns1).when(this.namespaceIndexer).getByKey("ns1");
        Mockito.doReturn(roleBinding1).when(this.roleBindingIndexer).getByKey(KeyUtil.buildKey("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        Mockito.doReturn(new V1Role()).when(this.roleIndexer).getByKey(KeyUtil.buildKey("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        RoleBindingReconciler roleBindingReconciler = new RoleBindingReconciler(
                this.namespaceIndexer,
                this.groupIndexer,
                this.roleBindingIndexer,
                this.roleIndexer,
                this.rbacAuthorizationV1Api);
        roleBindingReconciler.reconcile(new Request("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).deleteNamespacedRoleBinding(
                    Mockito.eq("resource-group-controller.resource-group.ten1010.io:group1"),
                    Mockito.eq("ns1"),
                    Mockito.eq(null),
                    Mockito.eq(null),
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
    void given_role_binding_has_namespace_which_group_not_contains_then_delete_the_role_binding() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        meta1.setUid("group1-uid");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        V1Subject subject = new V1Subject();
        spec1.setSubjects(List.of(subject));
        group1.setSpec(spec1);

        V1RoleBindingBuilder builder = new V1RoleBindingBuilder();
        V1RoleRef roleRef1 = new V1RoleRef();
        V1RoleBinding roleBinding1 = builder.withNewMetadata()
                .withNamespace("ns2")
                .withName("group1")
                .withOwnerReferences()
                .endMetadata()
                .withRoleRef(roleRef1)
                .withSubjects()
                .build();

        V1Namespace ns1 = new V1Namespace();
        V1ObjectMeta nsMeta1 = new V1ObjectMeta();
        nsMeta1.setName("ns1");
        ns1.setMetadata(nsMeta1);
        V1Namespace ns2 = new V1Namespace();
        V1ObjectMeta nsMeta2 = new V1ObjectMeta();
        nsMeta2.setName("ns2");
        ns2.setMetadata(nsMeta2);

        Mockito.doReturn(group1).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(ns2).when(this.namespaceIndexer).getByKey("ns2");
        Mockito.doReturn(roleBinding1).when(this.roleBindingIndexer).getByKey(KeyUtil.buildKey("ns2", "resource-group-controller.resource-group.ten1010.io:group1"));
        Mockito.doReturn(new V1Role()).when(this.roleIndexer).getByKey(KeyUtil.buildKey("ns2", "resource-group-controller.resource-group.ten1010.io:group1"));
        RoleBindingReconciler roleBindingReconciler = new RoleBindingReconciler(
                this.namespaceIndexer,
                this.groupIndexer,
                this.roleBindingIndexer,
                this.roleIndexer,
                this.rbacAuthorizationV1Api);
        roleBindingReconciler.reconcile(new Request("ns2", "resource-group-controller.resource-group.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).deleteNamespacedRoleBinding(
                    Mockito.eq("resource-group-controller.resource-group.ten1010.io:group1"),
                    Mockito.eq("ns2"),
                    Mockito.eq(null),
                    Mockito.eq(null),
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
    void given_role_binding_has_empty_subjects_and_roleRef_then_update_the_role_binding() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        meta1.setUid("group1-uid");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        spec1.setNamespaces(List.of("ns1"));
        V1Subject subject1 = new V1Subject();
        subject1.setApiGroup("rbac.authorization.k8s.io");
        subject1.setKind("User");
        subject1.setName("user1");
        subject1.setNamespace("ns1");
        spec1.setSubjects(List.of(subject1));
        group1.setSpec(spec1);

        V1RoleBindingBuilder builder = new V1RoleBindingBuilder();
        V1RoleRef roleRef1 = new V1RoleRef();
        V1RoleBinding roleBinding1 = builder.withNewMetadata()
                .withNamespace("ns1")
                .withName("group1")
                .withOwnerReferences()
                .endMetadata()
                .withRoleRef(roleRef1)
                .withSubjects()
                .build();

        V1Namespace ns1 = new V1Namespace();
        V1ObjectMeta nsMeta1 = new V1ObjectMeta();
        nsMeta1.setName("ns1");
        ns1.setMetadata(nsMeta1);

        Mockito.doReturn(ns1).when(this.namespaceIndexer).getByKey("ns1");
        Mockito.doReturn(group1).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(roleBinding1).when(this.roleBindingIndexer).getByKey(KeyUtil.buildKey("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        Mockito.doReturn(new V1Role()).when(this.roleIndexer).getByKey(KeyUtil.buildKey("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        RoleBindingReconciler roleBindingReconciler = new RoleBindingReconciler(
                this.namespaceIndexer,
                this.groupIndexer,
                this.roleBindingIndexer,
                this.roleIndexer,
                this.rbacAuthorizationV1Api);
        roleBindingReconciler.reconcile(new Request("ns1", "resource-group-controller.resource-group.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).replaceNamespacedRoleBinding(
                    Mockito.eq("group1"),
                    Mockito.eq("ns1"),
                    Mockito.argThat(roleBinding -> {
                        if (!roleBinding.getMetadata().getName().equals("group1")) {
                            return false;
                        }
                        if (!roleBinding.getMetadata().getNamespace().equals("ns1")) {
                            return false;
                        }
                        V1RoleRef roleRef = new V1RoleRef();
                        roleRef.setApiGroup("rbac.authorization.k8s.io");
                        roleRef.setKind("Role");
                        roleRef.setName("resource-group-controller.resource-group.ten1010.io:group1");
                        V1Subject subject = new V1Subject();
                        subject.setApiGroup("rbac.authorization.k8s.io");
                        subject.setKind("User");
                        subject.setName("user1");
                        subject.setNamespace("ns1");
                        if (!roleBinding.getRoleRef().equals(roleRef)) {
                            return false;
                        }
                        return roleBinding.getSubjects().equals(List.of(subject));
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
