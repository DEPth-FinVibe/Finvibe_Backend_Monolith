package depth.finvibe.modules.study.api.external;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.study.application.port.in.MetricQueryUseCase;
import depth.finvibe.modules.study.dto.StudyMetricDto;

@Tag(name = "학습 지표", description = "학습 지표 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/study/metrics")
public class StudyMetricQueryController {

    private final MetricQueryUseCase metricQueryUseCase;

    @Operation(summary = "내 학습 지표 조회", description = "인증된 사용자의 누적 학습 지표를 조회합니다")
    @GetMapping("/me")
    public StudyMetricDto.MyMetricResponse getMyMetric(@AuthenticatedUser Requester requester) {
        return metricQueryUseCase.getMyMetric(requester);
    }
}
