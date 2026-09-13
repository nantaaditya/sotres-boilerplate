package com.nantaaditya.sotres.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.nantaaditya.sotres.model.constant.BackoffPolicyConstant;
import com.nantaaditya.sotres.properties.LogProperties;
import com.nantaaditya.sotres.properties.RetryProperties;
import com.nantaaditya.sotres.properties.embedded.RetryConfiguration;
import com.nantaaditya.sotres.repository.DeadLetterProcessRepository;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.backoff.UniformRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("RetryTemplateConfiguration")
@ExtendWith(MockitoExtension.class)
class RetryTemplateConfigurationTest {

  @Mock
  private DeadLetterProcessRepository deadLetterProcessRepository;

  private static final Gson GSON = new Gson();
  private static final LogProperties LOG_PROPERTIES =
      new LogProperties(true, true, true, "cardNo,password", "json", "");

  private RetryTemplate build(RetryConfiguration configuration) {
    RetryProperties properties = new RetryProperties(Map.of("test", configuration));
    RetryTemplateConfiguration configurationUnderTest = new RetryTemplateConfiguration(
        properties, deadLetterProcessRepository, new ObjectMapper(), GSON, LOG_PROPERTIES);
    return configurationUnderTest.retryTemplateHelperFactory().getObject().getRetryTemplate("test");
  }

  private static RetryConfiguration config(BackoffPolicyConstant type, int maxAttempt,
      String retryableExceptions) {
    return new RetryConfiguration(type, 5L, 2.0, 40L, maxAttempt, false, retryableExceptions);
  }

  private static int countInvocations(RetryTemplate template, RuntimeException toThrow) {
    AtomicInteger calls = new AtomicInteger();
    RetryCallback<Void, RuntimeException> callback = context -> {
      calls.incrementAndGet();
      throw toThrow;
    };
    assertThatThrownBy(() -> template.execute(callback)).isSameAs(toThrow);
    return calls.get();
  }

  @Nested
  @DisplayName("backoff policy selection")
  class BackoffPolicySelection {

    @Test
    @DisplayName("FIXED -> FixedBackOffPolicy with the initial interval as the period")
    void fixed() {
      RetryTemplate template = build(config(BackoffPolicyConstant.FIXED, 2,
          "java.lang.IllegalStateException:true"));

      Object backOff = ReflectionTestUtils.getField(template, "backOffPolicy");
      assertThat(backOff).isExactlyInstanceOf(FixedBackOffPolicy.class);
      assertThat(((FixedBackOffPolicy) backOff).getBackOffPeriod()).isEqualTo(5L);
    }

    @Test
    @DisplayName("EXPONENTIAL -> ExponentialBackOffPolicy with initial/multiplier/max")
    void exponential() {
      RetryTemplate template = build(config(BackoffPolicyConstant.EXPONENTIAL, 2,
          "java.lang.IllegalStateException:true"));

      Object backOff = ReflectionTestUtils.getField(template, "backOffPolicy");
      assertThat(backOff).isExactlyInstanceOf(ExponentialBackOffPolicy.class);
      ExponentialBackOffPolicy policy = (ExponentialBackOffPolicy) backOff;
      assertThat(policy.getInitialInterval()).isEqualTo(5L);
      assertThat(policy.getMultiplier()).isEqualTo(2.0);
      assertThat(policy.getMaxInterval()).isEqualTo(40L);
    }

    @Test
    @DisplayName("EXPONENTIAL_RANDOM -> ExponentialRandomBackOffPolicy")
    void exponentialRandom() {
      RetryTemplate template = build(config(BackoffPolicyConstant.EXPONENTIAL_RANDOM, 2,
          "java.lang.IllegalStateException:true"));

      Object backOff = ReflectionTestUtils.getField(template, "backOffPolicy");
      assertThat(backOff).isExactlyInstanceOf(ExponentialRandomBackOffPolicy.class);
      assertThat(((ExponentialRandomBackOffPolicy) backOff).getInitialInterval()).isEqualTo(5L);
    }

