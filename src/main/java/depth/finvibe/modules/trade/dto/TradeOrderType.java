package depth.finvibe.modules.trade.dto;

import depth.finvibe.modules.trade.domain.enums.TradeType;

public enum TradeOrderType {
  NORMAL,
  RESERVED;

  public TradeType toTradeType() {
    return TradeType.valueOf(name());
  }
}
