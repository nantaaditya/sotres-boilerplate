package com.nantaaditya.sotres.helper;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class HealthCheckHelper {

  private final AtomicBoolean isHealthy = new AtomicBoolean(false);

  private final AtomicBoolean isSignedOn = new AtomicBoolean(false);

  public boolean isSignedOn() {
    return isSignedOn.get();
  }

  public void setIsSignedOn(boolean isSignedOn) {
    this.isSignedOn.set(isSignedOn);
  }

  public boolean isHealthy() {
    return isHealthy.get();
  }

  public void setIsHealthy(boolean isHealthy) {
    this.isHealthy.set(isHealthy);
  }
}
