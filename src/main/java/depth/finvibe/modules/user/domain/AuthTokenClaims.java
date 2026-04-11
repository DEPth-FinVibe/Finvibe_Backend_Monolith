package depth.finvibe.modules.user.domain;

import java.time.Instant;
import java.util.UUID;

import depth.finvibe.modules.user.domain.enums.AuthTokenType;
import depth.finvibe.modules.user.domain.enums.UserRole;

public record AuthTokenClaims(
	UUID userId,
	UserRole role,
	UUID tokenFamilyId,
	AuthTokenType tokenType,
	UUID tokenId,
	Instant expiresAt
) {
}
