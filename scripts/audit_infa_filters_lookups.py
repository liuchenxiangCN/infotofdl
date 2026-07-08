#!/usr/bin/env python3
"""Read-only Informatica Filter / Lookup Procedure audit."""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict, deque
from pathlib import Path


DEFAULT_XML = Path("/mnt/c/Users/nifen/Desktop/项目文档/2-项目/百年人寿/auditdb.xml")
SENSITIVE_RE = re.compile(r"(?i)(password|passwd|pwd|token|cookie|license|secret)(\s*[=:]\s*)[^\s&\"'<>]+")
IDENT_RE = re.compile(r"\b[A-Za-z_][A-Za-z0-9_]*\b")
VARIABLE_REF_RE = re.compile(r"(\$\$?[A-Za-z_][A-Za-z0-9_.$]*|\$\{[^}]+\})")
KEYWORDS = {
    "AND",
    "OR",
    "NOT",
    "NULL",
    "TRUE",
    "FALSE",
    "CASE",
    "WHEN",
    "THEN",
    "ELSE",
    "END",
    "IS",
}
FUNCTIONS = {"ISNULL", "NVL", "DECODE", "IIF", "TO_CHAR", "TO_DATE", "TRUNC", "SUBSTR"}


def redact(value: str | None) -> str:
    if not value:
        return ""
    return SENSITIVE_RE.sub(r"\1\2[REDACTED]", value)


def short(value: str | None, limit: int = 180) -> str:
    value = redact(value)
    value = re.sub(r"\s+", " ", value).strip()
    if len(value) <= limit:
        return value
    return value[:limit] + "..."


def parse_xml(path: Path) -> ET.Element:
    raw = path.read_bytes()
    head = raw[:200].decode("ascii", "ignore")
    match = re.search(r'encoding=["\']([^"\']+)["\']', head, re.I)
    encoding = match.group(1) if match else "utf-8"
    codec = "gb18030" if encoding.lower() in {"gb2312", "gbk", "gb18030"} else encoding
    text = raw.decode(codec)
    text = re.sub(r'encoding=["\'][^"\']+["\']', 'encoding="utf-8"', text, count=1, flags=re.I)
    return ET.fromstring(text.encode("utf-8"))


def table_attr(transformation: ET.Element, name: str) -> str:
    for attr in transformation.findall("TABLEATTRIBUTE"):
        if attr.get("NAME", "").lower() == name.lower():
            return attr.get("VALUE", "")
    for attr in transformation.findall("ATTRIBUTE"):
        if attr.get("NAME", "").lower() == name.lower():
            return attr.get("VALUE", "")
    return ""


def dependencies(expression: str) -> list[str]:
    scrubbed = re.sub(r"'([^']|'')*'", " ", expression or "")
    result: list[str] = []
    for match in IDENT_RE.finditer(scrubbed):
        token = match.group(0)
        upper = token.upper()
        tail = scrubbed[match.end() :].lstrip()
        if upper in KEYWORDS or upper in FUNCTIONS or tail.startswith("("):
            continue
        if token.upper() not in {x.upper() for x in result}:
            result.append(token)
    return result


def convert_filter_condition(condition: str) -> tuple[str, str]:
    sql = condition.strip()
    sql = re.sub(r"(?i)\bISNULL\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)\s*=\s*(?:FALSE|0)", r"\1 IS NOT NULL", sql)
    sql = re.sub(r"(?i)\bISNULL\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)\s*(?:!=|<>)\s*(?:TRUE|1)", r"\1 IS NOT NULL", sql)
    sql = re.sub(r"(?i)\bISNULL\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)\s*=\s*(?:TRUE|1)", r"\1 IS NULL", sql)
    sql = re.sub(r"(?i)\bISNULL\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)\s*(?:!=|<>)\s*(?:FALSE|0)", r"\1 IS NULL", sql)
    sql = re.sub(r"(?i)\bNOT\s+ISNULL\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)", r"\1 IS NOT NULL", sql)
    sql = re.sub(r"(?i)\bISNULL\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)", r"\1 IS NULL", sql)
    if re.search(r"(?i)\bISNULL\s*\(", sql):
        return "", "condition still contains unsupported ISNULL form"
    return sql, ""


def build_edges(mapping: ET.Element):
    incoming = defaultdict(list)
    outgoing = defaultdict(list)
    for connector in mapping.findall("CONNECTOR"):
        src = (
            connector.get("FROMINSTANCE", ""),
            connector.get("FROMFIELD", ""),
            connector.get("FROMINSTANCETYPE", ""),
        )
        dst = (
            connector.get("TOINSTANCE", ""),
            connector.get("TOFIELD", ""),
            connector.get("TOINSTANCETYPE", ""),
        )
        incoming[dst].append(src)
        outgoing[src].append(dst)
    return incoming, outgoing


def downstream_targets(outgoing, start: tuple[str, str, str]) -> list[str]:
    result: list[str] = []
    queue = deque([start])
    seen = set()
    while queue and len(seen) < 2000:
        node = queue.popleft()
        if node in seen:
            continue
        seen.add(node)
        for dst in outgoing.get(node, []):
            if dst[2] == "Target Definition":
                if dst[1] not in result:
                    result.append(dst[1])
            else:
                queue.append(dst)
    return result


def source_desc(edge: tuple[str, str, str]) -> str:
    return f"{edge[0]}.{edge[1]} [{edge[2]}]"


def is_select_only_sql(sql: str) -> bool:
    sql = strip_sql_line_comments(sql or "").strip()
    if not sql.upper().startswith("SELECT"):
        return False
    scrubbed = re.sub(r"'([^']|'')*'", " ", sql)
    if ";" in scrubbed:
        return False
    return not re.search(r"(?i)\b(INSERT|UPDATE|DELETE|MERGE|CALL|EXEC|TRUNCATE|DROP|ALTER|CREATE)\b", scrubbed)


