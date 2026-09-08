# 구현 — AIP-3022 CR 소유 워크로드 toleration/affinity 미주입

> 계획·설계 문서는 이 디렉토리의 `01-design.md` 다 (`01-plan.md` 대신 그 이름을 쓴다 — AIP-3022 가
> 조사·설계 티켓이었고 Jira 코멘트·PR #119·umbrella 백로그 BL-28 이 그 경로를 참조한다).
> 구현 티켓은 **AIP-3097**(1/3) · **AIP-3098**(2/3) · **AIP-3099**(3/3).

## 1. 변경 파일 (14개)

CRD 스펙 변경 없음 → `kubernetes/examples/*.yaml` 갱신 대상 없음. RBAC 변경 없음.
`±` 는 base `develop` `618ceef` 대비.

### 메인 (8개)

| 파일 | ± | 티켓 | 변경 |
|---|---|---|---|
| `controller/workload/WorkloadControllerReconciler.java` | +57/−1 | 3097 | `shouldSkipByControllerOwner()` + `isSupportedControllerType()` |
| `controller/workload/WorkloadControllerFactory.java` | +43/−2 | 3097 | `createController(List)` 오버로드 + 정책 훅 `reconcilesWhenOwnedByUnsupportedType()` |
| `controller/workload/DaemonSetWorkloadControllerFactory.java` | +11/−0 | 3097 | 정책 훅을 `true` 로 오버라이드 (유일) |
| `configuration/ControllerConfiguration.java` | +21/−5 | 3097 | `resolveSupportedWorkloadTypes()` 단일 수집 지점 |
| `controller/workload/RootWorkloadControllerResolver.java` | +27/−12 | 3098 | `getObject` → `findObject`(Optional), 재귀 중단, `requireNonNull` 제거 |
| `controller/workload/PodReconciler.java` | +5/−10 | 3098 | `catch → deletePod` 분기 제거 |
| `mutating/service/PodReviewHandler.java` | +1/−7 | 3099 | try/catch 제거 |
| `controller/workload/PodNodesResolver.java` | +5/−16 | 3099 | `_getNodes()` · `//todo` 제거 |

### 테스트 (6개)

| 파일 | ± | 비고 |
|---|---|---|
| `controller/workload/WorkloadControllerReconcilerTest.java` | +34/−4 | **기존 4개 테스트 본문 0줄 변경.** 삭제 4줄은 전부 `setUp()` 의 `factory` 지역변수 → 필드 승격 |
| `configuration/ControllerConfigurationWiringTest.java` | 신규 271줄 | 배선 불변식 6건 (§5) |
| `controller/workload/WorkloadControllerReconcilerUnsupportedOwnerTest.java` | 신규 178줄 | 정책 훅 `true`(DaemonSet 하네스) 경로 4건 |
| `mutating/service/PodReviewHandlerTest.java` | +162/−2 | CR 소유 DaemonSet 파드 (실제 배선 조립) |
| `controller/workload/RootWorkloadControllerResolverTest.java` | 신규 136줄 | 재귀 중단 4건 |
| `controller/workload/PodReconcilerTest.java` | 신규 184줄 | 삭제/미삭제 4건 |

`domain/k8s/ReconciliationService.java` 는 **한 줄도 바뀌지 않았다** (0 라인). `project == null` 경로가
기존 nodeAffinity term 을 보존하는 동작에 운영 DaemonSet 15개가 의존하기 때문이다.

## 2. AIP-3097 — 리컨실러 owner kind 대조 (DaemonSet 한정)

### 2-1. 판정 로직

스킵 여부는 두 갈래로 갈린다 — **타입 대조**(`isSupportedControllerType`)와 **정책**
(`reconcilesWhenOwnedByUnsupportedType`).

| controller ownerReference | 판정 |
|---|---|
| 없음 | 진행 (자기가 root) |
| 지원 타입 + informer 등록 | **스킵** (root 가 따로 리컨실됨 — Deployment→ReplicaSet 중복 방지) |
| 미지원 kind 또는 informer 미등록 | 정책 훅이 `true` 면 진행, `false` 면 스킵 |

대조 키는 `K8sObjectTypeKey(apiVersion, kind)` — **kind 단독 비교가 아니다.**
`RootWorkloadControllerResolver` 와 동일한 키·동일한 informer 판정을 쓴다.

정책 훅은 `WorkloadControllerFactory` 에 있고 기본값이 `false` 다.
`DaemonSetWorkloadControllerFactory` 만 `true` 로 오버라이드한다. **리컨실러는 boolean 만 받고 특정
타입 지식을 갖지 않는다** — `controllerObjectClass == V1DaemonSet.class` 같은 분기를 범용 리컨실러에
넣지 않기 위한 것이다.

왜 DaemonSet 한정인지는 `01-design.md` 결정 1 "적용 범위" 절에 있다. 요지는 6종 전부에 적용하면
`Workspace` 소유 StatefulSet 3건의 템플릿(`tolerations`/`affinity`/`imagePullSecrets` 가 모두 `null`)이
새로 쓰이면서 **실행 중 Workspace 파드가 롤링 재시작**되고 **aipub-backend 와 update 루프** 위험이
생긴다는 것이다.

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

## 4-1. 배선 검증 테스트

`controllerManager` 빈 메서드가 그 안에서 `Executors.newSingleThreadExecutor()` 로 매니저를 실행하므로
(`ControllerConfiguration:80-81`) 통짜 `@SpringBootTest` 는 컨텍스트 로딩만으로 인포머 watch 가 API
서버에 붙으려 한다. 그래서 쓰지 않고, 배선 불변식만 겨냥한 6건을 뒀다
(`ControllerConfigurationWiringTest`).

| # | 고정하는 것 |
|---|---|
| 1 | `resolveSupportedWorkloadTypes()` 가 모은 값 == 지원 6종. 하나라도 빠지면 그 kind 를 owner 로 갖는 자식이 미지원으로 판정돼 **Deployment→ReplicaSet 중복 리컨실이 조용히 되살아난다** |
| 2 | `ControllerConfiguration` 의 `@Bean` 메서드 중 `WorkloadControllerFactory` 반환형이 6개 (클래스 수가 아니라 `@Bean` 메서드 수) |
| 3 | 6개 팩토리가 `createController(supportedTypes)` 로 실제로 빌드된다 |
| 4 | 무인자 `createController()` 는 `UnsupportedOperationException` |
| 5 | 정책 훅이 `true` 인 팩토리는 DaemonSet 하나뿐. 클래스 이름이 아니라 `getObjectType().typeKey()` 로 판정해 오버라이드가 옮겨가도 잡는다 |
| 6 | **팩토리의 정책 값이 실제로 빌드된 리컨실러까지 전달된다** |

운영 코드 `SharedInformerFactoryProvider` + 운영과 같은 registrar 로 만든 **실제
`SharedInformerFactory`** 를 쓴다. informer 등록은 API 를 때리지 않으며, 스레드·소켓을 실측해
확인했다(informer/reflector/OkHttp 스레드 0개).

두 가지 제약을 우회해야 했다.

- `SharedInformerFactory` 생성자가 `read timeout of ApiClient must be zero` 를 요구한다 → 테스트에서
  `setReadTimeout(0)` 을 명시한다(운영에서는 `ClientBuilder` 가 세팅).
- 업스트림 `DefaultControllerBuilder()` 생성자가 `DefaultRateLimitingQueue(newSingleThreadExecutor())`
  를 만들고 그 생성자가 대기 루프를 submit 한다. 즉 **팩토리 생성만으로 non-daemon 스레드가 뜬다**
  (이번 변경과 무관하며 mock 인포머 팩토리를 써도 같다). 픽스처를 `@BeforeAll` 로 묶어 클래스당 6개로
  제한하고 `@AfterAll` 에서 `Controller.shutdown()` 을 호출한다. 실측 결과 `shutdown()` 은 워커 루프를
  끝내지만 **스레드를 회수하지는 않는다** — `DefaultDelayingQueue` 가 자기 executor 를 소유하고
  `shutDown()` 이 그것을 종료하지 않기 때문이다. 유휴·비네트워크 스레드라 무해하고, 팩토리에 executor
  주입 지점이 없어 메인 코드를 바꾸지 않고는 개선할 수 없다.

### 6번이 왜 필요했는가

QA 델타 검증에서 **뮤테이션으로 실증된 빈틈**이다. `createReconciler` 호출부를
`reconcilesWhenOwnedByUnsupportedType(),` → `false,` 로 바꾸면 운영에서 CR 소유 DaemonSet 이 조용히
toleration 0개로 회귀한다(= 이 티켓의 원래 결함 재발). 그런데 **전체 스위트가 통과했다.**

테스트 5는 팩토리 반환값만 보고, 리컨실러 단위 테스트는 테스트가 직접 넘긴 리터럴만 본다 —
**전달 구간이 무관측**이었다. 업스트림 바이트코드에서 `DefaultControllerBuilder.build()` 가 리컨실러를
감싸지 않고 그대로 넘기고 `DefaultController.getReconciler()` 가 public 임을 확인해, 실제 빌드된
컨트롤러에서 운영에 도는 리컨실러를 꺼내 팩토리 반환값과 대조한다. 메인 코드 가시성은 넓히지 않았다
(리컨실러 필드는 `private` 유지, 테스트에서만 리플렉션).

행동 기반 검증은 채택하지 않았다. 플래그 `true`/`false` 가 둘 다 `Result(false)` 를 반환하고(노드 0개
→ `Set.copyOf` 일치 → API 호출 없음) 구별하려면 실제 PATCH 를 유발해 "네트워크 없음" 성질을 깨야 한다.

### 이 테스트가 덮지 않는 것

registrar 목록을 테스트가 직접 구성하므로 **Spring 설정에서 `@Bean` registrar 가 빠지는 회귀는 잡지
못한다**(기동 시 `configureReadyFunc()` 의 `::hasSynced` NPE 로 크게 드러난다). Spring DI 자체와
`ControllerManager.run()` 경로도 명시적으로 범위 밖이다.

바이트코드 상수 풀을 검사해 "설정이 오버로드를 호출한다" 를 고정하던 테스트는 **채택하지 않았다.**
막으려는 회귀가 기동 시 컨텍스트 refresh 실패로 즉시·크게 드러나므로, 오버로드 시그니처를 바꿀 때마다
문자열을 함께 고쳐야 하는 유지보수 비용을 정당화하지 못한다.

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

1. **기동 로그로 워크로드 컨트롤러 6개 등록 확인.** Spring DI(빈 스캔,
   `List<WorkloadControllerFactory<?>>` 주입 해석)와 `ControllerManager.run()` 경로는 배선 테스트의
   명시적 범위 밖이다.
2. 스테이징에서 `trident-node-linux` 템플릿에 pm-toleration 6개 주입 + `metadata.generation` 안정화 +
   `managedFields[].time` 으로 `trident-operator` 재기록 여부 관측 (설계 결정 6 의 선행 조건).
3. `isolation-mode=strict` 전환 시 CR 소유 DaemonSet 파드가 삭제되지 않는지.
4. 운영 회귀: DaemonSet 15개 pm-tol 6개 유지 / cilium 2개 0 유지 / `fluent-bit` 의 coaster
   `evict-ds` nodeAffinity term 보존.
5. **범위 축소가 지켜졌는지** — CR 소유 Deployment/StatefulSet 6건의 템플릿이 그대로여야 한다. 특히
   `Workspace` 소유 StatefulSet 3건의 `tolerations`/`affinity`/`imagePullSecrets` 가 계속 `null` 이고
   **파드가 재시작되지 않아야** 한다.

> 초안(6종 전부 적용)이었다면 AIPub 자체 CR 소유 5건의 generation 안정화 관측이 필수였다. DaemonSet
> 한정으로 좁힌 뒤에는 이들이 쓰기 대상이 아니므로 관측 대신 **불변 확인**으로 바뀌었다(5번).
> 부수 효과로 aipub-backend 와의 update 루프 가능성도 제거됐다.
