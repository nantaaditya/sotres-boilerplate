package com.nantaaditya.sotres.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import com.nantaaditya.sotres.model.error.InvalidTemplateException;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.JsltException;
import com.schibsted.spt.data.jslt.Parser;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Synchronous JSLT transform with a compile-once cache.
 *
 * <p>The cache value is {@code Optional<Expression>}; {@link Optional#empty()} is
 * the "no template configured, pass through" sentinel — it is cached like any
 * other entry (bounded by {@code expireAfterWrite}) so a mis-configured selector
 * no longer hits the DB on every message.
 */
@Log4j2
@Component
public class JsltTransformationHelper {

  private static final Duration CACHE_TTL = Duration.ofMinutes(10);

  private final Cache<String, Optional<Expression>> expressionCache = Caffeine.newBuilder()
      .expireAfterWrite(CACHE_TTL)
      .build();

  private final SystemPropertiesService systemPropertiesService;
  private final ObjectMapper objectMapper;

  public JsltTransformationHelper(
      SystemPropertiesService systemPropertiesService,
      ObjectMapper objectMapper) {
    this.systemPropertiesService = systemPropertiesService;
    this.objectMapper = objectMapper;
  }

  public JsonNode transform(TemplateGroup group, String selector, Object input) {
    Optional<Expression> expression = expressionCache.get(
        cacheKey(group, selector), k -> loadExpression(group, selector));

    if (expression == null || expression.isEmpty()) {
      return toJsonNode(input);
    }
    return apply(expression.get(), input);
  }

  public void evictExpression(TemplateGroup group, String selector) {
    expressionCache.invalidate(cacheKey(group, selector));
    log.info(AppLogMessage.message("#JSLT - evicted cache for group={} selector={}", group.getGroup(), selector));
  }

  public Map<String, String> evictAndReload(String selector) {
    evictExpression(TemplateGroup.CLIENT_SPEC_REQUEST, selector);
    evictExpression(TemplateGroup.CLIENT_SPEC_RESPONSE, selector);

    Map<String, String> templates = new LinkedHashMap<>();
    templates.put(TemplateGroup.CLIENT_SPEC_REQUEST.getGroup(),
        reloadOne(TemplateGroup.CLIENT_SPEC_REQUEST, selector));
    templates.put(TemplateGroup.CLIENT_SPEC_RESPONSE.getGroup(),
        reloadOne(TemplateGroup.CLIENT_SPEC_RESPONSE, selector));
    return templates;
  }

  public void evictAll() {
    expressionCache.invalidateAll();
    log.info(AppLogMessage.message("#JSLT - evicted all cached expressions"));

    for (TemplateGroup group : new TemplateGroup[]{
        TemplateGroup.CLIENT_SPEC_REQUEST, TemplateGroup.CLIENT_SPEC_RESPONSE}) {
      systemPropertiesService.getByGroupId(group).forEach(sp ->
          expressionCache.put(cacheKey(sp.getGroupId(), sp.getPropertyId()),
              compileQuietly(sp.getGroupId(), sp.getPropertyId(), sp.getPropertyValue())));
    }
  }

  public void validateTemplate(String template) {
    if (template == null || template.isBlank()) {
      throw new InvalidTemplateException("template must not be blank");
    }
    try {
      Parser.compileString(template);
    } catch (JsltException e) {
      throw new InvalidTemplateException("invalid JSLT syntax", e);
    }
  }

  public Map<String, String> getTemplates(String selector) {
    Map<String, String> templates = new LinkedHashMap<>();
    templates.put(TemplateGroup.CLIENT_SPEC_REQUEST.getGroup(),
        Optional.ofNullable(rawTemplate(TemplateGroup.CLIENT_SPEC_REQUEST, selector)).orElse(""));
    templates.put(TemplateGroup.CLIENT_SPEC_RESPONSE.getGroup(),
        Optional.ofNullable(rawTemplate(TemplateGroup.CLIENT_SPEC_RESPONSE, selector)).orElse(""));
    return templates;
  }

  private Optional<Expression> loadExpression(TemplateGroup group, String selector) {
    String template = rawTemplate(group, selector);
    if (template == null || template.isBlank()) {
      log.warn(AppLogMessage.message("#JSLT - no template for group={} selector={}, using pass-through",
          group.getGroup(), selector));
      return Optional.empty();
    }
    return Optional.of(compile(group.getGroup(), selector, template));
  }

  private String reloadOne(TemplateGroup group, String selector) {
    String template = rawTemplate(group, selector);
    if (template == null || template.isBlank()) {
      return "";
    }
    expressionCache.put(cacheKey(group, selector),
        compileQuietly(group.getGroup(), selector, template));
    return template;
  }

  private Optional<Expression> compileQuietly(String groupId, String selector, String template) {
    try {
      return Optional.of(compile(groupId, selector, template));
    } catch (JsltException e) {
      return Optional.empty();
    }
  }

  private Expression compile(String groupId, String selector, String template) {
    try {
      Expression expression = Parser.compileString(template);
      log.info(AppLogMessage.message("#JSLT - compiled template for group={} selector={}", groupId, selector));
      return expression;
    } catch (JsltException e) {
      log.error(AppLogMessage.message("#JSLT - failed to compile template for group={} selector={}", groupId, selector).error(e));
      throw e;
    }
  }

  private JsonNode apply(Expression expression, Object input) {
    return expression.apply(toJsonNode(input));
  }

  private JsonNode toJsonNode(Object input) {
    return (input instanceof JsonNode jsonNode) ? jsonNode : objectMapper.valueToTree(input);
  }

  private String rawTemplate(TemplateGroup group, String selector) {
    return systemPropertiesService.getRawProperty(group, selector);
  }

  private String cacheKey(TemplateGroup group, String selector) {
    return group.getGroup() + ":" + selector;
  }

  private String cacheKey(String groupId, String selector) {
    return groupId + ":" + selector;
  }
}
