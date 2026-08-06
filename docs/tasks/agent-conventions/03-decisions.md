# AI 아키텍처·코드·오류 처리 컨벤션 결정 이력

각 항목은 검토한 대안, 선택, 선택 이유와 트레이드오프를 기록한다.

## D1. 루트 패키지와 공통 영역

### 대안

1. 현재 구조인 `depth.finvibe` + `common` 유지
2. `depth.finvibe.investment` + `shared`로 전체 변경
3. `depth.finvibe`는 유지하고 `common`만 `shared`로 변경

### 선택

**1번: `depth.finvibe` + `common` 유지**

### 이유

- `depth.finvibe`는 특정 도메인이나 현재 배포 구조를 루트 이름에 고정하지 않아, 모놀리스 확장과 향후 서비스 분리에 모두 사용할 수 있다.
- `depth.finvibe.investment`는 현재의 투자 도메인 묶음을 루트에 고정해 향후 wallet, user 등 독립 서비스 분리 시 부자연스러운 이름이 된다.
- `common`을 `shared`로 이름만 변경해도 모듈 결합도는 낮아지지 않는다. MSA 전환 가능성은 공통 영역의 이름보다 공유 범위와 모듈 간 계약에 좌우된다.
- 대규모 package/import 변경 없이 현재 코드와 일치하는 기준을 확립할 수 있다.

### 전제와 트레이드오프

- `common`에는 여러 모듈이 실제로 공유하는 도메인 중립적 기반 타입과 횡단 관심사만 새로 추가한다.
- 기존 `common`의 비즈니스 성격 패키지는 자동으로 정당화하지 않으며, 별도 작업에서 점진적으로 분리한다.
- 모듈 간 domain 객체와 JPA Entity 공유를 피하고, 호출 측 output port와 adapter를 통해 경계를 격리하는 방향을 후속 결정에서 구체화한다.
- MSA로 전환할 때 서비스별 최종 루트 패키지 변경은 발생할 수 있지만, 모듈 경계가 보존되면 이동 범위가 해당 모듈 내부로 제한된다.

## D2. 컨벤션 적용 범위

### 대안

1. 기존 코드 전체를 한 작업에서 즉시 준수하도록 변경
2. 신규·변경 코드에만 적용하고 기존 위반은 유지
3. 전체 준수를 목표로 하되 신규·변경 코드에는 즉시 적용하고 기존 위반은 독립 작업으로 점진 정리

### 선택

**3번: 전체 준수를 목표로 한 단계적 적용**

### 이유

- 기존 코드 전체를 조사한 결과 메인 Java 641개 중 최소 166개 기존 파일이 컨벤션 정리의 직접 영향 후보로 확인됐다.
- `common` 경계만 보더라도 비전역 파일 45개와 소비 파일 92개가 연결되어 있어, 컨벤션 문서 도입과 함께 변경하면 검증·리뷰 범위가 지나치게 커진다.
- 현재 테스트는 6개 파일, 18개 테스트 메서드로 대규모 일괄 리팩터링의 회귀를 보호하기에 부족하다.
- 목표를 낮추는 것이 아니라 구조 변경을 독립적인 gate와 검증 단위로 분리하는 편이 MSA 재분리 가능성을 더 안전하게 확보한다.

### 적용 방식

- 신규 코드는 확정된 컨벤션을 모두 준수한다.
- 기존 코드를 수정할 때는 변경 범위에서 새 위반을 만들지 않고, 가능한 범위에서 해당 규칙을 적용한다.
- 요청과 관계없는 legacy까지 같은 변경에 섞지 않는다.
- 기존 위반은 영향 범위와 검증 방법을 갖춘 별도 task와 브랜치에서 정리한다.
- `common` 부분 정리는 `docs/common-boundary-cleanup` 브랜치의 별도 작업으로 분리했고, `01-overview.md`까지만 확정했다.

### 트레이드오프

- 한동안 목표 컨벤션과 legacy 구조가 공존한다.
- 에이전트 문서에는 목표 규칙과 기존 예외를 구분해 적어야 한다.
- 단계별 작업이 누락되지 않도록 별도 task나 backlog로 추적해야 한다.

