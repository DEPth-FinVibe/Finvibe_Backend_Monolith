package depth.finvibe.modules.user.application.port.in;

import java.util.List;
import java.util.UUID;

import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.user.domain.LoginContext;
import depth.finvibe.modules.user.dto.UserDto;

public interface AuthCommandUseCase {
    UserDto.SignUpResponse signUp(UserDto.SignUpRequest request, LoginContext loginContext);

    UserDto.SignUpResponse oauthSignUp(UserDto.OAuthSignUpRequest request, LoginContext loginContext);

    UserDto.TokenResponse login(UserDto.LoginRequest request, LoginContext loginContext);

    UserDto.OAuthLoginResponse oauthLogin(UserDto.OAuthLoginRequest request, LoginContext loginContext);

    UserDto.TokenRefreshResponse refreshToken(UserDto.TokenRefreshRequest request);

    void logout(Requester requester);

    List<UserDto.SessionResponse> getSessions(Requester requester);

    void logoutSession(UUID userId, UUID tokenFamilyId);
}
