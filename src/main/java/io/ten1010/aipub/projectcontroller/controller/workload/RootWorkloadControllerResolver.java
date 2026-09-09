package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeKey;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RootWorkloadControllerResolver {

  private final KeyResolver keyResolver;
  private final Map<K8sObjectTypeKey, K8sObjectType<?>> supportedTypes;
  private final SharedInformerFactory sharedInformerFactory;

  public RootWorkloadControllerResolver(List<? extends K8sObjectType<?>> supportedTypes,
      SharedInformerFactory sharedInformerFactory) {
    this.keyResolver = new KeyResolver();
    this.supportedTypes = new HashMap<>();
    for (K8sObjectType type : supportedTypes) {
      this.supportedTypes.put(type.typeKey(), type);
    }
    this.sharedInformerFactory = sharedInformerFactory;
  }

  /**
   * 파드의 가장 가까운 지원 워크로드 root를 찾는다. 파드의 controller ownerReference가 없거나 그
   * 부모를 찾을 수 없으면(미지원 kind · informer 미등록 · 캐시 미존재) empty를 반환한다. 이때
   * 호출자는 파드가 속한 네임스페이스의 Project를 기준으로 노드를 해석한다
   * ({@link PodNodesResolver#getNodes}).
   */
  public Optional<KubernetesObject> getRootController(V1Pod pod) {
    Optional<V1OwnerReference> opt = K8sObjectUtils.findControllerOwnerReference(pod);
    if (opt.isEmpty()) {
      return Optional.empty();
    }
    return findObject(K8sObjectUtils.getNamespace(pod), opt.get()).map(this::getRootController);
  }

  private KubernetesObject getRootController(KubernetesObject controller) {
    Optional<V1OwnerReference> opt = K8sObjectUtils.findControllerOwnerReference(controller);
    if (opt.isEmpty()) {
      return controller;
    }
    Optional<KubernetesObject> parentControllerOpt = findObject(
        K8sObjectUtils.getNamespace(controller), opt.get());
    if (parentControllerOpt.isEmpty()) {
      return controller;
    }
    return getRootController(parentControllerOpt.get());
  }

  /**
   * ownerReference가 가리키는 객체를 informer 캐시에서 찾는다. 다음 세 경우는 모두 "여기서 root
   * 해석을 멈춘다"로 동일하게 취급해 empty를 반환한다.
   *
   * <ul>
   *   <li>지원 워크로드 타입이 아님 — 임의 CR이 소유한 워크로드. 그 워크로드 자신이 root다
   *   <li>타입의 informer가 등록되지 않음
   *   <li>캐시에 객체가 없음 — 아직 동기화되지 않았거나 이미 삭제됨
   * </ul>
   */
  private Optional<KubernetesObject> findObject(String namespace, V1OwnerReference reference) {
    K8sObjectTypeKey typeKey = new K8sObjectTypeKey(reference.getApiVersion(), reference.getKind());
    K8sObjectType<?> type = this.supportedTypes.get(typeKey);
    if (type == null) {
      return Optional.empty();
    }
    SharedIndexInformer<? extends KubernetesObject> informer = this.sharedInformerFactory.getExistingSharedIndexInformer(
        type.objClass());
    if (informer == null) {
      return Optional.empty();
    }
    String objKey = this.keyResolver.resolveKey(namespace, reference.getName());

    return Optional.ofNullable(informer.getIndexer().getByKey(objKey));
  }

}
