package io.ten1010.aipub.projectcontroller.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.DefaultController;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.ten1010.aipub.projectcontroller.controller.workload.CronJobInformerRegistrar;
import io.ten1010.aipub.projectcontroller.controller.workload.CronJobWorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.DaemonSetInformerRegistrar;
import io.ten1010.aipub.projectcontroller.controller.workload.DaemonSetWorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.DeploymentInformerRegistrar;
import io.ten1010.aipub.projectcontroller.controller.workload.DeploymentWorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.JobInformerRegistrar;
import io.ten1010.aipub.projectcontroller.controller.workload.JobWorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.ReplicaSetInformerRegistrar;
import io.ten1010.aipub.projectcontroller.controller.workload.ReplicaSetWorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.StatefulSetInformerRegistrar;
import io.ten1010.aipub.projectcontroller.controller.workload.StatefulSetWorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.WorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.WorkloadControllerReconciler;
import io.ten1010.aipub.projectcontroller.domain.k8s.DockerConfigJsonResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeKey;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadExclusionResolver;
import io.ten1010.aipub.projectcontroller.informer.InformerRegistrar;
import io.ten1010.aipub.projectcontroller.informer.SharedInformerFactoryProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

/**
 * 워크로드 컨트롤러 배선 불변식 테스트. 통짜 {@code @SpringBootTest}는 쓰지 않는다 —
 * {@link ControllerConfiguration#controllerManager} 빈이 메서드 안에서 컨트롤러 매니저를 실행하므로
 * 컨텍스트 로딩만으로 인포머 watch 가 API 서버에 붙는다.
 *
 * <p>대신 운영과 같은 부품으로 배선만 재현한다. {@link SharedInformerFactory}는 mock 이 아니라
 * 운영 코드인 {@link SharedInformerFactoryProvider}가 만든 실제 인스턴스다. 인포머 등록
 * ({@code sharedIndexInformerFor})은 list/watch 콜을 만들지 않는다 — 실제 요청은
 * {@code startAllRegisteredInformers()}에서 시작되고 이 테스트는 그것을 호출하지 않는다. 그래서
 * 연결되지 않는 더미 {@link ApiClient}로도 등록·컨트롤러 빌드까지 검증할 수 있다.
 *
 * <p>픽스처를 {@code @BeforeAll}로 한 번만 만드는 이유: 업스트림
 * {@code DefaultControllerBuilder} 생성자가 {@code DefaultRateLimitingQueue}를 만들고 그
 * 생성자가 대기 루프를 executor 에 submit 한다. 즉 워크로드 팩토리를 생성하는 것만으로 스레드가
 * 하나 뜬다(mock 인포머 팩토리를 써도 마찬가지). 그래서 팩토리 6개와 그 컨트롤러를 클래스당 한 번만
 * 만들고 {@code @AfterAll}에서 {@code shutdown()} 한다(정리 범위는 tearDown 주석 참고). 테스트
 * 메서드는 이 픽스처를 읽기만 한다.
 */
class ControllerConfigurationWiringTest {

  /**
   * 지원 워크로드 타입 6종. 이 목록이 하나라도 빠지면 그 kind 를 owner 로 갖는 자식 워크로드가
   * 미지원으로 판정돼 Deployment→ReplicaSet 중복 reconcile 이 되살아난다.
   */
  private static final Set<K8sObjectTypeKey> EXPECTED_TYPE_KEYS = Set.of(
      K8sObjectTypeConstants.CRON_JOB_V1.typeKey(),
      K8sObjectTypeConstants.DAEMON_SET_V1.typeKey(),
      K8sObjectTypeConstants.DEPLOYMENT_V1.typeKey(),
      K8sObjectTypeConstants.JOB_V1.typeKey(),
      K8sObjectTypeConstants.REPLICA_SET_V1.typeKey(),
      K8sObjectTypeConstants.STATEFUL_SET_V1.typeKey());

  private static final int EXPECTED_WORKLOAD_FACTORY_COUNT = 6;

  private static SharedInformerFactory sharedInformerFactory;
  private static List<WorkloadControllerFactory<?>> workloadControllerFactories;
  /** 팩토리별로 한 번만 빌드한다 — 같은 팩토리를 두 번 빌드하면 watch 가 중복 등록된다. */
  private static final Map<WorkloadControllerFactory<?>, Controller> BUILT_CONTROLLERS =
      new LinkedHashMap<>();

