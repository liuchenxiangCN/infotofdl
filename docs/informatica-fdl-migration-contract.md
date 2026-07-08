# Informatica XML to FDL Migration Contract

This contract defines the Wave 1 migration model for Informatica PowerCenter XML to FineDataLink. It is the shared standard for parser output, compiler decisions, audit reports, and later QA evidence.

The goal is bounded lossless migration: every supported Informatica XML semantic inside the current migration scope is either translated into an FDL structure, preserved as an adapter contract, reported as a runtime binding need, or classified as pending, blocked, or out of scope. The contract must not claim lossless execution when the generated FDL structure cannot yet run with the same behavior.

## Scope

Current scope covers task structure and runtime semantics that are expressed in Informatica XML:

1. Workflow, Worklet, Session, Decision, mapping, transformation, connection reference, parameter, variable, SQL, API, lookup, filter, expression, and target write semantics.
2. Control flow between task instances, including success, failure, condition, Decision output, and task status predicates when the XML exposes them.
3. Data flow between source readers, transformations, lookup readers, API or adapter nodes, projections, filters, writers, and generated FDL data flows.
4. Runtime binding requirements for values that are required at run time but cannot be safely filled from XML.

The following items are explicitly out of scope:

1. datasourceType inference as a plugin responsibility. The plugin records the Informatica connection name and original connection metadata. FDL data connection type resolution belongs to the product connection or adapter layer.
2. FDL data connection creation. Users must create FDL data connections with names matching Informatica `CONNECTIONNAME` values before migration validation.
3. credential migration. Passwords, tokens, cookies, licenses, secrets, and other private runtime credentials must not be copied, printed, generated, inferred, or stored by the migration compiler.
4. schedule migration. Informatica schedule definitions are not part of this phase. The contract covers task structure and runtime semantics only.

## Graph IR Terms

The intermediate representation is a directed, typed graph. A compiler may map several Informatica nodes into one FDL node, or one Informatica node into several FDL nodes, as long as the represented semantics remain classified truthfully.

1. Graph: the full migration unit produced from one Informatica XML document. It contains task graphs, data graphs, adapter contracts, runtime bindings, and status records.
2. Task flow node: a workflow level node such as Workflow, Worklet, Session, Decision, Command, Email, Timer, or another task instance represented in XML. It describes ordering, task status, and control behavior, not row data movement.
3. Data flow node: a mapping level or generated FDL data node such as source reader, Source Qualifier, Expression, Filter, Lookup, Java, Web Service, API, SQL projection, Spark SQL transform, target writer, pre SQL, or post SQL.
4. Control edge: an edge between task flow nodes. It represents a workflow link, Worklet link, Session ordering constraint, success path, failure path, or FDL task line condition.
5. Data edge: an edge between data flow nodes. It represents a row stream, field projection, lookup input, lookup output, API request field, API response field, or target field mapping.
6. Condition edge: a control edge or data edge that has a predicate from Informatica XML. Examples include `WORKFLOWLINK/@CONDITION`, task status predicates, Decision output references, Filter conditions, and Lookup conditions.
7. Parameter edge: a binding edge from an Informatica parameter, variable, port default, workflow variable, mapping variable, or session attribute into a task flow node, data flow node, condition, SQL text, API payload, or adapter contract.
8. Runtime binding edge: a parameter edge whose value cannot be safely resolved from XML and must be supplied by the FDL runtime, user configuration, or an adapter.
9. Adapter boundary: a graph boundary where the XML semantic is parsed, but native FDL generation needs plugin adapter code, product support, or runtime validation. The boundary must list inputs, outputs, required bindings, status, and limitation text.
10. External resource reference: a non secret pointer to an external data source, API endpoint, class name, SQL object, or connection name. It may be recorded, but it does not authorize secret migration or connection creation.

## Bounded Lossless Rules

1. Preserve XML evidence. Every classified node or edge must retain enough Informatica XML origin data for audit: object type, name where available, scope, expression or condition text, connection name, field or port mapping, and status reason.
2. Do not infer business meaning. Field names, task names, connection names, and sample file names are labels, not proof of semantics.
3. Do not require one to one node mapping. Informatica nodes may be combined, split, or wrapped when that produces a clearer FDL graph. The status must describe semantic coverage, not node count similarity.
4. Separate parsing from generation. Parsed XML semantics can be complete while FDL generation is still pending.
5. Separate generation from execution proof. Generated FDL structures are not `FDL_TRANSLATION_READY` unless the current compiler can produce an executable equivalent inside the supported subset.
6. Preserve unsupported semantics visibly. Unknown or unsupported semantics must become visible graph metadata, adapter boundaries, runtime binding records, pending statuses, or blocked statuses. They must not disappear into comments only.
7. Classify unknowns conservatively. If a condition, expression, lookup, API behavior, write mode, parameter, or task status cannot be proven equivalent, classify it as pending, adapter required, runtime binding required, or blocked. Do not mark it ready.
8. Keep connections external. The compiler checks and records same name FDL data connection prerequisites. It does not perform datasourceType inference, connection creation, credential migration, or schedule migration.
9. Protect secrets. Sensitive XML content, external service tokens, passwords, cookies, licenses, and generated credentials are never migration output.
10. Make status machine readable. Reports and generated metadata must use the exact status taxonomy below so later waves can test it deterministically.