    @Test
    @DisplayName("UNIFORM_RANDOM -> UniformRandomBackOffPolicy with min/max from initial/max interval")
    void uniformRandom() {
      RetryTemplate template = build(config(BackoffPolicyConstant.UNIFORM_RANDOM, 2,
          "java.lang.IllegalStateException:true"));

      Object backOff = ReflectionTestUtils.getField(template, "backOffPolicy");
      assertThat(backOff).isExactlyInstanceOf(UniformRandomBackOffPolicy.class);
      UniformRandomBackOffPolicy policy = (UniformRandomBackOffPolicy) backOff;
      assertThat(policy.getMinBackOffPeriod()).isEqualTo(5L);
      assertThat(policy.getMaxBackOffPeriod()).isEqualTo(40L);
    }
  }

  @Nested
  @DisplayName("retry policy")
  class Policy {

    @Test
    @DisplayName("max-attempt is the total execution count")
    void maxAttemptIsTotal() {
      RetryTemplate three = build(config(BackoffPolicyConstant.FIXED, 3,
          "java.lang.IllegalStateException:true"));
      assertThat(countInvocations(three, new IllegalStateException("x"))).isEqualTo(3);

      RetryTemplate one = build(config(BackoffPolicyConstant.FIXED, 1,
          "java.lang.IllegalStateException:true"));
      assertThat(countInvocations(one, new IllegalStateException("x"))).isEqualTo(1);
    }

    @Test
    @DisplayName("max-attempt below 1 is clamped to a single execution")
    void maxAttemptClamped() {
      RetryTemplate template = build(config(BackoffPolicyConstant.FIXED, 0,
          "java.lang.IllegalStateException:true"));

      Object retryPolicy = ReflectionTestUtils.getField(template, "retryPolicy");
      assertThat(retryPolicy).isInstanceOf(SimpleRetryPolicy.class);
      assertThat(((SimpleRetryPolicy) retryPolicy).getMaxAttempts()).isEqualTo(1);
      assertThat(countInvocations(template, new IllegalStateException("x"))).isEqualTo(1);
    }

    @Test
    @DisplayName("non-empty whitelist: only listed exceptions (and subclasses) retry")
    void whitelist() {
      RetryTemplate template = build(config(BackoffPolicyConstant.FIXED, 3,
          "java.lang.IllegalStateException:true"));

      assertThat(countInvocations(template, new IllegalStateException("retry me"))).isEqualTo(3);
      assertThat(countInvocations(template, new IllegalArgumentException("not me"))).isEqualTo(1);
    }

    @Test
    @DisplayName("empty whitelist with a blacklist: retry anything except the blacklisted type")
    void blacklistOnly() {
      RetryTemplate template = build(config(BackoffPolicyConstant.FIXED, 3,
          "java.lang.IllegalArgumentException:false"));

      assertThat(countInvocations(template, new IllegalStateException("retry by default"))).isEqualTo(3);
      assertThat(countInvocations(template, new IllegalArgumentException("blacklisted"))).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("factory")
  class Factory {

    @Test
    @DisplayName("returns null for an unknown retry name")
    void unknownName() {
      RetryProperties properties = new RetryProperties(Map.of("test",
          config(BackoffPolicyConstant.FIXED, 2, "java.lang.IllegalStateException:true")));
      RetryTemplateConfiguration configuration = new RetryTemplateConfiguration(
          properties, deadLetterProcessRepository, new ObjectMapper(), GSON, LOG_PROPERTIES);

      assertThat(configuration.retryTemplateHelperFactory().getObject().getRetryTemplate("missing"))
          .isNull();
    }

    @Test
    @DisplayName("no configurations -> empty factory, no templates")
    void noConfigurations() {
      RetryTemplateConfiguration configuration = new RetryTemplateConfiguration(
          new RetryProperties(Map.of()), deadLetterProcessRepository, new ObjectMapper(), GSON, LOG_PROPERTIES);

      assertThat(configuration.retryTemplateHelperFactory().getObject().getRetryTemplate("test"))
          .isNull();
    }
  }
}
