package depth.finvibe.modules.asset.application;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import depth.finvibe.modules.asset.application.port.in.UserProfitRankingQueryUseCase;
import depth.finvibe.modules.asset.application.port.out.UserProfitRankingQueryRepository;
import depth.finvibe.modules.asset.domain.UserProfitRanking;
import depth.finvibe.modules.asset.domain.enums.UserProfitRankType;
import depth.finvibe.modules.asset.dto.UserProfitRankingDto;

@Service
@RequiredArgsConstructor
public class UserProfitRankingQueryService implements UserProfitRankingQueryUseCase {
  private final UserProfitRankingQueryRepository userProfitRankingQueryRepository;

  @Override
  public UserProfitRankingDto.RankingPageResponse getUserProfitRankings(
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
