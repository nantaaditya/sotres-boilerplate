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
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal-api/configurations")
@RequiredArgsConstructor
public class SystemPropertiesController extends BaseController {

  private final SystemPropertiesService systemPropertiesService;

  @PutMapping(
      value = "/_reload",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Mono<ResponseEntity<Response<Boolean>>> reload(@RequestParam ConfigGroup group) {
    return Mono.fromCallable(() -> responseHelper.success(Boolean.TRUE))
        .map(this::toResponse)
        .doOnSuccess(response -> systemPropertiesService.reload(group));
  }

  @GetMapping(
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Mono<ResponseEntity<Response<Map<String, String>>>> find(@RequestParam ConfigGroup key) {
    return Mono.fromCallable(() -> systemPropertiesService.getProperty(key))
        .map(result -> responseHelper.success(result))
        .map(this::toResponse);
  }
}
