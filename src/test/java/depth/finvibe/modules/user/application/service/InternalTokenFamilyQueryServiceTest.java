package depth.finvibe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("토큰 패밀리가 존재하면 응답을 반환하고 캐시를 갱신한다")
    void getTokenFamily_exists_returnsAndRefreshesCache() {
        // given
        InternalTokenFamilyQueryService service = new InternalTokenFamilyQueryService(
            tokenFamilyRepository,
            tokenFamilyCacheRepository
        );
        TokenFamily tokenFamily = TokenFamily.create(1L, LoginContext.unknown(), Instant.now());
        tokenFamily.rotate("hashed-refresh", Instant.parse("2030-01-01T00:00:00Z"), Instant.now());

        when(tokenFamilyRepository.findById(tokenFamily.getId())).thenReturn(Optional.of(tokenFamily));

        // when
        Optional<InternalTokenFamilyDto.Response> result = service.getTokenFamily(tokenFamily.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getFamilyId()).isEqualTo(tokenFamily.getId());
        assertThat(result.get().getStatus()).isEqualTo(tokenFamily.getStatus().name());
        verify(tokenFamilyCacheRepository).save(tokenFamily);
    }

    @Test
    @DisplayName("토큰 패밀리가 없으면 빈 응답을 반환하고 캐시를 갱신하지 않는다")
    void getTokenFamily_missing_returnsEmpty() {
        // given
        InternalTokenFamilyQueryService service = new InternalTokenFamilyQueryService(
            tokenFamilyRepository,
            tokenFamilyCacheRepository
        );
        UUID tokenFamilyId = UUID.randomUUID();

        when(tokenFamilyRepository.findById(tokenFamilyId)).thenReturn(Optional.empty());

        // when
        Optional<InternalTokenFamilyDto.Response> result = service.getTokenFamily(tokenFamilyId);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(tokenFamilyCacheRepository);
    }
}
