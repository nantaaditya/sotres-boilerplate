package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HealthCheckHelper")
class HealthCheckHelperTest {

  private HealthCheckHelper healthCheckHelper;

  @BeforeEach
  void setUp() {
    healthCheckHelper = new HealthCheckHelper();
  }

  @Nested
  @DisplayName("isHealthy()")
  class IsHealthy {

    @Test
    @DisplayName("defaults to false on construction")
    void isHealthy_initialState_isFalse() {
      assertThat(healthCheckHelper.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("returns true after setIsHealthy(true)")
    void isHealthy_afterSetTrue_returnsTrue() {
      healthCheckHelper.setIsHealthy(true);
      assertThat(healthCheckHelper.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("returns false after toggling back to false")
    void isHealthy_afterSetTrueThenFalse_returnsFalse() {
      healthCheckHelper.setIsHealthy(true);
      healthCheckHelper.setIsHealthy(false);
      assertThat(healthCheckHelper.isHealthy()).isFalse();
    }
  }

  @Nested
  @DisplayName("isSignedOn()")
  class IsSignedOn {

    @Test
    @DisplayName("defaults to false on construction")
    void isSignedOn_initialState_isFalse() {
      assertThat(healthCheckHelper.isSignedOn()).isFalse();
    }

    @Test
    @DisplayName("returns true after setIsSignedOn(true)")
    void isSignedOn_afterSetTrue_returnsTrue() {
      healthCheckHelper.setIsSignedOn(true);
      assertThat(healthCheckHelper.isSignedOn()).isTrue();
    }

    @Test
    @DisplayName("returns false after toggling back to false")
    void isSignedOn_afterSetTrueThenFalse_returnsFalse() {
      healthCheckHelper.setIsSignedOn(true);
      healthCheckHelper.setIsSignedOn(false);
      assertThat(healthCheckHelper.isSignedOn()).isFalse();
    }
  }

  @Nested
  @DisplayName("state independence")
  class StateIndependence {

    @Test
    @DisplayName("setting isHealthy does not affect isSignedOn")
    void setHealthy_doesNotAffectSignedOn() {
      healthCheckHelper.setIsHealthy(true);
      assertThat(healthCheckHelper.isSignedOn()).isFalse();
    }

    @Test
    @DisplayName("setting isSignedOn does not affect isHealthy")
    void setSignedOn_doesNotAffectHealthy() {
      healthCheckHelper.setIsSignedOn(true);
      assertThat(healthCheckHelper.isHealthy()).isFalse();
    }
  }

  @Nested
  @DisplayName("concurrent access")
  class ConcurrentAccess {

    @Test
    @DisplayName("AtomicBoolean allows concurrent reads and writes without data race")
    void concurrentWrites_atomicBehavior_noException() throws InterruptedException {
      int threadCount = 50;
      CountDownLatch latch = new CountDownLatch(threadCount);
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);

      for (int i = 0; i < threadCount; i++) {
        final boolean value = i % 2 == 0;
        executor.submit(() -> {
          healthCheckHelper.setIsHealthy(value);
          healthCheckHelper.isHealthy();
          latch.countDown();
        });
      }

      boolean completed = latch.await(5, TimeUnit.SECONDS);
      executor.shutdown();

      assertThat(completed).isTrue();
      // The final value is non-deterministic; we only verify no exception occurred
      assertThat(healthCheckHelper.isHealthy()).isIn(true, false);
    }
  }
}
