package depth.finvibe.modules.market.infra.persistence;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import depth.finvibe.modules.market.application.port.out.CategoryRepository;
import depth.finvibe.modules.market.domain.Category;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {
    private final MarketCategoryJpaRepository jpaRepository;

    @Override
    public List<Category> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public Optional<Category> findById(Long categoryId) {
        return jpaRepository.findById(categoryId);
    }

    @Override
    public Optional<Category> findByName(String name) {
        return jpaRepository.findByName(name);
    }

    @Override
    public boolean existsAny() {
        return jpaRepository.count() > 0;
    }

    @Override
    public List<Category> saveAll(List<Category> categories) {
        return jpaRepository.saveAll(categories);
    }
}
