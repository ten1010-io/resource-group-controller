package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.coaster.groupcontroller.core.*;
import io.ten1010.coaster.groupcontroller.model.V1Beta2ResourceGroup;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Reconciliation {

    private static Optional<V1Affinity> reconcileAffinity(@Nullable V1Affinity existingAffinity, List<V1Beta2ResourceGroup> groups) {
        List<V1NodeSelectorTerm> terms = new ArrayList<>();
        if (existingAffinity == null) {
            if (groups.isEmpty()) {
                return Optional.empty();
            }
            terms.addAll(buildResourceGroupExclusiveNodeSelectorTerms(groups));
            return Optional.of(new V1AffinityBuilder()
                    .withNewNodeAffinity()
                    .withNewRequiredDuringSchedulingIgnoredDuringExecution()
                    .withNodeSelectorTerms(terms)
                    .endRequiredDuringSchedulingIgnoredDuringExecution()
                    .endNodeAffinity()
                    .build());
        }
        V1Affinity clone = new V1AffinityBuilder(existingAffinity).build();
        terms.addAll(extractNonResourceGroupExclusiveNodeSelectorTerms(clone));
        if (!groups.isEmpty()) {
            terms.addAll(buildResourceGroupExclusiveNodeSelectorTerms(groups));
        }
        if (terms.isEmpty()) {
            if (clone.getNodeAffinity() == null) {
                return Optional.of(clone);
            }
            if (clone.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution() == null) {
                return Optional.of(clone);
            }
            clone.getNodeAffinity().setRequiredDuringSchedulingIgnoredDuringExecution(null);
            return Optional.of(clone);
        }
        if (clone.getNodeAffinity() == null) {
            V1NodeAffinity nodeAffinity = new V1NodeAffinityBuilder()
                    .withNewRequiredDuringSchedulingIgnoredDuringExecution()
                    .withNodeSelectorTerms(terms)
                    .endRequiredDuringSchedulingIgnoredDuringExecution()
                    .build();
            clone.setNodeAffinity(nodeAffinity);
            return Optional.of(clone);
        }
        if (clone.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution() == null) {
            V1NodeSelector selector = new V1NodeSelectorBuilder()
                    .withNodeSelectorTerms(terms)
                    .build();
            clone.getNodeAffinity().setRequiredDuringSchedulingIgnoredDuringExecution(selector);
            return Optional.of(clone);
        }
        clone.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution().setNodeSelectorTerms(terms);
        return Optional.of(clone);
    }

    private static List<V1Toleration> reconcileTolerations(List<V1Toleration> existingTolerations, List<V1Beta2ResourceGroup> groups) {
        List<V1Toleration> tolerations = replaceAllKeyAllEffectTolerations(existingTolerations);
        tolerations = replaceAllKeyNoScheduleEffectTolerations(tolerations);
        tolerations = removeResourceGroupExclusiveTolerations(tolerations);
        tolerations.addAll(buildResourceGroupExclusiveTolerations(groups));
        return tolerations;
    }

    private static List<V1Toleration> replaceAllKeyAllEffectTolerations(List<V1Toleration> tolerations) {
        return tolerations.stream()
                .map(e -> {
                    if (isAllKeyAllEffectToleration(e)) {
                        return new V1TolerationBuilder()
                                .withEffect(Taints.EFFECT_NO_EXECUTE)
                                .withKey(null)
                                .withOperator(e.getOperator())
                                .withTolerationSeconds(e.getTolerationSeconds())
                                .withValue(e.getValue())
                                .build();
                    }
                    return e;
                })
                .collect(Collectors.toList());
    }

    private static List<V1Toleration> replaceAllKeyNoScheduleEffectTolerations(List<V1Toleration> tolerations) {
        return tolerations.stream()
                .map(e -> {
                    if (isAllKeyNoScheduleEffectToleration(e)) {
                        return new V1TolerationBuilder()
                                .withEffect(Taints.EFFECT_NO_SCHEDULE)
                                .withKey("node-role.kubernetes.io/control-plane")
                                .withOperator("Exists")
                                .withTolerationSeconds(null)
                                .withValue(null)
                                .build();
                    }
                    return e;
                })
                .collect(Collectors.toList());
    }

    private static boolean isAllKeyAllEffectToleration(V1Toleration toleration) {
        return (toleration.getKey() == null && toleration.getEffect() == null);
    }

    private static boolean isAllKeyNoScheduleEffectToleration(V1Toleration toleration) {
        if (toleration.getEffect() == null) {
            return false;
        }
        return (toleration.getKey() == null && toleration.getEffect().equals(Taints.EFFECT_NO_SCHEDULE));
    }

    private static List<V1NodeSelectorTerm> extractNonResourceGroupExclusiveNodeSelectorTerms(V1Affinity affinity) {
        if (affinity.getNodeAffinity() == null) {
            return List.of();
        }
        if (affinity.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution() == null) {
            return List.of();
        }
        return affinity.getNodeAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .getNodeSelectorTerms()
                .stream()
                .filter(term -> term.getMatchExpressions() != null)
                .filter(term -> term.getMatchExpressions()
                        .stream()
                        .noneMatch(Reconciliation::isResourceGroupExclusiveNodeSelectorRequirement))
                .collect(Collectors.toList());
    }

    private static List<V1NodeSelectorTerm> buildResourceGroupExclusiveNodeSelectorTerms(List<V1Beta2ResourceGroup> groups) {
        V1NodeSelectorRequirement requirement = new V1NodeSelectorRequirementBuilder()
                .withKey(Labels.KEY_RESOURCE_GROUP_EXCLUSIVE)
                .withOperator("In")
                .withValues(groups.stream()
                        .map(K8sObjectUtil::getName)
                        .distinct()
                        .collect(Collectors.toList()))
                .build();
        V1NodeSelectorTerm term = new V1NodeSelectorTermBuilder()
                .addToMatchExpressions(requirement)
                .build();
        return List.of(term);
    }

    private static boolean isResourceGroupExclusiveNodeSelectorRequirement(V1NodeSelectorRequirement requirement) {
        if (requirement.getKey() == null) {
            return false;
        }
        return requirement.getKey().equals(Labels.KEY_RESOURCE_GROUP_EXCLUSIVE);
    }

    private static List<V1Toleration> removeResourceGroupExclusiveTolerations(List<V1Toleration> tolerations) {
        return tolerations.stream()
                .filter(e -> !isResourceGroupExclusiveToleration(e))
                .collect(Collectors.toList());
    }

    private static List<V1Toleration> buildResourceGroupExclusiveTolerations(List<V1Beta2ResourceGroup> groups) {
        return groups.stream()
                .flatMap(e -> buildResourceGroupExclusiveTolerations(e).stream())
                .collect(Collectors.toList());
    }

    private static List<V1Toleration> buildResourceGroupExclusiveTolerations(V1Beta2ResourceGroup group) {
        V1TolerationBuilder baseBuilder = new V1TolerationBuilder()
                .withKey(Taints.KEY_RESOURCE_GROUP_EXCLUSIVE)
                .withValue(K8sObjectUtil.getName(group))
                .withOperator("Equal");
        V1Toleration noSchedule = baseBuilder
                .withEffect(Taints.EFFECT_NO_SCHEDULE)
                .build();

        return List.of(noSchedule);
    }

    private static boolean isResourceGroupExclusiveToleration(V1Toleration toleration) {
        if (toleration.getKey() == null) {
            return false;
        }
        return toleration.getKey().equals(Taints.KEY_RESOURCE_GROUP_EXCLUSIVE);
    }

    private Indexer<V1Beta2ResourceGroup> groupIndexer;

    public Reconciliation(Indexer<V1Beta2ResourceGroup> groupIndexer) {
        this.groupIndexer = groupIndexer;
    }

    public Optional<V1Affinity> reconcileUncontrolledCronJobAffinity(V1CronJob cronJob) {
        if (K8sObjectUtil.isControlled(cronJob)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(cronJob));

        return reconcileAffinity(CronJobUtil.getAffinity(cronJob).orElse(null), groups);
    }

    public Optional<V1Affinity> reconcileUncontrolledDaemonSetAffinity(V1DaemonSet daemonSet) {
        if (K8sObjectUtil.isControlled(daemonSet)) {
            throw new IllegalArgumentException();
        }
        return DaemonSetUtil.getAffinity(daemonSet);
    }

    public Optional<V1Affinity> reconcileUncontrolledDeploymentAffinity(V1Deployment deployment) {
        if (K8sObjectUtil.isControlled(deployment)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(deployment));

        return reconcileAffinity(DeploymentUtil.getAffinity(deployment).orElse(null), groups);
    }

    public Optional<V1Affinity> reconcileUncontrolledJobAffinity(V1Job job) {
        if (K8sObjectUtil.isControlled(job)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(job));

        return reconcileAffinity(JobUtil.getAffinity(job).orElse(null), groups);
    }

    public Optional<V1Affinity> reconcileUncontrolledPodAffinity(V1Pod pod) {
        if (K8sObjectUtil.isControlled(pod)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(pod));

        return reconcileAffinity(PodUtil.getAffinity(pod).orElse(null), groups);
    }

    public Optional<V1Affinity> reconcileUncontrolledReplicaSetAffinity(V1ReplicaSet replicaSet) {
        if (K8sObjectUtil.isControlled(replicaSet)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(replicaSet));

        return reconcileAffinity(ReplicaSetUtil.getAffinity(replicaSet).orElse(null), groups);
    }

    public Optional<V1Affinity> reconcileUncontrolledReplicationControllerAffinity(V1ReplicationController replicationController) {
        if (K8sObjectUtil.isControlled(replicationController)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(replicationController));

        return reconcileAffinity(ReplicationControllerUtil.getAffinity(replicationController).orElse(null), groups);
    }

    public Optional<V1Affinity> reconcileUncontrolledStatefulSetAffinity(V1StatefulSet statefulSet) {
        if (K8sObjectUtil.isControlled(statefulSet)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(statefulSet));

        return reconcileAffinity(StatefulSetUtil.getAffinity(statefulSet).orElse(null), groups);
    }

    public List<V1Toleration> reconcileUncontrolledCronJobTolerations(V1CronJob cronJob) {
        if (K8sObjectUtil.isControlled(cronJob)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(cronJob));

        return reconcileTolerations(CronJobUtil.getTolerations(cronJob), groups);
    }

    public List<V1Toleration> reconcileUncontrolledDaemonSetTolerations(V1DaemonSet daemonSet) {
        if (K8sObjectUtil.isControlled(daemonSet)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groupsContainingNamespace = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(daemonSet));
        List<V1Beta2ResourceGroup> groupsContainingDaemonSet = this.groupIndexer.byIndex(
                IndexNames.BY_DAEMON_SET_KEY_TO_GROUP_OBJECT,
                KeyUtil.buildKey(K8sObjectUtil.getNamespace(daemonSet), K8sObjectUtil.getName(daemonSet)));
        List<V1Beta2ResourceGroup> groupsAllowingAllDaemonSet = this.groupIndexer.byIndex(
                IndexNames.BY_GROUP_ALLOW_ALL_DAEMON_SET_TO_GROUP_OBJECT, Boolean.TRUE.toString()
        );
        List<V1Beta2ResourceGroup> groups = Stream.of(
                        groupsContainingNamespace, groupsContainingDaemonSet, groupsAllowingAllDaemonSet)
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());

        return reconcileTolerations(DaemonSetUtil.getTolerations(daemonSet), groups);
    }

    public List<V1Toleration> reconcileUncontrolledDeploymentTolerations(V1Deployment deployment) {
        if (K8sObjectUtil.isControlled(deployment)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(deployment));

        return reconcileTolerations(DeploymentUtil.getTolerations(deployment), groups);
    }

    public List<V1Toleration> reconcileUncontrolledJobTolerations(V1Job job) {
        if (K8sObjectUtil.isControlled(job)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(job));

        return reconcileTolerations(JobUtil.getTolerations(job), groups);
    }

    public List<V1Toleration> reconcileUncontrolledPodTolerations(V1Pod pod) {
        if (K8sObjectUtil.isControlled(pod)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(pod));

        return reconcileTolerations(PodUtil.getTolerations(pod), groups);
    }

    public List<V1Toleration> reconcileUncontrolledReplicaSetTolerations(V1ReplicaSet replicaSet) {
        if (K8sObjectUtil.isControlled(replicaSet)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(replicaSet));

        return reconcileTolerations(ReplicaSetUtil.getTolerations(replicaSet), groups);
    }

    public List<V1Toleration> reconcileUncontrolledReplicationControllerTolerations(V1ReplicationController replicationController) {
        if (K8sObjectUtil.isControlled(replicationController)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(replicationController));

        return reconcileTolerations(ReplicationControllerUtil.getTolerations(replicationController), groups);
    }

    public List<V1Toleration> reconcileUncontrolledStatefulSetTolerations(V1StatefulSet statefulSet) {
        if (K8sObjectUtil.isControlled(statefulSet)) {
            throw new IllegalArgumentException();
        }
        List<V1Beta2ResourceGroup> groups = this.groupIndexer.byIndex(
                IndexNames.BY_NAMESPACE_NAME_TO_GROUP_OBJECT, K8sObjectUtil.getNamespace(statefulSet));

        return reconcileTolerations(StatefulSetUtil.getTolerations(statefulSet), groups);
    }

}
