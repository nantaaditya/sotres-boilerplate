package com.nantaaditya.sotres.e2e;

import com.nantaaditya.sotres.BaseIntegrationTest;
import com.nantaaditya.sotres.e2e.support.FakeIsoHost;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for e2e classes that need only a live ISO8583 connection with no other special config, so
 * they merge into one Spring context instead of one each: {@code EventLogWiringE2eTest} and
 * {@code InboundApiLoggingE2eTest}. Each previously declared its own {@code @DynamicPropertySource}
 * binding a distinct FakeIsoHost port -- that alone forced a fresh context per class, since
 * Spring's context cache key includes the declaring method of every {@code @DynamicPropertySource}.
 *
 * <p>This wiring lives in its own class, one level below {@link BaseIntegrationTest}, rather than
 * directly on {@code BaseIntegrationTest} itself: Spring invokes <b>every</b>
 * {@code @DynamicPropertySource} method found anywhere in a test class's hierarchy, regardless of
 * which subclass triggers it. A subclass with different config -- a distinct
 * {@code @DynamicPropertySource} value, or an extra {@code @Import} -- needs its own,
 * self-contained {@code FakeIsoHost}; if it also inherited this class's
 * {@code @DynamicPropertySource}, both registrations would fire and collide on the same property
 * keys (confirmed by a real failure: the app connected to whichever {@code FakeIsoHost} won that
 * race, while the test itself kept polling the other one, forever).
 *
 * <p>Deliberately <b>not</b> shared more broadly: merging classes that also drive real
 * ISO-&gt;REST-&gt;Postgres traffic ({@code CallbackModeE2eTest}, {@code IsoToRestE2eTest},
 * {@code RestSenderRetryDeadLetterE2eTest}, {@code ObservabilityE2eTest}) into one context was
 * found to leave the shared HikariCP connection pool broken for whichever class ran next
 * (observed: {@code HikariPool total=0}, cascading into multi-minute
 * {@code systemPropertiesService.reload()} stalls) -- those classes each keep their own,
 * independent {@code ISO_HOST}/{@code DOWNSTREAM} and Spring context instead.
 */
public class SharedInfraE2eTestBase extends BaseIntegrationTest {

  protected static final FakeIsoHost ISO_HOST = new FakeIsoHost();

  static {
    try {
      ISO_HOST.start();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("failed to start shared FakeIsoHost", e);
    }
    Runtime.getRuntime().addShutdownHook(new Thread(ISO_HOST::stop));
  }

  @DynamicPropertySource
  static void sharedInfraProperties(DynamicPropertyRegistry registry) {
    registry.add("iso8583.configuration.connection.host", () -> "127.0.0.1");
    registry.add("iso8583.configuration.connection.port", ISO_HOST::getPort);
    registry.add("iso8583.configuration.network.reconnect-interval", () -> 2000);
    registry.add("iso8583.configuration.network.scheduled-echo-enabled", () -> false);
  }

  @BeforeEach
  void drainSharedIsoHost() {
    ISO_HOST.drain();
  }
}
