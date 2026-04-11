package depth.finvibe.boot.security;

import depth.finvibe.modules.user.domain.enums.UserRole;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Requester {
    private UUID userId;
    private UserRole role;
    private UUID tokenFamilyId;

    public UUID getUuid() {
        return userId;
    }
}
