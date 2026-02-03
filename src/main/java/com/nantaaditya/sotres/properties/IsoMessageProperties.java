package com.nantaaditya.sotres.properties;

import com.nantaaditya.sotres.model.constant.OutgoingProtocol;
import com.nantaaditya.sotres.properties.embedded.IsoMessageConnectionConfiguration;
import com.nantaaditya.sotres.properties.embedded.IsoMessageLogConfiguration;
import com.nantaaditya.sotres.properties.embedded.IsoMessageNetworkConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "iso8583.clientConfiguration")
@SuppressWarnings("squid:S1068")
public record IsoMessageProperties(
    OutgoingProtocol outgoingProtocol,
    IsoMessageLogConfiguration log,
    IsoMessageConnectionConfiguration connection,
    IsoMessageNetworkConfiguration network
) {

}
