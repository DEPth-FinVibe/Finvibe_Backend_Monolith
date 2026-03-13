import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

export const apiFailureCount = new Counter('api_failure_count');
export const apiBusinessFailureCount = new Counter('api_business_failure_count');
export const responseBodySize = new Trend('response_body_size', true);
export const unauthorizedRate = new Rate('unauthorized_rate');
export const notFoundRate = new Rate('not_found_rate');

export function assertResponse(response, context) {
	const ok = check(response, {
		[`status is expected for ${context.name}`]: (res) => context.expectedStatuses.includes(res.status),
	}, context.tags);

	responseBodySize.add((response.body || '').length, context.tags);

	if (!ok) {
		apiFailureCount.add(1, context.tags);
	}
	if (response.status === 401) {
		unauthorizedRate.add(1, context.tags);
	}
	if (response.status === 404) {
		notFoundRate.add(1, context.tags);
		apiBusinessFailureCount.add(1, context.tags);
	}
	return ok;
}
