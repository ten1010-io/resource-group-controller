package io.ten1010.aipub.projectcontroller.controller.workload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.ten1010.aipub.projectcontroller.domain.k8s.IsolationModeValueEnum;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeKey;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectManagedValueEnum;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class PodReconcilerTest {

  private static final String NAMESPACE = "trident";
  private static final String POD_NAME = "trident-node-linux-ck9td";
  private static final String STRICT_NODE_NAME = "vnode1.pnode7";
  private static final String OTHER_NODE_NAME = "vnode2.pnode15";

  private Cache<V1Pod> podCache;
  private Cache<V1Node> nodeCache;
  private Cache<V1Namespace> namespaceCache;
  private SharedInformerFactory factory;
  private PodNodesResolver mockPodNodesResolver;

  private static V1Node strictProjectManagedNode(String name) {
    return new V1Node().metadata(new V1ObjectMeta()
        .name(name)
        .labels(Map.of(
            LabelConstants.PROJECT_MANAGED_KEY, ProjectManagedValueEnum.TRUE.getStr(),
            LabelConstants.ISOLATION_MODE_KEY, IsolationModeValueEnum.STRICT.getStr())));
  }

  /** 임의 CR(TridentOrchestrator)이 소유한 DaemonSet 이 만든 파드. */
  private static V1Pod crOwnedPod(String nodeName) {
    return new V1Pod()
        .metadata(new V1ObjectMeta()
            .name(POD_NAME)
            .namespace(NAMESPACE)
            .ownerReferences(List.of(new V1OwnerReference()
                .apiVersion("apps/v1").kind("DaemonSet").name("trident-node-linux")
                .uid("ds-uid").controller(true))))
        .spec(new V1PodSpec().nodeName(nodeName));
  }

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    this.podCache = new Cache<>();
    this.nodeCache = new Cache<>();
    this.namespaceCache = new Cache<>();
    this.mockPodNodesResolver = mock(PodNodesResolver.class);

    this.factory = mock(SharedInformerFactory.class);
    SharedIndexInformer<V1Pod> podInformer = mock(SharedIndexInformer.class);
    when(podInformer.getIndexer()).thenReturn(this.podCache);
    when(this.factory.getExistingSharedIndexInformer(V1Pod.class)).thenReturn(podInformer);
    SharedIndexInformer<V1Node> nodeInformer = mock(SharedIndexInformer.class);
    when(nodeInformer.getIndexer()).thenReturn(this.nodeCache);
    when(this.factory.getExistingSharedIndexInformer(V1Node.class)).thenReturn(nodeInformer);
    SharedIndexInformer<V1alpha1Project> projectInformer = mock(SharedIndexInformer.class);
    when(projectInformer.getIndexer()).thenReturn(mock(Indexer.class));
    when(this.factory.getExistingSharedIndexInformer(V1alpha1Project.class))
        .thenReturn(projectInformer);
  }

  private PodReconciler createReconciler() {
    return new PodReconciler(
        this.factory,
        mock(K8sApiProvider.class),
        this.mockPodNodesResolver,
        new NamespaceAllowlistResolver(this.namespaceCache));
  }

  private MockedConstruction<CoreV1Api> mockCoreV1Api() {
    return mockConstruction(CoreV1Api.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
  }

  @Test
  @DisplayName("CR 소유 파드는 strict 노드에서도 삭제되지 않는다(허용 노드에 포함되면)")
  void crOwnedPodOnStrictNode_notDeleted() throws ApiException {
    V1Node node = strictProjectManagedNode(STRICT_NODE_NAME);
    this.nodeCache.add(node);
    this.podCache.add(crOwnedPod(STRICT_NODE_NAME));
    // 결정 2 이후 root 는 DaemonSet 자신이고, NodeGroup daemonSetPolicy 로 이 노드가 허용된다
    when(this.mockPodNodesResolver.getNodes(any())).thenReturn(List.of(node));

    try (MockedConstruction<CoreV1Api> mocked = mockCoreV1Api()) {
      PodReconciler reconciler = createReconciler();

      Result result = reconciler.reconcileInternal(new Request(NAMESPACE, POD_NAME));

      assertThat(result.isRequeue()).isFalse();
      verify(mocked.constructed().getFirst(), never()).deleteNamespacedPod(any(), any());
    }
  }

  @Test
  @DisplayName("노드 해석이 UnsupportedControllerException 이어도 파드를 삭제하지 않는다")
  void nodesResolutionFailure_doesNotDeletePod() {
    this.nodeCache.add(strictProjectManagedNode(STRICT_NODE_NAME));
    this.podCache.add(crOwnedPod(STRICT_NODE_NAME));
    when(this.mockPodNodesResolver.getNodes(any()))
        .thenThrow(new UnsupportedControllerException(
            new K8sObjectTypeKey("trident.netapp.io/v1", "TridentOrchestrator")));

    try (MockedConstruction<CoreV1Api> mocked = mockCoreV1Api()) {
      PodReconciler reconciler = createReconciler();

      // 예외는 AbstractReconciler 가 잡아 requeue 한다. 파드 삭제로 이어지지 않아야 한다
      Result result = reconciler.reconcile(new Request(NAMESPACE, POD_NAME));

      assertThat(result.isRequeue()).isTrue();
      verify(mocked.constructed().getFirst(), never()).deleteNamespacedPod(any(), any());
    }
  }

  @Test
  @DisplayName("허용 노드 밖의 파드는 strict 노드에서 여전히 삭제한다")
  void podOutsideAllowedProjectNodes_stillDeleted() throws ApiException {
    this.nodeCache.add(strictProjectManagedNode(STRICT_NODE_NAME));
    this.podCache.add(crOwnedPod(STRICT_NODE_NAME));
    when(this.mockPodNodesResolver.getNodes(any()))
        .thenReturn(List.of(strictProjectManagedNode(OTHER_NODE_NAME)));

    try (MockedConstruction<CoreV1Api> mocked = mockCoreV1Api()) {
      PodReconciler reconciler = createReconciler();

      Result result = reconciler.reconcileInternal(new Request(NAMESPACE, POD_NAME));

      assertThat(result.isRequeue()).isFalse();
      verify(mocked.constructed().getFirst()).deleteNamespacedPod(POD_NAME, NAMESPACE);
    }
  }

  @Test
  @DisplayName("allowlist 네임스페이스의 파드는 노드 해석 없이 조기 반환한다")
  void allowlistedNamespacePod_skippedBeforeNodesResolution() throws ApiException {
    this.namespaceCache.add(new V1Namespace().metadata(new V1ObjectMeta()
        .name(NAMESPACE)
        .labels(Map.of(LabelConstants.ALLOWLISTED_KEY, "true"))));
    this.nodeCache.add(strictProjectManagedNode(STRICT_NODE_NAME));
    this.podCache.add(crOwnedPod(STRICT_NODE_NAME));

    try (MockedConstruction<CoreV1Api> mocked = mockCoreV1Api()) {
      PodReconciler reconciler = createReconciler();

      Result result = reconciler.reconcileInternal(new Request(NAMESPACE, POD_NAME));

      assertThat(result.isRequeue()).isFalse();
      verify(mocked.constructed().getFirst(), never()).deleteNamespacedPod(any(), any());
    }
    verify(this.mockPodNodesResolver, never()).getNodes(any());
  }

}
