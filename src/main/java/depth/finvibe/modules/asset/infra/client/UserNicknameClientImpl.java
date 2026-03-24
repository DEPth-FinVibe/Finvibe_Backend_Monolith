package depth.finvibe.modules.asset.infra.client;

import depth.finvibe.modules.asset.application.port.out.UserNicknameClient;
import depth.finvibe.modules.user.application.port.in.UserQueryUseCase;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserNicknameClientImpl implements UserNicknameClient {
    private static final Logger log = LoggerFactory.getLogger(UserNicknameClientImpl.class);

    private final UserQueryUseCase userQueryUseCase;

    @Override
    public Map<UUID, String> getUserNicknamesByIds(Collection<UUID> userIds) {
        List<UUID> ids = new ArrayList<>(userIds);

        log.debug("[UserNicknameClientImpl] 닉네임 조회 요청 수신 - 요청 userIds 개수: {}, userIds: {}", ids.size(), ids);

        if (ids.isEmpty()) {
            log.debug("[UserNicknameClientImpl] 닉네임 조회 생략 - 빈 userIds 요청");
            return Map.of();
        }

        Map<UUID, String> nicknames = userQueryUseCase.getNicknames(ids);

        log.debug("[UserNicknameClientImpl] 닉네임 조회 응답 성공 - 요청 userIds 개수: {}, 응답 결과 개수: {}",
                ids.size(), nicknames.size());
        return nicknames;
    }
}
