package com.nantaaditya.sotres.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.JsltException;
import com.schibsted.spt.data.jslt.Parser;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Log4j2
@Component
public class JsltTransformationHelper {

  // Stores cached (hot) Mono<Expression> so concurrent callers on the same key share one compilation.
  private final ConcurrentHashMap<String, Mono<Expression>> expressionCache = new ConcurrentHashMap<>();

  private final SystemPropertiesService systemPropertiesService;
  private final ObjectMapper objectMapper;

  public JsltTransformationHelper(
      SystemPropertiesService systemPropertiesService,
      ObjectMapper objectMapper) {
    this.systemPropertiesService = systemPropertiesService;
    this.objectMapper = objectMapper;
  }

  public Mono<JsonNode> transform(TemplateGroup group, String selector, Object input) {
    return expressionCache.computeIfAbsent(
        cacheKey(group, selector),
        k -> systemPropertiesService.getRawProperty(group, selector)
            .flatMap(template -> compileExpression(group.getGroup(), selector, template))
            .cache()  // cold → hot: first subscriber triggers compilation, rest replay the result
    )
    .flatMap(expr -> apply(expr, input))
    .switchIfEmpty(Mono.<JsonNode>fromCallable(() -> {
      log.warn(AppLogMessage.message("#JSLT - no template for group={} selector={}, using pass-through",
          group.getGroup(), selector));
      return objectMapper.valueToTree(input);
    }).subscribeOn(Schedulers.boundedElastic()));
  }

  public void evictExpression(TemplateGroup group, String selector) {
    expressionCache.remove(cacheKey(group, selector));
    log.info(AppLogMessage.message("#JSLT - evicted cache for group={} selector={}", group.getGroup(), selector));
  }

  public Mono<Map<String, String>> evictAndReload(String selector) {
    evictExpression(TemplateGroup.CLIENT_SPEC_REQUEST, selector);
    evictExpression(TemplateGroup.CLIENT_SPEC_RESPONSE, selector);

    Mono<String> request = systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, selector)
        .flatMap(template -> {
          Mono<Expression> compiled = compileExpression(TemplateGroup.CLIENT_SPEC_REQUEST.getGroup(), selector, template).cache();
          expressionCache.put(cacheKey(TemplateGroup.CLIENT_SPEC_REQUEST, selector), compiled);
          return compiled.thenReturn(template);
        })
        .defaultIfEmpty("");

    Mono<String> response = systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_RESPONSE, selector)
        .flatMap(template -> {
          Mono<Expression> compiled = compileExpression(TemplateGroup.CLIENT_SPEC_RESPONSE.getGroup(), selector, template).cache();
          expressionCache.put(cacheKey(TemplateGroup.CLIENT_SPEC_RESPONSE, selector), compiled);
          return compiled.thenReturn(template);
        })
        .defaultIfEmpty("");

    return Mono.zip(request, response)
        .map(t -> Map.of(
            TemplateGroup.CLIENT_SPEC_REQUEST.getGroup(), t.getT1(),
            TemplateGroup.CLIENT_SPEC_RESPONSE.getGroup(), t.getT2()
        ));
  }

  public Mono<Void> evictAll() {
    expressionCache.clear();
    log.info(AppLogMessage.message("#JSLT - evicted all cached expressions"));
    return Flux.merge(
        systemPropertiesService.getByGroupId(TemplateGroup.CLIENT_SPEC_REQUEST),
        systemPropertiesService.getByGroupId(TemplateGroup.CLIENT_SPEC_RESPONSE)
    ).flatMap(sp -> {
      String key = cacheKey(sp.getGroupId(), sp.getPropertyId());
      Mono<Expression> compiled = compileExpression(sp.getGroupId(), sp.getPropertyId(), sp.getPropertyValue()).cache();
      expressionCache.put(key, compiled);
      return compiled;
    })
    .then();
  }

  public Mono<Map<String, String>> getTemplates(String selector) {
    Mono<String> request = systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_REQUEST, selector)
        .defaultIfEmpty("");
    Mono<String> response = systemPropertiesService.getRawProperty(TemplateGroup.CLIENT_SPEC_RESPONSE, selector)
        .defaultIfEmpty("");
    return Mono.zip(request, response)
        .map(t -> Map.of(
            TemplateGroup.CLIENT_SPEC_REQUEST.getGroup(), t.getT1(),
            TemplateGroup.CLIENT_SPEC_RESPONSE.getGroup(), t.getT2()
        ));
  }

  private Mono<Expression> compileExpression(String groupId, String selector, String template) {
    return Mono.fromCallable(() -> {
      try {
        Expression expression = Parser.compileString(template);
        log.info(AppLogMessage.message("#JSLT - compiled template for group={} selector={}", groupId, selector));
        return expression;
      } catch (JsltException e) {
        log.error(AppLogMessage.message("#JSLT - failed to compile template for group={} selector={}", groupId, selector).error(e));
        throw e;
      }
    }).subscribeOn(Schedulers.boundedElastic());
  }

  private Mono<JsonNode> apply(Expression expression, Object input) {
    return Mono.fromCallable(() -> {
      JsonNode inputNode = objectMapper.valueToTree(input);
      return expression.apply(inputNode);
    }).subscribeOn(Schedulers.boundedElastic());
  }

  private String cacheKey(TemplateGroup group, String selector) {
    return group.getGroup() + ":" + selector;
  }

  private String cacheKey(String groupId, String selector) {
    return groupId + ":" + selector;
  }
}
