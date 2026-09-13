package com.nantaaditya.sotres.api.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.api.ApiExceptionHandler;
import com.nantaaditya.sotres.helper.ContextHelper;
import com.nantaaditya.sotres.helper.ObservationWrapper;
import com.nantaaditya.sotres.helper.ResponseHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.request.RetryDeadLetterProcessRequest;
import com.nantaaditya.sotres.service.internal.DeadLetterProcessService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("DeadLetterProcessController")
class DeadLetterProcessControllerTest {

  private DeadLetterProcessService deadLetterProcessService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    deadLetterProcessService = mock(DeadLetterProcessService.class);
    ResponseHelper responseHelper =
        new ResponseHelper(mock(TracerHelper.class), mock(ContextHelper.class));
    ApiExceptionHandler exceptionHandler =
        new ApiExceptionHandler(new ObjectMapper(), responseHelper,
            mock(ObservationWrapper.class), mock(HttpServletRequest.class));
    DeadLetterProcessController controller =
        new DeadLetterProcessController(deadLetterProcessService);
    ReflectionTestUtils.setField(controller, "responseHelper", responseHelper);
    ReflectionTestUtils.setField(controller, "observationWrapper", mock(ObservationWrapper.class));

    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(exceptionHandler)
        .build();
  }

  @Test
  @DisplayName("DELETE / returns 200 and removes rows older than the given days")
  void remove_returnsSuccessAndRemovesRows() throws Exception {
    mockMvc.perform(delete("/internal-api/dead_letter_process").param("days", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.response.code").value("000"))
        .andExpect(jsonPath("$.data").value(true));

    verify(deadLetterProcessService).remove(7);
  }

  @Test
  @DisplayName("DELETE / defaults days to 30 when not provided")
  void remove_defaultsDaysTo30() throws Exception {
    mockMvc.perform(delete("/internal-api/dead_letter_process"))
        .andExpect(status().isOk());

    verify(deadLetterProcessService).remove(30);
  }

  @Test
  @DisplayName("POST /_retry with a valid body returns 200 and triggers retry")
  void retry_validBody_returnsSuccessAndTriggersRetry() throws Exception {
    RetryDeadLetterProcessRequest request =
        new RetryDeadLetterProcessRequest("client", "transaction", 10);

    mockMvc.perform(post("/internal-api/dead_letter_process/_retry")
            .contentType("application/json")
            .content(new ObjectMapper().writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.response.code").value("000"))
        .andExpect(jsonPath("$.data").value(true));

    verify(deadLetterProcessService).retry(request);
  }

  @Test
  @DisplayName("invalid _retry body -> 400 / code 900 with per-field violations")
  void retry_invalidBody_returnsBadRequestEnvelope() throws Exception {
    mockMvc.perform(post("/internal-api/dead_letter_process/_retry")
            .contentType("application/json")
            .content("{\"processType\":\"\",\"processName\":\"\",\"size\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.response.code").value("900"))
        .andExpect(jsonPath("$.error.violations.processType").exists())
        .andExpect(jsonPath("$.error.violations.size").exists());
  }

  @Test
  @DisplayName("malformed JSON body -> 400 / code 998")
  void retry_malformedJson_returnsBadRequest() throws Exception {
    mockMvc.perform(post("/internal-api/dead_letter_process/_retry")
            .contentType("application/json")
            .content("{ not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.response.code").value("998"));
  }
}
