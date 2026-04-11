package depth.finvibe.modules.user.domain;

public record LoginContext(
	String ipAddress,
	String location,
	String browserName,
	String osName,
	String userAgent
) {

	public static LoginContext unknown() {
		return new LoginContext(null, null, "Unknown", "Unknown", null);
	}
}