def split_sql_select_items(select_list: str) -> list[str]:
    result: list[str] = []
    current: list[str] = []
    in_string = False
    depth = 0
    for char in select_list or "":
        if char == "'":
            in_string = not in_string
            current.append(char)
            continue
        if not in_string:
            if char == "(":
                depth += 1
            elif char == ")" and depth:
                depth -= 1
            elif char == "," and depth == 0:
                item = "".join(current).strip()
                if item:
                    result.append(item)
                current = []
                continue
        current.append(char)
    item = "".join(current).strip()
    if item:
        result.append(item)
    return result


def extract_top_level_select_list(sql: str) -> str:
    source = strip_sql_line_comments(sql or "").strip()
    match = re.search(r"(?i)\bSELECT\b", source)
    if not match:
        return ""
    start = match.end()
    in_string = False
    depth = 0
    for index in range(start, len(source)):
        char = source[index]
        if char == "'":
            in_string = not in_string
            continue
        if in_string:
            continue
        if char == "(":
            depth += 1
            continue
        if char == ")" and depth:
            depth -= 1
            continue
        if depth == 0 and re.match(r"(?i)\bFROM\b", source[index:]):
            return source[start:index].strip()
    return ""


def strip_identifier_quotes(value: str) -> str:
    value = (value or "").strip()
    if len(value) >= 2 and ((value[0], value[-1]) in {('"', '"'), ("`", "`"), ("[", "]")}):
        return value[1:-1]
    return value


def sql_expression_identifiers(expression: str) -> list[str]:
    scrubbed = re.sub(r"'([^']|'')*'", " ", expression or "")
    result: list[str] = []
    for match in IDENT_RE.finditer(scrubbed):
        token = match.group(0)
        tail = scrubbed[match.end() :].lstrip()
        if token.upper() in KEYWORDS or tail.startswith("("):
            continue
        result.append(token)
    return result


def parse_sql_select_item(item: str) -> tuple[str, str]:
    source = (item or "").strip()
    match = re.match(r'(?is)^(.+?)\s+AS\s+(["`\[]?[A-Za-z_][A-Za-z0-9_]*["`\]]?)\s*$', source)
    if match:
        return match.group(1).strip(), strip_identifier_quotes(match.group(2))
    match = re.match(r'(?s)^(.+?)\s+(["`\[]?[A-Za-z_][A-Za-z0-9_]*["`\]]?)\s*$', source)
    if match and not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)?", source):
        return match.group(1).strip(), strip_identifier_quotes(match.group(2))
    identifiers = sql_expression_identifiers(source)
    return source, identifiers[-1] if identifiers else ""


def parse_explicit_sql_select_alias(item: str) -> str:
    source = (item or "").strip()
    match = re.match(r'(?is)^(.+?)\s+AS\s+(["`\[]?[A-Za-z_][A-Za-z0-9_]*["`\]]?)\s*$', source)
    if match:
        return strip_identifier_quotes(match.group(2))
    match = re.match(r'(?s)^(.+?)\s+(["`\[]?[A-Za-z_][A-Za-z0-9_]*["`\]]?)\s*$', source)
    if match and not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)?", source):
        return strip_identifier_quotes(match.group(2))
    return ""


def sql_select_items(sql: str) -> list[str]:
    select_list = extract_top_level_select_list(sql)
    if not select_list:
        return []
    return split_sql_select_items(select_list)


def sql_select_item_at_position(sql: str, position: int) -> str:
    items = sql_select_items(sql)
    if position < 1 or position > len(items):
        return ""
    return items[position - 1]


def display_attr(value: str | None, limit: int = 120) -> str:
    value = short(value, limit)
    return value if value else "<empty>"


def strip_sql_line_comments(sql: str) -> str:
    lines: list[str] = []
    for line in (sql or "").splitlines():
        in_string = False
        cut = len(line)
        index = 0
        while index < len(line) - 1:
            char = line[index]
            if char == "'":
                in_string = not in_string
            if not in_string and line[index : index + 2] == "--":
                cut = index
                break
            index += 1
        lines.append(line[:cut])
    return "\n".join(lines)


def variable_refs(value: str) -> list[str]:
    result: list[str] = []
    for match in VARIABLE_REF_RE.finditer(value or ""):
        ref = match.group(0)
        if ref not in result:
            result.append(ref)
    return result


def classify_expression_value(value: str | None) -> str:
    raw = (value or "").strip()
    if not raw:
        return "empty"
    if raw.upper() == "NULL":
        return "NULL"
    if re.fullmatch(r"'([^']|'')*'", raw):
        return "literal_empty_string" if raw in {"''", "' '"} else "literal"
    refs = variable_refs(raw)
    if refs:
        return "variable_reference:" + ",".join(refs)
    if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", raw):
        return "port_expression:" + raw
    return "expression"


def classify_sql_select_item(item: str) -> str:
    source = (item or "").strip()
    if not source:
        return "missing"
    expression, alias = parse_sql_select_item(source)
    expression = expression.strip()
    if expression.upper() == "NULL":
        base = "lookup_output_NULL"
    elif re.fullmatch(r"'([^']|'')*'", expression):
        base = "lookup_output_literal_empty_string" if expression in {"''", "' '"} else "lookup_output_literal"
    elif variable_refs(expression):
        base = "lookup_output_variable_reference"
    elif re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)?", expression):
        base = "lookup_output_sql_column"
    else:
        base = "lookup_output_sql_expression"
    if alias:
        return base + ";sql_alias=" + alias
    return base


