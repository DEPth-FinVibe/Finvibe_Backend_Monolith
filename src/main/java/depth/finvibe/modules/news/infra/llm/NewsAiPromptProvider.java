package depth.finvibe.modules.news.infra.llm;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class NewsAiPromptProvider {

    private static final String SYSTEM_PROMPT_PATH = "classpath:prompts/news-ai-analysis-system.txt";
    private static final String USER_PROMPT_PATH = "classpath:prompts/news-ai-analysis-user.txt";

    private final ResourceLoader resourceLoader;

    public NewsAiPromptProvider(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String getSystemPrompt(String categoryList, String keywordList) {
        return loadPrompt(SYSTEM_PROMPT_PATH)
                .replace("{{category_list}}", categoryList)
                .replace("{{keyword_list}}", keywordList);
    }

    public String getUserPrompt(String content) {
        return loadPrompt(USER_PROMPT_PATH).replace("{{news_content}}", content);
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
