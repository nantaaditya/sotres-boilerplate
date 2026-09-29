package com.nantaaditya.sotres.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequestContext")
class RequestContextTest {

  @Test
  @DisplayName("getInvoice: null invoiceNo returns null")
  void getInvoice_nullInvoiceNo_returnsNull() {
    RequestContext context = RequestContext.builder().invoiceNo(null).build();

    assertThat(context.getInvoice()).isNull();
  }

  @Test
  @DisplayName("getInvoice: empty invoiceNo returns null")
  void getInvoice_emptyInvoiceNo_returnsNull() {
    RequestContext context = RequestContext.builder().invoiceNo("").build();

    assertThat(context.getInvoice()).isNull();
  }

  @Test
  @DisplayName("getInvoice: non-empty invoiceNo splits into invoiceNo/originalInvoiceNo")
  void getInvoice_nonEmptyInvoiceNo_splitsIntoInvoiceFields() {
    RequestContext context = RequestContext.builder().invoiceNo("1234567890ABCDEFGHIJ").build();

    RequestContext.Invoice invoice = context.getInvoice();

    assertThat(invoice).isNotNull();
    assertThat(invoice.invoiceNo()).isEqualTo("1234567890");
    assertThat(invoice.originalInvoiceNo()).isEqualTo("ABCDEFGHIJ");
  }
}
