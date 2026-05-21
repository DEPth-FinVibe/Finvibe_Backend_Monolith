package depth.finvibe.modules.user.application.port.in;

import depth.finvibe.modules.user.dto.UserDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserQueryUseCase {
    UserDto.UserResponse getMe(Long userId);

    List<UserDto.FavoriteStockResponse> getFavoriteStocks(Long userId);

    UserDto.DuplicateCheckResponse checkLoginIdDuplicate(String loginId);

    UserDto.DuplicateCheckResponse checkEmailDuplicate(String email);

    UserDto.DuplicateCheckResponse checkNicknameDuplicate(String nickname);

    String getNickname(Long userId);

    Map<Long, String> getNicknames(Collection<Long> userIds);

    UserDto.MemberProfileResponse getMemberProfile(Long userId);
}
