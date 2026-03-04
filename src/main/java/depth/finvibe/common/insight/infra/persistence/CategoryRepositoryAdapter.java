package depth.finvibe.common.insight.infra.persistence;

import depth.finvibe.common.insight.application.port.out.CategoryRepository;
import depth.finvibe.common.insight.domain.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository categoryJpaRepository;

    @Override
    public Optional<Category> findById(Long id) {
        return categoryJpaRepository.findById(id);
    }

    @Override
    public List<Category> findAll() {
        return categoryJpaRepository.findAll();
    }
}
