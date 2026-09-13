package com.nantaaditya.sotres.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.entity.DeadLetterProcess;
import com.nantaaditya.sotres.model.constant.RetryStatus;
import com.nantaaditya.sotres.repository.DeadLetterProcessRepository;
import com.nantaaditya.sotres.service.AbstractRetryProcessorService.DeadLetterContext;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AbstractRetryProcessorService")
class AbstractRetryProcessorServiceTest {

  private DeadLetterProcessRepository repository;
  private TestRetryProcessorService service;

  @BeforeEach
  void setUp() {
    repository = mock(DeadLetterProcessRepository.class);
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    service = new TestRetryProcessorService(repository, new ObjectMapper());
  }

  private DeadLetterProcess deadLetter(int retryCount, int maxRetry) {
    DeadLetterProcess deadLetter = new DeadLetterProcess();
    deadLetter.setId(1L);
    deadLetter.setRetryCount(retryCount);
    deadLetter.setMaxRetry(maxRetry);
    deadLetter.setRetryHistories("[]".getBytes(StandardCharsets.UTF_8));
    return deadLetter;
  }

  @Nested
  @DisplayName("update(DeadLetterProcess, DeadLetterContext)")
  class Update {

    @Test
    @DisplayName("success: calls onSuccess, sets status SUCCESS, increments successCounter")
    void success_setsSuccessStatusAndIncrementsCounter() {
      DeadLetterProcess deadLetter = deadLetter(0, 3);

      service.update(deadLetter, new DeadLetterContext(true, "ok", null));

      assertThat(deadLetter.getStatus()).isEqualTo(RetryStatus.SUCCESS.name());
      assertThat(service.getSuccessCounter().get()).isEqualTo(1);
      assertThat(service.onSuccessCalled).isTrue();
      assertThat(service.onErrorCalled).isFalse();
    }

    @Test
    @DisplayName("failure, retries remaining: calls onError, sets status FAILED, increments failedCounter")
    void failureWithRetriesRemaining_setsFailedStatus() {
      DeadLetterProcess deadLetter = deadLetter(0, 3);

      service.update(deadLetter, new DeadLetterContext(false, null, new RuntimeException("boom")));

      assertThat(deadLetter.getStatus()).isEqualTo(RetryStatus.FAILED.name());
      assertThat(service.getFailedCounter().get()).isEqualTo(1);
      assertThat(service.onErrorCalled).isTrue();
    }

    @Test
    @DisplayName("failure, retry budget exhausted: sets status EXHAUSTED")
    void failureAtMaxRetry_setsExhaustedStatus() {
      DeadLetterProcess deadLetter = deadLetter(2, 3);

      service.update(deadLetter, new DeadLetterContext(false, null, new RuntimeException("boom")));

      assertThat(deadLetter.getStatus()).isEqualTo(RetryStatus.EXHAUSTED.name());
    }

    @Test
    @DisplayName("throwable present: lastError is set to the throwable's message")
    void throwablePresent_setsLastError() {
      DeadLetterProcess deadLetter = deadLetter(0, 3);

      service.update(deadLetter, new DeadLetterContext(false, null, new RuntimeException("boom")));

      assertThat(deadLetter.getLastError()).isEqualTo("boom");
    }

    @Test
    @DisplayName("throwable absent: lastError is left untouched")
    void throwableAbsent_leavesLastErrorUntouched() {
      DeadLetterProcess deadLetter = deadLetter(0, 3);

      service.update(deadLetter, new DeadLetterContext(true, "ok", null));

      assertThat(deadLetter.getLastError()).isNull();
    }

    @Test
    @DisplayName("always increments retryCount, stamps updatedBy, and saves via the repository")
    void alwaysIncrementsRetryCountAndSaves() {
      DeadLetterProcess deadLetter = deadLetter(0, 3);

      DeadLetterProcess result = service.update(deadLetter, new DeadLetterContext(true, "ok", null));

      assertThat(deadLetter.getRetryCount()).isEqualTo(1);
      assertThat(deadLetter.getUpdatedBy()).isEqualTo("internal-retry-process");
      assertThat(result).isSameAs(deadLetter);
      verify(repository).save(deadLetter);
    }

    @Test
    @DisplayName("malformed retryHistories JSON: logs and leaves the process otherwise updated")
    void malformedRetryHistories_doesNotThrow() {
      DeadLetterProcess deadLetter = deadLetter(0, 3);
      deadLetter.setRetryHistories("not json".getBytes(StandardCharsets.UTF_8));

      service.update(deadLetter, new DeadLetterContext(true, "ok", null));

      assertThat(deadLetter.getRetryCount()).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("resetCounter() zeroes success, failed, and notEligible counters")
  void resetCounter_zeroesAllCounters() {
    service.getSuccessCounter().incrementAndGet();
    service.getFailedCounter().incrementAndGet();
    service.getNotEligibleCounter().incrementAndGet();

    service.resetCounter();

    assertThat(service.getSuccessCounter().get()).isZero();
    assertThat(service.getFailedCounter().get()).isZero();
    assertThat(service.getNotEligibleCounter().get()).isZero();
  }

  private static final class TestRetryProcessorService extends AbstractRetryProcessorService {

    private boolean onSuccessCalled;
    private boolean onErrorCalled;

    TestRetryProcessorService(DeadLetterProcessRepository repository, ObjectMapper objectMapper) {
      super(repository, objectMapper);
    }

    @Override
    public String getProcessType() {
      return "test";
    }

    @Override
    public String getProcessName() {
      return "test";
    }

    @Override
    public boolean isEligibleToBeRetried(DeadLetterProcess deadLetterProcess) {
      return true;
    }

    @Override
    public DeadLetterContext execute(DeadLetterProcess deadLetterProcess) {
      return new DeadLetterContext(true, "ok", null);
    }

    @Override
    public void onSuccess(DeadLetterProcess deadLetterProcess, String response) {
      onSuccessCalled = true;
    }

    @Override
    public void onError(DeadLetterProcess deadLetterProcess, Throwable throwable) {
      onErrorCalled = true;
    }
  }
}
