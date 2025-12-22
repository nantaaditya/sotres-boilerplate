package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.IsoFeatureConstant;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;

public class RequestContextHelper {

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

    context.setTransaction(
        IsoFieldHelper.createTransaction(isoMessage, systemPropertiesService));
    context.setMerchant(IsoFieldHelper.createMerchant(isoMessage));
    context.setReversal(IsoFieldHelper.createReversal(isoMessage));
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

    return context;
  }
}
