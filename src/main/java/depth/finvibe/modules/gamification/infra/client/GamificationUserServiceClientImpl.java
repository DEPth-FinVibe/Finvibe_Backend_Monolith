package depth.finvibe.modules.gamification.infra.client;

import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import depth.finvibe.modules.gamification.application.port.out.UserServiceClient;

@Component
public class GamificationUserServiceClientImpl implements UserServiceClient {

    private static final ParameterizedTypeReference<Map<UUID, String>> MAP_OF_UUID_TO_STRING_TYPE_REF =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public GamificationUserServiceClientImpl(

    ) {
        this.restClient = RestClient.builder().baseUrl("http://user").build();
    }

    @Override
    public Optional<String> getNickname(UUID userId) {
        Map<UUID, String> nicknames = getNicknamesByIds(List.of(userId));
        return Optional.ofNullable(nicknames.get(userId));
    }

    @Override
    public Map<UUID, String> getNicknamesByIds(Collection<UUID> userIds) {
        List<UUID> ids = new ArrayList<>(userIds);
        if (ids.isEmpty()) {
            return Map.of();
        }

        try {
            Map<UUID, String> nicknames = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/internal/members/nicknames")
                    .queryParam("userIds", ids)
                    .build())
                .retrieve()
                .body(MAP_OF_UUID_TO_STRING_TYPE_REF);
            return nicknames != null ? nicknames : Map.of();
        } catch (RestClientException exception) {
            return Map.of();
        }
    }
}
