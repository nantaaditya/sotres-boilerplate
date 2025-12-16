package com.nantaaditya.sotres.controller.internal;

import com.nantaaditya.sotres.model.internal.Response;
import com.nantaaditya.sotres.service.NetworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal-api/network")
@RequiredArgsConstructor
public class NetworkController {

  private final NetworkService networkService;

  @GetMapping(
      value = "/sign-on",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Mono<Response<Boolean>> sendSignOn() {
    return Mono.fromCallable(() -> Response.success(true))
        .doOnNext(response -> networkService.sendSignOn());
  }

  @GetMapping(
      value = "/sign-off",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Mono<Response<Boolean>> sendSignOff() {
    return Mono.fromCallable(() -> Response.success(true))
        .doOnNext(response -> networkService.sendSignOff());
  }

  @GetMapping(
      value = "/echo",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Mono<Response<Boolean>> sendEcho() {
    return Mono.fromCallable(() -> Response.success(networkService.sendEcho()));
  }

}
