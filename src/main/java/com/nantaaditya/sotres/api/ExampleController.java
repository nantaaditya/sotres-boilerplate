package com.nantaaditya.sotres.api;

import com.nantaaditya.sotres.model.constant.ApiResponseCode;
import com.nantaaditya.sotres.model.response.Response;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(value = "/api/example")
@RequiredArgsConstructor
public class ExampleController extends BaseController{

  @GetMapping(
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Mono<ResponseEntity<Response<String>>> greeting(@RequestParam(required = false, defaultValue = "you") String name) {
    return Mono.fromCallable(() -> responseHelper.success("Hi " + name + "!"))
        .map(this::toResponse);
  }

  @GetMapping(
      value = "/error",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Mono<ResponseEntity<Response<Object>>> error() {
    return Mono.fromCallable(() -> responseHelper.failed(ApiResponseCode.BAD_REQUEST, Map.of("key", List.of("value"))))
        .map(this::toResponse);
  }
}
