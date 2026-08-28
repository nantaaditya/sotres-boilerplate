package com.nantaaditya.sotres.api.internal;

import com.nantaaditya.sotres.api.BaseController;
import com.nantaaditya.sotres.helper.JsltTransformationHelper;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import com.nantaaditya.sotres.model.response.Response;
import com.nantaaditya.sotres.model.response.TemplateResponse;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/internal-api/jslt")
@RequiredArgsConstructor
public class JsltAdminController extends BaseController {

  private final JsltTransformationHelper jsltTransformationHelper;
  private final SystemPropertiesService systemPropertiesService;

  @PostMapping(value = "/_reload", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Response<Map<String, String>>>> reload(@RequestParam String selector) {
    // TODO(refactor): blocking JSLT helper wrapped for the still-reactive controller (Phase 3)
    return Mono.fromCallable(() -> jsltTransformationHelper.evictAndReload(selector))
        .subscribeOn(Schedulers.boundedElastic())
        .map(responseHelper::success)
        .flatMap(this::toResponse);
  }

  @GetMapping(value = "/templates", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Response<Map<String, String>>>> templates(@RequestParam String selector) {
    return Mono.fromCallable(() -> jsltTransformationHelper.getTemplates(selector))
        .subscribeOn(Schedulers.boundedElastic())
        .map(responseHelper::success)
        .flatMap(this::toResponse);
  }

  @PostMapping(value = "/_reload-all", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Response<Boolean>>> reloadAll() {
    return Mono.fromCallable(() -> {
          jsltTransformationHelper.evictAll();
          return responseHelper.success(Boolean.TRUE);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(this::toResponse);
  }

  @PutMapping(value = "/template",
      consumes = MediaType.TEXT_PLAIN_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Response<TemplateResponse>>> save(
      @RequestParam String selector,
      @RequestParam TemplateGroup group,
      @RequestBody String template) {
    return Mono.<Void>fromRunnable(() -> jsltTransformationHelper.validateTemplate(template))
        .subscribeOn(Schedulers.boundedElastic())
        .then(Mono.defer(() -> systemPropertiesService.upsert(group, selector, template)))
        .doOnNext(saved -> jsltTransformationHelper.evictExpression(group, selector))
        .map(saved -> responseHelper.success(TemplateResponse.from(saved)))
        .flatMap(this::toResponse);
  }
}
