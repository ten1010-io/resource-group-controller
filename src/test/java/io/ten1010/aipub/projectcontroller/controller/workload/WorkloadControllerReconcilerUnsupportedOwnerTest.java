package io.ten1010.aipub.projectcontroller.controller.workload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1DaemonSetSpec;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.ten1010.aipub.projectcontroller.domain.k8s.DockerConfigJsonResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadExclusionResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 미지원 owner 시 reconcile 정책이 켜진 경로({@code reconcilesWhenOwnedByUnsupportedType == true},
 * 운영에서는 DaemonSet 팩토리만) 를 검증한다. 정책이 꺼진 경로는
 * {@link WorkloadControllerReconcilerTest} 에 있다.
 */
class WorkloadControllerReconcilerUnsupportedOwnerTest {

  /** 운영 배선과 동일한 지원 워크로드 타입 6종 (ControllerConfiguration 이 팩토리에서 수집하는 값) */
  private static final List<? extends K8sObjectType<?>> SUPPORTED_TYPES = List.of(
      K8sObjectTypeConstants.CRON_JOB_V1,
      K8sObjectTypeConstants.DAEMON_SET_V1,
      K8sObjectTypeConstants.DEPLOYMENT_V1,
      K8sObjectTypeConstants.JOB_V1,
      K8sObjectTypeConstants.REPLICA_SET_V1,
      K8sObjectTypeConstants.STATEFUL_SET_V1);

  private Cache<V1DaemonSet> daemonSetCache;
  private SharedInformerFactory factory;
  private ControllerObjectReconciler mockObjectReconciler;
  private WorkloadControllerNodesResolver mockNodesResolver;
  private WorkloadControllerReconciler reconciler;

  private static V1OwnerReference controllerRef(String apiVersion, String kind) {
    return new V1OwnerReference()
        .apiVersion(apiVersion).kind(kind).name("owner").uid("uid").controller(true);
  }

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    this.daemonSetCache = new Cache<>();
    this.mockObjectReconciler = mock(ControllerObjectReconciler.class);
    this.mockNodesResolver = mock(WorkloadControllerNodesResolver.class);

    this.factory = mock(SharedInformerFactory.class);
    SharedIndexInformer<V1DaemonSet> daemonSetInformer = mock(SharedIndexInformer.class);
    when(daemonSetInformer.getIndexer()).thenReturn(this.daemonSetCache);
    when(this.factory.getExistingSharedIndexInformer(V1DaemonSet.class))
        .thenReturn(daemonSetInformer);
    SharedIndexInformer<V1alpha1Project> projectInformer = mock(SharedIndexInformer.class);
    when(projectInformer.getIndexer()).thenReturn(mock(Indexer.class));
    when(this.factory.getExistingSharedIndexInformer(V1alpha1Project.class))
        .thenReturn(projectInformer);

    ReconciliationService reconciliationService = new ReconciliationService(
        mock(SubjectResolver.class),
        mock(DockerConfigJsonResolver.class),
        List.of(),
        new WorkloadExclusionResolver(List.of()),
        new NamespaceAllowlistResolver(new Cache<V1Namespace>()));

    this.reconciler = new WorkloadControllerReconciler(
        this.factory,
        reconciliationService,
        SUPPORTED_TYPES,
        true,
        V1DaemonSet.class,
        controller -> ((V1DaemonSet) controller).getSpec().getTemplate(),
        this.mockObjectReconciler,
        this.mockNodesResolver);
  }

  private V1DaemonSet daemonSet(List<V1OwnerReference> ownerReferences) {
    return new V1DaemonSet()
        .metadata(new V1ObjectMeta()
            .name("trident-node-linux")
            .namespace("trident")
            .ownerReferences(ownerReferences))
        .spec(new V1DaemonSetSpec().template(new V1PodTemplateSpec().spec(new V1PodSpec())));
  }

  @Test
  @DisplayName("미지원 owner(CR)면 스킵하지 않고 자기 자신을 root로 reconcile한다")
  void unsupportedOwnerKind_reconciledAsItsOwnRoot() throws ApiException {
    V1DaemonSet daemonSet = daemonSet(
        List.of(controllerRef("trident.netapp.io/v1", "TridentOrchestrator")));
    this.daemonSetCache.add(daemonSet);
    when(this.mockObjectReconciler.reconcileController(any(), anyList(), anyList(), anyList()))
        .thenReturn(new Result(false));

    Result result = this.reconciler.reconcileInternal(
        new Request("trident", "trident-node-linux"));

    assertThat(result.isRequeue()).isFalse();
    // 미지원 owner 는 root 가 아니므로 워크로드 자신을 root 로 보아 노드를 해석해야 한다
    verify(this.mockNodesResolver).getNodes(daemonSet);
    verify(this.mockObjectReconciler)
        .reconcileController(eq(daemonSet), anyList(), anyList(), anyList());
  }

  @Test
  @DisplayName("owner kind가 지원 타입이어도 informer가 등록되지 않았으면 미지원과 동일하게 처리한다")
  void supportedOwnerKindWithoutInformer_reconciledAsItsOwnRoot() throws ApiException {
    // apps/v1 StatefulSet 은 지원 타입이지만 informer 가 등록되지 않은 상태를 명시적으로 만든다
    when(this.factory.getExistingSharedIndexInformer(V1StatefulSet.class)).thenReturn(null);
    V1DaemonSet daemonSet = daemonSet(List.of(controllerRef("apps/v1", "StatefulSet")));
    this.daemonSetCache.add(daemonSet);
    when(this.mockObjectReconciler.reconcileController(any(), anyList(), anyList(), anyList()))
        .thenReturn(new Result(false));

    Result result = this.reconciler.reconcileInternal(
        new Request("trident", "trident-node-linux"));

    assertThat(result.isRequeue()).isFalse();
    verify(this.mockNodesResolver).getNodes(daemonSet);
    verify(this.mockObjectReconciler)
        .reconcileController(eq(daemonSet), anyList(), anyList(), anyList());
  }

  @Test
  @DisplayName("정책이 켜져 있어도 owner가 지원 타입(informer 등록)이면 reconcile하지 않는다")
  void supportedOwnerKindWithInformer_stillSkipped() throws ApiException {
    // root 워크로드가 따로 reconcile 되므로 자식은 건너뛴다 — 정책과 무관한 기존 동작이다
    this.daemonSetCache.add(daemonSet(List.of(controllerRef("apps/v1", "DaemonSet"))));

    Result result = this.reconciler.reconcileInternal(
        new Request("trident", "trident-node-linux"));

    assertThat(result.isRequeue()).isFalse();
    verifyNoInteractions(this.mockObjectReconciler, this.mockNodesResolver);
  }

  @Test
  @DisplayName("controller ownerRef가 없으면 정책과 무관하게 reconcile한다")
  void noControllerOwnerReference_reconciled() throws ApiException {
    V1DaemonSet daemonSet = daemonSet(null);
    this.daemonSetCache.add(daemonSet);
    when(this.mockObjectReconciler.reconcileController(any(), anyList(), anyList(), anyList()))
        .thenReturn(new Result(false));

    Result result = this.reconciler.reconcileInternal(
        new Request("trident", "trident-node-linux"));

    assertThat(result.isRequeue()).isFalse();
    verify(this.mockObjectReconciler)
        .reconcileController(eq(daemonSet), anyList(), anyList(), anyList());
  }

}
