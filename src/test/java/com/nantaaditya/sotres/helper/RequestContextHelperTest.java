package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.model.dto.RequestContext.Merchant;
import com.nantaaditya.sotres.model.dto.RequestContext.Reversal;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.IsoValue;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("RequestContextHelper")
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class RequestContextHelperTest {

  @Mock
  private IsoMessage isoMessage;
  @Mock
  private SystemPropertiesService systemPropertiesService;
  @Mock
  private IsoValue de4Field;
  @Mock
  private IsoValue de28Field;
  @Mock
  private IsoValue de48Field;

  @BeforeEach
  void setUp() {
    // DE4 = amount; DE28 = transaction fee (C-type, 12 chars); DE48 = additional data (empty TLV)
    lenient().when(de4Field.toString()).thenReturn("000000010000");
    lenient().when(de28Field.toString()).thenReturn("C00000000000");
    lenient().when(de48Field.toString()).thenReturn("");

    lenient().when(isoMessage.getType()).thenReturn(0x0200);
    lenient().when(isoMessage.getField(4)).thenReturn(de4Field);
    lenient().when(isoMessage.getField(28)).thenReturn(de28Field);
    lenient().when(isoMessage.getField(48)).thenReturn(de48Field);
    lenient().when(isoMessage.hasField(90)).thenReturn(false);

    lenient().when(
            systemPropertiesService.getProperty(ConfigGroup.CURRENCY_FRACTIONS, "fractions"))
        .thenReturn("360:2");
  }

  @Test
  @DisplayName("create with SUCCESS category returns RequestContext with mti populated")
  void create_withSuccessCategory_returnsMtiInRequestContext() {
    RequestContext context = RequestContextHelper.create(isoMessage, systemPropertiesService,
        IsoCategory.SUCCESS);

    assertThat(context).isNotNull();
    assertThat(context.getMti()).isEqualTo("0200");
  }

  @Test
  @DisplayName("create with LATE_RESPONSE sets lateResponse flag only")
  void create_withLateResponse_setsLateResponseFlagOnly() {
    RequestContext context = RequestContextHelper.create(isoMessage, systemPropertiesService,
        IsoCategory.LATE_RESPONSE);

    assertThat(context.isLateResponse()).isTrue();
    assertThat(context.isOrphanResponse()).isFalse();
    assertThat(context.isExternalRequest()).isFalse();
  }

  @Test
  @DisplayName("create with ORPHAN sets orphanResponse flag only")
  void create_withOrphan_setsOrphanResponseFlagOnly() {
    RequestContext context = RequestContextHelper.create(isoMessage, systemPropertiesService,
        IsoCategory.ORPHAN);

    assertThat(context.isOrphanResponse()).isTrue();
    assertThat(context.isLateResponse()).isFalse();
    assertThat(context.isExternalRequest()).isFalse();
  }

  @Test
  @DisplayName("create with EXTERNAL_REQUEST sets externalRequest flag only")
  void create_withExternalRequest_setsExternalRequestFlagOnly() {
    RequestContext context = RequestContextHelper.create(isoMessage, systemPropertiesService,
        IsoCategory.EXTERNAL_REQUEST);

    assertThat(context.isExternalRequest()).isTrue();
    assertThat(context.isLateResponse()).isFalse();
    assertThat(context.isOrphanResponse()).isFalse();
  }

  @Test
  @DisplayName("create builds reversal when DE90 is present")
  void create_whenDe90Present_buildsReversal() {
    IsoValue de90Field = Mockito.mock(IsoValue.class);
    // 42-char DE90: mti(4) + stan(6) + transmissionDateTime(10) + acquiringId(11) + forwardingId(11)
    String de90Value = "020012345606150101001234567890012345678901";
    when(de90Field.toString()).thenReturn(de90Value);
    when(isoMessage.hasField(90)).thenReturn(true);
    lenient().when(isoMessage.getField(90)).thenReturn(de90Field);

    RequestContext context = RequestContextHelper.create(isoMessage, systemPropertiesService,
        IsoCategory.SUCCESS);

    assertThat(context.getReversal()).isNotNull();
    assertThat(context.getReversal().getOriginalMti()).isEqualTo("0200");
    assertThat(context.getReversal().getOriginalStan()).isEqualTo("123456");
  }

  @Test
  @DisplayName("create with SUCCESS sets callbackResponse (this inbound message is our own callback reply)")
  void create_withSuccess_setsCallbackResponse() {
    RequestContext context = RequestContextHelper.create(isoMessage, systemPropertiesService,
        IsoCategory.SUCCESS);

    assertThat(context.isCallbackResponse()).isTrue();
  }

  @Test
  @DisplayName("create with LATE_RESPONSE sets callbackResponse")
  void create_withLateResponse_setsCallbackResponse() {
    RequestContext context = RequestContextHelper.create(isoMessage, systemPropertiesService,
        IsoCategory.LATE_RESPONSE);

    assertThat(context.isCallbackResponse()).isTrue();
  }

  @Test
  @DisplayName("create with ORPHAN sets callbackResponse")
  void create_withOrphan_setsCallbackResponse() {
    RequestContext context = RequestContextHelper.create(isoMessage, systemPropertiesService,
        IsoCategory.ORPHAN);

    assertThat(context.isCallbackResponse()).isTrue();
  }

  @Test
  @DisplayName("create with EXTERNAL_REQUEST leaves callbackResponse false (fresh switch-initiated request)")
  void create_withExternalRequest_leavesCallbackResponseFalse() {
    RequestContext context = RequestContextHelper.create(isoMessage, systemPropertiesService,
        IsoCategory.EXTERNAL_REQUEST);

    assertThat(context.isCallbackResponse()).isFalse();
  }

  @Test
  @DisplayName("create with null category leaves callbackResponse false")
  void create_withNullCategory_leavesCallbackResponseFalse() {
    RequestContext context = RequestContextHelper.create(isoMessage, systemPropertiesService, null);

    assertThat(context.isCallbackResponse()).isFalse();
  }

  @SuppressWarnings("unchecked")
  private IsoValue<Object> mockIsoValue(String value) {
    return new IsoValue<>(IsoType.ALPHA, value, value.length());
  }

  @Nested
  @DisplayName("convertAmount(double, int)")
  class ConvertAmount {

    @Test
    @DisplayName("moves decimal left by fractionDigit for a standard amount")
    void convertAmount_twoFractionDigits_movesDecimalLeft2() {
      BigDecimal result = RequestContextHelper.convertAmount(100000.0, 2);
      assertThat(result).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("moves decimal left by 3 for currencies with 3 fraction digits")
    void convertAmount_threeFractionDigits_movesDecimalLeft3() {
      BigDecimal result = RequestContextHelper.convertAmount(1000000.0, 3);
      assertThat(result).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("returns zero when amount is 0")
    void convertAmount_zeroAmount_returnsZero() {
      BigDecimal result = RequestContextHelper.convertAmount(0.0, 2);
      assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("returns zero when fractionDigit is less than 1")
    void convertAmount_zeroFractionDigit_returnsZero() {
      BigDecimal result = RequestContextHelper.convertAmount(50000.0, 0);
      assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("result always has scale of 2 after HALF_UP rounding")
    void convertAmount_anyValidInput_scaleIsTwo() {
      BigDecimal result = RequestContextHelper.convertAmount(100001.0, 2);
      assertThat(result.scale()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("createMerchant(IsoMessage)")
  class CreateMerchant {

    @Test
    @DisplayName("extracts MCC from DE18 and merchant name/city/country from DE43")
    void createMerchant_validFields_returnsMerchant() {
      when(isoMessage.getField(18)).thenReturn(mockIsoValue("5411"));
      // DE43 format: name(25 chars) + city(13 chars) + country code(2 chars) = 40 chars total
      when(isoMessage.getField(43)).thenReturn(
          mockIsoValue("GROCERY STORE NAME       JAKARTA      ID"));

      Merchant result = RequestContextHelper.createMerchant(isoMessage);

      assertThat(result).isNotNull();
      assertThat(result.getMerchantCategoryCode()).isEqualTo("5411");
      assertThat(result.getMerchantName()).isEqualTo("GROCERY STORE NAME       ");
      assertThat(result.getMerchantCountryCode()).isEqualTo("ID");
    }
  }

  @Nested
  @DisplayName("createReversal(IsoMessage)")
  class CreateReversal {

    @Test
    @DisplayName("returns null when DE90 is not present")
    void createReversal_noDE90_returnsNull() {
      when(isoMessage.hasField(90)).thenReturn(false);
      assertThat(RequestContextHelper.createReversal(isoMessage)).isNull();
    }

    @Test
    @DisplayName("extracts reversal fields from DE90 when present")
    void createReversal_withDE90_returnsReversal() {
      when(isoMessage.hasField(90)).thenReturn(true);
      // DE90: originalMti(4) + originalStan(6) + originalDateTime(10) + acquirer(11) + forwarding(11) = 42 chars
      when(isoMessage.getField(90))
          .thenReturn(mockIsoValue("020012345606151030450000000001100000000012"));

      Reversal result = RequestContextHelper.createReversal(isoMessage);

      assertThat(result).isNotNull();
      assertThat(result.getOriginalMti()).isEqualTo("0200");
      assertThat(result.getOriginalStan()).isEqualTo("123456");
      assertThat(result.getOriginalTransmissionDateTime()).isEqualTo("0615103045");
      assertThat(result.getOriginalAcquiringInstitutionId()).isEqualTo("00000000011");
      assertThat(result.getOriginalForwardingInstitutionId()).isEqualTo("00000000012");
    }
  }
}
