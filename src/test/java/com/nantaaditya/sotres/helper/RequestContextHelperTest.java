package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.model.constant.IsoCategory;
import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.model.dto.RequestContext;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
            systemPropertiesService.getProperty(PropertiesGroup.CURRENCY_FRACTIONS, "fractions"))
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
}
