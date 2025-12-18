package com.nantaaditya.sotres.interceptor;

import com.nantaaditya.sotres.helper.ContextHelper;
import com.nantaaditya.sotres.helper.DateTimeHelper;
import com.nantaaditya.sotres.helper.EventLogHelper;
import com.nantaaditya.sotres.helper.ObservationHelper;
import com.nantaaditya.sotres.helper.ObservationWrapper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.model.constant.ObservationConstant;
import com.nantaaditya.sotres.model.dto.ContextDTO;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationRegistry;
import java.time.ZonedDateTime;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log4j2
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class AppFilter implements WebFilter {

  @Value("${spring.webflux.base-path}")
  private String contextPath;

  @Autowired
  private EventLogHelper eventLogHelper;
  @Autowired
  private ContextHelper contextHelper;
  @Autowired
  private TracerHelper tracerHelper;
  @Autowired
  private ObservationWrapper observationWrapper;
  @Autowired
  private ObservationRegistry observationRegistry;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    ServerHttpResponse response = exchange.getResponse();
    ContextDTO contextDTO = decorateContext(request);

    contextHelper.put(contextDTO);
    decorateResponseHeaders(request, response);
    decorateBaggage(contextDTO);

    Observation.Context observationContext = ObservationHelper.createApiContext(contextDTO);
    Observation observation = Observation.start(
        ObservationConstant.API_PUBLIC.getName(),
        () -> observationContext,
        observationRegistry
    );
    observationWrapper.setObservation(observation);

    return Mono.usingWhen(
      Mono.fromCallable(() -> observation.openScope()),

      scope -> DataBufferUtils.join(request.getBody())
          .cast(DataBuffer.class)
          .switchIfEmpty(Mono.fromCallable(() ->
              exchange.getResponse().bufferFactory().allocateBuffer(0)
          ))
          .flatMap(dataBuffer -> {
            byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
            setCachedAttribute(exchange, dataBuffer, bodyBytes);
            Flux<DataBuffer> cachedBody = getDataBufferFlux(exchange, bodyBytes);
            ServerHttpRequest mutatedRequest = cachedServerHttpRequest(request, cachedBody);
            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

            return chain.filter(mutatedExchange)
                .doOnError(error -> doObservationOnError(error, observationContext, observation))
                .doFinally(signal -> {
                  eventLogHelper.save(mutatedExchange, contextDTO);
                  observation.stop();
                  observationWrapper.clear();
                });
          }),

      scope -> Mono.fromRunnable(scope::close)
    )
    .contextWrite(ctx ->
      ctx.put("context", contextDTO)
    );
  }

  private void decorateBaggage(ContextDTO contextDTO) {
    tracerHelper.setBaggage(HeaderConstant.REQUEST_ID.getHeader(), contextDTO.getRequestId());
    tracerHelper.setBaggage(HeaderConstant.CLIENT_ID.getHeader(), contextDTO.getClientId());
  }

  private void decorateResponseHeaders(ServerHttpRequest request, ServerHttpResponse response) {
    MultiValueMap<String, String> requestHeaders = request.getHeaders();
    String receivedTime = DateTimeHelper.getDateInFormat(ZonedDateTime.now(),
        DateTimeHelper.ISO_8601_GMT7_FORMAT);
    response.getHeaders().addAll(requestHeaders);
    response.getHeaders().add(HeaderConstant.RECEIVED_TIME.getHeader(), receivedTime);
  }

  private Flux<DataBuffer> getDataBufferFlux(ServerWebExchange exchange, byte[] bodyBytes) {
    return Flux.defer(() -> {
      DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bodyBytes);
      return Mono.just(buffer);
    });
  }

  private void setCachedAttribute(ServerWebExchange exchange, DataBuffer dataBuffer, byte[] bodyBytes) {
    if (dataBuffer.readableByteCount() > 0) {
      dataBuffer.read(bodyBytes);
    }
    DataBufferUtils.release(dataBuffer);
    exchange.getAttributes().put("cachedRequestBody", bodyBytes);
  }

  private ContextDTO decorateContext(ServerHttpRequest request) {
    ContextDTO contextDTO = new ContextDTO();
    contextDTO.decorateContext(request, contextPath);
    return contextDTO;
  }

  private ServerHttpRequest cachedServerHttpRequest(ServerHttpRequest request,
      Flux<DataBuffer> cachedBody) {
    return new ServerHttpRequestDecorator(request) {
      @Override
      public Flux<DataBuffer> getBody() {
        return cachedBody;
      }
    };
  }

  private <C extends Observation.Context> void doObservationOnError(Throwable exception, C context,
      Observation observation) {
    log.error(AppLogMessage.message("#Observation - error").error(exception));
    context.addLowCardinalityKeyValue(KeyValue.of("error", exception.getClass().getName()));
    observation.event(Event.of("error", exception.getClass().getName()));
  }
}
