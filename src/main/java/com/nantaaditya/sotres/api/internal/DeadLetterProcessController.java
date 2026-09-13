package com.nantaaditya.sotres.api.internal;

import com.nantaaditya.sotres.api.BaseController;
import com.nantaaditya.sotres.model.request.RetryDeadLetterProcessRequest;
import com.nantaaditya.sotres.model.response.Response;
import com.nantaaditya.sotres.service.internal.DeadLetterProcessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/internal-api/dead_letter_process")
@RequiredArgsConstructor
public class DeadLetterProcessController extends BaseController {

  private final DeadLetterProcessService deadLetterProcessService;

  @DeleteMapping(
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public ResponseEntity<Response<Boolean>> remove(@RequestParam(required = false, defaultValue = "30") int days) {
    deadLetterProcessService.remove(days);
    return toResponse(responseHelper.success(Boolean.TRUE));
  }

  @PostMapping(
      value = "/_retry",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public ResponseEntity<Response<Boolean>> retry(@RequestBody @Valid RetryDeadLetterProcessRequest request) {
    deadLetterProcessService.retry(request);
    return toResponse(responseHelper.success(Boolean.TRUE));
  }
}
