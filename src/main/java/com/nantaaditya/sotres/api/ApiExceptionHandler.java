package com.nantaaditya.sotres.api;

import com.nantaaditya.sotres.helper.ObservationHelper;
import com.nantaaditya.sotres.helper.ObservationWrapper;
import com.nantaaditya.sotres.helper.ResponseHelper;
import com.nantaaditya.sotres.model.constant.ApiResponseCode;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.error.GeneralFlowException;
import com.nantaaditya.sotres.model.error.InvalidTemplateException;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.model.response.Response;
import io.micrometer.observation.Observation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Log4j2
@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler {

  private final ObjectMapper objectMapper;
  private final ResponseHelper responseHelper;
  private final ObservationWrapper observationWrapper;
  private final HttpServletRequest request;

  private static final String ERROR_LOG = "#ApiError - got error";
  private static final String EXCEPTION_KEY = "exception";
  private static final int START_INDEX = 0;

  private record ErrorResult(Map<String, List<String>> errors, Response<Object> response) {}

  @ResponseBody
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(NoResourceFoundException.class)
  public Response<Object> noResourceFoundException(NoResourceFoundException ex) {
    return toBaseErrorResponse(ex, error -> {
      Map<String, List<String>> errors = Map.of("endpoint", List.of("not available"));
      Response<Object> response = responseHelper.failed(ApiResponseCode.BAD_REQUEST, errors);
      return new ErrorResult(errors, response);
    });
  }

  @ResponseBody
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(BadSqlGrammarException.class)
  public Response<Object> sqlException(BadSqlGrammarException ex) {
    return toBaseErrorResponse(ex, error -> {
      Map<String, List<String>> errors = Map.of(EXCEPTION_KEY, List.of(ex.getMessage()));
      Response<Object> response = responseHelper.failed(ApiResponseCode.INTERNAL_ERROR, Collections.emptyMap());
      return new ErrorResult(errors, response);
    });
  }

  @ResponseBody
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(GeneralFlowException.class)
  public Response<Object> sqlException(GeneralFlowException ex) {
    return toBaseErrorResponse(ex, error -> {
      Map<String, List<String>> errors = ex.getViolations();
      Response<Object> response = responseHelper.failed(ex.getResponse(), errors);
      return new ErrorResult(errors, response);
    });
  }

  @ResponseBody
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(HandlerMethodValidationException.class)
  public Response<Object> handlerMethodValidationException(HandlerMethodValidationException ex) {
    return toBaseErrorResponse(ex, error -> {
      List<String> errorKeys = ex.getParameterValidationResults()
          .stream()
          .map(ParameterValidationResult::getMethodParameter)
          .map(MethodParameter::getParameter)
          .map(Parameter::getName)
          .toList();
      Map<String, List<String>> errors = new HashMap<>();
      for (String errorKey : errorKeys) {
        errors.put(errorKey, List.of("NotValid"));
      }
      Response<Object> response = responseHelper.failed(ApiResponseCode.INVALID_PARAMS, errors);
      return new ErrorResult(errors, response);
    });
  }

  @ResponseBody
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public Response<Object> methodArgumentNotValidException(MethodArgumentNotValidException ex) {
    return toBaseErrorResponse(ex, error -> {
      Map<String, List<String>> errors = new HashMap<>();
      for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
        putEntry(errors, fieldError.getField(),
            fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "NotValid");
      }
      if (errors.isEmpty()) {
        errors.put("body", List.of("NotValid"));
      }
      Response<Object> response = responseHelper.failed(ApiResponseCode.INVALID_PARAMS, errors);
      return new ErrorResult(errors, response);
    });
  }

  @ResponseBody
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public Response<Object> httpMessageNotReadableException(HttpMessageNotReadableException ex) {
    return toBaseErrorResponse(ex, error -> {
      Map<String, List<String>> errors = Map.of("body", List.of("not readable"));
      Response<Object> response = responseHelper.failed(ApiResponseCode.BAD_REQUEST, errors);
      return new ErrorResult(errors, response);
    });
  }

  @ResponseBody
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ConstraintViolationException.class)
  public Response<Object> constraintViolationException(ConstraintViolationException ex) {
    return toBaseErrorResponse(ex, (ConstraintViolationException error) -> {
      Map<String, List<String>> map = new HashMap<>();

      error.getConstraintViolations()
        .forEach(violation -> {
          for (String attribute : getAttributes(violation)) {
            putEntry(map, attribute, violation.getMessage());
          }
        });

      Response<Object> response = responseHelper.failed(ApiResponseCode.INVALID_PARAMS, map);
      return new ErrorResult(map, response);
    });
  }

  @ResponseBody
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(InvalidTemplateException.class)
  public Response<Object> invalidTemplateException(InvalidTemplateException ex) {
    return toBaseErrorResponse(ex, error -> {
      Map<String, List<String>> errors = Map.of("template", List.of("NotValid"));
      Response<Object> response = responseHelper.failed(ApiResponseCode.INVALID_PARAMS, errors);
      return new ErrorResult(errors, response);
    });
  }

  @ResponseBody
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(Throwable.class)
  public Response<Object> throwable(Throwable ex) {
    return toBaseErrorResponse(ex, error -> {
      Map<String, List<String>> errors = Map.of(EXCEPTION_KEY, List.of(ex.getMessage()));
      Response<Object> response = responseHelper.failed(ApiResponseCode.INTERNAL_ERROR, Collections.emptyMap());
      return new ErrorResult(errors, response);
    });
  }

  private <T extends Throwable> Response<Object> toBaseErrorResponse(T ex, Function<T, ErrorResult> function) {
    log.error(AppLogMessage.message(ERROR_LOG).error(ex));

    ErrorResult result = function.apply(ex);

    if (!result.errors().isEmpty()) {
      String exceptionDetail = getErrors(result.errors());
      responseHelper.getContextHelper().put(getRequestId(), exceptionDetail);
    }

    Observation observation = observationWrapper.getObservation(request);
    ObservationHelper.observeResponse(observation, result.response.getResponse().getCode(), ex);

    return result.response();
  }

  private String getRequestId() {
    return responseHelper.getTracerHelper().getBaggage(HeaderConstant.REQUEST_ID);
  }

  private String getErrors(Map<String, List<String>> violations) {
    try {
      return objectMapper.writeValueAsString(violations);
    } catch (JacksonException e) {
      log.error(AppLogMessage.message("#ApiError - failed convert errors").error(e));
      return null;
    }
  }

  private void putEntry(Map<String, List<String>> map, String key, String value) {
    map.computeIfAbsent(key, r -> new LinkedList<>());
    map.get(key).add(value);
  }

  private String[] getAttributes(ConstraintViolation<?> constraintViolation) {
    String[] values = (String[]) constraintViolation.getConstraintDescriptor()
        .getAttributes()
        .get("path");
    if (values == null || values.length == START_INDEX) {
      return getAttributesFromPath(constraintViolation);
    } else {
      return values;
    }
  }

  private String[] getAttributesFromPath(ConstraintViolation<?> constraintViolation) {
    Path path = constraintViolation.getPropertyPath();

    StringBuilder builder = new StringBuilder();
    path.forEach(node -> {
      if (node.getName() != null) {
        if (builder.length() > START_INDEX) {
          builder.append(".");
        }
        builder.append(node.getName());
      }
    });

    return new String[]{builder.toString()};
  }
}
