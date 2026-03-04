package depth.finvibe.common.insight.application.port.out;

import depth.finvibe.common.insight.domain.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Optional<Category> findById(Long id);

    List<Category> findAll();
}
