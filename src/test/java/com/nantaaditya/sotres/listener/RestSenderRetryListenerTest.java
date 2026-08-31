package com.nantaaditya.sotres.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.entity.DeadLetterProcess;
import com.nantaaditya.sotres.model.constant.RetryConstant;
import com.nantaaditya.sotres.model.constant.RetryStatus;
import com.nantaaditya.sotres.repository.DeadLetterProcessRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.context.RetryContextSupport;
import org.springframework.web.client.ResourceAccessException;

@DisplayName("RestSenderRetryListener")
@ExtendWith(MockitoExtension.class)
class RestSenderRetryListenerTest {

  private static final int MAX_ATTEMPTS = 3;

  @Mock
  private DeadLetterProcessRepository deadLetterProcessRepository;

  private RestSenderRetryListener listener;

  @BeforeEach
  void setUp() {
    listener = new RestSenderRetryListener("transaction", MAX_ATTEMPTS, true,
        deadLetterProcessRepository, new ObjectMapper());
  }

  private RetryContextSupport exhaustedContext(Throwable last) {
    RetryContextSupport context = new RetryContextSupport(null);
    context.setAttribute(RetryConstant.CLIENT_NAME.key(), "transaction");
    context.setAttribute(RetryConstant.PROCESS_NAME.key(), "transaction");
    context.setAttribute(RetryConstant.METHOD.key(), "POST");
    context.setAttribute(RetryConstant.PATH.key(), "/api/payment");
    context.setAttribute(RetryConstant.REQUEST_ID.key(), "RRN-1");
    HttpHeaders headers = new HttpHeaders();
    headers.add("x-request-id", "RRN-1");
    context.setAttribute(RetryConstant.HEADERS.key(), headers);
    context.setAttribute(RetryConstant.REQUEST.key(), Map.of("amount", 100));
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      context.registerThrowable(last);
    }
    return context;
  }

  @Test
  @DisplayName("persists a NEW dead-letter row when the retry budget is exhausted")
  void close_exhausted_persistsDeadLetter() {
    ResourceAccessException last = new ResourceAccessException("downstream down");

    listener.close(exhaustedContext(last), null, last);

    ArgumentCaptor<DeadLetterProcess> captor = ArgumentCaptor.forClass(DeadLetterProcess.class);
    verify(deadLetterProcessRepository).save(captor.capture());
    DeadLetterProcess saved = captor.getValue();
    assertThat(saved.getProcessType()).isEqualTo("client");
    assertThat(saved.getProcessName()).isEqualTo("transaction");
    assertThat(saved.getClientName()).isEqualTo("transaction");
    assertThat(saved.getMethod()).isEqualTo("POST");
    assertThat(saved.getPath()).isEqualTo("/api/payment");
    assertThat(saved.getIdempotencyKey()).isEqualTo("RRN-1");
    assertThat(saved.getStatus()).isEqualTo(RetryStatus.NEW.name());
    assertThat(saved.getRetryCount()).isZero();
    assertThat(saved.getMaxRetry()).isEqualTo(MAX_ATTEMPTS);
    assertThat(saved.getLastError()).isEqualTo("downstream down");
    assertThat(saved.getHeaders()).contains("x-request-id");
    assertThat(saved.getPayload()).isNotEmpty();
    assertThat(saved.getRetryHistories()).isNotEmpty();
  }

  @Test
  @DisplayName("does nothing when the call ultimately succeeded")
  void close_success_noPersist() {
    listener.close(exhaustedContext(new ResourceAccessException("x")), null, null);
    verifyNoInteractions(deadLetterProcessRepository);
  }

  @Test
  @DisplayName("does not persist when the loop stopped before exhausting the budget")
  void close_notExhausted_noPersist() {
    ResourceAccessException last = new ResourceAccessException("one-shot");
    RetryContextSupport context = new RetryContextSupport(null);
    context.registerThrowable(last); // retryCount == 1 < MAX_ATTEMPTS

    listener.close(context, null, last);

    verify(deadLetterProcessRepository, never()).save(any());
  }

  @Test
  @DisplayName("swallows a repository failure so the caller still sees the original exception")
  void close_repositoryThrows_isSwallowed() {
    ResourceAccessException last = new ResourceAccessException("downstream down");
    doThrow(new RuntimeException("db down")).when(deadLetterProcessRepository).save(any());

    assertThatCode(() -> listener.close(exhaustedContext(last), null, last)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("deadLetterEnabled=false: exhaustion is logged, not persisted")
  void close_deadLetterDisabled_noPersist() {
    RestSenderRetryListener logOnly = new RestSenderRetryListener("transaction", MAX_ATTEMPTS, false,
        deadLetterProcessRepository, new ObjectMapper());
    ResourceAccessException last = new ResourceAccessException("downstream down");

    logOnly.close(exhaustedContext(last), null, last);

    verifyNoInteractions(deadLetterProcessRepository);
  }

  @Test
  @DisplayName("maxAttempts=1: a single failure is logged, not persisted")
  void close_maxAttemptsOne_noPersist() {
    RestSenderRetryListener singleShot = new RestSenderRetryListener("transaction", 1, true,
        deadLetterProcessRepository, new ObjectMapper());
    ResourceAccessException last = new ResourceAccessException("downstream down");
    RetryContextSupport context = new RetryContextSupport(null);
    context.registerThrowable(last); // retryCount == 1 == maxAttempts

    singleShot.close(context, null, last);

    verifyNoInteractions(deadLetterProcessRepository);
  }
}
