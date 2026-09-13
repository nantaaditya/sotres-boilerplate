package com.nantaaditya.sotres.factory;

import com.nantaaditya.sotres.helper.RetryTemplateHelper;
import com.nantaaditya.sotres.properties.embedded.RetryConfiguration;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.retry.support.RetryTemplate;

/**
 * Exposes the {@code <name>RetryTemplate} map and the source {@code <name> -> RetryConfiguration}
 * map built by {@code RetryTemplateConfiguration} as a single {@link RetryTemplateHelper} bean.
 * Mirrors {@code RetryProcessorHelperFactory}.
 */
@Setter
public class RetryTemplateHelperFactory implements FactoryBean<RetryTemplateHelper> {

  private Map<String, RetryTemplate> retryTemplates = new HashMap<>();
  private Map<String, RetryConfiguration> retryConfigurations = new HashMap<>();

  @Override
  public RetryTemplateHelper getObject() {
    return new RetryTemplateHelperImpl(retryTemplates, retryConfigurations);
  }

  @Override
  public Class<?> getObjectType() {
    return RetryTemplateHelper.class;
  }

  @AllArgsConstructor
  private static class RetryTemplateHelperImpl implements RetryTemplateHelper {

    private static final String POSTFIX_BEAN_NAME = "RetryTemplate";

    private final Map<String, RetryTemplate> retryTemplates;
    private final Map<String, RetryConfiguration> retryConfigurations;

    @Override
    public RetryTemplate getRetryTemplate(String retryTemplateName) {
      return retryTemplates.get(retryTemplateName + POSTFIX_BEAN_NAME);
    }

    @Override
    public RetryConfiguration getRetryConfiguration(String retryTemplateName) {
      return retryConfigurations.get(retryTemplateName);
    }
  }
}
