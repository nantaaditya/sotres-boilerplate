package com.nantaaditya.sotres.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.observation.Observation;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ObservationWrapper")
class ObservationWrapperTest {

  private static final String REQUEST_ATTR = "com.nantaaditya.sotres.observation";

  private final ObservationWrapper wrapper = new ObservationWrapper();

  @Nested
  @DisplayName("setObservation")
  class SetObservation {

    @Test
    @DisplayName("non-null request: stores the observation as a request attribute")
    void nonNullRequest_setsAttribute() {
      HttpServletRequest request = mock(HttpServletRequest.class);
      Observation observation = mock(Observation.class);

      wrapper.setObservation(request, observation);

      verify(request).setAttribute(REQUEST_ATTR, observation);
    }

    @Test
    @DisplayName("null request: does nothing, does not throw")
    void nullRequest_doesNothing() {
      wrapper.setObservation(null, mock(Observation.class));
    }
  }

  @Nested
  @DisplayName("getObservation")
  class GetObservation {

    @Test
    @DisplayName("null request: returns null")
    void nullRequest_returnsNull() {
      assertThat(wrapper.getObservation(null)).isNull();
    }

    @Test
    @DisplayName("attribute holds an Observation: returns it")
    void attributeIsObservation_returnsIt() {
      HttpServletRequest request = mock(HttpServletRequest.class);
      Observation observation = mock(Observation.class);
      when(request.getAttribute(REQUEST_ATTR)).thenReturn(observation);

      assertThat(wrapper.getObservation(request)).isSameAs(observation);
    }

    @Test
    @DisplayName("attribute is absent or not an Observation: returns null")
    void attributeMissingOrWrongType_returnsNull() {
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(request.getAttribute(REQUEST_ATTR)).thenReturn("not-an-observation");

      assertThat(wrapper.getObservation(request)).isNull();
    }
  }

  @Nested
  @DisplayName("clear")
  class Clear {

    @Test
    @DisplayName("non-null request: removes the observation attribute")
    void nonNullRequest_removesAttribute() {
      HttpServletRequest request = mock(HttpServletRequest.class);

      wrapper.clear(request);

      verify(request).removeAttribute(REQUEST_ATTR);
    }

    @Test
    @DisplayName("null request: does nothing, does not throw")
    void nullRequest_doesNothing() {
      wrapper.clear(null);
    }
  }
}
