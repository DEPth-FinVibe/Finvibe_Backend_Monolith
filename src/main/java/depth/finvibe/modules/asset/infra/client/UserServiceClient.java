package depth.finvibe.modules.asset.infra.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class UserServiceClient {
    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);
    private static final ParameterizedTypeReference<Map<UUID, String>> mapOfUuidToStringTypeRef = new ParameterizedTypeReference<>() {};
    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://user")
            .build();

    public Map<UUID, String> getUserNicknamesByIds(Collection<UUID> userIds) {
        List<UUID> ids = new ArrayList<>(userIds);

        log.debug("[UserServiceClient] 닉네임 조회 요청 수신 - 요청 userIds 개수: {}, userIds: {}", ids.size(), ids);

        if (ids.isEmpty()) {
            log.debug("[UserServiceClient] 닉네임 조회 생략 - 빈 userIds 요청");
            return Map.of();
        }

        try {
            Map<UUID, String> nicknames = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/members/nicknames")
                            .queryParam("userIds", ids)
                            .build())
                    .retrieve()
                    .body(mapOfUuidToStringTypeRef);

            Map<UUID, String> result = nicknames != null ? nicknames : Map.of();
            log.debug("[UserServiceClient] 닉네임 조회 응답 성공 - 요청 userIds 개수: {}, 응답 결과 개수: {}, 결과: {}",
                    ids.size(), result.size(), result);

            return result;
        } catch (RestClientException e) {
            log.error("[UserServiceClient] 닉네임 조회 실패 - 요청 userIds 개수: {}, userIds: {}, 원인: {}",
                    ids.size(), ids, e.getMessage(), e);
            throw e;
        }
    }


}
