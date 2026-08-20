# README 아키텍처 중심 전면 개편 Overview

## 연결 Issue

- Issue: [#4 docs: README 아키텍처 중심 전면 개편](https://github.com/DEPth-FinVibe/Finvibe_Backend_Monolith/issues/4)

## 작업 배경과 실제 문제

현재 README는 서비스 간 흐름을 짧은 텍스트 다이어그램으로만 표현하고 성능 개선 사례를 중심으로 구성되어 있다. 이 때문에 처음 방문한 사람이 다음 내용을 한눈에 파악하기 어렵다.

- Monolith, WebSocket Listener, Profit Worker가 어떤 메시징 경로로 연결되는지
- 각 애플리케이션 서버의 저장소가 어디에 있는지
- 장기 연결 전송과 수익률 계산을 별도 서버로 분리한 이유가 무엇인지
- 각 서버가 어떤 방식으로 수평 확장되는지

## 현재 구현과 확인된 사실

- Monolith는 KIS WebSocket으로 실시간 시세를 수신한다.
- UI용 시세는 Redis Cluster의 Sharded Pub/Sub 채널로 발행되고 WebSocket Listener가 구독하여 클라이언트에 전달한다.
- 수익률 계산용 시세 및 포트폴리오 변경 이벤트는 Kafka 토픽으로 발행되고 Profit Worker가 Consumer Group으로 소비한다.
- Profit Worker는 계산한 포트폴리오·유저 수익률 상태를 Redis에 저장한다.
- Monolith는 상태를 MariaDB, Redis 등 외부 저장소에 두고 분산 락, 스케줄러 락, KIS 구독 소유권 및 credential 할당 기능을 사용한다.
- 배포 Manifest는 WebSocket Listener와 Profit Worker를 각각 여러 replica로 실행하고 있다.

## 해결해야 하는 문제

- GitHub에서 바로 읽을 수 있는 전체 아키텍처 다이어그램이 필요하다.
- 다이어그램과 본문에서 각 저장소로 이동할 수 있어야 한다.
- 각 서비스의 역할과 분리 이유를 짧고 명확하게 설명해야 한다.
- 현재 replica 수를 과장하지 않으면서 설계상 수평 확장 방식을 정확히 전달해야 한다.
- 기존 README 내용을 유지하지 않고 새로운 목적에 맞는 구조로 전면 교체해야 한다.

## 변경 범위

- `README.md` 전면 재작성
- Mermaid 기반 전체 아키텍처 및 데이터 흐름 시각화
- Monolith, WebSocket Listener, Profit Worker, Manifest 저장소 링크 추가
- 서비스별 책임과 분리 이유 설명
- 애플리케이션 서버별 수평 확장 방식 설명
- 최소한의 기술 스택, 로컬 실행 및 관련 문서 안내 구성

## 제외 범위

- 애플리케이션 코드와 런타임 동작 변경
- API, DB 스키마, Kafka 토픽 및 Redis 키 변경
- Kubernetes replica, HPA 또는 인프라 설정 변경
- WebSocket Listener, Profit Worker, Manifest 저장소의 README 변경
- 기존 README의 성능 개선 사례 보존

## 예상 위험과 제약

- Mermaid 문법이나 링크가 GitHub에서 올바르게 렌더링되지 않을 수 있다.
- Redis Pub/Sub과 Kafka의 목적을 혼동하면 서비스 간 통신 구조를 잘못 전달할 수 있다.
- "스케일 아웃 가능"을 현재 자동 확장이 적용된 것으로 오해하게 표현할 수 있다.
- Monolith의 KIS 연결 확장은 외부 API credential 수와 구독 한도의 영향을 받으므로 무제한 확장처럼 표현하면 안 된다.
