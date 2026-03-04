package depth.finvibe.modules.study.application.port.in;

import depth.finvibe.boot.security.Requester;

public interface MetricCommandUseCase {
    void oneMinutePing(Requester requester, Long lessonId);
}
