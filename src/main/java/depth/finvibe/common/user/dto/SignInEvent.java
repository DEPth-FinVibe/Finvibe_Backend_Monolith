package depth.finvibe.common.user.dto;

import lombok.Builder;

@Builder
public record SignInEvent(
        String userId
) {
}
