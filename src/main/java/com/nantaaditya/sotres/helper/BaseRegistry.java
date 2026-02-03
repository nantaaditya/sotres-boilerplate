package com.nantaaditya.sotres.helper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class BaseRegistry {

  protected final IsoMessageLoggerHelper isoMessageLoggerHelper;

  public BaseRegistry(IsoMessageLoggerHelper isoMessageLoggerHelper) {
    this.isoMessageLoggerHelper = isoMessageLoggerHelper;
  }

  protected  <T> Cache<String, T> createCache(int poolSize, int timeOut) {
    return Caffeine.newBuilder()
        .expireAfterWrite(timeOut, TimeUnit.MILLISECONDS)
        .maximumSize(poolSize)
        .removalListener((String key, T value, RemovalCause cause) -> {
          if (RemovalCause.EXPIRED == cause && value != null) {
            log.warn(AppLogMessage.message("#ISO - registry key {} expired", key));
          }
        })
        .build();
  }
}
