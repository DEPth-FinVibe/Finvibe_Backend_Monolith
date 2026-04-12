package depth.finvibe.modules.user.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import depth.finvibe.modules.user.application.port.out.TokenFamilyRepository;
import depth.finvibe.modules.user.domain.TokenFamily;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TokenFamilyRepositoryImpl implements TokenFamilyRepository {

	private final JpaTokenFamilyRepository jpaTokenFamilyRepository;
	private final TokenFamilyQueryRepository tokenFamilyQueryRepository;

	@Override
	public TokenFamily save(TokenFamily tokenFamily) {
		return jpaTokenFamilyRepository.save(tokenFamily);
	}

	@Override
	public Optional<TokenFamily> findById(UUID tokenFamilyId) {
		return jpaTokenFamilyRepository.findById(tokenFamilyId);
	}

	@Override
	public List<TokenFamily> findAllByUserId(UUID userId) {
		return jpaTokenFamilyRepository.findAllByUserIdOrderByLastUsedAtDescCreatedAtDesc(userId);
	}

	@Override
	public List<TokenFamily> findAvailableByUserId(UUID userId) {
		return tokenFamilyQueryRepository.findAvailableByUserId(userId);
	}
}
