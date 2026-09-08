# QA 보고서 — AIP-3022 CR 소유 워크로드 toleration/affinity 미주입

검증 대상: 브랜치 `chore/AIP-3022` (base `develop` `618ceef`). 종합 판정 **PASS**.
`_workspace/03_qa_report.md` 를 기반으로 정리했다.

## 1. 빌드 / 테스트

| 항목 | 결과 |
|---|---|
| `./gradlew clean build` | **BUILD SUCCESSFUL** (QA 2회 + 리더 커밋 전 1회, 총 3회) |
| 테스트 | **256건 / 실패 0 / 에러 0** (본체 224 + `common-*` 32) |
| `./gradlew javadoc` | 본체 경고·에러 0. 신규 javadoc 의 `{@link}` 전부 해소 |
| 컴파일 경고 | base 와 동일한 `CudEventPublishingControllerFactory` unchecked 1건뿐 (신규 없음) |

신규 테스트 11건: 미지원 owner kind → 자기 자신 root(2) · informer 미등록 → 미지원 동일 처리(1) ·
root 재귀 중단 4건(미지원 owner / 부모 캐시 미존재 / 지원 체인 Pod→ReplicaSet→Deployment /
파드 owner 미해석 → empty) · `PodReconciler` 4건(CR 소유 파드 미삭제 / 예외 발생해도 미삭제 + requeue /
허용 노드 밖 여전히 삭제 / allowlist 조기 반환) · `PodReviewHandlerTest` 1건(CR 소유 DaemonSet 파드).

## 2. 회귀 검증 체크리스트

| # | 항목 | 판정 | 근거 |
|---|---|---|---|
| A1 | 기존 4개 테스트 무수정 통과 | PASS | `git diff` 직접 확인. 삭제 4줄은 전부 `setUp()` 의 `factory` 지역변수 → 필드화 + `SUPPORTED_TYPES` 인자 추가. `controllerOwnedWorkloadInAllowlistedNamespace_skipped`(owner `apps/v1 Deployment`) 본문·어서션 **한 글자도 안 바뀜**. 통과를 위해 테스트를 느슨하게 고친 흔적 없음 |
| A2 | 지원 kind 부모 스킵 유지 | PASS | Deployment→ReplicaSet 중복 리컨실 방지 유지 |
| A3 | 분기 우선순위 무변경 | PASS | 리컨실러(인포머 → ownerRef → allowlist → 제외 라벨) · 웹훅(allowlist → 제외 라벨 → 노드 해석) · `PodReconciler`(allowlist 조기 반환이 삭제 분기보다 앞) |
| A4 | `ReconciliationService` 무변경 | PASS | 변경 파일 목록에 아예 없음 (0 라인) |
| B | strict 격리 약화 없음 | PASS | 제거된 것은 `catch (UnsupportedControllerException) → deletePod` **단 하나**. `PodReconciler:110-113`(허용 노드 밖 삭제) · `:123-125`(비 project-managed 노드 + Project 존재 시 삭제) · `:99-101`(strict 조기 반환) 전부 유지 |
| C1 | 파드 레벨 `Optional.empty()` 판단 | PASS | `CompositeWorkloadControllerNodesResolver.resolvers` 가 `ControllerConfiguration:275-281` 에서 워크로드 팩토리 6개의 `objClass()` 로만 채워지고 `PodControllerFactory` 는 `ControllerFactory` 직접 구현체라 `V1Pod.class` 키가 없음 → 파드를 root 로 주면 예외 → `failurePolicy: Ignore` 로 조용한 미주입. **제거된 `_getNodes()` 와 동작 동일**함을 라인 대조로 확인(`getNodes()` root-없음 분기가 `_getNodes()` 와 동일한 6줄, `NamespaceNameResolver.resolveProjectName` 이 항등 함수라 `Default` resolver 와도 결과 일치). 회귀 아님 |
| C2 | 무인자 `createController()` 도달 불가 | PASS | 호출 지점 20곳 전수 확인 — 전부 비-워크로드 팩토리. `List<ControllerFactory>` 주입 지점 0건, 워크로드 팩토리 6개는 `@Bean` 에서 구상 타입 선언, `ControllerFactory` 는 `FactoryBean` 아님, `CudEventPublishingControllerFactory` 는 데코레이터가 아니라 자체 빌더 사용. 기동 실패 위험 없음 |
| D | 배선 정합 | PASS | `resolveSupportedWorkloadTypes()` 단일 지점을 두 빈이 공유. 대조 키 양쪽 모두 `K8sObjectTypeKey(apiVersion, kind)` 이고 지원 6종 상수가 `"apps/v1"`·`"batch/v1"` 형태로 ownerReference 와 문자열 일치. informer 판정 동일. `UnsupportedControllerException`·Composite 방어 잔존. 미사용 import 0건(11개 파일 전수 스캔) |
| E | 멱등성 / 웹훅 안전성 | PASS | §3 참조 |
| F | 테스트 품질 | PASS | §4 참조 |

