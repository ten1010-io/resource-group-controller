package io.ten1010.aipub.projectcontroller.controller.workload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RootWorkloadControllerResolverTest {

  private static final String NAMESPACE = "trident";

  /** 운영 배선과 동일한 지원 워크로드 타입 6종 (ControllerConfiguration 이 팩토리에서 수집하는 값) */
  private static final List<? extends K8sObjectType<?>> SUPPORTED_TYPES = List.of(
      K8sObjectTypeConstants.CRON_JOB_V1,
      K8sObjectTypeConstants.DAEMON_SET_V1,
      K8sObjectTypeConstants.DEPLOYMENT_V1,
      K8sObjectTypeConstants.JOB_V1,
      K8sObjectTypeConstants.REPLICA_SET_V1,
      K8sObjectTypeConstants.STATEFUL_SET_V1);

  private Cache<V1DaemonSet> daemonSetCache;
  private Cache<V1Deployment> deploymentCache;
  private Cache<V1ReplicaSet> replicaSetCache;
  private RootWorkloadControllerResolver resolver;

  private static V1OwnerReference controllerRef(String apiVersion, String kind, String name) {
    return new V1OwnerReference()
        .apiVersion(apiVersion)
        .kind(kind)
        .name(name)
        .uid(name + "-uid")
        .controller(true);
  }

  private static V1ObjectMeta metadata(String name, List<V1OwnerReference> ownerReferences) {
    return new V1ObjectMeta().name(name).namespace(NAMESPACE).ownerReferences(ownerReferences);
  }

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    this.daemonSetCache = new Cache<>();
    this.deploymentCache = new Cache<>();
    this.replicaSetCache = new Cache<>();

    SharedInformerFactory factory = mock(SharedInformerFactory.class);
    SharedIndexInformer<V1DaemonSet> daemonSetInformer = mock(SharedIndexInformer.class);
    when(daemonSetInformer.getIndexer()).thenReturn(this.daemonSetCache);
    when(factory.getExistingSharedIndexInformer(V1DaemonSet.class)).thenReturn(daemonSetInformer);
    SharedIndexInformer<V1Deployment> deploymentInformer = mock(SharedIndexInformer.class);
    when(deploymentInformer.getIndexer()).thenReturn(this.deploymentCache);
    when(factory.getExistingSharedIndexInformer(V1Deployment.class)).thenReturn(deploymentInformer);
    SharedIndexInformer<V1ReplicaSet> replicaSetInformer = mock(SharedIndexInformer.class);
    when(replicaSetInformer.getIndexer()).thenReturn(this.replicaSetCache);
    when(factory.getExistingSharedIndexInformer(V1ReplicaSet.class)).thenReturn(replicaSetInformer);

    this.resolver = new RootWorkloadControllerResolver(SUPPORTED_TYPES, factory);
  }

  @Test
  @DisplayName("owner가 미지원 kind면 재귀를 멈추고 직전 지원 객체를 root로 반환한다")
  void unsupportedOwnerKind_returnsNearestSupportedController() {
    V1DaemonSet daemonSet = new V1DaemonSet().metadata(metadata("trident-node-linux",
        List.of(controllerRef("trident.netapp.io/v1", "TridentOrchestrator", "trident"))));
    this.daemonSetCache.add(daemonSet);
    V1Pod pod = new V1Pod().metadata(metadata("trident-node-linux-ck9td",
        List.of(controllerRef("apps/v1", "DaemonSet", "trident-node-linux"))));

    Optional<KubernetesObject> root = this.resolver.getRootController(pod);

    assertThat(root).containsSame(daemonSet);
  }

  @Test
  @DisplayName("부모가 캐시에 없으면 재귀를 멈추고 현재 객체를 root로 반환한다")
  void parentNotInCache_returnsCurrentController() {
    // ReplicaSet 의 부모 Deployment 는 지원 타입이고 informer 도 등록됐지만 캐시에 없다
    V1ReplicaSet replicaSet = new V1ReplicaSet().metadata(metadata("test-rs",
        List.of(controllerRef("apps/v1", "Deployment", "test-deploy"))));
    this.replicaSetCache.add(replicaSet);
    V1Pod pod = new V1Pod().metadata(metadata("test-rs-abcde",
        List.of(controllerRef("apps/v1", "ReplicaSet", "test-rs"))));

    Optional<KubernetesObject> root = this.resolver.getRootController(pod);

    assertThat(root).containsSame(replicaSet);
  }

  @Test
  @DisplayName("지원 타입 체인은 최상단까지 거슬러 올라간다")
  void supportedControllerChain_returnsTopmostController() {
    V1Deployment deployment = new V1Deployment().metadata(metadata("test-deploy", null));
    this.deploymentCache.add(deployment);
    V1ReplicaSet replicaSet = new V1ReplicaSet().metadata(metadata("test-rs",
        List.of(controllerRef("apps/v1", "Deployment", "test-deploy"))));
    this.replicaSetCache.add(replicaSet);
    V1Pod pod = new V1Pod().metadata(metadata("test-rs-abcde",
        List.of(controllerRef("apps/v1", "ReplicaSet", "test-rs"))));

    Optional<KubernetesObject> root = this.resolver.getRootController(pod);

    assertThat(root).containsSame(deployment);
  }

  @Test
  @DisplayName("파드의 owner를 찾을 수 없으면 empty를 반환해 네임스페이스 Project 기준 해석에 맡긴다")
  void podOwnerNotResolvable_returnsEmpty() {
    V1Pod unsupportedOwnerPod = new V1Pod().metadata(metadata("cr-owned-pod",
        List.of(controllerRef("trident.netapp.io/v1", "TridentOrchestrator", "trident"))));
    V1Pod missingParentPod = new V1Pod().metadata(metadata("orphan-pod",
        List.of(controllerRef("apps/v1", "DaemonSet", "absent-daemon-set"))));
    V1Pod barePod = new V1Pod().metadata(metadata("bare-pod", null));

    assertThat(this.resolver.getRootController(unsupportedOwnerPod)).isEmpty();
    assertThat(this.resolver.getRootController(missingParentPod)).isEmpty();
    assertThat(this.resolver.getRootController(barePod)).isEmpty();
  }

}
