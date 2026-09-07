# 설계 — AIP-3022 CR 소유 워크로드 toleration/affinity 미주입

## 개요

`WorkloadControllerReconciler` 가 owner 의 **kind 를 보지 않고** controller ownerReference 존재만으로 조기 반환하기 때문에, 워크로드가 아닌 임의 CR 이 소유한 워크로드는 아무도 리컨실하지 않는다. 같은 가정이 `RootWorkloadControllerResolver` 의 미지원 kind 예외로 이어져 `PodReconciler`(파드 삭제)와 `PodReviewHandler`(`_getNodes()` 폴백)까지 번진다.

cluster12 실측으로 **결함 대상은 DaemonSet 1건(`trident/trident-node-linux`)** 이며, 파드 레벨에서는 이미 실제 손상이 관측된다. 아래 7개 결정으로 세 지점을 함께 해소한다.

조사 시점: 2026-09-07 (cluster12, 조회 전용). 코드 기준: `develop` `618ceef` (인용한 모든 위치는 이 커밋에서 재확인했다).

---

## 1. 조사 결과

### 1.1 클러스터 전제

| 항목 | 값 |
|---|---|
| 노드 | 6개. project-managed 3개 (`vnode1.pnode7`, `vnode2.pnode15`, `vnode7.pnode15`) |
| isolation-mode | project-managed 3개 **전부 `lenient`**. taint 는 `NoSchedule` 만(`NoExecute` 없음) |
| NodeGroup | `aipub-node-group` 1개. `policy.daemonSet.allowAllDaemonSets: true`, `status.allBoundNodes` = project-managed 3개 전부 |
| Project | 17개. 각 프로젝트 네임스페이스에 `project.aipub.ten1010.io/project=<name>` 라벨 |
| 네임스페이스 allowlist | **사용 0건** (`project.aipub.ten1010.io/allowlisted` 라벨을 가진 네임스페이스 없음) |
| 제외 셀렉터 | `app.kubernetes.io/part-of=cilium`, `kubevirt.io` (`application.yaml:38`) |

`allowAllDaemonSets: true` 는 "클러스터의 모든 DaemonSet 에 바인딩 노드 toleration 을 준다"는 선언이다. 이 정책과 실제 상태의 어긋남이 이 티켓의 본질이다.

### 1.2 DaemonSet 18개 주입 현황 — 결함 대상은 1건

`allowAllDaemonSets: true` 이므로 기대값은 전 DaemonSet 에 project-managed toleration 6개(바인딩 노드 3 × effect 2).

| 구분 | 개수 | pm-toleration | 판정 |
|---|---|---|---|
| 정상 주입 | 15 | 6 | 기대대로 동작 |
| `kube-system/cilium`, `cilium-envoy` | 2 | 0 | **의도된 제외**. `app.kubernetes.io/part-of=cilium` 이 DaemonSet·파드 템플릿 양쪽에 있어 리컨실·웹훅 모두에서 빠지고, 자체 catch-all toleration 으로 동작 |
| `trident/trident-node-linux` | 1 | 0 | **결함**. owner `TridentOrchestrator(trident.netapp.io/v1)` 때문에 조기 반환 |

`network-operator/rdma-shared-dp-ds` 는 pm-toleration 6개가 정상 주입돼 있다. 티켓의 "이 결함 사례 아님"(ownerReference 없이 라벨로 관리) 확인.

### 1.3 CR 소유 워크로드 전수 — 11건

| kind | 네임스페이스/이름 | owner CR | pm-tol | 평가 |
|---|---|---|---|---|
| DaemonSet | `trident/trident-node-linux` | `TridentOrchestrator` | 0 | **결함 대상** |
| Deployment | `trident/trident-controller` | `TridentOrchestrator` | 0 | Project 없는 ns → Default resolver 로도 결과 동일. 증상 없음 |
| Deployment | `aipub-monitoring/vmagent-vmstack-vmagent` | `VMAgent` | 0 | 동일 |
| Deployment | `aipub-monitoring/vmalert-vmstack-vmalert` | `VMAlert` | 0 | 동일 |
| Deployment | `aipub-monitoring/vmauth-vmstack-vmauth` | `VMAuth` | 0 | 동일 |
| StatefulSet | `aipub-monitoring/vmalertmanager-vmstack-alertmanager` | `VMAlertmanager` | 0 | 동일 |
| Deployment | `jb-test2/jb-test-ops-4f4577c7` | `Operation`(aipub) | 4 | 생성자가 직접 주입. 아래 참고 |
| Deployment | `jb-test2/multi-pod-single-gpu-7d4e0f54` | `Operation`(aipub) | 4 | 동일 |
| StatefulSet | `fe-team/qewrqwer-d5daaeb7` | `Workspace`(aipub) | 0 | 파드가 웹훅 폴백으로 받음 |
| StatefulSet | `hk-proj/qwe-b993afcc` | `Workspace`(aipub) | 0 | 동일 |
| StatefulSet | `test-lyj/test-lyj-ws-63f1f04f` | `Workspace`(aipub) | 0 | 동일 |

