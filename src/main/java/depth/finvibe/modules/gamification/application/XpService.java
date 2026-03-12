package depth.finvibe.modules.gamification.application;

import depth.finvibe.modules.gamification.application.port.in.XpCommandUseCase;
import depth.finvibe.modules.gamification.application.port.in.XpQueryUseCase;
import depth.finvibe.modules.gamification.application.port.out.SquadRankingHistoryRepository;
import depth.finvibe.modules.gamification.application.port.out.SquadXpRepository;
import depth.finvibe.modules.gamification.application.port.out.UserServiceClient;
import depth.finvibe.modules.gamification.application.port.out.UserSquadRepository;
import depth.finvibe.modules.gamification.application.port.out.UserXpAwardRepository;
import depth.finvibe.modules.gamification.application.port.out.UserXpRankingSnapshotRepository;
import depth.finvibe.modules.gamification.application.port.out.UserXpRepository;
import depth.finvibe.modules.gamification.domain.SquadRankingHistory;
import depth.finvibe.modules.gamification.domain.SquadXp;
import depth.finvibe.modules.gamification.domain.UserSquad;
import depth.finvibe.modules.gamification.domain.UserXp;
import depth.finvibe.modules.gamification.domain.UserXpAward;
import depth.finvibe.modules.gamification.domain.UserXpRankingSnapshot;
import depth.finvibe.modules.gamification.domain.enums.RankingPeriod;
import depth.finvibe.modules.gamification.domain.error.GamificationErrorCode;
import depth.finvibe.modules.gamification.domain.vo.Xp;
import depth.finvibe.modules.gamification.dto.XpDto;
import depth.finvibe.common.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class XpService implements XpCommandUseCase, XpQueryUseCase {

    private final UserXpAwardRepository userXpAwardRepository;
    private final UserXpRepository userXpRepository;
    private final UserXpRankingSnapshotRepository userXpRankingSnapshotRepository;
    private final SquadXpRepository squadXpRepository;
    private final SquadRankingHistoryRepository squadRankingHistoryRepository;
    private final UserSquadRepository userSquadRepository;
    private final UserServiceClient userServiceClient;

    @Override
    @Transactional
    public void grantUserXp(UUID userId, Long value, String reason) {
        Xp xp = Xp.of(value, reason);
        UserXpAward userXpAward = UserXpAward.of(userId, xp);
        userXpAwardRepository.save(userXpAward);

        updateUserXp(userId, value);
        updateSquadXp(userId, value);
    }

    @Override
    @Transactional
    public void updateWeeklySquadRanking() {
        settleSquadRankingsAndResetWeeklyXp();
        resetAllUsersWeeklyXp();
    }

    @Override
    @Transactional
    public void refreshUserRankingSnapshots() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        refreshUserRankingSnapshotByPeriod(RankingPeriod.WEEKLY, now);
        refreshUserRankingSnapshotByPeriod(RankingPeriod.MONTHLY, now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<XpDto.SquadRankingResponse> getSquadXpRanking() {
        List<SquadXp> allSquadXp = squadXpRepository.findAllByOrderByTotalXpDesc();

        List<XpDto.SquadRankingResponse> result = new ArrayList<>(allSquadXp.size());
        for (int i = 0; i < allSquadXp.size(); i++) {
            SquadXp squadXp = allSquadXp.get(i);
            int currentRanking = i + 1;
            int previousRanking = squadRankingHistoryRepository
                    .findFirstBySquadIdOrderByRecordDateDescIdDesc(squadXp.getSquadId())
                    .map(SquadRankingHistory::getRanking)
                    .orElse(currentRanking);

            result.add(toSquadRankingResponse(squadXp, currentRanking, previousRanking));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<XpDto.ContributionRankingResponse> getSquadContributionRanking(UUID userId) {
        UserSquad userSquad = userSquadRepository.findByUserId(userId)
                .orElseThrow(() -> new DomainException(GamificationErrorCode.USER_SQUAD_NOT_FOUND));

        Long squadId = userSquad.getSquad().getId();
        List<UserSquad> squadMembers = userSquadRepository.findAllBySquadId(squadId);

        List<UUID> memberIds = new ArrayList<>(squadMembers.size());
        for (UserSquad member : squadMembers) {
            memberIds.add(member.getUserId());
        }

        List<UserXp> memberXps = userXpRepository.findAllByUserIdInOrderByWeeklyXpDesc(memberIds);
        Map<UUID, String> nicknames = userServiceClient.getNicknamesByIds(memberIds);

        List<XpDto.ContributionRankingResponse> result = new ArrayList<>(memberXps.size());
        for (int i = 0; i < memberXps.size(); i++) {
            UserXp userXp = memberXps.get(i);
            String nickname = nicknames.getOrDefault(userXp.getUserId(), "이름 없음");
            result.add(toContributionRankingResponse(userXp, i + 1, nickname));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public XpDto.Response getUserXp(UUID userId) {
        UserXp userXp = findOrCreateUserXp(userId);

        return XpDto.Response.from(userXp);
    }

    @Override
    @Transactional(readOnly = true)
    public List<XpDto.UserRankingResponse> getUserXpRanking(RankingPeriod period) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        LocalDateTime currentPeriodStart = getCurrentStart(period, now);
        LocalDateTime currentPeriodEnd = getCurrentEnd(period, currentPeriodStart);

        List<UserXpAwardRepository.UserPeriodXp> rankedUsers = userXpAwardRepository
                .findUserPeriodXpRankingBetween(currentPeriodStart, currentPeriodEnd, 100);

        if (rankedUsers.isEmpty()) {
            return List.of();
        }

        List<UUID> userIds = rankedUsers.stream()
                .map(UserXpAwardRepository.UserPeriodXp::userId)
                .toList();

        Map<UUID, UserXp> userXpMap = new HashMap<>();
        for (UserXp userXp : userXpRepository.findAllByUserIdIn(userIds)) {
            userXpMap.put(userXp.getUserId(), userXp);
        }

        List<XpDto.UserRankingResponse> result = new ArrayList<>(rankedUsers.size());
        for (int i = 0; i < rankedUsers.size(); i++) {
            UserXpAwardRepository.UserPeriodXp rankedUser = rankedUsers.get(i);
            UserXp userXp = userXpMap.get(rankedUser.userId());

            result.add(XpDto.UserRankingResponse.builder()
                    .userId(rankedUser.userId())
                    .nickname(userXp != null ? userXp.getNickname() : "이름 없음")
                    .ranking(i + 1)
                    .currentXp(userXp != null ? userXp.getTotalXp() : rankedUser.xp())
                    .periodXp(rankedUser.xp())
                    .previousPeriodXp(0L)
                    .growthRate(null)
                    .build());
        }
        return result;
    }

    private void updateUserXp(UUID userId, Long amount) {
        UserXp userXp = findOrCreateUserXp(userId);
        userXp.addXp(amount);
        userXpRepository.save(userXp);
    }

    private void updateSquadXp(UUID userId, Long amount) {
        userSquadRepository.findByUserId(userId).ifPresent(userSquad -> {
            if (userSquad.getSquad() == null || userSquad.getSquad().getId() == null) {
                log.warn("스쿼드 XP 갱신 생략 - 사용자 스쿼드 정보가 유효하지 않음: {}", userId);
                return;
            }

            Long squadId = userSquad.getSquad().getId();
            SquadXp squadXp = squadXpRepository.findBySquadId(squadId)
                    .orElseGet(() -> SquadXp.builder()
                            .squad(userSquad.getSquad())
                            .build());
            squadXp.addXp(amount);
            squadXpRepository.save(squadXp);
        });
    }

    private UserXp findOrCreateUserXp(UUID userId) {
        if (userXpRepository.findByUserId(userId).isPresent()) {
            return userXpRepository.findByUserId(userId).get();
        }

        String nickname = userServiceClient.getNickname(userId)
                .orElseGet(() -> {
                    log.error("사용자 닉네임 조회 실패: {}", userId);
                    return "이름 없음";
                });
                
        return UserXp.of(userId, nickname);
    }

    private void settleSquadRankingsAndResetWeeklyXp() {
        List<SquadXp> allSquadXp = squadXpRepository.findAllByOrderByTotalXpDesc();

        for (int i = 0; i < allSquadXp.size(); i++) {
            SquadXp squadXp = allSquadXp.get(i);
            int ranking = i + 1;

            saveRankingHistory(squadXp, ranking);
            squadXp.resetWeeklyXp();
        }
        squadXpRepository.saveAll(allSquadXp);
    }

    private void resetAllUsersWeeklyXp() {
        List<UserXp> allUserXp = userXpRepository.findAll();
        allUserXp.forEach(UserXp::resetWeeklyXp);
        userXpRepository.saveAll(allUserXp);
    }

    private void saveRankingHistory(SquadXp squadXp, int ranking) {
        SquadRankingHistory history = SquadRankingHistory.of(
                squadXp.getSquadId(),
                ranking,
                squadXp.getTotalXp());
        squadRankingHistoryRepository.save(history);
    }

    private XpDto.SquadRankingResponse toSquadRankingResponse(
            SquadXp squadXp,
            int currentRanking,
            int previousRanking) {
        return XpDto.SquadRankingResponse.builder()
                .squadId(squadXp.getSquadId())
                .squadName(squadXp.getSquad().getName())
                .currentRanking(currentRanking)
                .totalXp(squadXp.getTotalXp())
                .weeklyXp(squadXp.getWeeklyXp())
                .weeklyXpChangeRate(squadXp.getWeeklyXpChangeRate())
                .rankingChange(previousRanking - currentRanking)
                .build();
    }

    private XpDto.ContributionRankingResponse toContributionRankingResponse(UserXp userXp, int ranking, String nickname) {
        return XpDto.ContributionRankingResponse.builder()
                .nickname(nickname)
                .ranking(ranking)
                .weeklyContributionXp(userXp.getWeeklyXp())
                .build();
    }

    private Double calculateGrowthRate(Long currentPeriodXp, Long previousPeriodXp) {
        if (previousPeriodXp == null || previousPeriodXp == 0L) {
            return null;
        }
        return ((double) (currentPeriodXp - previousPeriodXp) / previousPeriodXp) * 100;
    }

    private LocalDateTime getCurrentStart(RankingPeriod rankingPeriod, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        switch (rankingPeriod) {
            case DAILY:
                return today.atStartOfDay();
            case WEEKLY:
                return today.minusDays(today.getDayOfWeek().getValue() - 1L).atStartOfDay();
            case MONTHLY:
                return today.withDayOfMonth(1).atStartOfDay();
            default:
                throw new IllegalArgumentException("지원하지 않는 랭킹 기간: " + rankingPeriod);
        }
    }

    private LocalDateTime getCurrentEnd(RankingPeriod rankingPeriod, LocalDateTime currentStart) {
        switch (rankingPeriod) {
            case DAILY:
                return currentStart.plusDays(1);
            case WEEKLY:
                return currentStart.plusWeeks(1);
            case MONTHLY:
                return currentStart.plusMonths(1);
            default:
                throw new IllegalArgumentException("지원하지 않는 랭킹 기간: " + rankingPeriod);
        }
    }

    private LocalDateTime getPreviousStart(RankingPeriod rankingPeriod, LocalDateTime currentStart) {
        switch (rankingPeriod) {
            case DAILY:
                return currentStart.minusDays(1);
            case WEEKLY:
                return currentStart.minusWeeks(1);
            case MONTHLY:
                return currentStart.minusMonths(1);
            default:
                throw new IllegalArgumentException("지원하지 않는 랭킹 기간: " + rankingPeriod);
        }
    }

    private void refreshUserRankingSnapshotByPeriod(RankingPeriod rankingPeriod, LocalDateTime now) {
        LocalDateTime currentStart = getCurrentStart(rankingPeriod, now);
        LocalDateTime currentEnd = getCurrentEnd(rankingPeriod, currentStart);
        LocalDateTime previousStart = getPreviousStart(rankingPeriod, currentStart);

        List<UserXpAwardRepository.UserPeriodXp> rankedUsers = userXpAwardRepository
                .findUserPeriodXpRankingBetween(currentStart, currentEnd, Integer.MAX_VALUE);

        List<UserXpAwardRepository.UserPeriodXp> uniqueRankedUsers = deduplicateRankedUsers(rankedUsers, rankingPeriod, currentStart.toLocalDate());

        if (uniqueRankedUsers.isEmpty()) {
            userXpRankingSnapshotRepository.replaceSnapshots(rankingPeriod, currentStart.toLocalDate(), List.of());
            return;
        }

        List<UUID> userIds = uniqueRankedUsers.stream()
                .map(UserXpAwardRepository.UserPeriodXp::userId)
                .toList();

        Map<UUID, Long> previousXpMap = userXpAwardRepository.findUserPeriodXpMapBetween(
                userIds,
                previousStart,
                currentStart);

        Map<UUID, UserXp> userXpMap = new HashMap<>();
        for (UserXp userXp : userXpRepository.findAllByUserIdIn(userIds)) {
            userXpMap.put(userXp.getUserId(), userXp);
        }

        List<UserXpRankingSnapshot> snapshots = new ArrayList<>(uniqueRankedUsers.size());
        for (int i = 0; i < uniqueRankedUsers.size(); i++) {
            UserXpAwardRepository.UserPeriodXp rankedUser = uniqueRankedUsers.get(i);
            UserXp userXp = userXpMap.get(rankedUser.userId());
            long previousPeriodXp = previousXpMap.getOrDefault(rankedUser.userId(), 0L);

            snapshots.add(UserXpRankingSnapshot.of(
                    rankingPeriod,
                    currentStart.toLocalDate(),
                    currentEnd.toLocalDate().minusDays(1),
                    rankedUser.userId(),
                    userXp != null ? userXp.getNickname() : "이름 없음",
                    i + 1,
                    userXp != null ? userXp.getTotalXp() : rankedUser.xp(),
                    rankedUser.xp(),
                    previousPeriodXp,
                    calculateGrowthRate(rankedUser.xp(), previousPeriodXp),
                    now));
        }

        userXpRankingSnapshotRepository.replaceSnapshots(rankingPeriod, currentStart.toLocalDate(), snapshots);
    }

    private List<UserXpAwardRepository.UserPeriodXp> deduplicateRankedUsers(
            List<UserXpAwardRepository.UserPeriodXp> rankedUsers,
            RankingPeriod rankingPeriod,
            LocalDate periodStartDate) {
        LinkedHashMap<UUID, UserXpAwardRepository.UserPeriodXp> deduplicated = new LinkedHashMap<>();

        for (UserXpAwardRepository.UserPeriodXp rankedUser : rankedUsers) {
            deduplicated.putIfAbsent(rankedUser.userId(), rankedUser);
        }

        if (deduplicated.size() < rankedUsers.size()) {
            log.warn(
                    "랭킹 스냅샷 집계 중 사용자 중복 감지 - 기간: {}, 시작일: {}, 입력건수: {}, 중복제거후: {}",
                    rankingPeriod,
                    periodStartDate,
                    rankedUsers.size(),
                    deduplicated.size());
        }

        return List.copyOf(deduplicated.values());
    }
}
