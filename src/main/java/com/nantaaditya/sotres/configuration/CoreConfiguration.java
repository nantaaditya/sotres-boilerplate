package com.nantaaditya.sotres.configuration;

import com.github.kpavlov.jreactive8583.client.ClientConfiguration;
import com.github.kpavlov.jreactive8583.client.Iso8583Client;
import com.nantaaditya.sotres.model.constant.PackagerConstant;
import com.nantaaditya.sotres.model.logger.AppLogMessage;
import com.nantaaditya.sotres.participant.NetworkProcessorParticipant;
import com.nantaaditya.sotres.participant.TransactionProcessorParticipant;
import com.nantaaditya.sotres.properties.IsoMessageProperties;
import com.nantaaditya.sotres.properties.embedded.IsoMessageConnectionConfiguration;
import com.nantaaditya.sotres.properties.embedded.IsoMessageNetworkConfiguration;
import com.solab.iso8583.IsoMessage;
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
  private final NetworkProcessorParticipant networkProcessorParticipant;
  private final TransactionProcessorParticipant transactionProcessorParticipant;

  @Bean
  public Iso8583Client<IsoMessage> client() throws InterruptedException {

    IsoMessageNetworkConfiguration networkConfiguration = isoMessageProperties.network();
    IsoMessageConnectionConfiguration connectionConfiguration = isoMessageProperties.connection();
    String host = connectionConfiguration.host();
    int port = connectionConfiguration.port();

    Iso8583Client<IsoMessage> client = new Iso8583Client<>(
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
      packagerConfiguration.createMessageFactory(PackagerConstant.DEFAULT)
    );

    client.addMessageListener(networkProcessorParticipant); // for network handling
    client.addMessageListener(transactionProcessorParticipant); // for transaction handling

    try {
      log.info(AppLogMessage.message("#Channel - connecting to server"));
      client.init();
      client.connect();
    } catch (InterruptedException e) {
      log.error(AppLogMessage.message("#Channel - can't connect to server").error(e));
      Thread.currentThread().interrupt();
      throw new InterruptedException(e.getMessage());
    }

    log.info("#Channel - connected to server");
    return client;
  }

}
