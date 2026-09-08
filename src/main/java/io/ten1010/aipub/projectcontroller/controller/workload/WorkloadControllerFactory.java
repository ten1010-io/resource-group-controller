package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.builder.DefaultControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.ten1010.aipub.projectcontroller.controller.ControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.DefaultControllerWatch;
import io.ten1010.aipub.projectcontroller.controller.watch.OnUpdateFilterFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.RequestBuilderFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import java.util.List;
import java.util.function.Function;

public abstract class WorkloadControllerFactory<T extends KubernetesObject> implements
    ControllerFactory {

  protected final DefaultControllerBuilder builder;
  protected final SharedInformerFactory sharedInformerFactory;
  protected final ReconciliationService reconciliationService;
  private final OnUpdateFilterFactory onUpdateFilterFactory;
  private final RequestBuilderFactory requestBuilderFactory;

  public WorkloadControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService) {
    this.builder = ControllerBuilder.defaultBuilder(sharedInformerFactory);
    this.sharedInformerFactory = sharedInformerFactory;
    this.reconciliationService = reconciliationService;
    this.onUpdateFilterFactory = new OnUpdateFilterFactory();
    this.requestBuilderFactory = new RequestBuilderFactory(sharedInformerFactory);
  }

  /**
   * 워크로드 컨트롤러는 owner kind 대조에 쓸 지원 타입 목록이 필요하므로 인자 없는 생성은
   * 지원하지 않는다. {@link #createController(List)}를 쓸 것.
   *
   * <p>지원 타입 목록은 워크로드 팩토리 빈들 자신에서 수집되므로(각 팩토리의
   * {@link #getObjectType()}) 팩토리 생성자로는 주입할 수 없다. 팩토리 전체 목록이 모인 지점
   * (ControllerConfiguration의 controllerManager 빈)에서 인자로 넘긴다.
   */
  @Override
  public Controller createController() {
    throw new UnsupportedOperationException(
        "WorkloadControllerFactory requires supported types; use createController(List) instead");
  }

  /**
   * @param supportedTypes reconcile 대상 워크로드 타입 전체. 워크로드의 controller
   *     ownerReference가 이 목록에 있는 타입을 가리키면 root 워크로드가 따로 reconcile되므로
   *     건너뛴다. {@link RootWorkloadControllerResolver}에 넘기는 것과 같은 값이어야 한다
   */
  public Controller createController(List<? extends K8sObjectType<?>> supportedTypes) {
    configureControllerName();
    configureReadyFunc();
    configureWatch();
    configureNamespaceAllowlistWatch();
    this.builder.withWorkerCount(1);
    this.builder.withReconciler(createReconciler(supportedTypes));

    return this.builder.build();
  }

  public abstract K8sObjectType<T> getObjectType();

  /**
   * controller ownerReference 가 지원 워크로드 타입이 아닐 때(임의 CR 이 소유한 워크로드) 이 타입의
   * 워크로드를 자기 자신을 root 로 보고 reconcile 할지. 기본값은 {@code false} — 스킵한다.
   *
   * <p><b>DaemonSet 만 {@code true} 다.</b> NodeGroup 의 {@code daemonSetPolicy} 는 DaemonSet
   * 전용 예외 정책이라(AIP-1998), 프로젝트에 바인딩되지 않은 네임스페이스의 DaemonSet 도
   * project-managed 노드에 올라가야 한다. 그래서 CR 소유 DaemonSet 이 아무에게도 reconcile 되지
   * 않는 상태가 실제 결함이 된다(trident-node-linux: toleration 0개 → 노드 재생성 시 Pending).
   *
   * <p>Deployment·StatefulSet 등으로 넓히지 말 것. 프로젝트 네임스페이스의 AIPub 자체 CR 소유
   * 워크로드(Workspace 소유 StatefulSet 등)가 새 쓰기 대상이 되어 {@code spec.template} 이 바뀌고
   * <b>실행 중인 워크로드가 롤링 재시작</b>된다. 게다가 그 워크로드를 관리하는 aipub-backend 가
   * 같은 필드를 되돌리면 update 루프가 된다. 파드 레벨 결함(strict 노드에서의 삭제, 웹훅의
   * toleration 치환)은 이 플래그와 무관하게 모든 kind 에 대해 해소돼 있다
   * ({@link RootWorkloadControllerResolver} · {@link PodReconciler} · PodReviewHandler).
   */
  protected boolean reconcilesWhenOwnedByUnsupportedType() {
    return false;
  }

  public abstract WorkloadControllerNodesResolver getWorkloadNodesResolver();

  protected abstract void configureControllerName();

  protected abstract void configureReadyFunc();

  protected abstract void configureWatch();

  protected abstract Function<KubernetesObject, V1PodTemplateSpec> getPodTemplateSpecResolver();

  protected abstract ControllerObjectReconciler getObjectReconciler();

  /**
   * 네임스페이스의 allowlist 라벨이 바뀌면 해당 네임스페이스의 워크로드를 다시 reconcile해, 재시작
   * 없이 런타임에 toleration 주입/제거가 반영되게 한다.
   */
  private void configureNamespaceAllowlistWatch() {
    this.builder.withReadyFunc(this.sharedInformerFactory
        .getExistingSharedIndexInformer(V1Namespace.class)::hasSynced);
    this.builder.watch(this::createNamespaceWatch);
  }

  private ControllerWatch<V1Namespace> createNamespaceWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1Namespace> watch = new DefaultControllerWatch<>(workQueue,
        V1Namespace.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.namespaceAllowlistLabelFilter());
    watch.setRequestBuilder(
        this.requestBuilderFactory.namespaceToNamespacedObjects(getObjectType().objClass()));
    return watch;
  }

  private Reconciler createReconciler(List<? extends K8sObjectType<?>> supportedTypes) {
    return new WorkloadControllerReconciler(
        this.sharedInformerFactory,
        this.reconciliationService,
        supportedTypes,
        reconcilesWhenOwnedByUnsupportedType(),
        getObjectType().objClass(),
        getPodTemplateSpecResolver(),
        getObjectReconciler(),
        getWorkloadNodesResolver());
  }

}
