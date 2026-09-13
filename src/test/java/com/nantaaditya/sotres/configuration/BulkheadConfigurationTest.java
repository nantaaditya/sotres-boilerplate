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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  @DisplayName("explicit permits <= 0 throws — every tryAcquire would time out silently")
  void resolvePermits_explicitPermitsNotPositive_throws(int explicitPermits) {
    BulkheadPoolConfiguration bulkheadConfig = new BulkheadPoolConfiguration(explicitPermits, 0, false);
    AsyncTaskProperties asyncProperties = new AsyncTaskProperties(Map.of());

    assertThatThrownBy(() ->
        configuration.resolvePermits("isoTransaction", bulkheadConfig, asyncProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("isoTransaction");
  }

  @Test
  @DisplayName("negative headroom throws, even when deriving permits from the matching async config")
  void resolvePermits_negativeHeadroom_throws() {
    BulkheadPoolConfiguration bulkheadConfig = new BulkheadPoolConfiguration(null, -1, false);
    AsyncTaskProperties asyncProperties = new AsyncTaskProperties(Map.of(
        "isoTransaction", asyncConfig(90, 10)));

    assertThatThrownBy(() ->
        configuration.resolvePermits("isoTransaction", bulkheadConfig, asyncProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("isoTransaction");
  }

  @Test
  @DisplayName("negative headroom throws even when permits is set explicitly (unused, but still validated)")
  void resolvePermits_negativeHeadroomWithExplicitPermits_throws() {
    BulkheadPoolConfiguration bulkheadConfig = new BulkheadPoolConfiguration(100, -1, false);
    AsyncTaskProperties asyncProperties = new AsyncTaskProperties(Map.of());

    assertThatThrownBy(() ->
        configuration.resolvePermits("isoTransaction", bulkheadConfig, asyncProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("isoTransaction");
  }

  @Test
  @DisplayName("a derived permit count of 0 throws — pool + queue + headroom summing to zero is a misconfiguration")
  void resolvePermits_derivedPermitsZero_throws() {
    BulkheadPoolConfiguration bulkheadConfig = new BulkheadPoolConfiguration(null, 0, false);
    AsyncTaskProperties asyncProperties = new AsyncTaskProperties(Map.of(
        "isoTransaction", asyncConfig(0, 0)));

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
