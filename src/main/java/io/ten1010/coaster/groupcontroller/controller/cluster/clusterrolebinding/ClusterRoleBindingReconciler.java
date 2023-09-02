package io.ten1010.coaster.groupcontroller.controller.cluster.clusterrolebinding;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.coaster.groupcontroller.controller.cluster.clusterrole.ResourceGroupClusterRoleName;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class ClusterRoleBindingReconciler implements Reconciler {

    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    private static V1RoleRef buildClusterRoleRef(String clusterRoleName) {
        return new V1RoleRefBuilder()
                .withApiGroup("rbac.authorization.k8s.io")
                .withKind("ClusterRole")
                .withName(clusterRoleName)
                .build();
    }

    private static Optional<V1RoleRef> getRoleRef(V1ClusterRoleBinding clusterRoleBinding) {
        return Optional.ofNullable(clusterRoleBinding.getRoleRef());
    }

    private static List<V1Subject> getSubjects(V1ClusterRoleBinding clusterRoleBinding) {
        return clusterRoleBinding.getSubjects() == null ? new ArrayList<>() : clusterRoleBinding.getSubjects();
    }

    private static V1OwnerReference buildOwnerReference(String groupName, String groupUid) {
        V1OwnerReferenceBuilder builder = new V1OwnerReferenceBuilder();
        return builder.withApiVersion(V1ResourceGroup.API_VERSION)
                .withBlockOwnerDeletion(true)
                .withController(true)
                .withKind(V1ResourceGroup.KIND)
                .withName(groupName)
                .withUid(groupUid)
                .build();
    }

    private static V1OwnerReference buildOwnerReference(V1ResourceGroup group) {
        Objects.requireNonNull(group.getMetadata());
        Objects.requireNonNull(group.getMetadata().getName());
        Objects.requireNonNull(group.getMetadata().getUid());
        return buildOwnerReference(group.getMetadata().getName(), group.getMetadata().getUid());
    }

    private KubernetesApiReconcileExceptionHandlingTemplate template;
    private Indexer<V1ResourceGroup> groupIndexer;
    private Indexer<V1ClusterRoleBinding> clusterRoleBindingIndexer;
    private Indexer<V1ClusterRole> clusterRoleIndexer;
    private RbacAuthorizationV1Api rbacAuthorizationV1Api;

    public ClusterRoleBindingReconciler(
            Indexer<V1ResourceGroup> groupIndexer,
            Indexer<V1ClusterRoleBinding> clusterRoleBindingIndexer,
            Indexer<V1ClusterRole> clusterRoleIndexer,
            RbacAuthorizationV1Api rbacAuthorizationV1Api) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.groupIndexer = groupIndexer;
        this.clusterRoleBindingIndexer = clusterRoleBindingIndexer;
        this.clusterRoleIndexer = clusterRoleIndexer;
        this.rbacAuthorizationV1Api = rbacAuthorizationV1Api;
    }

    @Override
    public Result reconcile(Request request) {
        return this.template.execute(
                () -> {
                    if (!ResourceGroupClusterRoleBindingName.isResourceGroupClusterRoleBindingName(request.getName())) {
                        return new Result(false);
                    }
                    String groupName = ResourceGroupClusterRoleBindingName.fromClusterRoleBindingName(request.getName()).getResourceGroupName();
                    V1ResourceGroup group = this.groupIndexer.getByKey(groupName);
                    if (group == null) {
                        deleteClusterRoleBindingIfExist(request.getName());
                        return new Result(false);
                    }
                    String clusterRoleName = new ResourceGroupClusterRoleName(groupName).getName();
                    V1RoleRef roleRef = buildClusterRoleRef(clusterRoleName);
                    Objects.requireNonNull(group.getSpec());
                    List<V1Subject> subjects = group.getSpec().getSubjects();
                    V1ClusterRoleBinding clusterRoleBinding = this.clusterRoleBindingIndexer.getByKey(KeyUtil.buildKey(request.getName()));
                    if (clusterRoleBinding == null) {
                        V1ClusterRole clusterRole = this.clusterRoleIndexer.getByKey(KeyUtil.buildKey(clusterRoleName));
                        if (clusterRole == null) {
                            return new Result(true, Duration.ofSeconds(3));
                        }
                        Objects.requireNonNull(group.getMetadata());
                        createClusterRoleBinding(request.getName(), roleRef, subjects, buildOwnerReference(group));
                        return new Result(false);
                    }
                    Optional<V1RoleRef> roleRefOpt = getRoleRef(clusterRoleBinding);
                    if (roleRefOpt.isPresent() && roleRefOpt.get().equals(roleRef) && getSubjects(clusterRoleBinding).equals(subjects)) {
                        return new Result(false);
                    }
                    updateClusterRoleBinding(clusterRoleBinding, roleRef, subjects);
                    return new Result(false);
                },
                request);
    }

    private void createClusterRoleBinding(String name, V1RoleRef roleRef, List<V1Subject> subjects, V1OwnerReference ownerReference) throws ApiException {
        V1ClusterRoleBindingBuilder builder = new V1ClusterRoleBindingBuilder();
        V1ClusterRoleBinding clusterRoleBinding = builder.withNewMetadata()
                .withName(name)
                .withOwnerReferences(ownerReference)
                .endMetadata()
                .withRoleRef(roleRef)
                .withSubjects(subjects)
                .build();
        this.rbacAuthorizationV1Api.createClusterRoleBinding(clusterRoleBinding, null, null, null, null);
    }

    private void updateClusterRoleBinding(V1ClusterRoleBinding target, V1RoleRef roleRef, List<V1Subject> subjects) throws ApiException {
        V1ClusterRoleBindingBuilder builder = new V1ClusterRoleBindingBuilder(target);
        V1ClusterRoleBinding updated = builder
                .withRoleRef(roleRef)
                .withSubjects(subjects)
                .build();
        V1ObjectMeta meta = target.getMetadata();
        Objects.requireNonNull(meta);
        Objects.requireNonNull(meta.getName());
        this.rbacAuthorizationV1Api.replaceClusterRoleBinding(meta.getName(), updated, null, null, null, null);
    }

    private void deleteClusterRoleBinding(String name) throws ApiException {
        this.rbacAuthorizationV1Api.deleteClusterRoleBinding(
                name,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private void deleteClusterRoleBindingIfExist(String name) throws ApiException {
        V1ClusterRole clusterRole = this.clusterRoleIndexer.getByKey(KeyUtil.buildKey(name));
        if (clusterRole == null) {
            return;
        }
        deleteClusterRoleBinding(name);
    }

}
