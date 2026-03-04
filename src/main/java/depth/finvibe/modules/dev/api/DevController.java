package depth.finvibe.modules.dev.api;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import depth.finvibe.boot.security.investment.JwtTokenGenerator;
import depth.finvibe.modules.user.domain.enums.UserRole;

/**
 * 개발 환경 전용 테스트 API 컨트롤러
 * local 프로파일에서만 활성화됩니다.
 */
@Profile("local")
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
@Tag(name = "개발", description = "개발 전용 API")
public class DevController {

    private final JwtTokenGenerator jwtTokenGenerator;

    /**
     * WebSocket 인증용 테스트 JWT 토큰을 발급합니다.
     * userId는 랜덤 생성, role은 USER, 만료기한은 1일(86400초)로 고정됩니다.
     *
     * @return 생성된 JWT 토큰
     */
    @PostMapping("/jwt/token")
    @Operation(summary = "테스트 JWT 발급", description = "로컬 테스트용 JWT 토큰을 발급합니다.")
    public ResponseEntity<JwtTokenResponse> generateToken() {
        UUID userId = UUID.randomUUID();
        UserRole role = UserRole.USER;
        Long expirationSeconds = 86400L; // 1일
        
        String token = jwtTokenGenerator.generate(userId, role, expirationSeconds);

        return ResponseEntity.ok(new JwtTokenResponse(token, userId, role));
    }

    /**
     * JWT 토큰 생성 응답
     */
    public record JwtTokenResponse(
            String token,
            UUID userId,
            UserRole role
    ) {}
}
