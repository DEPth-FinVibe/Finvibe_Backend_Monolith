package depth.finvibe.modules.study.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.study.application.port.in.MetricCommandUseCase;
import depth.finvibe.modules.study.application.port.in.MetricQueryUseCase;
import depth.finvibe.modules.study.application.port.out.LessonRepository;
import depth.finvibe.modules.study.application.port.out.StudyMetricRepository;
import depth.finvibe.modules.study.domain.StudyMetric;
import depth.finvibe.modules.study.domain.error.StudyErrorCode;
import depth.finvibe.modules.study.dto.StudyMetricDto;
import depth.finvibe.common.gamification.error.DomainException;
import depth.finvibe.common.gamification.error.GlobalErrorCode;

@Service
@RequiredArgsConstructor
public class StudyMetricService implements MetricCommandUseCase, MetricQueryUseCase {
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private final LessonRepository lessonRepository;
    private final StudyMetricRepository studyMetricRepository;

    @Override
    @Transactional(readOnly = true)
    public StudyMetricDto.MyMetricResponse getMyMetric(Requester requester) {
        UUID userId = requester.getUuid();
        return studyMetricRepository.findByUserId(userId)
                .map(StudyMetricDto.MyMetricResponse::from)
                .orElseGet(StudyMetricDto.MyMetricResponse::empty);
    }

    @Override
    @Transactional
    public void oneMinutePing(Requester requester, Long lessonId) {
        lessonRepository.findById(lessonId)
                .orElseThrow(() -> new DomainException(GlobalErrorCode.NOT_FOUND));

        UUID userId = requester.getUuid();
        StudyMetric studyMetric = studyMetricRepository.findByUserId(userId)
                .orElseGet(() -> StudyMetric.of(userId));

        Instant now = Instant.now();
        Instant lastPingAt = studyMetric.getLastPingAt();
        if (lastPingAt != null && lastPingAt.isAfter(now.minus(ONE_MINUTE))) {
            throw new DomainException(StudyErrorCode.PING_TOO_FREQUENT);
        }

        studyMetric.addTimeSpentMinutes(1L);
        studyMetric.updateLastPingAt(now);
        studyMetricRepository.save(studyMetric);
    }
}
