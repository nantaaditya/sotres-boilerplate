package com.nantaaditya.sotres.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.nantaaditya.sotres.helper.ApiLogbookFormatter;
import com.nantaaditya.sotres.helper.ApiLogbookWriter;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.HeaderConstant;
import com.nantaaditya.sotres.properties.LogProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.LogbookCreator;
import org.zalando.logbook.core.DefaultSink;
import org.zalando.logbook.core.HeaderFilters;
import org.zalando.logbook.netty.LogbookServerHandler;

@Log4j2
@Configuration
public class AppLogbookConfiguration {

  @Autowired
  private TracerHelper tracerHelper;
  @Autowired
  private LogProperties logProperties;
  @Autowired
  private ObjectMapper objectMapper;

  @Bean
  public Logbook logbook(Gson gson) {
    return LogbookCreator.builder()
        .correlationId(this::composeCorrelationId)
        .headerFilter(HeaderFilters.replaceHeaders(logProperties.getSensitiveFields(), "*"))
        .sink(new DefaultSink(
            new ApiLogbookFormatter(objectMapper, gson, logProperties),
            new ApiLogbookWriter(objectMapper)
        ))
        .build();
  }

  @Bean
  public NettyServerCustomizer nettyServerCustomizer(Logbook logbook) {
    if (!logProperties.enableApiLog()) {
      return httpServer -> httpServer;
    }

    return httpServer -> httpServer
        .doOnConnection(
            connection -> connection.addHandlerLast(new LogbookServerHandler(logbook))
        );
  }

  private String composeCorrelationId(HttpRequest httpRequest) {
    String rrn = httpRequest.getHeaders().getFirst(HeaderConstant.REQUEST_ID.getHeader());
    tracerHelper.setBaggage(HeaderConstant.REQUEST_ID.getHeader(), rrn);
    return rrn;
  }
}