- **AIPub 자체 CR(Operation·Workspace) 소유 워크로드 5건이 티켓에 언급되지 않은 채 존재한다.** 다만 전부 프로젝트 네임스페이스에 있고, 파드가 웹훅 폴백에서 Project 바인딩 노드 toleration 을 받으므로 현재 증상이 없다. `hk-proj` 파드 실측: `pm-tol=4` = 바인딩 노드 2개 × effect 2 로 정상.
- `Operation` 소유 Deployment 2건의 toleration writer 는 `managedFields` 상 `OpenAPI-Generator`(Java client) 로, 객체 전체 필드를 단일 매니저가 소유한다. **현재 리컨실러는 조기 반환하므로 이 값을 쓴 주체가 아니다.** aipub-backend 생성 시점 주입으로 보이나 단정하지 않는다 — aipub-backend 쪽 확인 필요.

### 1.4 파드 레벨 실측 — 웹훅이 벤더 toleration 을 실제로 깎는다 (핵심)

`trident-node-linux` DaemonSet 템플릿의 toleration (writer = `trident-operator`):

```
{effect: NoExecute,  operator: Exists}
{effect: NoSchedule, operator: Exists}
```

파드 6개 중 5개(2026-07-27T06:42:38Z 생성)는 템플릿 그대로다. 그런데 **`trident-node-linux-ck9td`(2026-08-19T07:53:19Z 생성, `vnode1.pnode7`)** 만 다르다.

```
{effect: NoExecute,  operator: Exists}                                        ← 유지
{effect: NoSchedule, key: node-role.kubernetes.io/control-plane, Exists}      ← 치환됨
project-managed toleration: 0개
```

- 치환 주체는 `ReconciliationService.replaceAllKeyNoScheduleEffectTolerations`(:188-212). 즉 **웹훅이 폴백 경로로 이 파드를 처리하면서 벤더의 catch-all `NoSchedule` 을 제거하고 대체 toleration 을 아무것도 주입하지 않았다.**
- 나머지 5개가 멀쩡한 이유는 클러스터 부트스트랩 순서다(노드 06:38 → trident 06:42, project-controller 미기동). `ck9td` 는 `vnode1.pnode7` 이 조인한 **바로 그 초**에 생성돼 웹훅을 온전히 통과한 유일한 파드다.
- 지금 Running 인 이유: `NoSchedule` 은 이미 바인딩된 파드를 축출하지 않고, 파드 생성이 노드에 project-managed taint 가 붙기 전이라 스케줄도 통과했다. `NoExecute` catch-all 은 두 치환 함수 어디에도 걸리지 않아(`isAllKeyAllEffectToleration` 은 `effect==null` 대상) 살아남아 축출도 없다.
- **따라서 이 파드가 재생성되는 순간(노드 재부팅 · DaemonSet 업데이트 · drain) `vnode1.pnode7` 에서 Pending 이 된다.** 티켓은 이 결함을 "strict 노드에서만 발현되는 잠복"으로 평가했으나, **lenient 노드에서도 스케줄 실패로 발현된다.** 위험도를 한 단계 올려야 한다.

> 무관한 관측 (오귀인 방지): `jb-test2`/`hk-proj` 의 Pending 파드 다수는 `[LicenseFilter] 0 nodes are available` 로, coaster 라이선스 문제다. 해당 파드들의 pm-toleration 은 정상 주입돼 있다.

### 1.5 update 루프 위험 — 실측상 낮음, trident 만 미검증

`managedFields` 타임스탬프로 ping-pong 여부를 봤다.

