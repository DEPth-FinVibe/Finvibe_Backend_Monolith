import { Counter, Rate, Trend } from 'k6/metrics';

export const wsHotkeyConnectRate = new Rate('ws_hotkey_connect_rate');
export const wsHotkeyAuthRate = new Rate('ws_hotkey_auth_rate');

export const wsHotkeySubscribeAckLatency = new Trend('ws_hotkey_subscribe_ack_latency_ms', true);
export const wsHotkeyInitialSnapshotLatency = new Trend('ws_hotkey_initial_snapshot_latency_ms', true);

export const wsHotkeyConnectFailCount = new Counter('ws_hotkey_connect_fail_count');
export const wsHotkeyAuthFailCount = new Counter('ws_hotkey_auth_fail_count');
export const wsHotkeySubscribeFailCount = new Counter('ws_hotkey_subscribe_fail_count');
export const wsHotkeyRejectedTopicCount = new Counter('ws_hotkey_rejected_topic_count');
export const wsHotkeySnapshotMissCount = new Counter('ws_hotkey_snapshot_miss_count');
export const wsHotkeyDisconnectCount = new Counter('ws_hotkey_disconnect_count');