## Status Taxonomy

Use these statuses exactly.

1. `FDL_TRANSLATION_READY`: The XML semantic is parsed and the current compiler can generate an FDL structure that is executable within the supported subset. This status requires enough proof to avoid a bounded lossless gap.
2. `FDL_ADAPTER_REQUIRED`: The XML semantic is parsed and the adapter contract is known, but native FDL generation is not enough for equivalent execution. The record must list adapter inputs, outputs, invocation rules, runtime bindings, and validation requirements.
3. `FDL_GENERATION_RESEARCH_PENDING`: The XML semantic is parsed, but the FDL native node shape, plugin API, configuration syntax, or runtime behavior still needs research before generation can be claimed.
4. `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`: The XML semantic is understood and represented in the graph, but the compiler has not yet generated the matching FDL structure.
5. `RUNTIME_BINDING_REQUIRED`: The XML references a value or runtime behavior that must be supplied outside static XML parsing. Examples include unresolved parameters, task runtime status bindings outside proven subsets, Decision output storage, row context, or adapter supplied values. This does not include secret migration.
6. `OUT_OF_SCOPE`: The item is intentionally excluded by this contract. This includes datasourceType inference as a plugin responsibility, FDL data connection creation, credential migration, and schedule migration.
7. `BLOCKED`: The compiler cannot proceed without required information, proof, or product capability. Use this when a semantic is neither safely executable nor merely pending, such as missing upstream lineage needed for a required output field.

## Classification Rules

1. Supported native FDL subset: classify as `FDL_TRANSLATION_READY` only when parser evidence, generated structure, and known FDL behavior cover the XML semantic.
2. Known adapter path: classify as `FDL_ADAPTER_REQUIRED` when XML inputs, outputs, conditions, and status mappings are parsed, but equivalent execution depends on adapter code or adapter runtime validation.
3. Known XML, unknown FDL shape: classify as `FDL_GENERATION_RESEARCH_PENDING` when the XML semantic is clear but FDL generation needs product API or node configuration research.
4. Known XML, not yet generated: classify as `XML_SEMANTICS_PARSED_TRANSLATION_PENDING` when the parser can represent the semantic and the implementation work is compiler output, not discovery.
5. Missing non secret runtime value: classify as `RUNTIME_BINDING_REQUIRED` when a user, runtime, or adapter must supply a value or event binding. Never downgrade this to ready by guessing from names.
6. Contract exclusion: classify datasourceType inference, connection creation, credential migration, schedule migration, and any equivalent excluded item as `OUT_OF_SCOPE`.
7. Unsafe or incomplete semantic: classify as `BLOCKED` when a required condition, field lineage, lookup input, external code behavior, write rule, or adapter binding cannot be represented enough to execute or proceed safely.

Unsupported and unknown semantics must still produce records. A record must include the XML origin, graph location, status, reason, and next required proof. This lets reports say what is executable, what is adapter required, what is pending, what requires runtime binding, what is out of scope, and what is blocked without claiming full lossless execution.

## Required Graph Coverage

Future compiler and report outputs must be able to express at least these classes:

1. Task flow nodes for Workflow, Worklet, Session, Decision, task instance, pre SQL, post SQL, and adapter placeholder tasks.
2. Data flow nodes for source readers, Source Qualifier SQL, Expression, Filter, Lookup, external API or Java call, SQL transform, writer, and adapter provided transform.
3. Control edges for plain workflow links, success links, failure links, strict success AND Decision subsets, and unresolved runtime status branches.
4. Data edges for source to transform, transform to transform, lookup input, lookup output, API request field, API response field, projection, and writer field mapping.
5. Condition edges for workflow link conditions, task status predicates, Decision output predicates, Filter predicates, Lookup predicates, adapter invocation predicates, and generated FDL line conditions.
6. Parameter and runtime binding edges for MappingVariable, WorkflowVariable, session parameters, port defaults, SQL parameters, API payload parameters, adapter row context, and unresolved runtime values.
7. Adapter boundaries for Java, SOAP, API, row level invocation, HTTP status output binding, unproven delete semantics, unresolved task runtime binding, and data source adapter needs.

## Output Requirements

Every audit or generated migration report should list:

1. Source XML file and graph scope.
2. Node or edge type.
3. Informatica XML evidence.
4. Generated or intended FDL representation.
5. Status from the exact taxonomy.
6. Reason and limitation text.
7. Required runtime bindings or adapter bindings, excluding credentials and secrets.
8. Out of scope reason when the status is `OUT_OF_SCOPE`.

This artifact is a contract for later implementation waves. It does not change product code, create tests, create FDL data connections, infer datasourceType, copy credentials, or migrate schedules.
