#!/usr/bin/env python3
"""Audit Informatica Expression transformations without starting FDL.

The script reads exported PowerCenter XML files, classifies Expression fields,
tracks dependencies and downstream target fields, and reports whether a
computed expression can be safely pushed down to SQL by the plugin.
"""

from __future__ import annotations

import argparse
import csv
import re
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

import audit_infa_api_calls

DEFAULT_XML_DIR = Path("/mnt/c/Users/nifen/Desktop/项目文档/2-项目/百年人寿")
DEFAULT_OUTPUT_DIR = Path("/home/liufeng/code/bainianrenshou/infotofdl/docs/lineage-audit/expression-audit")
DEFAULT_XML_FILES = [
    "cc_lis_20260521.xml",
    "WF_HNC.XML",
    "WF_PRIP_Stage.XML",
    "auditdb.xml",
]

CSV_FIELDS = [
    "xml_file",
    "mapping",
    "transformation",
    "field",
    "port_type",
    "kind",
    "expression",
    "dependencies",
    "dependency_sources",
    "targets",
    "sql_expression",
    "sql_pushdown",
    "fdl_dataflow_status",
    "fdl_dataflow_evidence",
    "limitation",
]


@dataclass(frozen=True)
class Connector:
    from_instance: str
    from_field: str
    from_type: str
    to_instance: str
    to_field: str
    to_type: str


@dataclass
class ExpressionRow:
    xml_file: str
    mapping: str
    transformation: str
    field: str
    port_type: str
    kind: str
    expression: str
    dependencies: list[str] = field(default_factory=list)
    dependency_sources: list[str] = field(default_factory=list)
    targets: list[str] = field(default_factory=list)
    sql_expression: str = ""
    sql_pushdown: bool = False
    fdl_dataflow_status: str = ""
    fdl_dataflow_evidence: str = ""
    limitation: str = ""

    def as_dict(self) -> dict[str, str]:
        return {
            "xml_file": self.xml_file,
            "mapping": self.mapping,
            "transformation": self.transformation,
            "field": self.field,
            "port_type": self.port_type,
            "kind": self.kind,
            "expression": self.expression,
            "dependencies": ",".join(self.dependencies),
            "dependency_sources": "; ".join(self.dependency_sources),
            "targets": "; ".join(self.targets),
            "sql_expression": self.sql_expression,
            "sql_pushdown": "yes" if self.sql_pushdown else "no",
            "fdl_dataflow_status": self.fdl_dataflow_status,
            "fdl_dataflow_evidence": self.fdl_dataflow_evidence,
            "limitation": self.limitation,
        }


def load_xml(path: Path) -> ET.Element:
    data = path.read_bytes()
    for encoding in ("gb18030", "gbk", "gb2312", "utf-8-sig", "utf-8"):
        try:
            text = data.decode(encoding)
            break
        except UnicodeDecodeError:
            continue
    else:
        raise RuntimeError(f"Unable to decode {path}")
    text = re.sub(r"^\s*<\?xml[^>]*\?>", "", text, count=1)
    return ET.fromstring(text)


def clean(value: str | None) -> str:
    return (value or "").strip()


def norm(value: str | None) -> str:
    return re.sub(r'["`\[\]\s]', "", clean(value)).upper()


