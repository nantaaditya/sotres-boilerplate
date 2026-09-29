package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.IsoFeatureConstant;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.RequestContext.Merchant;
import com.nantaaditya.sotres.model.dto.RequestContext.Reversal;
import com.nantaaditya.sotres.model.dto.RequestContext.Transaction;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class RequestContextHelper {

  private static final int DEFAULT_FRACTION_DIGIT = 2;

  private RequestContextHelper() { }

  public static RequestContext create(
      IsoMessage isoMessage,
      SystemPropertiesService systemPropertiesService,
      IsoCategory isoCategory) {

    RequestContext context = new RequestContext();
    context.setMti(IsoFieldHelper.getMTI(isoMessage.getType()));
    context.setCardNo(IsoFieldHelper.getField(isoMessage, 2));
    context.setProcessingCode(IsoFieldHelper.getField(isoMessage, 3));
    context.setTransmissionDateTime(IsoFieldHelper.getField(isoMessage, 7));
    context.setStan(IsoFieldHelper.getField(isoMessage, 11));
    context.setLocalTransactionTime(IsoFieldHelper.getField(isoMessage, 12));
    context.setLocalTransactionDate(IsoFieldHelper.getField(isoMessage, 13));
    context.setExpirationDate(IsoFieldHelper.getField(isoMessage, 14));
    context.setSettlementDate(IsoFieldHelper.getField(isoMessage, 15));
    context.setCaptureDate(IsoFieldHelper.getField(isoMessage, 17));
    context.setPosEntryMode(IsoFieldHelper.getField(isoMessage, 22));
    context.setAcquiringInstitutionId(IsoFieldHelper.getField(isoMessage, 32));
    context.setForwardingInstitutionId(IsoFieldHelper.getField(isoMessage, 33));
    context.setRrn(IsoFieldHelper.getField(isoMessage, 37));
    context.setCardAcceptorTerminalId(IsoFieldHelper.getField(isoMessage, 41));
    context.setCardAcceptorId(IsoFieldHelper.getField(isoMessage, 42));
    context.setAdditionalData(IsoFieldHelper.getField(isoMessage, 48));
    context.setOriginalDataElement(IsoFieldHelper.getField(isoMessage, 90));
    context.setIssuerId(IsoFieldHelper.getField(isoMessage, 100));
    context.setAccountIdentification(IsoFieldHelper.getField(isoMessage, 102));
    context.setInvoiceNo(IsoFieldHelper.getField(isoMessage, 123));

    context.setTransaction(createTransaction(isoMessage, systemPropertiesService));
    context.setMerchant(createMerchant(isoMessage));
    context.setReversal(createReversal(isoMessage));
    context.setIsoFeatureConstant(IsoFeatureConstant.getBySelector(context.getSelector()));

    if (IsoCategory.LATE_RESPONSE == isoCategory) {
      context.setLateResponse(true);
    }

    if (IsoCategory.ORPHAN == isoCategory) {
      context.setOrphanResponse(true);
    }

    if (IsoCategory.EXTERNAL_REQUEST == isoCategory) {
      context.setExternalRequest(true);
    }

    // any non-null, non-EXTERNAL_REQUEST classification means IsoCallbackResponseHandler already
    // matched this message as a reply to something we sent via EnhancedIsoClient — never a fresh
    // switch-initiated request needing our own ISO reply.
    if (isoCategory != null && isoCategory != IsoCategory.EXTERNAL_REQUEST) {
      context.setCallbackResponse(true);
    }

    return context;
  }

  private static Transaction createTransaction(
      IsoMessage isoMessage, SystemPropertiesService systemPropertiesService) {
    Map<String, Integer> currencyFractions = ConfigGroup.getMap(
            systemPropertiesService, ConfigGroup.CURRENCY_FRACTIONS
        )
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> Integer.parseInt(entry.getValue())));

    String de4 = IsoFieldHelper.getField(isoMessage, 4);
    String de49 = IsoFieldHelper.getField(isoMessage, 49);
    String de28 = IsoFieldHelper.getField(isoMessage, 28);

    double originalAmount = IsoFieldHelper.parse(de4);
    int fractionDigit = currencyFractions.getOrDefault(de49, DEFAULT_FRACTION_DIGIT);

    // DE28 (transaction fee) is optional in ISO8583 — absent means no fee
    double transactionFee = 0d;
    String feeType = null;
    if (StringUtils.isNotBlank(de28)) {
      transactionFee = IsoFieldHelper.parse(IsoFieldHelper.substring(de28, 1, de28.length() - 1));
      feeType = IsoFieldHelper.substring(de28, 0, 1);
    }

    double transactionAmount = getCalculateTransactionAmount(feeType, originalAmount, transactionFee);

    return Transaction.builder()
        .originalAmount(convertAmount(originalAmount, fractionDigit))
        .transactionFeeAmount(convertAmount(transactionFee, fractionDigit))
        .transactionAmount(convertAmount(transactionAmount, fractionDigit))
        .originalCurrencyCode(de49)
        .build();
  }

  private static double getCalculateTransactionAmount(String feeType, double originalAmount, double transactionFee) {
    return "C".equalsIgnoreCase(feeType) ?
        (originalAmount + transactionFee) : (originalAmount - transactionFee);
  }

  static Merchant createMerchant(IsoMessage isoMessage) {
    String de18 = IsoFieldHelper.getField(isoMessage, 18);
    String de43 = IsoFieldHelper.getField(isoMessage, 43);

    return Merchant.builder()
        .merchantCategoryCode(de18)
        .merchantName(IsoFieldHelper.substring(de43, 0, 25))
        .merchantCity(IsoFieldHelper.substring(de43, 25, 38))
        .merchantCountryCode(IsoFieldHelper.substring(de43, 38, 40))
        .build();
  }

  static Reversal createReversal(IsoMessage isoMessage) {
    if (!isoMessage.hasField(90)) return null;

    String de90 = IsoFieldHelper.getField(isoMessage, 90);

    return Reversal.builder()
        .originalMti(IsoFieldHelper.substring(de90, 0, 4))
        .originalStan(IsoFieldHelper.substring(de90, 4, 10))
        .originalTransmissionDateTime(IsoFieldHelper.substring(de90, 10, 20))
        .originalAcquiringInstitutionId(IsoFieldHelper.substring(de90, 20, 31))
        .originalForwardingInstitutionId(IsoFieldHelper.substring(de90, 31, 42))
        .build();
  }

  static BigDecimal convertAmount(double amount, int fractionDigit) {
    if (fractionDigit < 1 || amount == 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    return BigDecimal.valueOf(amount)
        .movePointLeft(fractionDigit)
        .setScale(2, RoundingMode.HALF_UP);
  }
}
