package com.nantaaditya.sotres.api.internal;

import com.nantaaditya.sotres.api.BaseController;
import com.nantaaditya.sotres.helper.JsltTransformationHelper;
import com.nantaaditya.sotres.model.response.Response;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal-api/jslt")
@RequiredArgsConstructor
public class JsltAdminController extends BaseController {

  private final JsltTransformationHelper jsltTransformationHelper;

  @PostMapping(value = "/_reload", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Response<Map<String, String>>>> reload(@RequestParam String selector) {
    return jsltTransformationHelper.evictAndReload(selector)
        .map(templates -> responseHelper.success(templates))
        .map(this::toResponse);
  }

  @GetMapping(value = "/templates", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Response<Map<String, String>>>> templates(@RequestParam String selector) {
    return jsltTransformationHelper.getTemplates(selector)
        .map(templates -> responseHelper.success(templates))
        .map(this::toResponse);
  }

  @PostMapping(value = "/_reload-all", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Response<Boolean>>> reloadAll() {
    return jsltTransformationHelper.evictAll()
        .then(Mono.fromCallable(() -> responseHelper.success(Boolean.TRUE)))
        .map(this::toResponse);
  }
}
