package depth.finvibe.modules.market.infra.client.dto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import depth.finvibe.modules.market.infra.client.dto.KisDto.ChkHolidayOutput;

/**
 * KIS 국내휴장일조회 API의 output 필드 역직렬화기.
 * API가 output을 단일 객체 또는 배열로 반환하는 경우 모두 List로 변환합니다.
 */
public class ChkHolidayOutputListDeserializer extends JsonDeserializer<List<ChkHolidayOutput>> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public List<ChkHolidayOutput> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonToken t = p.currentToken();
    if (t == null) {
      t = p.nextToken();
    }
    if (t == JsonToken.START_ARRAY) {
      List<ChkHolidayOutput> list = new ArrayList<>();
      for (JsonToken next; (next = p.nextToken()) != JsonToken.END_ARRAY && next != null; ) {
        list.add(MAPPER.readValue(p, ChkHolidayOutput.class));
      }
      return list;
    }
    if (t == JsonToken.START_OBJECT) {
      return List.of(MAPPER.readValue(p, ChkHolidayOutput.class));
    }
    return List.of();
  }
}
