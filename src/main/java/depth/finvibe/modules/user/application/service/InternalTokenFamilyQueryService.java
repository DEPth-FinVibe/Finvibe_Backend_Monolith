package depth.finvibe.modules.user.application.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.modules.user.application.port.in.InternalTokenFamilyQueryUseCase;
import depth.finvibe.modules.user.application.port.out.TokenFamilyCacheRepository;
import depth.finvibe.modules.user.application.port.out.TokenFamilyRepository;
import depth.finvibe.modules.user.dto.InternalTokenFamilyDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalTokenFamilyQueryService implements InternalTokenFamilyQueryUseCase {

	private final TokenFamilyRepository tokenFamilyRepository;
	private final TokenFamilyCacheRepository tokenFamilyCacheRepository;

	@Override
	public Optional<InternalTokenFamilyDto.Response> getTokenFamily(UUID tokenFamilyId) {
		return tokenFamilyRepository.findById(tokenFamilyId)
			.map(tokenFamily -> {
				tokenFamilyCacheRepository.save(tokenFamily);
				return InternalTokenFamilyDto.Response.from(tokenFamily);
			});
	}
}
