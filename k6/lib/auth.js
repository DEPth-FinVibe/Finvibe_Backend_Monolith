import { pickToken } from './data.js';

export function buildAuthHeaders(extraHeaders = {}) {
	const token = pickToken();
	if (!token) {
		return extraHeaders;
	}
	return {
		Authorization: `Bearer ${token}`,
		...extraHeaders,
	};
}

export function requireAuthHeaders(extraHeaders = {}) {
	const token = pickToken();
	if (!token) {
		throw new Error('Auth scenario requires at least one token in TOKENS_FILE');
	}
	return {
		Authorization: `Bearer ${token}`,
		...extraHeaders,
	};
}
