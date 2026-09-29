package com.nantaaditya.sotres.api.internal;

import com.nantaaditya.sotres.api.BaseController;
import com.nantaaditya.sotres.model.constant.ConfigGroup;
import com.nantaaditya.sotres.model.response.Response;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal-api/configurations")
@RequiredArgsConstructor
public class SystemPropertiesController extends BaseController {

  private final SystemPropertiesService systemPropertiesService;

  @PutMapping(
      value = "/_reload",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public ResponseEntity<Response<Boolean>> reload(@RequestParam ConfigGroup group) {
    systemPropertiesService.reload(group);
    return toResponse(responseHelper.success(Boolean.TRUE));
  }

  @GetMapping(
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public ResponseEntity<Response<Map<String, String>>> find(@RequestParam ConfigGroup key) {
    return toResponse(responseHelper.success(systemPropertiesService.getProperty(key)));
  }
}
