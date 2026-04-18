package depth.finvibe.modules.asset.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import depth.finvibe.modules.asset.application.port.in.UserProfitRankingQueryUseCase;
import depth.finvibe.modules.asset.application.port.out.UserNicknameClient;
import depth.finvibe.modules.asset.application.port.out.UserProfitRankingQueryRepository;
import depth.finvibe.modules.asset.domain.UserProfitRanking;
import depth.finvibe.modules.asset.domain.enums.UserProfitRankType;
import depth.finvibe.modules.asset.dto.UserProfitRankingDto;
import depth.finvibe.modules.asset.infra.redis.UserProfitRankingRedisRepository;
import depth.finvibe.modules.asset.infra.redis.UserProfitRankingRedisRepository.RankingEntry;
import depth.finvibe.modules.asset.infra.redis.UserProfitSummaryRedisRepository;
import depth.finvibe.modules.asset.infra.redis.UserProfitSummaryRedisRepository.ProfitSummary;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfitRankingQueryService implements UserProfitRankingQueryUseCase {
	private final UserProfitRankingQueryRepository userProfitRankingQueryRepository;
	private final UserProfitRankingRedisRepository userProfitRankingRedisRepository;
	private final UserProfitSummaryRedisRepository userProfitSummaryRedisRepository;
	private final UserNicknameClient userNicknameClient;

	@Override
	public UserProfitRankingDto.RankingPageResponse getUserProfitRankings(
		UserProfitRankType rankType,
		Pageable pageable
	) {
		if (rankType == UserProfitRankType.DAILY) {
			UserProfitRankingDto.RankingPageResponse redisResult = tryGetFromRedis(rankType, pageable);
			if (redisResult != null) {
				return redisResult;
			}
		}

		return getFromDatabase(rankType, pageable);
	}

	private UserProfitRankingDto.RankingPageResponse tryGetFromRedis(
		UserProfitRankType rankType,
		Pageable pageable
	) {
		try {
			long totalCount = userProfitRankingRedisRepository.getCount(rankType);
			if (totalCount == 0) {
				return null;
			}

			int offset = (int) pageable.getOffset();
			int size = pageable.getPageSize();
			if (offset >= totalCount) {
				return UserProfitRankingDto.RankingPageResponse.builder()
					.rankType(rankType)
					.page(pageable.getPageNumber())
					.size(size)
					.totalElements(totalCount)
					.totalPages((int) Math.ceil((double) totalCount / size))
					.items(List.of())
					.build();
			}

			List<RankingEntry> entries = userProfitRankingRedisRepository.getTopN(rankType, offset + size);
			List<RankingEntry> pageEntries = entries.size() > offset
				? entries.subList(offset, Math.min(entries.size(), offset + size))
				: List.of();

			if (pageEntries.isEmpty()) {
				return null;
			}

			Collection<UUID> userIds = pageEntries.stream()
				.map(RankingEntry::userId)
				.toList();
			Map<UUID, String> nicknames = userNicknameClient.getUserNicknamesByIds(userIds);
			if (nicknames == null) {
				nicknames = Map.of();
			}

			List<UserProfitRankingDto.RankingItem> items = new ArrayList<>(pageEntries.size());
			for (RankingEntry entry : pageEntries) {
				ProfitSummary summary = userProfitSummaryRedisRepository.get(entry.userId());
				BigDecimal profitLoss = summary != null ? summary.totalProfitLoss() : null;

				items.add(UserProfitRankingDto.RankingItem.builder()
					.rank(entry.rank())
					.userId(entry.userId())
					.nickname(nicknames.getOrDefault(entry.userId(), ""))
					.returnRate(BigDecimal.valueOf(entry.returnRate()))
					.profitLoss(profitLoss)
					.build());
			}

			return UserProfitRankingDto.RankingPageResponse.builder()
				.rankType(rankType)
				.page(pageable.getPageNumber())
				.size(size)
				.totalElements(totalCount)
				.totalPages((int) Math.ceil((double) totalCount / size))
				.items(items)
				.build();
		} catch (Exception e) {
			log.warn("Failed to get rankings from Redis, falling back to DB. rankType={}", rankType, e);
			return null;
		}
	}

	private UserProfitRankingDto.RankingPageResponse getFromDatabase(
		UserProfitRankType rankType,
		Pageable pageable
	) {
		Page<UserProfitRanking> rankings = userProfitRankingQueryRepository.findByRankType(rankType, pageable);
		List<UserProfitRankingDto.RankingItem> items = rankings.getContent().stream()
			.map(UserProfitRankingDto.RankingItem::from)
			.toList();

		return UserProfitRankingDto.RankingPageResponse.builder()
			.rankType(rankType)
			.page(rankings.getNumber())
			.size(rankings.getSize())
			.totalElements(rankings.getTotalElements())
			.totalPages(rankings.getTotalPages())
			.items(items)
			.build();
	}
}
