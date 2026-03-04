package depth.finvibe.modules.gamification.infra.startup;

import java.time.LocalDate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import depth.finvibe.modules.gamification.application.port.in.ChallengeCommandUseCase;
import depth.finvibe.modules.gamification.application.port.out.PersonalChallengeRepository;
import depth.finvibe.modules.gamification.domain.vo.Period;
import depth.finvibe.common.gamification.lock.DistributedLockManager;
import depth.finvibe.common.gamification.lock.LockAcquisitionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersonalChallengeStartupInitializer implements ApplicationRunner {

    private static final String LOCK_KEY = "gamification_personal_challenge_init";

    private final DistributedLockManager distributedLockManager;
    private final PersonalChallengeRepository personalChallengeRepository;
    private final ChallengeCommandUseCase challengeCommandUseCase;

    @Override
    public void run(ApplicationArguments args) {
        try {
            distributedLockManager.executeWithLock(LOCK_KEY, () -> {
                Period currentPeriod = Period.ofWeek(LocalDate.now());
                if (personalChallengeRepository.existsByPeriod(currentPeriod)) {
                    log.info("Personal challenges already exist for period {}", currentPeriod);
                    return null;
                }

                challengeCommandUseCase.generatePersonalChallenges();
                log.info("Personal challenges generated for period {}", currentPeriod);
                return null;
            });
        } catch (LockAcquisitionException ex) {
            log.warn("Skip personal challenge initialization due to lock acquisition failure", ex);
        }
    }
}
