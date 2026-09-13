package com.nantaaditya.sotres.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nantaaditya.sotres.model.constant.RejectionPolicy;
import com.nantaaditya.sotres.properties.AsyncTaskProperties;
import com.nantaaditya.sotres.properties.embedded.AsyncConfiguration;
import com.nantaaditya.sotres.properties.embedded.BulkheadPoolConfiguration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BulkheadConfiguration")
class BulkheadConfigurationTest {

  private final BulkheadConfiguration configuration = new BulkheadConfiguration();

  @Test
  @DisplayName("explicit permits are used as-is, ignoring any matching async config")
  void resolvePermits_explicitPermitsSet_returnsExplicitValue() {
    BulkheadPoolConfiguration bulkheadConfig = new BulkheadPoolConfiguration(100, 0, false);
    AsyncTaskProperties asyncProperties = new AsyncTaskProperties(Map.of(
        "isoTransaction", asyncConfig(90, 10)));

    int permits = configuration.resolvePermits("isoTransaction", bulkheadConfig, asyncProperties);

    assertThat(permits).isEqualTo(100);
  }

  @Test
  @DisplayName("null permits derive from the matching async executor's pool + queue + headroom")
  void resolvePermits_permitsNull_derivesFromMatchingAsyncConfig() {
    BulkheadPoolConfiguration bulkheadConfig = new BulkheadPoolConfiguration(null, 10, false);
    AsyncTaskProperties asyncProperties = new AsyncTaskProperties(Map.of(
        "isoTransaction", asyncConfig(90, 10)));

    int permits = configuration.resolvePermits("isoTransaction", bulkheadConfig, asyncProperties);

    assertThat(permits).isEqualTo(90 + 10 + 10);
  }

  @Test
  @DisplayName("null permits with no matching async config throws — cannot silently pick a number")
  void resolvePermits_permitsNullAndNoMatchingAsyncConfig_throws() {
    BulkheadPoolConfiguration bulkheadConfig = new BulkheadPoolConfiguration(null, 10, false);
    AsyncTaskProperties asyncProperties = new AsyncTaskProperties(Map.of());

    assertThatThrownBy(() ->
        configuration.resolvePermits("isoTransaction", bulkheadConfig, asyncProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("isoTransaction");
  }

  private AsyncConfiguration asyncConfig(int maxPoolSize, int queueCapacity) {
    return new AsyncConfiguration(5, maxPoolSize, queueCapacity, 30, "test-", false,
        RejectionPolicy.ABORT);
  }
}
