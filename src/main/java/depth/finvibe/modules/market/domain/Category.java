package depth.finvibe.modules.market.domain;

import depth.finvibe.modules.market.domain.error.MarketErrorCode;
import depth.finvibe.common.error.DomainException;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity(name = "MarketCategory")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class Category {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // 카테고리명 변경
    public void changeName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException(MarketErrorCode.INVALID_CATEGORY_NAME);
        }
        this.name = name;
    }

    // 카테고리명 검증
    public boolean hasName(String name) {
        return this.name.equals(name);
    }

}