| DaemonSet | 매니저별 마지막 쓰기 | 판정 |
|---|---|---|
| `network-operator/rdma-shared-dp-ds` (gen 5, 09-02 생성) | `manager`(NVIDIA) 02:23:43 → `OpenAPI-Generator` 02:25:22(`f:affinity`) → **`Kubernetes Java Client`(project-controller) 04:39:20** 이후 5일간 없음 | **루프 없음.** 오퍼레이터가 project-controller 의 쓰기를 되돌리지 않았다 |
| `aipub-monitoring/fluent-bit` (gen 8) | project-controller 마지막 쓰기 2026-08-19T07:56:41 (= `vnode1.pnode7` 조인 시각) 이후 없음 | 쓰기가 이벤트 구동이며 안정 |
| `trident/trident-node-linux` (gen 1) | `trident-operator` 가 `f:tolerations`·`f:affinity` 소유. 생성 후 6주간 spec 무변경 | **미검증.** 필드 소유권은 주장하지만 상시 재기록은 안 한다. 스테이징 실측 필요 |

즉 "오퍼레이터가 관리하는 DaemonSet 을 project-controller 가 써도 되돌려지지 않는다"는 선례가 이미 운영에 있다. trident-operator 만 남은 미지수다.

### 1.6 결정 1 적용 시 affinity 부작용 — 없음 (확인)

`trident-node-linux` 는 required nodeAffinity(arch/os)와 podAntiAffinity 를 갖는다. 리컨실 대상이 되면:

- `trident` 네임스페이스에 Project 가 없으므로 `project == null` → `addProjectManagedExpressionToTerms(terms, null)` 이 **기존 term 을 그대로 반환**한다(`ReconciliationService:1199-1201`).
- 결과 term 이 비어 있지 않으므로 `DaemonSetWorkloadControllerFactory.reconcileController` 의 else 분기를 타 nodeAffinity 가 보존된다. `requiredDuringSchedulingIgnoredDuringExecution(null)` 로 지우는 if 분기는 term 이 비었을 때만이다.
- `reconcileImageRegistrySecrets(_, null)` 도 기존 값을 반환한다.

이 경로는 이미 같은 네임스페이스군의 DaemonSet 15개에서 매일 돌고 있다(`fluent-bit` 이 coaster `evict-ds` term 을 유지한 채 toleration 6개를 받은 것이 그 증거). **결정 1의 추가 위험은 trident-operator 와의 쓰기 경합 하나뿐이다.**

### 1.7 세 지점의 결함 경로 정리

| 경로 | 현재 동작 | trident 사례 결과 |
|---|---|---|
| 리컨실 (`WorkloadControllerReconciler:69-71`) | owner kind 무검사 조기 반환. **allowlist 검사(:79)보다 앞** | DaemonSet 템플릿에 toleration 0개. allowlist 를 붙여도 템플릿은 못 받는다 |
| 웹훅 (`PodReviewHandler:71-76`) | root 해석 실패 → `_getNodes()` 폴백 → ns 의 Project 조회 → 없으면 `List.of()` | 벤더 catch-all 이 치환되고 주입 0개 (§1.4) |
| 파드 리컨실 (`PodReconciler:107-110`) | 예외 → `deletePod`. 도달 조건 = allowlist 아님 + project-managed 노드 + **strict** | 현재 전 노드 lenient 라 미도달. strict 전환 시 생성→삭제 반복 |

`PodNodesResolver._getNodes()`(:60-72) 는 `getNodes()` 의 root-없음 분기(:46-56)와 **한 줄도 다르지 않은 복사본**이다. 별도 폴백 로직이 아니라 중복 코드다.

---

## 2. 설계 결정

### 결정 1 — 리컨실러가 owner kind 를 대조한다 **(채택)**

`WorkloadControllerReconciler` 의 조기 반환을 "controller ownerReference 존재"에서 "controller ownerReference 의 `(apiVersion, kind)` 가 `supportedTypes` 에 있음"으로 좁힌다. 미지원이면 스킵하지 않고 자기 자신을 root 로 리컨실한다.

