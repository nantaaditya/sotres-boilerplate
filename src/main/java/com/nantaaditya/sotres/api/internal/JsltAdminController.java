package com.nantaaditya.sotres.api.internal;

import com.nantaaditya.sotres.api.BaseController;
import com.nantaaditya.sotres.entity.SystemProperties;
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

@RestController
@RequestMapping("/internal-api/jslt")
@RequiredArgsConstructor
public class JsltAdminController extends BaseController {

  private final JsltTransformationHelper jsltTransformationHelper;
  private final SystemPropertiesService systemPropertiesService;

  @PostMapping(value = "/_reload", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Response<Map<String, String>>> reload(@RequestParam String selector) {
    return toResponse(responseHelper.success(jsltTransformationHelper.evictAndReload(selector)));
  }

  @GetMapping(value = "/templates", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Response<Map<String, String>>> templates(@RequestParam String selector) {
    return toResponse(responseHelper.success(jsltTransformationHelper.getTemplates(selector)));
  }

  @PostMapping(value = "/_reload-all", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Response<Boolean>> reloadAll() {
    jsltTransformationHelper.evictAll();
    return toResponse(responseHelper.success(Boolean.TRUE));
  }

  @PutMapping(value = "/template",
      consumes = MediaType.TEXT_PLAIN_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Response<TemplateResponse>> save(
      @RequestParam String selector,
      @RequestParam TemplateGroup group,
      @RequestBody String template) {
    jsltTransformationHelper.validateTemplate(template);
    SystemProperties saved = systemPropertiesService.upsert(group, selector, template);
    jsltTransformationHelper.evictExpression(group, selector);
    return toResponse(responseHelper.success(TemplateResponse.from(saved)));
  }
}
