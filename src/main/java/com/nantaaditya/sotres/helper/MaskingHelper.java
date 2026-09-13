package com.nantaaditya.sotres.helper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.StringUtils;

@Log4j2
public class MaskingHelper {

  public static final String MASKED_CHAR = "*";
  private static final Set<String> CARD_NO_KEYS = Set.of("cardNo", "2");
  private static final Pattern PAN_LIKE_DIGIT_RUN = Pattern.compile("\\b\\d{13,19}\\b");

  private MaskingHelper() {}

  public static String masking(String value) {
    if (!StringUtils.hasText(value) || value.length() <= 2) {
      return value;
    }

    int valueLength = value.length();
    int halfCharLength = valueLength / 2;
    int startCharLength = (valueLength - halfCharLength) / 2;

    return masking(value, startCharLength, startCharLength);
  }

  public static String masking(String value, int nStartChar, int nEndChar) {
    int totalLength = nStartChar + nEndChar;
    if (!StringUtils.hasText(value) || value.length() <= totalLength) {
      return value;
    }

    StringBuilder sb = new StringBuilder();
    sb.append(value, 0, nStartChar);
    sb.append(MASKED_CHAR.repeat(value.length() - totalLength));
    sb.append(value, sb.length(), value.length());
    return sb.toString();
  }

  public static String maskingCardNo(String cardNo) {
    if (!StringUtils.hasText(cardNo) || cardNo.length() < 16) return cardNo;

    return masking(cardNo, 6, 4);
  }

  /**
   * Masks the values of any header {@code isSensitive} flags true, leaving all others untouched.
   * Shared by {@link ApiLogbookFormatter} (inbound/outbound HTTP logging) and
   * {@link com.nantaaditya.sotres.listener.RestSenderRetryListener} (dead-letter persistence) —
   * the two places headers get written somewhere durable.
   */
  public static Map<String, List<String>> maskHeaders(
      Map<String, List<String>> headers, Predicate<String> isSensitive) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      result.put(
          entry.getKey(),
          isSensitive.test(entry.getKey())
              ? entry.getValue().stream().map(MaskingHelper::masking).toList()
              : entry.getValue()
      );
    }
    return result;
  }

  public static String maskingJson(Gson gson, Set<String> maskingKeys, String jsonPayload) {
    if (!StringUtils.hasText(jsonPayload)) {
      return jsonPayload;
    }

    String content = null;
    try {
      if (jsonPayload.startsWith("[")) {
        JsonArray jsonArray = gson.fromJson(jsonPayload, JsonArray.class);
        for (String key : maskingKeys) {
          maskArray(jsonArray, key);
        }
        content = GsonHelper.cleanJson(gson.toJson(jsonArray), gson);
      } else if (jsonPayload.startsWith("{")) {
        JsonObject jsonObject = gson.fromJson(jsonPayload, JsonObject.class);
        for (String key : maskingKeys) {
          maskObject(jsonObject, key);
        }
        content = GsonHelper.cleanJson(gson.toJson(jsonObject), gson);
      }

      return content;
    } catch (Exception e) {
      log.error(AppLogMessage.message("#Masking - json error {}", e.getMessage()).error(e));
      return "not a json";
    }
  }

  private static void maskArray(JsonArray jsonArray, String targetKey) {
    for (int i=0; i<jsonArray.size(); i++) {
      if (jsonArray.get(i) instanceof JsonObject jsonObject) {
        maskObject(jsonObject, targetKey);
      } else if (jsonArray.get(i) instanceof JsonPrimitive jsonPrimitive) {
        jsonArray.set(i, new JsonPrimitive(jsonPrimitive.getAsString()));
      }
    }
  }

  private static void maskObject(JsonObject jsonObject, String targetKey) {
    for (String key : jsonObject.keySet()) {
      Object value = jsonObject.get(key);
      if (value instanceof JsonObject jsonObj) {
        maskObject(jsonObj, targetKey);
      } else if (value instanceof JsonArray jsonArray) {
        maskArray(jsonArray, targetKey);
      } else if (CARD_NO_KEYS.contains(key)) {
        jsonObject.addProperty(key, maskingCardNo(jsonObject.get(key).getAsString()));
      } else if (key.equals(targetKey)) {
        jsonObject.addProperty(key, masking(jsonObject.get(key).getAsString()));
      }
    }
  }
}