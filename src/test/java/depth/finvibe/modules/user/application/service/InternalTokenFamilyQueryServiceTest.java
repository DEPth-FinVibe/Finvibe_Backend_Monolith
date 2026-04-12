package depth.finvibe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import depth.finvibe.modules.user.application.port.out.TokenFamilyCacheRepository;
import depth.finvibe.modules.user.application.port.out.TokenFamilyRepository;
import depth.finvibe.modules.user.domain.LoginContext;
import depth.finvibe.modules.user.domain.TokenFamily;
import depth.finvibe.modules.user.dto.InternalTokenFamilyDto;

@ExtendWith(MockitoExtension.class)
class InternalTokenFamilyQueryServiceTest {

	@Mock
	private TokenFamilyRepository tokenFamilyRepository;

	@Mock
	private TokenFamilyCacheRepository tokenFamilyCacheRepository;

	@Test
	void returnsTokenFamilyAndRefreshesCacheWhenFound() {
		InternalTokenFamilyQueryService service = new InternalTokenFamilyQueryService(
			tokenFamilyRepository,
			tokenFamilyCacheRepository
		);
		TokenFamily tokenFamily = TokenFamily.create(UUID.randomUUID(), LoginContext.unknown(), Instant.now());
		tokenFamily.rotate("hashed-refresh", Instant.parse("2030-01-01T00:00:00Z"), Instant.now());

		when(tokenFamilyRepository.findById(tokenFamily.getId())).thenReturn(Optional.of(tokenFamily));

		Optional<InternalTokenFamilyDto.Response> result = service.getTokenFamily(tokenFamily.getId());

		assertThat(result).isPresent();
		assertThat(result.get().getFamilyId()).isEqualTo(tokenFamily.getId());
		assertThat(result.get().getStatus()).isEqualTo(tokenFamily.getStatus().name());
		verify(tokenFamilyCacheRepository).save(tokenFamily);
	}

	@Test
	void returnsEmptyWhenTokenFamilyDoesNotExist() {
		InternalTokenFamilyQueryService service = new InternalTokenFamilyQueryService(
			tokenFamilyRepository,
			tokenFamilyCacheRepository
		);
		UUID tokenFamilyId = UUID.randomUUID();

		when(tokenFamilyRepository.findById(tokenFamilyId)).thenReturn(Optional.empty());

		Optional<InternalTokenFamilyDto.Response> result = service.getTokenFamily(tokenFamilyId);

		assertThat(result).isEmpty();
		verifyNoInteractions(tokenFamilyCacheRepository);
	}
}
