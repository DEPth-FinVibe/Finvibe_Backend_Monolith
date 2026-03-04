package depth.finvibe.modules.news.infra.llm;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class ThemePromptProvider {

    private static final String SYSTEM_PROMPT_PATH = "classpath:prompts/theme-analysis-system.txt";
    private static final String USER_PROMPT_PATH = "classpath:prompts/theme-analysis-user.txt";

    private final ResourceLoader resourceLoader;

    public ThemePromptProvider(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String getSystemPrompt() {
        return loadPrompt(SYSTEM_PROMPT_PATH);
    }

    public String getUserPrompt(String categoryName, String newsTitles) {
        return loadPrompt(USER_PROMPT_PATH)
                .replace("{{category_name}}", categoryName)
                .replace("{{news_titles}}", newsTitles);
    }

    private String loadPrompt(String path) {
        Resource resource = resourceLoader.getResource(path);
        try {
            byte[] bytes = resource.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load prompt: " + path, ex);
        }
    }
}
