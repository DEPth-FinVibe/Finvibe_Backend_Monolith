package depth.finvibe.modules.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor(staticName = "create")
public class RefreshToken {
    private final UUID userId;
    private final String token;

    public RefreshToken rotate(String newToken) {
        return RefreshToken.create(this.userId, newToken);
    }
}
