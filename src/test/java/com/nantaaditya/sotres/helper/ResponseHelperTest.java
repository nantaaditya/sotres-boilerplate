package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nantaaditya.sotres.model.constant.ApiResponseCode;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("ResponseHelper")
@ExtendWith(MockitoExtension.class)
class ResponseHelperTest {

  @Mock
  private TracerHelper tracerHelper;
  @Mock
  private ContextHelper contextHelper;

  private ResponseHelper responseHelper;

  @BeforeEach
  void setUp() {
    responseHelper = new ResponseHelper(tracerHelper, contextHelper);
    when(tracerHelper.getBaggage(HeaderConstant.REQUEST_ID)).thenReturn("req-123");
  }

  @Test
  @DisplayName("success returns Response with SUCCESS code and data")
  void success_returnsResponseWithSuccessCodeAndData() {
    Response<String> result = responseHelper.success("payload");

    assertThat(result).isNotNull();
    assertThat(result.getResponse().getCode()).isEqualTo(ApiResponseCode.SUCCESS.getCode());
    assertThat(result.getResponse().getDescription()).isEqualTo(
        ApiResponseCode.SUCCESS.getMessage());
    assertThat(result.getData()).isEqualTo("payload");
    assertThat(result.getError()).isNull();
  }

  @Test
  @DisplayName("success updates ContextHelper with response metadata")
  void success_updatesContextHelperWithResponseMetadata() {
    responseHelper.success("payload");

    verify(contextHelper).update(eq("req-123"), any());
  }

  @Test
  @DisplayName("failed returns Response with given error code and no data")
  void failed_returnsResponseWithGivenErrorCode() {
    Response<Object> result = responseHelper.failed(ApiResponseCode.BAD_REQUEST, Map.of());

    assertThat(result).isNotNull();
    assertThat(result.getResponse().getCode()).isEqualTo(ApiResponseCode.BAD_REQUEST.getCode());
    assertThat(result.getResponse().getDescription()).isEqualTo(
        ApiResponseCode.BAD_REQUEST.getMessage());
    assertThat(result.getData()).isNull();
  }

  @Test
  @DisplayName("failed returns Response with violations in ErrorMetadata")
  void failed_returnsResponseWithViolations() {
    Map<String, List<String>> violations = Map.of("field", List.of("must not be blank"));

    Response<Object> result = responseHelper.failed(ApiResponseCode.INVALID_PARAMS, violations);

    assertThat(result.getError()).isNotNull();
    assertThat(result.getError().getViolations()).containsKey("field");
  }

  @Test
  @DisplayName("failed updates ContextHelper with response metadata")
  void failed_updatesContextHelperWithResponseMetadata() {
    responseHelper.failed(ApiResponseCode.INTERNAL_ERROR, Map.of());

    verify(contextHelper).update(eq("req-123"), any());
  }
}
