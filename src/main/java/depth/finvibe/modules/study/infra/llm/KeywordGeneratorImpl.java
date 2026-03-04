package depth.finvibe.modules.study.infra.llm;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import depth.finvibe.modules.study.application.port.out.KeywordGenerator;

@Component
public class KeywordGeneratorImpl implements KeywordGenerator {
    private static final int MAX_RETRY_COUNT = 2;
    private static final String SYSTEM_PROMPT_PATH = "classpath:prompts/study-keyword-generation-system.txt";
    private static final String USER_PROMPT_PATH = "classpath:prompts/study-keyword-generation-user.txt";
    private static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .rootElement(JsonArraySchema.builder()
                            .items(JsonObjectSchema.builder()
                                    .addStringProperty("keyword")
                                    .required("keyword")
                                    .build())
                            .build())
                    .build())
            .build();
    private static final TypeReference<List<Map<String, String>>> RESPONSE_TYPE = new TypeReference<>() {};

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public KeywordGeneratorImpl(
            @Qualifier("chatModel") ChatModel chatModel,
            ObjectMapper objectMapper,
            @Qualifier("webApplicationContext") ResourceLoader resourceLoader
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public List<String> generateKeywords(List<String> interestStocks) {
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                List<String> parsed = requestKeywords(interestStocks);
                if (isValid(parsed)) {
                    return parsed;
                }
            } catch (IllegalStateException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                if (attempt == MAX_RETRY_COUNT) {
                    break;
                }
            }
        }
        return fallbackKeywords(interestStocks);
    }

    private List<String> requestKeywords(List<String> interestStocks) {
        String systemMessage = loadPrompt(SYSTEM_PROMPT_PATH);
        String userMessage = loadPrompt(USER_PROMPT_PATH)
                .replace("{{interest_stocks}}", String.join(", ", interestStocks));

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(systemMessage),
                        UserMessage.from(userMessage)
                )
                .responseFormat(RESPONSE_FORMAT)
                .build();

        ChatResponse chatResponse = chatModel.chat(request);
        return parseResponse(chatResponse.aiMessage().text());
    }

    private List<String> parseResponse(String response) {
        try {
            List<Map<String, String>> parsed = objectMapper.readValue(response, RESPONSE_TYPE);
            return parsed.stream()
                    .map(item -> item.get("keyword"))
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            return List.of();
        }
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

    private boolean isValid(List<String> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }

        return items.stream().allMatch(StringUtils::hasText);
    }

    private List<String> fallbackKeywords(List<String> interestStocks) {
        if (interestStocks == null || interestStocks.isEmpty()) {
            return List.of(
                    "investing basics",
                    "portfolio management",
                    "risk management"
            );
        }

        List<String> derived = new ArrayList<>();
        for (String stock : interestStocks) {
            if (StringUtils.hasText(stock)) {
                derived.add(stock + " fundamentals");
            }
            if (derived.size() >= 3) {
                break;
            }
        }
        return derived.isEmpty()
                ? List.of("investing basics", "portfolio management", "risk management")
                : derived;
    }
}
