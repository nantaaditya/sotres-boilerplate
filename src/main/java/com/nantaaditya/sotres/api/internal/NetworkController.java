package com.nantaaditya.sotres.api.internal;

import com.nantaaditya.sotres.api.BaseController;
import com.nantaaditya.sotres.model.response.Response;
import com.nantaaditya.sotres.service.NetworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal-api/network")
@RequiredArgsConstructor
public class NetworkController extends BaseController {

  private final NetworkService networkService;

  @GetMapping(
      value = "/sign-on",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Mono<ResponseEntity<Response<Boolean>>> sendSignOn() {
    return Mono.fromCallable(() -> responseHelper.success(Boolean.TRUE))
        .map(this::toResponse)
        .doOnSuccess(response -> networkService.sendSignOn());
  }

  @GetMapping(
      value = "/sign-off",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Mono<ResponseEntity<Response<Boolean>>> sendSignOff() {
    return Mono.fromCallable(() -> responseHelper.success(Boolean.TRUE))
        .map(this::toResponse)
        .doOnNext(response -> networkService.sendSignOff());
  }

  @GetMapping(
      value = "/echo",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Mono<ResponseEntity<Response<Boolean>>> sendEcho() {
    return Mono.fromCallable(() -> responseHelper.success(networkService.sendEcho()))
        .map(this::toResponse);
  }

}