  @BeforeAll
  static void setUp() {
    // 연결하지 않는 더미 ApiClient. 이 테스트는 list/watch 를 시작하지 않는다.
    // read timeout 0 은 SharedInformerFactory 생성자의 요구사항이다(watch 는 무기한 대기).
    // 운영에서는 ClientBuilder 가 같은 값을 세팅한다(DomainConfiguration.apiClient).
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath("http://localhost:1");
    apiClient.setReadTimeout(0);
    K8sApiProvider k8sApiProvider = new K8sApiProvider(apiClient);

    List<InformerRegistrar> registrars = List.of(
        new CronJobInformerRegistrar(k8sApiProvider),
        new DaemonSetInformerRegistrar(k8sApiProvider),
        new DeploymentInformerRegistrar(k8sApiProvider),
        new JobInformerRegistrar(k8sApiProvider),
        new ReplicaSetInformerRegistrar(k8sApiProvider),
        new StatefulSetInformerRegistrar(k8sApiProvider));
    sharedInformerFactory = new SharedInformerFactoryProvider(k8sApiProvider, registrars)
        .createSharedInformerFactory();

    ReconciliationService reconciliationService = new ReconciliationService(
        mock(SubjectResolver.class),
        mock(DockerConfigJsonResolver.class),
        List.of(),
        new WorkloadExclusionResolver(List.of()),
        new NamespaceAllowlistResolver(sharedInformerFactory
            .getExistingSharedIndexInformer(V1Namespace.class)
            .getIndexer()));

    workloadControllerFactories = List.of(
        new CronJobWorkloadControllerFactory(sharedInformerFactory, reconciliationService,
            k8sApiProvider),
        new DaemonSetWorkloadControllerFactory(sharedInformerFactory, reconciliationService,
            k8sApiProvider),
        new DeploymentWorkloadControllerFactory(sharedInformerFactory, reconciliationService,
            k8sApiProvider),
        new JobWorkloadControllerFactory(sharedInformerFactory, reconciliationService,
            k8sApiProvider),
        new ReplicaSetWorkloadControllerFactory(sharedInformerFactory, reconciliationService,
            k8sApiProvider),
        new StatefulSetWorkloadControllerFactory(sharedInformerFactory, reconciliationService,
            k8sApiProvider));

    // build() 는 watch 대상 타입의 informer 가 등록돼 있지 않으면 IllegalStateException 을 던진다.
    // 즉 이 루프 자체가 supportedTypes 전달 경로와 informer 등록 상태를 함께 검증한다.
    List<? extends K8sObjectType<?>> supportedTypes =
        ControllerConfiguration.resolveSupportedWorkloadTypes(workloadControllerFactories);
    for (WorkloadControllerFactory<?> factory : workloadControllerFactories) {
      BUILT_CONTROLLERS.put(factory, factory.createController(supportedTypes));
    }
  }

  @AfterAll
  static void tearDown() {
    // 워크큐의 대기 루프를 멈춘다(테스트는 Controller.run() 을 호출하지 않는다). 업스트림
    // DefaultDelayingQueue 는 자기 executor 를 소유하고 shutDown() 이 그것을 종료하지 않으므로
    // 스레드 자체는 테스트 JVM 이 끝날 때까지 유휴 상태로 남는다(heartBeatInterval=10s 뒤 WAITING).
    // 팩토리에 executor 주입 지점이 없어 이 이상은 메인 코드 변경 없이 불가능하다.
    BUILT_CONTROLLERS.values().forEach(Controller::shutdown);
    BUILT_CONTROLLERS.clear();
  }

  @Test
  @DisplayName("resolveSupportedWorkloadTypes는 워크로드 팩토리에서 지원 6종을 정확히 모은다")
  void resolveSupportedWorkloadTypes_collectsExactlySixSupportedTypes() {
    List<? extends K8sObjectType<?>> supportedTypes =
        ControllerConfiguration.resolveSupportedWorkloadTypes(workloadControllerFactories);

    assertThat(supportedTypes)
        .extracting(K8sObjectType::typeKey)
        .containsExactlyInAnyOrderElementsOf(EXPECTED_TYPE_KEYS);
  }

  @Test
  @DisplayName("ControllerConfiguration의 워크로드 팩토리 빈은 6개다 — 추가되면 이 테스트가 알린다")
  void workloadControllerFactoryBeans_areExactlySix() {
    List<String> beanMethods = Arrays.stream(ControllerConfiguration.class.getDeclaredMethods())
        .filter(m -> m.isAnnotationPresent(Bean.class))
        .filter(m -> WorkloadControllerFactory.class.isAssignableFrom(m.getReturnType()))
        .map(Method::getName)
        .sorted()
        .toList();

    // 7번째 팩토리가 추가되면 EXPECTED_TYPE_KEYS 와 setUp 의 팩토리 목록도 함께 갱신해야 한다
    assertThat(beanMethods).hasSize(EXPECTED_WORKLOAD_FACTORY_COUNT);
    assertThat(workloadControllerFactories).hasSize(EXPECTED_WORKLOAD_FACTORY_COUNT);
  }

  @Test
  @DisplayName("워크로드 팩토리 6개는 supportedTypes로 컨트롤러를 빌드한다")
  void eachWorkloadControllerFactory_buildsControllerWithSupportedTypes() {
    assertThat(BUILT_CONTROLLERS).hasSize(EXPECTED_WORKLOAD_FACTORY_COUNT);
    assertThat(BUILT_CONTROLLERS.values()).doesNotContainNull();
    // 컨트롤러 빌드만으로는 list/watch 가 시작되지 않는다(run() 미호출)
    assertThat(sharedInformerFactory.getExistingSharedIndexInformer(V1DaemonSet.class)
        .hasSynced()).isFalse();
  }

