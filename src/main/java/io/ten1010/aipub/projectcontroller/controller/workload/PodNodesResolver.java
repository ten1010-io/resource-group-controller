package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1Pod;
import io.ten1010.aipub.projectcontroller.controller.BoundObjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.NodeUtils;
import java.util.List;
import java.util.Optional;

public class PodNodesResolver {

  private final RootWorkloadControllerResolver rootWorkloadControllerResolver;
  private final CompositeWorkloadControllerNodesResolver workloadControllerNodesResolver;
  private final NamespaceNameResolver namespaceNameResolver;
  private final KeyResolver keyResolver;
  private final Indexer<V1alpha1Project> projectIndexer;
  private final BoundObjectResolver boundObjectResolver;

  public PodNodesResolver(
      RootWorkloadControllerResolver rootWorkloadControllerResolver,
      CompositeWorkloadControllerNodesResolver workloadControllerNodesResolver,
      SharedInformerFactory sharedInformerFactory) {
    this.rootWorkloadControllerResolver = rootWorkloadControllerResolver;
    this.workloadControllerNodesResolver = workloadControllerNodesResolver;
    this.namespaceNameResolver = new NamespaceNameResolver();
    this.keyResolver = new KeyResolver();
    this.projectIndexer = sharedInformerFactory.getExistingSharedIndexInformer(
        V1alpha1Project.class).getIndexer();
    this.boundObjectResolver = new BoundObjectResolver(sharedInformerFactory);
  }

  /**
   * 파드가 스케줄될 수 있는 project-managed 노드를 해석한다. 가장 가까운 지원 워크로드 root가
   * 있으면 그 타입의 resolver를 따르고(DaemonSet은 NodeGroup daemonSetPolicy까지 반영),
   * root가 없으면 파드가 속한 네임스페이스의 Project에 바인딩된 노드를 쓴다.
   */
  public List<V1Node> getNodes(V1Pod pod) {
    Optional<KubernetesObject> rootControllerOpt = this.rootWorkloadControllerResolver.getRootController(
        pod);
    if (rootControllerOpt.isPresent()) {
      return this.workloadControllerNodesResolver.getNodes(rootControllerOpt.get());
    }

    String projName = this.namespaceNameResolver.resolveProjectName(
        K8sObjectUtils.getNamespace(pod));
    String projKey = this.keyResolver.resolveKey(projName);
    V1alpha1Project project = this.projectIndexer.getByKey(projKey);
    if (project == null) {
      return List.of();
    }

    List<V1Node> allBoundNodes = this.boundObjectResolver.getAllBoundNodes(project);

    return NodeUtils.getProjectManagedNodes(allBoundNodes);
  }

}
