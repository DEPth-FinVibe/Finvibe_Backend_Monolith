package depth.finvibe.modules.study.api.external;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import depth.finvibe.boot.security.AuthenticatedUser;
import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.study.application.port.in.MetricCommandUseCase;

@Tag(name = "학습 지표", description = "학습 시간 기록 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/study/lessons/{lessonId}/metrics")
public class StudyMetricController {

    private final MetricCommandUseCase metricCommandUseCase;

    @Operation(summary = "1분 학습 핑", description = "1분 단위 학습 시간을 기록합니다")
    @PostMapping("/one-minute")
    public void oneMinutePing(
            @PathVariable Long lessonId,
            @AuthenticatedUser Requester requester
    ) {
        metricCommandUseCase.oneMinutePing(requester, lessonId);
    }
}
