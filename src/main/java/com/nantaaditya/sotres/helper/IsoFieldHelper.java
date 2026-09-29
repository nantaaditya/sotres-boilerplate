package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.constant.IsoFeatureConstant;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

@Log4j2
public class IsoFieldHelper {

  private IsoFieldHelper() { }

  public static String getMTI(int type) {
    try {
      String mti = Integer.toHexString(type);
      return StringUtils.leftPad(mti, 4, '0');
    } catch (Exception e) {
      log.error(AppLogMessage.message("#Network - cannot convert ISO8583 MTI. with message : {}", e.getMessage()).error(e));
    }
    return "";
  }

  public static boolean isMTIRequest(int type) {
    String mti = getMTI(type);
    if (mti == null || mti.length() < 4) {
      return false;
    }

    char functionDigit = mti.charAt(2);

    return switch (functionDigit) {
      case '0', '2', '4' -> true; // 0=Req, 2=Advice, 4=Notif
      case '1', '3', '5' -> false; // 1=ReqRes, 3=AdvRes, 5=NotifRes
      default -> false;
    };
  }

  public static boolean isMTIResponse(int type) {
    String mti = getMTI(type);
    if (mti == null || mti.length() < 4) {
      return false;
    }

    char functionDigit = mti.charAt(2);

    return switch (functionDigit) {
      case '0', '2', '4' -> false; // 0=Req, 2=Advice, 4=Notif
      case '1', '3', '5' -> true; // 1=ReqRes, 3=AdvRes, 5=NotifRes
      default -> false;
    };
  }

  public static Map<String, String> unpackTLV(String raw, int tagLength, int lengthSize) {
    Map<String, String> tlv = new LinkedHashMap<>();
    int i = 0;
    while (i < raw.length()) {
      String tag = raw.substring(i, i + tagLength);
      i += tagLength;
      int valueLength = Integer.parseInt(raw.substring(i, i + lengthSize));
      i += lengthSize;
      String value = raw.substring(i, i + valueLength);
      i += valueLength;

      tlv.put(tag, value);
    }
    return tlv;
  }

  public static String packTLV(Map<String, String> tlv, int tagLength, int lengthSize) {
    StringBuilder sb = new StringBuilder();
    for (Entry<String, String> entry : tlv.entrySet()) {
      sb.append(entry.getKey());
      sb.append(StringHelper.prepend(String.valueOf(entry.getValue().length()), '0', lengthSize));
      sb.append(entry.getValue());
    }
    return sb.toString();
  }

  public static String getField(IsoMessage msg, int field) {
    return Optional.ofNullable(msg)
        .map(m -> m.getField(field))
        .map(IsoValue::toString)
        .orElse(null);
  }

  public static String getCorrelationId(IsoMessage request) {
    String de48 = getField(request,48);
    if (de48 == null) {
      log.error(AppLogMessage.message("#CorrelationId is null").additionalData(request));
      return null;
    }

    String productIndicator = unpackTLV(de48, 2, 2)
        .getOrDefault("PI", "NA"); // product indicator
    String processingCode = Optional.ofNullable(getField(request, 3)) // processing code
        .map(result -> substring(result, 0, 2))
        .orElseGet(() -> "NA");

    return new StringBuilder()
        .append(productIndicator)
        .append(".")
        .append(processingCode)
        .append("|")
        .append(getField(request, 11)) // STAN
        .append("-")
        .append(getField(request, 37)) // RRN
        .append("-")
        .append(getField(request, 7)) // Date
        .toString();
  }

  public static String createSelector(IsoMessage isoMessage) {
    return createSelector(
        getMTI(isoMessage.getType()),
        getField(isoMessage, 3),
        IsoFieldHelper.unpackTLV(getField(isoMessage, 48), 2, 2)
    );
  }

  public static String createSelector(String mti, String processingCode, Map<String, String> tlv) {
    StringBuilder sb = new StringBuilder();
    // mti
    sb.append(substring(mti, 1, 3));
    sb.append(".");

    // processing code
    if (StringUtils.isNotBlank(processingCode)) {
      sb.append(substring(processingCode, 0, 2));
    } else {
      sb.append("NA");
    }

    // product indicator
    sb.append("-");
    sb.append(tlv.getOrDefault("PI", "NA"));
    return sb.toString();
  }

  public static String substring(String str, int start) {
    return substring(str, start, str.length());
  }

  public static String substring(String str, int start, int end) {
    if (str == null || str.isBlank() || start >= end) return str;

    if (end > 0) {
      return str.substring(start, end);
    }

    return str.substring(start);
  }

  public static double parse(String str) {
    try {
      return Double.parseDouble(str);
    } catch (NumberFormatException e) {
      log.error(AppLogMessage.message("#IsoField - failed to parse {}", str).error(e));
      return Double.MIN_VALUE;
    }
  }

  public static void setApprovalCode(IsoMessage isoMessage, String approvalCode) {
    Optional.ofNullable(approvalCode)
        .ifPresent(code -> isoMessage.setField(38, IsoType.ALPHA.value(IsoFieldHelper.substring(code,code.length() - 6), 6)));
  }

  public static String getIsoFeature(IsoMessage isoMessage) {
    String selector = createSelector(isoMessage);
    return IsoFeatureConstant.getBySelector(selector);
  }
}
