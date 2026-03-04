package depth.finvibe.modules.user.infra.persistence;

import depth.finvibe.modules.user.domain.User;
import depth.finvibe.modules.user.domain.vo.Email;
import depth.finvibe.modules.user.domain.vo.LoginId;
import depth.finvibe.modules.user.domain.vo.OAuthInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaUserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByLoginId(LoginId loginId);
    Optional<User> findByOauthInfo(OAuthInfo oauthInfo);
    boolean existsByPersonalDetails_Email(Email email);
    boolean existsByLoginId(LoginId loginId);
    boolean existsByPersonalDetails_Nickname(String nickname);
}
