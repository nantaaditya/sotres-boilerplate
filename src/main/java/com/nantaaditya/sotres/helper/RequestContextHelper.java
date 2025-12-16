package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.constant.FeatureConstant;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;

public class RequestContextHelper {

  private RequestContextHelper() {}

  public static RequestContext create(IsoMessage isoMessage, SystemPropertiesService systemPropertiesService) {
    RequestContext requestContext = RequestContext.builder()
        .mti(IsoFieldHelper.getMTI(isoMessage.getType()))
        .cardNo(isoMessage.getObjectValue(2))
        .processingCode(isoMessage.getObjectValue(3))
        .transmissionDateTime(isoMessage.getObjectValue(7))
        .stan(isoMessage.getObjectValue(11))
        .localTransactionTime(isoMessage.getObjectValue(12))
        .localTransactionDate(isoMessage.getObjectValue(13))
        .expirationDate(isoMessage.getObjectValue(14))
        .settlementDate(isoMessage.getObjectValue(15))
        .captureDate(isoMessage.getObjectValue(17))
        .posEntryMode(isoMessage.getObjectValue(22))
        .acquiringInstitutionId(isoMessage.getObjectValue(32))
        .forwardingInstitutionId(isoMessage.getObjectValue(33))
        .rrn(isoMessage.getObjectValue(37))
        .cardAcceptorTerminalId(isoMessage.getObjectValue(41))
        .cardAcceptorId(isoMessage.getObjectValue(42))
        .additionalData(isoMessage.getObjectValue(48))
        .originalDataElement(isoMessage.getObjectValue(90))
        .issuerId(isoMessage.getObjectValue(100))
        .accountIdentification(isoMessage.getObjectValue(102))
        .invoiceNo(isoMessage.getObjectValue(123))
        .build();

    requestContext.setTransaction(IsoFieldHelper.createTransaction(isoMessage, systemPropertiesService));
    requestContext.setMerchant(IsoFieldHelper.createMerchant(isoMessage));
    requestContext.setReversal(IsoFieldHelper.createReversal(isoMessage));
    requestContext.setFeatureConstant(FeatureConstant.getBySelector(requestContext.getSelector()));

    return requestContext;
  }
}
