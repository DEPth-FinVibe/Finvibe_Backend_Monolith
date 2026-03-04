package depth.finvibe.modules.market.application.port.out;

import java.util.List;
import java.util.Optional;

import depth.finvibe.modules.market.domain.Category;

public interface CategoryRepository {
    List<Category> findAll();

    Optional<Category> findById(Long categoryId);

    Optional<Category> findByName(String name);

    boolean existsAny();

    List<Category> saveAll(List<Category> categories);
}
