# 로그인 기기 관리 + RTR + 원격 로그아웃 설계

## Summary
- 이 서비스는 `로그인/refresh/세션 조회/세션 무효화`의 소스 오브 트루스를 맡고, 일반 API 요청의 인증 판정은 API Gateway가 수행한다.
- 세션 단위는 `TokenFamily`로 정의한다. 로그인 1회마다 새 `TokenFamily`가 생성되고, 다중 기기를 허용한다.
- `AccessToken`과 `RefreshToken` 모두 `tokenFamilyId`를 claim으로 가진다. Gateway는 일반 요청에서 `tokenFamilyId` 상태를 확인해 차단하고, 서비스는 로그인/refresh/강제 로그아웃 시 상태를 변경한다.
- 기존 `user당 refresh token 1개` Redis 구조는 폐기하고, `TokenFamily + RefreshToken rotation 상태`를 저장하는 구조로 바꾼다.
- 기존 `/auth/logout`는 현재 기기만 로그아웃하도록 의미를 바꾸고, 기기 목록 조회와 특정 기기 원격 로그아웃 API를 추가한다.

## Key Changes
### 1. 세션/토큰 모델 재정의
- `TokenFamily` 도메인 추가:
  - 필드: `tokenFamilyId`, `userId`, `status(ACTIVE/INVALIDATED/EXPIRED/REUSED_DETECTED)`, `currentRefreshTokenHash`, `refreshTokenVersion` 또는 `jti`, `expiresAt`, `lastUsedAt`, `createdAt`
  - 기기 메타: `ipAddress`, `countryCode/region/city` 또는 표시용 location 문자열, `userAgentRaw`, `browserName`, `osName`
- `RefreshToken` 값 객체는 단순 `userId+token` 대신 `tokenFamilyId`, `tokenHash`, `jti`, `expiresAt` 관점으로 변경한다.
- JWT claim 확장:
  - 공통: `sub=userId`, `role`, `tokenFamilyId`, `tokenType(access|refresh)`
  - refresh 전용: `jti` 또는 rotation 검사용 버전
- 만료 정책:
  - Access Token은 현행 짧은 TTL 유지
  - TokenFamily는 30일 sliding expiration
  - refresh 성공 시 family `expiresAt`를 현재 시점 + 30일로 갱신
  - refresh token 자체 만료 시각도 family 만료와 맞춰 재발급

### 2. 저장소/캐시 전략
- 영속 저장소는 DB에 `token_families` 테이블로 둔다.
  - 이유: 기기 목록 조회, 감사 추적, 운영성, 강제 로그아웃 이력 관리에 적합
- Redis는 Gateway 판정용 캐시 + refresh hot path 가속에 사용한다.
- Gateway 구현 기준 Redis 저장 구조는 `TokenFamily별 Hash`로 맞춘다.
- 권장 키 구조:
  - `auth:family:{tokenFamilyId}` -> Redis Hash
    - `status`: `ACTIVE/INVALIDATED/EXPIRED/REUSED_DETECTED`
    - `expiresAt`: ISO-8601 UTC timestamp
  - `auth:family:user:{userId}` -> 사용자 세션 목록 조회용 보조 인덱스 또는 DB 조회 캐시
  - `auth:refresh:current:{tokenFamilyId}` -> 현재 유효한 refresh token hash/jti
- Gateway의 `RedisTokenFamilyReader`는 최소한 `status`, `expiresAt` 두 필드를 읽는 계약으로 본다.
- 향후 확장 필드가 필요하면 같은 Hash에 `lastUsedAt`, `userId`, `version` 또는 `refreshJti`를 추가할 수 있지만, Gateway 판정의 최소 계약은 유지한다.
- 정합성 원칙:
  - 쓰기 기준은 DB 우선
  - 로그인/refresh/무효화 직후 Redis write-through
  - Gateway miss 시 DB fallback 여부는 게이트웨이 정책에 따르되, 기본은 Redis only + 짧은 복구 경로 권장
- Refresh token은 평문 저장 대신 해시 저장으로 바꾼다.

### 3. 서비스 책임과 흐름
- 로그인:
  - ID/PW 로그인 성공 시 새 `TokenFamily` 생성
  - UA/IP 파싱 후 family에 저장
  - IP 기반 위치는 동기 외부조회로 로그인 지연을 키우지 않도록 기본은 `IP 저장 + 비동기 enrich 또는 로컬 DB 기반 조회`로 둔다
  - access/refresh 발급 후 family 현재 refresh hash 저장
- Refresh Token Rotation:
  - refresh 요청 시 JWT 서명/만료/타입 확인
  - `tokenFamilyId + jti/hash`가 저장된 현재 값과 일치하는지 확인
  - 일치하면 새 access/refresh 발급, family 만료 30일 연장, current refresh hash 교체
  - 불일치하면 재사용 공격으로 간주하고 해당 family를 즉시 `REUSED_DETECTED` 또는 `INVALIDATED` 처리
- 현재 기기 로그아웃:
  - Gateway가 전달하거나 서비스가 refresh/access에서 읽은 `tokenFamilyId` 기준으로 현재 family만 무효화
- 원격 로그아웃:
  - 사용자 세션 목록에서 선택한 `tokenFamilyId`를 무효화
  - 무효화 후 Gateway가 해당 family access token을 즉시 차단
