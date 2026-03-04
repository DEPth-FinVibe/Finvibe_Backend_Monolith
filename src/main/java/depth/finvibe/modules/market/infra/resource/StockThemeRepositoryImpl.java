package depth.finvibe.modules.market.infra.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import depth.finvibe.modules.market.application.port.out.StockThemeRepository;

@Repository
public class StockThemeRepositoryImpl implements StockThemeRepository {

    private static final String RESOURCE_PATH = "seed/kospi_themes.json";

    private final ObjectMapper objectMapper;

    public StockThemeRepositoryImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, String> findSymbolToThemeMap() {
        Resource resource = new ClassPathResource(RESOURCE_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            List<ThemeSeed> seeds = objectMapper.readValue(inputStream, new TypeReference<>() {});
            return seeds.stream()
                    .filter(seed -> seed.code() != null && !seed.code().isBlank())
                    .collect(Collectors.toMap(
                            ThemeSeed::code,
                            seed -> Objects.requireNonNullElse(seed.theme(), ""),
                            (existing, replacement) -> existing,
                            LinkedHashMap::new
                    ));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read stock theme resource: " + RESOURCE_PATH, e);
        }
    }

    private record ThemeSeed(String code, String name, String theme) {}
}
