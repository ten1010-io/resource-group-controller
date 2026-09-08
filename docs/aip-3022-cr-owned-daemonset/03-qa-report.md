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
