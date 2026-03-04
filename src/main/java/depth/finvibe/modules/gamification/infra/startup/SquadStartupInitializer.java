package depth.finvibe.modules.gamification.infra.startup;

import java.nio.charset.StandardCharsets;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import depth.finvibe.modules.gamification.application.port.out.SquadRepository;
import depth.finvibe.modules.gamification.domain.Squad;
import depth.finvibe.common.gamification.lock.DistributedLockManager;
import depth.finvibe.common.gamification.lock.LockAcquisitionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class SquadStartupInitializer implements ApplicationRunner {

    private static final String LOCK_KEY = "gamification_squad_init";
    private static final String SEED_PATH = "classpath:seed/squads.json";

    private final DistributedLockManager distributedLockManager;
    private final SquadRepository squadRepository;
    private final ObjectMapper objectMapper;
    @Qualifier("webApplicationContext")
    private final ResourceLoader resourceLoader;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            distributedLockManager.executeWithLock(LOCK_KEY, () -> {
                // Idempotency 체크
                if (!squadRepository.findAll().isEmpty()) {
                    log.info("스쿼드 데이터가 이미 존재하여 초기화를 건너뜁니다.");
                    return null;
                }

                log.info("스쿼드 초기화를 시작합니다.");

                // JSON 로드
                SeedSquads seedSquads = loadSeedSquads();

                // 데이터 검증
                if (seedSquads == null || seedSquads.squads == null || seedSquads.squads.isEmpty()) {
                    log.warn("스쿼드 시드 데이터가 비어 있어 초기화를 건너뜁니다.");
                    return null;
                }

                // Squad 생성 및 저장
                for (SeedSquad seedSquad : seedSquads.squads) {
                    if (seedSquad.name != null && !seedSquad.name.isBlank()
                        && seedSquad.region != null && !seedSquad.region.isBlank()) {
                        saveSquad(seedSquad);
                    }
                }

                log.info("스쿼드 초기화가 완료되었습니다. 총 {}건 처리되었습니다.", seedSquads.squads.size());
                return null;
            });
        } catch (LockAcquisitionException ex) {
            log.warn("락 획득에 실패하여 스쿼드 초기화를 건너뜁니다.", ex);
        }
    }

    private SeedSquads loadSeedSquads() {
        Resource resource = resourceLoader.getResource(SEED_PATH);
        try {
            byte[] bytes = resource.getInputStream().readAllBytes();
            String json = new String(bytes, StandardCharsets.UTF_8).trim();
            return objectMapper.readValue(json, SeedSquads.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load squad seed data", ex);
        }
    }

    private void saveSquad(SeedSquad seedSquad) {
        Squad squad = Squad.builder()
                .name(seedSquad.name)
                .region(seedSquad.region)
                .build();

        squadRepository.save(squad);
    }

    private static class SeedSquads {
        public List<SeedSquad> squads;
    }

    private static class SeedSquad {
        public String name;
        public String region;
    }
}
