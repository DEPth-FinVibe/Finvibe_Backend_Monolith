package depth.finvibe.modules.gamification.application;

import depth.finvibe.boot.security.Requester;
import depth.finvibe.modules.user.domain.enums.UserRole;
import depth.finvibe.modules.gamification.application.port.in.SquadCommandUseCase;
import depth.finvibe.modules.gamification.application.port.in.SquadQueryUseCase;
import depth.finvibe.modules.gamification.application.port.in.XpCommandUseCase;
import depth.finvibe.modules.gamification.application.port.out.SquadRepository;
import depth.finvibe.modules.gamification.application.port.out.UserSquadRepository;
import depth.finvibe.modules.gamification.domain.Squad;
import depth.finvibe.modules.gamification.domain.UserSquad;
import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.modules.gamification.dto.SquadDto;
import depth.finvibe.common.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SquadService implements SquadCommandUseCase, SquadQueryUseCase {

    private static final Long FIRST_SQUAD_JOIN_REWARD_XP = 100L;
    private static final String FIRST_SQUAD_JOIN_REWARD_REASON = "[스쿼드] 첫 가입 축하 보상";

    private final SquadRepository squadRepository;
    private final UserSquadRepository userSquadRepository;
    private final XpCommandUseCase xpCommandUseCase;

    @Override
    @Transactional
    public void joinSquad(Long squadId, Requester requester) {
        Squad squad = squadRepository.findById(squadId)
                .orElseThrow(() -> new DomainException(GamificationErrorCode.SQUAD_NOT_FOUND));

        Optional<UserSquad> existingUserSquad = userSquadRepository.findByUserId(requester.getUuid());
        boolean isFirstJoin = existingUserSquad.isEmpty();

        UserSquad userSquad = existingUserSquad
                .orElseGet(() -> UserSquad.builder().userId(requester.getUuid()).build());

        userSquad.changeSquad(squad);
        userSquadRepository.save(userSquad);

        if (isFirstJoin) {
            xpCommandUseCase.grantUserXp(
                    requester.getUuid(),
                    FIRST_SQUAD_JOIN_REWARD_XP,
                    FIRST_SQUAD_JOIN_REWARD_REASON);
        }
    }

    @Override
    @Transactional
    public Long createSquad(String name, String region, Requester requester) {
        validateAdmin(requester);
        validateSquadName(name);
        validateSquadRegion(region);

        Squad squad = Squad.builder()
                .name(name)
                .region(region)
                .build();

        squadRepository.save(squad);
        return squad.getId();
    }

    @Override
    @Transactional
    public void updateSquad(Long squadId, String name, String region, Requester requester) {
        validateAdmin(requester);

        Squad squad = squadRepository.findById(squadId)
                .orElseThrow(() -> new DomainException(GamificationErrorCode.SQUAD_NOT_FOUND));

        squad.updateInfo(name, region);
        squadRepository.save(squad);
    }

    @Override
    @Transactional
    public void deleteSquad(Long squadId, Requester requester) {
        validateAdmin(requester);

        Squad squad = squadRepository.findById(squadId)
                .orElseThrow(() -> new DomainException(GamificationErrorCode.SQUAD_NOT_FOUND));

        squadRepository.delete(squad);
    }

    private void validateAdmin(Requester requester) {
        if (!requester.getRole().equals(UserRole.ADMIN)) {
            throw new DomainException(GamificationErrorCode.FORBIDDEN_ACCESS);
        }
    }

    private void validateSquadName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException(GamificationErrorCode.SQUAD_NAME_IS_EMPTY);
        }
    }

    private void validateSquadRegion(String region) {
        if (region == null || region.isBlank()) {
            throw new DomainException(GamificationErrorCode.SQUAD_REGION_IS_EMPTY);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SquadDto.Response getUserSquad(UUID userId) {
        UserSquad userSquad = userSquadRepository.findByUserId(userId)
                .orElseThrow(() -> new DomainException(GamificationErrorCode.USER_SQUAD_NOT_FOUND));

        Squad squad = userSquad.getSquad();
        return SquadDto.Response.builder()
                .id(squad.getId())
                .name(squad.getName())
                .region(squad.getRegion())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SquadDto.Response> getAllSquads() {
        return squadRepository.findAll().stream()
                .map(squad -> SquadDto.Response.builder()
                        .id(squad.getId())
                        .name(squad.getName())
                        .region(squad.getRegion())
                        .build())
                .collect(Collectors.toList());
    }
}
