package depth.finvibe.modules.user.domain;

import java.time.Instant;
import java.util.UUID;

import depth.finvibe.common.user.domain.TimeStampedBaseEntity;
import depth.finvibe.modules.user.domain.enums.TokenFamilyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "token_families", indexes = {
	@Index(name = "idx_token_families_user_id", columnList = "user_id"),
	@Index(name = "idx_token_families_status", columnList = "status"),
	@Index(name = "idx_token_families_expires_at", columnList = "expires_at")
})
public class TokenFamily extends TimeStampedBaseEntity {

	@Id
	@Builder.Default
	private UUID id = UUID.randomUUID();

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TokenFamilyStatus status;

	@Column(name = "current_refresh_token_hash", length = 128)
	private String currentRefreshTokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "last_used_at", nullable = false)
	private Instant lastUsedAt;

	@Column(name = "ip_address", length = 64)
	private String ipAddress;

	@Column(name = "location", length = 255)
	private String location;

	@Column(name = "browser_name", length = 100)
	private String browserName;

	@Column(name = "os_name", length = 100)
	private String osName;

	@Column(name = "user_agent", length = 1000)
	private String userAgent;

	public static TokenFamily create(UUID userId, LoginContext loginContext, Instant now) {
		return TokenFamily.builder()
			.id(UUID.randomUUID())
			.userId(userId)
			.status(TokenFamilyStatus.ACTIVE)
			.lastUsedAt(now)
			.expiresAt(now)
			.ipAddress(loginContext.ipAddress())
			.location(loginContext.location())
			.browserName(loginContext.browserName())
			.osName(loginContext.osName())
			.userAgent(loginContext.userAgent())
			.build();
	}

	public void rotate(String refreshTokenHash, Instant expiresAt, Instant now) {
		this.status = TokenFamilyStatus.ACTIVE;
		this.currentRefreshTokenHash = refreshTokenHash;
		this.expiresAt = expiresAt;
		this.lastUsedAt = now;
	}

	public void invalidate() {
		this.status = TokenFamilyStatus.INVALIDATED;
		this.currentRefreshTokenHash = null;
	}

	public void markRefreshReuseDetected() {
		this.status = TokenFamilyStatus.REUSED_DETECTED;
		this.currentRefreshTokenHash = null;
	}

	public void markExpired() {
		this.status = TokenFamilyStatus.EXPIRED;
		this.currentRefreshTokenHash = null;
	}

	public boolean isAccessibleBy(UUID userId) {
		return this.userId.equals(userId);
	}
}
