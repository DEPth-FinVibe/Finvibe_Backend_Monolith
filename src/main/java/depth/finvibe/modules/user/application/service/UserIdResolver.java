package depth.finvibe.modules.user.application.service;

import depth.finvibe.common.error.DomainException;
import depth.finvibe.modules.user.application.port.out.UserIdCacheRepository;
import depth.finvibe.modules.user.application.port.out.UserRepository;
import depth.finvibe.modules.user.domain.User;
import depth.finvibe.modules.user.domain.error.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserIdResolver {

    private final UserIdCacheRepository userIdCacheRepository;
    private final UserRepository userRepository;

    public Long resolveInternalUserId(UUID externalUserId) {
        return userIdCacheRepository.findInternalUserIdByExternalUserId(externalUserId)
            .orElseGet(() -> loadAndCacheInternalUserId(externalUserId));
    }

    private Long loadAndCacheInternalUserId(UUID externalUserId) {
        User user = userRepository.findByExternalUserId(externalUserId)
            .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));
        Long internalUserId = user.getInternalUserId();
        if (internalUserId == null) {
            throw new DomainException(UserErrorCode.USER_NOT_FOUND);
        }
        userIdCacheRepository.save(externalUserId, internalUserId);
        return internalUserId;
    }
}
