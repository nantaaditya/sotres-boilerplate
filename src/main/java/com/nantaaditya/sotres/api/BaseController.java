package com.nantaaditya.sotres.api;

import com.nantaaditya.sotres.helper.ObservationHelper;
import com.nantaaditya.sotres.helper.ResponseHelper;
import com.nantaaditya.sotres.model.constant.ApiResponseCode;
import com.nantaaditya.sotres.model.error.GeneralFlowException;
import com.nantaaditya.sotres.model.response.Response;
import io.micrometer.observation.Observation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public class BaseController {

  @Autowired
  protected ResponseHelper responseHelper;

  protected <T> Mono<ResponseEntity<Response<T>>> toResponse(Response<T> tResponse) {
    return Mono.deferContextual(ctx -> {
      ApiResponseCode responseCode = ApiResponseCode.fromCode(tResponse.getResponse().getCode());

      boolean isSuccess = ApiResponseCode.SUCCESS == responseCode;
      HttpStatusCode httpStatusCode = isSuccess ? HttpStatus.OK : HttpStatus.BAD_REQUEST;

      ObservationHelper.observeResponse(
          ctx.getOrDefault(Observation.class, null),
          responseCode.getCode(),
          isSuccess ? null : new GeneralFlowException(responseCode)
      );

      return Mono.just(new ResponseEntity<>(tResponse, httpStatusCode));
    });
  }
}
