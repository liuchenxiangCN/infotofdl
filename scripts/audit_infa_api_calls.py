#!/usr/bin/env python3
"""Audit Informatica Java/SOAP/API calls without starting FDL."""

from __future__ import annotations

import argparse
import re
import xml.etree.ElementTree as ET
from collections import OrderedDict
from dataclasses import dataclass, field
from pathlib import Path


DEFAULT_XML_DIR = Path("/mnt/c/Users/nifen/Desktop/项目文档/2-项目/百年人寿")
SENSITIVE_WORDS = ("password", "passwd", "pwd", "token", "cookie", "license", "secret")
ADAPTER_ROW_BINDING_JOIN_KEY = "_fdl_adapter_row_id"
ROW_API_ADAPTER_NODE = "row_api_adapter"
ROW_API_UDF_PROVIDER_FUNCTION = "infa_soap_http_status"
ROW_API_UDF_FUNCTION = f"plugin_{ROW_API_UDF_PROVIDER_FUNCTION}"


@dataclass
class ApiCall:
    xml_file: str
    session: str
    mapping: str
    transformation: str
    transformation_type: str
    template: str
    method: str = ""
    url: str = ""
    action: str = ""
    soap_action: str = ""
    soap_action_source: str = ""
    invocation_condition: str = ""
    protocol: str = ""
    java_class: str = ""
    java_method: str = ""
    execution_status: str = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
    execution_reason: str = ""
    body_type: str = ""
    content_type: str = ""
    body_preview: str = ""
    request_fields: OrderedDict[str, str] = field(default_factory=OrderedDict)
    response_fields: OrderedDict[str, str] = field(default_factory=OrderedDict)
    adapter_metadata: OrderedDict[str, str] = field(default_factory=OrderedDict)
    response_status_mappings: list[str] = field(default_factory=list)
    invocation_condition_fields: list[str] = field(default_factory=list)
    row_invocation_binding: OrderedDict[str, str] = field(default_factory=OrderedDict)
    http_status_output_bindings: list[OrderedDict[str, str]] = field(default_factory=list)
    row_invocation_adapter_supported: bool = False
    http_status_output_adapter_supported: bool = False
    required_config: list[str] = field(default_factory=list)
    limitations: list[str] = field(default_factory=list)
    post_api_expressions: list[OrderedDict[str, str]] = field(default_factory=list)


def load_xml(path: Path) -> ET.Element:
    data = path.read_bytes()
    for encoding in ("gb18030", "gbk", "utf-8-sig", "utf-8"):
        try:
            text = data.decode(encoding)
            break
        except UnicodeDecodeError:
            continue
    else:
        raise RuntimeError(f"Unable to decode {path}")
    text = re.sub(r"^\s*<\?xml[^>]*\?>", "", text, count=1)
    return ET.fromstring(text)


def children(element: ET.Element, tag: str) -> list[ET.Element]:
    return [child for child in list(element) if child.tag == tag]


def clean(value: str | None) -> str:
    return (value or "").strip()


def is_sensitive(value: str | None) -> bool:
    lower = (value or "").lower()
    return any(word in lower for word in SENSITIVE_WORDS)


def sanitize(value: str | None, limit: int = 220) -> str:
    if value is None:
        return ""
    redacted = re.sub(
        r"(?i)(password|passwd|pwd|token|cookie|license|secret)(\s*[=:]\s*)[^&\s\"'<>]+",
        r"\1\2[REDACTED]",
        value,
    )
    lines: list[str] = []
    for line in redacted.splitlines() or [""]:
        if is_sensitive(line):
            lines.append("[REDACTED sensitive line]")
        else:
            lines.append(line)
    compact = re.sub(r"\s+", " ", " ".join(lines)).strip()
    if len(compact) <= limit:
        return compact
    return compact[: limit - 3] + "..."


def remove_line_comments(code: str) -> str:
    return "\n".join(line for line in (code or "").splitlines() if not line.strip().startswith("//"))


def unescape_java(value: str) -> str:
    return (
        value.replace(r"\"", '"')
        .replace(r"\n", "\n")
        .replace(r"\r", "\r")
        .replace(r"\t", "\t")
        .replace(r"\\", "\\")
    )


def collect_string_assignments(code: str) -> OrderedDict[str, str]:
    result: OrderedDict[str, str] = OrderedDict()
    pattern = re.compile(r'(?m)^\s*(?:String\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*"((?:\\.|[^"\\])*)"\s*;')
    for match in pattern.finditer(code or ""):
        result[match.group(1)] = unescape_java(match.group(2))
    return result


def split_java_args(args: str) -> list[str]:
    result: list[str] = []
    current: list[str] = []
    in_string = False
    escaped = False
    depth = 0
    for char in args:
        if in_string:
            current.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
            current.append(char)
            continue
        if char == "(":
            depth += 1
        elif char == ")" and depth > 0:
            depth -= 1
        if char == "," and depth == 0:
            result.append("".join(current).strip())
            current = []
            continue
        current.append(char)
    if current:
        result.append("".join(current).strip())
    return result


def find_call_args(code: str) -> list[str]:
    pattern = re.compile(r"([A-Za-z_][A-Za-z0-9_]*)\s*\(([^;]*?)\)\s*;", re.DOTALL)
    for match in pattern.finditer(code or ""):
        method_name = match.group(1).lower()
        if any(word in method_name for word in ("send", "call", "request", "response")):
            args = split_java_args(match.group(2))
            if len(args) >= 2:
                return args
    return []


def resolve_arg(raw: str, assignments: OrderedDict[str, str]) -> str | None:
    value = clean(raw)
    if len(value) >= 2 and value.startswith('"') and value.endswith('"'):
        return unescape_java(value[1:-1])
    return assignments.get(value)


