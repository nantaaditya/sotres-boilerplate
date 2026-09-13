package com.nantaaditya.sotres.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.helper.ContextHelper;
import com.nantaaditya.sotres.helper.ObservationWrapper;
import com.nantaaditya.sotres.helper.ResponseHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.ApiResponseCode;
import com.nantaaditya.sotres.model.error.GeneralFlowException;
import com.nantaaditya.sotres.model.request.RetryDeadLetterProcessRequest;
import com.nantaaditya.sotres.model.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@DisplayName("ApiExceptionHandler")
class ApiExceptionHandlerTest {

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  private ApiExceptionHandler exceptionHandler;

  @BeforeEach
  void setUp() {
    ResponseHelper responseHelper =
        new ResponseHelper(mock(TracerHelper.class), mock(ContextHelper.class));
    exceptionHandler = new ApiExceptionHandler(new ObjectMapper(), responseHelper,
        mock(ObservationWrapper.class), mock(HttpServletRequest.class));
  }

  @Test
  @DisplayName("NoResourceFoundException -> code 900, endpoint violation")
  void noResourceFoundException_returnsInvalidParamsWithEndpointViolation() {
    NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/no/such/path");

    Response<Object> response = exceptionHandler.noResourceFoundException(ex);

    assertThat(response.getResponse().getCode()).isEqualTo(ApiResponseCode.BAD_REQUEST.getCode());
    assertThat(response.getError().getViolations()).containsKey("endpoint");
  }

  @Test
  @DisplayName("BadSqlGrammarException -> code 999, empty violations on the response itself")
  void sqlException_badSqlGrammar_returnsInternalError() {
    BadSqlGrammarException ex =
        new BadSqlGrammarException("select", "select * from nowhere", new SQLException("bad sql"));

    Response<Object> response = exceptionHandler.sqlException(ex);

    assertThat(response.getResponse().getCode()).isEqualTo(ApiResponseCode.INTERNAL_ERROR.getCode());
    assertThat(response.getError().getViolations()).isEmpty();
  }

  @Test
  @DisplayName("GeneralFlowException with no violations -> maps straight through, no context write")
  void generalFlowException_noViolations_mapsThrough() {
    GeneralFlowException ex = new GeneralFlowException(ApiResponseCode.BAD_REQUEST);

    Response<Object> response = exceptionHandler.sqlException(ex);

    assertThat(response.getResponse().getCode()).isEqualTo(ApiResponseCode.BAD_REQUEST.getCode());
    assertThat(response.getError().getViolations()).isEmpty();
  }

  @Test
  @DisplayName("GeneralFlowException with violations -> violations carried through to the response")
  void generalFlowException_withViolations_carriesViolationsThrough() {
    Map<String, java.util.List<String>> violations = Map.of("field", java.util.List.of("NotValid"));
    GeneralFlowException ex = new GeneralFlowException(ApiResponseCode.INVALID_PARAMS, violations);

    Response<Object> response = exceptionHandler.sqlException(ex);

    assertThat(response.getResponse().getCode()).isEqualTo(ApiResponseCode.INVALID_PARAMS.getCode());
    assertThat(response.getError().getViolations()).isEqualTo(violations);
  }

  @Test
  @DisplayName("ConstraintViolationException (real bean-validation violations) -> code 900, per-field violations")
  void constraintViolationException_realViolations_returnsPerFieldErrors() {
    RetryDeadLetterProcessRequest invalid = new RetryDeadLetterProcessRequest("", "", 0);
    Set<ConstraintViolation<RetryDeadLetterProcessRequest>> violations = VALIDATOR.validate(invalid);
    ConstraintViolationException ex = new ConstraintViolationException(violations);

    Response<Object> response = exceptionHandler.constraintViolationException(ex);

    assertThat(response.getResponse().getCode()).isEqualTo(ApiResponseCode.INVALID_PARAMS.getCode());
    assertThat(response.getError().getViolations())
        .containsKeys("processType", "processName", "size");
  }

  @Test
  @DisplayName("InvalidTemplateException -> code 900, template violation")
  void invalidTemplateException_returnsTemplateViolation() {
    var ex = new com.nantaaditya.sotres.model.error.InvalidTemplateException("bad JSLT");

    Response<Object> response = exceptionHandler.invalidTemplateException(ex);

    assertThat(response.getResponse().getCode()).isEqualTo(ApiResponseCode.INVALID_PARAMS.getCode());
    assertThat(response.getError().getViolations()).containsKey("template");
  }

  @Test
  @DisplayName("HttpMessageNotReadableException -> code 998, body violation")
  void httpMessageNotReadableException_returnsBodyViolation() {
    var ex = new org.springframework.http.converter.HttpMessageNotReadableException(
        "malformed", (org.springframework.http.HttpInputMessage) null);

    Response<Object> response = exceptionHandler.httpMessageNotReadableException(ex);

    assertThat(response.getResponse().getCode()).isEqualTo(ApiResponseCode.BAD_REQUEST.getCode());
    assertThat(response.getError().getViolations()).containsKey("body");
  }

  @Test
  @DisplayName("any other Throwable -> code 999, exception message captured")
  void throwable_returnsInternalErrorWithExceptionMessage() {
    RuntimeException ex = new RuntimeException("unexpected failure");

    Response<Object> response = exceptionHandler.throwable(ex);

    assertThat(response.getResponse().getCode()).isEqualTo(ApiResponseCode.INTERNAL_ERROR.getCode());
    assertThat(response.getError().getViolations().get("exception")).containsExactly("unexpected failure");
  }
}
