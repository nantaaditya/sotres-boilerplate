package com.nantaaditya.sotres.properties;

import com.nantaaditya.sotres.properties.embedded.ClientConfiguration;
import java.beans.Transient;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("client")
@SuppressWarnings("squid:S1068")
public class ClientProperties {
    private Map<String, ClientConfiguration> configurations = new HashMap<>();

    @Transient
    public ClientConfiguration getConfiguration(String clientName) {
      return configurations.get(clientName);
    }
}
