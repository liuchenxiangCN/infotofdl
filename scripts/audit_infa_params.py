#!/usr/bin/env python3
"""Read-only Informatica scoped parameter and variable audit.

The script reads Informatica PowerCenter XML exports only. It does not start
FDL, does not connect to databases, and does not infer business meaning from
variable names. Defaults are reported only when the XML declares them.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter, OrderedDict, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable


DEFAULT_XML_DIR = Path("/mnt/d/项目文档/2-项目/百年人寿")
DEFAULT_OUTPUT_DIR = Path("/home/liufeng/code/bainianrenshou/infotofdl/docs/lineage-audit/param-audit")
DEFAULT_XML_FILES = [
    "cc_lis_20260521.xml",
    "WF_HNC.XML",
    "WF_PRIP_Stage.XML",
    "auditdb.xml",
]

KIND_MAPPING_VARIABLE = "MAPPING_VARIABLE"
KIND_MAPPING_PARAMETER = "MAPPING_PARAMETER"
KIND_WORKFLOW_VARIABLE = "WORKFLOW_VARIABLE"
KIND_BRACED_PARAMETER = "BRACED_PARAMETER"
KIND_PM_BUILTIN = "PM_BUILTIN"
KIND_SESSION_RUNTIME_ALIAS = "SESSION_RUNTIME_ALIAS"
KIND_TASK_STATUS = "TASK_STATUS"
KIND_DECISION_OUTPUT = "DECISION_OUTPUT"

DEFAULT_XML_VALUE = "XML_DEFAULT_VALUE"
DEFAULT_XML_EMPTY = "XML_DEFAULT_EMPTY"
DEFAULT_XML_MISSING = "XML_MISSING_PARAMETER_DEFAULT"
DEFAULT_RUNTIME_PLACEHOLDER = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
DEFAULT_PARAMETER_FILE_REFERENCE = "PARAMETER_FILE_REFERENCE"

STATUS_RESOLVED_IN_XML_SCOPE = "RESOLVED_IN_XML_SCOPE"
STATUS_RESOLVED_IN_XML_UNIQUE_NAME = "RESOLVED_IN_XML_UNIQUE_NAME"
STATUS_AMBIGUOUS_MAPPING_SCOPE = "AMBIGUOUS_MAPPING_SCOPE"
STATUS_AMBIGUOUS_WORKFLOW_SCOPE = "AMBIGUOUS_WORKFLOW_SCOPE"
STATUS_UNDECLARED_XML_REFERENCE = "UNDECLARED_XML_REFERENCE"
STATUS_RUNTIME_PLACEHOLDER = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
STATUS_PARAMETER_FILE_REFERENCE = "PARAMETER_FILE_REFERENCE"

DECLARATION_TAGS = {"MAPPINGVARIABLE", "WORKFLOWVARIABLE"}
SENSITIVE_RE = re.compile(r"(?i)(password|passwd|pwd|token|cookie|license|secret)(\s*[=:]\s*)[^\s&\"'<>]+")
SENSITIVE_WORDS = ("password", "passwd", "pwd", "token", "cookie", "license", "secret")
REF_RE = re.compile(
    r"\$\$[A-Za-z_][A-Za-z0-9_]*"
    r"|\$\{[^}\s\"'<>]+\}"
    r"|\$PM[A-Za-z_][A-Za-z0-9_]*"
    r"|\$[^\s\"'<>;,()=!&|\\/]+(?:\.[^\s\"'<>;,()=!&|\\/]+)*"
)

VARIABLE_FIELDS = [
    "xml_file",
    "kind",
    "scope",
    "scope_type",
    "scope_name",
    "workflow",
    "worklet",
    "session",
    "mapping",
    "name",
    "datatype",
    "default_value",
    "default_source",
    "persistent",
    "description",
    "is_parameter",
    "user_defined",
    "aggregation",
    "reference_count",
    "reference_locations",
    "resolution_status",
    "notes",
]

REFERENCE_FIELDS = [
    "xml_file",
    "reference",
    "reference_kind",
    "resolution_status",
    "resolved_kind",
    "resolved_scope",
    "line",
    "workflow",
    "worklet",
    "session",
    "mapping",
    "transformation",
    "task",
    "element",
    "attribute",
    "value_preview",
    "context_path",
    "replacement_contract",
    "required_binding",
]

PARAMETER_FILE_FIELDS = [
    "xml_file",
    "line",
    "workflow",
    "worklet",
    "session",
    "mapping",
    "scope",
    "value",
    "value_source",
    "resolution_status",
    "replacement_contract",
    "context_path",
]


@dataclass
class XmlDocument:
    path: Path
    root: ET.Element
    text: str
    parent_map: dict[int, ET.Element]
    line_by_id: dict[int, int]


@dataclass
class Context:
    workflow: str = ""
    worklet: str = ""
    session: str = ""
    mapping: str = ""
    transformation: str = ""
    task: str = ""


@dataclass
class VariableDecl:
    declaration_id: str
    xml_file: str
    kind: str
    scope_type: str
    scope_name: str
    workflow: str
    worklet: str
    session: str
    mapping: str
    name: str
    datatype: str
    default_value: str
    default_source: str
    persistent: str
    description: str
    is_parameter: str
    user_defined: str
    aggregation: str
    line: int = 0
    references: list[str] = field(default_factory=list)
    resolution_status: str = "DECLARED_IN_XML"
    notes: str = ""

    @property
    def scope(self) -> str:
        return f"{self.scope_type}:{self.scope_name}" if self.scope_name else self.scope_type

    def to_row(self) -> dict[str, str]:
        return {
            "xml_file": self.xml_file,
            "kind": self.kind,
            "scope": self.scope,
            "scope_type": self.scope_type,
            "scope_name": self.scope_name,
            "workflow": self.workflow,
            "worklet": self.worklet,
            "session": self.session,
            "mapping": self.mapping,
            "name": self.name,
            "datatype": self.datatype,
            "default_value": self.default_value,
            "default_source": self.default_source,
            "persistent": self.persistent,
            "description": self.description,
            "is_parameter": self.is_parameter,
            "user_defined": self.user_defined,
            "aggregation": self.aggregation,
            "reference_count": str(len(self.references)),
            "reference_locations": summarize_values(self.references, 20),
            "resolution_status": self.resolution_status,
            "notes": self.notes,
        }


@dataclass
class Reference:
    xml_file: str
    reference: str
    reference_kind: str
    resolution_status: str
    resolved_id: str
    resolved_kind: str
    resolved_scope: str
    line: int
    context: Context
    element: str
    attribute: str
    value_preview: str
    context_path: str

    def location_label(self) -> str:
        parts = [f"{self.xml_file}:{self.line or '?'}"]
        if self.context.session:
            parts.append(f"session={self.context.session}")
        if self.context.mapping:
            parts.append(f"mapping={self.context.mapping}")
        if self.context.workflow:
            parts.append(f"workflow={self.context.workflow}")
        if self.context.worklet:
            parts.append(f"worklet={self.context.worklet}")
        parts.append(f"{self.element}.{self.attribute}")
        return " ".join(parts)

    def replacement_contract(self) -> str:
        if self.resolution_status in {STATUS_RESOLVED_IN_XML_SCOPE, STATUS_RESOLVED_IN_XML_UNIQUE_NAME}:
            return f"XML_DECLARATION:{self.resolved_scope}:{self.reference}"
        if self.resolution_status == STATUS_PARAMETER_FILE_REFERENCE:
            return runtime_placeholder(self.reference_kind, self.reference)
        if self.resolution_status == STATUS_RUNTIME_PLACEHOLDER:
            return runtime_placeholder(self.reference_kind, self.reference)
        return f"FDL_GENERATION_RESEARCH_PENDING:{self.resolution_status}:{self.reference}"

    def required_binding(self) -> str:
        if self.resolution_status == STATUS_PARAMETER_FILE_REFERENCE:
            return f"parameter file value for {self.reference}"
        if self.resolution_status == STATUS_RUNTIME_PLACEHOLDER:
            return f"runtime adapter value for {self.reference}"
        if self.resolution_status.startswith("AMBIGUOUS"):
            return f"scope disambiguation for {self.reference}"
        if self.resolution_status == STATUS_UNDECLARED_XML_REFERENCE:
            return f"XML declaration or runtime binding for {self.reference}"
        return ""

    def to_row(self) -> dict[str, str]:
        return {
            "xml_file": self.xml_file,
            "reference": self.reference,
            "reference_kind": self.reference_kind,
            "resolution_status": self.resolution_status,
            "resolved_kind": self.resolved_kind,
            "resolved_scope": self.resolved_scope,
            "line": str(self.line or ""),
            "workflow": self.context.workflow,
            "worklet": self.context.worklet,
            "session": self.context.session,
            "mapping": self.context.mapping,
            "transformation": self.context.transformation,
            "task": self.context.task,
            "element": self.element,
            "attribute": self.attribute,
            "value_preview": self.value_preview,
            "context_path": self.context_path,
            "replacement_contract": self.replacement_contract(),
            "required_binding": self.required_binding(),
        }


@dataclass
class ParameterFileRef:
    xml_file: str
    line: int
    context: Context
    scope: str
    value: str
    value_source: str
    context_path: str

    def replacement_contract(self) -> str:
        if self.value_source == DEFAULT_PARAMETER_FILE_REFERENCE:
            return self.value
        return runtime_placeholder(KIND_BRACED_PARAMETER, self.scope)

    def to_row(self) -> dict[str, str]:
        return {
            "xml_file": self.xml_file,
            "line": str(self.line or ""),
            "workflow": self.context.workflow,
            "worklet": self.context.worklet,
            "session": self.context.session,
            "mapping": self.context.mapping,
            "scope": self.scope,
            "value": self.value,
            "value_source": self.value_source,
            "resolution_status": STATUS_PARAMETER_FILE_REFERENCE,
            "replacement_contract": self.replacement_contract(),
            "context_path": self.context_path,
        }


@dataclass
class AuditResult:
    variables: list[VariableDecl]
    references: list[Reference]
    parameter_files: list[ParameterFileRef]
    summary_by_xml: OrderedDict[str, dict[str, int]]


def clean(value: str | None) -> str:
    return (value or "").strip()


def normalize(value: str | None) -> str:
    return clean(value).upper()


def ordered_unique(values: Iterable[str]) -> list[str]:
    seen: OrderedDict[str, None] = OrderedDict()
    for value in values:
        value = clean(value)
        if value:
            seen.setdefault(value, None)
    return list(seen.keys())


def summarize_values(values: Iterable[str], limit: int) -> str:
    unique = ordered_unique(values)
    shown = unique[:limit]
    suffix = f"; ...(+{len(unique) - limit})" if len(unique) > limit else ""
    return "; ".join(shown) + suffix


def is_sensitive_context(*values: str | None) -> bool:
    text = " ".join(value or "" for value in values).lower()
    return any(word in text for word in SENSITIVE_WORDS)


def redact(value: str | None, *context: str | None) -> str:
    value = clean(value)
    if not value:
        return ""
    redacted = SENSITIVE_RE.sub(r"\1\2[REDACTED]", value)
    if is_sensitive_context(*context) and redacted == value:
        return "[REDACTED]"
    return redacted


def preview(value: str | None, *context: str | None, limit: int = 180) -> str:
    value = redact(value, *context)
    value = re.sub(r"\s+", " ", value).strip()
    if len(value) <= limit:
        return value
    return value[: limit - 3] + "..."


def runtime_placeholder(kind: str, reference: str) -> str:
    return f"${{INFA_RUNTIME:{clean(kind) or 'UNKNOWN_RUNTIME_REFERENCE'}:{clean(reference) or 'UNKNOWN'}}}"


def load_document(path: Path) -> XmlDocument:
    raw = path.read_bytes()
    text = ""
    for encoding in ("gb18030", "gbk", "utf-8-sig", "utf-8"):
        try:
            text = raw.decode(encoding)
            break
        except UnicodeDecodeError:
            continue
    if not text:
        raise RuntimeError(f"Unable to decode {path}")
    xml_text = re.sub(r"^\s*<\?xml[^>]*\?>", "", text, count=1)
    root = ET.fromstring(xml_text)
    parent_map = {id(child): parent for parent in root.iter() for child in list(parent)}
    line_by_id = assign_element_lines(root, text)
    return XmlDocument(path=path, root=root, text=text, parent_map=parent_map, line_by_id=line_by_id)


def assign_element_lines(root: ET.Element, text: str) -> dict[int, int]:
    lines = text.splitlines()
    result: dict[int, int] = {}
    cursor = 0
    for element in root.iter():
        marker = f"<{element.tag}"
        found = 0
        for index in range(cursor, len(lines)):
            if marker in lines[index]:
                found = index + 1
                cursor = index + 1
                break
        if not found:
            for index, line in enumerate(lines):
                if marker in line:
                    found = index + 1
                    break
        result[id(element)] = found
    return result


def ancestors(element: ET.Element, doc: XmlDocument) -> list[ET.Element]:
    chain: list[ET.Element] = []
    current: ET.Element | None = element
    while current is not None:
        chain.append(current)
        current = doc.parent_map.get(id(current))
    return chain


def nearest(element: ET.Element, doc: XmlDocument, tag: str) -> ET.Element | None:
    for candidate in ancestors(element, doc):
        if candidate.tag == tag:
            return candidate
    return None


def context_for(element: ET.Element, doc: XmlDocument) -> Context:
    workflow = nearest(element, doc, "WORKFLOW")
    worklet = nearest(element, doc, "WORKLET")
    session = nearest(element, doc, "SESSION")
    mapping = nearest(element, doc, "MAPPING")
    transformation = nearest(element, doc, "TRANSFORMATION")
    task = nearest(element, doc, "TASK")
    return Context(
        workflow=clean(workflow.get("NAME")) if workflow is not None else "",
        worklet=clean(worklet.get("NAME")) if worklet is not None else "",
        session=clean(session.get("NAME")) if session is not None else "",
        mapping=clean(mapping.get("NAME")) if mapping is not None else clean(session.get("MAPPINGNAME")) if session is not None else "",
        transformation=clean(transformation.get("NAME")) if transformation is not None else "",
        task=clean(task.get("NAME")) if task is not None else "",
    )


def element_label(element: ET.Element) -> str:
    for attr in ("NAME", "SINSTANCENAME", "DSQINSTNAME", "TRANSFORMATIONNAME", "MAPPINGNAME", "TYPE"):
        value = clean(element.get(attr))
        if value:
            return f"{element.tag}:{value}"
    return element.tag


def context_path(element: ET.Element, doc: XmlDocument) -> str:
    labels: list[str] = []
    for item in reversed(ancestors(element, doc)):
        if item.tag in {
            "FOLDER",
            "WORKFLOW",
            "WORKLET",
            "SESSION",
            "MAPPING",
            "TRANSFORMATION",
            "TASK",
            "ATTRIBUTE",
            "TABLEATTRIBUTE",
            "SESSIONEXTENSION",
            "SESSTRANSFORMATIONINST",
        }:
            labels.append(element_label(item))
    return " > ".join(labels[-10:])


def default_value_and_source(element: ET.Element) -> tuple[str, str]:
    if "DEFAULTVALUE" not in element.attrib:
        return DEFAULT_XML_MISSING, DEFAULT_XML_MISSING
    raw_value = clean(element.get("DEFAULTVALUE"))
    if raw_value == "":
        return '""', DEFAULT_XML_EMPTY
    return redact(raw_value, element.get("NAME")), DEFAULT_XML_VALUE


def declaration_kind(element: ET.Element) -> str:
    if element.tag == "MAPPINGVARIABLE":
        return KIND_MAPPING_PARAMETER if normalize(element.get("ISPARAM")) == "YES" else KIND_MAPPING_VARIABLE
    if element.tag == "WORKFLOWVARIABLE":
        return KIND_WORKFLOW_VARIABLE
    return element.tag


def declaration_scope(element: ET.Element, doc: XmlDocument, context: Context) -> tuple[str, str]:
    if element.tag == "MAPPINGVARIABLE":
        return "MAPPING", context.mapping
    worklet = nearest(element, doc, "WORKLET")
    if worklet is not None:
        return "WORKLET", clean(worklet.get("NAME"))
    workflow = nearest(element, doc, "WORKFLOW")
    if workflow is not None:
        return "WORKFLOW", clean(workflow.get("NAME"))
    return "WORKFLOW", context.workflow


def collect_variables(doc: XmlDocument) -> list[VariableDecl]:
    variables: list[VariableDecl] = []
    duplicate_count: Counter[str] = Counter()
    for element in doc.root.iter():
        if element.tag not in DECLARATION_TAGS:
            continue
        context = context_for(element, doc)
        scope_type, scope_name = declaration_scope(element, doc, context)
        name = clean(element.get("NAME"))
        default_value, default_source = default_value_and_source(element)
        base_id = f"{doc.path.name}|{scope_type}|{scope_name}|{declaration_kind(element)}|{name}"
        duplicate_count[base_id] += 1
        declaration_id = base_id if duplicate_count[base_id] == 1 else f"{base_id}|{duplicate_count[base_id]}"
        variables.append(
            VariableDecl(
                declaration_id=declaration_id,
                xml_file=doc.path.name,
                kind=declaration_kind(element),
                scope_type=scope_type,
                scope_name=scope_name,
                workflow=context.workflow,
                worklet=context.worklet,
                session=context.session,
                mapping=context.mapping,
                name=name,
                datatype=clean(element.get("DATATYPE")),
                default_value=default_value,
                default_source=default_source,
                persistent=clean(element.get("ISPERSISTENT")),
                description=redact(element.get("DESCRIPTION"), element.get("NAME")),
                is_parameter=clean(element.get("ISPARAM")),
                user_defined=clean(element.get("USERDEFINED")),
                aggregation=clean(element.get("AGGFUNCTION")),
                line=doc.line_by_id.get(id(element), 0),
            )
        )
    return variables


def reference_kind(reference: str) -> str:
    if reference.startswith("$$"):
        return KIND_MAPPING_VARIABLE
    if reference.startswith("${"):
        return KIND_BRACED_PARAMETER
    if reference.startswith("$PM"):
        return KIND_PM_BUILTIN
    if reference in {"$Source", "$Target"}:
        return KIND_SESSION_RUNTIME_ALIAS
    if reference.startswith("$") and "." in reference:
        suffix = reference.rsplit(".", 1)[-1]
        if suffix.lower() == "status":
            return KIND_TASK_STATUS
        if suffix.lower() == "condition":
            return KIND_DECISION_OUTPUT
    return KIND_WORKFLOW_VARIABLE


def is_builtin_reference(kind: str) -> bool:
    return kind in {KIND_PM_BUILTIN, KIND_SESSION_RUNTIME_ALIAS, KIND_TASK_STATUS, KIND_DECISION_OUTPUT}


def declaration_index(variables: list[VariableDecl]) -> dict[tuple[str, str, str, str], list[VariableDecl]]:
    index: dict[tuple[str, str, str, str], list[VariableDecl]] = defaultdict(list)
    for variable in variables:
        if variable.kind in {KIND_MAPPING_VARIABLE, KIND_MAPPING_PARAMETER}:
            index[(variable.xml_file, "MAPPING", variable.scope_name, variable.name)].append(variable)
        elif variable.kind == KIND_WORKFLOW_VARIABLE:
            index[(variable.xml_file, variable.scope_type, variable.scope_name, variable.name)].append(variable)
    return index


def resolve_reference(
    xml_file: str,
    reference: str,
    kind: str,
    context: Context,
    variables: list[VariableDecl],
    index: dict[tuple[str, str, str, str], list[VariableDecl]],
) -> tuple[str, str, str, str]:
    if kind == KIND_MAPPING_VARIABLE:
        candidates = index.get((xml_file, "MAPPING", context.mapping, reference), [])
        if len(candidates) == 1:
            variable = candidates[0]
            return variable.declaration_id, variable.kind, variable.scope, STATUS_RESOLVED_IN_XML_SCOPE
        all_candidates = [variable for variable in variables if variable.xml_file == xml_file and variable.name == reference]
        if len(all_candidates) == 1:
            variable = all_candidates[0]
            return variable.declaration_id, variable.kind, variable.scope, STATUS_RESOLVED_IN_XML_UNIQUE_NAME
        if len(all_candidates) > 1:
            scopes = summarize_values((variable.scope for variable in all_candidates), 8)
            return "", KIND_MAPPING_VARIABLE, scopes, STATUS_AMBIGUOUS_MAPPING_SCOPE
        return "", KIND_MAPPING_VARIABLE, "", STATUS_UNDECLARED_XML_REFERENCE

    if kind == KIND_WORKFLOW_VARIABLE:
        search_keys = []
        if context.worklet:
            search_keys.append((xml_file, "WORKLET", context.worklet, reference))
        if context.workflow:
            search_keys.append((xml_file, "WORKFLOW", context.workflow, reference))
        for key in search_keys:
            candidates = index.get(key, [])
            if len(candidates) == 1:
                variable = candidates[0]
                return variable.declaration_id, variable.kind, variable.scope, STATUS_RESOLVED_IN_XML_SCOPE
        all_candidates = [variable for variable in variables if variable.xml_file == xml_file and variable.name == reference]
        if len(all_candidates) == 1:
            variable = all_candidates[0]
            return variable.declaration_id, variable.kind, variable.scope, STATUS_RESOLVED_IN_XML_UNIQUE_NAME
        if len(all_candidates) > 1:
            scopes = summarize_values((variable.scope for variable in all_candidates), 8)
            return "", KIND_WORKFLOW_VARIABLE, scopes, STATUS_AMBIGUOUS_WORKFLOW_SCOPE
        return "", KIND_WORKFLOW_VARIABLE, "", STATUS_UNDECLARED_XML_REFERENCE

    if kind == KIND_BRACED_PARAMETER:
        return "", kind, "PARAMETER_FILE", STATUS_PARAMETER_FILE_REFERENCE

    if is_builtin_reference(kind):
        return "", kind, "INFORMATICA_RUNTIME", STATUS_RUNTIME_PLACEHOLDER

    return "", kind, "", STATUS_UNDECLARED_XML_REFERENCE


def iter_attribute_values(element: ET.Element) -> Iterable[tuple[str, str]]:
    for attr, value in element.attrib.items():
        yield attr, value
    if element.text and clean(element.text):
        yield "#text", element.text


def collect_references(doc: XmlDocument, variables: list[VariableDecl]) -> list[Reference]:
    refs: list[Reference] = []
    index = declaration_index(variables)
    seen: set[tuple[str, str, int, str, str, str]] = set()
    for element in doc.root.iter():
        context = context_for(element, doc)
        line = doc.line_by_id.get(id(element), 0)
        for attr, value in iter_attribute_values(element):
            if element.tag in DECLARATION_TAGS and attr == "NAME":
                continue
            for match in REF_RE.finditer(value or ""):
                reference = match.group(0).rstrip("\\/")
                if not reference or reference == "$":
                    continue
                kind = reference_kind(reference)
                resolved_id, resolved_kind, resolved_scope, status = resolve_reference(
                    doc.path.name,
                    reference,
                    kind,
                    context,
                    variables,
                    index,
                )
                key = (reference, kind, line, element_label(element), attr, preview(value, attr))
                if key in seen:
                    continue
                seen.add(key)
                refs.append(
                    Reference(
                        xml_file=doc.path.name,
                        reference=reference,
                        reference_kind=kind,
                        resolution_status=status,
                        resolved_id=resolved_id,
                        resolved_kind=resolved_kind,
                        resolved_scope=resolved_scope,
                        line=line,
                        context=context,
                        element=element_label(element),
                        attribute=attr,
                        value_preview=preview(value, attr),
                        context_path=context_path(element, doc),
                    )
                )
    return refs


def collect_parameter_files(doc: XmlDocument) -> list[ParameterFileRef]:
    refs: list[ParameterFileRef] = []
    for element in doc.root.iter():
        if element.tag not in {"ATTRIBUTE", "TABLEATTRIBUTE"}:
            continue
        if clean(element.get("NAME")).lower() != "parameter filename":
            continue
        context = context_for(element, doc)
        value = redact(element.get("VALUE"), element.get("NAME"))
        value_source = DEFAULT_PARAMETER_FILE_REFERENCE if value else DEFAULT_XML_EMPTY
        if not value:
            value = '""'
        scope_parts = []
        if context.session:
            scope_parts.append(f"SESSION:{context.session}")
        if context.workflow:
            scope_parts.append(f"WORKFLOW:{context.workflow}")
        if context.worklet:
            scope_parts.append(f"WORKLET:{context.worklet}")
        refs.append(
            ParameterFileRef(
                xml_file=doc.path.name,
                line=doc.line_by_id.get(id(element), 0),
                context=context,
                scope=" / ".join(scope_parts) or "UNKNOWN_SCOPE",
                value=value,
                value_source=value_source,
                context_path=context_path(element, doc),
            )
        )
    return refs


def attach_reference_locations(variables: list[VariableDecl], references: list[Reference]) -> None:
    by_id = {variable.declaration_id: variable for variable in variables}
    for reference in references:
        if reference.resolved_id and reference.resolved_id in by_id:
            by_id[reference.resolved_id].references.append(reference.location_label())


def build_reference_only_variables(references: list[Reference]) -> list[VariableDecl]:
    grouped: OrderedDict[tuple[str, str, str, str], list[Reference]] = OrderedDict()
    for reference in references:
        if reference.resolved_id:
            continue
        if reference.resolution_status.startswith("AMBIGUOUS"):
            default_value = f"FDL_GENERATION_RESEARCH_PENDING:{reference.resolution_status}"
        elif reference.resolution_status in {STATUS_RUNTIME_PLACEHOLDER, STATUS_PARAMETER_FILE_REFERENCE}:
            default_value = runtime_placeholder(reference.reference_kind, reference.reference)
        else:
            default_value = DEFAULT_XML_MISSING
        grouped.setdefault((reference.xml_file, reference.reference_kind, reference.reference, default_value), []).append(reference)

    variables: list[VariableDecl] = []
    for (xml_file, kind, name, default_value), refs in grouped.items():
        first = refs[0]
        runtime_like = first.resolution_status in {STATUS_RUNTIME_PLACEHOLDER, STATUS_PARAMETER_FILE_REFERENCE}
        scope_type = "RUNTIME" if runtime_like else "UNDECLARED"
        scope_name = "INFORMATICA_RUNTIME" if first.resolution_status == STATUS_RUNTIME_PLACEHOLDER else "PARAMETER_FILE" if first.resolution_status == STATUS_PARAMETER_FILE_REFERENCE else "XML_REFERENCE"
        default_source = first.resolution_status if runtime_like else DEFAULT_XML_MISSING
        variable = VariableDecl(
            declaration_id=f"{xml_file}|{scope_type}|{scope_name}|{kind}|{name}",
            xml_file=xml_file,
            kind=f"REFERENCE_ONLY_{kind}",
            scope_type=scope_type,
            scope_name=scope_name,
            workflow="",
            worklet="",
            session="",
            mapping="",
            name=name,
            datatype=default_source,
            default_value=default_value,
            default_source=default_source,
            persistent=default_source,
            description="Reference has no explicit MappingVariable/WorkflowVariable declaration in the XML.",
            is_parameter="",
            user_defined="",
            aggregation="",
            references=[ref.location_label() for ref in refs],
            resolution_status=first.resolution_status,
            notes="Do not infer runtime value from the reference name; implement scoped resolver/runtime binding.",
        )
        variables.append(variable)
    return variables


def summarize_by_xml(
    xml_files: list[Path],
    variables: list[VariableDecl],
    references: list[Reference],
    parameter_files: list[ParameterFileRef],
) -> OrderedDict[str, dict[str, int]]:
    summary: OrderedDict[str, dict[str, int]] = OrderedDict()
    for path in xml_files:
        name = path.name
        declared = [variable for variable in variables if variable.xml_file == name and not variable.kind.startswith("REFERENCE_ONLY")]
        refs = [reference for reference in references if reference.xml_file == name]
        params = [item for item in parameter_files if item.xml_file == name]
        summary[name] = {
            "mapping_variables": sum(1 for variable in declared if variable.kind in {KIND_MAPPING_VARIABLE, KIND_MAPPING_PARAMETER}),
            "workflow_variables": sum(1 for variable in declared if variable.kind == KIND_WORKFLOW_VARIABLE),
            "declared_variables": len(declared),
            "references": len(refs),
            "resolved_references": sum(1 for ref in refs if ref.resolved_id),
            "runtime_references": sum(1 for ref in refs if ref.resolution_status == STATUS_RUNTIME_PLACEHOLDER),
            "unresolved_references": sum(1 for ref in refs if ref.resolution_status == STATUS_UNDECLARED_XML_REFERENCE),
            "ambiguous_references": sum(1 for ref in refs if ref.resolution_status.startswith("AMBIGUOUS")),
            "parameter_file_attrs": len(params),
            "parameter_file_non_empty": sum(1 for item in params if item.value_source == DEFAULT_PARAMETER_FILE_REFERENCE),
        }
    return summary


def audit_xml_files(xml_files: list[Path]) -> AuditResult:
    declared_variables: list[VariableDecl] = []
    references: list[Reference] = []
    parameter_files: list[ParameterFileRef] = []
    for xml_file in xml_files:
        doc = load_document(xml_file)
        doc_variables = collect_variables(doc)
        declared_variables.extend(doc_variables)
        references.extend(collect_references(doc, doc_variables))
        parameter_files.extend(collect_parameter_files(doc))
    attach_reference_locations(declared_variables, references)
    variables = declared_variables + build_reference_only_variables(references)
    summary = summarize_by_xml(xml_files, variables, references, parameter_files)
    return AuditResult(
        variables=variables,
        references=references,
        parameter_files=parameter_files,
        summary_by_xml=summary,
    )


def write_csv(path: Path, fields: list[str], rows: Iterable[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as fp:
        writer = csv.DictWriter(fp, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def write_json(path: Path, result: AuditResult) -> None:
    payload = OrderedDict(
        [
            ("generated_by", "scripts/audit_infa_params.py"),
            ("read_only", True),
            ("default_policy", "XML defaults only; missing defaults are XML_MISSING_PARAMETER_DEFAULT; runtime-only references stay XML_SEMANTICS_PARSED_TRANSLATION_PENDING; ${...}/parameter-file references use PARAMETER_FILE_REFERENCE."),
            ("resolver_vocabulary", {
                "xml_default_value": DEFAULT_XML_VALUE,
                "xml_default_empty": DEFAULT_XML_EMPTY,
                "xml_missing_parameter_default": DEFAULT_XML_MISSING,
                "runtime_placeholder": STATUS_RUNTIME_PLACEHOLDER,
                "parameter_file_reference": STATUS_PARAMETER_FILE_REFERENCE,
            }),
            ("summary_by_xml", result.summary_by_xml),
            ("variables", [variable.to_row() for variable in result.variables]),
            ("references", [reference.to_row() for reference in result.references]),
            ("parameter_files", [item.to_row() for item in result.parameter_files]),
        ]
    )
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def markdown_escape(value: str) -> str:
    value = value.replace("\n", " ")
    return value.replace("|", "\\|")


def write_markdown(path: Path, result: AuditResult) -> None:
    lines: list[str] = [
        "# 参数/变量作用域离线审计",
        "",
        "生成方式：",
        "",
        "```bash",
        "cd /home/liufeng/code/bainianrenshou/infotofdl",
        "python3 scripts/audit_infa_params.py /mnt/d/项目文档/2-项目/百年人寿",
        "```",
        "",
        "本审计只读取 Informatica XML，不启动 FDL，不连接数据库，不修改 FDL 运行目录。",
        "",
        "## 口径",
        "",
        "- 只输出 XML 中声明的 `MAPPINGVARIABLE` / `WORKFLOWVARIABLE` 和属性值中的变量引用位置。",
        "- 不按变量名猜 `sysdate`、业务日期、连接或环境含义。",
        "- XML 有 `DEFAULTVALUE` 时按 XML 输出；空字符串显示为 `\"\"`，缺失显示为 `XML_MISSING_PARAMETER_DEFAULT`。",
        "- `$PM...`、`$Source`、`$Target`、`.Status/.Condition` 等运行期/内置引用标为 `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`。",
        "- `${...}` 和 `Parameter Filename` 标为 `PARAMETER_FILE_REFERENCE`，只作为外部参数文件绑定合同，不猜实际值。",
        "",
        "## 输出文件",
        "",
        "- `params.json`：稳定 JSON，包含变量声明、引用位置和参数文件属性。",
        "- `params.csv`：变量/参数声明与 reference-only 运行期引用聚合。",
        "- `param_references.csv`：每个引用位置一行，包含 Session/Mapping/Workflow 上下文、replacement contract 和 required binding。",
        "- `parameter_files.csv`：`Parameter Filename` 属性位置、值和 replacement contract。",
        "",
        "## XML 覆盖摘要",
        "",
        "| XML | Mapping variables | Workflow variables | References | Resolved | Runtime pending | Unresolved | Ambiguous | Parameter file attrs | Non-empty parameter file |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for xml_file, values in result.summary_by_xml.items():
        lines.append(
            "| {xml} | {mapping_variables} | {workflow_variables} | {references} | {resolved_references} | "
            "{runtime_references} | {unresolved_references} | {ambiguous_references} | {parameter_file_attrs} | "
            "{parameter_file_non_empty} |".format(xml=markdown_escape(xml_file), **values)
        )

    unresolved = [
        reference
        for reference in result.references
        if reference.resolution_status in {STATUS_UNDECLARED_XML_REFERENCE, STATUS_RUNTIME_PLACEHOLDER, STATUS_PARAMETER_FILE_REFERENCE}
        or reference.resolution_status.startswith("AMBIGUOUS")
    ]
    grouped: Counter[tuple[str, str, str]] = Counter((ref.xml_file, ref.reference, ref.resolution_status) for ref in unresolved)
    lines.extend(
        [
            "",
            "## 待 Java resolver 处理的引用类型",
            "",
            "| XML | Reference | Status | Count |",
            "|---|---|---|---:|",
        ]
    )
    for (xml_file, reference, status), count in grouped.most_common(40):
        lines.append(f"| {markdown_escape(xml_file)} | `{markdown_escape(reference)}` | {status} | {count} |")

    non_empty_param_files = [item for item in result.parameter_files if item.value_source == DEFAULT_PARAMETER_FILE_REFERENCE]
    lines.extend(
        [
            "",
            "## 非空 Parameter Filename",
            "",
            "| XML | Scope | Value |",
            "|---|---|---|",
        ]
    )
    if non_empty_param_files:
        for item in non_empty_param_files:
            lines.append(f"| {markdown_escape(item.xml_file)} | {markdown_escape(item.scope)} | `{markdown_escape(item.value)}` |")
    else:
        lines.append("| - | - | - |")

    lines.extend(
        [
            "",
            "## 后续生产 Java 接入点",
            "",
            "1. 在 XML 解析层建立 `Mapping/Workflow/Worklet/Session` 作用域索引，按当前 Mapping/Session 解析 `$$...`，按 Workflow/Worklet 解析 `$Task.Status` / `$Decision.Condition`。",
            "2. 在 FDL 节点生成前增加 scoped param resolver，把 XML 默认值、空默认值、运行期内置引用和外部 parameter file 明确区分。",
            "3. 在条件线、SQL override、Expression、Filter、Lookup、API adapter contract 生成路径中统一调用 resolver，禁止按变量名猜业务含义。",
        ]
    )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_outputs(output_dir: Path, result: AuditResult) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    write_csv(output_dir / "params.csv", VARIABLE_FIELDS, (variable.to_row() for variable in result.variables))
    write_csv(output_dir / "param_references.csv", REFERENCE_FIELDS, (reference.to_row() for reference in result.references))
    write_csv(output_dir / "parameter_files.csv", PARAMETER_FILE_FIELDS, (item.to_row() for item in result.parameter_files))
    write_json(output_dir / "params.json", result)
    write_markdown(output_dir / "README.md", result)


def print_summary(result: AuditResult, output_dir: Path | None) -> None:
    print("# Informatica parameter/variable audit")
    print("read_only=true defaults=xml_only")
    for xml_file, values in result.summary_by_xml.items():
        print(
            "{xml}: mapping_variables={mapping_variables} workflow_variables={workflow_variables} "
            "references={references} resolved={resolved_references} runtime_pending={runtime_references} "
            "unresolved={unresolved_references} ambiguous={ambiguous_references} parameter_files={parameter_file_attrs}".format(
                xml=xml_file,
                **values,
            )
        )
    if output_dir is not None:
        print(f"wrote: {output_dir}")


def expand_xml_inputs(paths: list[Path], xml_dir: Path) -> list[Path]:
    if not paths:
        return [xml_dir / name for name in DEFAULT_XML_FILES]
    result: list[Path] = []
    for path in paths:
        if path.is_dir():
            defaults = [path / name for name in DEFAULT_XML_FILES if (path / name).exists()]
            if defaults:
                result.extend(defaults)
            else:
                result.extend(sorted(candidate for candidate in path.iterdir() if candidate.suffix.lower() == ".xml"))
        else:
            result.append(path)
    return ordered_paths(result)


def ordered_paths(paths: Iterable[Path]) -> list[Path]:
    seen: OrderedDict[str, Path] = OrderedDict()
    for path in paths:
        seen.setdefault(str(path), path)
    return list(seen.values())


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xml", nargs="*", type=Path, help="XML files or directories to audit.")
    parser.add_argument("--xml-dir", type=Path, default=DEFAULT_XML_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--no-write", action="store_true", help="Print summary without writing JSON/CSV/Markdown files.")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    xml_files = expand_xml_inputs(args.xml, args.xml_dir)
    missing = [path for path in xml_files if not path.exists()]
    if missing:
        for path in missing:
            print(f"missing XML: {path}", file=sys.stderr)
        return 2
    result = audit_xml_files(xml_files)
    output_dir = None if args.no_write else args.output_dir
    if output_dir is not None:
        write_outputs(output_dir, result)
    print_summary(result, output_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
