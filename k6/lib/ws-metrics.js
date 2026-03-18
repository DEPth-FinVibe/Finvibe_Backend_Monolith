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
