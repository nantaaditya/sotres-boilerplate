package com.nantaaditya.sotres.model.dto;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.util.StreamUtils;

/**
 * Wraps an {@link HttpServletRequest} so the body can be read more than once — the filter needs it
 * for context, the controller binds it, and the event-log interceptor persists it.
 */
public class CacheBodyRequest extends HttpServletRequestWrapper {

  private final byte[] cachedBody;

  public CacheBodyRequest(HttpServletRequest request) throws IOException {
    super(request);
    InputStream requestInputStream = request.getInputStream();
    this.cachedBody = StreamUtils.copyToByteArray(requestInputStream);
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    return new CachedBodyInputStream(this.cachedBody);
  }

  @Override
  public BufferedReader getReader() throws IOException {
    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
    return new BufferedReader(new InputStreamReader(byteArrayInputStream, StandardCharsets.UTF_8));
  }
}
