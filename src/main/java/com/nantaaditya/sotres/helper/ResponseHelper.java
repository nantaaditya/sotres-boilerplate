package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.model.constant.ApiResponseCode;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.response.Response;
import com.nantaaditya.sotres.model.response.Response.ErrorMetadata;
import com.nantaaditya.sotres.model.response.Response.ResponseMetadata;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Getter
@Component
@RequiredArgsConstructor
public class ResponseHelper {
  private final TracerHelper tracerHelper;
  private final ContextHelper contextHelper;

  public <T> Response<T> success(T data) {
    Response.ResponseMetadata responseMetadata = Response.ResponseMetadata.builder()
        .code(ApiResponseCode.SUCCESS.getCode())
        .description(ApiResponseCode.SUCCESS.getMessage())
        .time(DateTimeHelper.getDateInFormat(ZonedDateTime.now(), DateTimeHelper.ISO_8601_GMT7_FORMAT))
        .build();

    String requestId = tracerHelper.getBaggage(HeaderConstant.REQUEST_ID);
    contextHelper.update(requestId, contextDTO ->
      contextDTO.withResponse(responseMetadata)
    );

    return Response.<T>builder()
        .response(responseMetadata)
        .data(data)
        .build();
  }

  public <T> Response<T> failed(ApiResponseCode responseCode, Map<String, List<String>> errors) {
    ResponseMetadata responseMetadata = ResponseMetadata.builder()
        .code(responseCode.getCode())
        .description(responseCode.getMessage())
        .time(DateTimeHelper.getDateInFormat(ZonedDateTime.now(), DateTimeHelper.ISO_8601_GMT7_FORMAT))
        .build();

    String requestId = tracerHelper.getBaggage(HeaderConstant.REQUEST_ID);
    contextHelper.update(requestId, contextDTO ->
        contextDTO.withResponse(responseMetadata)
    );

    return Response.<T>builder()
        .response(responseMetadata)
        .error(ErrorMetadata.builder()
            .violations(errors)
            .build()
        )
        .build();
  }
}
