package depth.finvibe.common.user.dto;

import lombok.Builder;

@Builder
public record SignUpEvent(
        String userId
) {
}
