import http from 'k6/http';
import { sleep } from 'k6';

import { sharedRuntimeData } from './data.js';
import { assertResponse } from './checks.js';

function buildUrl(path, query = {}) {
	const queryPairs = [];
	Object.entries(query).forEach(([key, value]) => {
		if (value === undefined || value === null || value === '') {
			return;
		}
		if (Array.isArray(value)) {
			value.forEach((item) =>
				queryPairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(item))}`)
			);
			return;
		}
		queryPairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
	});
	if (queryPairs.length === 0) {
		return `${sharedRuntimeData.baseUrl}${path}`;
	}
	return `${sharedRuntimeData.baseUrl}${path}?${queryPairs.join('&')}`;
}

export function getJson(path, { query, headers, tags, expectedStatuses = [200], timeout } = {}) {
	const response = http.get(buildUrl(path, query), {
		headers,
		tags,
		timeout: timeout || __ENV.HTTP_TIMEOUT || '10s',
		responseCallback: http.expectedStatuses(...expectedStatuses),
	});

	assertResponse(response, {
		name: path,
		expectedStatuses,
		tags,
	});

	return response;
}

export function randomThinkTime(minSeconds, maxSeconds) {
	const duration = Math.random() * (maxSeconds - minSeconds) + minSeconds;
	sleep(duration);
}

export function isoAtKstOffset({ days = 0, hours = 0, minutes = 0 } = {}) {
	const now = new Date();
	const shifted = new Date(now.getTime() + (days * 24 + hours) * 60 * 60 * 1000 + minutes * 60 * 1000);
	return shifted.toISOString().slice(0, 19);
}