## 3. 멱등성 / 웹훅 안전성

`reconcileTolerations` 를 trident 케이스로 **2회 적용해 고정점을 증명**했다. 1회차에 벤더 catch-all
`NoSchedule` 이 치환되고 pm-toleration 6개가 추가되며, 2회차에는 `key == null && effect == NoSchedule`
대상이 없어 무변화 → `Set.copyOf` 동등 비교 3종 성립 → 쓰기 없이 `Result(false)`.

`project == null` 경로에서 arch/os nodeAffinity term 도 보존된다(`reconciledSelectorTerms.isEmpty()`
if 분기 미진입).

웹훅은 결정 2 이후 예외 경로가 **순감소**(도달 불가)다. 리컨실러와 웹훅이 CR 소유 DaemonSet 에 대해
같은 resolver 를 타 노드 집합이 일치한다.

## 4. 테스트 품질 — 뮤테이션 3건으로 실증

주장 대신 회귀를 의도적으로 주입해 테스트가 실제로 잡는지 확인했다(백업 후 정확히 복원,
`git diff --numstat` 으로 검증).

| 뮤테이션 | 결과 |
|---|---|
| 스킵 조건을 예전 `findControllerOwnerReference(...).isPresent()` 로 되돌림 | `6 tests completed, 2 failed` — 신규 2건만 실패 = **기존 4건이 이번 변경에 agnostic** 함도 동시 증명 |
| `catch → deletePod` 재도입 | `nodesResolutionFailure_doesNotDeletePod` FAILED |
| `getRootController(V1Pod)` 가 파드 자신 반환 | `podOwnerNotResolvable_returnsEmpty` FAILED |

- `PodReviewHandlerTest` 의 CR 소유 DaemonSet 케이스는 mock 이 아니라 실제 배선
  (`RootWorkloadControllerResolver` → `DaemonSetWorkloadControllerNodesResolver`)을 조립한다.
  Project 없는 네임스페이스로 두어 root 해석이 끊기면 toleration 이 0개가 되므로 판별력이 있다.
- `PodReconcilerTest` 의 `Mockito.mockConstruction(CoreV1Api.class, RETURNS_DEEP_STUBS)` 는
  `createReconciler()` 를 try 블록 안에서 호출해 `getFirst()` 가 항상 실제 인스턴스를 반환하고,
  삭제 **양성** 케이스로 관측 경로 자체를 검증하므로 "호출 없음" 단정이 공허하지 않다.
  (`PodReconciler` 가 생성자에서 `new CoreV1Api(...)` 하므로 택한 방식 — 테스트 편의를 위한 메인 코드
  리팩터링을 피했다.)

## 5. QA 가 직접 수정한 이슈 (1건, 심각도 낮음)

`WorkloadControllerReconcilerTest` 의 informer-미등록 테스트가 전제를 **Mockito 미스텁 기본값(null)** 에
암묵적으로 의존하고 있었다. `factory` 를 필드로 올려
`when(this.factory.getExistingSharedIndexInformer(V1StatefulSet.class)).thenReturn(null)` 로 전제를
명시했다. 기존 4개 테스트 본문은 여전히 무변경이고 재빌드 후 256/0/0.

## 6. 미해결 / 후속

FAIL 사유는 없다. 코드로 해결 불가한 항목만 남는다.

1. **Spring 컨텍스트 로딩 테스트 부재** — `ApplicationTests` 가 빈 클래스라
   `createController(supportedTypes)` 배선의 런타임 DI 검증이 없다. 정적으로는 전수 확인 완료
   (C2). 기동 로그로 워크로드 컨트롤러 6개 등록을 확인해야 한다.
2. **스테이징 실측** — `trident-node-linux` pm-toleration 6개 주입 + `metadata.generation` 안정화 +
   `trident-operator` 재기록 여부. 설계 결정 6 의 선행 조건이다.
