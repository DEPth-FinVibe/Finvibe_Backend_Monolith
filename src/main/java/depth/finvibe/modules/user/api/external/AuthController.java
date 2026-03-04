package depth.finvibe.modules.user.api.external;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.modules.user.application.port.in.AuthCommandUseCase;
import depth.finvibe.modules.user.dto.UserDto;
import depth.finvibe.boot.security.Requester;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Tag(name = "인증", description = "인증 API")
public class AuthController {

    private final AuthCommandUseCase authCommandUseCase;

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "로그인을 수행하고 토큰을 발급합니다.")
    public UserDto.TokenResponse login(@RequestBody @Valid UserDto.LoginRequest request) {
        return authCommandUseCase.login(request);
    }

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "회원가입을 수행합니다.")
    public UserDto.SignUpResponse signUp(@RequestBody @Valid UserDto.SignUpRequest request) {
        return authCommandUseCase.signUp(request);
    }

    @PostMapping("/oauth-signup")
    @Operation(summary = "OAuth 회원가입", description = "OAuth 회원가입을 수행합니다.")
    public UserDto.SignUpResponse oauthSignUp(@RequestBody @Valid UserDto.OAuthSignUpRequest request) {
        return authCommandUseCase.oauthSignUp(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "리프레시 토큰으로 액세스 토큰을 갱신합니다.")
    public UserDto.TokenRefreshResponse refreshToken(@RequestBody @Valid UserDto.TokenRefreshRequest request) {
        return authCommandUseCase.refreshToken(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "로그아웃을 수행합니다.")
    public void logout(@Parameter(hidden = true) @AuthenticatedUser Requester requester) {
        authCommandUseCase.logout(requester.getUserId());
    }
}
