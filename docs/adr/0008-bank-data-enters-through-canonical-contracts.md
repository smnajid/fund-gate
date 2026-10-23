# Bank data enters through canonical contracts; scales stay bank codes

The app is built in an external cloud environment that cannot reach the bank, then cloned into an internal environment where the bank's APIs live (the CRM, a GraphQL API with `portfolio` and `person` queries; FMD for instruments and their documents). So the app reads bank data only through out ports whose models we define ourselves ([Client Profile contract](../playbook/contracts/client-profile.md)), each with a fixture adapter outside the bank and a bank adapter inside it, chosen by Spring profile. No REST endpoint sits between the app and its adapters, and nothing above a port knows the bank's schema or protocol: moving inside the bank means writing the bank adapters and nothing else.

Inside the contracts, the bank's **scales** (risk profile, knowledge and experience, ability to bear losses) are passed through as bank codes and translated only by the validated Scale Mapping, because translating them is a judgement someone must check, and it must change with the bank's real scales without code. **Legal categories** (Investor Classification, opting declarations, mandate type) become canonical enums in the bank adapter, because they are closed lists set by law that rules branch on directly. Every fact is optional: absent means unknown, unknown gives Review, and adapters never fill defaults.

## Considered Options

- **Guess the bank's GraphQL schema and mock it outside**: the adapter would look written early, but a guessed schema would almost certainly be wrong and get rewritten at the event anyway.
- **A small REST facade serving the contract, mocked outside, real inside the bank**: the app would be one build moved by a URL, but it adds a deployable for a 20-hour build, and only the adapter's code differs between the two environments either way.
- **Bank adapter translates the scales too**: one place for all mapping, but it would bury a validated, regulatory correspondence in code no validator reads.
