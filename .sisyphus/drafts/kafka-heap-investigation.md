# Draft: Kafka Heap Investigation

## Requirements (confirmed)
- User reports Kafka event processing may be consuming excessive heap memory.
- Data volume is high (about 100,000 users + related scaled datasets).
- Goal is to identify whether current Kafka consumer handling has heap-heavy patterns.
- Symptom timing: spike at traffic peak.
- Main symptom: frequent Full GC.
- Available evidence: GC logs are available.
- User can provide/confirm next correlation data: Kafka lag timeline, topic TPS, async queue metrics.
- Correlation focus window confirmed: 22:30~22:50.
- Immediately available artifact for that window: GC logs (Full GC start/end/pause).
- User preference: wants immediate analysis based on provided screenshots without additional raw log extraction first.
- User clarification: there is no separate application batch job currently.

## Technical Decisions
- Diagnosis-first approach: identify high-risk code paths and config amplifiers before tuning execution.
- Correlation priority set: (1) lag vs Full GC time window, (2) topic TPS vs GC spikes, (3) async executor queue depth vs spikes.
- Provide screenshot-only preliminary diagnosis now, then request only minimal next data if needed.
- Re-validate whether earlier "batch" risk is from scheduled batch jobs vs Kafka event payload/topic naming; avoid wrong assumption.

## Research Findings
- `bg_2a49087a` summary:
  - Kafka consumer entry points identified across wallet/news/market/trade/asset/gamification modules.
  - High risk #1: async `@Async` handling path in asset transfer flow can queue backlog in peak traffic.
  - High risk #2: batch price update path can accumulate large in-memory lists before persistence/event emission.
  - Medium risk: most listeners are per-message handlers, but backlog can still grow at peak.
- `bg_da7c0b8e` summary:
  - Kafka + async config can amplify heap pressure under load (deserialization churn, queueing, missing explicit poll/fetch caps).
  - Potential payload graph hotspot: `BatchPriceUpdatedEvent.updatedStockIds (List<Long>)`.
  - Listener concurrency tuning is not explicit in key consumer points, increasing lag/backpressure risk.

## User Evidence (metrics screenshots)
- JVM Heap (used) repeatedly rises near ~2.7-2.9 GiB and then drops sharply to low hundreds of MiB.
- G1 Old Gen used also shows repeated high utilization with sharp drops, consistent with frequent major collection pressure.
- Non-heap pools (Metaspace/CodeHeap) look relatively stable, so current symptom is more allocation/retention churn in heap than classloader/metaspace leak.
- Interpreted pattern: likely bursty allocation + backlog drain cycles at peak traffic, not a monotonic memory leak.

## Open Questions
- Which consumer group/topic lag grows first at traffic peak?
- During Full GC windows, which consumer/executor thread pool shows highest CPU/queue depth?
- What are avg/max sizes for `BatchPriceUpdatedEvent.updatedStockIds`?
- Do you have async executor queue metrics (active threads, queue size, rejected count)?
- Can you share the exact timestamp window for one Full GC spike so we can correlate with Kafka consumer lag and topic throughput?
- User reply "gownj" appears to be ambiguous/typo; pending confirmation whether to proceed with work plan generation now.

## Scope Boundaries
- INCLUDE: Identify likely heap hotspots in Kafka event processing path and propose mitigation plan.
- EXCLUDE: Immediate implementation changes (planning and diagnosis only in this phase).