3. **설계 §5 체크리스트의 사각지대** — 결정 1로 새 쓰기 대상이 되는 **AIPub 자체 CR 소유 5건**
   (Operation×2 Deployment, Workspace×3 StatefulSet, `01-design.md` §1.3)이 스테이징 관측 목록에
   없다. §1.3 이 "writer 가 `OpenAPI-Generator`, aipub-backend 확인 필요" 로 남긴 항목이므로 이들의
   generation 안정화도 함께 볼 것. (파드 레벨 toleration 값 자체는 C1 에서 불변 확인.)
4. **운영 회귀 체크** — DaemonSet 15개 pm-tol 6개 유지 / cilium 2개 0 유지 / `fluent-bit` 의 coaster
   `evict-ds` term 보존.

### 기타 관찰 (FAIL 아님)

- 리컨실러와 root resolver 의 **"캐시 미존재" 처리 비대칭**: 리컨실러는 owner 객체를 조회하지 않고
  타입만 보므로 캐시 미존재를 구분하지 않는다. 결과 차이를 만들지 않음을 확인했다 — DaemonSet 은
  다른 지원 타입의 자식이 될 수 없고, ReplicaSet/Deployment 는 둘 다 `Default` resolver 를 타
  노드 집합이 동일하다.
- 테스트 커버리지 공백 3건: 웹훅 레벨 owner-미해석 · lenient project-managed 노드 ·
  `processCaseThatNotProjectManagedNode` 삭제 분기.

---

# 델타 검증 — 범위 축소 + 배선 테스트

1차 구현(`ffc9204`) PASS 이후 추가된 두 변경에 대한 검증. 종합 판정 **PASS**. 단 D 항목에서
**실제 빈틈을 찾아 닫았다.**

## 7. 빌드 / 테스트

`./gradlew clean build` → **BUILD SUCCESSFUL**, **265건 / 실패 0 / 에러 0**. 메인 코드는 검증 과정에서
손대지 않았다(`git diff --numstat ffc9204 -- src/main/java` 검증 전후 동일).

## 8. 축소 정확성 (A) — 전부 PASS

| # | 항목 | 근거 |
|---|---|---|
| A1 | 네 분기 전부 코드 확인 + 각각 대응 테스트 존재 | ownerRef 없음→진행 / 지원+informer→스킵 / 미지원·informer미등록+`true`→진행 / 미지원+`false`→스킵 |
| A2 | 기본값 `false`, 오버라이드는 `DaemonSetWorkloadControllerFactory` **한 곳뿐**(grep 전수) | `typeKey()` 고정이 작동함을 뮤테이션으로 실증 — 오버라이드를 Deployment 로 옮기면 FAILED, 제거해도 FAILED |
| A3 | 리컨실러에 타입 지식 **0건** | `V1DaemonSet`/`instanceof`/`DAEMON_SET` 미검출. `shouldSkipByControllerOwner`(정책) / `isSupportedControllerType`(타입 대조) 분리 적절 |
| A4 | 결정 2·3·4·7 네 파일 `git diff ffc9204` **빈 출력** | 파드 레벨은 모든 kind 에 대해 고쳐진 상태 유지 |

## 9. 회귀 (B) — PASS

- **B1**: base 대비 `34+/4−`. 삭제 4줄 전수 확인 → 전부 `setUp()` 의 `factory` 필드 승격분. 기존 4개
  테스트 본문·어서션 **0줄 변경**.
- **B2**: Deployment 케이스의 기대값 반전은 **옳다.** 세 가지로 "느슨하게 고친 것" 과 구별했다 —
  ① 하네스가 `V1Deployment` + 플래그 `false` 로 운영과 일치 ② 1차의 `true` 어서션은 삭제가 아니라
  `WorkloadControllerReconcilerUnsupportedOwnerTest` 로 **이전**돼 커버리지가 2분기 → 4분기로 증가
  ③ 스킵 로직 반전 뮤테이션 시 양쪽 파일 3건이 함께 FAILED.
- **B3**: `ReconciliationService` 무변경(0 라인).

## 10. 배선 테스트 건전성 (C)

- **C1 네트워크 없음 — PASS, 3중 확인.** 코드 경로(`startAllRegisteredInformers()` 호출자는
  `OwnedObjectInformerManager:90` 뿐) · 업스트림 바이트코드(`setReadTimeout(0)` 요구가 실재) ·
  **런타임 스레드 실측**(informer/reflector/OkHttp 스레드 0개, 6건 34ms).
