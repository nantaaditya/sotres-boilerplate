package com.nantaaditya.sotres.configuration;

import com.github.kpavlov.jreactive8583.client.ClientConfiguration;
import com.nantaaditya.sotres.helper.EnhancedIsoClient;
import com.nantaaditya.sotres.helper.IsoMessageRegistry;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.model.constant.PackagerConstant;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.participant.NetworkProcessorParticipant;
import com.nantaaditya.sotres.participant.TransactionProcessorParticipant;
import com.nantaaditya.sotres.properties.IsoMessageProperties;
import com.nantaaditya.sotres.properties.embedded.IsoMessageConnectionConfiguration;
import com.nantaaditya.sotres.properties.embedded.IsoMessageNetworkConfiguration;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import java.net.InetSocketAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Main process to connect to TCP and process the message
 */
@Log4j2
@Configuration
@RequiredArgsConstructor
public class CoreConfiguration {

  private final IsoMessageProperties isoMessageProperties;
  private final PackagerConfiguration packagerConfiguration;
  private final SystemPropertiesService systemPropertiesService;
  private final IsoMessageRegistry isoMessageRegistry;
  private final TracerHelper tracerHelper;
  private final NetworkProcessorParticipant networkProcessorParticipant;
  private final TransactionProcessorParticipant transactionProcessorParticipant;

  @Bean
  public EnhancedIsoClient client() throws InterruptedException {

    IsoMessageNetworkConfiguration networkConfiguration = isoMessageProperties.network();
    IsoMessageConnectionConfiguration connectionConfiguration = isoMessageProperties.connection();
    String host = connectionConfiguration.host();
    int port = connectionConfiguration.port();

    EnhancedIsoClient client = new EnhancedIsoClient(
      new InetSocketAddress(host, port),
      ClientConfiguration.newBuilder()
        .reconnectInterval(networkConfiguration.reconnectInterval())
        .addEchoMessageListener(false)
        .idleTimeout(networkConfiguration.timeOut())
        .workerThreadsCount(connectionConfiguration.workerThreadCount())
        .logSensitiveData(!isoMessageProperties.log().maskingEnabled())
        .describeFieldsInLog(isoMessageProperties.log().fieldDescriptionEnabled())
        .addLoggingHandler(isoMessageProperties.log().defaultLogHandlerEnabled())
        .build(),
      packagerConfiguration.createMessageFactory(PackagerConstant.DEFAULT),
      isoMessageRegistry,
      systemPropertiesService,
      tracerHelper
    );

    client.addMessageListener(networkProcessorParticipant); // for network handling
    client.addMessageListener(transactionProcessorParticipant); // for transaction handling

    try {
      log.info(AppLogMessage.message("#Channel - connecting to server"));
      client.init();
      client.connect();
    } catch (Exception e) {
      log.error(AppLogMessage.message("#Channel - can't connect to server").error(e));
      Thread.currentThread().interrupt();
      throw new InterruptedException(e.getMessage());
    }

    log.info(AppLogMessage.message("#Channel - connected to server"));
    return client;
  }

}
