package com.nantaaditya.sotres.api.internal;

import com.nantaaditya.sotres.api.BaseController;
import com.nantaaditya.sotres.model.request.RetryDeadLetterProcessRequest;
import com.nantaaditya.sotres.model.response.Response;
import com.nantaaditya.sotres.service.internal.DeadLetterProcessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(value = "/internal-api/dead_letter_process")
@RequiredArgsConstructor
public class DeadLetterProcessController extends BaseController{

  private final DeadLetterProcessService deadLetterProcessService;

  @DeleteMapping(
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Mono<ResponseEntity<Response<Boolean>>> remove(@RequestParam(required = false, defaultValue = "30") int days) {
    return Mono.fromCallable(() -> responseHelper.success(Boolean.TRUE))
        .flatMap(this::toResponse)
        .doOnSuccess(result ->
          deadLetterProcessService.remove(days).subscribe()
        );
  }

  @PostMapping(
      value = "/_retry",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Mono<ResponseEntity<Response<Boolean>>> retry(@RequestBody @Valid RetryDeadLetterProcessRequest request) {
    return Mono.fromCallable(() -> responseHelper.success(Boolean.TRUE))
        .flatMap(this::toResponse)
        .doOnSuccess(result ->
            deadLetterProcessService.retry(request).subscribe()
        );
  }
}
