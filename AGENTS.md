# Finvibe Monolith Agent Guide

이 문서는 에이전트가 가장 먼저 읽는 짧은 진입점이다. 모든 상세 문서를 미리 읽지 말고, 현재 작업에 필요한 문서만 불러온다.

## Progressive Disclosure

- 질문 답변이나 읽기 전용 조사라면 저장소를 확인한 뒤 바로 답한다.
- 코드, 설정, 스키마, 인프라 또는 문서를 변경하는 작업이라면 구현 전에 [`docs/agent/workflow.md`](docs/agent/workflow.md)를 읽고 따른다.
- 참조 문서가 연결되어 있더라도 현재 작업과 관계없는 문서는 읽지 않는다.
- 상세 규칙은 이 파일에 중복해서 적지 않는다. 기준 문서는 아래 라우팅 표의 파일 하나로 유지한다.

## Context Routing

| 작업 상황 | 읽을 문서 |
|---|---|
| 저장소를 변경하는 모든 작업 | `docs/agent/workflow.md` |
| 패키지 생성, 모듈 경계·port·adapter·영속성 구조 변경 | `docs/agent/architecture-conventions.md` |
| Java 코드 또는 테스트 작성·수정 | `docs/agent/code-conventions.md` |
| 예외, error code, HTTP 오류 응답 변경 | `docs/agent/error-handling-conventions.md` |

현재 작업이 여러 조건에 해당하면 해당 문서만 함께 읽는다.
