package com.nantaaditya.sotres.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nantaaditya.sotres.helper.DateTimeHelper;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.model.constant.AccountType;
import java.beans.Transient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * internal DTO to map ISO8583 message
 * cardNo => DE2
 * processingCode => DE3
 * transmissionDateTime => DE7
 * stan => DE11
 * localTransactionTime => DE12
 * localTransactionDate => DE13
 * expirationDate => DE14
 * settlementDate => DE15
 * captureDate => DE17
 * posEntryMode => DE22
 * acquiringInstitutionId => DE32
 * forwardingInstitutionId => DE33
 * rrn => DE37
 * cardAcceptorTerminalId => DE41
 * cardAcceptorId => DE42
 * additionalData => DE48
 * originalDataElement => DE90
 * issuerId => DE100
 * accountIdentification => DE102
 * invoiceNo => DE123
 * transaction => DE4, DE28, DE49
 * merchant => DE18, DE43
 * reversal => DE90
 * invoice => DE123
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SuppressWarnings("squid:S1068")
public class RequestContext {
  private String mti;
  private String cardNo;
  private String processingCode;
  private String transmissionDateTime;
  private String stan;
  private String localTransactionTime;
  private String localTransactionDate;
  private String expirationDate;
  private String settlementDate;
  private String captureDate;
  private String posEntryMode;
  private String acquiringInstitutionId;
  private String forwardingInstitutionId;
  private String rrn;
  private String cardAcceptorTerminalId;
  private String cardAcceptorId;
  private String additionalData;
  private String originalDataElement;
  private String issuerId;
  private String accountIdentification;
  private String invoiceNo;
  private boolean lateResponse;
  private boolean orphanResponse;
  private boolean externalRequest;

  private Transaction transaction;
  private Merchant merchant;
  private Reversal reversal;
  @JsonIgnore
  private String isoFeatureConstant;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Transaction {
    private BigDecimal originalAmount;
    private String originalCurrencyCode;
    private BigDecimal transactionFeeAmount;
    private BigDecimal transactionAmount;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Merchant {
    private String merchantCategoryCode;
    private String merchantName;
    private String merchantCity;
    private String merchantCountryCode;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Reversal {
    private String originalMti;
    private String originalStan;
    private String originalTransmissionDateTime;
    private String originalAcquiringInstitutionId;
    private String originalForwardingInstitutionId;
  }

  /**
   * @param invoiceNo
   *  - for payment credit / debit => invoice no from acquirer
   *  - for refund => invoice no of payment credit / debit
   * @param originalInvoiceNo
   *  - for payment credit / debit => invoice no
   *  - for refund => last 10 digit of payment credit / debit RRN
   */
  public record Invoice (
      String invoiceNo,
      String originalInvoiceNo
  ){ };

  @Transient
  public ZonedDateTime convertTransmissionDateTime() {
    return DateTimeHelper.convertTransmissionDateTime(transmissionDateTime);
  }

  @Transient
  public LocalDate convertLocalTransactionDate() {
    return DateTimeHelper.convertLocalTransactionDate(localTransactionDate);
  }

  @Transient
  public LocalTime convertLocalTransactionTime() {
    return DateTimeHelper.convertLocalTransactionTime(localTransactionTime);
  }

  @Transient
  public LocalDate convertSettlementDate() {
    return DateTimeHelper.convertLocalTransactionDate(settlementDate);
  }

  @Transient
  public LocalDate convertCaptureDate() {
    return DateTimeHelper.convertLocalTransactionDate(captureDate);
  }

  @Transient
  public LocalDate convertExpiryDate() {
    return DateTimeHelper.convertExpiryDate(expirationDate);
  }

  @Transient
  public AccountType getFromAccountType() {
    return AccountType.fromCode(IsoFieldHelper.substring(processingCode, 2, 4));
  }

  @Transient
  public AccountType getToAccountType() {
    return AccountType.fromCode(IsoFieldHelper.substring(processingCode, 4, 6));
  }

  @Transient
  public Map<String, String> getAdditionalDataMap() {
    return IsoFieldHelper.unpackTLV(additionalData, 2, 2);
  }

  @Transient
  public void packAdditionalData(Map<String, String> additionalData) {
    this.additionalData = IsoFieldHelper.packTLV(additionalData, 2, 2);
  }

  @Transient
  public Invoice getInvoice() {
    if (invoiceNo == null || invoiceNo.isEmpty()) return null;

    return new Invoice(
        IsoFieldHelper.substring(invoiceNo, 0, 10),
        IsoFieldHelper.substring(invoiceNo,10, 20)
    );
  }

  // TODO: change this mapping
  @Transient
  public String getSelector() {
    return IsoFieldHelper.createSelector(mti, processingCode, getAdditionalDataMap());
  }
}
