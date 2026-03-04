package depth.finvibe.modules.user.application.port.in;

import java.util.UUID;

import depth.finvibe.modules.user.dto.UserDto;

public interface AuthCommandUseCase {
    UserDto.SignUpResponse signUp(UserDto.SignUpRequest request);

    UserDto.SignUpResponse oauthSignUp(UserDto.OAuthSignUpRequest request);

    UserDto.TokenResponse login(UserDto.LoginRequest request);

    UserDto.OAuthLoginResponse oauthLogin(UserDto.OAuthLoginRequest request);

    UserDto.TokenRefreshResponse refreshToken(UserDto.TokenRefreshRequest request);

    void logout(UUID userId);
}
