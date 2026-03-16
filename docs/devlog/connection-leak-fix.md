# DB 커넥션 누수 원인 분석 및 해결

## 문제 상황

운영 로그에서 HikariCP의 커넥션 누수 경고가 반복 발생.

```
WARN [HikariPool-1:housekeeper] Connection leak detection triggered for
org.mariadb.jdbc.Connection@334d96af on thread KafkaListenerEndpointContainer#0-0-C-1
```

Kafka 메시지 수신 시(`batch-price-updated` 이벤트) 매번 발생하며, 대용량 배치 처리(약 271개 종목) 중에 트리거됨.

---

## 원인 분석

### 핵심 원인: 트랜잭션 내 외부 I/O

`ProfitCalculationService.recalculateAllProfits`가 `@Transactional` 하나로 감싸져 있었고, 그 안에서 외부 HTTP API 호출이 수행됨.

**수정 전 흐름:**
```
[트랜잭션 시작 → DB 커넥션 획득]
  ① findAllByStockIdsWithAssets()     — DB 조회
  ② marketPriceClient.getBatchPrices() — 외부 HTTP API 호출 ← 커넥션 점유 중
  ③ asset.updateValuation() 루프       — 메모리 처리
  ④ findAllWithAssets()               — DB 전체 조회
  ⑤ 이벤트 발행 / 랭킹 집계
[트랜잭션 종료 → 커넥션 반환]
```

외부 API 응답을 기다리는 동안 DB 커넥션이 계속 점유되어 HikariCP의 `leakDetectionThreshold`를 초과.

또한 `AssetEventService.handleBatchPriceUpdatedEvent`도 `@Transactional`로 감싸져 있었기 때문에, `recalculateAllProfits`를 호출하는 시점에 이미 외부 트랜잭션이 활성화된 상태였음. Spring의 기본 트랜잭션 전파(REQUIRED)에 의해 `recalculateAllProfits` 내부의 `@Transactional`이 새 트랜잭션을 만들지 않고 외부 트랜잭션에 참여하므로, helper 분리 효과가 무력화될 수 있었음.

---

## 해결 방법

### 트랜잭션 경계를 작업 단위별로 분리

외부 I/O가 DB 커넥션을 점유하지 않도록, 읽기 / 외부 호출 / 쓰기를 각각의 짧은 트랜잭션으로 분리.

**핵심 설계 원칙:**
> 트랜잭션 안에는 DB 작업만 넣는다. 외부 API 호출, 메시지 발행 등 I/O는 트랜잭션 밖에서 수행한다.

**수정 후 흐름:**
```
[짧은 read 트랜잭션] ① 포트폴리오 조회 → 커넥션 반환
[커넥션 없음]        ② getBatchPrices() — 외부 HTTP API 호출
[짧은 write 트랜잭션] ③ valuation 업데이트 후 saveAll → 커넥션 반환
[짧은 read 트랜잭션]  ④ 전체 조회 (랭킹 집계용) → 커넥션 반환
                     ⑤ 이벤트 발행 / 랭킹 집계
```

### Spring AOP 우회 문제 해결

같은 빈(bean) 내부에서 `@Transactional` 메서드를 호출하면 Spring AOP 프록시를 거치지 않아 새 트랜잭션이 생성되지 않는다. 이를 해결하기 위해 트랜잭션 단위별 메서드를 **별도 빈(`ProfitCalculationTxHelper`)으로 추출**.

---

## 변경 내용

### 1. `ProfitCalculationTxHelper` 신규 생성

```java
@Service
@RequiredArgsConstructor
class ProfitCalculationTxHelper {

    private final PortfolioGroupRepository portfolioGroupRepository;

    @Transactional(readOnly = true)
    public List<PortfolioGroup> readPortfoliosByStockIds(List<Long> stockIds) {
        return portfolioGroupRepository.findAllByStockIdsWithAssets(stockIds);
    }

    @Transactional
    public void updateValuations(List<PortfolioGroup> portfolios, Map<Long, BigDecimal> priceByStockId) {
        for (PortfolioGroup portfolio : portfolios) {
            for (Asset asset : portfolio.getAssets()) {
                BigDecimal currentPrice = priceByStockId.get(asset.getStockId());
                if (currentPrice != null) {
                    asset.updateValuation(currentPrice);
                }
            }
            portfolio.recalculateValuation();
        }
        portfolioGroupRepository.saveAll(portfolios);  // detached 엔티티 merge
    }

    @Transactional(readOnly = true)
    public List<PortfolioGroup> readAllPortfolios() {
        return portfolioGroupRepository.findAllWithAssets();
    }
}
```

**Detached 엔티티 처리:**
read 트랜잭션 종료 후 엔티티는 detached 상태가 된다. write 트랜잭션에서 `saveAll()`을 호출하면 `EntityManager.merge()`가 실행되어 변경된 상태가 DB에 반영된다. `PortfolioGroup.assets`에 `CascadeType.MERGE`가 설정되어 있어 asset의 `AssetValuation` 변경도 함께 반영된다.

### 2. `ProfitCalculationService.recalculateAllProfits` 수정

- `@Transactional` 어노테이션 제거 (순수 오케스트레이션 메서드로 전환)
- `PortfolioGroupRepository` 직접 의존 제거, `ProfitCalculationTxHelper` 주입
- 단계별 호출로 구조 변경

### 3. `AssetEventService.handleBatchPriceUpdatedEvent` 수정

- 메서드 자체에 DB 작업이 없으므로 `@Transactional` 제거
- 외부 트랜잭션이 없어야 helper의 트랜잭션 분리가 정상 동작

### 4. `PortfolioGroupRepository` 인터페이스 및 구현체

- `saveAll(List<PortfolioGroup>)` 메서드 추가
- `PortfolioGroupRepositoryImpl`에서 `jpaRepository.saveAll()` 위임

---

## 변경 파일 목록

| 파일 | 변경 내용 |
|---|---|
| `application/ProfitCalculationTxHelper.java` | 신규 생성 — 트랜잭션 단위별 메서드 |
| `application/ProfitCalculationService.java` | `@Transactional` 제거, txHelper 사용 |
| `application/AssetEventService.java` | `handleBatchPriceUpdatedEvent` `@Transactional` 제거 |
| `application/port/out/PortfolioGroupRepository.java` | `saveAll` 메서드 추가 |
| `infra/persistence/PortfolioGroupRepositoryImpl.java` | `saveAll` 구현 |

---

## 핵심 교훈

- `@Transactional` 하나로 긴 로직을 감싸면 커넥션 점유 시간이 길어진다.
- **외부 API 호출, Kafka 발행, 캐시 접근 등은 트랜잭션 밖에서 수행**해야 한다.
- 동일 빈 내 트랜잭션 분리가 필요할 때는 별도 빈으로 추출하거나 `TransactionTemplate`을 사용한다.
- HikariCP `leakDetectionThreshold`는 실제 누수뿐 아니라 커넥션을 오래 점유하는 로직도 감지하므로, 경고 발생 시 트랜잭션 경계를 의심해볼 것.
