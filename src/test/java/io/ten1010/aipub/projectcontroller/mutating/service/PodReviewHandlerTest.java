package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.ten1010.aipub.projectcontroller.controller.workload.CompositeWorkloadControllerNodesResolver;
import io.ten1010.aipub.projectcontroller.controller.workload.DaemonSetWorkloadControllerNodesResolver;
import io.ten1010.aipub.projectcontroller.controller.workload.PodNodesResolver;
import io.ten1010.aipub.projectcontroller.controller.workload.RootWorkloadControllerResolver;
import io.ten1010.aipub.projectcontroller.controller.workload.WorkloadControllerNodesResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.DockerConfigJsonResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.IsolationModeValueEnum;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectManagedValueEnum;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.TaintConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHub;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroupDaemonSetPolicy;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroupPolicy;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroupSpec;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ResourceSet;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadExclusionResolver;
import io.ten1010.aipub.projectcontroller.informer.IndexerConstants;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PodReviewHandlerTest {

  private static final String EXCLUSION_LABEL = "test.aipub/excluded";

  private PodReviewHandler handler;
  private PodNodesResolver mockPodNodesResolver;
  private ReconciliationService reconciliationService;
  private ObjectMapper mapper;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    this.mockPodNodesResolver = mock(PodNodesResolver.class);
    SharedInformerFactory factory = mock(SharedInformerFactory.class);
    SharedIndexInformer<V1alpha1Project> projectInformer = mock(SharedIndexInformer.class);
    when(projectInformer.getIndexer()).thenReturn(mock(Indexer.class));
    when(factory.getExistingSharedIndexInformer(V1alpha1Project.class))
        .thenReturn(projectInformer);

    Cache<V1Namespace> namespaceCache = new Cache<>();
    namespaceCache.add(new V1Namespace().metadata(new V1ObjectMeta()
        .name("kubevirt")
        .labels(Map.of(LabelConstants.ALLOWLISTED_KEY, "true"))));

    this.reconciliationService = new ReconciliationService(
        mock(SubjectResolver.class),
        mock(DockerConfigJsonResolver.class),
        List.of(),
        new WorkloadExclusionResolver(List.of(EXCLUSION_LABEL)),
        new NamespaceAllowlistResolver(namespaceCache));

    this.handler = new PodReviewHandler(this.mockPodNodesResolver, factory,
        this.reconciliationService);
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  private V1AdmissionReview createReview(String namespace, Map<String, String> podLabels) {
    V1Pod pod = new V1Pod()
        .metadata(new V1ObjectMeta().name("test-pod").namespace(namespace).labels(podLabels))
        .spec(new V1PodSpec());

    V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
    request.setUid("test-uid");
    request.setOperation("CREATE");
    request.setNamespace(namespace);
    request.setObject(this.mapper.valueToTree(pod));

    V1AdmissionReview review = new V1AdmissionReview();
    review.setApiVersion("admission.k8s.io/v1");
    review.setKind("AdmissionReview");
    review.setRequest(request);
    return review;
  }

  private V1AdmissionReview createReview(V1Pod pod) {
    V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
    request.setUid("test-uid");
    request.setOperation("CREATE");
    request.setNamespace(pod.getMetadata().getNamespace());
    request.setObject(this.mapper.valueToTree(pod));

    V1AdmissionReview review = new V1AdmissionReview();
    review.setApiVersion("admission.k8s.io/v1");
    review.setKind("AdmissionReview");
    review.setRequest(request);
    return review;
  }

  private static String decodePatch(V1AdmissionReview review) {
    return new String(Base64.getDecoder().decode(review.getResponse().getPatch()),
        StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("allowlist 네임스페이스의 파드에는 Exists toleration 쌍 패치를 주입한다")
  void handle_allowlistedNamespace_injectsExistsTolerationPatch() {
    V1AdmissionReview review = createReview("kubevirt", Map.of());

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNotNull();
    String patch = decodePatch(review);
    assertThat(patch).contains("/spec/tolerations");
    assertThat(patch).contains(TaintConstants.PROJECT_MANAGED_KEY);
    assertThat(patch).contains("Exists");
    // allowlist 분기가 선행되어 노드 조회 없이 반환되어야 한다
    verifyNoInteractions(this.mockPodNodesResolver);
  }

  @Test
  @DisplayName("제외 라벨이 붙은 파드라도 allowlist 네임스페이스면 toleration 패치를 주입한다(allowlist 우선)")
  void handle_excludedPodInAllowlistedNamespace_stillInjectsToleration() {
    V1AdmissionReview review = createReview("kubevirt", Map.of(EXCLUSION_LABEL, "true"));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    // 제외 분기가 먼저였다면 무패치 허용이었을 것이다 — 패치 존재가 allowlist 선행의 증거다
    assertThat(review.getResponse().getPatch()).isNotNull();
    assertThat(decodePatch(review)).contains(TaintConstants.PROJECT_MANAGED_KEY);
  }

  @Test
  @DisplayName("allowlist가 아닌 네임스페이스에서 제외 라벨 파드는 무패치 허용한다")
  void handle_excludedPodInOrdinaryNamespace_allowsWithoutPatch() {
    V1AdmissionReview review = createReview("default", Map.of(EXCLUSION_LABEL, "true"));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
    verifyNoInteractions(this.mockPodNodesResolver);
  }


  @SuppressWarnings("unchecked")
  private static <T extends KubernetesObject> void stubEmptyInformer(
      SharedInformerFactory factory, Class<T> objClass) {
    SharedIndexInformer<T> informer = mock(SharedIndexInformer.class);
    when(informer.getIndexer()).thenReturn(mock(Indexer.class));
    when(factory.getExistingSharedIndexInformer(objClass)).thenReturn(informer);
  }

  /**
   * CR 소유 DaemonSet 파드용 하네스. {@link PodNodesResolver}를 mock 하지 않고 운영과 같은 실제
   * 배선(root 해석 → DaemonSet resolver)을 구성해, NodeGroup {@code daemonSetPolicy} 기반
   * toleration 이 실제로 붙는지 본다.
   */
  @SuppressWarnings("unchecked")
  private PodReviewHandler createHandlerWithRealNodesResolver(V1DaemonSet daemonSet,
      V1Node boundNode, V1alpha1NodeGroup nodeGroup) {
    SharedInformerFactory factory = mock(SharedInformerFactory.class);

    Cache<V1DaemonSet> daemonSetCache = new Cache<>();
    daemonSetCache.add(daemonSet);
    SharedIndexInformer<V1DaemonSet> daemonSetInformer = mock(SharedIndexInformer.class);
    when(daemonSetInformer.getIndexer()).thenReturn(daemonSetCache);
    when(factory.getExistingSharedIndexInformer(V1DaemonSet.class)).thenReturn(daemonSetInformer);

    Cache<V1Node> nodeCache = new Cache<>();
    nodeCache.add(boundNode);
    SharedIndexInformer<V1Node> nodeInformer = mock(SharedIndexInformer.class);
    when(nodeInformer.getIndexer()).thenReturn(nodeCache);
    when(factory.getExistingSharedIndexInformer(V1Node.class)).thenReturn(nodeInformer);

    // 프로젝트가 없는 네임스페이스다(getByKey → null). 그래서 project 바인딩 노드가 아니라
    // NodeGroup daemonSetPolicy 만으로 노드가 해석되어야 한다.
    SharedIndexInformer<V1alpha1Project> projectInformer = mock(SharedIndexInformer.class);
    when(projectInformer.getIndexer()).thenReturn(mock(Indexer.class));
    when(factory.getExistingSharedIndexInformer(V1alpha1Project.class))
        .thenReturn(projectInformer);

    Indexer<V1alpha1NodeGroup> nodeGroupIndexer = mock(Indexer.class);
    when(nodeGroupIndexer.byIndex(
        IndexerConstants.ALLOW_ALL_DAEMON_SETS_TO_NODE_GROUPS_INDEXER_NAME,
        IndexerConstants.TRUE_VALUE)).thenReturn(List.of(nodeGroup));
    SharedIndexInformer<V1alpha1NodeGroup> nodeGroupInformer = mock(SharedIndexInformer.class);
    when(nodeGroupInformer.getIndexer()).thenReturn(nodeGroupIndexer);
    when(factory.getExistingSharedIndexInformer(V1alpha1NodeGroup.class))
        .thenReturn(nodeGroupInformer);

    // BoundObjectResolver 가 생성자에서 요구하는 나머지 informer
    stubEmptyInformer(factory, V1alpha1AipubUser.class);
    stubEmptyInformer(factory, V1alpha1ImageHub.class);
    stubEmptyInformer(factory, V1alpha1ResourceSet.class);
    stubEmptyInformer(factory, V1PersistentVolume.class);

    List<? extends K8sObjectType<?>> supportedTypes = List.of(
        K8sObjectTypeConstants.CRON_JOB_V1,
        K8sObjectTypeConstants.DAEMON_SET_V1,
        K8sObjectTypeConstants.DEPLOYMENT_V1,
        K8sObjectTypeConstants.JOB_V1,
        K8sObjectTypeConstants.REPLICA_SET_V1,
        K8sObjectTypeConstants.STATEFUL_SET_V1);
    Map<Class<? extends KubernetesObject>, WorkloadControllerNodesResolver> resolvers = Map.of(
        V1DaemonSet.class, new DaemonSetWorkloadControllerNodesResolver(factory));
    PodNodesResolver podNodesResolver = new PodNodesResolver(
        new RootWorkloadControllerResolver(supportedTypes, factory),
        new CompositeWorkloadControllerNodesResolver(resolvers),
        factory);

    return new PodReviewHandler(podNodesResolver, factory, this.reconciliationService);
  }

  @Test
  @DisplayName("CR 소유 DaemonSet의 파드는 DaemonSet resolver 경로를 타 daemonSetPolicy 노드 toleration을 받는다")
  void handle_crOwnedDaemonSetPod_injectsDaemonSetPolicyNodeToleration() {
    String nodeName = "vnode1.pnode7";
    V1DaemonSet daemonSet = new V1DaemonSet().metadata(new V1ObjectMeta()
        .name("trident-node-linux")
        .namespace("trident")
        .ownerReferences(List.of(new V1OwnerReference()
            .apiVersion("trident.netapp.io/v1").kind("TridentOrchestrator")
            .name("trident").uid("cr-uid").controller(true))));
    V1Node boundNode = new V1Node().metadata(new V1ObjectMeta()
        .name(nodeName)
        .labels(Map.of(
            LabelConstants.PROJECT_MANAGED_KEY, ProjectManagedValueEnum.TRUE.getStr(),
            LabelConstants.ISOLATION_MODE_KEY, IsolationModeValueEnum.LENIENT.getStr())));
    V1alpha1NodeGroup nodeGroup = new V1alpha1NodeGroup();
    nodeGroup.setMetadata(new V1ObjectMeta().name("aipub-node-group"));
    V1alpha1NodeGroupDaemonSetPolicy daemonSetPolicy = new V1alpha1NodeGroupDaemonSetPolicy();
    daemonSetPolicy.setAllowAllDaemonSets(true);
    V1alpha1NodeGroupPolicy policy = new V1alpha1NodeGroupPolicy();
    policy.setDaemonSet(daemonSetPolicy);
    V1alpha1NodeGroupSpec spec = new V1alpha1NodeGroupSpec();
    spec.setPolicy(policy);
    spec.setNodes(List.of(nodeName));
    nodeGroup.setSpec(spec);

    PodReviewHandler handlerWithRealResolver = createHandlerWithRealNodesResolver(daemonSet,
        boundNode, nodeGroup);
    V1Pod pod = new V1Pod()
        .metadata(new V1ObjectMeta()
            .name("trident-node-linux-ck9td")
            .namespace("trident")
            .ownerReferences(List.of(new V1OwnerReference()
                .apiVersion("apps/v1").kind("DaemonSet").name("trident-node-linux")
                .uid("ds-uid").controller(true))))
        .spec(new V1PodSpec());
    V1AdmissionReview review = createReview(pod);

    handlerWithRealResolver.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNotNull();
    String patch = decodePatch(review);
    // 프로젝트 미바인딩 네임스페이스라 root 해석이 끊기면 toleration 이 0개다. 노드 이름이 값으로
    // 들어간 project-managed toleration 쌍이 DaemonSet resolver 경로를 탔다는 증거다.
    assertThat(patch).contains("/spec/tolerations");
    assertThat(patch).contains(TaintConstants.PROJECT_MANAGED_KEY);
    assertThat(patch).contains(nodeName);
    assertThat(patch).contains(TaintConstants.NO_SCHEDULE_EFFECT);
    assertThat(patch).contains(TaintConstants.NO_EXECUTE_EFFECT);
  }

}