def build_port_semantic_line(
    field: ET.Element,
    position: int,
    source_sql: str,
    read_alias: str = "",
    target_fields: list[str] | None = None,
    connected: bool = False,
) -> str:
    field_name = field.get("NAME", "")
    select_item = sql_select_item_at_position(source_sql, position)
    select_alias = parse_explicit_sql_select_alias(select_item)
    target_fields = target_fields or []
    default_value = field.get("DEFAULTVALUE", "")
    expression = field.get("EXPRESSION", "")
    refs: list[str] = []
    for value in [default_value, expression, select_item]:
        for ref in variable_refs(value):
            if ref not in refs:
                refs.append(ref)
    role_hints: list[str] = []
    if not connected:
        role_hints.append("unconnected_lookup_port")
    if target_fields:
        role_hints.append("lookup_output_feeds_target")
    if select_item:
        role_hints.append("sql_select_position_output")
    if read_alias:
        role_hints.append("base_reader_input")
    parts = [
        field_name,
        "input_edge=" + ("connected" if connected else "unconnected"),
        f"port_type={display_attr(field.get('PORTTYPE'))}",
        f"default_semantics={classify_expression_value(default_value)}",
        f"port_expression_semantics={classify_expression_value(expression)}",
        f"sql_select_position={position if select_item else '-'}",
        "sql_select_semantics=" + classify_sql_select_item(select_item),
        f"sql_select_alias={display_attr(select_alias)}",
        f"read_alias={display_attr(read_alias)}",
        f"targets={display_attr(','.join(target_fields))}",
        "role_hints=" + (",".join(role_hints) if role_hints else "-"),
    ]
    if refs:
        parts.append("variables=" + ",".join(refs))
    return "{" + "; ".join(parts) + "}"


def spark_ident(value: str) -> str:
    return "`" + (value or "").replace("`", "``") + "`"


def safe_node_suffix(value: str) -> str:
    value = re.sub(r"[^A-Za-z0-9_]+", "_", value or "").strip("_")
    return value or "lookup"


def format_port_diagnostic(
    field: ET.Element,
    position: int,
    source_sql: str,
    read_alias: str = "",
    target_fields: list[str] | None = None,
) -> str:
    select_item = sql_select_item_at_position(source_sql, position)
    select_alias = parse_explicit_sql_select_alias(select_item)
    parts = [
        f"{field.get('NAME', '')}",
        f"position={position}",
        f"port_type={display_attr(field.get('PORTTYPE'))}",
        f"default={display_attr(field.get('DEFAULTVALUE'))}",
        f"expression={display_attr(field.get('EXPRESSION'))}",
        f"expression_type={display_attr(field.get('EXPRESSIONTYPE'))}",
        f"sql_select_position={position if select_item else '-'}",
        f"sql_select_alias={display_attr(select_alias)}",
        f"sql_select_item={display_attr(select_item, 160)}",
        f"read_alias={display_attr(read_alias)}",
        f"targets={display_attr(','.join(target_fields or []))}",
    ]
    return "{" + "; ".join(parts) + "}"


def resolve_sql_select_alias(sql: str, field: str) -> str:
    select_list = extract_top_level_select_list(sql)
    if not select_list or not field:
        return ""
    field_key = field.upper()
    for item in split_sql_select_items(select_list):
        expression, alias = parse_sql_select_item(item)
        if not alias:
            continue
        if alias.upper() == field_key:
            return alias
        identifiers = sql_expression_identifiers(expression)
        if len(identifiers) == 1 and identifiers[0].upper() == field_key:
            return alias
    return ""


def parse_lookup_predicates(condition: str) -> tuple[list[tuple[str, str, str]], str]:
    if not condition:
        return [], "lookup condition is empty"
    if re.search(r"(?i)\s+OR\s+", condition):
        return [], "lookup condition contains OR"
    predicates: list[tuple[str, str, str]] = []
    for part in [p.strip() for p in re.split(r"(?i)\s+AND\s+", condition) if p.strip()]:
        match = re.match(r"^\s*([A-Za-z_][A-Za-z0-9_]*)\s*(=|!=|<>)\s*([A-Za-z_][A-Za-z0-9_]*)\s*$", part)
        if not match:
            return [], f"lookup predicate is not a simple =, !=, <> comparison: {part}"
        predicates.append((match.group(1), match.group(2), match.group(3)))
    return predicates, ""


def rewrite_predicates_for_single_row_draft(join_predicates: list[str]) -> str:
    result: list[str] = []
    for predicate in join_predicates:
        result.append(predicate.replace("lookup.", "lookup_row.").replace("base.", "base_row."))
    return "\n     AND ".join(result)


def lookup_source_plan(report: dict[str, object]) -> str:
    if report.get("source_sql"):
        return "lookup_sql_override"
    if report.get("source_table"):
        return "lookup_table"
    return "missing_lookup_source"


def single_row_plan_limitation(report: dict[str, object]) -> str:
    return (
        "single-row lookup plan is plan-only: cache="
        + (str(report.get("caching_enabled") or "-"))
        + "; multiple_match_policy="
        + (str(report.get("multiple_match_policy") or "-"))
        + "; source="
        + lookup_source_plan(report)
        + "; predicates="
        + (", ".join(str(x) for x in report.get("predicate_sources", [])) or "-")
        + "; XML does not prove deterministic Informatica cache/order row selection or an FDL adapter preserving that choice"
    )


def single_row_equivalence_proof(report: dict[str, object]) -> str:
    # Keep this conservative. A row_number draft becomes executable only when
    # XML parsing proves a stable Informatica row-choice contract or an adapter
    # supplies one. cache=YES plus Use Any Value is not enough by itself.
    return ""


