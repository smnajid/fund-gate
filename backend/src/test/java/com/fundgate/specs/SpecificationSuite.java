package com.fundgate.specs;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;

/** Runs the executable specification in specs/. */
@Suite
@IncludeEngines("cucumber")
@SelectDirectories("../specs")
public class SpecificationSuite {
}
