package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.ten1010.coaster.groupcontroller.core.IndexNameConstants;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import org.javatuples.Pair;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GroupResolver {

    public static Pair<Boolean, V1OwnerReference> isDaemonSetPod(V1Pod pod) {
        if (pod.getMetadata() == null || pod.getMetadata().getOwnerReferences() == null) {
            return Pair.with(false, null);
        }
        List<V1OwnerReference> references = pod.getMetadata().getOwnerReferences();
        Optional<V1OwnerReference> daemonSetRefOpt = references.stream()
                .filter(e -> {
                    if (!e.getKind().equals("DaemonSet")) {
                        return false;
                    }
                    if (!e.getApiVersion().equals("apps/v1")) {
                        return false;
                    }
                    return e.getController();
                })
                .findAny();
        return Pair.with(daemonSetRefOpt.isPresent(), daemonSetRefOpt.orElse(null));
    }

    private Indexer<V1ResourceGroup> groupIndexer;

    public GroupResolver(Indexer<V1ResourceGroup> groupIndexer) {
        this.groupIndexer = groupIndexer;
    }

    public List<V1ResourceGroup> resolve(V1Pod pod) {
        List<V1ResourceGroup> groupsContainingNamespace = resolve(ReconcilerUtil.getNamespace(pod));
        Pair<Boolean, V1OwnerReference> result = isDaemonSetPod(pod);
        if (!result.getValue0()) {
            return groupsContainingNamespace;
        }
        List<V1ResourceGroup> groupsContainingDaemonSet = this.groupIndexer.byIndex(
                IndexNameConstants.BY_DAEMON_SET_KEY_TO_GROUP_OBJECT,
                KeyUtil.buildKey(ReconcilerUtil.getNamespace(pod), result.getValue1().getName()));

        return Stream.concat(groupsContainingNamespace.stream(), groupsContainingDaemonSet.stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<V1ResourceGroup> resolve(V1DaemonSet daemonSet) {
        List<V1ResourceGroup> groupsContainingNamespace = resolve(ReconcilerUtil.getNamespace(daemonSet));
        List<V1ResourceGroup> groupsContainingDaemonSet = this.groupIndexer.byIndex(
                IndexNameConstants.BY_DAEMON_SET_KEY_TO_GROUP_OBJECT,
                KeyUtil.buildKey(ReconcilerUtil.getNamespace(daemonSet), ReconcilerUtil.getName(daemonSet)));

        return Stream.concat(groupsContainingNamespace.stream(), groupsContainingDaemonSet.stream())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<V1ResourceGroup> resolve(String namespace) {
        return this.groupIndexer.byIndex(
                IndexNameConstants.BY_NAMESPACE_NAME_TO_GROUP_OBJECT,
                namespace);
    }

}