- **C1-b 스레드 정리 — 주의사항으로 기록.** 일회용 프로브 실측: 팩토리 6개 생성 시 non-daemon 스레드
  6개 생성, `createController()` 는 추가 0개, `shutdown()` 후 대기 루프는 종료되지만 **스레드는 회수되지
  않는다**(`DefaultDelayingQueue` 가 자기 executor 를 소유하고 `shutDown()` 이 그것을 종료하지 않음).
  무해하며 팩토리에 executor 주입 지점이 없어 메인 코드 없이는 개선 불가 — 현 방식이 최선. "대기 루프를
  정리한다" 는 주석이 자원 회수로 읽히므로 **실측대로 정정**했다.
- **C2 잔여물** — 미사용 `import java.util.Map;` 1건 발견·삭제. 바이트코드 검사 잔여 코드·죽은 헬퍼 0건.
- **C3 판별력** — 뮤테이션 7건으로 재확인. 6건 중 5건이 각각 고유 뮤테이션에서만 깨진다.
  **`daemonSetInformerRegistrar` `@Bean` 삭제는 잡지 못한다** — 테스트가 registrar 목록을 직접 구성하기
  때문. 기동 시 `::hasSynced` NPE 로 크게 드러나므로 닫지 않고 남겼다(바이트코드 테스트를 삭제할 때와
  같은 기준).

## 11. 구현자 자기보고 한계 (D) — **빈틈 실재. 닫았다** (심각도 중간)

두 테스트의 조합은 같은 보증을 주지 **않았다.** (a) 정책 훅 고정 테스트는 팩토리 반환값만,
(b) 리컨실러 단위 테스트는 테스트가 직접 넘긴 리터럴만 본다 — **전달 구간이 무관측**이었다.

**뮤테이션으로 실증**: `createReconciler` 호출부를 `reconcilesWhenOwnedByUnsupportedType(),` →
`false,` 로 바꿨다. 이는 운영에서 CR 소유 DaemonSet 이 조용히 toleration 0개로 회귀하는 상태
(= 이 티켓의 원래 결함 재발)인데 **전체 스위트가 BUILD SUCCESSFUL** 이었다.

메인 코드 변경 없이 닫았다 — 업스트림 바이트코드에서 ① `DefaultControllerBuilder.build()` 가 리컨실러를
감싸지 않고 그대로 넘긴다 ② `DefaultController.getReconciler()` 가 public 임을 확인해, 실제 빌드된
컨트롤러에서 운영에 도는 리컨실러를 꺼내 팩토리 반환값과 대조하는 테스트를 추가했다(리컨실러 필드는
`private` 유지, 테스트에서만 리플렉션). **재실행 시 이 테스트 1건 FAILED** — 닫혔음을 확인.

행동 기반 검증은 채택하지 않았다. 플래그 `true`/`false` 가 둘 다 `Result(false)` 를 반환해(노드 0개 →
`Set.copyOf` 일치 → API 호출 없음) 구별 불가하고, 구별하려면 실제 PATCH 를 유발해 "네트워크 없음"
성질을 깨야 한다. 부수적으로 컨트롤러 빌드를 `@BeforeAll` 로 옮겨 팩토리당 1회만 빌드하도록 정리했다
(중복 watch 등록 방지, 기존 어서션 전부 유지).

## 12. 멱등성 관점의 개선

축소로 `Workspace` 소유 StatefulSet 3건이 쓰기 대상에서 빠져 **aipub-backend 와의 update 루프
가능성이 제거**됐다. 1차 보고서 §6 관찰이 지적한 사각지대가 코드로 해소된 셈이다.

## 13. 델타 이후 미해결

1. **Spring DI 미검증** — 배선 테스트의 명시적 범위 밖. `@Bean` registrar 누락 회귀도 이 범주.
   기동 로그 확인으로 대체한다.
2. **스테이징 실측** — 회귀 체크에 추가된 "CR 소유 Deployment/StatefulSet 6건 템플릿 불변 ·
   Workspace 파드 미재시작" 이 축소가 지켜졌는지 보는 핵심 지표다.
3. 실제 DaemonSet 팩토리 → 리컨실러 조립은 §11 로 닫혔으나, `ControllerManager.run()` 이후의 런타임
   경로(watch 연결, 캐시 동기화, 워커 루프)는 여전히 의도적 미검증.