## D3. 표현 계층 패키지 이름

### 대안

1. 신규 표준은 `api`로 정하고 기존 `presentation`은 legacy로 유지
2. `api`와 `presentation`을 모두 공식 허용
3. 기존 `presentation` Controller를 현재 작업에서 `api`로 이동

### 선택

**3번: 기존 Controller까지 `api`로 이동해 즉시 통일**

### 이유

- 전체 표현 계층을 `api`로 통일하면 새 클래스를 배치할 때 예외 규칙이 필요 없다.
- 현재 `presentation` 아래에는 discussion과 news의 Controller 네 개만 있어 영향 범위가 작고 참조도 없다.
- HTTP endpoint와 클래스 내용은 유지하고 Java package 경로만 변경하므로 독립적인 대규모 히스토리가 필요한 작업이 아니다.

### 적용 범위

- `modules/discussion/presentation/external` -> `modules/discussion/api/external`
- `modules/news/presentation/external` -> `modules/news/api/external`
- Controller 네 개의 package 선언 변경
- 컴파일을 통한 component scan과 참조 검증

## D4. `common` 최소화 적용 방식

### 대안

1. 현재 `common` 구조를 공식 기준으로 그대로 인정
2. 기존 구조를 legacy 예외로 유지하고 신규 유입만 제한
3. 공통 계약·오류·기반 타입은 남기고 비즈니스 domain과 모듈 전용 infra를 별도 작업에서 부분 정리

### 선택

**3번: `common` 경계를 별도 작업에서 부분 정리**

### 이유

- 이벤트 계약과 전역 오류 타입처럼 여러 모듈이 실제로 공유할 수 있는 요소까지 해체할 필요는 없다.
- 특정 domain, JPA repository, Kafka producer와 같은 구현이 `common`에 남으면 향후 서비스 분리 시 소유권과 이동 범위가 불명확해진다.
- 잠재 영향 범위가 최대 128개 기존 파일이므로 AI 컨벤션 문서 도입과 같은 변경으로 묶지 않는다.

### 적용 방식

- 현재 컨벤션 문서에는 `common`의 목표 원칙과 기존 구조의 점진 정리 방침을 함께 적는다.
- 현재 브랜치에서는 `common` Java 코드를 이동하지 않는다.
- 별도 `docs/common-boundary-cleanup` 브랜치를 `main`에서 생성했다.
- 별도 작업의 `01-overview.md`를 커밋 `2f38443`으로 확정했다.
- 후속 plan과 구현은 해당 작업에서 사용자 gate를 거쳐 진행한다.

## D5. 모듈 간 호출 계약

### 대안

1. 호출 모듈이 대상 모듈의 application port를 직접 의존
2. 호출 모듈이 output port를 소유하고 infra adapter가 대상 모듈의 input port 또는 내부 transport 계약을 호출
3. 모든 모듈 간 통신을 비동기 이벤트로 전환

### 선택

**2번: caller-owned output port + infra adapter**

### 이유

- 현재 asset, trade, news 등 주요 교차 모듈 호출이 이미 이 구조를 사용한다.
- 호출 모듈의 application은 자신이 필요한 기능만 output port로 정의하므로 대상 모듈 구현을 모른다.
- 모놀리스에서는 infra adapter가 대상 모듈의 `application/port/in`을 직접 호출해 불필요한 HTTP 통신과 중복 interface를 피할 수 있다.
- MSA 전환 시 application 코드는 유지하고 infra adapter만 HTTP, gRPC 또는 messaging client로 교체할 수 있다.
- 즉시 결과가 필요한 조회·검증은 동기 port를 사용하고, 상태 전파·알림은 이벤트를 사용할 수 있다.

### 세부 규칙

