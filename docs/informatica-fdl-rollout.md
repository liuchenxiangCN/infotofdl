# Informatica to FDL Rollout Guide

This guide is for customer operators who will pilot the Informatica XML to FineDataLink migration. It explains what must be ready before import, what to check during rollout, and when to stop.

This is not a promise that every customer XML will migrate by itself. The current migration is bounded-lossless: graph and task semantics are migrated only when each item is classified as one of these outcomes:

1. Executable FDL.
2. Adapter-required.
3. Runtime-binding-required.
4. Pending or blocked.
5. Out-of-scope.

If a semantic can't be proven executable, it must stay visible in the migration report or generated task metadata. It must not be hidden as a silent success.

## Scope Boundaries

Users must create same-name FDL data connections before migration or import. The required FDL data connection name must match the Informatica `CONNECTIONNAME` value. Migration checks and records the names, but it doesn't create FDL data connections.

These items are out of scope and aren't migrated:

1. credentials, including passwords, tokens, cookies, licenses, and other secrets.
2. datasourceType inference from Informatica XML.
3. schedules and scheduler rules.
4. Java, SOAP, or other adapter behavior that still needs runtime validation.

Do not paste credentials into migration notes, reports, tickets, or screenshots. Configure secrets only through the normal FDL connection or runtime configuration path.

## Pilot Order

Run the pilot in this exact order:

1. `cc_lis`
2. `WF_HNC`
3. `auditdb`
4. `WF_PRIP_Stage`

This order starts with the smallest already proven path, then adds Worklet reuse, Lookup and Filter cases, ODBC adapter review, Decision/status gating, and Java/SOAP adapter-required behavior.

## Rollout Gates

### Gate 1: Migration Report Review

Artifact: `docs/migration-report/README.md`

Go when:

1. The report lists the pilot XML you are about to import.
2. Required connection names are listed.
3. Runtime parameter counts are listed.
4. Adapter-required, runtime-binding-required, pending, blocked, and out-of-scope records are visible.

Stop when:

1. The report is missing the XML.
2. A required connection name is missing from the report.
3. A pending or blocked record is treated as executable without proof.
4. The report implies credentials, datasourceType inference, or schedules were migrated.

### Gate 2: Same-Name Connection Readiness

Artifact: migration report connection list, plus the FDL data connection page.

Go when:

1. Every Informatica `CONNECTIONNAME` for the pilot has a same-name FDL data connection.
2. The FDL connection owner confirms the connection type, host, schema, and access outside this migration tool.
3. ODBC or JDBC_ODBC entries such as `agentchannel` and `training_uat` are reviewed as adapter-required or product-configuration work before use.

Stop when:

1. Any same-name FDL data connection is absent.
2. A connection only exists under a renamed FDL name.
3. The team expects the migration to create a connection, infer datasourceType, or copy credentials.

### Gate 3: E2E Validation Evidence

Artifact: `.omo/evidence/task-11-informatica-fdl-migration.md`

Go when:

1. Positive E2E validation passed for the four local baseline XML samples.
2. Negative probes passed for missing connection, unresolved parameter, unresolved lookup, adapter-required Java, and scope exclusions.
3. The evidence says no real FDL SDK save, deployment, network call, or runtime mutation happened during the offline validation.

Stop when:

1. A required negative probe is missing.
2. A missing connection, unresolved parameter, unresolved lookup, or adapter-required Java case is accepted as ready.
3. The evidence relies only on UI anecdotes instead of the report and E2E gates.

### Gate 4: Plugin Zip and Build Receipt

Artifacts: `docs/build-and-ui-smoke-test.md` and `plugin-src/fine-plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0.zip`

Go when:

1. The plugin zip exists at the documented path.
2. The build receipt or E2E evidence shows the build command exited successfully.
3. The operator has a rollback plan for the currently installed plugin before any install attempt.

Stop when:

1. The zip is missing.
2. The build command failed.
3. The team plans to overwrite the FDL runtime directory directly without backup and approval.

### Gate 5: Runtime Binding Review

Artifact: migration report runtime parameter counts and generated task metadata.

Go when:

1. Runtime-binding-required records have an owner.
2. Operators know which values must be supplied in FDL or adapter configuration.
3. Missing values are treated as required input, not guessed from names.

Stop when:

1. Runtime-bound values are guessed by field, task, or connection names.
2. A parameter value is treated as a secret migration path.
3. A task is promoted before the required runtime values are assigned and reviewed.

### Gate 6: Adapter-Required Review

Artifacts: migration report, generated task metadata, and the relevant adapter notes for Java, SOAP, API, ODBC, JDBC_ODBC, non-equality Lookup, and row-level delete behavior.

Go when:

1. Adapter-required records have an owner and a validation plan.
2. Java/SOAP row API paths are reviewed as adapter-required until runtime binding and status mapping are proven.
3. ODBC/JDBC_ODBC datasourceType handling is reviewed as product configuration or adapter work, not XML inference.
4. Non-equality Lookup or blocked Lookup inputs remain pending, blocked, or adapter-reviewed until row selection and inputs are proven.

Stop when:

1. Adapter-required records are treated as native FDL ready.
2. Java/SOAP behavior is called equivalent without runtime proof.
3. ODBC/JDBC_ODBC entries are imported as if datasourceType had been inferred from XML.

## Pilot Stop/Go Summary

Proceed to the next pilot only after the current pilot passes every gate above.

For `cc_lis`, confirm same-name FDL data connections and the basic generated tasks first.

For `WF_HNC`, confirm Worklet reuse and runtime binding records before continuing.

For `auditdb`, confirm the migration report's executable Lookup/Filter subset, non-equality Lookup single-row plan status, unresolved Lookup inputs, and adapter-required ODBC/JDBC_ODBC entries.

For `WF_PRIP_Stage`, confirm Decision/status gate evidence, runtime-binding-required counts, and Java/SOAP adapter-required review before any runtime run.

Stop the rollout if any gate contradicts the migration report or if the team needs migrated credentials, inferred datasourceType, migrated schedules, or automatic readiness across all XML files. Those are outside the current scope.
