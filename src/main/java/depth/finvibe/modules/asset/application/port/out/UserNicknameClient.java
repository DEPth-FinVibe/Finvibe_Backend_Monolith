package depth.finvibe.modules.asset.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface UserNicknameClient {
    Map<UUID, String> getUserNicknamesByIds(Collection<UUID> userIds);
}
