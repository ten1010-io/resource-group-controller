package io.ten1010.groupcontroller.controller.cluster.clusterrolebinding;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.groupcontroller.core.KeyUtil;
import io.ten1010.groupcontroller.model.V1Beta1ResourceGroup;
import io.ten1010.groupcontroller.model.V1Beta1ResourceGroupSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

class ClusterRoleBindingReconcilerTest {

    Indexer<V1Beta1ResourceGroup> groupIndexer;
    Indexer<V1ClusterRoleBinding> clusterRoleBindingIndexer;
    Indexer<V1ClusterRole> clusterRoleIndexer;
    RbacAuthorizationV1Api rbacAuthorizationV1Api;

    @BeforeEach
    void setUp() {
        this.groupIndexer = Mockito.mock(Indexer.class);
        this.clusterRoleBindingIndexer = Mockito.mock(Indexer.class);
        this.clusterRoleIndexer = Mockito.mock(Indexer.class);
        this.rbacAuthorizationV1Api = Mockito.mock(RbacAuthorizationV1Api.class);
    }

    @Test
    void should_create_the_cluster_role_binding() {
        V1Beta1ResourceGroup group1 = new V1Beta1ResourceGroup();
        V1ObjectMeta meta1 = new V1ObjectMeta();
        meta1.setName("group1");
        meta1.setUid("group1-uid");
        group1.setMetadata(meta1);
        V1Beta1ResourceGroupSpec spec1 = new V1Beta1ResourceGroupSpec();
        V1Subject subject = new V1Subject();
        subject.setApiGroup("rbac.authorization.k8s.io");
        subject.setKind("User");
        subject.setName("user1");
        spec1.setSubjects(List.of(subject));
        group1.setSpec(spec1);

        Mockito.doReturn(group1).when(this.groupIndexer).getByKey("group1");
        Mockito.doReturn(null).when(this.clusterRoleBindingIndexer).getByKey(KeyUtil.buildKey("resource-group-controller.resource-group.ten1010.io:group1"));
        Mockito.doReturn(new V1ClusterRole()).when(this.clusterRoleIndexer).getByKey(KeyUtil.buildKey("resource-group-controller.resource-group.ten1010.io:group1"));
        ClusterRoleBindingReconciler clusterRoleBindingReconciler = new ClusterRoleBindingReconciler(
                this.groupIndexer,
                this.clusterRoleBindingIndexer,
                this.clusterRoleIndexer,
                this.rbacAuthorizationV1Api);
        clusterRoleBindingReconciler.reconcile(new Request("resource-group-controller.resource-group.ten1010.io:group1"));
        try {
            Mockito.verify(this.rbacAuthorizationV1Api).createClusterRoleBinding(
                    Mockito.argThat(clusterRoleBinding -> {
                        if (!clusterRoleBinding.getMetadata().getName().equals("resource-group-controller.resource-group.ten1010.io:group1")) {
                            return false;
                        }
                        V1RoleRef roleRef = new V1RoleRef();
                        roleRef.setApiGroup("rbac.authorization.k8s.io");
                        roleRef.setKind("ClusterRole");
                        roleRef.setName("resource-group-controller.resource-group.ten1010.io:group1");
                        if (!clusterRoleBinding.getRoleRef().equals(roleRef)) {
                            return false;
                        }
                        if (clusterRoleBinding.getSubjects() == null) {
                            return false;
                        }
                        return clusterRoleBinding.getSubjects().equals(List.of(subject));
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
