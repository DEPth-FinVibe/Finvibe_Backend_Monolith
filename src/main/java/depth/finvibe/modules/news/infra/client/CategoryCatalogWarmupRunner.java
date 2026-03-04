package depth.finvibe.modules.news.infra.client;

import depth.finvibe.modules.news.application.port.out.CategoryCatalogPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "market.category.warmup.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class CategoryCatalogWarmupRunner implements ApplicationRunner {

    private final CategoryCatalogPort categoryCatalogPort;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int loadedCount = categoryCatalogPort.refresh().size();
            log.info("Loaded {} categories from market internal API.", loadedCount);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to preload market categories at startup", ex);
        }
    }
}
