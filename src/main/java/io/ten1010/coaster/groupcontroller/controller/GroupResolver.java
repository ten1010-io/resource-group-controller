package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Pod;
import io.ten1010.coaster.groupcontroller.core.IndexNameConstants;
import io.ten1010.coaster.groupcontroller.core.K8sObjectUtil;
import io.ten1010.coaster.groupcontroller.core.KeyUtil;
import io.ten1010.coaster.groupcontroller.core.PodUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GroupResolver {

    private Indexer<V1ResourceGroup> groupIndexer;

    public GroupResolver(Indexer<V1ResourceGroup> groupIndexer) {
        this.groupIndexer = groupIndexer;
    }

    public List<V1ResourceGroup> resolve(V1Pod pod) {
        List<V1ResourceGroup> groupsContainingNamespace = resolve(K8sObjectUtil.getNamespace(pod));
        if (!PodUtil.isDaemonSetPod(pod)) {
            return groupsContainingNamespace;
        }
        List<V1ResourceGroup> groupsContainingDaemonSet = this.groupIndexer.byIndex(
                IndexNameConstants.BY_DAEMON_SET_KEY_TO_GROUP_OBJECT,
                KeyUtil.buildKey(K8sObjectUtil.getNamespace(pod), PodUtil.getDaemonSetOwnerReference(pod).getName()));

        return Stream.concat(groupsContainingNamespace.stream(), groupsContainingDaemonSet.stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<V1ResourceGroup> resolve(V1DaemonSet daemonSet) {
        List<V1ResourceGroup> groupsContainingNamespace = resolve(K8sObjectUtil.getNamespace(daemonSet));
        List<V1ResourceGroup> groupsContainingDaemonSet = this.groupIndexer.byIndex(
                IndexNameConstants.BY_DAEMON_SET_KEY_TO_GROUP_OBJECT,
                KeyUtil.buildKey(K8sObjectUtil.getNamespace(daemonSet), K8sObjectUtil.getName(daemonSet)));

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