  @Test
  @DisplayName("워크로드 팩토리의 무인자 createController()는 UnsupportedOperationException을 던진다")
  void noArgCreateController_throwsUnsupportedOperationException() {
    WorkloadControllerFactory<?> factory = workloadControllerFactories.getFirst();

    assertThatThrownBy(factory::createController)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("createController(List)");
  }

  @Test
  @DisplayName("미지원 owner 시 reconcile 정책은 DaemonSet 팩토리 하나만 켜져 있다")
  void unsupportedOwnerReconcilePolicy_isEnabledOnlyForDaemonSet() throws Exception {
    Method hook = WorkloadControllerFactory.class.getDeclaredMethod(
        "reconcilesWhenOwnedByUnsupportedType");
    hook.setAccessible(true);

    Map<String, Boolean> policyByFactory = new LinkedHashMap<>();
    for (WorkloadControllerFactory<?> factory : workloadControllerFactories) {
      policyByFactory.put(factory.getClass().getSimpleName(), (Boolean) hook.invoke(factory));
    }

    // 다른 팩토리에서 켜면 프로젝트 네임스페이스의 AIPub 자체 CR 소유 워크로드(Workspace 소유
    // StatefulSet 등)의 spec.template 이 새로 쓰여 실행 중 파드가 롤링 재시작되고, 그 워크로드를
    // 관리하는 aipub-backend 와 쓰기 경합이 생긴다. 이 표가 그 사고의 안전장치다.
    assertThat(policyByFactory).containsExactlyInAnyOrderEntriesOf(Map.of(
        "CronJobWorkloadControllerFactory", false,
        "DaemonSetWorkloadControllerFactory", true,
        "DeploymentWorkloadControllerFactory", false,
        "JobWorkloadControllerFactory", false,
        "ReplicaSetWorkloadControllerFactory", false,
        "StatefulSetWorkloadControllerFactory", false));

    // 클래스 이름이 아니라 타입으로도 고정한다 — 오버라이드가 다른 팩토리로 옮겨가도 잡힌다
    List<K8sObjectTypeKey> enabledTypeKeys = new ArrayList<>();
    for (WorkloadControllerFactory<?> factory : workloadControllerFactories) {
      if ((Boolean) hook.invoke(factory)) {
        enabledTypeKeys.add(factory.getObjectType().typeKey());
      }
    }
    assertThat(enabledTypeKeys)
        .containsExactly(K8sObjectTypeConstants.DAEMON_SET_V1.typeKey());
  }

  /**
   * 위 테스트는 팩토리가 어떤 값을 <i>반환</i>하는지만 본다. 그 값이 실제로 리컨실러까지
   * <i>전달</i>되는지는 별개이며, 전달이 끊기면(예: createReconciler 가 상수를 넘기면) 다른 어떤
   * 테스트도 잡지 못한다 — 운영에서는 CR 소유 DaemonSet 이 조용히 다시 미주입 상태가 된다.
   * 그래서 실제 팩토리가 빌드한 컨트롤러에서 리컨실러를 꺼내 그 값을 확인한다.
   */
  @Test
  @DisplayName("팩토리의 미지원 owner 정책 값이 실제로 빌드된 리컨실러까지 전달된다")
  void unsupportedOwnerReconcilePolicy_reachesBuiltReconciler() throws Exception {
    Method hook = WorkloadControllerFactory.class.getDeclaredMethod(
        "reconcilesWhenOwnedByUnsupportedType");
    hook.setAccessible(true);
    Field flagField = WorkloadControllerReconciler.class.getDeclaredField(
        "reconcilesWhenOwnedByUnsupportedType");
    flagField.setAccessible(true);

    Map<K8sObjectTypeKey, Boolean> flagOnReconciler = new LinkedHashMap<>();
    for (Map.Entry<WorkloadControllerFactory<?>, Controller> entry : BUILT_CONTROLLERS.entrySet()) {
      WorkloadControllerFactory<?> factory = entry.getKey();
      // DefaultControllerBuilder.build() 는 withReconciler 로 받은 인스턴스를 감싸지 않고 그대로
      // DefaultController 에 넘긴다. 따라서 이것이 운영에서 실제로 도는 리컨실러다.
      WorkloadControllerReconciler reconciler = (WorkloadControllerReconciler)
          ((DefaultController) entry.getValue()).getReconciler();
      boolean onReconciler = (Boolean) flagField.get(reconciler);

      assertThat(onReconciler)
          .as("%s: 팩토리 값이 리컨실러까지 전달되지 않았다", factory.getClass().getSimpleName())
          .isEqualTo((Boolean) hook.invoke(factory));
      flagOnReconciler.put(factory.getObjectType().typeKey(), onReconciler);
    }

    assertThat(flagOnReconciler)
        .containsEntry(K8sObjectTypeConstants.DAEMON_SET_V1.typeKey(), true);
    assertThat(flagOnReconciler.entrySet().stream().filter(Map.Entry::getValue).map(
        Map.Entry::getKey)).containsExactly(K8sObjectTypeConstants.DAEMON_SET_V1.typeKey());
  }

}