- **근거**: `allowAllDaemonSets: true` 정책이 18개 중 17개에 적용되는데 trident 만 owner kind 때문에 빠진다. 정책 선언과 실제의 불일치이며, 벤더 기본값 덕에 우연히 살아 있었을 뿐이다(§1.2, §1.4).
- 대조 키는 `RootWorkloadControllerResolver` 와 **동일하게 `K8sObjectTypeKey(apiVersion, kind)`** 를 쓴다. 한쪽만 kind 만 보면 두 경로의 root 정의가 갈라진다.
- informer 미등록도 미지원과 같게 취급한다(`RootWorkloadControllerResolver:59-63` 의 기존 처리와 통일).
- `supportedTypes` 를 리컨실러에 주입해야 하므로 `WorkloadControllerFactory.createReconciler()`(:86-94) 시그니처가 바뀐다. `ControllerConfiguration:251-257` 이 이미 팩토리 목록에서 `supportedTypes` 를 모으므로 같은 값을 재사용한다.
- **회귀 안전**: 기존 테스트 `WorkloadControllerReconcilerTest:148`(`controllerOwnedWorkloadInAllowlistedNamespace_skipped`)는 owner kind 를 `apps/v1 Deployment`(지원 타입)로 쓰므로 그대로 통과한다. 티켓의 "지원 kind 부모는 여전히 스킵" 수용 기준이 이미 부분 커버돼 있다.
- **부수 효과**: 조기 반환이 allowlist 검사보다 앞이므로(§1.7), 이 변경으로 CR 소유 워크로드도 allowlist 분기에 도달하게 된다. 의도한 방향이다.

### 결정 2 — root 해석이 미지원 owner 에서 예외 대신 직전 지원 객체를 반환한다 **(채택)**

`RootWorkloadControllerResolver.getRootController` 재귀에서 부모 조회가 미지원 kind 면 `UnsupportedControllerException` 대신 **현재 객체를 root 로 반환**한다. 티켓 표현("가장 가까운 지원 워크로드")과 같은 의미이며, 구현상 "미지원 owner 를 만나면 재귀를 멈춘다"가 정확하다.

- trident 파드는 root = `trident-node-linux`(DaemonSet)를 얻어 `DaemonSetWorkloadControllerNodesResolver` 를 타고 NodeGroup `daemonSetPolicy` 를 정상 적용받는다. 결정 1의 root 정의와 일치한다.
- 이 변경으로 `PodReviewHandler` 폴백과 `PodReconciler` 삭제 분기가 **함께 해소**된다(예외가 발생하지 않으므로).
- `CompositeWorkloadControllerNodesResolver:19` 도 같은 예외를 던지지만, 결정 2 이후 root 는 항상 지원 타입이라 여전히 도달 불가다. 예외 클래스와 이 방어는 남긴다.

### 결정 3 — `PodReconciler` 의 "모르면 지운다"는 **방어적 기본값**. 삭제 분기를 제거한다 **(채택)**

- **근거**: 삭제 분기(`PodReconciler:107-110`)와 `_getNodes()` 의 `//todo` 가 **같은 커밋 `96e16fe`(2024-11-04, "feat: switch over to the project domain", 378 파일 12,447+/9,901−)** 에서 함께 들어왔다. 안전장치를 의도한 별도 커밋이 아니고, 같은 예외의 다른 처리 지점에 저자가 `//todo` 를 남긴 것은 미결 사항이었다는 정황이다.
- 결정 2를 적용하면 이 catch 블록은 죽은 코드가 된다. "모르면 지운다" 정책은 명시적으로 폐기한다.
- 대안(예외 시 삭제 대신 스킵)은 채택하지 않는다. 그러면 strict 모드 격리가 조용히 약해진다. 결정 2로 예외를 원천 제거하는 쪽이 격리 강도를 유지한다.
- **삭제 분기 전체를 없애는 것은 아니다.** `allowedProjectNodes` 에 현재 노드가 없을 때의 삭제(`:115-118`)는 strict 격리의 본체이므로 유지한다.

### 결정 4 — `_getNodes()` 를 제거한다 **(채택)**

`getNodes()` 의 root-없음 분기와 완전히 동일한 복사본이고, 결정 2 이후 호출자가 사라진다. `PodReviewHandler:71-76` 의 try/catch 도 함께 제거한다.

### 결정 5 — Deployment/StatefulSet 의 비대칭은 **의도로 확정. 유지한다**

