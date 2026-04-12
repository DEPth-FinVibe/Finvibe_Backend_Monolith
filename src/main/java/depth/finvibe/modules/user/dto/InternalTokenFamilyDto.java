package depth.finvibe.modules.user.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import depth.finvibe.modules.user.domain.TokenFamily;
import lombok.Getter;

public class InternalTokenFamilyDto {

	@Getter
	public static class Response {
		private final UUID familyId;
		private final String status;
		private final OffsetDateTime expiresAt;

		public Response(UUID familyId, String status, OffsetDateTime expiresAt) {
			this.familyId = familyId;
			this.status = status;
			this.expiresAt = expiresAt;
		}

		public static Response from(TokenFamily tokenFamily) {
			return new Response(
				tokenFamily.getId(),
				tokenFamily.getStatus().name(),
				tokenFamily.getExpiresAt().atOffset(ZoneOffset.UTC)
			);
		}
	}
}
