package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.helper.MaskingHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.model.logger.JsonLogHttpRequest;
import com.nantaaditya.sotres.model.logger.JsonLogHttpResponse;
import com.nantaaditya.sotres.properties.LogProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.actuate.web.exchanges.HttpExchange;
import org.springframework.boot.actuate.web.exchanges.HttpExchange.Request;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Log4j2
@Component
@RequiredArgsConstructor
public class TraceLogConfiguration implements HttpExchangeRepository {

  private final LogProperties logProperties;
  private final TracerHelper tracerHelper;
  private final AtomicReference<HttpExchange> httpTrace = new AtomicReference<>();

  @Override
  public List<HttpExchange> findAll() {
    return Collections.singletonList(httpTrace.get());
  }

  @Override
  public void add(HttpExchange trace) {
    HttpExchange.Request request = trace.getRequest();
    HttpExchange.Response response = trace.getResponse();

    if (!logProperties.enableTraceLog()) {
      return;
    }

    if (logProperties.isIgnoredPath(request.getUri().getPath())) {
      return;
    }

    // request
    if (request.getHeaders().containsKey(HeaderConstant.REQUEST_ID.getHeader())) {
      tracerHelper.setBaggage(
          HeaderConstant.REQUEST_ID.getHeader(),
          request.getHeaders().get(HeaderConstant.REQUEST_ID.getHeader()).stream().findFirst().orElse(null));
    }
    MultiValueMap<String, String> requestHeaders = new LinkedMultiValueMap<>();
    for (Entry<String, List<String>> headers : request.getHeaders().entrySet()) {
      if (isInternalHeader(headers.getKey())) {
        maskHeader(requestHeaders, headers);
      }
    }
    JsonLogHttpRequest httpRequest = new JsonLogHttpRequest(
        request.getMethod(),
        getPath(request),
        requestHeaders,
        null
    );

    // response
    MultiValueMap<String, String> responseHeaders = new LinkedMultiValueMap<>();
    for (Entry<String, List<String>> headers : request.getHeaders().entrySet()) {
      if (isInternalHeader(headers.getKey())) {
        maskHeader(responseHeaders, headers);
      }
    }
    JsonLogHttpResponse httpResponse = new JsonLogHttpResponse(
        request.getMethod(),
        getPath(request),
        String.valueOf(response.getStatus()),
        String.format("%s ms", trace.getTimeTaken().toMillis()),
        responseHeaders,
        null
    );

    log.info(AppLogMessage.message("#Trace").httpRequest(httpRequest).httpResponse(httpResponse));
  }

  private static String getPath(Request request) {
    return (request.getUri().getRawQuery() != null) ?
        request.getUri().getPath().concat("?").concat(request.getUri().getRawQuery())
        : request.getUri().getPath();
  }

  private boolean isInternalHeader(String headerKey) {
    return Stream.of(HeaderConstant.values())
        .anyMatch(h -> h.getHeader().equals(headerKey));
  }

  private void maskHeader(MultiValueMap<String, String> maskedHeaders,
      Entry<String, List<String>> headers) {
    maskedHeaders.put(headers.getKey(), logProperties.isSensitiveFields(headers.getKey()) ?
        headers.getValue().stream().map(MaskingHelper::masking).toList()  : headers.getValue());
  }
}
