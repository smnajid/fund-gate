package fundgate.rulecard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** PROTOTYPE, ticket #31: the offline Rule Card pipeline as a command-line Spring Boot app (ADR 0006). */
@SpringBootApplication
public class RuleCardPipeline {

    public static void main(String[] args) {
        SpringApplication.run(RuleCardPipeline.class, args);
    }
}