def ordered_unique(values: Iterable[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        value = clean(value)
        key = norm(value)
        if value and key not in seen:
            seen.add(key)
            result.append(value)
    return result


def short(value: str, limit: int = 220) -> str:
    compact = re.sub(r"\s+", " ", clean(value))
    if len(compact) <= limit:
        return compact
    return compact[: limit - 3] + "..."


def expression_dependencies(expression: str) -> list[str]:
    scrubbed = re.sub(r"'([^']|'')*'", " ", expression or "")
    tokens = []
    for match in re.finditer(r"\b[A-Za-z_][A-Za-z0-9_]*\b", scrubbed):
        token = match.group(0)
        upper = token.upper()
        if upper in {"AND", "OR", "NOT", "NULL", "TRUE", "FALSE", "CASE", "WHEN", "THEN", "ELSE", "END", "IS"}:
            continue
        index = match.end()
        while index < len(scrubbed) and scrubbed[index].isspace():
            index += 1
        if index < len(scrubbed) and scrubbed[index] == "(":
            continue
        tokens.append(token)
    return ordered_unique(tokens)


def split_args(args: str) -> list[str]:
    result: list[str] = []
    current: list[str] = []
    in_string = False
    depth = 0
    i = 0
    while i < len(args):
        ch = args[i]
        if ch == "'":
            in_string = not in_string
            current.append(ch)
            i += 1
            continue
        if not in_string:
            if ch == "(":
                depth += 1
            elif ch == ")" and depth > 0:
                depth -= 1
            elif ch == "," and depth == 0:
                result.append("".join(current).strip())
                current = []
                i += 1
                continue
        current.append(ch)
        i += 1
    if current:
        result.append("".join(current).strip())
    return result


def supported_function(name: str) -> bool:
    return name.upper() in {
        "DECODE",
        "IIF",
        "ISNULL",
        "NVL",
        "TO_CHAR",
        "TO_DATE",
        "TRUNC",
        "SUBSTR",
        "LTRIM",
        "RTRIM",
        "LPAD",
        "RPAD",
        "ROUND",
        "ABS",
        "UPPER",
        "LOWER",
        "LENGTH",
        "SYSDATE",
    }


def unsupported_function(expression: str) -> str:
    for match in re.finditer(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*\(", expression or ""):
        if not supported_function(match.group(1)):
            return match.group(1)
    return ""


def convert_to_sql(expression: str) -> str:
    expression = clean(expression)
    if expression.upper().startswith("IIF(") and expression.endswith(")"):
        args = split_args(expression[4:-1])
        if len(args) == 3:
            return f"CASE WHEN {convert_to_sql(args[0])} THEN {convert_to_sql(args[1])} ELSE {convert_to_sql(args[2])} END"
    if expression.upper().startswith("DECODE(") and expression.endswith(")"):
        args = split_args(expression[7:-1])
        if len(args) >= 3:
            value = convert_to_sql(args[0])
            default = "NULL"
            pair_end = len(args)
            if (len(args) - 1) % 2 == 1:
                default = convert_to_sql(args[-1])
                pair_end = len(args) - 1
            parts = ["CASE"]
            for index in range(1, pair_end, 2):
                if index + 1 >= pair_end:
                    break
                parts.append(f"WHEN {value} = {convert_to_sql(args[index])} THEN {convert_to_sql(args[index + 1])}")
            parts.append(f"ELSE {default} END")
            return " ".join(parts)
    return re.sub(r"(?i)\bISNULL\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)", r"(\1 IS NULL)", expression)


def classify(field_name: str, expression: str) -> tuple[str, str, bool, str]:
    expression = clean(expression)
    if not expression or norm(expression) == norm(field_name):
        return "PASS_THROUGH", "", False, ""
    if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", expression):
        return "RENAME", "", False, ""
    if re.fullmatch(r"'([^']|'')*'|[+-]?\d+(\.\d+)?|NULL", expression, flags=re.IGNORECASE):
        return "CONSTANT", expression, True, ""
    if expression.upper().startswith("ERROR("):
        return "ERROR", "", False, "Informatica ERROR(...) is not converted to runnable FDL/SQL."
    function = unsupported_function(expression)
    if function:
        return "UNSUPPORTED", "", False, f"unsupported Informatica function: {function}"
    sql = convert_to_sql(expression)
    if re.search(r"(?i)\b(IIF|DECODE|ISNULL)\s*\(", expression):
        return "CONDITIONAL", sql, True, ""
    return "SQL_EXPRESSION", sql, True, ""


def connectors_for(mapping: ET.Element) -> tuple[dict[tuple[str, str], list[Connector]], dict[tuple[str, str], list[Connector]]]:
    incoming: dict[tuple[str, str], list[Connector]] = defaultdict(list)
    outgoing: dict[tuple[str, str], list[Connector]] = defaultdict(list)
    for element in mapping.findall("CONNECTOR"):
        connector = Connector(
            from_instance=clean(element.get("FROMINSTANCE")),
            from_field=clean(element.get("FROMFIELD")),
            from_type=clean(element.get("FROMINSTANCETYPE")),
            to_instance=clean(element.get("TOINSTANCE")),
            to_field=clean(element.get("TOFIELD")),
            to_type=clean(element.get("TOINSTANCETYPE")),
        )
        outgoing[(connector.from_instance, connector.from_field)].append(connector)
        incoming[(connector.to_instance, connector.to_field)].append(connector)
    return incoming, outgoing


def collect_targets(outgoing: dict[tuple[str, str], list[Connector]], instance: str, field_name: str, depth: int = 0, seen: set[tuple[str, str]] | None = None) -> list[str]:
    if depth > 32:
        return []
    seen = set() if seen is None else seen
    key = (instance, field_name)
    if key in seen:
        return []
    seen.add(key)
    result: list[str] = []
    for connector in outgoing.get(key, []):
        if connector.to_type == "Target Definition":
            result.append(f"{connector.to_instance}.{connector.to_field}")
        else:
            result.extend(collect_targets(outgoing, connector.to_instance, connector.to_field, depth + 1, set(seen)))
    return ordered_unique(result)


def dependency_sources(incoming: dict[tuple[str, str], list[Connector]], instance: str, dependencies: list[str]) -> list[str]:
    sources: list[str] = []
    for dep in dependencies:
        edges = incoming.get((instance, dep), [])
        if not edges:
            sources.append(f"{dep} <= unresolved")
            continue
        for edge in edges:
            sources.append(f"{dep} <= {edge.from_instance}.{edge.from_field} [{edge.from_type}]")
    return ordered_unique(sources)


def can_pushdown(
    sql_convertible: bool,
    sources: list[str],
    mapping_name: str,
    adapter_reviewed_outputs: dict[tuple[str, str, str], str],
) -> tuple[bool, str]:
    if not sql_convertible:
        return False, ""
    for source in sources:
        if "unresolved" in source:
            return False, f"unresolved dependency: {source}"
        if "[Source Qualifier]" not in source and "[Source Definition]" not in source:
            if source_is_adapter_reviewed_output(source, mapping_name, adapter_reviewed_outputs):
                joined = "; ".join(sources)
                binding_evidence = adapter_binding_evidence_for_source(source, mapping_name, adapter_reviewed_outputs)
                if "FDL_ROW_API_UDF" in binding_evidence:
                    return (
                        False,
                        "depends on API adapter output generated through plugin Spark UDF row API adapter; "
                        "this expression is handled by the FDL post-API projection dataflow, not source SQL pushdown; "
                        "runtimeValidationRequired=yes; noOnOneEqualsOne=yes: "
                        + joined
                        + ("; API binding evidence: " + binding_evidence if binding_evidence else ""),
                    )
                return (
                    False,
                    "requires API adapter row-bound projection; API row invocation and HTTP status binding are parsed as adapter contract metadata, "
                    "but current FDL input nodes cannot consume upstream rows, so native DBRead+ApiRead dataflow is not generated until the adapter provides stable row context; "
                    f"FDL_API_POST_PROJECTION rowBinding=required; joinKey={audit_infa_api_calls.ADAPTER_ROW_BINDING_JOIN_KEY}; "
                    "nativeStableRowId=not_proven; noOnOneEqualsOne=yes: "
                    + joined
                    + ("; API binding evidence: " + binding_evidence if binding_evidence else ""),
                )
            return False, non_sql_dependency_limitation(sources)
    return True, ""


def source_is_sql_reader(source: str) -> bool:
    return "[Source Qualifier]" in source or "[Source Definition]" in source


def api_post_projection_status(
    sql_convertible: bool,
    sources: list[str],
    mapping_name: str,
    adapter_reviewed_outputs: dict[tuple[str, str, str], str],
) -> tuple[str, str]:
    if not sql_convertible:
        return "", ""
    has_source = False
    has_api = False
    api_evidence: list[str] = []
    for source in sources:
        if "unresolved" in source:
            return "XML_SEMANTICS_PARSED_TRANSLATION_PENDING", f"unresolved dependency in XML lineage: {source}"
        if source_is_adapter_reviewed_output(source, mapping_name, adapter_reviewed_outputs):
            has_api = True
            evidence = adapter_binding_evidence_for_source(source, mapping_name, adapter_reviewed_outputs)
            if evidence:
                api_evidence.append(evidence)
            continue
        if source_is_sql_reader(source):
            has_source = True
            continue
        return "XML_SEMANTICS_PARSED_TRANSLATION_PENDING", non_sql_dependency_limitation(sources)
    if has_api:
        has_row_api_udf = any("FDL_ROW_API_UDF" in evidence for evidence in api_evidence)
        if has_row_api_udf:
            evidence = (
                "API post-projection generated through plugin Spark UDF row API adapter; "
                f"adapter={audit_infa_api_calls.ROW_API_ADAPTER_NODE}; function={audit_infa_api_calls.ROW_API_UDF_FUNCTION}; "
                "executionPath=DBRead->SparkSqlNode(row_api_adapter)->SparkSqlNode(post_expression)->DBWrite; "
                "runtimeValidationRequired=yes; noOnOneEqualsOne=yes; projection dependencies: "
                + "; ".join(sources)
            )
            if not has_source:
                evidence += "; no source-row dependency was required by this expression"
            if api_evidence:
                evidence += "; API binding evidence: " + "; ".join(ordered_unique(api_evidence))
            return "FDL_ADAPTER_REQUIRED", evidence
        evidence = (
            "API adapter row-bound projection required before generating native FDL dataflow; "
            f"FDL_ADAPTER_ROW_BINDING required=yes join_key={audit_infa_api_calls.ADAPTER_ROW_BINDING_JOIN_KEY} native_stable_row_id=not_proven no_on_1_equals_1=yes; "
            f"FDL_API_POST_PROJECTION rowBinding=required; joinKey={audit_infa_api_calls.ADAPTER_ROW_BINDING_JOIN_KEY}; nativeStableRowId=not_proven; noOnOneEqualsOne=yes; "
            "projection dependencies: "
            + "; ".join(sources)
        )
        if not has_source:
            evidence += "; no source-row dependency was required by this expression"
        if api_evidence:
            evidence += "; API binding evidence: " + "; ".join(ordered_unique(api_evidence))
        return "FDL_API_POST_PROJECTION_ADAPTER_REQUIRED", evidence
    return "", ""


def source_is_adapter_reviewed_output(
    source: str,
    mapping_name: str,
    adapter_reviewed_outputs: dict[tuple[str, str, str], str],
) -> bool:
    match = re.search(r"<=\s*([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_]*)\s+\[", source or "")
    if not match:
        return False
    return (mapping_name, match.group(1), match.group(2)) in adapter_reviewed_outputs


def adapter_binding_evidence_for_source(
    source: str,
    mapping_name: str,
    adapter_reviewed_outputs: dict[tuple[str, str, str], str],
) -> str:
    match = re.search(r"<=\s*([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_]*)\s+\[", source or "")
    if not match:
        return ""
    return adapter_reviewed_outputs.get((mapping_name, match.group(1), match.group(2)), "")


def non_sql_dependency_limitation(sources: list[str]) -> str:
    joined = "; ".join(sources)
    lower = joined.lower()
    if "custom transformation" in lower or "web service" in lower or "external procedure" in lower:
        return "depends on external-call output; upstream call must be FDL translation ready or have generated adapter contract evidence before this expression can be runnable: " + joined
    return "depends on non-SQL upstream fields: " + joined


def adapter_reviewed_api_outputs(path: Path) -> dict[tuple[str, str, str], str]:
    result: dict[tuple[str, str, str], str] = {}
    try:
        calls = audit_infa_api_calls.audit_file(path)
    except Exception:
        return result
    for call in calls:
        if call.execution_status not in {"FDL_TRANSLATION_READY", "FDL_ADAPTER_REQUIRED"}:
            continue
        for output_field in call.response_fields:
            evidence = audit_infa_api_calls.binding_evidence_for_output(call, output_field)
            if not evidence:
                evidence = f"API_STATUS {call.execution_status}"
            result[(call.mapping, call.transformation, output_field)] = evidence
    return result


def audit_file(path: Path) -> list[ExpressionRow]:
    root = load_xml(path)
    reviewed_outputs = adapter_reviewed_api_outputs(path)
    rows: list[ExpressionRow] = []
    for mapping in root.findall(".//MAPPING"):
        mapping_name = clean(mapping.get("NAME"))
        incoming, outgoing = connectors_for(mapping)
        for transformation in mapping.findall("TRANSFORMATION"):
            if clean(transformation.get("TYPE")) != "Expression":
                continue
            transformation_name = clean(transformation.get("NAME"))
            for field in transformation.findall("TRANSFORMFIELD"):
                field_name = clean(field.get("NAME"))
                expression = clean(field.get("EXPRESSION"))
                if not expression:
                    continue
                kind, sql_expression, sql_convertible, limitation = classify(field_name, expression)
                deps = expression_dependencies(expression)
                sources = dependency_sources(incoming, transformation_name, deps)
                pushdown, pushdown_limitation = can_pushdown(sql_convertible, sources, mapping_name, reviewed_outputs)
                dataflow_status, dataflow_evidence = api_post_projection_status(
                    sql_convertible, sources, mapping_name, reviewed_outputs
                )
                if pushdown_limitation:
                    limitation = pushdown_limitation
                rows.append(
                    ExpressionRow(
                        xml_file=path.name,
                        mapping=mapping_name,
                        transformation=transformation_name,
                        field=field_name,
                        port_type=clean(field.get("PORTTYPE")),
                        kind=kind,
                        expression=expression,
                        dependencies=deps,
                        dependency_sources=sources,
                        targets=collect_targets(outgoing, transformation_name, field_name),
                        sql_expression=sql_expression,
                        sql_pushdown=pushdown,
                        fdl_dataflow_status=dataflow_status,
                        fdl_dataflow_evidence=dataflow_evidence,
                        limitation=limitation,
                    )
                )
    return rows


def write_reports(rows_by_file: dict[str, list[ExpressionRow]], output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    all_rows = [row for rows in rows_by_file.values() for row in rows]
    with (output_dir / "expressions.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        for row in all_rows:
            writer.writerow(row.as_dict())

    index_lines = ["# Informatica Expression audit", "", "| XML | fields | computed | SQL pushdown | key risks |", "|---|---:|---:|---:|---|"]
    for xml_name, rows in rows_by_file.items():
        computed = [row for row in rows if row.kind not in {"PASS_THROUGH", "RENAME"}]
        pushdown = [row for row in computed if row.sql_pushdown]
        risks = [row.limitation for row in computed if row.limitation]
        index_lines.append(
            f"| [{xml_name}]({xml_name}.md) | {len(rows)} | {len(computed)} | {len(pushdown)} | {short('; '.join(ordered_unique(risks)), 120)} |"
        )
        write_file_report(output_dir / f"{xml_name}.md", xml_name, rows)
    index_lines.extend(["", f"- CSV: `expressions.csv`"])
    (output_dir / "README.md").write_text("\n".join(index_lines) + "\n", encoding="utf-8")


def write_file_report(path: Path, xml_name: str, rows: list[ExpressionRow]) -> None:
    kind_counts = Counter(row.kind for row in rows)
    computed = [row for row in rows if row.kind not in {"PASS_THROUGH", "RENAME"}]
    lines = [
        f"# {xml_name} Expression audit",
        "",
        f"- Expression fields: {len(rows)}",
        f"- Computed/non-pass-through fields: {len(computed)}",
        f"- SQL pushdown supported: {sum(1 for row in computed if row.sql_pushdown)}",
        "",
        "## Kind counts",
        "",
        "| kind | count |",
        "|---|---:|",
    ]
    for kind, count in sorted(kind_counts.items()):
        lines.append(f"| {kind} | {count} |")
    lines.extend(["", "## Computed fields", "", "| mapping | expression field | target | kind | deps | SQL pushdown | FDL dataflow | limitation |", "|---|---|---|---|---|---|---|---|"])
    if computed:
        for row in computed:
            lines.append(
                "| {mapping} | {expr_field} | {targets} | {kind} | {deps} | {pushdown} | {dataflow} | {limitation} |".format(
                    mapping=row.mapping,
                    expr_field=f"{row.transformation}.{row.field}: {short(row.expression)}",
                    targets=short("; ".join(row.targets), 100),
                    kind=row.kind,
                    deps=short(",".join(row.dependencies), 100),
                    pushdown="yes" if row.sql_pushdown else "no",
                    dataflow=short(row.fdl_dataflow_status or row.fdl_dataflow_evidence, 160),
                    limitation=short(row.limitation, 180),
                )
            )
    else:
        lines.append("| - | - | - | - | - | - | - |")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xml", nargs="*", help="XML files to audit. Defaults to known project samples.")
    parser.add_argument("--xml-dir", type=Path, default=DEFAULT_XML_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--no-write", action="store_true", help="Print summary only and do not write reports.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    xml_files = [Path(value) for value in args.xml]
    if not xml_files:
        xml_files = [args.xml_dir / name for name in DEFAULT_XML_FILES]
    rows_by_file = {path.name: audit_file(path) for path in xml_files}
    for name, rows in rows_by_file.items():
        computed = [row for row in rows if row.kind not in {"PASS_THROUGH", "RENAME"}]
        pushdown = [row for row in computed if row.sql_pushdown]
        print(f"{name}: expression_fields={len(rows)} computed={len(computed)} sql_pushdown={len(pushdown)}")
        for row in computed[:10]:
            print(f"  {row.mapping}/{row.transformation}.{row.field} -> {', '.join(row.targets) or '-'} [{row.kind}] {short(row.expression)}")
            if row.dependencies:
                print(f"    deps: {', '.join(row.dependencies)}")
            if row.limitation:
                print(f"    limitation: {row.limitation}")
            if row.fdl_dataflow_status:
                print(f"    fdl_dataflow_status: {row.fdl_dataflow_status}")
                print(f"    fdl_dataflow_evidence: {row.fdl_dataflow_evidence}")
    if not args.no_write:
        write_reports(rows_by_file, args.output_dir)
        print(f"wrote {args.output_dir}")


if __name__ == "__main__":
    main()
