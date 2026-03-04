package depth.finvibe.modules.study.application.port.out;

import org.apache.kafka.common.protocol.types.Field;

public interface AiRecommendGenerator {
    String generateStudyRecommendContent(
            String recentTrades
    );
}
