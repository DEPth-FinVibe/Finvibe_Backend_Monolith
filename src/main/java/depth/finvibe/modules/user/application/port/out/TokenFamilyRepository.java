package depth.finvibe.modules.user.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import depth.finvibe.modules.user.domain.TokenFamily;

public interface TokenFamilyRepository {
	TokenFamily save(TokenFamily tokenFamily);

	Optional<TokenFamily> findById(UUID tokenFamilyId);

	List<TokenFamily> findAllByUserId(UUID userId);

	List<TokenFamily> findAvailableByUserId(UUID userId);
}