def looks_like_payload(value: str) -> bool:
    lower = clean(value).lower()
    return lower.startswith("<?xml") or lower.startswith("<soap") or lower.startswith("{") or lower.startswith("[")


def body_from_method(functions: str, method_name: str) -> str:
    method_body = functions
    method_match = re.search(rf"{re.escape(method_name)}\s*\(\s*\)\s*\{{(.*?)\n\s*\}}", functions or "", re.DOTALL)
    if method_match:
        method_body = method_match.group(1)
    assignments = collect_string_assignments(method_body)
    return_var = ""
    return_match = re.search(r"return\s+([A-Za-z_][A-Za-z0-9_]*)\s*;", method_body or "")
    if return_match:
        return_var = return_match.group(1)
    if return_var and return_var in assignments:
        return assignments[return_var]
    payload = ""
    for value in assignments.values():
        if looks_like_payload(value):
            payload = value
    return payload


def resolve_body(call_args: list[str], assignments: OrderedDict[str, str], functions: str, all_code: str) -> str:
    body_arg = call_args[1].strip() if len(call_args) > 1 else ""
    direct = resolve_arg(body_arg, assignments)
    if direct:
        return direct
    if body_arg.endswith("()"):
        body = body_from_method(functions, body_arg[:-2].strip())
        if body:
            return body
    for value in assignments.values():
        if looks_like_payload(value):
            return value
    matches = re.findall(r'"((?:\\.|[^"\\])*(?:<soap|<\?xml|\{)(?:\\.|[^"\\])*)"', all_code or "", re.I)
    return unescape_java(matches[-1]) if matches else ""


def extract_headers(code: str, assignments: OrderedDict[str, str]) -> OrderedDict[str, str]:
    headers: OrderedDict[str, str] = OrderedDict()
    pattern = re.compile(r'setRequestProperty\s*\(\s*"([^"]+)"\s*,\s*([^\)]*?)\s*\)')
    for match in pattern.finditer(code or ""):
        name = "Content-Type" if match.group(1).lower() == "content-type" else match.group(1)
        value = resolve_arg(match.group(2).strip(), assignments)
        headers[name] = value if value is not None else match.group(2).strip()
    return headers


def header_value(headers: OrderedDict[str, str], key: str) -> str:
    for name, value in headers.items():
        if name.lower() == key.lower():
            return value
    return ""


def extract_method(code: str, body: str) -> str:
    match = re.search(r'setRequestMethod\s*\(\s*"([A-Za-z]+)"\s*\)', code or "")
    if match:
        return match.group(1).upper()
    return "POST" if body else "GET"


def extract_soap_operation(body: str) -> str:
    match = re.search(r"<(?:[A-Za-z0-9_]+:)?Body\b[^>]*>\s*<(?:(?:[A-Za-z0-9_]+):)?([A-Za-z_][A-Za-z0-9_.-]*)", body or "", re.I | re.S)
    return match.group(1) if match else ""


def extract_condition(on_input: str) -> str:
    match = re.search(r"if\s*\((.*?)\)\s*\{[^{}]*?(?:send|call|request|response)[A-Za-z0-9_]*\s*\(", on_input or "", re.I | re.S)
    return re.sub(r"\s+", " ", match.group(1)).strip() if match else ""


def is_external_transformation(transformation: ET.Element) -> bool:
    marker = " ".join(
        [
            clean(transformation.get("TYPE")),
            clean(transformation.get("TEMPLATENAME")),
            " ".join(clean(item.get("NAME")) + " " + clean(item.get("VALUE"))[:2048] for item in transformation.iter("METADATAEXTENSION")),
            " ".join(clean(item.get("NAME")) + " " + clean(item.get("VALUE")) for item in transformation.iter("TABLEATTRIBUTE")),
        ]
    ).lower()
    return any(
        word in marker
        for word in (
            "java transformation",
            "external procedure",
            "java class",
            "class name",
            "method name",
            "procedure",
            "web service",
            "soap",
            "soapaction",
            "endpoint",
            "wsdl",
            "httpurlconnection",
            "sendandgetresponsedata",
            "setrequestproperty",
            "://",
            " rest ",
            " api ",
        )
    )


def table_attributes(transformation: ET.Element) -> OrderedDict[str, str]:
    result: OrderedDict[str, str] = OrderedDict()
    for item in transformation.iter("TABLEATTRIBUTE"):
        result[clean(item.get("NAME"))] = clean(item.get("VALUE"))
    return result


def find_named_metadata(metadata: OrderedDict[str, str], kind: str) -> str:
    for name, value in metadata.items():
        lower = name.lower()
        if not value or "snippet" in lower or "num_of_inner_classes" in lower:
            continue
        if kind == "class" and (
            lower == "class" or "class name" in lower or "classname" in lower or "java class" in lower
        ):
            return value
        if kind == "method" and ("method" in lower or "procedure" in lower):
            return value
    return ""


def detect_protocol(url: str, body: str, headers: OrderedDict[str, str], transformation_type: str, template: str) -> str:
    marker = f"{url} {body} {headers} {transformation_type} {template}".lower()
    if "soap" in marker or "wsdl" in marker or "envelope" in marker:
        return "SOAP_HTTP"
    if url.lower().startswith(("http://", "https://")):
        return "HTTP"
    if "external procedure" in marker:
        return "EXTERNAL_PROCEDURE"
    if "java transformation" in marker or "java class" in marker:
        return "JAVA"
    return ""


def looks_like_soap(body: str) -> bool:
    return "envelope" in clean(body).lower()


