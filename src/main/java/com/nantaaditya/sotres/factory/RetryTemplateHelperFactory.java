package com.nantaaditya.sotres.factory;

import com.nantaaditya.sotres.helper.RetryTemplateHelper;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.retry.support.RetryTemplate;

/**
 * Exposes the {@code <name>RetryTemplate} map built by {@code RetryTemplateConfiguration} as a
 * single {@link RetryTemplateHelper} bean. Mirrors {@code RetryProcessorHelperFactory}.
 */
@Setter
public class RetryTemplateHelperFactory implements FactoryBean<RetryTemplateHelper> {

  private Map<String, RetryTemplate> retryTemplates = new HashMap<>();

  @Override
  public RetryTemplateHelper getObject() {
    return new RetryTemplateHelperImpl(retryTemplates);
  }

  @Override
  public Class<?> getObjectType() {
    return RetryTemplateHelper.class;
  }

  @AllArgsConstructor
  private static class RetryTemplateHelperImpl implements RetryTemplateHelper {

    private static final String POSTFIX_BEAN_NAME = "RetryTemplate";

    private final Map<String, RetryTemplate> retryTemplates;

    @Override
    public RetryTemplate getRetryTemplate(String retryTemplateName) {
      return retryTemplates.get(retryTemplateName + POSTFIX_BEAN_NAME);
    }
  }
}
