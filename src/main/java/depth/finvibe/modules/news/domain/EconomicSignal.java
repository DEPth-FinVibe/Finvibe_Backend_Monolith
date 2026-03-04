package depth.finvibe.modules.news.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum EconomicSignal {
    POSITIVE("호재"),
    NEGATIVE("악재"),
    NEUTRAL("중립");

    private final String label;

    public static EconomicSignal fromString(String value) {
        return Arrays.stream(EconomicSignal.values())
                .filter(s -> s.name().equalsIgnoreCase(value) || s.label.equals(value))
                .findFirst()
                .orElse(NEUTRAL);
    }
}
