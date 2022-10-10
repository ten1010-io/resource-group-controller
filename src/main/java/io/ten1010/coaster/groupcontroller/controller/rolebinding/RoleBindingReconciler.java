package io.ten1010.coaster.groupcontroller.controller.rolebinding;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.coaster.groupcontroller.controller.role.RoleNameUtil;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;

@Slf4j
public class RoleBindingReconciler implements Reconciler {

    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    private static V1RoleRef buildRoleRef(String roleName) {
        return new V1RoleRefBuilder()
                .withApiGroup("rbac.authorization.k8s.io")
                .withKind("Role")
                .withName(roleName)
                .build();
    }

    private static String getResourceGroupName(Pair<Boolean, Matcher> checkResult) {
        if (!checkResult.getValue0()) {
            throw new IllegalArgumentException();
        }
        return checkResult.getValue1().group(1);
    }

    private static Optional<V1RoleRef> getRoleRef(V1RoleBinding roleBinding) {
        return Optional.ofNullable(roleBinding.getRoleRef());
    }

    private static List<V1Subject> getSubjects(V1RoleBinding roleBinding) {
        return roleBinding.getSubjects() == null ? new ArrayList<>() : roleBinding.getSubjects();
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
    private RoleNameUtil roleNameUtil;
    private Indexer<V1Namespace> namespaceIndexer;
    private Indexer<V1ResourceGroup> groupIndexer;
    private Indexer<V1RoleBinding> roleBindingIndexer;
    private Indexer<V1Role> roleIndexer;
    private RbacAuthorizationV1Api rbacAuthorizationV1Api;

    public RoleBindingReconciler(
            Indexer<V1Namespace> namespaceIndexer,
            Indexer<V1ResourceGroup> groupIndexer,
            RoleNameUtil roleNameUtil,
            Indexer<V1RoleBinding> roleBindingIndexer,
            Indexer<V1Role> roleIndexer,
            RbacAuthorizationV1Api rbacAuthorizationV1Api) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.roleNameUtil = roleNameUtil;
        this.namespaceIndexer = namespaceIndexer;
        this.groupIndexer = groupIndexer;
        this.roleBindingIndexer = roleBindingIndexer;
        this.roleIndexer = roleIndexer;
        this.rbacAuthorizationV1Api = rbacAuthorizationV1Api;
    }

    @Override
    public Result reconcile(Request request) {
        return this.template.execute(
                () -> {
                    Pair<Boolean, Matcher> result = this.roleNameUtil.checkResourceGroupRoleBindingNameFormat(request.getName());
                    if (!result.getValue0()) {
                        return new Result(false);
                    }
                    V1Namespace namespace = this.namespaceIndexer.getByKey(KeyUtil.buildKey(request.getNamespace()));
                    if (namespace == null) {
                        return new Result(false);
                    }
                    String groupName = getResourceGroupName(result);
                    V1ResourceGroup group = this.groupIndexer.getByKey(groupName);
                    if (group == null) {
                        deleteRoleBindingIfExist(request.getNamespace(), request.getName());
                        return new Result(false);
                    }
                    Objects.requireNonNull(group.getSpec());
                    List<String> namespacesInGroup = group.getSpec().getNamespaces();
                    if (!namespacesInGroup.contains(request.getNamespace())) {
                        deleteRoleBindingIfExist(request.getNamespace(), request.getName());
                        return new Result(false);
                    }
                    String roleName = this.roleNameUtil.buildRoleName(groupName);
                    V1RoleRef roleRef = buildRoleRef(roleName);
                    List<V1Subject> subjects = group.getSpec().getSubjects();
                    V1RoleBinding roleBinding = this.roleBindingIndexer.getByKey(KeyUtil.buildKey(request.getNamespace(), request.getName()));
                    if (roleBinding == null) {
                        String roleKey = KeyUtil.buildKey(request.getNamespace(), roleRef.getName());
                        V1Role role = this.roleIndexer.getByKey(roleKey);
                        if (role == null) {
                            return new Result(true, Duration.ofSeconds(3));
                        }
                        Objects.requireNonNull(group.getMetadata());
                        createRoleBinding(request.getNamespace(), request.getName(), roleRef, subjects, buildOwnerReference(group));
                        return new Result(false);
                    }
                    Optional<V1RoleRef> roleRefOpt = getRoleRef(roleBinding);
                    if (roleRefOpt.isPresent() && roleRefOpt.get().equals(roleRef) && getSubjects(roleBinding).equals(subjects)) {
                        return new Result(false);
                    }
                    updateRoleBinding(roleBinding, roleRef, subjects);
                    return new Result(false);
                },
                request);
    }

    private void createRoleBinding(String namespace, String name, V1RoleRef roleRef, List<V1Subject> subjects, V1OwnerReference ownerReference) throws ApiException {
        V1RoleBindingBuilder builder = new V1RoleBindingBuilder();
        V1RoleBinding roleBinding = builder.withNewMetadata()
                .withNamespace(namespace)
                .withName(name)
                .withOwnerReferences(ownerReference)
                .endMetadata()
                .withRoleRef(roleRef)
                .withSubjects(subjects)
                .build();
        this.rbacAuthorizationV1Api.createNamespacedRoleBinding(namespace, roleBinding, null, null, null, null);
    }

    private void updateRoleBinding(V1RoleBinding target, V1RoleRef roleRef, List<V1Subject> subjects) throws ApiException {
        V1RoleBindingBuilder builder = new V1RoleBindingBuilder(target);
        V1RoleBinding updated = builder
                .withRoleRef(roleRef)
                .withSubjects(subjects)
                .build();
        V1ObjectMeta meta = target.getMetadata();
        Objects.requireNonNull(meta);
        Objects.requireNonNull(meta.getNamespace());
        Objects.requireNonNull(meta.getName());
        this.rbacAuthorizationV1Api.replaceNamespacedRoleBinding(meta.getName(), meta.getNamespace(), updated, null, null, null, null);
    }

    private void deleteRoleBinding(String namespace, String name) throws ApiException {
        this.rbacAuthorizationV1Api.deleteNamespacedRoleBinding(
                name,
                namespace,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private void deleteRoleBindingIfExist(String namespace, String name) throws ApiException {
        V1RoleBinding roleBinding = this.roleBindingIndexer.getByKey(KeyUtil.buildKey(namespace, name));
        if (roleBinding == null) {
            return;
        }
        deleteRoleBinding(namespace, name);
    }

}