def build_single_row_sql_draft(report: dict[str, object]) -> str:
    join_predicates = list(report.get("join_predicates", []))
    if not join_predicates:
        return ""
    output_target_map = dict(report.get("output_target_map", {}))
    projected_outputs = [
        output
        for output in report.get("output_fields", [])
        if output_target_map.get(output)
    ]
    if not projected_outputs:
        projected_outputs = list(report.get("output_fields", []))[:12]
    lookup_node = "lookup_" + safe_node_suffix(str(report.get("name", "")))
    lookup_projection = []
    for output in projected_outputs[:20]:
        lookup_projection.append(f"        lookup_row.{spark_ident(output)} AS {spark_ident(output)}")
    if not lookup_projection:
        lookup_projection.append("        /* TODO: project Lookup output ports from XML TRANSFORMFIELD order */")
    downstream_filters = list(report.get("downstream_filter_conditions", []))
    match_policy = str(report.get("multiple_match_policy") or "unresolved multiple-match policy")
    plan_limitation = str(report.get("single_row_plan_limitation") or single_row_plan_limitation(report))
    where_clause = ""
    if downstream_filters:
        where_clause = "\n WHERE " + "\n   AND ".join(
            condition.split(":", 1)[1].strip() if ":" in condition else condition
            for condition in downstream_filters
        )
    return "\n".join(
        [
            "/* single_row_lookup_plan: source="
            + lookup_source_plan(report)
            + "; cache="
            + (str(report.get("caching_enabled") or "-"))
            + "; multiple_match_policy="
            + (str(report.get("multiple_match_policy") or "-"))
            + "; predicates="
            + (", ".join(str(x) for x in report.get("predicate_sources", [])) or "-")
            + " */",
            "/* executable=false; " + plan_limitation + " */",
            "WITH base_row AS (",
            "  SELECT base_.*, monotonically_increasing_id() AS infa_base_row_id",
            "    FROM $[reader] base_",
            "),",
            "lookup_candidates AS (",
            "  SELECT base_row.*,",
            ",\n".join(lookup_projection) + ",",
            "         ROW_NUMBER() OVER (",
            "           PARTITION BY base_row.infa_base_row_id",
            f"           ORDER BY 1 /* TODO: preserve Informatica lookup cache/order semantics for {match_policy} */",
            "         ) AS infa_lookup_rn",
            "    FROM base_row",
            f"    LEFT JOIN $[{lookup_node}] lookup_row",
            "      ON " + rewrite_predicates_for_single_row_draft(join_predicates),
            "),",
            "lookup_single AS (",
            "  SELECT * FROM lookup_candidates WHERE infa_lookup_rn = 1",
            ")",
            "SELECT lookup_single.*",
            "  FROM lookup_single" + where_clause,
        ]
    )


def single_row_plan_lines(report: dict[str, object]) -> list[str]:
    if not report.get("single_row_plan_ready"):
        return []
    downstream_filters = report.get("downstream_filter_conditions", [])
    return [
        "mode=lookup-single-row",
        "dataflow=DBRead(base) + DBRead(lookup) -> SparkSqlNode(row_number single-row Lookup) -> DBWrite",
        "source=" + lookup_source_plan(report),
        "predicates=" + (", ".join(str(x) for x in report.get("predicate_sources", [])) or "-"),
        "row_id=infa_base_row_id generated before Lookup join",
        "row_selector=ROW_NUMBER() OVER (PARTITION BY base row id ORDER BY unresolved Informatica lookup/cache order) = 1",
        f"match_policy={report.get('multiple_match_policy') or '-'}",
        f"cache={report.get('caching_enabled') or '-'}",
        "equivalence_proof=" + (str(report.get("single_row_equivalence_proof")) or "-"),
        "executable=" + ("true" if report.get("single_row_executable") else "false"),
        "ordering_required=true",
        "adapter_or_ordering_required=true",
        "plan_limitation=" + str(report.get("single_row_plan_limitation") or "-"),
        "downstream_filter_after_single_row="
        + (", ".join(downstream_filters) if downstream_filters else "-"),
    ]


