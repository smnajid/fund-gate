# Spring Boot 4 and Angular everywhere; Python only where it adds real value

We build both runtimes in the bank's standard stack: Spring Boot 4 (Java) for the dealer app backend and for the offline Rule Card pipeline CLI, and Angular for the dealer screen and the Rule Card validation screen. The IT department is still young on Python, so Python is used only when a module needs something the JVM cannot do well. Today no module does. The extraction spike was written in Python, but everything it relied on has a Java equivalent. The official Anthropic Java SDK supports PDF document blocks, Citations with page locations and structured outputs, and it has a Microsoft Foundry backend (`anthropic-java-foundry`). Apache PDFBox replaces pypdf for page selection and the quote-on-page check. The spike's code could not be reused anyway, because the organisers forbid pre-built product code. The egress client calls the official SDK directly, so Citations reach the Rule Card unchanged.

## Considered Options

- **Python for the offline Rule Card pipeline**: closest to the spike and quick for prompt iteration, but it adds a second build, a second container and a stack the bank's teams can't yet support after the hackathon, for no capability Java lacks.
- **Spring AI as the LLM layer**: portable across providers, but we have not checked that it exposes page-level Citations and Foundry hosting options, and it adds an abstraction between the Privacy Gate and the one egress client.
- **React instead of Angular**: more AI-generated examples exist for it, but the bank runs Angular. Angular's own agent skill and MCP server close most of that gap.
