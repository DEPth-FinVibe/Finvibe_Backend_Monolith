package depth.finvibe.modules.user.application.port.out;

import depth.finvibe.modules.user.domain.User;
import depth.finvibe.modules.user.domain.vo.Email;
import depth.finvibe.modules.user.domain.vo.LoginId;
import depth.finvibe.modules.user.domain.vo.OAuthInfo;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByExternalUserId(UUID externalUserId);

    Optional<User> findByInternalUserId(Long internalUserId);

    Optional<User> findByLoginId(LoginId loginId);

    Optional<User> findByOauthInfo(OAuthInfo oauthInfo);

    Map<Long, String> findNicknamesByIds(Collection<Long> ids);

    boolean existsByEmail(Email email);

    boolean existsByLoginId(LoginId loginId);

    boolean existsByNickname(String nickname);
}
