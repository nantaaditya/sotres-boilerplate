package com.nantaaditya.sotres.api.internal;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nantaaditya.sotres.api.ApiExceptionHandler;
import com.nantaaditya.sotres.helper.ContextHelper;
import com.nantaaditya.sotres.helper.ResponseHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.service.internal.DeadLetterProcessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("DeadLetterProcessController — exception handling")
class DeadLetterProcessControllerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ResponseHelper responseHelper =
        new ResponseHelper(mock(TracerHelper.class), mock(ContextHelper.class));
    ApiExceptionHandler exceptionHandler =
        new ApiExceptionHandler(new ObjectMapper(), responseHelper);
    DeadLetterProcessController controller =
        new DeadLetterProcessController(mock(DeadLetterProcessService.class));

    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(exceptionHandler)
        .build();
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
