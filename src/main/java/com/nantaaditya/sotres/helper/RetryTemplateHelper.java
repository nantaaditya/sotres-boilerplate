package com.nantaaditya.sotres.helper;

import org.springframework.retry.support.RetryTemplate;

/**
 * Lookup for the per-client {@link RetryTemplate} beans built from
 * {@code apps.retry.configurations.<name>}. Returns {@code null} when no template is configured
 * for the given name.
 */
public interface RetryTemplateHelper {
  RetryTemplate getRetryTemplate(String retryTemplateName);
}
