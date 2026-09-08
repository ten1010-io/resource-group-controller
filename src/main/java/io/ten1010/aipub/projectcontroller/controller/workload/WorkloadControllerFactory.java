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
        getObjectType().objClass(),
        getPodTemplateSpecResolver(),
        getObjectReconciler(),
        getWorkloadNodesResolver());
  }

}
