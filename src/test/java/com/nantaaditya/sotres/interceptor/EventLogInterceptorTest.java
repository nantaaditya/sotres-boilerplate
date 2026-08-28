package com.nantaaditya.sotres.interceptor;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.nantaaditya.sotres.entity.EventLog;
import com.nantaaditya.sotres.helper.ContextHelper;
import com.nantaaditya.sotres.model.dto.ContextDTO;
import com.nantaaditya.sotres.properties.LogProperties;
import com.nantaaditya.sotres.repository.EventLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("EventLogInterceptor")
@ExtendWith(MockitoExtension.class)
class EventLogInterceptorTest {

  @Mock
  private EventLogRepository eventLogRepository;
  @Mock
  private LogProperties logProperties;
  @Mock
  private ContextHelper contextHelper;

  private EventLogInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new EventLogInterceptor(eventLogRepository, logProperties, new Gson(), contextHelper);
  }

  private MockHttpServletRequest requestWithContext(ContextDTO context) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sotres/api/payment");
    request.setContent("{\"cardNo\":\"1234\"}".getBytes());
    if (context != null) {
      request.setAttribute(HeaderFilter.CONTEXT_ATTRIBUTE, context);
    }
    return request;
  }

  private ContextDTO context(String path) {
    ContextDTO context = new ContextDTO();
    context.setRequestId("req-001");
    context.setClientId("client-001");
    context.setMethod("POST");
    context.setPath(path);
    context.setResponseCode("00");
    context.setResponseDescription("SUCCESS");
    return context;
  }

  @Test
  @DisplayName("persists an EventLog and cleans up the context")
  void afterCompletion_savesEventLog() {
    MockHttpServletRequest request = requestWithContext(context("/api/payment"));
    when(logProperties.isIgnoredPath("/api/payment")).thenReturn(false);

    interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

    verify(eventLogRepository).save(any(EventLog.class));
    verify(contextHelper).cleanUp("req-001");
  }

  @Test
  @DisplayName("no context attribute -> skips entirely, no save")
  void afterCompletion_noContext_skips() {
    MockHttpServletRequest request = requestWithContext(null);

    interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

    verify(eventLogRepository, never()).save(any());
    verify(contextHelper, never()).cleanUp(anyString());
  }

  @Test
  @DisplayName("ignored path -> no save but still cleans up")
  void afterCompletion_ignoredPath_skipsSaveButCleansUp() {
    MockHttpServletRequest request = requestWithContext(context("/actuator/health"));
    when(logProperties.isIgnoredPath("/actuator/health")).thenReturn(true);

    interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

    verify(eventLogRepository, never()).save(any());
    verify(contextHelper).cleanUp("req-001");
  }

  @Test
  @DisplayName("repository failure is swallowed and context is still cleaned up")
  void afterCompletion_saveThrows_swallowed() {
    MockHttpServletRequest request = requestWithContext(context("/api/payment"));
    when(logProperties.isIgnoredPath("/api/payment")).thenReturn(false);
    doThrow(new RuntimeException("db down")).when(eventLogRepository).save(any(EventLog.class));

    assertThatCode(() ->
        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null))
        .doesNotThrowAnyException();

    verify(contextHelper).cleanUp("req-001");
  }
}
