package depth.finvibe.boot.config.gamification;

import java.time.Duration;

import org.apache.kafka.common.protocol.types.Field;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "langchain4j.google-ai-gemini.chat-model")
public record GeminiChatModelProperties(
    String apiKey,
    String modelName,
    String highModelName,
    Duration timeout,
    Double temperature
) {
}