- 호출 모듈의 `application/port/out`은 호출 모듈이 소유한 원시 타입 또는 contract DTO를 반환한다.
- 호출 모듈의 application port가 대상 모듈의 domain이나 DTO를 import하지 않는다.
- in-process adapter는 대상 모듈의 `application/port/in`을 호출할 수 있다.
- `api/external`은 클라이언트에 공개하는 HTTP API다.
- `api/internal`은 서비스 간 통신에 공개하는 내부 HTTP API다.
- `api/internal`과 `api/external`은 전송 계층의 공개 범위를 나타내며 application port와 다른 개념이다.
- `application/port/in`은 application이 제공하는 유스케이스 계약이고, `application/port/out`은 application이 외부에 요구하는 의존 계약이다.
- 모놀리스의 in-process 호출에 `api/internal` HTTP 경로를 강제하지 않는다. MSA 분리 후 infra adapter가 대상 서비스의 `api/internal` endpoint를 호출한다.
- 비동기 처리가 자연스러운 상태 변경은 event contract를 사용한다.

### 현재 확인된 legacy

- `asset.application.port.out.WalletClient`가 `wallet.dto.WalletDto`를 반환한다.
- `news.application.port.out.NewsDiscussionPort`가 discussion DTO를 노출한다.
- 일부 application과 Controller가 다른 모듈의 domain enum을 직접 import한다.

이 항목은 D2의 단계적 적용 원칙에 따라 별도 경계 정리 작업으로 다룬다. 현재 컨벤션 문서 작업에서 기능 코드를 함께 변경하지 않는다.

## D6. Application의 프레임워크 의존 범위

### 대안

1. Spring과 Micrometer를 포함한 모든 프레임워크 의존을 port로 추상화
2. Application 구성·트랜잭션·로깅·관측성은 허용하고 기능 I/O는 port로 역전
3. Application의 기술 의존을 제한하지 않음

### 선택

**2번: Spring·관측성 허용, 기능 I/O는 port로 분리**

### 허용

- `@Service`, `@Component` 등 application component 선언
- `@Transactional`을 통한 유스케이스 트랜잭션 경계
- SLF4J 로깅
- Micrometer metric 기록
- 입력 검증과 application orchestration에 필요한 Spring 기반 지원

### port로 분리

- DB와 Spring Data repository
- Redis
- Kafka producer와 외부 messaging
- 외부 HTTP API와 SDK
- 파일·object storage 등 외부 I/O

### 판단 기준

- 프레임워크 타입이 비즈니스 규칙과 유스케이스 계약을 오염시키지 않아야 한다.
- 관측 코드가 비즈니스 흐름을 크게 방해하거나 단위 테스트를 어렵게 만들면 별도 port/adapter 추출을 검토한다.
- application에서 특정 infra 구현 클래스를 직접 import하지 않는다.

### 현재 확인된 legacy

- `asset.application.RedisIndexSyncService`가 `asset.infra.redis` 구현 세 개를 직접 import한다.
- `news.application.port.in.NewsQueryUseCase`가 Spring Data의 `Page`, `Pageable`을 유스케이스 계약에 노출한다.

이 항목은 D2에 따라 별도 작업 또는 해당 코드의 다음 변경 시 정리한다.

## D7. 포맷과 들여쓰기

### 대안

1. 탭 사용
2. 공백 4칸 사용
3. formatter 도입 전까지 주변 파일 스타일 유지

### 선택

**2번: 공백 4칸**

### 적용 방식

- 신규 Java 파일은 공백 4칸 들여쓰기를 사용한다.
- 기존 Java 파일을 수정하면 직접 변경한 코드 블록을 공백 4칸 기준으로 작성한다.
- IntelliJ IDEA의 Reformat 결과를 기본으로 삼고 공백으로 열을 수동 정렬하지 않는다.
- 요청과 관계없는 기존 코드 전체를 같은 diff에서 재포맷하지 않는다.
- 기존 탭·2칸 들여쓰기는 해당 코드가 실제 변경 대상이 되거나 별도 formatter 작업을 할 때 점진적으로 정리한다.

### 트레이드오프

- 전체 formatter를 도입하기 전까지 한 파일 안에 legacy 스타일과 신규 스타일이 잠시 공존할 수 있다.
- 개발 환경에 따라 Reformat 결과가 달라질 수 있으므로 장기적으로 `.editorconfig` 또는 formatter 도입을 별도 검토한다.

## D8. 테스트 컨벤션 적용 범위

