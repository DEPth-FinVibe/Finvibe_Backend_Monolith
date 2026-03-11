package depth.finvibe.modules.news.infra.client;

import depth.finvibe.modules.market.application.port.in.CategoryQueryUseCase;
import depth.finvibe.modules.news.application.port.out.CategoryCatalogPort;
import depth.finvibe.common.insight.domain.CategoryInfo;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class CategoryCatalogClientImpl implements CategoryCatalogPort {

    public static final String CACHE_NAME = "marketCategories";
    private static final String CACHE_KEY = "all";
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP_TYPE = new TypeReference<>() {};

    private final CategoryQueryUseCase categoryQueryUseCase;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'" + CACHE_KEY + "'")
    public List<CategoryInfo> getAll() {
        return fetchCategories();
    }

    @Override
    public List<CategoryInfo> refresh() {
        List<CategoryInfo> latest = fetchCategories();
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.put(CACHE_KEY, latest);
        }
        return latest;
    }

    private List<CategoryInfo> fetchCategories() {
        List<Map<String, Object>> categories = objectMapper.convertValue(
                categoryQueryUseCase.getAllCategoriesForInternal(),
                LIST_OF_MAP_TYPE
        );
        return Objects.requireNonNull(categories, "category response must not be null").stream()
                .map(this::toCategoryInfo)
                .toList();
    }

    private CategoryInfo toCategoryInfo(Map<String, Object> response) {
        Object categoryId = response.get("categoryId");
        Object categoryName = response.get("categoryName");

        Long parsedCategoryId = categoryId == null ? null : Long.valueOf(categoryId.toString());
        String parsedCategoryName = categoryName == null ? null : categoryName.toString();
        return new CategoryInfo(parsedCategoryId, parsedCategoryName);
    }
}
