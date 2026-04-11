package depth.finvibe.modules.user.infra.security;

import org.springframework.stereotype.Component;

import depth.finvibe.modules.user.application.port.out.LoginContextResolver;
import depth.finvibe.modules.user.domain.LoginContext;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class HttpLoginContextResolver implements LoginContextResolver {

	private static final String USER_AGENT_HEADER = "User-Agent";
	private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

	@Override
	public LoginContext resolve(HttpServletRequest request) {
		if (request == null) {
			return LoginContext.unknown();
		}

		String userAgent = trimToNull(request.getHeader(USER_AGENT_HEADER));
		String ipAddress = resolveIpAddress(request);

		return new LoginContext(
			ipAddress,
			null,
			resolveBrowserName(userAgent),
			resolveOsName(userAgent),
			userAgent
		);
	}

	private String resolveIpAddress(HttpServletRequest request) {
		String forwardedFor = trimToNull(request.getHeader(X_FORWARDED_FOR_HEADER));
		if (forwardedFor != null) {
			int separatorIndex = forwardedFor.indexOf(',');
			return separatorIndex >= 0 ? forwardedFor.substring(0, separatorIndex).trim() : forwardedFor;
		}
		return trimToNull(request.getRemoteAddr());
	}

	private String resolveBrowserName(String userAgent) {
		if (userAgent == null) {
			return "Unknown";
		}
		String normalized = userAgent.toLowerCase();
		if (normalized.contains("edg/")) {
			return "Edge";
		}
		if (normalized.contains("chrome/")) {
			return "Chrome";
		}
		if (normalized.contains("safari/") && !normalized.contains("chrome/")) {
			return "Safari";
		}
		if (normalized.contains("firefox/")) {
			return "Firefox";
		}
		return "Unknown";
	}

	private String resolveOsName(String userAgent) {
		if (userAgent == null) {
			return "Unknown";
		}
		String normalized = userAgent.toLowerCase();
		if (normalized.contains("iphone") || normalized.contains("ipad") || normalized.contains("ios")) {
			return "iOS";
		}
		if (normalized.contains("android")) {
			return "Android";
		}
		if (normalized.contains("mac os x")) {
			return "macOS";
		}
		if (normalized.contains("windows")) {
			return "Windows";
		}
		if (normalized.contains("linux")) {
			return "Linux";
		}
		return "Unknown";
	}

	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
