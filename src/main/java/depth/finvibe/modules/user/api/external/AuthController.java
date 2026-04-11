package depth.finvibe.modules.user.api.external;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.ResolvedLoginContext;
import depth.finvibe.modules.user.application.port.in.AuthCommandUseCase;
import depth.finvibe.modules.user.domain.LoginContext;
import depth.finvibe.modules.user.dto.UserDto;
import depth.finvibe.boot.security.Requester;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public UserDto.TokenResponse login(
            @RequestBody @Valid UserDto.LoginRequest request,
            @ResolvedLoginContext LoginContext loginContext
    ) {
        return authCommandUseCase.login(request, loginContext);
    }

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "회원가입을 수행합니다.")
    public UserDto.SignUpResponse signUp(
            @RequestBody @Valid UserDto.SignUpRequest request,
            @ResolvedLoginContext LoginContext loginContext
    ) {
        return authCommandUseCase.signUp(request, loginContext);
    }

    @PostMapping("/oauth-signup")
    @Operation(summary = "OAuth 회원가입", description = "OAuth 회원가입을 수행합니다.")
    public UserDto.SignUpResponse oauthSignUp(
            @RequestBody @Valid UserDto.OAuthSignUpRequest request,
            @ResolvedLoginContext LoginContext loginContext
    ) {
        return authCommandUseCase.oauthSignUp(request, loginContext);
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "리프레시 토큰으로 액세스 토큰을 갱신합니다.")
    public UserDto.TokenRefreshResponse refreshToken(@RequestBody @Valid UserDto.TokenRefreshRequest request) {
        return authCommandUseCase.refreshToken(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "로그아웃을 수행합니다.")
    public void logout(@Parameter(hidden = true) @AuthenticatedUser Requester requester) {
        authCommandUseCase.logout(requester);
    }

    @GetMapping("/sessions")
    @Operation(summary = "로그인 기기 목록 조회", description = "현재 로그인된 기기 목록을 조회합니다.")
    public java.util.List<UserDto.SessionResponse> getSessions(@Parameter(hidden = true) @AuthenticatedUser Requester requester) {
        return authCommandUseCase.getSessions(requester);
    }

    @DeleteMapping("/sessions/{tokenFamilyId}")
    @Operation(summary = "특정 기기 로그아웃", description = "특정 로그인 기기를 원격으로 로그아웃합니다.")
    public void logoutSession(
            @Parameter(hidden = true) @AuthenticatedUser Requester requester,
            @PathVariable java.util.UUID tokenFamilyId
    ) {
        authCommandUseCase.logoutSession(requester.getUserId(), tokenFamilyId);
    }
}