def default_content_type(body: str) -> str:
    lower = clean(body).lower()
    if not lower:
        return ""
    if looks_like_soap(body):
        return "application/soap+xml;charset=utf-8"
    if lower.startswith(("{", "[")):
        return "application/json;charset=utf-8"
    if lower.startswith("<"):
        return "application/xml;charset=utf-8"
    return "text/plain;charset=utf-8"


def detect_body_type(body: str, content_type: str) -> str:
    lower = f"{content_type} {clean(body)}".lower()
    if "json" in lower or clean(body).startswith(("{", "[")):
        return "JSON"
    return "TEXT" if body else ""


def collect_adapter_metadata(*metadata_groups: OrderedDict[str, str]) -> OrderedDict[str, str]:
    result: OrderedDict[str, str] = OrderedDict()
    for metadata in metadata_groups:
        for name, value in metadata.items():
            lower = name.lower()
            if not name or not value or "snippet" in lower:
                continue
            if "num_of_inner_classes" in lower:
                continue
            if (
                lower == "class"
                or "class name" in lower
                or "java class" in lower
                or "method" in lower
                or "procedure" in lower
                or "endpoint" in lower
                or "url" in lower
                or "soapaction" in lower
                or lower == "action"
                or "protocol" in lower
                or "service" in lower
            ):
                result[name] = sanitize(value)
    return result


def classify_call(call: ApiCall) -> None:
    has_url = bool(call.url)
    has_method = bool(call.method)
    has_protocol = bool(call.protocol)
    has_outputs = bool(call.response_fields)
    has_mapped_output = any(bool(value) for value in call.response_fields.values())
    has_body_or_get = call.method.upper() == "GET" or bool(call.body_preview)
    if not has_url:
        call.required_config.append("endpoint URL")
    if not has_method:
        call.required_config.append("HTTP method/protocol")
    if not has_body_or_get:
        call.required_config.append("request body or GET semantics")
    if not has_outputs:
        call.required_config.append("response/output fields")
    if not has_mapped_output:
        call.required_config.append("response-to-target field mapping")
    if call.invocation_condition:
        call.required_config.append("row-level invocation condition binding")
    if call.response_status_mappings:
        call.required_config.append("HTTP status-code output binding adapter")
    api_supported = has_url and has_method and has_protocol and has_outputs and has_mapped_output and has_body_or_get
    adapter_required = bool(call.invocation_condition or call.response_status_mappings)
    adapter_supported = (
        (not call.invocation_condition or call.row_invocation_adapter_supported)
        and (not call.response_status_mappings or call.http_status_output_adapter_supported)
    )
    if api_supported and not call.limitations:
        if adapter_required:
            if adapter_supported:
                call.execution_status = "FDL_ADAPTER_REQUIRED"
                call.execution_reason = (
                    "adapter contract contains parsed row-level invocation and HTTP status output bindings; "
                    "native FDL API dataflow is not generated until the adapter provides row context"
                )
            else:
                call.execution_status = "FDL_GENERATION_RESEARCH_PENDING"
                call.execution_reason = "adapter bindings still need executable FDL/plugin structure before generating runnable API dataflow"
        else:
            call.execution_status = "FDL_TRANSLATION_READY"
            call.execution_reason = "protocol, endpoint, request mapping, and response mapping are present"
    elif api_supported:
        call.execution_status = "FDL_GENERATION_RESEARCH_PENDING"
        call.execution_reason = "preserved limitations require FDL/plugin generation work before generating runnable API dataflow"
    elif has_url or has_outputs or call.adapter_metadata:
        call.execution_status = "FDL_ADAPTER_REQUIRED"
        call.execution_reason = "external call metadata is incomplete for a native FDL API node; generate a plugin adapter or add missing XML-derived mapping"
    else:
        call.execution_status = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
        call.execution_reason = "external call marker is visible, but executable metadata was not derived"


def incoming_source(mapping: ET.Element, transformation: str, field_name: str) -> str:
    for connector in mapping.iter("CONNECTOR"):
        if connector.get("TOINSTANCE") == transformation and connector.get("TOFIELD") == field_name:
            source = f"{connector.get('FROMINSTANCE', '')}.{connector.get('FROMFIELD', '')}"
            source_type = clean(connector.get("FROMINSTANCETYPE"))
            return f"{source} [{source_type}]" if source_type else source
    return ""


def output_targets(mapping: ET.Element, instance: str, field_name: str) -> list[str]:
    result: list[str] = []

    def walk(current_instance: str, current_field: str, visited: set[tuple[str, str]], depth: int) -> None:
        if depth > 32 or (current_instance, current_field) in visited:
            return
        visited.add((current_instance, current_field))
        for connector in mapping.iter("CONNECTOR"):
            if connector.get("FROMINSTANCE") != current_instance or connector.get("FROMFIELD") != current_field:
                continue
            target_field = clean(connector.get("TOFIELD"))
            target_type = clean(connector.get("TOINSTANCETYPE"))
            if target_type == "Target Definition":
                if target_field and target_field not in result:
                    result.append(target_field)
            else:
                walk(clean(connector.get("TOINSTANCE")), target_field, set(visited), depth + 1)

    walk(instance, field_name, set(), 0)
    return result


