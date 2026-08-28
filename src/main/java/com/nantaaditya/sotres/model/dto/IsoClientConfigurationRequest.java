package com.nantaaditya.sotres.model.dto;

import com.github.kpavlov.jreactive8583.client.ClientConfiguration;
import com.github.kpavlov.jreactive8583.iso.MessageFactory;
import com.nantaaditya.sotres.helper.CorrelationRegistry;
import com.nantaaditya.sotres.helper.IsoFieldHelper;
import com.nantaaditya.sotres.helper.IsoMessageLoggerHelper;
import com.nantaaditya.sotres.helper.TracerHelper;
import com.nantaaditya.sotres.properties.ClientProperties;
import com.nantaaditya.sotres.properties.ParticipantConfigurationProperties;
import com.nantaaditya.sotres.service.internal.SystemPropertiesService;
import com.solab.iso8583.IsoMessage;
import java.net.SocketAddress;

public record IsoClientConfigurationRequest(
    SocketAddress socketAddress,
    ClientConfiguration clientConfiguration,
    MessageFactory<IsoMessage> messageFactory,
    CorrelationRegistry correlationRegistry,
    IsoFieldHelper isoFieldHelper,
    IsoMessageLoggerHelper isoMessageLoggerHelper,
    SystemPropertiesService systemPropertiesService,
    TracerHelper tracerHelper,
    ParticipantConfigurationProperties participantConfigurationProperties,
    ClientProperties clientProperties
) {

}
