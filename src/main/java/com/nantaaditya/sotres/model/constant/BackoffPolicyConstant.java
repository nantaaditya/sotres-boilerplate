package com.nantaaditya.sotres.model.constant;

/**
 * Backoff strategy for {@code apps.retry.configurations.<name>.type}. Each value maps to a
 * concrete {@code org.springframework.retry.backoff.BackOffPolicy} in
 * {@code RetryTemplateConfiguration}.
 */
public enum BackoffPolicyConstant {
  FIXED,
  EXPONENTIAL,
  EXPONENTIAL_RANDOM,
  UNIFORM_RANDOM
}
