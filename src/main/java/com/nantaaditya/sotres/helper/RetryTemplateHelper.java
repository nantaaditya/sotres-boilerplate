package com.nantaaditya.sotres.helper;

import com.nantaaditya.sotres.properties.embedded.RetryConfiguration;
import org.springframework.retry.support.RetryTemplate;

/**
 * Lookup for the per-client {@link RetryTemplate} beans (and their source
 * {@link RetryConfiguration}) built from {@code apps.retry.configurations.<name>}. Both accessors
 * return {@code null} when no configuration exists for the given name.
 */
public interface RetryTemplateHelper {
  RetryTemplate getRetryTemplate(String retryTemplateName);

  RetryConfiguration getRetryConfiguration(String retryTemplateName);
}
