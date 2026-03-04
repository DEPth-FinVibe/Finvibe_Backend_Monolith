package depth.finvibe.common.insight.infra.persistence;

import depth.finvibe.common.insight.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryJpaRepository extends JpaRepository<Category, Long> {
}