### 대안

1. 기존 테스트까지 현재 작업에서 형식 통일
2. 신규·변경 테스트부터 적용
3. JUnit 5·AssertJ와 예외 코드 검증만 필수화하고 나머지는 권장

### 선택

**1번: 기존 테스트까지 형식 통일**

### 이유

- 현재 테스트는 6개 파일, 18개 메서드로 범위가 작아 한 번에 기준을 맞출 수 있다.
- 테스트 로직과 검증값을 바꾸지 않고 이름과 구조만 정리할 수 있다.
- 이후 추가되는 테스트가 참고할 일관된 예제를 제공한다.

### 적용 방식

- JUnit 5와 기존 AssertJ·Mockito 사용을 유지한다.
- 모든 테스트에 행위를 설명하는 한국어 `@DisplayName`을 추가한다.
- 테스트 메서드는 `대상_조건_결과`를 읽을 수 있는 이름으로 변경한다.
- 준비·실행·검증이 존재하는 테스트는 `// given`, `// when`, `// then`으로 구분한다.
- 예외 테스트는 예외 타입뿐 아니라 `DomainException.errorCode`도 검증한다.
- 테스트 Java 파일의 들여쓰기는 D7에 따라 공백 4칸으로 통일한다.
- 테스트 동작, 검증값과 커버리지는 이 결정에서 변경하지 않는다.

## D9. 오류 메시지 계약

### 대안

1. 현재 `message` 계약 유지
2. `messageKey` 기반으로 즉시 전환
3. `messageKey`를 추가하고 기존 `message`를 함께 제공하는 호환 전환

### 선택

**1번: 현재 `message` 계약 유지**

### 이유

- 현재 `DomainErrorCode`와 모든 모듈 ErrorCode가 `getCode()`, `getMessage()` 계약을 사용한다.
- 외부 `ErrorResponse`는 `status`, `code`, `message`, 선택적 `fieldErrors`를 반환한다.
- i18n 전환 요구가 없는 상태에서 `messageKey`를 도입하면 외부 API와 클라이언트 변경만 증가한다.
- 오류의 안정적인 식별자는 `code`로 유지할 수 있다.

### 적용 방식

- 컨벤션 문서는 현재 구현과 같은 `message` 계약을 기준으로 작성한다.
- `messageKey`를 현재 필드인 것처럼 기술하지 않는다.
- 향후 i18n 요구가 생기면 클라이언트 호환성과 전환 기간을 포함한 별도 작업으로 계획한다.

## D10. 운영 작업의 Issue·PR 추적 방식

### 대안

1. 로컬 작업 문서와 GitHub Issue·PR을 역할별로 연결
2. GitHub Issue·PR을 유일한 작업 기록으로 사용
3. 로컬 작업 문서를 중심으로 두고 Issue·PR에는 링크만 작성

### 선택

**1번: 로컬 작업 문서와 GitHub Issue·PR 연계**

### 이유

- GitHub Issue는 운영 작업의 등록, 상태 추적과 완료 조건 공유에 적합하다.
- PR은 실제 diff, 검증 결과, 운영 영향과 리뷰를 코드 변경에 연결할 수 있다.
- `docs/tasks/*`는 상세 조사, plan, 사용자 결정 이력과 최종 결과를 저장소 안에 보존하므로 AI가 필요한 문서만 점진적으로 읽을 수 있다.
- GitHub 기록과 로컬 문서의 역할을 나누면 같은 내용을 전부 복제하지 않으면서 운영 추적성과 progressive disclosure를 함께 유지할 수 있다.

### 적용 원칙

- 앞으로 저장소를 변경하는 모든 작업은 GitHub Issue와 PR을 반드시 가진다.
- Issue·PR과 `docs/tasks/<task-name>`은 서로 식별 가능한 링크 또는 번호로 연결한다.
- Issue는 문제·범위·완료 조건을, 로컬 문서는 상세 분석·plan·결정 이력을, PR은 실제 변경·검증·운영 영향을 중심으로 기록한다.
- 구체적인 Issue 양식, PR 양식, 생성 시점과 병합 조건은 후속 결정을 통해 확정한다.
- 현재 진행 중인 agent conventions와 common boundary 작업의 소급 적용 방식도 후속 결정에서 정한다.

