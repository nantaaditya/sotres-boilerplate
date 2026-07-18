package com.nantaaditya.sotres.helper;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.dto.ParticipantContext;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.RequestContext.Merchant;
import com.nantaaditya.sotres.model.dto.RequestContext.Reversal;
import com.nantaaditya.sotres.model.dto.RequestContext.Transaction;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.model.logger.JsonLogIsoMessage;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import io.micrometer.observation.Observation;
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
  private static final String ISO_REQUEST_EVENT = "iso_request";
  private static final String ISO_RESPONSE_EVENT = "iso_response";

  private final MessageFactoryHelper messageFactoryHelper;
  private final IsoMessageLoggerHelper isoMessageLoggerHelper;
  private final ObjectMapper objectMapper;

  private static final Random RANDOM = new SecureRandom();

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

  public static BigDecimal convertAmount(double amount, int fractionDigit) {
    if (fractionDigit < 1 || amount == 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    return BigDecimal.valueOf(amount)
        .movePointLeft(fractionDigit)
        .setScale(2, RoundingMode.HALF_UP);
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
    Map<String, Integer> currencyFractions = ConfigGroup.getMap(
            systemPropertiesService, ConfigGroup.CURRENCY_FRACTIONS
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

  public void sendResponse(ChannelHandlerContext context, IsoMessage request, String responseCode) {
    sendResponse(context, request, response -> {
      response.setField(39, new IsoValue<>(IsoType.ALPHA, responseCode, 2));
    });
  }

  public void sendResponse(ChannelHandlerContext context, IsoMessage request, Consumer<IsoMessage> responseConsumer) {
    IsoMessage response = createResponse(request);
    responseConsumer.accept(response);
    isoMessageLoggerHelper.logIsoMessage(response);
    context.writeAndFlush(response);
  }

  public void sendResponseWithObservation(ParticipantContext context, String responseCode, Throwable throwable) {
    IsoMessage request = context.getIsoMessage();
    IsoMessage response = createResponse(request);
    response.setField(39, new IsoValue<>(IsoType.ALPHA, responseCode, 2));

    Observation observation = context.getObservation();
    publishIsoEvent(observation, response, ISO_RESPONSE_EVENT);

    isoMessageLoggerHelper.logIsoMessage(response);

    ChannelHandlerContext channelHandlerContext = context.getChannelHandlerContext();
    channelHandlerContext.writeAndFlush(response);

    ObservationHelper.observeResponse(context.getObservation(), responseCode, throwable);
  }

  public IsoMessage createResponse(IsoMessage request) {
    return messageFactoryHelper.getDefaultMessageFactory().createResponse(request);
  }

  public RequestContext logAndObserve(IsoMessage isoMessage, RequestContext requestContext,
      Observation observation) {

    ObservationHelper.createIsoContext(observation, requestContext.getRrn(), requestContext.getIsoFeatureConstant());

    publishIsoEvent(observation, isoMessage, ISO_REQUEST_EVENT);

    isoMessageLoggerHelper.logIsoMessage(isoMessage);

    return requestContext;
  }

  private void publishIsoEvent(Observation observation, IsoMessage isoMessage, String event) {
    try {
      JsonLogIsoMessage jsonLogIsoMessage = isoMessageLoggerHelper.toLogMessage(isoMessage);
      String isoRequest = objectMapper.writeValueAsString(jsonLogIsoMessage);
      ObservationHelper.publishEvent(observation, event, isoRequest);
    } catch (JacksonException e) {
      log.error(AppLogMessage.message("#IsoField - failed to log iso message").error(e));
    }
  }
}
