package depth.finvibe.modules.market.application;

import depth.finvibe.modules.market.application.port.in.CategoryCommandUseCase;
import depth.finvibe.modules.market.application.port.out.CategoryRepository;
import depth.finvibe.modules.market.domain.Category;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService implements CategoryCommandUseCase {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean existsAny() {
        return categoryRepository.existsAny();
    }

    @Override
    @Transactional
    public void bulkInsert(List<Category> categories) {
        categoryRepository.saveAll(categories);
    }
}
