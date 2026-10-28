package com.fundgate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The dealer app; with the pipeline profile, the offline Rule Card pipeline instead (ADR 0004). */
@SpringBootApplication
public class FundEli5Application {

    public static void main(String[] args) {
        SpringApplication.run(FundEli5Application.class, args);
    }
}
