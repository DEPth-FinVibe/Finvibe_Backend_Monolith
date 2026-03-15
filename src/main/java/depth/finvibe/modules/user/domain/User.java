package depth.finvibe.modules.user.domain;

import java.util.UUID;

import depth.finvibe.modules.user.domain.vo.*;
import jakarta.persistence.*;
import lombok.Builder;
import org.springframework.security.crypto.password.PasswordEncoder;

import depth.finvibe.modules.user.domain.enums.UserRole;
import depth.finvibe.modules.user.domain.error.UserErrorCode;
import depth.finvibe.common.user.domain.TimeStampedBaseEntity;
import depth.finvibe.common.error.DomainException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@SuperBuilder
@Table(name = "users", indexes = {
    @Index(name = "idx_users_login_id", columnList = "login_id", unique = true),
})
public class User extends TimeStampedBaseEntity {
    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "login_id"))
    private LoginId loginId;

    @Embedded
    @AttributeOverride(name = "passwordHash", column = @Column(name = "password_hash", nullable = false))
    private PasswordHash passwordHash;

    @Embedded
    private OAuthInfo oauthInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Embedded
    private PersonalDetails personalDetails;

    @Builder.Default
    @Column(nullable = false)
    private boolean isDeleted = false;

    public static User create(LoginId loginId, PasswordHash password, PersonalDetails personalDetails) {
        return User.builder()
                .id(UUID.randomUUID())
                .loginId(loginId)
                .passwordHash(password)
                .personalDetails(personalDetails)
                .role(UserRole.USER)
                .build();
    }

    public static User createSocial(OAuthInfo oAuthInfo, PersonalDetails personalDetails, PasswordEncoder passwordEncoder) {
        String randomPassword = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 12);

        return User.builder()
                .id(UUID.randomUUID())
                .oauthInfo(oAuthInfo)
                .passwordHash(PasswordHash.create(randomPassword, passwordEncoder))
                .personalDetails(personalDetails)
                .role(UserRole.USER)
                .build();
    }

    public void withdraw() {
        validateActive();

        this.isDeleted = true;
    }

    public void update(
        LoginId loginId,
        PasswordHash passwordHash,
        PersonalDetails personalDetails
    ) {
        if (loginId != null) {
            this.loginId = loginId;
        }
        if (passwordHash != null) {
            this.passwordHash = passwordHash;
        }
        if (personalDetails != null) {
            this.personalDetails = personalDetails;
        }
    }


    public void validateLogin(String rawPassword, PasswordEncoder passwordEncoder) {
        validateActive();
        if (this.passwordHash == null || !this.passwordHash.matches(rawPassword, passwordEncoder)) {
            throw new DomainException(UserErrorCode.INVALID_PASSWORD);
        }
    }

    public void validateActive() {
        if (this.isDeleted) {
            throw new DomainException(UserErrorCode.USER_DELETED);
        }
    }

    public void validateUpdatable(UUID requesterId, UserRole requesterRole) {
        if (!this.id.equals(requesterId) && requesterRole != UserRole.ADMIN) {
            throw new DomainException(UserErrorCode.UNAUTHORIZED_USER_UPDATE);
        }
    }
}
