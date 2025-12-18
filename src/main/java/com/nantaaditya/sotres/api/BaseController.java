package com.nantaaditya.sotres.api;

import com.nantaaditya.sotres.helper.ObservationHelper;
import com.nantaaditya.sotres.helper.ObservationWrapper;
import com.nantaaditya.sotres.helper.ResponseHelper;
import com.nantaaditya.sotres.model.constant.ApiResponseCode;
import com.nantaaditya.sotres.model.error.GeneralFlowException;
import com.nantaaditya.sotres.model.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

public class BaseController {

  @Autowired
  private ObservationWrapper observationWrapper;

  @Autowired
  protected ResponseHelper responseHelper;

  protected <T> ResponseEntity<Response<T>> toResponse(Response<T> tResponse) {
    ApiResponseCode responseCode = ApiResponseCode.fromCode(tResponse.getResponse().getCode());

    boolean isSuccess = ApiResponseCode.SUCCESS == responseCode;
    HttpStatusCode httpStatusCode = isSuccess ? HttpStatus.OK : HttpStatus.BAD_REQUEST;

    ObservationHelper.observeResponse(
        observationWrapper.getObservation(),
        responseCode.getCode(),
        isSuccess ? null : new GeneralFlowException(responseCode)
    );

    return new ResponseEntity<>(tResponse, httpStatusCode);
  }
}
