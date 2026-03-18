import { Counter, Gauge, Rate, Trend } from 'k6/metrics';

// 이벤트 ts ~ k6 수신 시각 간격 (ms) — 백프레셔 핵심 지표
export const wsDeliveryLag = new Trend('ws_delivery_lag_ms', true);

// 연결 성공률
export const wsConnectRate = new Rate('ws_connect_rate');

// 인증 성공률
export const wsAuthRate = new Rate('ws_auth_rate');

// 수신한 주기 이벤트 총합
export const wsEventsReceived = new Counter('ws_events_received');

// 연결 실패 수
export const wsConnectFail = new Counter('ws_connect_fail');

// 현재 열린 연결 수
export const wsActiveConnections = new Gauge('ws_active_connections');

// 인증 거부 수 (만료·서명 오류 등으로 서버가 UNAUTHORIZED 에러를 반환한 경우)
export const wsAuthFailCount = new Counter('ws_auth_fail_count');

// 세션 유지 시간 (ms) — 연결 수립부터 종료까지
export const wsSessionDuration = new Trend('ws_session_duration_ms', true);

// 정상 종료율 (에러 없이 클라이언트가 close를 주도한 경우)
export const wsCleanCloseRate = new Rate('ws_clean_close_rate');
