package com.nantaaditya.sotres.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.nantaaditya.sotres.entity.SystemProperties;
import com.nantaaditya.sotres.model.constant.TemplateGroup;
import com.nantaaditya.sotres.model.error.InvalidTemplateException;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.CacheProperties;
import com.nantaaditya.sotres.properties.embedded.CacheConfiguration;
import com.nantaaditya.sotres.repository.SystemPropertiesRepository;
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

  private final Cache<String, Optional<Expression>> expressionCache;

  private final SystemPropertiesRepository systemPropertiesRepository;
  private final ObjectMapper objectMapper;

  public JsltTransformationHelper(
      SystemPropertiesRepository systemPropertiesRepository,
      ObjectMapper objectMapper,
      CaffeineCacheHelper caffeineCacheHelper) {
    this.systemPropertiesRepository = systemPropertiesRepository;
    this.objectMapper = objectMapper;

    this.expressionCache = caffeineCacheHelper.createCache(
        "jslt",
        caffeine -> caffeine.removalListener(((key, value, cause) -> {
          if (cause == RemovalCause.SIZE) {
            log.warn(AppLogMessage.message("#JSLT - evicted cache for key={} due to size limit", key));
          }
        }))
    );
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

  /**
   * Rewarms the cache for every configured selector. A template that fails to compile is still
   * cached as a pass-through (so live traffic degrades gracefully rather than throwing on every
   * message for that selector) but is reported as {@code false} in the returned map, keyed
   * {@code "<groupId>:<selector>"} — unlike the old behaviour, a broken template no longer looks
   * identical to a successful reload.
   */
  public Map<String, Boolean> evictAll() {
    expressionCache.invalidateAll();
    log.info(AppLogMessage.message("#JSLT - evicted all cached expressions"));

    Map<String, Boolean> results = new LinkedHashMap<>();
    for (TemplateGroup group : new TemplateGroup[]{
        TemplateGroup.CLIENT_SPEC_REQUEST, TemplateGroup.CLIENT_SPEC_RESPONSE}) {
      systemPropertiesRepository.findByGroupId(group.getGroup())
          .forEach(sp -> {
            Optional<Expression> compiled = compileQuietly(sp.getGroupId(), sp.getPropertyId(), sp.getPropertyValue());
            expressionCache.put(cacheKey(sp.getGroupId(), sp.getPropertyId()), compiled);
            results.put(cacheKey(sp.getGroupId(), sp.getPropertyId()), compiled.isPresent());
          });
    }
    return results;
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

  /**
   * Reloads one direction's template. A compile failure is still cached as a pass-through (so
   * this reload attempt doesn't make live traffic for the selector any worse than it already
   * was) but is thrown here as {@link InvalidTemplateException} so the caller — an operator
   * hitting the reload endpoint — is told loudly that the reload did not actually take effect,
   * instead of getting a {@code 200 OK} echoing the broken template text as if it succeeded.
   */
  private String reloadOne(TemplateGroup group, String selector) {
    String template = rawTemplate(group, selector);
    if (template == null || template.isBlank()) {
      return "";
    }
    Optional<Expression> compiled = compileQuietly(group.getGroup(), selector, template);
    expressionCache.put(cacheKey(group, selector), compiled);
    if (compiled.isEmpty()) {
      throw new InvalidTemplateException(
          "template for group=" + group.getGroup() + " selector=" + selector + " failed to compile");
    }
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
    return systemPropertiesRepository
        .findByGroupIdAndPropertyId(group.getGroup(), selector)
        .map(SystemProperties::getPropertyValue)
        .orElse(null);
  }

  private String cacheKey(TemplateGroup group, String selector) {
    return group.getGroup() + ":" + selector;
  }

  private String cacheKey(String groupId, String selector) {
    return groupId + ":" + selector;
  }
}
