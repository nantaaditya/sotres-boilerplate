package com.nantaaditya.sotres.api.internal;

import com.nantaaditya.sotres.api.BaseController;
import com.nantaaditya.sotres.model.response.Response;
import com.nantaaditya.sotres.service.internal.EventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal-api/event_log")
@RequiredArgsConstructor
public class EventLogController extends BaseController {

  private final EventLogService eventLogService;

  @DeleteMapping(
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public ResponseEntity<Response<Boolean>> remove(@RequestParam(required = false, defaultValue = "30") int days) {
    ResponseEntity<Response<Boolean>> response = toResponse(responseHelper.success(Boolean.TRUE));
    eventLogService.remove(days);
    return response;
  }
}
