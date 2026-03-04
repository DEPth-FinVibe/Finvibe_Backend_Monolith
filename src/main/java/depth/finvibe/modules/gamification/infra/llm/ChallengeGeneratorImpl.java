package depth.finvibe.modules.gamification.infra.llm;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
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

import depth.finvibe.modules.gamification.application.port.out.ChallengeGenerator;
import depth.finvibe.modules.gamification.domain.enums.UserMetricType;
import depth.finvibe.modules.gamification.dto.ChallengeDto;

@Component
public class ChallengeGeneratorImpl implements ChallengeGenerator {
    private static final int CHALLENGE_COUNT = 3;
    private static final int MAX_RETRY_COUNT = 2;
    private static final String SYSTEM_PROMPT_PATH = "classpath:prompts/challenge-generation-system.txt";
    private static final String USER_PROMPT_PATH = "classpath:prompts/challenge-generation-user.txt";
    private static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .rootElement(JsonArraySchema.builder()
                            .items(JsonObjectSchema.builder()
                                    .addStringProperty("title")
                                    .addStringProperty("description")
                                    .addStringProperty("metricType")
                                    .addNumberProperty("targetValue")
                                    .addIntegerProperty("rewardXp")
                                    .required("title", "description", "metricType", "targetValue", "rewardXp")
                                    .build())
                            .build())
                    .build())
            .build();
    private static final TypeReference<List<ChallengeDto.ChallengeGenerationResponse>> RESPONSE_TYPE =
            new TypeReference<>() {};

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public ChallengeGeneratorImpl(
            @Qualifier("chatModel") ChatModel chatModel,
            ObjectMapper objectMapper,
            @Qualifier("webApplicationContext") ResourceLoader resourceLoader
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public List<ChallengeDto.ChallengeGenerationResponse> generate() {
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                List<ChallengeDto.ChallengeGenerationResponse> parsed = requestChallenges();
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
        return fallbackChallenges();
    }

    private List<ChallengeDto.ChallengeGenerationResponse> requestChallenges() {
        String metricTypes = Arrays.stream(UserMetricType.values())
                .map(UserMetricType::name)
                .collect(Collectors.joining(", "));
        String metricTypeDetails = Arrays.stream(UserMetricType.values())
                .map(metricType -> "- %s: %s".formatted(metricType.name(), metricType.getLlmDescription()))
                .collect(Collectors.joining("\n"));

        String systemMessage = loadPrompt(SYSTEM_PROMPT_PATH);
        String userMessage = loadPrompt(USER_PROMPT_PATH)
                .replace("{{metric_types}}", metricTypes)
                .replace("{{metric_type_details}}", metricTypeDetails);

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

    private List<ChallengeDto.ChallengeGenerationResponse> parseResponse(String response) {
        try {
            return objectMapper.readValue(response, RESPONSE_TYPE);
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

    private boolean isValid(List<ChallengeDto.ChallengeGenerationResponse> items) {
        if (items == null || items.size() != CHALLENGE_COUNT) {
            return false;
        }

        return items.stream().allMatch(this::isValidItem);
    }

    private boolean isValidItem(ChallengeDto.ChallengeGenerationResponse item) {
        if (item == null) {
            return false;
        }

        if (!StringUtils.hasText(item.getTitle())) {
            return false;
        }

        if (!StringUtils.hasText(item.getDescription())) {
            return false;
        }

        if (item.getMetricType() == null) {
            return false;
        }

        if (item.getTargetValue() == null || item.getTargetValue() <= 0) {
            return false;
        }

        return item.getRewardXp() != null && item.getRewardXp() > 0;
    }

    private List<ChallengeDto.ChallengeGenerationResponse> fallbackChallenges() {
        return List.of(
                ChallengeDto.ChallengeGenerationResponse.builder()
                        .title("3일 연속 접속")
                        .description("이번 주 3일 이상 접속해 보세요.")
                        .metricType(UserMetricType.LOGIN_COUNT_PER_DAY)
                        .targetValue(3.0)
                        .rewardXp(50L)
                        .build(),
                ChallengeDto.ChallengeGenerationResponse.builder()
                        .title("거래 5회 달성")
                        .description("주식 구매 또는 판매를 합쳐 5회 달성하세요.")
                        .metricType(UserMetricType.STOCK_BUY_COUNT)
                        .targetValue(5.0)
                        .rewardXp(80L)
                        .build(),
                ChallengeDto.ChallengeGenerationResponse.builder()
                        .title("커뮤니티 참여")
                        .description("토론 댓글 3개를 남겨보세요.")
                        .metricType(UserMetricType.DISCUSSION_COMMENT_COUNT)
                        .targetValue(3.0)
                        .rewardXp(40L)
                        .build()
        );
    }
}
