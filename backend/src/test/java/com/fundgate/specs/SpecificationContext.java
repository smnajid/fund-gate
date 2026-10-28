package com.fundgate.specs;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/** The one Spring context every module's step definitions share. */
@CucumberContextConfiguration
@SpringBootTest
public class SpecificationContext {
}
