# README 아키텍처 중심 전면 개편 Result

## 연결 Issue

- Issue: [#4 docs: README 아키텍처 중심 전면 개편](https://github.com/DEPth-FinVibe/Finvibe_Backend_Monolith/issues/4)

## 실제 변경 파일과 동작

### `README.md`

- 기존 성능 개선 사례, 관측성, CI/CD 중심 내용을 제거하고 면접관을 위한 Monolith 중심 소개로 전면 교체했다.
- Monolith에서 시작하는 두 실시간 처리 경로를 단일 Mermaid 다이어그램으로 표현했다.
  - UI 시세: Monolith → Redis Cluster Sharded Pub/Sub → WebSocket Listener → Client
  - 수익률 계산: Monolith → Kafka → Profit Worker → Redis Cluster
- 다이어그램 바로 다음에 Monolith, WebSocket Listener, Profit Worker, Manifest 저장소 링크를 배치했다.
- 서비스 분리 이유 다음에 Monolith의 핵심 책임을 API, 트랜잭션, 시장 데이터 수신, 이벤트 발행 순으로 설명했다.
- Monolith, WebSocket Listener, Profit Worker의 수평 확장 단위와 다중 인스턴스 조정 방식을 구분했다.
- 내부 도메인 모듈, 기술 스택과 로컬 실행 방법을 새 구조에 맞게 간결하게 작성했다.

### 작업 문서

- `01-overview.md`: 승인된 문제, 범위와 위험 기록
- `02-plan.md`: 구현 단계, 완료 조건과 대안 기록
- `03-decisions.md`: 사용자 선택과 추천안 적용 근거 기록
- `04-result.md`: 실제 변경과 검증 결과 기록

## 결정 반영 결과

- D1: README 전체에서 Monolith를 중심에 두고 주변 서비스는 분리한 책임의 관점으로 설명했다.
- D2: 단일 다이어그램을 사용하고 저장소 링크를 다이어그램 직후에 배치했다.
- D3: 모든 서버의 설계상 수평 확장 능력과 현재 Manifest의 replica 수를 별도로 명시했다.
- D4: 기존 README의 성능 개선 사례를 유지하지 않았다.

## 수행한 검증과 결과

| 검증 | 결과 |
|---|---|
| `git diff --check origin/main` | 통과 — 공백 오류 없음 |
| Mermaid CLI 11.16.0 렌더링 | 통과 — 실제 SVG/PNG 생성 성공 |
| 렌더링 이미지 육안 확인 | 통과 — 두 이벤트 경로가 Monolith에서 분기되어 왼쪽에서 오른쪽으로 표시됨 |
| `gh repo view`로 저장소 4개 확인 | 통과 — 네 링크 모두 존재하는 public 저장소 |
| 변경 파일 범위 확인 | 통과 — README와 `docs/tasks/readme-architecture-overhaul`만 변경 |
| 기존 README 주요 섹션 제거 확인 | 통과 — 기존 성능 개선·관측성·CI/CD 제목 없음 |

애플리케이션 코드와 설정을 변경하지 않았으므로 Gradle 테스트는 실행하지 않았다.

## 구현 중 확인된 새 사실

- 현재 Manifest의 replica는 Monolith 1개, WebSocket Listener 3개, Profit Worker 3개다.
- 현재 Manifest에는 HPA가 없으므로 자동 확장이 적용된 것처럼 표현하지 않았다.
- Profit Worker의 유효 병렬도는 Kafka 파티션 수, Monolith의 KIS 연결 확장은 증권사 credential과 구독 한도의 영향을 받는다.
- 큰 Mermaid subgraph는 연결선을 불필요하게 교차시켰다. 최종 다이어그램은 중첩 박스를 제거하고 두 처리 경로를 평행하게 배치했다.

## 운영 영향과 롤백

- API, DB, Kafka, Redis, 설정, 배포, 성능과 보안 영향은 없다.
- 문서 표현에 문제가 있으면 이 PR의 README 변경 커밋을 revert하여 이전 README로 복구할 수 있다.
- 데이터와 런타임 호환성 문제는 없다.

## 남은 한계와 후속 과제

- replica 수는 Manifest 변경 시 README와 달라질 수 있으므로 배포 구성 변경 때 함께 갱신해야 한다.
- GitHub 테마와 화면 폭에 따라 Mermaid 색상과 줄바꿈이 다르게 보일 수 있다.
