package depth.finvibe.modules.study.application.port.in;

import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.study.dto.StudyMetricDto;

public interface MetricQueryUseCase {
    StudyMetricDto.MyMetricResponse getMyMetric(Requester requester);
}
