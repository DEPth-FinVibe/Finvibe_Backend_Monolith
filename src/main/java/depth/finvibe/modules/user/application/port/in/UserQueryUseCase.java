package depth.finvibe.modules.user.application.port.in;

import depth.finvibe.modules.user.dto.UserDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserQueryUseCase {
    UserDto.UserResponse getMe(UUID userId);

    List<UserDto.FavoriteStockResponse> getFavoriteStocks(UUID userId);

    UserDto.DuplicateCheckResponse checkLoginIdDuplicate(String loginId);

    UserDto.DuplicateCheckResponse checkEmailDuplicate(String email);

    UserDto.DuplicateCheckResponse checkNicknameDuplicate(String nickname);

    String getNickname(UUID userId);

    Map<UUID, String> getNicknames(Collection<UUID> userIds);

    UserDto.MemberProfileResponse getMemberProfile(UUID userId);
}
