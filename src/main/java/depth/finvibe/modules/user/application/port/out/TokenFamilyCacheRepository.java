package depth.finvibe.modules.user.application.port.out;

import depth.finvibe.modules.user.domain.TokenFamily;

public interface TokenFamilyCacheRepository {
	void save(TokenFamily tokenFamily);
}
