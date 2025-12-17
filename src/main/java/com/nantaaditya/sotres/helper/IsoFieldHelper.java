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
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class IsoFieldHelper {

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

    String de4 = isoMessage.getObjectValue(4);
    String de49 = isoMessage.getObjectValue(49);
    String de28 = isoMessage.getObjectValue(28);

    double originalAmount = Double.parseDouble(de4);
    int fractionDigit = currencyFractions.getOrDefault(de49, 2);

    double transactionFee = Double.parseDouble(de28.substring(1, de28.length() - 1));
    String feeType = de28.substring(0, 1);
    double transactionAmount = "C".equalsIgnoreCase(feeType) ?
        (originalAmount + transactionFee) : (originalAmount - transactionFee);

    return Transaction.builder()
        .originalAmount(IsoFieldHelper.convertAmount(originalAmount, fractionDigit))
        .originalCurrencyCode(de49)
        .transactionFeeAmount(IsoFieldHelper.convertAmount(transactionFee, fractionDigit))
        .transactionAmount(IsoFieldHelper.convertAmount(transactionAmount, fractionDigit))
        .build();
  }

  public static Merchant createMerchant(IsoMessage isoMessage) {
    String de18 = isoMessage.getObjectValue(18);
    String de43 = isoMessage.getObjectValue(43);

    return Merchant.builder()
        .merchantCategoryCode(de18)
        .merchantName(de43.substring(0, 25))
        .merchantCity(de43.substring(25, 38))
        .merchantCountryCode(de43.substring(38, 40))
        .build();
  }

  public static Reversal createReversal(IsoMessage isoMessage) {
    if (!isoMessage.hasField(90)) return null;

    String de90 = isoMessage.getObjectValue(90);

    return Reversal.builder()
        .originalMti(de90.substring(0, 4))
        .originalStan(de90.substring(4, 10))
        .originalTransmissionDateTime(de90.substring(10, 20))
        .originalAcquiringInstitutionId(de90.substring(20, 31))
        .originalForwardingInstitutionId(de90.substring(31, 42))
        .build();
  }

  public static Map<String, String> unpackTLV(String raw, int tagLength) {
    Map<String, String> tlv = new LinkedHashMap<>();
    int i = 0;
    while (i < raw.length()) {
      String tag = raw.substring(i, i + tagLength);
      i += tagLength;
      int valueLength = Integer.parseInt(raw.substring(i, i + 2));
      i += 2;
      String value = raw.substring(i, i + valueLength);
      i += valueLength;

      tlv.put(tag, value);
    }
    return tlv;
  }

  public static String packTLV(Map<String, String> tlv, int tagLength) {
    StringBuilder sb = new StringBuilder();
    for (Entry<String, String> entry : tlv.entrySet()) {
      sb.append(entry.getKey());
      sb.append(StringHelper.prepend(String.valueOf(entry.getValue().length()), '0', 2));
      sb.append(entry.getValue());
    }
    return sb.toString();
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
