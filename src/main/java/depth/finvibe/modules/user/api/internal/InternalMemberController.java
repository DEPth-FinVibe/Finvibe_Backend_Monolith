package depth.finvibe.modules.user.api.internal;

import depth.finvibe.modules.user.application.port.in.UserQueryUseCase;
import depth.finvibe.modules.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/members")
@RequiredArgsConstructor
public class InternalMemberController {

    private final UserQueryUseCase userQueryUseCase;

    @GetMapping("/{userId}/nickname")
    public String getNickname(
        @PathVariable String userId
    ) {
        return userQueryUseCase.getNickname(UUID.fromString(userId));
    }

    @GetMapping("/{userId}/favorite-stocks")
    public List<UserDto.FavoriteStockResponse> getFavoriteStocks(
        @PathVariable String userId
    ) {
        return userQueryUseCase.getFavoriteStocks(UUID.fromString(userId));
    }

    @GetMapping("/nicknames")
    public Map<UUID, String> getNicknames(
        @RequestParam List<UUID> userIds
    ) {
        return userIds.stream()
            .collect(Collectors.toMap(
                Function.identity(),
                userQueryUseCase::getNickname,
                (existing, replacement) -> existing,
                LinkedHashMap::new
            ));
    }
}
