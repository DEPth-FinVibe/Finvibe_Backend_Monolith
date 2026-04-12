package depth.finvibe.modules.user.infra.persistence;

import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import depth.finvibe.modules.user.domain.QTokenFamily;
import depth.finvibe.modules.user.domain.TokenFamily;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static depth.finvibe.modules.user.domain.QTokenFamily.tokenFamily;

@Repository
@RequiredArgsConstructor
public class TokenFamilyQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<TokenFamily> findAvailableByUserId(UUID userId) {
        return queryFactory.selectFrom(tokenFamily)
            .where(
                tokenFamily.userId.eq(userId),
                tokenFamily.expiresAt.after(Instant.now())
            )
            .fetch();
    }
}
