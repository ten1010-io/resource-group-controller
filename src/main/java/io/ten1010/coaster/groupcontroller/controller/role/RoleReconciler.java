package io.ten1010.coaster.groupcontroller.controller.role;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
public class RoleReconciler implements Reconciler {

    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    private static final List<V1PolicyRule> RULES;

    static {
        RULES = buildRules();
    }

    /* grant all verbs permission for following resources
        1. core
	        1. Pod
	        2. Service
	        3. ConfigMap
	        4. Secret
	        5. PersistentVolumeClaim
	        6. ServiceAccount
	        7. LimitRange
	        8. Event
        2. events.k8s.io
	        1. Event
        3. batch
	        1. Job
	        2. CronJob
        4. apps
	        1. Deployment
	        2. StatefulSet
        5. autoscaling
	        1. HorizontalPodAutoscaler
        6. policy
	        1. PodDisruptionBudget
     */
    private static List<V1PolicyRule> buildRules() {
        V1PolicyRule coreApiRule = new V1PolicyRuleBuilder().withApiGroups("")
                .withResources("pods", "services", "configmaps", "secrets", "persistentvolumeclaims", "serviceaccounts", "limitranges", "events")
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
                .withResources("deployments", "statefulsets")
                .withVerbs("*")
                .build();
        V1PolicyRule autoscalingApiRule = new V1PolicyRuleBuilder().withApiGroups("autoscaling")
                .withResources("horizontalpodautoscalers")
                .withVerbs("*")
                .build();
        V1PolicyRule poddisruptionbudgetApiRule = new V1PolicyRuleBuilder().withApiGroups("policy")
                .withResources("poddisruptionbudgets")
                .withVerbs("*")
                .build();

        return List.of(coreApiRule, eventApiRule, batchApiRule, appsApiRule, autoscalingApiRule, poddisruptionbudgetApiRule);
    }

    private static List<V1PolicyRule> getRules(V1Role role) {
        return role.getRules() == null ? new ArrayList<>() : role.getRules();
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
    private Indexer<V1Role> roleIndexer;
    private RbacAuthorizationV1Api rbacAuthorizationV1Api;

    public RoleReconciler(
            Indexer<V1Namespace> namespaceIndexer,
            Indexer<V1ResourceGroup> groupIndexer,
            RoleNameUtil roleNameUtil,
            Indexer<V1Role> roleIndexer,
            RbacAuthorizationV1Api rbacAuthorizationV1Api) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.roleNameUtil = roleNameUtil;
        this.namespaceIndexer = namespaceIndexer;
        this.groupIndexer = groupIndexer;
        this.roleIndexer = roleIndexer;
        this.rbacAuthorizationV1Api = rbacAuthorizationV1Api;
    }

    /**
     * Reconcile given role based on {@link Request} to ensure its namespace and its policy compared with {@link V1ResourceGroup}.
     *
     * @param request the reconcile request which triggered by watch events
     * @return the result
     */
    @Override
    public Result reconcile(Request request) {
        return this.template.execute(
                () -> {
                    if (!this.roleNameUtil.isResourceGroupRoleNameFormat(request.getName())) {
                        return new Result(false);
                    }
                    V1Namespace namespace = this.namespaceIndexer.getByKey(KeyUtil.buildKey(request.getNamespace()));
                    if (namespace == null) {
                        return new Result(false);
                    }
                    String groupName = this.roleNameUtil.getResourceGroupNameFromRoleName(request.getName());
                    V1ResourceGroup group = this.groupIndexer.getByKey(groupName);
                    if (group == null) {
                        deleteRoleIfExist(request.getNamespace(), request.getName());
                        return new Result(false);
                    }
                    Objects.requireNonNull(group.getSpec());
                    List<String> namespacesInGroup = group.getSpec().getNamespaces();
                    String roleKey = KeyUtil.buildKey(request.getNamespace(), request.getName());
                    if (!namespacesInGroup.contains(request.getNamespace())) {
                        deleteRoleIfExist(request.getNamespace(), request.getName());
                        return new Result(false);
                    }
                    V1Role role = this.roleIndexer.getByKey(roleKey);
                    if (role == null) {
                        Objects.requireNonNull(group.getMetadata());
                        createRole(request.getNamespace(), request.getName(), RULES, buildOwnerReference(group));
                        return new Result(false);
                    }
                    if (getRules(role).equals(RULES)) {
                        return new Result(false);
                    }
                    updateRole(role, RULES);
                    return new Result(false);
                },
                request);
    }

    private void createRole(String namespace, String name, List<V1PolicyRule> rules, V1OwnerReference ownerReference) throws ApiException {
        V1RoleBuilder builder = new V1RoleBuilder();
        V1Role role = builder.withNewMetadata()
                .withNamespace(namespace)
                .withName(name)
                .withOwnerReferences(ownerReference)
                .endMetadata()
                .withRules(rules)
                .build();
        this.rbacAuthorizationV1Api.createNamespacedRole(namespace, role, null, null, null, null);
    }

    private void updateRole(V1Role target, List<V1PolicyRule> rules) throws ApiException {
        V1RoleBuilder builder = new V1RoleBuilder(target);
        V1Role updated = builder.withRules(rules)
                .build();
        V1ObjectMeta meta = target.getMetadata();
        Objects.requireNonNull(meta);
        Objects.requireNonNull(meta.getNamespace());
        Objects.requireNonNull(meta.getName());
        this.rbacAuthorizationV1Api.replaceNamespacedRole(meta.getName(), meta.getNamespace(), updated, null, null, null, null);
    }

    private void deleteRole(String namespace, String name) throws ApiException {
        this.rbacAuthorizationV1Api.deleteNamespacedRole(name, namespace, null, null, null, null, null, null);
    }

    private void deleteRoleIfExist(String namespace, String name) throws ApiException {
        V1Role role = this.roleIndexer.getByKey(KeyUtil.buildKey(namespace, name));
        if (role == null) {
            return;
        }
        deleteRole(namespace, name);
    }

}
