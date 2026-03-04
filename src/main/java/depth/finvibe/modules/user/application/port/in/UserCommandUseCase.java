package depth.finvibe.modules.user.application.port.in;

import java.util.UUID;

import depth.finvibe.modules.user.dto.UserDto;
import depth.finvibe.boot.security.Requester;

public interface UserCommandUseCase {
    UserDto.UserResponse update(UserDto.UpdateUserRequest request, Requester requester);

    UserDto.UserResponse changeNickname(UserDto.ChangeNicknameRequest request, Requester requester);

    UserDto.FavoriteStockResponse addFavoriteStock(Long stockId, Requester requester);

    UserDto.FavoriteStockResponse removeFavoriteStock(Long stockId, Requester requester);

    void withdraw(UUID userId);
}
