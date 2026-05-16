package com.nantaaditya.sotres.service.impl;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.entity.DeadLetterProcess;
import com.nantaaditya.sotres.helper.RetryProcessorHelper;
import com.nantaaditya.sotres.model.constant.RetryStatus;
import com.nantaaditya.sotres.model.request.RetryDeadLetterProcessRequest;
import com.nantaaditya.sotres.repository.DeadLetterProcessRepository;
import com.nantaaditya.sotres.service.AbstractRetryProcessorService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("DeadLetterProcessServiceImpl")
@ExtendWith(MockitoExtension.class)
class DeadLetterProcessServiceImplTest {

  @Mock private DeadLetterProcessRepository deadLetterProcessRepository;
  @Mock private RetryProcessorHelper retryProcessorHelper;

  private DeadLetterProcessServiceImpl service;
  private RetryDeadLetterProcessRequest request;

  @BeforeEach
  void setUp() {
    service = new DeadLetterProcessServiceImpl(deadLetterProcessRepository, retryProcessorHelper);
    request = new RetryDeadLetterProcessRequest("ORDER", "PAYMENT", 10);
  }

  private DeadLetterProcess buildDlp(int retryCount, int maxRetry) {
    DeadLetterProcess dlp = new DeadLetterProcess();
    dlp.setProcessType("ORDER");
    dlp.setProcessName("PAYMENT");
    dlp.setRetryCount(retryCount);
    dlp.setMaxRetry(maxRetry);
    dlp.setStatus(RetryStatus.NEW.name());
    dlp.setRetryHistories("[]".getBytes());
    dlp.setCreatedDate(LocalDateTime.now());
    dlp.setUpdatedDate(LocalDateTime.now());
    return dlp;
  }

  @Nested
  @DisplayName("remove")
  class Remove {

    @Test
    @DisplayName("deletes exhausted records older than the given days threshold")
    void deletesExhaustedRecordsBeforeDateThreshold() {
      when(deadLetterProcessRepository.deleteByCreatedDateBeforeAndStatus(
              any(LocalDateTime.class), eq(RetryStatus.EXHAUSTED.name())))
          .thenReturn(Mono.empty());

      StepVerifier.create(service.remove(7))
          .verifyComplete();

      verify(deadLetterProcessRepository).deleteByCreatedDateBeforeAndStatus(
          argThat(date -> date.isBefore(LocalDateTime.now())),
          eq(RetryStatus.EXHAUSTED.name())
      );
    }
  }

  @Nested
  @DisplayName("retry")
  class Retry {

    @Test
    @DisplayName("filters records that reached maxRetry and completes without executing")
    void filtersRecordsAtMaxRetry_completesEmpty() {
      DeadLetterProcess dlp = buildDlp(3, 3); // 3 >= 3 → filtered
      when(deadLetterProcessRepository.findByProcessTypeAndProcessNameAndStatusIn(
              eq("ORDER"), eq("PAYMENT"), anySet(), any()))
          .thenReturn(Flux.just(dlp));

      StepVerifier.create(service.retry(request))
          .verifyComplete();
    }
  }

  @Nested
  @DisplayName("executeRetryProcess")
  class ExecuteRetryProcess {

    @Test
    @DisplayName("returns empty mono when no processor is registered")
    void returnsEmptyMono_whenNoProcessorRegistered() {
      when(retryProcessorHelper.getProcessor("ORDER", "PAYMENT")).thenReturn(null);

      StepVerifier.create(service.executeRetryProcess(request, List.of()))
          .verifyComplete();
    }

    @Test
    @DisplayName("sets SUCCESS status and increments notEligibleCounter for ineligible records")
    void setsSuccessStatus_andIncrementsNotEligibleCounter_forIneligibleRecords() {
      DeadLetterProcess dlp = buildDlp(0, 3);
      AtomicBoolean saveCalled = new AtomicBoolean(false);
      AbstractRetryProcessorService processor = new IneligibleTestProcessor(deadLetterProcessRepository);

      when(retryProcessorHelper.getProcessor("ORDER", "PAYMENT")).thenReturn(processor);
      when(deadLetterProcessRepository.saveAll(anyList())).thenReturn(Flux.just(dlp));
      when(deadLetterProcessRepository.save(dlp)).thenAnswer(inv -> {
        saveCalled.set(true);
        return Mono.just(inv.getArgument(0));
      });

      StepVerifier.create(service.executeRetryProcess(request, List.of(dlp)))
          .verifyComplete();

      await().atMost(2, SECONDS).untilTrue(saveCalled);
      assertThat(dlp.getStatus()).isEqualTo(RetryStatus.SUCCESS.name());
      assertThat(processor.getNotEligibleCounter().get()).isEqualTo(1);
    }

    @Test
    @DisplayName("executes eligible records and saves SUCCESS status")
    void executesEligibleRecords_andSavesSuccessStatus() {
      DeadLetterProcess dlp = buildDlp(0, 3);
      AtomicBoolean saveCalled = new AtomicBoolean(false);
      AbstractRetryProcessorService processor = new EligibleTestProcessor(deadLetterProcessRepository);

      when(retryProcessorHelper.getProcessor("ORDER", "PAYMENT")).thenReturn(processor);
      when(deadLetterProcessRepository.saveAll(anyList())).thenReturn(Flux.just(dlp));
      when(deadLetterProcessRepository.save(dlp)).thenAnswer(inv -> {
        saveCalled.set(true);
        return Mono.just(inv.getArgument(0));
      });

      StepVerifier.create(service.executeRetryProcess(request, List.of(dlp)))
          .expectComplete()
          .verify();

      await().atMost(2, SECONDS).untilTrue(saveCalled);
      assertThat(dlp.getStatus()).isEqualTo(RetryStatus.SUCCESS.name());
      assertThat(processor.getSuccessCounter().get()).isEqualTo(1);
    }
  }

  private static class IneligibleTestProcessor extends AbstractRetryProcessorService {

    IneligibleTestProcessor(DeadLetterProcessRepository repo) {
      super(repo, new ObjectMapper());
    }

    @Override
    public String getProcessType() { return "ORDER"; }

    @Override
    public String getProcessName() { return "PAYMENT"; }

    @Override
    public boolean isEligibleToBeRetried(DeadLetterProcess dlp) { return false; }

    @Override
    public Mono<DeadLetterContext> execute(DeadLetterProcess dlp) {
      return Mono.error(new UnsupportedOperationException("should not be called"));
    }

    @Override
    public void onSuccess(DeadLetterProcess dlp, String response) {}

    @Override
    public void onError(DeadLetterProcess dlp, Throwable throwable) {}
  }

  private static class EligibleTestProcessor extends AbstractRetryProcessorService {

    EligibleTestProcessor(DeadLetterProcessRepository repo) {
      super(repo, new ObjectMapper());
    }

    @Override
    public String getProcessType() { return "ORDER"; }

    @Override
    public String getProcessName() { return "PAYMENT"; }

    @Override
    public boolean isEligibleToBeRetried(DeadLetterProcess dlp) { return true; }

    @Override
    public Mono<DeadLetterContext> execute(DeadLetterProcess dlp) {
      return Mono.just(new DeadLetterContext(Boolean.TRUE, "ok", null));
    }

    @Override
    public void onSuccess(DeadLetterProcess dlp, String response) {}

    @Override
    public void onError(DeadLetterProcess dlp, Throwable throwable) {}
  }
}
