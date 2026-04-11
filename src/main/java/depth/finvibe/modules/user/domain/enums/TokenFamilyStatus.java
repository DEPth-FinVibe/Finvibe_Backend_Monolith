package depth.finvibe.modules.user.domain.enums;

public enum TokenFamilyStatus {
	ACTIVE,
	INVALIDATED,
	EXPIRED,
	REUSED_DETECTED;

	public boolean isActive() {
		return this == ACTIVE;
	}
}
