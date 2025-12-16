package com.nantaaditya.sotres.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.nantaaditya.sotres.helper.ErrorHelper;
import com.nantaaditya.sotres.helper.MaskingHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.properties.LogbookLogProperties;
import jakarta.annotation.Nullable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.BodyFilter;
import org.zalando.logbook.ContentType;
import org.zalando.logbook.HttpLogFormatter;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.LogbookCreator;
import org.zalando.logbook.core.DefaultHttpLogFormatter;
import org.zalando.logbook.core.DefaultHttpLogWriter;
import org.zalando.logbook.core.DefaultSink;
import org.zalando.logbook.core.HeaderFilters;
import org.zalando.logbook.json.JsonHttpLogFormatter;

@Slf4j
@Configuration
public class LogbookWebClientConfiguration {

  @Autowired
  private TracerHelper tracerHelper;
  @Autowired
  private LogbookLogProperties logProperties;
  @Autowired
  private ObjectMapper objectMapper;
  @Value("${app.log-style}")
  private String logStyle;

  @Bean
  public Logbook logbook(Gson gson) {
    return LogbookCreator.builder()
        .correlationId(this::composeCorrelationId)
        .headerFilter(HeaderFilters.replaceHeaders(logProperties.getSensitiveFields(), "*"))
        .bodyFilter(jsonBodyFilter(gson))
        .sink(new DefaultSink(getLogFormatter(), new DefaultHttpLogWriter()))
        .build();
  }

  private BodyFilter jsonBodyFilter(Gson gson) {
    return new BodyFilter() {
      @Override
      public String filter(@Nullable String contentType, String body) {
        if (!ContentType.isJsonMediaType(contentType)) {
          return body;
        }

        String json = MaskingHelper.maskingJson(gson, logProperties.getSensitiveFields(), body);
        Map<String, Object> jsonMap = gson.fromJson(json, Map.class);
        return logBody(jsonMap);
      }
    };
  }

  private HttpLogFormatter getLogFormatter() {
    return switch (logStyle) {
      case "json" -> new JsonHttpLogFormatter();
      case "text" -> new DefaultHttpLogFormatter();
      default -> new DefaultHttpLogFormatter();
    };
  }

  private String logBody(Map<String, Object> jsonMap) {
    return switch (logStyle) {
      case "text" -> {
        try {
          yield objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonMap);
        } catch (JsonProcessingException e) {
          log.error("#Logbook - not json {}, cause {}", e.getMessage(), ErrorHelper.getRootCause(e));
          yield "";
        }
      }
      case "json" -> {
        yield jsonMap.toString();
      }
      default -> jsonMap.toString();
    };
  }

  private String composeCorrelationId(HttpRequest httpRequest) {
    String rrn = httpRequest.getHeaders().getFirst("x-request-id");
    tracerHelper.setBaggage("reqId", rrn);
    return rrn;
  }
}