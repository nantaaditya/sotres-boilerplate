package com.nantaaditya.sotres.interceptor;

import com.nantaaditya.sotres.helper.ContextHelper;
import com.nantaaditya.sotres.helper.DateTimeHelper;
import com.nantaaditya.sotres.helper.ObservationHelper;
import com.nantaaditya.sotres.helper.ObservationWrapper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.constant.ObservationConstant;
import com.nantaaditya.sotres.model.dto.CacheBodyRequest;
import com.nantaaditya.sotres.model.dto.ContextDTO;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Log4j2
@Component("appHeaderFilter")
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class HeaderFilter extends OncePerRequestFilter {

  /** Request attribute holding the {@link ContextDTO} for the {@link EventLogInterceptor}. */
  public static final String CONTEXT_ATTRIBUTE = "com.nantaaditya.sotres.context";

  private final String contextPath;
  private final ContextHelper contextHelper;
  private final TracerHelper tracerHelper;
  private final ObservationRegistry observationRegistry;
  private final ObservationWrapper observationWrapper;

  public HeaderFilter(
      @Value("${server.servlet.context-path}") String contextPath,
      ContextHelper contextHelper,
      TracerHelper tracerHelper,
      ObservationRegistry observationRegistry,
      ObservationWrapper observationWrapper) {
    this.contextPath = contextPath;
    this.contextHelper = contextHelper;
    this.tracerHelper = tracerHelper;
    this.observationRegistry = observationRegistry;
    this.observationWrapper = observationWrapper;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    HttpServletRequest cachedRequest = new CacheBodyRequest(request);

    ContextDTO contextDTO = new ContextDTO();
    contextDTO.decorateContext(cachedRequest, contextPath);

    contextHelper.put(contextDTO);
    cachedRequest.setAttribute(CONTEXT_ATTRIBUTE, contextDTO);
    decorateResponseHeaders(cachedRequest, response);
    decorateBaggage(contextDTO);

    Observation observation = Observation.start(
        ObservationConstant.API_PUBLIC.getName(),
        () -> ObservationHelper.createApiContext(contextDTO),
        observationRegistry
    );
    observationWrapper.setObservation(cachedRequest, observation);

    Map<String, String> contextMap = MDC.getCopyOfContextMap();

    try (Observation.Scope scope = observation.openScope()) {
      if (contextMap != null) {
        MDC.setContextMap(contextMap);
      }
      filterChain.doFilter(cachedRequest, response);
    } catch (Exception exception) {
      log.error(AppLogMessage.message("#Observation - error").error(exception));
      ObservationHelper.observeResponse(observation, null, exception);
      throw exception;
    } finally {
      if (!observation.isNoop()) {
        observation.stop();
      }
      observationWrapper.clear(cachedRequest);
    }
  }

  private void decorateBaggage(ContextDTO contextDTO) {
    tracerHelper.setBaggage(HeaderConstant.REQUEST_ID.getHeader(), contextDTO.getRequestId());
    tracerHelper.setBaggage(HeaderConstant.CLIENT_ID.getHeader(), contextDTO.getClientId());
  }

  private void decorateResponseHeaders(HttpServletRequest request, HttpServletResponse response) {
    copyHeader(request, response, HeaderConstant.CLIENT_ID.getHeader());
    copyHeader(request, response, HeaderConstant.REQUEST_ID.getHeader());
    copyHeader(request, response, HeaderConstant.REQUEST_TIME.getHeader());
    response.setHeader(
        HeaderConstant.RECEIVED_TIME.getHeader(),
        DateTimeHelper.getDateInFormat(ZonedDateTime.now(), DateTimeHelper.ISO_8601_GMT7_FORMAT)
    );
  }

  private void copyHeader(HttpServletRequest request, HttpServletResponse response, String header) {
    String value = request.getHeader(header);
    if (value != null) {
      response.setHeader(header, value);
    }
  }
}
