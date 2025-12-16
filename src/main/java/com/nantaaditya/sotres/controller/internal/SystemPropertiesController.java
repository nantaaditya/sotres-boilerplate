package com.nantaaditya.sotres.controller.internal;

import com.nantaaditya.sotres.model.constant.PropertiesGroup;
import com.nantaaditya.sotres.model.internal.Response;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal-api/configurations")
@RequiredArgsConstructor
public class SystemPropertiesController {

  private final SystemPropertiesService systemPropertiesService;

  @PutMapping(
      value = "/_reload",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Mono<Response<Boolean>> reload(@RequestParam PropertiesGroup group) {
    return Mono.fromCallable(() -> Response.success(true))
        .doOnNext(response -> systemPropertiesService.reload(group));
  }
}
