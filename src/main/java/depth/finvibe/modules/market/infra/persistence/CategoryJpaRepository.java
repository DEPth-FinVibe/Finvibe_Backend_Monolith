package depth.finvibe.modules.market.infra.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import depth.finvibe.modules.market.domain.Category;

public interface CategoryJpaRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);
}