- CR 소유 non-DaemonSet 10건 중 AIPub 자체 CR(Operation 2 · Workspace 3)은 전부 프로젝트 네임스페이스에 있고, `DefaultWorkloadControllerNodesResolver` 결과(Project 바인딩 노드)가 정확히 필요한 값이다(§1.3 `hk-proj` 실측).
- NodeGroup `daemonSetPolicy` 는 이름 그대로 DaemonSet 예외 정책(AIP-1998)이다. Deployment/StatefulSet 까지 확장하면 "프로젝트 미바인딩 네임스페이스의 임의 워크로드가 project-managed 노드에 올라갈 수 있다"가 되어 격리 모델이 무너진다.
- 남는 구멍: `aipub-monitoring` 의 VictoriaMetrics 4건은 Project 없는 네임스페이스라 toleration 0 → project-managed 노드 스케줄 불가. 일반 노드 3개로 충족돼 현재 증상 없다. 필요해지면 **네임스페이스 allowlist** 로 처리한다(daemonSetPolicy 확장이 아니라).

### 결정 6 — 제외 셀렉터는 **최후 수단**. 스테이징 실측이 먼저다 **(순서 고정)**

- `reconcile-excluded-label-selectors` 는 "벤더가 스스로 무제한 toleration 을 보장하는 경우"에 쓰는 장치다. cilium 2건이 그 용례이고, 제외 라벨이 파드 템플릿에도 있어 리컨실·웹훅 양쪽에서 빠진다(§1.2).
- trident 에 적용한다면 `app=node.csi.trident.netapp.io` 가 DaemonSet·파드 템플릿 양쪽에 있어 기술적으로는 작동한다. 그러나 trident 의 벤더 catch-all 은 **웹훅이 이미 깎고 있으므로**(§1.4) 제외로 빼는 것은 결함을 덮는 것이지 고치는 게 아니다. 다른 오퍼레이터가 catch-all 없이 들어오면 같은 문제가 재발한다.
- **순서**: ① 스테이징에서 결정 1·2 적용 → trident-operator 가 되돌리는지 관측 → ② 되돌리는 것이 확인된 오퍼레이터에 대해서만 제외 셀렉터. 이 순서를 뒤집지 않는다. §1.5 의 network-operator 선례상 루프 확률은 낮다.

### 결정 7 — `Objects.requireNonNull` NPE 경로도 같이 없앤다 **(티켓 목록 외 · 추가 채택)**

`RootWorkloadControllerResolver:66` 은 지원 kind 인데 informer 캐시에 부모가 없으면(미동기 · 삭제 직후) NPE 를 던진다. `UnsupportedControllerException` 이 아니라서 `PodReviewHandler` 의 catch 를 통과한다.

- 웹훅: 500 → pods 웹훅은 `failurePolicy: Ignore`(`mutating-webhook-configuration.yaml:15`) → **파드가 toleration 0개로 조용히 통과**. 실패가 로그 외에 드러나지 않는다.
- 파드 리컨실: `AbstractReconciler:52-62` 가 잡아 60초 requeue 무한 재시도(삭제는 안 됨).
- 처리: 부모 미존재도 결정 2와 같게 "root 를 여기서 끊는다"로 통일한다. `requireNonNull` 을 제거하고 현재 객체를 root 로 반환한다.

---

## 3. 즉시 적용 가능한 임시 대응 (코드 변경 없음)

`trident` 네임스페이스에 `project.aipub.ten1010.io/allowlisted=true` 라벨을 붙이면 **파드 레벨 결함이 곧바로 해소된다.**

- `PodReviewHandler:53` allowlist 분기가 폴백보다 앞이므로 `Exists` toleration 쌍이 주입된다 → §1.4 의 catch-all 치환 손상이 사라진다.
- `PodReconciler:70` 에서 조기 반환하므로 strict 전환 시에도 삭제되지 않는다.
- 한계: 리컨실러 조기 반환이 allowlist 검사보다 앞이라 **DaemonSet 템플릿은 여전히 미주입**이다. 파드가 admission 에서 받으므로 실효는 있다.
- 현재 클러스터에 allowlist 라벨을 쓰는 네임스페이스는 0건이므로, 이 조치가 첫 사용 사례가 된다. 되돌리기는 라벨 제거 한 번이다.

구현 티켓 처리 전 `vnode1.pnode7` 재부팅이나 trident 업그레이드가 예정돼 있으면 이 조치를 먼저 적용할 것을 권한다.

---

## 4. 후속 구현 티켓 (3개)

2026-09-07 생성 완료. 전부 부모 에픽 AIP-2646, 9-10월 스프린트.

