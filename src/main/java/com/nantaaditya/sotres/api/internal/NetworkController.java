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

@RestController
@RequestMapping("/internal-api/network")
@RequiredArgsConstructor
public class NetworkController extends BaseController {

  private final NetworkService networkService;

  @GetMapping(
      value = "/sign-on",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public ResponseEntity<Response<Boolean>> sendSignOn() {
    ResponseEntity<Response<Boolean>> response = toResponse(responseHelper.success(Boolean.TRUE));
    networkService.sendSignOn();
    return response;
  }

  @GetMapping(
      value = "/sign-off",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public ResponseEntity<Response<Boolean>> sendSignOff() {
    ResponseEntity<Response<Boolean>> response = toResponse(responseHelper.success(Boolean.TRUE));
    networkService.sendSignOff();
    return response;
  }

  @GetMapping(
      value = "/echo",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public ResponseEntity<Response<Boolean>> sendEcho() {
    return toResponse(responseHelper.success(networkService.sendEcho()));
  }
}
