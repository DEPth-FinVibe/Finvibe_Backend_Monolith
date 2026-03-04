package depth.finvibe.modules.study.infra.llm;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import depth.finvibe.modules.study.application.port.out.CourseGenerator;
import depth.finvibe.modules.study.dto.GeneratorDto;

@Component
@RequiredArgsConstructor
public class CourseGeneratorImpl implements CourseGenerator {
    private static final int MAX_RETRY_COUNT = 2;
    private static final String SYSTEM_PROMPT_PATH = "classpath:prompts/study-course-generation-system.txt";
    private static final String PREVIEW_USER_PROMPT_PATH = "classpath:prompts/study-course-preview-user.txt";
    private static final String DESCRIPTION_USER_PROMPT_PATH = "classpath:prompts/study-course-description-user.txt";
    private static final ResponseFormat PREVIEW_RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .rootElement(JsonObjectSchema.builder()
                            .addStringProperty("content")
                            .required("content")
                            .build())
                    .build())
            .build();
    private static final ResponseFormat DESCRIPTION_RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .rootElement(JsonObjectSchema.builder()
                            .addStringProperty("description")
                            .required("description")
                            .build())
                    .build())
            .build();
    private static final TypeReference<Map<String, String>> RESPONSE_TYPE = new TypeReference<>() {};

    @Qualifier("chatModel")
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    @Qualifier("webApplicationContext")
    private final ResourceLoader resourceLoader;

    @Override
    public String generateCoursePreview(String title, List<String> keywords) {
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                String parsed = requestPreview(title, keywords);
                if (StringUtils.hasText(parsed)) {
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
        return fallbackPreview(title, keywords);
    }

    @Override
    public String generateCourseDescription(String title, List<String> keywords, List<GeneratorDto.LessonIndex> lessonIndices) {
        for (int attempt = 0; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                String parsed = requestDescription(title, keywords, lessonIndices);
                if (StringUtils.hasText(parsed)) {
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
        return fallbackDescription(title, keywords, lessonIndices);
    }

    private String requestPreview(String title, List<String> keywords) {
        String systemMessage = loadPrompt(SYSTEM_PROMPT_PATH);
        String userMessage = loadPrompt(PREVIEW_USER_PROMPT_PATH)
                .replace("{{title}}", title)
                .replace("{{keywords}}", String.join(", ", keywords));

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(systemMessage),
                        UserMessage.from(userMessage)
                )
                .responseFormat(PREVIEW_RESPONSE_FORMAT)
                .build();

        ChatResponse chatResponse = chatModel.chat(request);
        return parseResponse(chatResponse.aiMessage().text(), "content");
    }

    private String requestDescription(String title, List<String> keywords, List<GeneratorDto.LessonIndex> lessonIndices) {
        String systemMessage = loadPrompt(SYSTEM_PROMPT_PATH);
        String userMessage = loadPrompt(DESCRIPTION_USER_PROMPT_PATH)
                .replace("{{title}}", title)
                .replace("{{keywords}}", String.join(", ", keywords))
                .replace("{{lesson_indices}}", renderLessonIndices(lessonIndices));

        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(systemMessage),
                        UserMessage.from(userMessage)
                )
                .responseFormat(DESCRIPTION_RESPONSE_FORMAT)
                .build();

        ChatResponse chatResponse = chatModel.chat(request);
        return parseResponse(chatResponse.aiMessage().text(), "description");
    }

    private String parseResponse(String response, String key) {
        try {
            Map<String, String> parsed = objectMapper.readValue(response, RESPONSE_TYPE);
            return parsed.get(key);
        } catch (Exception ex) {
            return null;
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

    private String renderLessonIndices(List<GeneratorDto.LessonIndex> lessonIndices) {
        if (lessonIndices == null || lessonIndices.isEmpty()) {
            return "(none)";
        }
        return lessonIndices.stream()
                .map(idx -> "- " + idx.getTitle() + ": " + idx.getDescription())
                .collect(Collectors.joining("\n"));
    }

    private String fallbackPreview(String title, List<String> keywords) {
        return "Preview for course '" + title + "' covering: " + String.join(", ", keywords) + ".";
    }

    private String fallbackDescription(String title, List<String> keywords, List<GeneratorDto.LessonIndex> lessonIndices) {
        String outline = renderLessonIndices(lessonIndices);
        return "Course '" + title + "' explores " + String.join(", ", keywords)
                + ". Lessons include:\n" + outline;
    }
}