## D11. Issue 작성 양식

### 대안

1. 작업 유형별 GitHub Issue Form 사용
2. 모든 작업이 단일 Markdown Issue template 사용
3. 저장소 template 없이 workflow 문서의 작성 규칙만 사용

### 초기 선택

**1번: 작업 유형별 GitHub Issue Form 사용**

### 구성

- `.github/ISSUE_TEMPLATE/change.yml`: 기능, 리팩터링, 문서, 인프라 등 계획된 변경
- `.github/ISSUE_TEMPLATE/bug.yml`: 장애와 버그 수정
- `.github/ISSUE_TEMPLATE/config.yml`: 빈 Issue 생성을 막고 정해진 form 사용을 유도

### 공통 필수 내용

- 배경과 현재 문제
- 목표
- 변경 범위
- 제외 범위
- 검증 가능한 완료 조건
- 운영 영향과 위험
- 관련 `docs/tasks/<task-name>` 경로

버그 form에는 재현 절차, 기대 동작, 실제 동작과 영향 범위를 추가한다.

### 이유와 트레이드오프

- GitHub가 필수 필드 누락을 제출 전에 검증할 수 있다.
- 계획 작업과 버그의 문맥이 달라 각각 필요한 정보를 받을 수 있다.
- Markdown template보다 초기 설정은 많지만 운영 Issue의 품질 편차를 줄일 수 있다.
- 지나치게 세분화된 form을 만들지 않고 두 가지 유형으로 시작하며, 실제 사용 중 누락이 확인될 때만 확장한다.

### 정정 이력

2026-08-06에 Issue와 PR의 양식 수를 다시 검토했다. 모든 작업이 동일한 workflow와 로컬 작업 문서를 사용하므로 현재 규모에서 change와 bug form을 분리할 실익보다 관리 비용이 크다고 판단했다.

### 최종 선택

**단일 GitHub Issue Form 사용**

### 최종 구성

- `.github/ISSUE_TEMPLATE/work.yml`: 모든 변경 작업에 사용하는 단일 form
- `.github/ISSUE_TEMPLATE/config.yml`: 빈 Issue 생성을 막고 정해진 form 사용을 유도
- 작업 유형 dropdown: 기능, 버그, 리팩터링, 인프라, 문서
- 공통 필수 항목: 배경과 문제, 목표, 변경 범위, 제외 범위, 완료 조건, 운영 영향과 위험, 관련 작업 문서
- 버그 재현 절차, 기대 동작과 실제 동작은 해당하는 경우 작성하는 선택 항목

Issue 유형별 운영 절차가 실제로 달라질 때만 별도 form 분리를 다시 검토한다.

## D12. PR 작성 양식

### 대안

1. 모든 변경에 단일 구조화 PR template 사용
2. 기능, 버그, 리팩터링 등 작업 유형별 PR template 사용
3. 저장소 template 없이 workflow 문서의 작성 규칙만 사용

### 선택

**1번: 단일 구조화 PR template 사용**

### 필수 내용

- 연결 Issue: `Closes #<issue-number>`
- 변경 목적과 요약
- 주요 변경 사항
- 관련 `docs/tasks/<task-name>` 경로
- 테스트 명령과 결과
- API, DB, Kafka, 설정, 배포 영향
- 롤백 방법
- 리뷰 집중 지점
- 운영·품질 체크리스트

해당하지 않는 필드는 삭제하지 않고 `해당 없음`과 간단한 이유를 적는다.

### 이유와 트레이드오프

- 작업 유형이 달라도 PR 시점에는 실제 변경, 검증, 운영 영향과 롤백 가능성을 공통으로 확인해야 한다.
- 하나의 template만 유지하므로 작성자와 리뷰어가 같은 위치에서 정보를 찾을 수 있다.
- 단순 문서 변경에는 일부 항목이 불필요할 수 있지만 `해당 없음`을 명시해 검토 누락과 의도적인 비적용을 구분한다.

