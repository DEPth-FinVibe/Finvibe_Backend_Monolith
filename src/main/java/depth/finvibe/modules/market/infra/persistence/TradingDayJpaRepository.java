package depth.finvibe.modules.market.infra.persistence;

import depth.finvibe.modules.market.domain.TradingDay;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TradingDayJpaRepository extends JpaRepository<TradingDay, LocalDate> {
}