- 전체 로그아웃 API는 이번 범위에서 필수는 아니지만 확장 포인트로 남긴다.
- 일반 보호 API의 사용자 식별 계약:
  - 현재 내부 `JwtArgumentResolver`는 payload만 디코딩하므로 그대로 두면 위험하다
  - Gateway 검증 전제라면 서비스는 원본 JWT 대신 `X-Authenticated-User-Id`, `X-Authenticated-Role`, `X-Token-Family-Id` 같은 신뢰 헤더를 읽도록 전환
  - 과도기에는 원본 JWT fallback을 허용할 수 있으나 최종 목표는 신뢰 헤더 전용이다

### 4. API/인터페이스 변경
- 기존 변경:
  - `POST /auth/login`: 응답에 토큰 외 현재 세션 식별자 또는 세션 요약 포함 가능
  - `POST /auth/refresh`: 내부적으로 RTR 수행
  - `POST /auth/logout`: 현재 `tokenFamilyId`만 무효화
- 신규 API:
  - `GET /auth/sessions`: 로그인 기기 목록 조회
  - `DELETE /auth/sessions/{tokenFamilyId}`: 특정 기기 원격 로그아웃
- DTO 추가:
  - `SessionResponse`: `tokenFamilyId`, `currentDevice`, `browserName`, `osName`, `location`, `ipAddress(masked)`, `lastUsedAt`, `createdAt`, `status`
- 포트 추가/변경:
  - `TokenFamilyRepository`
  - `DeviceMetadataResolver` 또는 `LoginContextExtractor`
  - `TokenProvider`는 `generateToken(userId, role, tokenFamilyId, refreshJti)` 형태로 확장
  - `AuthCommandUseCase.logout`는 `userId`만이 아니라 현재 family 식별이 가능해야 함

### 5. 성능 측정과 최적화
- 1단계 기준선:
  - 인증이 필요한 대표 API 3~5개를 고정하고, 현행 구조에서 p50/p95/p99, Redis 호출 수, DB 호출 수를 측정
  - `/auth/login`, `/auth/refresh`도 별도 기준선 측정
- 2단계 측정:
  - RTR/기기 조회/원격 로그아웃 추가 후 동일 시나리오 재측정
  - 일반 API 요청은 Gateway에서 `family status` 조회 1회가 추가되는 구조를 기준으로 측정
- 최적화 원칙:
  - 일반 요청은 Redis 1회 조회 이내를 목표로 한다
  - refresh는 Redis 1~2회 + DB 1회 업데이트 수준으로 제한한다
  - 세션 목록 조회만 DB 중심으로 처리하고, 일반 요청 경로에는 DB를 넣지 않는다
  - family 상태 캐시는 TTL보다 명시적 무효화/write-through를 우선한다
- 계측 추가:
  - `auth.login.duration`
  - `auth.refresh.duration`
  - `auth.refresh.result(success|reused|invalid|expired)`
  - `auth.session.invalidate.count`
  - `auth.gateway.family_status.lookup(hit|miss)`는 게이트웨이 쪽에서 측정
- MAU 10만 가정은 큰 부하가 아니다.
  - 활성 refresh token family 10만 건은 Redis/단순 indexed DB 모두 충분히 감당 가능
  - 병목은 데이터량보다 `일반 요청마다 DB 조회를 넣는 설계`에서 나오므로, 핵심 최적화는 캐시 우선과 key 설계다

## Test Plan
- 단위 테스트:
  - 로그인 시 새 TokenFamily 생성
  - refresh 성공 시 refresh token 교체 + family 만료 연장
  - 이전 refresh token 재사용 시 family 무효화
  - 현재 기기 로그아웃 시 해당 family만 비활성화
  - 원격 로그아웃 시 타 family만 비활성화
- 통합 테스트:
  - `/auth/login` -> `/auth/sessions`에 세션 표시
  - 두 기기 로그인 후 한 기기만 원격 로그아웃
  - 로그아웃된 family의 refresh 실패
  - 재사용된 refresh token으로 인한 family 차단
- 성능/시나리오 테스트:
  - 인증 필요한 대표 API의 평균/백분위 응답시간 전후 비교
  - `/auth/refresh` 동시성 테스트
  - 동일 family에 대한 중복 refresh 경쟁 상태 테스트
- 보안 회귀:
  - 위조된 JWT payload만으로 `Requester`가 만들어지지 않도록 사용자 컨텍스트 해석 경로 변경 검증

## Assumptions
- 다중 기기 허용을 기본으로 한다.
- 기기 표시는 `User-Agent 파싱` 기준으로 하고, 사용자 지정 별칭은 넣지 않는다.
- Gateway는 일반 API에서 최종 인증 판정을 수행하지만, 이 서비스는 로그인/refresh/세션 상태의 소스 오브 트루스가 된다.
- 서비스와 Gateway 간 계약은 최종적으로 `검증된 사용자 헤더 + tokenFamilyId 전달`로 정리하는 것이 목표다. 현재 사용자가 선택한 `원본 JWT 그대로 전달`은 과도기 호환 전략으로만 본다.
- 위치 정보는 로그인 지연을 키우지 않기 위해 외부 API 동기 호출보다 `IP 저장 후 비동기 enrich` 또는 로컬 GeoIP 방식이 기본이다.
- 기존 테스트가 사실상 없으므로, 이번 작업에는 인증 시나리오 통합 테스트 세트 구축이 포함된다.