## D13. Issue·브랜치·PR 생성 시점

### 대안

1. Issue를 먼저 만들고 Plan 승인 후 Draft PR 생성
2. Overview 승인 후 Issue와 브랜치를 만들고 Plan 승인 후 Draft PR 생성
3. Issue를 먼저 만들고 모든 작업 완료 후 PR 생성

### 사용자 조정안

**2번의 Issue 생성 시점과 작업 완료 후 PR 생성을 결합한다.**

### 확정 순서

```text
읽기 전용 사전 조사
→ Overview 임시 초안 작성
→ 사용자 Overview 승인
→ GitHub Issue 생성
→ 최신 main에서 <type>/<issue-number>-<slug> 브랜치 생성
→ 정식 01-overview.md 작성·커밋
→ Plan 승인·커밋
→ 결정·구현·검증
→ Result 작성·전체 검증·커밋
→ 브랜치 push
→ 일반 PR 생성
```

### 세부 규칙

- Overview 승인 전에는 저장소에 task 파일이나 branch를 만들지 않는다.
- 임시 Overview는 대화 또는 저장소 밖의 임시 파일로 검토한다.
- 승인된 Overview를 바탕으로 Issue를 작성해 문제와 범위가 빈 상태로 등록되지 않게 한다.
- 브랜치 이름에는 Issue 번호를 포함한다.
- Draft PR은 사용하지 않는다.
- PR은 구현, 검증과 `04-result.md`까지 끝난 뒤 생성한다.
- 진행 중 추적은 Issue와 로컬 task 문서가 담당한다.

### 트레이드오프

- GitHub에서는 작업 완료 전 diff를 PR로 볼 수 없다.
- 대신 미완성 PR을 운영 목록에 노출하지 않고, 승인된 문제 정의를 기반으로 Issue를 만들 수 있다.

## D14. PR 생성 이후 책임 범위

### 대안

1. CI 통과 후 작성자가 직접 병합
2. 리뷰어 1명 승인 후 병합
3. 리뷰어 2명 또는 CODEOWNERS 승인 후 병합

### 사용자 결정

**에이전트는 PR 생성까지만 담당하고 병합 정책에는 관여하지 않는다.**

### 적용 범위

- 에이전트는 작업 결과와 검증 내용을 단일 PR template에 맞춰 작성한다.
- base가 `main`이고 관련 Issue를 종료하도록 연결했는지 확인한다.
- 원격 브랜치를 push하고 PR URL을 사용자에게 전달하면 해당 작업의 GitHub 수행 범위가 끝난다.
- PR 승인, 병합 시점, 병합 방식, branch protection과 병합 후 브랜치 삭제는 사용자가 결정한다.
- 에이전트는 명시적인 추가 요청이 없는 한 PR을 직접 병합하거나 닫지 않는다.

## D15. 기존 진행 작업의 소급 적용

### 대안

1. agent conventions와 common boundary 작업 모두 Issue·PR을 소급 생성
2. agent conventions에만 소급 적용하고 common boundary는 기존 방식으로 완료
3. 두 기존 작업에는 적용하지 않고 새 workflow 반영 후 시작하는 작업부터 적용

### 선택

**3번: 기존 작업에는 소급 적용하지 않음**

### 적용 범위

- 현재 `docs/agent-conventions` 작업은 기존 절차로 완료한다.
- 현재 `docs/common-boundary-cleanup` 작업도 생성 당시 절차를 유지한다.
- Issue·PR 필수 규칙은 이번 workflow 변경이 `main`에 반영된 뒤 새로 시작하는 변경 작업부터 적용한다.
- 기존 두 작업에 Issue나 PR이 없다는 이유로 완료 상태를 되돌리거나 브랜치 이름을 변경하지 않는다.
- 이후 시작하는 작업에는 문서, 설정과 작은 수정도 예외 없이 Issue와 PR을 생성한다.

### 트레이드오프

- 기존 두 작업은 새 운영 추적 규칙의 예외로 남는다.
- 대신 진행 중인 브랜치와 문서 이력을 인위적으로 바꾸지 않고 명확한 적용 기준 시점을 둔다.
