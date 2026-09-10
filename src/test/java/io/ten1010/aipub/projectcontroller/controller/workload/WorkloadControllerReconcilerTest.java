package io.ten1010.aipub.projectcontroller.controller.workload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.aipub.projectcontroller.domain.k8s.DockerConfigJsonResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.TaintConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadExclusionResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkloadControllerReconcilerTest {

  private static final String EXCLUSION_LABEL = "test.aipub/excluded";

  /** 운영 배선과 동일한 지원 워크로드 타입 6종 (ControllerConfiguration 이 팩토리에서 수집하는 값) */
  private static final List<? extends K8sObjectType<?>> SUPPORTED_TYPES = List.of(
      K8sObjectTypeConstants.CRON_JOB_V1,
      K8sObjectTypeConstants.DAEMON_SET_V1,
      K8sObjectTypeConstants.DEPLOYMENT_V1,
      K8sObjectTypeConstants.JOB_V1,
      K8sObjectTypeConstants.REPLICA_SET_V1,
      K8sObjectTypeConstants.STATEFUL_SET_V1);

  private Cache<V1Deployment> deploymentCache;
  private SharedInformerFactory factory;
  private ControllerObjectReconciler mockObjectReconciler;
  private WorkloadControllerNodesResolver mockNodesResolver;
  private WorkloadControllerReconciler reconciler;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    this.deploymentCache = new Cache<>();
    this.mockObjectReconciler = mock(ControllerObjectReconciler.class);
    this.mockNodesResolver = mock(WorkloadControllerNodesResolver.class);

    this.factory = mock(SharedInformerFactory.class);
    SharedIndexInformer<V1Deployment> deploymentInformer = mock(SharedIndexInformer.class);
    when(deploymentInformer.getIndexer()).thenReturn(this.deploymentCache);
    when(this.factory.getExistingSharedIndexInformer(V1Deployment.class))
        .thenReturn(deploymentInformer);
    SharedIndexInformer<V1alpha1Project> projectInformer = mock(SharedIndexInformer.class);
    when(projectInformer.getIndexer()).thenReturn(mock(Indexer.class));
    when(this.factory.getExistingSharedIndexInformer(V1alpha1Project.class))
        .thenReturn(projectInformer);

    Cache<V1Namespace> namespaceCache = new Cache<>();
    namespaceCache.add(new V1Namespace().metadata(new V1ObjectMeta()
        .name("kubevirt")
        .labels(Map.of(LabelConstants.ALLOWLISTED_KEY, "true"))));

    ReconciliationService reconciliationService = new ReconciliationService(
        mock(SubjectResolver.class),
        mock(DockerConfigJsonResolver.class),
        List.of(),
        new WorkloadExclusionResolver(List.of(EXCLUSION_LABEL)),
        new NamespaceAllowlistResolver(namespaceCache));

    this.reconciler = new WorkloadControllerReconciler(
        this.factory,
        reconciliationService,
        SUPPORTED_TYPES,
        false,
        V1Deployment.class,
        controller -> ((V1Deployment) controller).getSpec().getTemplate(),
        this.mockObjectReconciler,
        this.mockNodesResolver);
  }

  private V1Deployment deployment(String namespace, Map<String, String> labels,
      List<V1OwnerReference> ownerReferences) {
    return new V1Deployment()
        .metadata(new V1ObjectMeta()
            .name("test-deployment")
            .namespace(namespace)
            .labels(labels)
            .ownerReferences(ownerReferences))
        .spec(new V1DeploymentSpec().template(new V1PodTemplateSpec().spec(new V1PodSpec())));
  }

  @Test
  @DisplayName("allowlist 네임스페이스면 controller ownerRef 검사 이후 Exists toleration 쌍으로 reconcile한다")
  void allowlistedNamespace_reconcilesWithExistsTolerations() throws ApiException {
    this.deploymentCache.add(deployment("kubevirt", Map.of(), null));
    when(this.mockObjectReconciler.reconcileController(any(), anyList(), anyList(), anyList()))
        .thenReturn(new Result(false));

    this.reconciler.reconcileInternal(new Request("kubevirt", "test-deployment"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<V1Toleration>> tolerationsCaptor = ArgumentCaptor.forClass(List.class);
    verify(this.mockObjectReconciler).reconcileController(any(), tolerationsCaptor.capture(),
        anyList(), anyList());
    assertThat(tolerationsCaptor.getValue())
        .extracting(V1Toleration::getKey, V1Toleration::getOperator, V1Toleration::getEffect)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(TaintConstants.PROJECT_MANAGED_KEY, "Exists",
                TaintConstants.NO_SCHEDULE_EFFECT),
            org.assertj.core.groups.Tuple.tuple(TaintConstants.PROJECT_MANAGED_KEY, "Exists",
                TaintConstants.NO_EXECUTE_EFFECT));
    // allowlist 분기가 선행되어 노드 조회 없이 처리되어야 한다
    verifyNoInteractions(this.mockNodesResolver);
  }

  @Test
  @DisplayName("제외 라벨이 붙어 있어도 allowlist 네임스페이스면 toleration 주입 경로를 탄다(allowlist 우선)")
  void excludedWorkloadInAllowlistedNamespace_stillReconciled() throws ApiException {
    this.deploymentCache.add(deployment("kubevirt", Map.of(EXCLUSION_LABEL, "true"), null));
    when(this.mockObjectReconciler.reconcileController(any(), anyList(), anyList(), anyList()))
        .thenReturn(new Result(false));

    this.reconciler.reconcileInternal(new Request("kubevirt", "test-deployment"));

    // 제외 분기가 먼저였다면 reconcileController가 호출되지 않았을 것이다
    verify(this.mockObjectReconciler).reconcileController(any(), anyList(), anyList(), anyList());
  }

  @Test
  @DisplayName("allowlist가 아닌 네임스페이스에서 제외 라벨 워크로드는 reconcile하지 않는다")
  void excludedWorkloadInOrdinaryNamespace_skipped() throws ApiException {
    this.deploymentCache.add(deployment("default", Map.of(EXCLUSION_LABEL, "true"), null));

    Result result = this.reconciler.reconcileInternal(new Request("default", "test-deployment"));

    assertThat(result.isRequeue()).isFalse();
    verifyNoInteractions(this.mockObjectReconciler, this.mockNodesResolver);
  }

  @Test
  @DisplayName("controller ownerRef가 있으면 allowlist 네임스페이스라도 reconcile하지 않는다")
  void controllerOwnedWorkloadInAllowlistedNamespace_skipped() throws ApiException {
    V1OwnerReference controllerRef = new V1OwnerReference()
        .apiVersion("apps/v1").kind("Deployment").name("owner").uid("uid").controller(true);
    this.deploymentCache.add(deployment("kubevirt", Map.of(), List.of(controllerRef)));

    Result result = this.reconciler.reconcileInternal(new Request("kubevirt", "test-deployment"));

    assertThat(result.isRequeue()).isFalse();
    verifyNoInteractions(this.mockObjectReconciler, this.mockNodesResolver);
  }

  @Test
  @DisplayName("미지원 owner여도 정책이 꺼진 타입(Deployment)이면 reconcile하지 않는다")
  void unsupportedControllerOwnedWorkload_skippedWhenPolicyDisabled() throws ApiException {
    // reconcilesWhenOwnedByUnsupportedType == false 인 타입이다. 이 워크로드의 spec.template 을
    // 새로 쓰면 실행 중 파드가 재시작되고 소유 CR 컨트롤러와 쓰기 경합이 생기므로 건드리지 않는다.
    V1OwnerReference controllerRef = new V1OwnerReference()
        .apiVersion("trident.netapp.io/v1").kind("TridentOrchestrator")
        .name("owner").uid("uid").controller(true);
    this.deploymentCache.add(deployment("default", Map.of(), List.of(controllerRef)));

    Result result = this.reconciler.reconcileInternal(new Request("default", "test-deployment"));

    assertThat(result.isRequeue()).isFalse();
    verifyNoInteractions(this.mockObjectReconciler, this.mockNodesResolver);
  }

}
