package depth.finvibe.modules.user.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import depth.finvibe.modules.user.domain.InterestStock;
import depth.finvibe.modules.user.domain.User;
import depth.finvibe.modules.user.domain.enums.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserDto {

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "SignUpRequest", description = "회원가입 요청")
    public static class SignUpRequest {
        @Email
        @Schema(description = "이메일", example = "user@example.com")
        private String email;

        @NotEmpty
        @Schema(description = "로그인 아이디", example = "finvibe_user")
        private String loginId;

        @NotEmpty
        @Schema(description = "비밀번호", example = "P@ssw0rd!")
        private String password;

        @NotEmpty
        @Schema(description = "닉네임", example = "핀바이브")
        private String nickname;

        @NotEmpty
        @Schema(description = "이름", example = "홍길동")
        private String name;

        @NotNull
        @Past
        @Schema(description = "생년월일", example = "1990-01-01")
        private LocalDate birthDate;

        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "휴대폰 번호는 010-0000-0000 형식이어야 합니다.")
        @Schema(description = "휴대폰 번호", example = "010-1234-5678")
        private String phoneNumber;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "OAuthSignUpRequest", description = "OAuth 회원가입 요청")
    public static class OAuthSignUpRequest {
        @Email
        @Schema(description = "이메일", example = "user@example.com")
        private String email;

        @NotEmpty
        @Schema(description = "닉네임", example = "핀바이브")
        private String nickname;

        @NotEmpty
        @Schema(description = "이름", example = "홍길동")
        private String name;

        @NotNull
        @Past
        @Schema(description = "생년월일", example = "1990-01-01")
        private LocalDate birthDate;

        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "휴대폰 번호는 010-0000-0000 형식이어야 합니다.")
        @Schema(description = "휴대폰 번호", example = "010-1234-5678")
        private String phoneNumber;

        @NotEmpty
        @Schema(description = "임시 토큰", example = "temp-token")
        private String temporaryToken;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(staticName = "of")
    @Schema(name = "SignUpResponse", description = "회원가입 응답")
    public static class SignUpResponse {
        @Schema(description = "회원 정보")
        private UserResponse user;

        @Schema(description = "토큰 정보")
        private TokenResponse tokens;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "LoginRequest", description = "로그인 요청")
    public static class LoginRequest {
        @Schema(description = "로그인 아이디", example = "finvibe_user")
        private String loginId;

        @Schema(description = "비밀번호", example = "P@ssw0rd!")
        private String password;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "OAuthLoginRequest", description = "OAuth 로그인 요청")
    public static class OAuthLoginRequest {
        @Schema(description = "OAuth 제공자", example = "KAKAO")
        private AuthProvider provider;

        @Schema(description = "OAuth 제공자 사용자 ID", example = "1234567890")
        private String providerId;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(staticName = "of")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "OAuthLoginResponse", description = "OAuth 로그인 응답")
    public static class OAuthLoginResponse {
        @Schema(description = "토큰 정보")
        private TokenResponse tokens;

        @Schema(description = "임시 토큰", example = "temp-token")
        private String temporaryToken;

        @Schema(description = "추가 가입 필요 여부", example = "true")
        private boolean registrationRequired;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "UpdateUserRequest", description = "회원 정보 수정 요청")
    public static class UpdateUserRequest {
        @Schema(description = "로그인 아이디", example = "finvibe_user")
        private String loginId;

        @Schema(description = "기존 비밀번호", example = "OldP@ssw0rd!")
        private String oldPassword;

        @Schema(description = "새 비밀번호", example = "NewP@ssw0rd!")
        private String newPassword;

        @Schema(description = "이메일", example = "user@example.com")
        private String email;

        @Schema(description = "이름", example = "홍길동")
        private String name;

        @Schema(description = "닉네임", example = "핀바이브")
        private String nickname;

        @Schema(description = "생년월일", example = "1990-01-01")
        private LocalDate birthDate;

        @Schema(description = "휴대폰 번호", example = "010-1234-5678")
        private String phoneNumber;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "ChangeNicknameRequest", description = "닉네임 변경 요청")
    public static class ChangeNicknameRequest {
        @NotEmpty
        @Size(max = 10, message = "닉네임은 최대 10자까지 가능합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9가-힣]+$", message = "닉네임은 특수문자를 포함할 수 없습니다.")
        @Schema(description = "닉네임", example = "핀바이브")
        private String nickname;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "TokenResponse", description = "토큰 응답")
    public static class TokenResponse {
        @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        private String accessToken;

        @Schema(description = "액세스 토큰 만료 시각", example = "2024-01-01T00:00:00Z")
        private OffsetDateTime accessExpiresAt;

        @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        private String refreshToken;

        @Schema(description = "리프레시 토큰 만료 시각", example = "2024-02-01T00:00:00Z")
        private OffsetDateTime refreshExpiresAt;

        @Schema(description = "토큰 패밀리 ID", example = "00000000-0000-0000-0000-000000000000")
        private UUID tokenFamilyId;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "TokenRefreshRequest", description = "토큰 갱신 요청")
    public static class TokenRefreshRequest {
        @NotEmpty
        @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        private String refreshToken;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "TokenRefreshResponse", description = "토큰 갱신 응답")
    public static class TokenRefreshResponse {
        @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        private String accessToken;

        @Schema(description = "액세스 토큰 만료 시각", example = "2024-01-01T00:00:00Z")
        private OffsetDateTime accessExpiresAt;

        @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        private String refreshToken;

        @Schema(description = "리프레시 토큰 만료 시각", example = "2024-02-01T00:00:00Z")
        private OffsetDateTime refreshExpiresAt;

        @Schema(description = "토큰 패밀리 ID", example = "00000000-0000-0000-0000-000000000000")
        private UUID tokenFamilyId;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "SessionResponse", description = "로그인 기기 세션 응답")
    public static class SessionResponse {
        @Schema(description = "토큰 패밀리 ID", example = "00000000-0000-0000-0000-000000000000")
        private UUID tokenFamilyId;

        @Schema(description = "현재 요청 기기 여부", example = "true")
        private boolean currentDevice;

        @Schema(description = "브라우저 이름", example = "Chrome")
        private String browserName;

        @Schema(description = "운영체제 이름", example = "macOS")
        private String osName;

        @Schema(description = "위치", example = "Seoul, KR")
        private String location;

        @Schema(description = "마스킹된 IP 주소", example = "127.0.*.*")
        private String ipAddress;

        @Schema(description = "마지막 사용 시각", example = "2024-01-01T00:00:00Z")
        private OffsetDateTime lastUsedAt;

        @Schema(description = "생성 시각", example = "2024-01-01T00:00:00Z")
        private OffsetDateTime createdAt;

        @Schema(description = "세션 상태", example = "ACTIVE")
        private String status;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "UserResponse", description = "회원 정보 응답")
    public static class UserResponse {
        @Schema(description = "회원 ID", example = "00000000-0000-0000-0000-000000000000")
        private UUID userId;

        @Schema(description = "이메일", example = "user@example.com")
        private String email;

        @Schema(description = "닉네임", example = "핀바이브")
        private String nickname;

        @Schema(description = "이름", example = "홍길동")
        private String name;

        @Schema(description = "생년월일", example = "1990-01-01")
        private LocalDate birthDate;

        @Schema(description = "휴대폰 번호", example = "010-1234-5678")
        private String phoneNumber;

        public static UserResponse from(User user) {
            return UserResponse.builder()
                    .userId(user.getId())
                    .email(user.getPersonalDetails().getEmail().getValue())
                    .nickname(user.getPersonalDetails().getNickname())
                    .name(user.getPersonalDetails().getName())
                    .birthDate(user.getPersonalDetails().getBirthDate())
                    .phoneNumber(user.getPersonalDetails().getPhoneNumber().toString())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "DuplicateCheckResponse", description = "중복 확인 응답")
    public static class DuplicateCheckResponse {
        @Schema(description = "중복 여부", example = "false")
        private boolean isDuplicate;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "FavoriteStockResponse", description = "관심 종목 응답")
    public static class FavoriteStockResponse {
        @Schema(description = "종목 ID", example = "1")
        private Long stockId;

        @Schema(description = "종목명", example = "삼성전자")
        private String name;

        @Schema(description = "회원 ID", example = "00000000-0000-0000-0000-000000000000")
        private UUID userId;

        public static FavoriteStockResponse from(InterestStock interestStock) {
            return FavoriteStockResponse.builder()
                    .stockId(interestStock.getStockId())
                    .name(interestStock.getStockName())
                    .userId(interestStock.getUserId())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "UserSummaryResponse", description = "유저 게이미피케이션 요약 정보")
    public static class UserSummaryResponse {
        @Schema(description = "사용자 UUID")
        private UUID userId;

        @Schema(description = "보유 뱃지 목록")
        private java.util.List<OwnedBadge> badges;

        @Schema(description = "주간 XP 랭킹 순위")
        private Integer ranking;

        @Schema(description = "누적 보유 XP")
        private Long totalXp;

        @Schema(description = "현재 수익률")
        private Double currentReturnRate;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "OwnedBadge", description = "사용자 보유 뱃지")
    public static class OwnedBadge {
        @Schema(description = "뱃지 동적 필드")
        private Map<String, Object> attributes = new HashMap<>();

        @JsonAnySetter
        public void addAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getAttributes() {
            return attributes;
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Schema(name = "MemberProfileResponse", description = "닉네임 + 게이미피케이션 요약 응답")
    public static class MemberProfileResponse {
        @Schema(description = "회원 ID", example = "00000000-0000-0000-0000-000000000000")
        private UUID userId;

        @Schema(description = "닉네임", example = "핀바이브")
        private String nickname;

        @Schema(description = "게이미피케이션 요약")
        private UserSummaryResponse gamificationSummary;
    }
}
