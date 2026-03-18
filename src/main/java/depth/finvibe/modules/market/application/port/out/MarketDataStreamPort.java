package depth.finvibe.modules.market.application.port.out;

import java.util.Set;

/**
 * 외부 실시간 시세 데이터 스트림 프로바이더와의 연결을 추상화하는 포트입니다.
 * KIS 등 다양한 증권사 API로 교체 가능하도록 합니다.
 */
public interface MarketDataStreamPort {

	/** 프로바이더 연결 초기화 */
	void initializeSessions();

	/** 할당된 Credential에 맞게 세션을 동기화합니다. */
	void synchronizeSessions();

	/** 연결이 끊긴 세션을 정리하고 제거된 수를 반환합니다. */
	int removeClosedSessions();

	/** 모든 세션을 종료합니다. */
	void closeAllSessions();

	/** 현재 사용 가능한 세션 수를 반환합니다. */
	int getAvailableSessionCount();

	/** 특정 종목 실시간 가격 구독을 시작합니다. */
	void subscribe(Long stockId, String symbol);

	/** 특정 종목 실시간 가격 구독을 해제합니다. */
	void unsubscribe(Long stockId, String symbol);

	/** 특정 종목이 현재 구독 중인지 확인합니다. */
	boolean isSubscribed(Long stockId);

	/** 현재 구독 중인 모든 종목 ID를 반환합니다. */
	Set<Long> getSubscribedStockIds();
}
