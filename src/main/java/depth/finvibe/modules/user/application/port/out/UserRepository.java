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

    Optional<User> findById(UUID id);

    Optional<User> findByLoginId(LoginId loginId);

    Optional<User> findByOauthInfo(OAuthInfo oauthInfo);

    Map<UUID, String> findNicknamesByIds(Collection<UUID> ids);

    boolean existsByEmail(Email email);

    boolean existsByLoginId(LoginId loginId);

    boolean existsByNickname(String nickname);
}
