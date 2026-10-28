package com.fundgate;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The dealer app; with the pipeline profile, the offline Rule Card pipeline instead (ADR 0004). */
@SpringBootApplication
// A fixed server URL keeps the generated Angular client the same from one run to the next
@OpenAPIDefinition(info = @Info(title = "FundEli5", version = "0.1.0"), servers = @Server(url = "/"))
public class FundEli5Application {

    public static void main(String[] args) {
        SpringApplication.run(FundEli5Application.class, args);
    }
}
