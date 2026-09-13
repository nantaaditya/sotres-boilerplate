package com.nantaaditya.sotres.configuration;

import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.properties.AsyncTaskProperties;
import com.nantaaditya.sotres.properties.BulkheadProperties;
import com.nantaaditya.sotres.properties.embedded.AsyncConfiguration;
import com.nantaaditya.sotres.properties.embedded.BulkheadPoolConfiguration;
import java.util.Map;
import java.util.concurrent.Semaphore;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Factory for {@link Semaphore} bulkhead beans — one per
 * {@code apps.bulkhead.configurations} entry, named {@code <key>Bulkhead} —
 * mirroring {@link AsyncTaskConfiguration}'s executor factory.
 *
 * <p>Runs as a {@link BeanDefinitionRegistryPostProcessor} (before any component is
 * instantiated) rather than on {@code ApplicationReadyEvent}: {@link Semaphore} is a
 * JDK class that cannot be CGLIB-proxied under JPMS, so it can't be consumed through a
 * {@code @Lazy} proxy the way the executors are — the definitions must exist up front
 * so consumers can inject them directly by {@code @Qualifier}.
 */
@Log4j2
@Component
public class BulkheadConfiguration
    implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

  private static final String POSTFIX_BEAN_NAME = "Bulkhead";

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
    BulkheadProperties properties = Binder.get(environment)
        .bind(BulkheadProperties.PREFIX, BulkheadProperties.class)
        .orElseGet(() -> new BulkheadProperties(Map.of()));

    if (properties.configurations() == null || properties.configurations().isEmpty()) {
      log.warn(AppLogMessage.message("#Bulkhead - no bean defined"));
      return;
    }

    AsyncTaskProperties asyncProperties = Binder.get(environment)
        .bind(AsyncTaskProperties.PREFIX, AsyncTaskProperties.class)
        .orElseGet(() -> new AsyncTaskProperties(Map.of()));

    properties.configurations().forEach((key, config) ->
        registry.registerBeanDefinition(
            key + POSTFIX_BEAN_NAME,
            bulkheadDefinition(
                resolvePermits(key, config, asyncProperties),
                config.fair()
            )
        )
    );

    log.debug(AppLogMessage.message("#Bulkhead - bean {} created",
        properties.getBeanNames(POSTFIX_BEAN_NAME)));
  }

  /**
   * Explicit {@code permits} wins. Otherwise derive from the {@link AsyncConfiguration} sharing
   * this bulkhead's key: {@code maxPoolSize + queueCapacity + headroom}. This keeps the bulkhead
   * from ever becoming its own out-of-sync bottleneck ahead of the executor's own admission
   * control — resizing the pool automatically resizes the bulkhead with it.
   */
  int resolvePermits(String key, BulkheadPoolConfiguration config, AsyncTaskProperties asyncProperties) {
    if (config.headroom() < 0) {
      throw new IllegalStateException(
          "Bulkhead '" + key + "' has a negative headroom (" + config.headroom() + "); it must be >= 0");
    }

    int permits;
    if (config.permits() != null) {
      permits = config.permits();
    } else {
      AsyncConfiguration matching = asyncProperties.getConfiguration(key);
      if (matching == null) {
        throw new IllegalStateException(
            "Bulkhead '" + key + "' has no explicit permits and no matching "
                + "apps.async.configurations." + key + " entry to derive them from");
      }
      permits = matching.maxPoolSize() + matching.queueCapacity() + config.headroom();
    }

    // a resolved permit count <= 0 means every tryAcquire() times out silently — every ISO8583
    // transaction would be shed with no startup signal that anything is wrong.
    if (permits <= 0) {
      throw new IllegalStateException(
          "Bulkhead '" + key + "' resolved to " + permits + " permit(s); must be > 0");
    }
    return permits;
  }

  private RootBeanDefinition bulkheadDefinition(int permits, boolean fair) {
    return new RootBeanDefinition(Semaphore.class, () -> new Semaphore(permits, fair));
  }
}