def audit(path: Path) -> int:
    root = parse_xml(path)
    filter_count = 0
    lookup_count = 0
    print(f"# Informatica Filter / Lookup audit: {path.name}")
    print("read_only=true sensitive_values=redacted")
    for mapping in root.findall(".//MAPPING"):
        mapping_name = mapping.get("NAME", "")
        transformations = {t.get("NAME", ""): t for t in mapping.findall("TRANSFORMATION")}
        instance_to_transformation = {
            i.get("NAME", ""): i.get("TRANSFORMATION_NAME", "") or i.get("NAME", "")
            for i in mapping.findall("INSTANCE")
        }
        filters = [t for t in transformations.values() if t.get("TYPE") == "Filter"]
        lookups = [t for t in transformations.values() if t.get("TYPE") == "Lookup Procedure"]
        if not filters and not lookups:
            continue
        incoming, outgoing = build_edges(mapping)
        sessions = [s for s in root.findall(".//SESSION") if s.get("MAPPINGNAME", "") == mapping_name]

        def transformation_for_instance(instance: str) -> ET.Element | None:
            return transformations.get(instance_to_transformation.get(instance, instance))

        def transform_field(instance: str, field: str) -> ET.Element | None:
            trans = transformation_for_instance(instance)
            if trans is None:
                return None
            wanted = field.upper()
            for item in trans.findall("TRANSFORMFIELD"):
                if item.get("NAME", "").upper() == wanted:
                    return item
            return None

        def pure_expression_source(instance: str, field: str) -> str:
            item = transform_field(instance, field)
            expression = (item.get("EXPRESSION", "") if item is not None else "").strip()
            if not expression:
                return field
            if expression.upper() == field.upper():
                return field
            if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", expression):
                return expression
            return ""

        def source_qualifier_read_alias(instance: str, field: str) -> str:
            trans = transformation_for_instance(instance)
            if trans is None:
                return field
            alias = resolve_sql_select_alias(table_attr(trans, "Sql Query"), field)
            return alias or field

        def resolve_direct_sql_alias(node: tuple[str, str, str], seen: set[tuple[str, str, str]] | None = None) -> str:
            seen = seen or set()
            if node in seen:
                return ""
            seen.add(node)
            inst, field, inst_type = node
            if inst_type == "Source Qualifier":
                return source_qualifier_read_alias(inst, field)
            if inst_type == "Source Definition":
                return field
            if inst_type == "Expression":
                upstream = pure_expression_source(inst, field)
                if not upstream:
                    return ""
                edges = incoming.get((inst, upstream, inst_type), []) or incoming.get(node, [])
                for edge in edges:
                    alias = resolve_direct_sql_alias(edge, set(seen))
                    if alias:
                        return alias
            if inst_type == "Filter":
                edges = incoming.get(node, [])
                for edge in edges:
                    alias = resolve_direct_sql_alias(edge, set(seen))
                    if alias:
                        return alias
            return ""

        def connection_safe(connection: str) -> bool:
            if not connection:
                return False
            for session in sessions:
                for conn in session.findall(".//CONNECTIONREFERENCE"):
                    if conn.get("CONNECTIONNAME", "").lower() != connection.lower():
                        continue
                    raw = f"{conn.get('CONNECTIONTYPE', '')}/{conn.get('CONNECTIONSUBTYPE', '')}".upper()
                    if "ODBC" in raw or "JDBC_ODBC" in raw:
                        return False
                    return any(x in raw for x in ["ORACLE", "MYSQL", "SQLSERVER", "POSTGRES"])
            return True

        def downstream_filter_conditions(start_nodes: list[tuple[str, str, str]]) -> list[str]:
            result: list[str] = []
            queue = deque(start_nodes)
            seen = set()
            while queue and len(seen) < 2000:
                node = queue.popleft()
                if node in seen:
                    continue
                seen.add(node)
                for dst in outgoing.get(node, []):
                    if dst[2] == "Filter":
                        filter_trans = transformation_for_instance(dst[0])
                        filter_condition = table_attr(filter_trans, "Filter Condition") if filter_trans is not None else ""
                        sql_condition, _ = convert_filter_condition(filter_condition)
                        label = f"{dst[0]}: {sql_condition or filter_condition}"
                        if label not in result:
                            result.append(label)
                    elif dst[2] != "Target Definition":
                        queue.append(dst)
            return result

        lookup_reports: dict[str, dict[str, object]] = {}

        for trans in lookups:
            name = trans.get("NAME", "")
            condition = table_attr(trans, "Lookup condition")
            source_sql = table_attr(trans, "Lookup Sql Override")
            source_table = table_attr(trans, "Lookup table name")
            caching_enabled = table_attr(trans, "Lookup caching enabled")
            multiple_match_policy = table_attr(trans, "Lookup policy on multiple match")
            input_fields: list[str] = []
            output_fields: list[str] = []
            input_mappings: list[str] = []
            input_aliases: dict[str, str] = {}
            target_fields: list[str] = []
            output_target_map: dict[str, list[str]] = {}
            input_port_diagnostics: list[str] = []
            unresolved_input_port_diagnostics: list[str] = []
            input_port_semantics: list[str] = []
            unresolved_input_port_semantics: list[str] = []
            unresolved_input_semantics_by_field: dict[str, str] = {}
            output_port_diagnostics: list[str] = []
            output_port_semantics: list[str] = []
            lookup_fields = list(trans.findall("TRANSFORMFIELD"))
            for position, field in enumerate(lookup_fields, start=1):
                field_name = field.get("NAME", "")
                port_type = field.get("PORTTYPE", "")
                field_targets = downstream_targets(outgoing, (name, field_name, "Lookup Procedure"))
                edges = incoming.get((name, field_name, "Lookup Procedure"), [])
                if "INPUT" in port_type.upper():
                    input_fields.append(field_name)
                    for edge in edges:
                        input_mappings.append(f"{field_name} <= {source_desc(edge)}")
                        alias = resolve_direct_sql_alias(edge)
                        if alias:
                            input_aliases[field_name.upper()] = alias
                    if not edges:
                        input_mappings.append(f"{field_name} <= unresolved")
                    diagnostic = format_port_diagnostic(
                        field,
                        position,
                        source_sql,
                        input_aliases.get(field_name.upper(), ""),
                        field_targets,
                    )
                    input_port_diagnostics.append(diagnostic)
                    semantic_line = build_port_semantic_line(
                        field,
                        position,
                        source_sql,
                        input_aliases.get(field_name.upper(), ""),
                        field_targets,
                        bool(edges),
                    )
                    input_port_semantics.append(semantic_line)
                    if not edges:
                        unresolved_input_port_diagnostics.append(diagnostic)
                        unresolved_input_port_semantics.append(semantic_line)
                        unresolved_input_semantics_by_field[field_name.upper()] = semantic_line
                if "OUTPUT" in port_type.upper() or "LOOKUP" in port_type.upper():
                    output_fields.append(field_name)
                    output_port_diagnostics.append(
                        format_port_diagnostic(
                            field,
                            position,
                            source_sql,
                            input_aliases.get(field_name.upper(), ""),
                            field_targets,
                        )
                    )
                    output_port_semantics.append(
                        build_port_semantic_line(
                            field,
                            position,
                            source_sql,
                            input_aliases.get(field_name.upper(), ""),
                            field_targets,
                            bool(edges),
                        )
                    )
                    if field_targets:
                        output_target_map[field_name] = field_targets
                for target in field_targets:
                    if target not in target_fields:
                        target_fields.append(target)
            source_readable = is_select_only_sql(source_sql) if source_sql else bool(source_table)
            parsed_predicates, predicate_limit = parse_lookup_predicates(condition)
            predicate_ok = bool(parsed_predicates) and not predicate_limit
            translation_pending = False
            join_predicates: list[str] = []
            predicate_sources: list[str] = []
            predicate_limitations: list[str] = []
            for left, operator, right in parsed_predicates:
                predicate_sources.append(f"{left} {operator} {right}")
                left_alias = input_aliases.get(left.upper(), "")
                right_alias = input_aliases.get(right.upper(), "")
                if bool(left_alias) == bool(right_alias):
                    unresolved = [
                        side
                        for side, alias in ((left, left_alias), (right, right_alias))
                        if side.upper() in {x.upper() for x in input_fields} and not alias
                    ]
                    if unresolved:
                        traces = [
                            unresolved_input_semantics_by_field.get(side.upper(), side)
                            for side in unresolved
                        ]
                        predicate_limitations.append(
                            f"lookup predicate base-side input remains unresolved for {left} {operator} {right}: {', '.join(unresolved)}; trace={' | '.join(traces)}"
                        )
                    else:
                        predicate_limitations.append(
                            f"lookup predicate does not pair one lookup reader field with one traceable SQL reader input field: {left} {operator} {right}"
                        )
                    predicate_ok = False
                    continue
                if left_alias:
                    join_predicates.append(f"lookup.{right} {operator} base.{left_alias}")
                else:
                    join_predicates.append(f"lookup.{left} {operator} base.{right_alias}")
                if operator != "=":
                    translation_pending = True
            conn = table_attr(trans, "Connection Information")
            limitations: list[str] = []
            if predicate_limit:
                limitations.append(predicate_limit)
            limitations.extend(predicate_limitations)
            if translation_pending and predicate_ok:
                pass
            if not predicate_ok:
                limitations.append(
                    "lookup predicate input is not traceable to a base SQL reader field; unresolved ports are classified by default, NULL, port expression, SQL select alias, variable reference, and lookup-output role"
                )
            if not source_readable:
                limitations.append("lookup source SQL/table is empty or not read-only SELECT")
            if not connection_safe(conn):
                limitations.append("lookup connection is not safe/configured")
            executable = predicate_ok and not translation_pending and source_readable and connection_safe(conn)
            pending_candidate = predicate_ok and translation_pending and source_readable and connection_safe(conn)
            downstream_filters = downstream_filter_conditions(
                [(name, field_name, "Lookup Procedure") for field_name in output_fields]
            )
            single_row_plan_ready = pending_candidate and bool(join_predicates) and bool(output_target_map)
            single_row_report_probe: dict[str, object] = {
                "source_sql": source_sql,
                "source_table": source_table,
                "caching_enabled": caching_enabled,
                "multiple_match_policy": multiple_match_policy,
                "predicate_sources": predicate_sources,
            }
            single_row_proof = single_row_equivalence_proof(single_row_report_probe)
            single_row_plan_limit = single_row_plan_limitation(single_row_report_probe)
            single_row_executable = single_row_plan_ready and bool(single_row_proof)
            if single_row_plan_ready and not single_row_executable:
                limitations.append(single_row_plan_limit)
            report: dict[str, object] = {
                "name": name,
                "executable": executable,
                "translation_pending": pending_candidate,
                "single_row_plan_ready": single_row_plan_ready,
                "input_fields": input_fields,
                "input_mappings": input_mappings,
                "input_aliases": input_aliases,
                "input_port_diagnostics": input_port_diagnostics,
                "unresolved_input_port_diagnostics": unresolved_input_port_diagnostics,
                "input_port_semantics": input_port_semantics,
                "unresolved_input_port_semantics": unresolved_input_port_semantics,
                "output_fields": output_fields,
                "output_port_diagnostics": output_port_diagnostics,
                "output_port_semantics": output_port_semantics,
                "output_target_map": output_target_map,
                "target_fields": target_fields,
                "limitations": limitations,
                "join_predicates": join_predicates,
                "predicate_sources": predicate_sources,
                "condition": condition,
                "connection": conn,
                "source_sql": source_sql,
                "source_table": source_table,
                "source_filter": table_attr(trans, "Lookup Source Filter"),
                "caching_enabled": caching_enabled,
                "multiple_match_policy": multiple_match_policy,
                "downstream_filter_conditions": downstream_filters,
                "single_row_executable": single_row_executable,
                "single_row_equivalence_proof": single_row_proof,
                "single_row_plan_limitation": single_row_plan_limit if single_row_plan_ready else "",
            }
            report["single_row_sql_draft"] = build_single_row_sql_draft(report) if single_row_plan_ready else ""
            lookup_reports[name] = report

        def available_after_dataflow(node: tuple[str, str, str], seen: set[tuple[str, str, str]] | None = None) -> bool:
            seen = seen or set()
            if node in seen:
                return False
            seen.add(node)
            inst, field, inst_type = node
            if inst_type in {"Source Qualifier", "Source Definition"}:
                return True
            if inst_type == "Lookup Procedure":
                report = lookup_reports.get(inst, {})
                return bool(report.get("executable") or report.get("single_row_executable"))
            if inst_type == "Expression":
                upstream = pure_expression_source(inst, field)
                if not upstream:
                    return False
                edges = incoming.get((inst, upstream, inst_type), []) or incoming.get(node, [])
                return any(available_after_dataflow(edge, set(seen)) for edge in edges)
            if inst_type == "Filter":
                edges = incoming.get(node, [])
                return any(available_after_dataflow(edge, set(seen)) for edge in edges)
            return False

        def translation_pending_after_dataflow(node: tuple[str, str, str], seen: set[tuple[str, str, str]] | None = None) -> bool:
            seen = seen or set()
            if node in seen:
                return False
            seen.add(node)
            inst, field, inst_type = node
            if inst_type == "Lookup Procedure":
                return bool(lookup_reports.get(inst, {}).get("translation_pending"))
            if inst_type == "Expression":
                upstream = pure_expression_source(inst, field)
                if not upstream:
                    return False
                edges = incoming.get((inst, upstream, inst_type), []) or incoming.get(node, [])
                return any(translation_pending_after_dataflow(edge, set(seen)) for edge in edges)
            if inst_type == "Filter":
                return any(translation_pending_after_dataflow(edge, set(seen)) for edge in incoming.get(node, []))
            return False

        def single_row_plan_ready_after_dataflow(node: tuple[str, str, str], seen: set[tuple[str, str, str]] | None = None) -> bool:
            seen = seen or set()
            if node in seen:
                return False
            seen.add(node)
            inst, field, inst_type = node
            if inst_type == "Lookup Procedure":
                report = lookup_reports.get(inst, {})
                return bool(report.get("single_row_plan_ready") and not report.get("single_row_executable"))
            if inst_type == "Expression":
                upstream = pure_expression_source(inst, field)
                if not upstream:
                    return False
                edges = incoming.get((inst, upstream, inst_type), []) or incoming.get(node, [])
                return any(single_row_plan_ready_after_dataflow(edge, set(seen)) for edge in edges)
            if inst_type == "Filter":
                return any(single_row_plan_ready_after_dataflow(edge, set(seen)) for edge in incoming.get(node, []))
            return False

        print(f"\n## MAPPING {mapping_name}")
        for trans in filters:
            filter_count += 1
            name = trans.get("NAME", "")
            condition = table_attr(trans, "Filter Condition")
            deps = dependencies(condition)
            dep_sources: list[str] = []
            target_fields: list[str] = []
            output_fields: list[str] = []
            for field in trans.findall("TRANSFORMFIELD"):
                field_name = field.get("NAME", "")
                if not field_name:
                    continue
                for target in downstream_targets(outgoing, (name, field_name, "Filter")):
                    if target not in target_fields:
                        target_fields.append(target)
                if outgoing.get((name, field_name, "Filter")) and field_name not in output_fields:
                    output_fields.append(field_name)
            for dep in deps:
                edges = incoming.get((name, dep, "Filter"), [])
                if not edges:
                    dep_sources.append(f"{dep} <= unresolved")
                for edge in edges:
                    dep_sources.append(f"{dep} <= {source_desc(edge)}")
            sql_condition, convert_limit = convert_filter_condition(condition)
            direct_sql = dep_sources and all("[Source Qualifier]" in s or "[Source Definition]" in s for s in dep_sources)
            if not deps:
                direct_sql = True
            dataflow_available = True
            dataflow_translation_pending = False
            dataflow_single_row_plan_ready = False
            for dep in deps:
                dep_edges = incoming.get((name, dep, "Filter"), [])
                if dep_edges and any(available_after_dataflow(edge) for edge in dep_edges):
                    continue
                if dep_edges and any(single_row_plan_ready_after_dataflow(edge) for edge in dep_edges):
                    dataflow_single_row_plan_ready = True
                    dataflow_available = False
                    continue
                if dep_edges and any(translation_pending_after_dataflow(edge) for edge in dep_edges):
                    dataflow_translation_pending = True
                    dataflow_available = False
                    continue
                dataflow_available = False
            if not deps:
                dataflow_available = True
            if sql_condition and dataflow_available:
                status = "FDL_DATAFLOW_EXECUTABLE"
            elif sql_condition and dataflow_single_row_plan_ready:
                status = "FDL_DATAFLOW_SINGLE_ROW_PLAN_READY"
            elif sql_condition and dataflow_translation_pending:
                status = "FDL_DATAFLOW_TRANSLATION_PENDING"
            elif sql_condition and direct_sql:
                status = "SQL_REWRITE_CANDIDATE"
            elif convert_limit:
                status = "UNSUPPORTED_WITH_LIMITATION"
            else:
                status = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
            if convert_limit:
                limitation = convert_limit
            elif dataflow_single_row_plan_ready:
                limitation = "upstream non-equality Lookup has a single-row dataflow plan; apply this Filter WHERE after lookup rn=1, but do not mark executable until row ordering semantics are proven"
            elif dataflow_translation_pending:
                limitation = "upstream Lookup translation is pending; generate this Filter in the same FDL dataflow after lookup single-row semantics are implemented"
            else:
                limitation = "" if dataflow_available else "depends on unsupported Expression/Lookup/API/non-SQL upstream fields"
            print(f"- FILTER {name}")
            print(f"  condition: {redact(condition)}")
            print(f"  dependencies: {', '.join(deps) if deps else '-'}")
            print(f"  dependency_sources: {', '.join(dep_sources) if dep_sources else '-'}")
            print(f"  output_fields: {', '.join(output_fields[:20])}{'...' if len(output_fields) > 20 else ''}")
            print(f"  target_fields: {len(target_fields)} {', '.join(target_fields[:20])}{'...' if len(target_fields) > 20 else ''}")
            print(f"  conversion_status: {status}")
            print(
                "  execution_mode: "
                + (
                    "DBRead -> SparkSqlNode(Filter) -> DBWrite"
                    if status == "FDL_DATAFLOW_EXECUTABLE"
                    else "plan: SparkSqlNode WHERE after upstream single-row Lookup rn=1"
                    if status == "FDL_DATAFLOW_SINGLE_ROW_PLAN_READY"
                    else "pending: SparkSqlNode WHERE after Lookup translation"
                    if status == "FDL_DATAFLOW_TRANSLATION_PENDING"
                    else "-"
                )
            )
            print(f"  sql_condition: {sql_condition if sql_condition else '-'}")
            print(f"  limitation: {limitation if limitation else '-'}")
        for trans in lookups:
            lookup_count += 1
            name = trans.get("NAME", "")
            report = lookup_reports[name]
            status = "FDL_DATAFLOW_EXECUTABLE" if report["executable"] else "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
            if report["single_row_executable"]:
                status = "FDL_DATAFLOW_SINGLE_ROW_EXECUTABLE"
            elif report["single_row_plan_ready"]:
                status = "FDL_DATAFLOW_SINGLE_ROW_PLAN_READY"
            elif report["translation_pending"]:
                status = "FDL_DATAFLOW_TRANSLATION_PENDING"
            if report["limitations"] and not report["executable"]:
                status = (
                    "FDL_DATAFLOW_SINGLE_ROW_EXECUTABLE"
                    if report["single_row_executable"]
                    else "FDL_DATAFLOW_SINGLE_ROW_PLAN_READY"
                    if report["single_row_plan_ready"]
                    else "FDL_DATAFLOW_TRANSLATION_PENDING"
                    if report["translation_pending"]
                    else "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
                )
            print(f"- LOOKUP {name}")
            print(f"  connection: {redact(str(report['connection'])) or '-'}")
            print(f"  table: {redact(str(report['source_table'])) or '-'}")
            print(f"  condition: {redact(str(report['condition']))}")
            print(f"  caching_enabled: {redact(str(report['caching_enabled'])) or '-'}")
            print(f"  multiple_match_policy: {redact(str(report['multiple_match_policy'])) or '-'}")
            print(f"  sql_override_present: {'yes' if report['source_sql'] else 'no'}")
            print(f"  sql_override_preview: {short(str(report['source_sql'])) if report['source_sql'] else '-'}")
            print(f"  lookup_source_filter: {redact(str(report['source_filter'])) or '-'}")
            print(f"  input_fields: {', '.join(report['input_fields']) if report['input_fields'] else '-'}")
            print(f"  input_mappings: {', '.join(report['input_mappings']) if report['input_mappings'] else '-'}")
            print(f"  input_read_aliases: {', '.join(f'{k}->{v}' for k, v in report['input_aliases'].items()) if report['input_aliases'] else '-'}")
            if report["single_row_plan_ready"] or report["unresolved_input_port_semantics"]:
                print(
                    "  lookup_input_port_semantics: "
                    + (
                        " | ".join(report["input_port_semantics"])
                        if report["input_port_semantics"]
                        else "-"
                    )
                )
            print(
                "  unresolved_input_port_diagnostics: "
                + (
                    " | ".join(report["unresolved_input_port_diagnostics"])
                    if report["unresolved_input_port_diagnostics"]
                    else "-"
                )
            )
            print(
                "  unresolved_input_port_semantics: "
                + (
                    " | ".join(report["unresolved_input_port_semantics"])
                    if report["unresolved_input_port_semantics"]
                    else "-"
                )
            )
            if report["single_row_plan_ready"] or report["unresolved_input_port_diagnostics"]:
                print(
                    "  lookup_output_port_diagnostics: "
                    + (
                        " | ".join(report["output_port_diagnostics"][:20])
                        if report["output_port_diagnostics"]
                        else "-"
                    )
                )
                print(
                    "  lookup_output_port_semantics: "
                    + (
                        " | ".join(report["output_port_semantics"][:20])
                        if report["output_port_semantics"]
                        else "-"
                    )
                )
            print(f"  join_predicates: {', '.join(report['join_predicates']) if report['join_predicates'] else '-'}")
            print(f"  output_fields: {', '.join(report['output_fields']) if report['output_fields'] else '-'}")
            if report["single_row_plan_ready"]:
                print(
                    "  output_target_map: "
                    + (
                        ", ".join(
                            f"{field}->{','.join(targets)}"
                            for field, targets in report["output_target_map"].items()
                        )
                        if report["output_target_map"]
                        else "-"
                    )
                )
                print(
                    "  downstream_filter_after_lookup: "
                    + (
                        ", ".join(report["downstream_filter_conditions"])
                        if report["downstream_filter_conditions"]
                        else "-"
                    )
                )
            print(f"  target_fields: {len(report['target_fields'])} {', '.join(report['target_fields'][:20])}{'...' if len(report['target_fields']) > 20 else ''}")
            print(f"  conversion_status: {status}")
            print(
                "  execution_mode: "
                + (
                    "DBRead(base) + DBRead(lookup) -> SparkSqlNode(LEFT JOIN) -> DBWrite"
                    if report["executable"]
                    else "DBRead(base) + DBRead(lookup) -> SparkSqlNode(row_number single-row Lookup + downstream Filter) -> DBWrite"
                    if report["single_row_executable"]
                    else "plan only: DBRead(base) + DBRead(lookup) -> SparkSqlNode(row_number single-row Lookup + downstream Filter) -> DBWrite"
                    if report["single_row_plan_ready"]
                    else "pending: DBRead(base) + DBRead(lookup) -> SparkSqlNode(single-row Lookup semantics) -> DBWrite"
                    if report["translation_pending"]
                    else "-"
                )
            )
            if report["single_row_plan_ready"]:
                print("  single_row_plan:")
                for line in single_row_plan_lines(report):
                    print(f"    {line}")
                print("  single_row_sql_draft:")
                for line in str(report["single_row_sql_draft"]).splitlines():
                    print(f"    {line}")
            print(f"  limitation: {'; '.join(report['limitations']) if report['limitations'] else '-'}")
    print(f"\nsummary: filters={filter_count} lookups={lookup_count}")
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xml", nargs="?", type=Path, default=DEFAULT_XML)
    args = parser.parse_args(argv)
    return audit(args.xml)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
