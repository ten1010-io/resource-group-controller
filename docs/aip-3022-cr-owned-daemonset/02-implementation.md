# 구현 — AIP-3022 CR 소유 워크로드 toleration/affinity 미주입

> 계획·설계 문서는 이 디렉토리의 `01-design.md` 다 (`01-plan.md` 대신 그 이름을 쓴다 — AIP-3022 가
> 조사·설계 티켓이었고 Jira 코멘트·PR #119·umbrella 백로그 BL-28 이 그 경로를 참조한다).
> 구현 티켓은 **AIP-3097**(1/3) · **AIP-3098**(2/3) · **AIP-3099**(3/3).

## 1. 변경 파일 (11개)

CRD 스펙 변경 없음 → `kubernetes/examples/*.yaml` 갱신 대상 없음. RBAC 변경 없음.

### 메인 (7개)

| 파일 | ± | 티켓 | 변경 |
|---|---|---|---|
| `controller/workload/WorkloadControllerReconciler.java` | +38/−1 | 3097 | 조기 반환을 `isOwnedBySupportedControllerType()` 으로 축소 |
| `controller/workload/WorkloadControllerFactory.java` | +22/−2 | 3097 | `createController(List<? extends K8sObjectType<?>>)` 오버로드 추가 |
| `configuration/ControllerConfiguration.java` | +18/−5 | 3097 | `resolveSupportedWorkloadTypes()` 단일 수집 지점 |
| `controller/workload/RootWorkloadControllerResolver.java` | +27/−12 | 3098 | `getObject` → `findObject`(Optional), 재귀 중단, `requireNonNull` 제거 |
| `controller/workload/PodReconciler.java` | +5/−10 | 3098 | `catch → deletePod` 분기 제거 |
| `mutating/service/PodReviewHandler.java` | +1/−7 | 3099 | try/catch 제거 |
| `controller/workload/PodNodesResolver.java` | +5/−16 | 3099 | `_getNodes()` · `//todo` 제거 |

### 테스트 (4개)

| 파일 | ± | 비고 |
|---|---|---|
| `controller/workload/WorkloadControllerReconcilerTest.java` | +59/−4 | **기존 4개 테스트 본문 무변경.** 삭제 4줄은 전부 `setUp()` 의 `factory` 지역변수 → 필드화 + `SUPPORTED_TYPES` 인자 추가 |
| `mutating/service/PodReviewHandlerTest.java` | +162/−2 | CR 소유 DaemonSet 파드 케이스 (실제 배선 조립) |
| `controller/workload/RootWorkloadControllerResolverTest.java` | 신규 | 재귀 중단 4건 |
| `controller/workload/PodReconcilerTest.java` | 신규 | 삭제/미삭제 4건 |

`domain/k8s/ReconciliationService.java` 는 **한 줄도 바뀌지 않았다** (0 라인). `project == null` 경로가
기존 nodeAffinity term 을 보존하는 동작에 운영 DaemonSet 15개가 의존하기 때문이다.

## 2. AIP-3097 — 리컨실러 owner kind 대조

### 2-1. 판정 로직

```java
private boolean isOwnedBySupportedControllerType(KubernetesObject controller) {
  Optional<V1OwnerReference> ownerReferenceOpt =
      K8sObjectUtils.findControllerOwnerReference(controller);
  if (ownerReferenceOpt.isEmpty()) {
    return false;                                    // owner 없음 → 자기가 root
  }
  V1OwnerReference ownerReference = ownerReferenceOpt.get();
  K8sObjectType<?> ownerType = this.supportedTypes.get(
      new K8sObjectTypeKey(ownerReference.getApiVersion(), ownerReference.getKind()));
  if (ownerType == null) {
    return false;                                    // 미지원 kind → 자기가 root
  }
  return this.sharedInformerFactory
      .getExistingSharedIndexInformer(ownerType.objClass()) != null;   // informer 미등록도 동일
}
```

`true` 일 때만 스킵한다. 대조 키는 `K8sObjectTypeKey(apiVersion, kind)` — **kind 단독 비교가 아니다.**
`RootWorkloadControllerResolver` 와 동일한 키·동일한 informer 판정을 쓴다(결정 1).

### 2-2. `supportedTypes` 주입 — 시그니처 선택

`createController()` 는 `ControllerFactory` 인터페이스 메서드이고 비-워크로드 팩토리 20여 개가 이를
구현·호출한다. 인터페이스를 바꾸면 전부 깨진다. 두 후보를 검토했다.

- **(A) 오버로드 추가 — 채택.** `WorkloadControllerFactory` 에
  `createController(List<? extends K8sObjectType<?>>)` 를 추가하고, 무인자 `createController()` 는
  `UnsupportedOperationException` 을 던지도록 override 한다.
- (B) 팩토리에 `setSupportedTypes()` 세터 — 기각. 팩토리에 가변 상태를 만들고 "세터 누락 → 조용히
  전부 미지원 취급" 이라는 실패 모드를 만든다.

인자 타입을 `Collection<K8sObjectTypeKey>` 가 아니라 `List<? extends K8sObjectType<?>>` 로 둔 것은
informer 등록 판정에 `objClass()` 가 필요하고, `RootWorkloadControllerResolver` 에 넘기는 값과
**동일한 객체**여야 root 정의가 갈라지지 않기 때문이다.

무인자 경로는 현재 도달 불가다 — 호출 지점 20곳이 전부 비-워크로드 팩토리이고
(`PodControllerFactory` 는 `ControllerFactory` 직접 구현체), `List<ControllerFactory>` 주입 지점이
없고, 워크로드 팩토리 6개는 `@Bean` 에서 구상 타입으로 선언되며 `ControllerFactory` 는
`FactoryBean` 이 아니다.

### 2-3. 단일 수집 지점

```java
private static List<? extends K8sObjectType<?>> resolveSupportedWorkloadTypes(
    List<WorkloadControllerFactory<?>> workloadControllerFactories) {
  return workloadControllerFactories.stream()
      .map(WorkloadControllerFactory::getObjectType)
      .toList();
}
```

`controllerManager` 빈(`:67-73`)과 `rootControllerResolver` 빈(`:265-270`)이 이 메서드를 공유한다.
**두 곳이 각자 수집하면 root 정의가 갈라진다** — 이번 변경의 핵심 불변식이다.

순환은 없다. `createController()` 는 팩토리 전체 목록이 이미 주입된 `controllerManager` 빈 안에서
호출되므로, 팩토리 빈 → supportedTypes 빈 → 팩토리 빈 사이클이 생기지 않는다.

## 3. AIP-3098 — root 해석 재귀 중단 + 파드 삭제 분기 제거

### 3-1. `findObject` 가 Optional 을 반환한다

세 경우를 **"여기서 root 해석을 멈춘다"** 로 동일하게 취급한다.

| 경우 | 이전 | 이후 |
|---|---|---|
| 미지원 kind | `UnsupportedControllerException` | `Optional.empty()` |
| informer 미등록 | `UnsupportedControllerException` | `Optional.empty()` |
| 캐시에 부모 없음 | **NPE** (`Objects.requireNonNull`) | `Optional.empty()` |

세 번째가 결정 7 이다. NPE 는 `UnsupportedControllerException` 이 아니라서 `PodReviewHandler` 의
catch 를 통과했고, 웹훅 500 → pods 웹훅 `failurePolicy: Ignore` → **toleration 0개로 조용히 통과**
였다.

### 3-2. 파드 레벨은 파드 자신을 root 로 반환하지 않는다

`getRootController(V1Pod)` 는 부모를 해석할 수 없을 때 **`Optional.empty()`** 를 반환한다(파드 자신이
아니다). 파드를 root 로 주면 `CompositeWorkloadControllerNodesResolver.resolvers` 에 `V1Pod` 키가
없어(`ControllerConfiguration:275-281` 이 워크로드 팩토리 6개의 `objClass()` 로만 채운다)
`UnsupportedControllerException` 이 되고, 폴백이 사라진 웹훅이 다시 500 을 낸다.

`empty` 는 `PodNodesResolver.getNodes()` 의 root-없음 분기가 받아 네임스페이스의 Project 바인딩 노드를
쓴다 — **제거된 `_getNodes()` 와 동일한 결과**다. `NamespaceNameResolver.resolveProjectName` 이 항등
함수라 `DefaultWorkloadControllerNodesResolver` 와도 결과가 같다. 즉 이 경로는 회귀가 아니다.

### 3-3. 남긴 삭제와 없앤 삭제

`PodReconciler.processCaseThatProjectManagedNode`:

```java
if (!NodeUtils.isStrictIsolationMode(node)) { return new Result(false); }   // 유지
List<V1Node> allowedProjectNodeObjects = this.podNodesResolver.getNodes(pod);
//  ↑ 이전에는 try/catch 로 감싸 UnsupportedControllerException 시 deletePod — 제거됨
if (!allowedProjectNodes.contains(K8sObjectUtils.getName(node))) {
  deletePod(pod);                                                          // 유지 (strict 격리 본체)
  return new Result(false);
}
```

`processCaseThatNotProjectManagedNode` 의 `deletePod` 도 유지. 즉 **"모르면 지운다" 만 폐기하고
"허용 노드 밖이면 지운다" 는 그대로**다.

`UnsupportedControllerException` 클래스와 `CompositeWorkloadControllerNodesResolver` 의 방어는
계약 명시용으로 남겼다(결정 2 이후 도달 불가).

## 4. AIP-3099 — 웹훅 폴백 제거

```java
// before
List<V1Node> allowedProjectNodeObjects;
try {
  allowedProjectNodeObjects = this.podNodesResolver.getNodes(pod);
} catch (UnsupportedControllerException e) {
  allowedProjectNodeObjects = this.podNodesResolver._getNodes(pod); // todo
}

// after
List<V1Node> allowedProjectNodeObjects = this.podNodesResolver.getNodes(pod);
```

`PodNodesResolver._getNodes()` 와 `//todo` 주석 제거. `getNodes()` 의 root-없음 분기는 유지 —
제거 대상은 그 복사본뿐이었다. `UnsupportedControllerException` import 2곳 정리.

## 5. 흐름 — trident 사례 (before / after)

```
[before]  DaemonSet trident-node-linux (owner: TridentOrchestrator)
          └ 리컨실러: ownerRef 있음 → 조기 반환                    → 템플릿 toleration 0개
            파드: root 해석 → DaemonSet → owner TridentOrchestrator
                  → UnsupportedControllerException
                  → 웹훅 _getNodes() 폴백 → trident ns 에 Project 없음 → List.of()
                  → 벤더 catch-all NoSchedule 이 치환되고 주입 0개   → 재생성 시 Pending
                    (strict 였다면 PodReconciler 가 파드 삭제)

[after]   DaemonSet trident-node-linux (owner: TridentOrchestrator)
          └ 리컨실러: owner kind 미지원 → 스킵 안 함 → 자기 자신이 root
                     → DaemonSetWorkloadControllerNodesResolver
                     → NodeGroup daemonSetPolicy(allowAllDaemonSets) 반영
                     → 템플릿에 pm-toleration 6개 주입 (바인딩 노드 3 × effect 2)
                     → project == null 이라 arch/os nodeAffinity 는 보존
            파드: root 해석 → DaemonSet 에서 재귀 중단 → 같은 resolver → 같은 노드 집합
                  → 파드도 동일 toleration. 삭제 분기 진입 없음
```

리컨실러와 웹훅이 CR 소유 DaemonSet 에 대해 **같은 resolver 를 타 노드 집합이 일치**한다.

## 6. 멱등성

trident 케이스로 `reconcileTolerations` 를 2회 적용해 고정점을 확인했다. 1회차에 벤더 catch-all
`NoSchedule` 이 치환되고 pm-toleration 6개가 추가되며, 2회차에는 `key == null && effect == NoSchedule`
대상이 없어 무변화다. 따라서 `DaemonSetWorkloadControllerFactory.reconcileController` 의 `Set.copyOf`
동등 비교 3종(tolerations · nodeSelectorTerms · imagePullSecrets)이 성립해 쓰기 없이
`Result(false)` 로 끝난다 — update 루프가 코드 쪽에서 생기지 않는다.

오퍼레이터 쪽 되돌림은 코드로 보증할 수 없다. 설계 결정 6 의 순서(스테이징 실측 → 필요 시 제외
셀렉터)를 따른다.

## 7. 배포 전 확인 (코드로 대체 불가)

1. `ApplicationTests` 가 빈 클래스라 **Spring 컨텍스트 로딩 테스트가 없다.**
   `createController(supportedTypes)` 배선은 컴파일과 정적 전수 확인으로만 보증된다. 기동 로그에서
   워크로드 컨트롤러 6개 등록을 확인할 것.
2. 스테이징에서 `trident-node-linux` pm-toleration 6개 주입 + `metadata.generation` 안정화 +
   `managedFields[].time` 으로 `trident-operator` 재기록 여부 관측.
3. **AIPub 자체 CR 소유 5건**(Operation×2 Deployment, Workspace×3 StatefulSet — `01-design.md` §1.3)도
   결정 1로 새 쓰기 대상이 된다. 이들의 generation 안정화도 함께 볼 것. 설계 §5 체크리스트에 빠져
   있던 항목이다.
4. 운영 회귀: DaemonSet 15개 pm-tol 6개 유지 / cilium 2개 0 유지 / `fluent-bit` 의 coaster
   `evict-ds` nodeAffinity term 보존.
5. `isolation-mode=strict` 전환 시 CR 소유 DaemonSet 파드가 삭제되지 않는지.
