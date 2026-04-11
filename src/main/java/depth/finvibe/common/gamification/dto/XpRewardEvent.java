package depth.finvibe.common.gamification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
@Data
@Builder
@Schema(description = "경험치 지급 이벤트")
public class XpRewardEvent {
    @Schema(description = "사용자 UUID")
    private String userId;

    @Schema(description = "지급 사유")
    private String reason;

    @Schema(description = "지급 경험치")
    private Long xpAmount;
}
