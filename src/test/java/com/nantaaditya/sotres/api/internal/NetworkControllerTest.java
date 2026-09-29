package com.nantaaditya.sotres.api.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nantaaditya.sotres.helper.ContextHelper;
import com.nantaaditya.sotres.helper.ObservationWrapper;
import com.nantaaditya.sotres.helper.ResponseHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.service.NetworkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("NetworkController")
class NetworkControllerTest {

  private NetworkService networkService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    networkService = mock(NetworkService.class);
    ResponseHelper responseHelper =
        new ResponseHelper(mock(TracerHelper.class), mock(ContextHelper.class));

    NetworkController controller = new NetworkController(networkService);
    ReflectionTestUtils.setField(controller, "responseHelper", responseHelper);
    ReflectionTestUtils.setField(controller, "observationWrapper", mock(ObservationWrapper.class));

    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  @DisplayName("GET /sign-on returns 200 and triggers a sign-on message")
  void sendSignOn_returnsSuccessAndTriggersSignOn() throws Exception {
    mockMvc.perform(get("/internal-api/network/sign-on"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.response.code").value("000"))
        .andExpect(jsonPath("$.data").value(true));

    verify(networkService).sendSignOn();
  }

  @Test
  @DisplayName("GET /sign-off returns 200 and triggers a sign-off message")
  void sendSignOff_returnsSuccessAndTriggersSignOff() throws Exception {
    mockMvc.perform(get("/internal-api/network/sign-off"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.response.code").value("000"))
        .andExpect(jsonPath("$.data").value(true));

    verify(networkService).sendSignOff();
  }

  @Test
  @DisplayName("GET /echo returns 200 with the echo result from the service")
  void sendEcho_returnsServiceResult() throws Exception {
    when(networkService.sendEcho()).thenReturn(true);

    mockMvc.perform(get("/internal-api/network/echo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.response.code").value("000"))
        .andExpect(jsonPath("$.data").value(true));
  }

  @Test
  @DisplayName("GET /echo returns 200 with false when the client is not connected/signed on")
  void sendEcho_returnsFalseWhenNotConnected() throws Exception {
    when(networkService.sendEcho()).thenReturn(false);

    mockMvc.perform(get("/internal-api/network/echo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(false));
  }
}
