package io.github.anirbanroy88.mcp.openfigi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OpenFigiApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpenFigiApplication.class, args);
    }
}
