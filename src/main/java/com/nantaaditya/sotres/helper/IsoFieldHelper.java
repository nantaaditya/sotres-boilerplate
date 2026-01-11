package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.configuration.PackagerConfiguration;
import com.nantaaditya.sotres.model.constant.PackagerConstant;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.model.dto.RequestContext.Merchant;
import com.nantaaditya.sotres.model.dto.RequestContext.Reversal;
import com.nantaaditya.sotres.model.dto.RequestContext.Transaction;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.netty.channel.ChannelHandlerContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class IsoFieldHelper {

  private static final int DEFAULT_FRACTION_DIGIT = 2;

  private final PackagerConfiguration packagerConfiguration;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;

  private static final Random RANDOM = new SecureRandom();

  public static String getMTI(int type) {
    try {
      return String.format("%04x", type);
    } catch (Exception e) {
      log.error(AppLogMessage.message("#Network - cannot convert ISO8583 MTI. with message : {}", e.getMessage()).error(e));
    }
    return "";
  }

  public static BigDecimal convertAmount(double amount, int fractionDigit) {
    if (fractionDigit < 1 || amount == 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    for (int i=0; i<fractionDigit; i++) {
      amount /= 10;
    }

    BigDecimal result = BigDecimal.valueOf(amount);
    return result.setScale(2, RoundingMode.HALF_UP);
  }

  public static String generateNumeric(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i=0; i<length; i++) {
      sb.append(RANDOM.nextInt(10));
    }
    return sb.toString();
  }

  public static Transaction createTransaction(
      IsoMessage isoMessage, SystemPropertiesService systemPropertiesService) {
    Map<String, Integer> currencyFractions = PropertiesGroup.getMap(
            systemPropertiesService, PropertiesGroup.CURRENCY_FRACTIONS
        )
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> Integer.parseInt(entry.getValue())));

    String de4 = getField(isoMessage,4);
    String de49 = getField(isoMessage,49);
    String de28 = getField(isoMessage,28);

    double originalAmount = parse(de4);
    int fractionDigit = currencyFractions.getOrDefault(de49, DEFAULT_FRACTION_DIGIT);
    double transactionFee = parse(substring(de28,1, de28.length() - 1));
    String feeType = substring(de28, 0, 1);
    double transactionAmount = getCalculateTransactionAmount(feeType, originalAmount, transactionFee);

    return Transaction.builder()
        .originalAmount(IsoFieldHelper.convertAmount(originalAmount, fractionDigit))
        .transactionFeeAmount(IsoFieldHelper.convertAmount(transactionFee, fractionDigit))
        .transactionAmount(IsoFieldHelper.convertAmount(transactionAmount, fractionDigit))
        .originalCurrencyCode(de49)
        .build();
  }

  private static double getCalculateTransactionAmount(String feeType, double originalAmount, double transactionFee) {
    return  "C".equalsIgnoreCase(feeType) ?
        (originalAmount + transactionFee) : (originalAmount - transactionFee);
  }

  public static Merchant createMerchant(IsoMessage isoMessage) {
    String de18 = getField(isoMessage,18);
    String de43 = getField(isoMessage,43);

    return Merchant.builder()
        .merchantCategoryCode(de18)
        .merchantName(substring(de43,0, 25))
        .merchantCity(substring(de43,25, 38))
        .merchantCountryCode(substring(de43, 38, 40))
        .build();
  }

  public static Reversal createReversal(IsoMessage isoMessage) {
    if (!isoMessage.hasField(90)) return null;

    String de90 = getField(isoMessage,90);

    return Reversal.builder()
        .originalMti(substring(de90, 0, 4))
        .originalStan(substring(de90, 4, 10))
        .originalTransmissionDateTime(substring(de90, 10, 20))
        .originalAcquiringInstitutionId(substring(de90, 20, 31))
        .originalForwardingInstitutionId(substring(de90, 31, 42))
        .build();
  }

  public static Map<String, String> unpackTLV(String raw, int tagLength, int lengthSize) {
    Map<String, String> tlv = new LinkedHashMap<>();
    int i = 0;
    while (i < raw.length()) {
      String tag = raw.substring(i, i + tagLength);
      i += tagLength;
      int valueLength = Integer.parseInt(raw.substring(i, i + lengthSize));
      i += 2;
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
    String productIndicator = unpackTLV(getField(request, 48), 2, 2)
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
    return substring(str, start, 0);
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

  public void sendResponse(ChannelHandlerContext context, IsoMessage request, String responseCode) {
    IsoMessage response = packagerConfiguration.createMessageFactory(PackagerConstant.DEFAULT)
        .createResponse(request);
    response.setField(39, new IsoValue<>(IsoType.ALPHA, responseCode, 2));
    isoMessageLoggerHelper.logIsoMessage(response);
    context.writeAndFlush(response);
  }

  public void sendResponse(ChannelHandlerContext context, IsoMessage request, Consumer<IsoMessage> responseConsumer) {
    IsoMessage response = packagerConfiguration.createMessageFactory(PackagerConstant.DEFAULT)
        .createResponse(request);
    responseConsumer.accept(response);
    isoMessageLoggerHelper.logIsoMessage(response);
    context.writeAndFlush(response);
  }
}