def ordered_unique(values: list[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        value = clean(value)
        key = value.upper()
        if value and key not in seen:
            seen.add(key)
            result.append(value)
    return result


def expression_dependencies(expression: str) -> list[str]:
    scrubbed = re.sub(r"'([^']|'')*'", " ", expression or "")
    tokens: list[str] = []
    keywords = {"AND", "OR", "NOT", "NULL", "TRUE", "FALSE", "CASE", "WHEN", "THEN", "ELSE", "END", "IS"}
    for match in re.finditer(r"\b[A-Za-z_][A-Za-z0-9_]*\b", scrubbed):
        token = match.group(0)
        upper = token.upper()
        if upper in keywords:
            continue
        index = match.end()
        while index < len(scrubbed) and scrubbed[index].isspace():
            index += 1
        if index < len(scrubbed) and scrubbed[index] == "(":
            continue
        tokens.append(token)
    return ordered_unique(tokens)


def expression_dependency_sources(mapping: ET.Element, transformation: str, dependencies: list[str]) -> list[str]:
    result: list[str] = []
    for dependency in dependencies:
        matches = []
        for connector in mapping.iter("CONNECTOR"):
            if connector.get("TOINSTANCE") == transformation and connector.get("TOFIELD") == dependency:
                source = f"{clean(connector.get('FROMINSTANCE'))}.{clean(connector.get('FROMFIELD'))}"
                source_type = clean(connector.get("FROMINSTANCETYPE"))
                matches.append(f"{dependency} <= {source} [{source_type}]" if source_type else f"{dependency} <= {source}")
        result.extend(matches or [f"{dependency} <= unresolved"])
    return ordered_unique(result)


def source_is_current_api_output(source: str, call: ApiCall) -> bool:
    match = re.search(r"<=\s*([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_]*)\s+\[", source or "")
    if not match:
        return False
    return match.group(1) == call.transformation and match.group(2) in call.response_fields


def source_is_sql_reader(source: str) -> bool:
    return "[Source Qualifier]" in source or "[Source Definition]" in source


def find_post_api_expressions(mapping: ET.Element, call: ApiCall) -> list[OrderedDict[str, str]]:
    if call.execution_status not in {"FDL_TRANSLATION_READY", "FDL_ADAPTER_REQUIRED"}:
        return []
    result: list[OrderedDict[str, str]] = []
    for transformation in children(mapping, "TRANSFORMATION"):
        if clean(transformation.get("TYPE")) != "Expression":
            continue
        transformation_name = clean(transformation.get("NAME"))
        for field in children(transformation, "TRANSFORMFIELD"):
            field_name = clean(field.get("NAME"))
            expression = clean(field.get("EXPRESSION"))
            if not field_name or not expression or expression.upper() == field_name.upper():
                continue
            dependencies = expression_dependencies(expression)
            if not dependencies:
                continue
            sources = expression_dependency_sources(mapping, transformation_name, dependencies)
            has_api = any(source_is_current_api_output(source, call) for source in sources)
            if not has_api:
                continue
            all_bound = all(source_is_current_api_output(source, call) or source_is_sql_reader(source) for source in sources)
            row_api_udf_supported = row_api_udf_adapter_supported(call)
            if all_bound and row_api_udf_supported:
                status = "FDL_ADAPTER_REQUIRED"
                evidence = (
                    "API post-projection generated through plugin Spark UDF row API adapter; "
                    f"adapter={ROW_API_ADAPTER_NODE}; function={ROW_API_UDF_FUNCTION}; "
                    f"providerFunction={ROW_API_UDF_PROVIDER_FUNCTION}; "
                    "executionPath=DBRead->SparkSqlNode(row_api_adapter)->SparkSqlNode(post_expression)->DBWrite; "
                    "runtimeValidationRequired=yes; noOnOneEqualsOne=yes"
                )
            elif all_bound:
                status = "FDL_API_POST_PROJECTION_ADAPTER_REQUIRED"
                evidence = (
                    "API adapter row-bound projection required before generating native FDL dataflow; "
                    "FDL_ADAPTER_ROW_BINDING " + adapter_row_binding_line(call)
                )
            else:
                status = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
                evidence = "dependency is not fully available from source reader plus current API output"
            item: OrderedDict[str, str] = OrderedDict()
            item["status"] = status
            item["expression"] = f"{transformation_name}.{field_name}: {sanitize(expression)}"
            item["targets"] = ", ".join(output_targets(mapping, transformation_name, field_name))
            item["dependencies"] = "; ".join(sources)
            item["evidence"] = evidence
            item["row_binding"] = adapter_row_binding_line(call)
            result.append(item)
    return result


def find_output_method_assignments(code: str, output_fields: list[str]) -> OrderedDict[str, str]:
    result: OrderedDict[str, str] = OrderedDict()
    outputs = set(output_fields)
    pattern = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([A-Za-z_][A-Za-z0-9_]*)\s*\(")
    for match in pattern.finditer(code or ""):
        output_field = match.group(1)
        if output_field in outputs:
            result[output_field] = match.group(2)
    return result


def find_method_body(code: str, method_name: str) -> str:
    if not code or not method_name:
        return ""
    match = re.search(rf"\b{re.escape(method_name)}\s*\([^)]*\)\s*\{{", code)
    if not match:
        return ""
    start = match.end()
    depth = 1
    in_string = False
    in_single = False
    escaped = False
    for index in range(start, len(code)):
        char = code[index]
        if escaped:
            escaped = False
            continue
        if char == "\\":
            escaped = True
            continue
        if char == '"' and not in_single:
            in_string = not in_string
            continue
        if char == "'" and not in_string:
            in_single = not in_single
            continue
        if in_string or in_single:
            continue
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return code[start:index]
    return ""


def first_string_assignment(code: str, variable: str) -> str:
    if not code or not variable:
        return ""
    match = re.search(
        rf'(?:\bString\s+)?\b{re.escape(variable)}\s*=\s*"((?:\\.|[^"\\])*)"\s*;',
        code,
    )
    return unescape_java(match.group(1)) if match else ""


def http_status_variable(method_body: str) -> str:
    match = re.search(r"(?:\bint\s+)?\b([A-Za-z_][A-Za-z0-9_]*)\s*=\s*[^;]*\.getResponseCode\s*\(", method_body or "")
    return match.group(1) if match else ""


def status_assignments(method_body: str, status_variable: str, return_variable: str, reversed_order: bool) -> list[tuple[str, str]]:
    if reversed_order:
        condition = rf"(\d{{3}})\s*==\s*{re.escape(status_variable)}"
    else:
        condition = rf"{re.escape(status_variable)}\s*==\s*(\d{{3}})"
    result: list[tuple[str, str]] = []
    pattern = re.compile(rf"\bif\s*\(\s*{condition}\s*\)\s*\{{(.*?)\}}", re.S)
    for match in pattern.finditer(method_body or ""):
        value = first_string_assignment(match.group(2), return_variable)
        if value:
            result.append((match.group(1), value))
    return result


def parse_status_return_mapping(method_body: str) -> tuple[str, list[tuple[str, str]]]:
    if not method_body or "getResponseCode" not in method_body:
        return "", []
    return_matches = re.findall(r"\breturn\s+([A-Za-z_][A-Za-z0-9_]*)\s*;", method_body)
    return_variable = return_matches[-1] if return_matches else ""
    if not return_variable:
        return "", []
    default_value = first_string_assignment(method_body, return_variable)
    status_variable = http_status_variable(method_body) or "statusCode"
    values = status_assignments(method_body, status_variable, return_variable, False)
    values.extend(status_assignments(method_body, status_variable, return_variable, True))
    return default_value, values


def status_binding_visible_line(binding: OrderedDict[str, str]) -> str:
    line = f"{binding.get('output', '')} <= {binding.get('value', '')} when HTTP status {binding.get('status', '')}"
    if binding.get("default"):
        line += f"; default {binding.get('output', '')} <= {binding.get('default', '')}"
    if binding.get("source_method"):
        line += f"; source method {binding.get('source_method', '')}"
    return sanitize(line)


def extract_response_status_bindings(on_input: str, functions: str, output_fields: list[str]) -> list[OrderedDict[str, str]]:
    result: list[OrderedDict[str, str]] = []
    for output_field, method_name in find_output_method_assignments(on_input, output_fields).items():
        method_body = find_method_body(functions, method_name)
        default_value, values = parse_status_return_mapping(method_body)
        for status_code, output_value in values:
            binding: OrderedDict[str, str] = OrderedDict(
                [
                    ("output", sanitize(output_field)),
                    ("status", sanitize(status_code)),
                    ("value", sanitize(output_value)),
                    ("default", sanitize(default_value)),
                    ("source_method", sanitize(method_name)),
                    ("supported", "no"),
                ]
            )
            binding["mapping_line"] = status_binding_visible_line(binding)
            result.append(binding)
    return result


def row_binding_line(binding: OrderedDict[str, str]) -> str:
    if not binding:
        return ""
    return (
        f"mode={binding.get('mode', '')} fields={binding.get('fields', '')} "
        f"supported={binding.get('supported', '')} condition={binding.get('condition', '')}"
    )


def status_binding_line(binding: OrderedDict[str, str]) -> str:
    line = (
        f"output={binding.get('output', '')} status={binding.get('status', '')} "
        f"value={binding.get('value', '')}"
    )
    if binding.get("default"):
        line += f" default={binding.get('default', '')}"
    if binding.get("source_method"):
        line += f" source_method={binding.get('source_method', '')}"
    line += f" supported={binding.get('supported', '')}"
    return line


def adapter_row_binding_line(call: ApiCall) -> str:
    if not call.row_invocation_binding:
        return ""
    return (
        f"required=yes join_key={ADAPTER_ROW_BINDING_JOIN_KEY} native_stable_row_id=not_proven "
        f"no_on_1_equals_1=yes invocation_mode={call.row_invocation_binding.get('mode', '')} "
        f"invocation_fields={call.row_invocation_binding.get('fields', '')} "
        f"supported={call.row_invocation_binding.get('supported', '')} "
        f"condition={call.row_invocation_binding.get('condition', '')}"
    )


def contract_value(value: str | None) -> str:
    return sanitize(value or "", limit=260).replace(";", r"\;")


def contract_line(name: str, fields: OrderedDict[str, str]) -> str:
    return name + " " + "; ".join(f"{key}={contract_value(value)}" for key, value in fields.items())


def call_requires_adapter_contract(call: ApiCall) -> bool:
    return bool(
        call.row_invocation_binding
        or call.http_status_output_bindings
        or call.request_fields
        or is_soap_call(call)
        or call.post_api_expressions
    )


def is_soap_call(call: ApiCall) -> bool:
    marker = " ".join(
        [
            call.protocol,
            call.body_preview,
            call.action,
            call.soap_action,
            call.content_type,
        ]
    ).lower()
    return "soap" in marker or "envelope" in marker


def is_simple_equals_condition(condition: str) -> bool:
    patterns = [
        r'^"((?:\\.|[^"])*)"\s*\.\s*equals\s*\(\s*[A-Za-z_][A-Za-z0-9_]*\s*\)$',
        r'^[A-Za-z_][A-Za-z0-9_]*\s*\.\s*equals\s*\(\s*"((?:\\.|[^"])*)"\s*\)$',
        r'^[A-Za-z_][A-Za-z0-9_]*\s*(?:==|=)\s*"((?:\\.|[^"])*)"$',
    ]
    trimmed = clean(condition)
    return any(re.match(pattern, trimmed) for pattern in patterns)


def row_api_udf_adapter_supported(call: ApiCall) -> bool:
    return (
        call.execution_status == "FDL_ADAPTER_REQUIRED"
        and call.method.upper() == "POST"
        and is_soap_call(call)
        and bool(call.url)
        and bool(call.body_preview)
        and call.row_invocation_binding.get("supported") == "yes"
        and is_simple_equals_condition(call.row_invocation_binding.get("condition", ""))
        and call.http_status_output_adapter_supported
        and len(call.http_status_output_bindings) == 1
    )


def adapter_contract_lines(call: ApiCall) -> list[str]:
    row_api_udf_supported = row_api_udf_adapter_supported(call)
    lines: list[str] = [
        contract_line(
            "FDL_API_NATIVE_CONTRACT",
            OrderedDict(
                [
                    ("status", call.execution_status),
                    ("nativeNode", "ApiReadNode" if call.execution_status == "FDL_TRANSLATION_READY" else "not_generated"),
                    ("adapterNode", "SparkSqlNode+SparkUDF" if row_api_udf_supported else "not_generated"),
                    ("adapter", ROW_API_ADAPTER_NODE if row_api_udf_supported else ""),
                    ("function", ROW_API_UDF_FUNCTION if row_api_udf_supported else ""),
                    ("providerFunction", ROW_API_UDF_PROVIDER_FUNCTION if row_api_udf_supported else ""),
                    ("runtimeValidationRequired", "yes" if row_api_udf_supported else "no"),
                    ("method", call.method),
                    ("protocol", call.protocol),
                    ("adapterRequired", "yes" if call_requires_adapter_contract(call) else "no"),
                    ("rowContextKey", ADAPTER_ROW_BINDING_JOIN_KEY),
                    ("noOnOneEqualsOne", "yes"),
                ]
            ),
        )
    ]
    if row_api_udf_supported:
        lines.append(
            contract_line(
                "FDL_ROW_API_UDF",
                OrderedDict(
                    [
                        ("adapter", ROW_API_ADAPTER_NODE),
                        ("function", ROW_API_UDF_FUNCTION),
                        ("providerFunction", ROW_API_UDF_PROVIDER_FUNCTION),
                        ("generated", "yes"),
                        ("executionPath", "DBRead->SparkSqlNode(row_api_adapter)->SparkSqlNode(post_expression)->DBWrite"),
                        ("runtimeValidationRequired", "yes"),
                        ("noOnOneEqualsOne", "yes"),
                    ]
                ),
            )
        )
    for input_field, source in call.request_fields.items():
        lines.append(
            contract_line(
                "FDL_API_REQUEST_BINDING",
                OrderedDict(
                    [
                        ("input", input_field),
                        ("source", source),
                        ("binding", "upstream-field"),
                        ("rowContextKey", ADAPTER_ROW_BINDING_JOIN_KEY),
                        ("required", "yes"),
                    ]
                ),
            )
        )
    if call.row_invocation_binding:
        lines.append(
            contract_line(
                "FDL_API_ROW_CONDITION",
                OrderedDict(
                    [
                        ("mode", call.row_invocation_binding.get("mode", "")),
                        ("fields", call.row_invocation_binding.get("fields", "")),
                        ("supported", call.row_invocation_binding.get("supported", "")),
                        ("rowContextKey", ADAPTER_ROW_BINDING_JOIN_KEY),
                        ("condition", call.row_invocation_binding.get("condition", "")),
                    ]
                ),
            )
        )
    for binding in call.http_status_output_bindings:
        lines.append(
            contract_line(
                "FDL_API_HTTP_STATUS_OUTPUT",
                OrderedDict(
                    [
                        ("output", binding.get("output", "")),
                        ("target", call.response_fields.get(binding.get("output", ""), "")),
                        ("status", binding.get("status", "")),
                        ("value", binding.get("value", "")),
                        ("default", binding.get("default", "")),
                        ("sourceMethod", binding.get("source_method", "")),
                        ("supported", binding.get("supported", "")),
                    ]
                ),
            )
        )
    if is_soap_call(call):
        lines.append(
            contract_line(
                "FDL_API_SOAP_BODY",
                OrderedDict(
                    [
                        ("bodyType", call.body_type),
                        ("contentType", call.content_type),
                        ("action", call.action),
                        ("soapAction", call.soap_action),
                        ("soapActionSource", call.soap_action_source),
                        ("bodyTemplate", "preserved-in-adapter-contract"),
                        ("requestParamMode", "upstream-field-driven"),
                    ]
                ),
            )
        )
    if call.post_api_expressions:
        lines.append(
            contract_line(
                "FDL_API_POST_PROJECTION",
                OrderedDict(
                    [
                        ("rowBinding", "inline-row-transform" if row_api_udf_supported else "required"),
                        ("joinKey", "" if row_api_udf_supported else ADAPTER_ROW_BINDING_JOIN_KEY),
                        ("nativeStableRowId", "not_required" if row_api_udf_supported else "not_proven"),
                        ("noOnOneEqualsOne", "yes"),
                    ]
                ),
            )
        )
    return lines


def binding_evidence_for_output(call: ApiCall, output_field: str) -> str:
    parts: list[str] = []
    if call.row_invocation_binding:
        parts.append("ROW_INVOCATION_BINDING " + row_binding_line(call.row_invocation_binding))
        parts.append("FDL_ADAPTER_ROW_BINDING " + adapter_row_binding_line(call))
    for binding in call.http_status_output_bindings:
        if binding.get("output", "").upper() == output_field.upper():
            parts.append("HTTP_STATUS_OUTPUT_BINDING " + status_binding_line(binding))
    parts.extend(adapter_contract_lines(call))
    return "; ".join(parts)



def condition_fields(condition: str) -> list[str]:
    scrubbed = re.sub(r'"(?:\\.|[^"\\])*"', " ", condition or "")
    scrubbed = re.sub(r"'(?:\\.|[^'\\])*'", " ", scrubbed)
    result: list[str] = []
    ignored = {"equals", "true", "false", "null", "if", "return", "string", "boolean", "this"}
    for match in re.finditer(r"\b[A-Za-z_][A-Za-z0-9_]*\b", scrubbed):
        token = match.group(0)
        if token.lower() in ignored:
            continue
        if token.upper() not in {item.upper() for item in result}:
            result.append(token)
    return result


def all_condition_fields_bound(call: ApiCall) -> bool:
    if not call.invocation_condition_fields:
        return False
    inputs = {field.upper() for field in call.request_fields}
    return all(field.upper() in inputs for field in call.invocation_condition_fields)


def all_status_mappings_target_outputs(call: ApiCall) -> bool:
    if not call.http_status_output_bindings:
        return False
    outputs = {field.upper() for field in call.response_fields}
    for binding in call.http_status_output_bindings:
        output_field = binding.get("output", "")
        if not output_field or output_field.upper() not in outputs:
            return False
    return True


def review_adapter_bindings(call: ApiCall, all_code: str) -> None:
    if call.invocation_condition:
        call.invocation_condition_fields = condition_fields(call.invocation_condition)
        call.row_invocation_binding = OrderedDict(
            [
                ("mode", "predicate-before-request"),
                ("condition", call.invocation_condition),
                ("fields", ",".join(call.invocation_condition_fields)),
                ("supported", "no"),
            ]
        )
        if not call.invocation_condition_fields:
            call.limitations.append(
                "Original Java row-level invocation condition could not be parsed into input fields; adapter binding generation is pending"
            )
        elif all_condition_fields_bound(call):
            call.row_invocation_adapter_supported = True
            call.row_invocation_binding["supported"] = "yes"
            call.adapter_metadata["rowInvocationMode"] = "predicate-before-request"
            call.adapter_metadata["rowInvocationFields"] = "; ".join(call.invocation_condition_fields)
        else:
            call.limitations.append(
                "Original Java row-level invocation condition references fields not bound to API input ports: "
                + ", ".join(call.invocation_condition_fields)
            )

    if "getResponseCode" in all_code or "statusCode" in all_code:
        if not call.http_status_output_bindings:
            call.limitations.append("Java maps HTTP status code to output fields; FDL/API adapter status mapping generation is pending")
        elif all_status_mappings_target_outputs(call):
            call.http_status_output_adapter_supported = True
            for binding in call.http_status_output_bindings:
                binding["supported"] = "yes"
            call.adapter_metadata["httpStatusOutputBinding"] = "parsed-from-java-getResponseCode"
        else:
            call.limitations.append("Parsed HTTP status-to-output mapping references fields that are not API output ports")


def parse_api_call(path: Path, session: ET.Element, mapping: ET.Element, transformation: ET.Element) -> ApiCall:
    snippets = OrderedDict((clean(item.get("NAME")), clean(item.get("VALUE"))) for item in transformation.iter("METADATAEXTENSION"))
    attrs = table_attributes(transformation)
    on_input = remove_line_comments(snippets.get("OnInputRow_Method_Snippet", ""))
    functions = remove_line_comments(snippets.get("Infa_Functions_Snippet", ""))
    all_code = f"{on_input}\n{functions}"
    assignments = collect_string_assignments(all_code)
    call_args = find_call_args(on_input)
    url = resolve_arg(call_args[0], assignments) if call_args else ""
    if not url:
        match = re.search(r"https?://[^\s\"'<>]+", all_code)
        url = match.group(0) if match else ""
    body = resolve_body(call_args, assignments, functions, all_code)
    headers = extract_headers(all_code, assignments)
    content_type = header_value(headers, "Content-Type")
    if not content_type:
        content_type = default_content_type(body)
        if content_type:
            headers["Content-Type"] = content_type
    soap_action_source = call_args[2] if len(call_args) > 2 else ""
    soap_action = resolve_arg(soap_action_source, assignments) if soap_action_source else header_value(headers, "SOAPAction")
    if soap_action is None:
        soap_action = ""
    if len(call_args) > 2:
        for header_name in list(headers.keys()):
            if header_name.lower() == "soapaction":
                del headers[header_name]
        headers["SOAPAction"] = soap_action
    elif looks_like_soap(body) and not any(name.lower() == "soapaction" for name in headers):
        headers["SOAPAction"] = soap_action
    action = soap_action or extract_soap_operation(body)
    body_type = detect_body_type(body, content_type)
    protocol = detect_protocol(url or "", body, headers, clean(transformation.get("TYPE")), clean(transformation.get("TEMPLATENAME")))
    java_class = find_named_metadata(snippets, "class") or find_named_metadata(attrs, "class")
    java_method = find_named_metadata(snippets, "method") or find_named_metadata(attrs, "method")
    call = ApiCall(
        xml_file=path.name,
        session=clean(session.get("NAME")),
        mapping=clean(mapping.get("NAME")),
        transformation=clean(transformation.get("NAME")),
        transformation_type=clean(transformation.get("TYPE")),
        template=clean(transformation.get("TEMPLATENAME")),
        method=extract_method(all_code, body),
        url=sanitize(url),
        action=sanitize(action),
        soap_action=sanitize(soap_action),
        soap_action_source=sanitize(soap_action_source),
        invocation_condition=sanitize(extract_condition(on_input)),
        protocol=protocol,
        java_class=sanitize(java_class),
        java_method=sanitize(java_method),
        body_type=body_type,
        content_type=sanitize(content_type),
        body_preview=sanitize(body),
        adapter_metadata=collect_adapter_metadata(snippets, attrs),
    )
    for field in transformation.iter("TRANSFORMFIELD"):
        field_name = clean(field.get("NAME"))
        port_type = clean(field.get("PORTTYPE")).upper()
        if not field_name:
            continue
        if "INPUT" in port_type:
            call.request_fields[field_name] = sanitize(incoming_source(mapping, call.transformation, field_name))
        if "OUTPUT" in port_type:
            targets = output_targets(mapping, call.transformation, field_name)
            call.response_fields[field_name] = sanitize(", ".join(targets))
            if not targets:
                call.limitations.append(f"Output field {field_name} has no parsed target connector")
    call.http_status_output_bindings = extract_response_status_bindings(on_input, functions, list(call.response_fields.keys()))
    call.response_status_mappings = [binding["mapping_line"] for binding in call.http_status_output_bindings]
    review_adapter_bindings(call, all_code)
    if not call.url:
        call.limitations.append("URL was not parsed")
    if not call.protocol:
        call.limitations.append("Executable protocol could not be derived; configure an adapter before running")
    if call.method != "GET" and not body:
        call.limitations.append("Request body was not parsed")
    classify_call(call)
    if row_api_udf_adapter_supported(call):
        call.execution_reason = (
            "plugin generates Spark UDF row API adapter for parsed row-level invocation and HTTP status output bindings; "
            "FDL runtime validation is still required"
        )
    call.post_api_expressions = find_post_api_expressions(mapping, call)
    return call


def audit_file(path: Path) -> list[ApiCall]:
    root = load_xml(path)
    mappings = {clean(mapping.get("NAME")): mapping for mapping in root.iter("MAPPING")}
    calls: list[ApiCall] = []
    for session in root.iter("SESSION"):
        mapping_name = clean(session.get("MAPPINGNAME"))
        mapping = mappings.get(mapping_name)
        if mapping is None:
            continue
        for transformation in children(mapping, "TRANSFORMATION"):
            if is_external_transformation(transformation):
                calls.append(parse_api_call(path, session, mapping, transformation))
    return calls


def resolve_files(args: argparse.Namespace) -> list[Path]:
    if args.xml:
        return [Path(item) for item in args.xml]
    return sorted(path for path in args.xml_dir.iterdir() if path.suffix.lower() == ".xml")


def print_call(call: ApiCall) -> None:
    print(f"  SESSION {call.session} / MAPPING {call.mapping}")
    print(f"    TRANSFORMATION {call.transformation} [{call.transformation_type} / {call.template}]")
    print(f"    STATUS {call.execution_status} ({call.execution_reason})")
    print(f"    METHOD {call.method}")
    print(f"    PROTOCOL {call.protocol}")
    print(f"    URL {call.url}")
    if call.java_class:
        print(f"    JAVA_CLASS {call.java_class}")
    if call.java_method:
        print(f"    JAVA_METHOD {call.java_method}")
    print(f"    ACTION {call.action}")
    print(f"    SOAPAction {call.soap_action} (source: {call.soap_action_source})")
    if call.invocation_condition:
        fields = f" fields={','.join(call.invocation_condition_fields)}" if call.invocation_condition_fields else ""
        print(f"    INVOCATION_CONDITION {call.invocation_condition}{fields}")
    if call.row_invocation_binding:
        print(f"    ROW_INVOCATION_BINDING {row_binding_line(call.row_invocation_binding)}")
    print(f"    BODY {call.body_type} {call.body_preview}")
    if call.request_fields:
        for field_name, source in call.request_fields.items():
            print(f"    REQUEST_FIELD {field_name} <= {source}")
    if call.response_fields:
        for field_name, target in call.response_fields.items():
            print(f"    RESPONSE_FIELD {field_name} -> {target}")
    for mapping in call.response_status_mappings:
        print(f"    STATUS_MAPPING {mapping}")
    for binding in call.http_status_output_bindings:
        print(f"    HTTP_STATUS_OUTPUT_BINDING {status_binding_line(binding)}")
    if call.adapter_metadata:
        for key, value in call.adapter_metadata.items():
            print(f"    ADAPTER_METADATA {key}={value}")
    if call.row_invocation_adapter_supported or call.http_status_output_adapter_supported:
        bindings: list[str] = []
        if call.row_invocation_adapter_supported:
            bindings.append("row-level invocation condition")
        if call.http_status_output_adapter_supported:
            bindings.append("HTTP status-code output binding")
        print(f"    FDL_API_PARSED_BINDINGS {'; '.join(bindings)}")
    row_binding = adapter_row_binding_line(call)
    if row_binding:
        print(f"    FDL_ADAPTER_ROW_BINDING {row_binding}")
    for line in adapter_contract_lines(call):
        print(f"    {line}")
    for expression in call.post_api_expressions:
        print(
            "    POST_API_EXPRESSION {status} {expression} -> {targets}".format(
                status=expression.get("status", ""),
                expression=expression.get("expression", ""),
                targets=expression.get("targets", ""),
            )
        )
        print(f"      DEPENDENCIES {expression.get('dependencies', '')}")
        print(f"      EVIDENCE {expression.get('evidence', '')}")
        if expression.get("row_binding"):
            print(f"      ROW_BINDING {expression.get('row_binding', '')}")
    if call.required_config:
        print(f"    REQUIRED_CONFIG {'; '.join(call.required_config)}")
    for limitation in call.limitations:
        print(f"    LIMITATION {limitation}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xml", nargs="*", help="Specific Informatica XML file(s) to audit.")
    parser.add_argument("--xml-dir", type=Path, default=DEFAULT_XML_DIR)
    args = parser.parse_args()

    exit_code = 0
    for path in resolve_files(args):
        calls = audit_file(path)
        status_counts = OrderedDict()
        for call in calls:
            status_counts[call.execution_status] = status_counts.get(call.execution_status, 0) + 1
        print(f"{path.name}: external_calls={len(calls)} statuses={dict(status_counts)}")
        for call in calls:
            print_call(call)
        if path.name == "WF_PRIP_Stage.XML" and not calls:
            exit_code = 1
            print("  ERROR expected external Java/SOAP/API call was not found")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
