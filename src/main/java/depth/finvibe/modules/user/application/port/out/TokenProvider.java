package depth.finvibe.modules.user.application.port.out;

import depth.finvibe.modules.user.dto.UserDto;

import java.util.UUID;

import depth.finvibe.modules.user.domain.enums.UserRole;

public interface TokenProvider {
    UserDto.TokenResponse generateToken(UUID userId, UserRole role, UUID tokenFamilyId);

    UserDto.TokenRefreshResponse refreshToken(UUID userId, UserRole role, UUID tokenFamilyId);
}
