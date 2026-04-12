package depth.finvibe.modules.user.api.internal;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import depth.finvibe.modules.user.application.port.in.InternalTokenFamilyQueryUseCase;
import depth.finvibe.modules.user.dto.InternalTokenFamilyDto;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/auth/token-families")
public class InternalTokenFamilyController {

	private final InternalTokenFamilyQueryUseCase internalTokenFamilyQueryUseCase;

	@GetMapping("/{tokenFamilyId}")
	public InternalTokenFamilyDto.Response getTokenFamily(@PathVariable UUID tokenFamilyId) {
		return internalTokenFamilyQueryUseCase.getTokenFamily(tokenFamilyId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}
}