| 티켓 | 범위 | 결정 | 주요 변경 |
|---|---|---|---|
| **AIP-3097** (1/3) | `WorkloadControllerReconciler` owner kind 대조 | 1 | `supportedTypes` 주입, 조기 반환 조건 축소, `WorkloadControllerFactory.createReconciler()` 시그니처 |
| **AIP-3098** (2/3) | `RootWorkloadControllerResolver` · `PodReconciler` | 2, 3, 7 | 미지원/미존재 owner 에서 재귀 중단, `requireNonNull` 제거, `PodReconciler` catch 삭제 분기 제거 |
| **AIP-3099** (3/3) | `PodReviewHandler` · `PodNodesResolver` | 4 | try/catch 및 `_getNodes()` 삭제 |

AIP-3097 과 AIP-3098 은 서로 독립적으로 적용 가능하다. AIP-3099 는 AIP-3098 선행이 필요하다(Jira `Blocks` 링크로 표시).

---

## 5. 검증 계획

**단위 테스트**

- `WorkloadControllerReconcilerTest`
  - 유지: owner kind 가 `apps/v1 Deployment` 면 스킵 (`:148`, 회귀 고정)
  - 신규: owner kind 가 미지원(`trident.netapp.io/v1 TridentOrchestrator`)이면 자기 자신을 root 로 리컨실
  - 신규: owner kind 가 지원 타입이지만 informer 미등록이면 미지원과 동일 처리
- `RootWorkloadControllerResolver`: 미지원 owner → 직전 지원 객체 반환 / 부모 미존재 → 현재 객체 반환
- `PodReviewHandlerTest`: CR 소유 DaemonSet 의 파드가 `DaemonSetWorkloadControllerNodesResolver` 경로를 타 NodeGroup 정책 노드 toleration 을 받는지
- `PodReconciler`: 미지원 owner 파드가 **삭제되지 않고**, `allowedProjectNodes` 밖의 파드는 여전히 삭제되는지

**스테이징 실측 (결정 6의 전제)**

1. trident 설치 후 결정 1·2 적용
2. `trident-node-linux` 템플릿에 pm-toleration 6개 주입 확인
3. `metadata.generation` 이 한 번 오르고 멈추는지 관측 → 루프 없음 판정. `managedFields[].time` 으로 `trident-operator` 의 재기록 여부 확인
4. 파드 재생성 시 project-managed 노드에 스케줄 성공 확인
5. 노드를 `isolation-mode=strict` 로 전환해 파드가 삭제되지 않는지 확인

**운영 회귀 방지 체크**

- DaemonSet 15개의 pm-toleration 6개 유지
- cilium 2개는 0 유지
- `fluent-bit` 의 coaster `evict-ds` nodeAffinity term 보존 (project==null 경로에서 affinity 가 지워지지 않는지)

---

## 6. 근거 위치

| 대상 | 경로 |
|---|---|
| 리컨실 스킵 | `controller/workload/WorkloadControllerReconciler.java:69-71` (allowlist 는 :79) |
| root 해석 · NPE | `controller/workload/RootWorkloadControllerResolver.java:43-69` (`requireNonNull` :66) |
| 파드 삭제 | `controller/workload/PodReconciler.java:99-120` (allowlist 조기 반환 :70) |
| 웹훅 폴백 | `mutating/service/PodReviewHandler.java:71-76` (allowlist 분기 :53) |
| `_getNodes()` 중복 | `controller/workload/PodNodesResolver.java:39-72` |
| toleration 치환 | `domain/k8s/ReconciliationService.java:153-212` |
| null project 시 affinity 보존 | `domain/k8s/ReconciliationService.java:1197-1216` |
| DaemonSet 노드 해석 | `controller/workload/DaemonSetWorkloadControllerNodesResolver.java` |
| supportedTypes 수집 | `configuration/ControllerConfiguration.java:251-257` |
| 리컨실러 생성 | `controller/workload/WorkloadControllerFactory.java:86-94` |
| 제외 셀렉터 | `src/main/resources/application.yaml:38-40` |
| 예외 처리 정책 | `controller/AbstractReconciler.java:46-66` |
| pods 웹훅 failurePolicy | `kubernetes/controller/project-controller/templates/mutating-webhook-configuration.yaml:15` |
| 3개 지점 도입 커밋 | `96e16fe` (2024-11-04) |

## 7. 관련 티켓

- AIP-1998 `[JA] project controller daemonset 예외 처리` — NodeGroup `daemonSetPolicy` 도입
- AIP-2389 `[JA] Project Controller 특정 namespace 예외처리` — 네임스페이스 allowlist 경로 (§3 임시 대응의 근거)
