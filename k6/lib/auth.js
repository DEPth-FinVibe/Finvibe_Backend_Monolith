import http from 'k6/http';

import { pickToken } from './data.js';

function asJson(response) {
	try {
		return response.json();
	} catch (_) {
		return null;
	}
}

export function issueTokensFromCredentials(baseUrl, credentials) {
	if (!Array.isArray(credentials) || credentials.length === 0) {
		throw new Error('No credentials found in TOKENS_FILE. Expected {"credentials":[{"loginId":"...","password":"..."}]}');
	}

	const tokens = [];

	credentials.forEach((credential, index) => {
		const loginId = String(credential?.loginId || '').trim();
		const password = String(credential?.password || '').trim();
		if (!loginId || !password) {
			throw new Error(`Invalid credential at index ${index}. loginId/password are required.`);
		}

		const response = http.post(
			`${baseUrl}/auth/login`,
			JSON.stringify({ loginId, password }),
			{
				headers: { 'Content-Type': 'application/json' },
				timeout: __ENV.HTTP_TIMEOUT || '10s',
				tags: { module: 'auth', flow: 'login_bootstrap', scope: 'auth' },
			}
		);

		if (response.status !== 200) {
			throw new Error(
				`Bootstrap login failed for credential[${index}] (loginId=${loginId}): status=${response.status}, body=${response.body}`
			);
		}

		const body = asJson(response);
		const accessToken = body?.accessToken;
		if (!accessToken || typeof accessToken !== 'string') {
			throw new Error(
				`Bootstrap login response missing accessToken for credential[${index}] (loginId=${loginId}).`
			);
		}

		tokens.push(accessToken);
	});

	return tokens;
}

export function buildAuthHeaders(tokens, extraHeaders = {}) {
	const token = pickToken(tokens);
	if (!token) {
		return extraHeaders;
	}
	return {
		Authorization: `Bearer ${token}`,
		...extraHeaders,
	};
}

export function requireAuthHeaders(tokens, extraHeaders = {}) {
	const token = pickToken(tokens);
	if (!token) {
		throw new Error('Auth scenario requires at least one issued token from login bootstrap');
	}
	return {
		Authorization: `Bearer ${token}`,
		...extraHeaders,
	};
}
