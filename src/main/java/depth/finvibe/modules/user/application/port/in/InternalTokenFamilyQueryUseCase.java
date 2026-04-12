package depth.finvibe.modules.user.application.port.in;

import java.util.Optional;
import java.util.UUID;

import depth.finvibe.modules.user.dto.InternalTokenFamilyDto;

public interface InternalTokenFamilyQueryUseCase {
    Optional<InternalTokenFamilyDto.Response> getTokenFamily(UUID tokenFamilyId);
}
