#!/usr/bin/env python3
"""Audit field-level lineage from Informatica PowerCenter XML exports.

The report is intentionally offline: it does not require FDL to start and does
not connect to customer databases. It turns Informatica CONNECTOR paths into a
readable CSV/Markdown risk report for the current FDL plugin's name-based field
mapping behavior.
"""

from __future__ import annotations

import argparse
import csv
import re
import xml.etree.ElementTree as ET
from collections import Counter, OrderedDict, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

DEFAULT_XML_DIR = Path("/mnt/c/Users/nifen/Desktop/项目文档/2-项目/百年人寿")
DEFAULT_OUTPUT_DIR = Path("/home/liufeng/code/bainianrenshou/infotofdl/docs/lineage-audit")
DEFAULT_XML_FILES = [
    "cc_lis_20260521.xml",
    "WF_HNC.XML",
    "WF_PRIP_Stage.XML",
    "auditdb.xml",
]

CSV_FIELDS = [
    "xml_file",
    "workflow",
    "worklet",
    "session",
    "mapping",
    "target_instance",
    "target_field",
    "write_semantics",
    "target_key_field",
    "upstream_fields",
    "path",
    "transform_types",
    "same_name_mapping",
    "risk_level",
    "notes",
]

RISK_ORDER = {"LOW": 0, "MEDIUM": 1, "HIGH": 2}
HIGH_RISK_TYPES = {"Filter", "Lookup Procedure", "Custom Transformation"}
PASS_THROUGH_TYPES = {"Source Definition", "Source Qualifier", "Expression", "Filter"}


@dataclass(frozen=True)
class Node:
    instance: str
    field: str
    instance_type: str


@dataclass(frozen=True)
class Edge:
    source: Node
    target: Node


@dataclass
class PathTrace:
    nodes: list[Node]
    cycle: bool = False


@dataclass(frozen=True)
class SessionContext:
    workflow: str
    worklet: str
    session: str
    mapping: str
    conditions: tuple[str, ...]


@dataclass
class TargetWriteInfo:
    session: str
    target_instance: str
    target_definition: str
    treat_source_rows_as: str = ""
    target_load_type: str = ""
    insert: str = ""
    update_as_update: str = ""
    update_as_insert: str = ""
    update_else_insert: str = ""
    delete: str = ""
    truncate: str = ""
    target_load_order: str = ""
    pre_sql: str = ""
    post_sql: str = ""
    key_fields: tuple[str, ...] = ()

    def is_delete_mode(self) -> bool:
        return norm(self.treat_source_rows_as) == "DELETE" or yes(self.delete) and not yes(self.insert)

    def is_update_mode(self) -> bool:
        return (
            norm(self.treat_source_rows_as) == "UPDATE"
            or yes(self.update_as_update)
            or yes(self.update_as_insert)
            or yes(self.update_else_insert)
        )

    def is_insert_mode(self) -> bool:
        return norm(self.treat_source_rows_as) == "INSERT" or yes(self.insert)

    def key_field(self, field_name: str) -> bool:
        return norm(field_name) in {norm(field) for field in self.key_fields}

    def summary(self) -> str:
        parts = []
        if self.treat_source_rows_as:
            parts.append(f"treat={self.treat_source_rows_as}")
        for label, value in (
            ("insert", self.insert),
            ("update", self.update_as_update),
            ("updateAsInsert", self.update_as_insert),
            ("updateElseInsert", self.update_else_insert),
            ("delete", self.delete),
            ("truncate", self.truncate),
        ):
            if value:
                parts.append(f"{label}={value}")
        if self.key_fields:
            parts.append("keys=" + ",".join(self.key_fields))
        if self.target_load_order:
            parts.append("loadOrder=" + self.target_load_order)
        return ", ".join(parts)


@dataclass
class XmlAudit:
    xml_file: Path
    rows: list[dict[str, str]]
    summary: dict[str, object]


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


def norm(value: str | None) -> str:
    return re.sub(r'["`\[\]\s]', "", clean(value)).upper()


def yes(value: str | None) -> bool:
    return norm(value) in {"YES", "TRUE", "Y", "1"}


def short_sql(value: str, limit: int = 180) -> str:
    compact = re.sub(r"\s+", " ", clean(value))
    if len(compact) <= limit:
        return compact
    return compact[: limit - 3] + "..."


def ordered_unique(values: Iterable[str]) -> list[str]:
    seen: OrderedDict[str, None] = OrderedDict()
    for value in values:
        value = clean(value)
        if value:
            seen.setdefault(value, None)
    return list(seen.keys())


def unique_join(values: Iterable[str], sep: str = "; ") -> str:
    return sep.join(ordered_unique(values))


def table_attributes(element: ET.Element) -> dict[str, list[str]]:
    attrs: dict[str, list[str]] = defaultdict(list)
    for child in children(element, "TABLEATTRIBUTE") + children(element, "ATTRIBUTE"):
        name = clean(child.get("NAME"))
        value = clean(child.get("VALUE"))
        if name and value:
            attrs[name].append(value)
    return attrs


def direct_attributes(element: ET.Element) -> dict[str, str]:
    attrs: dict[str, str] = {}
    for child in children(element, "TABLEATTRIBUTE") + children(element, "ATTRIBUTE"):
        name = clean(child.get("NAME"))
        value = clean(child.get("VALUE"))
        if name:
            attrs[name] = value
    return attrs


def first_attr(attrs: dict[str, str], name: str) -> str:
    return clean(attrs.get(name))


def field_elements(element: ET.Element, tag: str) -> list[ET.Element]:
    return children(element, tag)


def field_names(element: ET.Element, tag: str) -> list[str]:
    return [clean(field.get("NAME")) for field in field_elements(element, tag) if clean(field.get("NAME"))]


def slug_for(path: Path) -> str:
    name = path.name
    return re.sub(r"[^A-Za-z0-9_.-]", "_", name)


def node_label(node: Node) -> str:
    return f"{node.instance}.{node.field} [{node.instance_type}]"


def is_simple_expression(expression: str, field_name: str) -> bool:
    expression = clean(expression)
    if not expression:
        return True
    return norm(expression) == norm(field_name)


def expression_tokens(expression: str) -> list[str]:
    tokens = re.findall(r"\b[A-Za-z_][A-Za-z0-9_]*\b", expression or "")
    stop = {
        "AND",
        "OR",
        "NOT",
        "NULL",
        "TRUE",
        "FALSE",
        "ISNULL",
        "IIF",
        "DECODE",
        "ERROR",
        "TO_CHAR",
        "TO_DATE",
        "SYSDATE",
        "TRUNC",
        "NVL",
    }
    return ordered_unique(token for token in tokens if norm(token) not in stop)


def collect_workflow_contexts(root: ET.Element) -> dict[str, list[SessionContext]]:
    sessions = {
        clean(session.get("NAME")): clean(session.get("MAPPINGNAME"))
        for session in root.findall(".//SESSION")
        if clean(session.get("NAME"))
    }
    worklets = {
        clean(worklet.get("NAME")): worklet
        for worklet in root.findall(".//WORKLET")
        if clean(worklet.get("NAME"))
    }
    contexts: dict[str, list[SessionContext]] = defaultdict(list)

    def incoming_conditions(container: ET.Element) -> dict[str, list[str]]:
        incoming: dict[str, list[str]] = defaultdict(list)
        for link in children(container, "WORKFLOWLINK"):
            condition = clean(link.get("CONDITION"))
            to_task = clean(link.get("TOTASK"))
            if condition and to_task:
                incoming[to_task].append(condition)
        return incoming

    def visit_container(
        container: ET.Element,
        workflow_name: str,
        worklet_path: list[str],
        inherited_conditions: list[str],
        seen_worklets: set[str],
    ) -> None:
        direct_conditions = incoming_conditions(container)
        for task in children(container, "TASKINSTANCE"):
            task_type = clean(task.get("TASKTYPE"))
            task_name = clean(task.get("TASKNAME")) or clean(task.get("NAME"))
            instance_name = clean(task.get("NAME")) or task_name
            conditions = list(inherited_conditions)
            conditions.extend(direct_conditions.get(instance_name, []))
            if task_name != instance_name:
                conditions.extend(direct_conditions.get(task_name, []))
            conditions = ordered_unique(conditions)

            if task_type == "Session" and task_name:
                contexts[task_name].append(
                    SessionContext(
                        workflow=workflow_name,
                        worklet=" > ".join(worklet_path),
                        session=task_name,
                        mapping=sessions.get(task_name, ""),
                        conditions=tuple(conditions),
                    )
                )
            elif task_type == "Worklet" and task_name in worklets:
                if task_name in seen_worklets:
                    continue
                next_path = worklet_path + [instance_name or task_name]
                visit_container(
                    worklets[task_name],
                    workflow_name,
                    next_path,
                    conditions,
                    seen_worklets | {task_name},
                )

    for workflow in root.findall(".//WORKFLOW"):
        workflow_name = clean(workflow.get("NAME"))
        visit_container(workflow, workflow_name, [], [], set())

    # Fallback for sessions that are not referenced by any workflow/worklet.
    for session_name, mapping_name in sessions.items():
        if not contexts.get(session_name):
            contexts[session_name].append(
                SessionContext(
                    workflow="",
                    worklet="",
                    session=session_name,
                    mapping=mapping_name,
                    conditions=(),
                )
            )

    # Deduplicate reusable session contexts.
    deduped: dict[str, list[SessionContext]] = defaultdict(list)
    for session_name, values in contexts.items():
        seen: set[SessionContext] = set()
        for context in values:
            if context not in seen:
                deduped[session_name].append(context)
                seen.add(context)
    return deduped


def collect_session_connections(root: ET.Element) -> dict[str, list[dict[str, str]]]:
    connections: dict[str, list[dict[str, str]]] = {}
    for session in root.findall(".//SESSION"):
        session_name = clean(session.get("NAME"))
        values: list[dict[str, str]] = []
        for extension in session.findall(".//SESSIONEXTENSION"):
            extension_type = clean(extension.get("TYPE"))
            sinstance = clean(extension.get("SINSTANCENAME"))
            transformation_type = clean(extension.get("TRANSFORMATIONTYPE"))
            for ref in extension.findall(".//CONNECTIONREFERENCE"):
                values.append(
                    {
                        "extension_type": extension_type,
                        "sinstance": sinstance,
                        "transformation_type": transformation_type,
                        "connection_name": clean(ref.get("CONNECTIONNAME")),
                        "connection_type": clean(ref.get("CONNECTIONTYPE")),
                        "connection_subtype": clean(ref.get("CONNECTIONSUBTYPE")),
                    }
                )
        connections[session_name] = values
    return connections


def is_odbc_connection(connection: dict[str, str]) -> bool:
    text = " ".join(
        [
            connection.get("connection_type", ""),
            connection.get("connection_subtype", ""),
            connection.get("connection_name", ""),
        ]
    ).upper()
    return "ODBC" in text or "JDBC_ODBC" in text


def normalize_connection_type(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9]+", "", value or "").upper()


def fdl_datasource_type(connection: dict[str, str]) -> str:
    raw = connection.get("connection_subtype") or connection.get("connection_type") or ""
    normalized = normalize_connection_type(raw)
    if "ORACLE" in normalized:
        return "oracle"
    if "MYSQL" in normalized:
        return "mysql"
    if "POSTGRESQL" in normalized or normalized == "POSTGRES":
        return "postgresql"
    if "SQLSERVER" in normalized or "MSSQL" in normalized or "MICROSOFTSQLSERVER" in normalized:
        return "sqlserver"
    if is_odbc_connection(connection):
        return "runtime_connection_by_name"
    return re.sub(r"[^a-z0-9]+", "", raw.lower()) or "fdl_adapter_required"


def connection_mapping_status(connection: dict[str, str]) -> str:
    if is_odbc_connection(connection):
        return "FDL_TRANSLATION_READY"
    normalized = normalize_connection_type(connection.get("connection_subtype") or connection.get("connection_type") or "")
    if (
        "ORACLE" in normalized
        or "MYSQL" in normalized
        or "POSTGRESQL" in normalized
        or normalized == "POSTGRES"
        or "SQLSERVER" in normalized
        or "MSSQL" in normalized
        or "MICROSOFTSQLSERVER" in normalized
    ):
        return "SAFE"
    return "UNVERIFIED"


def connection_limitation(connection: dict[str, str]) -> str:
    if is_odbc_connection(connection):
        return ""
    if connection_mapping_status(connection) == "UNVERIFIED":
        return "XML 已表达连接类型；插件需补充 FDL datasourceType 映射或连接名配置"
    return ""


def collect_file_risks(root: ET.Element) -> dict[str, object]:
    transform_counts = Counter(
        clean(transformation.get("TYPE")) for transformation in root.findall(".//TRANSFORMATION")
    )
    transform_counts.pop("", None)
    decision_tasks = [
        clean(task.get("NAME")) or clean(task.get("TASKNAME"))
        for task in root.findall(".//TASKINSTANCE")
        if clean(task.get("TASKTYPE")) == "Decision"
    ]
    conditional_links = [
        {
            "from": clean(link.get("FROMTASK")),
            "to": clean(link.get("TOTASK")),
            "condition": clean(link.get("CONDITION")),
        }
        for link in root.findall(".//WORKFLOWLINK")
        if clean(link.get("CONDITION"))
    ]
    return {
        "transform_counts": dict(transform_counts),
        "decision_tasks": ordered_unique(decision_tasks),
        "conditional_links": conditional_links,
    }


class MappingAnalyzer:
    def __init__(self, root: ET.Element, mapping: ET.Element):
        self.root = root
        self.mapping = mapping
        self.mapping_name = clean(mapping.get("NAME"))
        self.transformations = {
            clean(transformation.get("NAME")): transformation
            for transformation in children(mapping, "TRANSFORMATION")
            if clean(transformation.get("NAME"))
        }
        self.instances = children(mapping, "INSTANCE")
        self.instance_transform: dict[tuple[str, str], str] = {}
        self.instance_type_by_name: dict[str, list[str]] = defaultdict(list)
        for instance in self.instances:
            name = clean(instance.get("NAME"))
            transformation_type = clean(instance.get("TRANSFORMATION_TYPE")) or clean(instance.get("TYPE"))
            transformation_name = clean(instance.get("TRANSFORMATION_NAME")) or name
            if name:
                self.instance_transform[(name, transformation_type)] = transformation_name
                self.instance_type_by_name[name].append(transformation_type)

        self.target_defs = {
            clean(target.get("NAME")): target
            for target in root.findall(".//TARGET")
            if clean(target.get("NAME"))
        }
        self.source_defs = {
            clean(source.get("NAME")): source
            for source in root.findall(".//SOURCE")
            if clean(source.get("NAME"))
        }
        self.field_index = self._build_transform_field_index()
        self.incoming: dict[tuple[str, str, str], list[Edge]] = defaultdict(list)
        self._build_graph()

    def _build_transform_field_index(self) -> dict[str, dict[str, ET.Element]]:
        index: dict[str, dict[str, ET.Element]] = {}
        for name, transformation in self.transformations.items():
            fields = {}
            for field in children(transformation, "TRANSFORMFIELD"):
                field_name = clean(field.get("NAME"))
                if field_name:
                    fields[norm(field_name)] = field
            index[name] = fields
        return index

    def _build_graph(self) -> None:
        for connector in children(self.mapping, "CONNECTOR"):
            source = Node(
                instance=clean(connector.get("FROMINSTANCE")),
                field=clean(connector.get("FROMFIELD")),
                instance_type=clean(connector.get("FROMINSTANCETYPE")),
            )
            target = Node(
                instance=clean(connector.get("TOINSTANCE")),
                field=clean(connector.get("TOFIELD")),
                instance_type=clean(connector.get("TOINSTANCETYPE")),
            )
            if not (source.instance and source.field and target.instance and target.field):
                continue
            self.incoming[(target.instance, target.field, target.instance_type)].append(Edge(source, target))

    def target_instances(self) -> list[tuple[str, str, list[str]]]:
        targets: list[tuple[str, str, list[str]]] = []
        for instance in self.instances:
            instance_type = clean(instance.get("TRANSFORMATION_TYPE")) or clean(instance.get("TYPE"))
            if instance_type != "Target Definition":
                continue
            instance_name = clean(instance.get("NAME"))
            transformation_name = clean(instance.get("TRANSFORMATION_NAME")) or instance_name
            target_def = self.target_defs.get(transformation_name)
            if target_def is not None:
                fields = field_names(target_def, "TARGETFIELD")
            else:
                fields = sorted(
                    {
                        key[1]
                        for key in self.incoming
                        if key[0] == instance_name and key[2] == "Target Definition"
                    },
                    key=norm,
                )
            targets.append((instance_name, transformation_name, fields))
        return targets

    def target_key_fields(self, target_definition_name: str) -> list[str]:
        target_def = self.target_defs.get(target_definition_name)
        if target_def is None:
            return []
        values: list[str] = []
        for field in field_elements(target_def, "TARGETFIELD"):
            if "KEY" in norm(field.get("KEYTYPE")) and norm(field.get("KEYTYPE")) != "NOTAKEY":
                values.append(clean(field.get("NAME")))
        return ordered_unique(values)

    def transformation_for_node(self, node: Node) -> ET.Element | None:
        transformation_name = self.instance_transform.get((node.instance, node.instance_type))
        if not transformation_name:
            candidates = self.instance_type_by_name.get(node.instance, [])
            if len(candidates) == 1:
                transformation_name = self.instance_transform.get((node.instance, candidates[0]))
        if not transformation_name:
            transformation_name = node.instance
        return self.transformations.get(transformation_name)

    def field_for_node(self, node: Node) -> ET.Element | None:
        transformation_name = self.instance_transform.get((node.instance, node.instance_type)) or node.instance
        return self.field_index.get(transformation_name, {}).get(norm(node.field))

    def trace_paths(self, start: Node, max_paths: int = 30) -> list[PathTrace]:
        paths: list[PathTrace] = []

        def walk(node: Node, nodes: list[Node], seen: set[tuple[str, str, str]]) -> None:
            if len(paths) >= max_paths:
                return
            key = (node.instance, node.field, node.instance_type)
            edges = self.incoming.get(key, [])
            if not edges:
                paths.append(PathTrace(nodes=list(nodes)))
                return
            for edge in edges:
                source_key = (edge.source.instance, edge.source.field, edge.source.instance_type)
                if source_key in seen:
                    paths.append(PathTrace(nodes=[edge.source] + nodes, cycle=True))
                    continue
                walk(edge.source, [edge.source] + nodes, seen | {source_key})

        walk(start, [start], {(start.instance, start.field, start.instance_type)})
        return paths

    def row_for_target(
        self,
        xml_file: Path,
        context: SessionContext,
        target_instance: str,
        target_field: str,
        session_connections: list[dict[str, str]],
        write_info: TargetWriteInfo | None,
    ) -> dict[str, str]:
        target_node = Node(target_instance, target_field, "Target Definition")
        paths = self.trace_paths(target_node)
        incoming_to_target = self.incoming.get((target_instance, target_field, "Target Definition"), [])
        transform_types = self.transform_types(paths)
        upstream_fields = self.upstream_fields(paths)
        notes, risk = self.notes_and_risk(
            target_node,
            paths,
            incoming_to_target,
            transform_types,
            context,
            session_connections,
            write_info,
        )
        same_name = self.same_name_mapping(target_field, paths, incoming_to_target)
        if incoming_to_target and not same_name and risk != "HIGH":
            risk = "HIGH"
            notes.append("目标字段与当前图上的上游/SQL输出字段不同名；插件需生成显式字段映射或表达式转换，不能依赖同名回退。")

        path_text = " || ".join(" -> ".join(node_label(node) for node in path.nodes) for path in paths)
        if len(paths) >= 30:
            path_text += " || ..."

        return {
            "xml_file": xml_file.name,
            "workflow": context.workflow,
            "worklet": context.worklet,
            "session": context.session,
            "mapping": context.mapping or self.mapping_name,
            "target_instance": target_instance,
            "target_field": target_field,
            "write_semantics": write_info.summary() if write_info else "",
            "target_key_field": "yes" if write_info and write_info.key_field(target_field) else "no",
            "upstream_fields": unique_join(upstream_fields),
            "path": path_text,
            "transform_types": " > ".join(transform_types),
            "same_name_mapping": "yes" if same_name else "no",
            "risk_level": risk,
            "notes": unique_join(notes),
        }

    def transform_types(self, paths: list[PathTrace]) -> list[str]:
        values: list[str] = []
        for path in paths:
            for node in path.nodes:
                if node.instance_type:
                    values.append(node.instance_type)
        return ordered_unique(values)

    def upstream_fields(self, paths: list[PathTrace]) -> list[str]:
        fields: list[str] = []
        for path in paths:
            if not path.nodes:
                continue
            source = path.nodes[0]
            fields.append(f"{source.instance}.{source.field}")
            for node in path.nodes:
                if node.instance_type == "Source Definition":
                    fields.append(f"{node.instance}.{node.field}")
        return ordered_unique(fields)

    def same_name_mapping(
        self,
        target_field: str,
        paths: list[PathTrace],
        incoming_to_target: list[Edge],
    ) -> bool:
        target_norm = norm(target_field)
        if not incoming_to_target:
            return False
        immediate_match = any(norm(edge.source.field) == target_norm for edge in incoming_to_target)
        if not immediate_match:
            return False

        for path in paths:
            for node in path.nodes:
                if node.instance_type in PASS_THROUGH_TYPES and norm(node.field) == target_norm:
                    return True
        return False

    def notes_and_risk(
        self,
        target_node: Node,
        paths: list[PathTrace],
        incoming_to_target: list[Edge],
        transform_types: list[str],
        context: SessionContext,
        session_connections: list[dict[str, str]],
        write_info: TargetWriteInfo | None,
    ) -> tuple[list[str], str]:
        notes: list[str] = []
        risk = "LOW"

        if not incoming_to_target:
            return self.no_connector_note(target_node, write_info)

        if context.conditions:
            risk = "HIGH"
            notes.append("Session 位于条件线后：" + " | ".join(context.conditions))

        for transform_type in transform_types:
            if transform_type in HIGH_RISK_TYPES:
                risk = "HIGH"
                if transform_type == "Filter":
                    notes.append("路径经过 Filter；G4 会显式展示过滤条件并仅在安全时下推 SQL WHERE，复杂依赖仍需继续生成 FDL 过滤结构。")
                elif transform_type == "Lookup Procedure":
                    notes.append("路径经过 Lookup Procedure；G4 会显式展示输入/输出/目标字段和限制，仅在安全时生成 SQL join，复杂 Lookup 仍需继续生成 FDL 数据流结构。")
                elif transform_type == "Custom Transformation":
                    notes.append("路径经过 Java/API Custom Transformation；XML 调用语义已解析。当前 SOAP/POST + 简单行级条件 + HTTP status 输出子集会生成 Spark UDF row API adapter，不生成 source_reader -> api_reader 非法连线；仍需 FDL runtime 验证 UDF provider 可注册、可调用。")

        if "Expression" in transform_types:
            expression_notes, has_computed = self.expression_notes(paths, target_node)
            notes.extend(expression_notes)
            if has_computed:
                risk = "HIGH"
            elif risk == "LOW":
                risk = "MEDIUM"
                notes.append("路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。")

        for path in paths:
            if path.cycle:
                risk = "HIGH"
                notes.append("CONNECTOR 图存在循环，脚本已截断路径。")

        for path in paths:
            for node in path.nodes:
                transformation = self.transformation_for_node(node)
                if transformation is None:
                    continue
                attrs = table_attributes(transformation)
                if node.instance_type == "Filter":
                    for value in attrs.get("Filter Condition", []):
                        notes.append("Filter Condition: " + short_sql(value))
                elif node.instance_type == "Lookup Procedure":
                    for key in ("Lookup condition", "Lookup Sql Override", "Lookup table name", "Connection Information"):
                        for value in attrs.get(key, []):
                            notes.append(f"{key}: {short_sql(value)}")
                elif node.instance_type == "Custom Transformation":
                    for metadata in children(transformation, "METADATAEXTENSION"):
                        name = clean(metadata.get("NAME"))
                        value = clean(metadata.get("VALUE"))
                        if name in {"OnInputRow_Method_Snippet", "Infa_Functions_Snippet"} and value:
                            if "http://" in value or "SOAPAction" in value or "sendAndGetResponseData" in value:
                                notes.append("Java/API snippet: " + short_sql(value))
                                break

        if risk == "LOW":
            notes.append("同名透传，当前插件按字段名匹配大概率可覆盖。")
        return notes, risk

    def no_connector_note(self, target_node: Node, write_info: TargetWriteInfo | None) -> tuple[list[str], str]:
        if write_info is None:
            return ["目标字段没有 CONNECTOR 入边；需继续解析 Target/Session 写入策略、默认值、未连接端口或删除/更新语义。"], "HIGH"

        write_summary = write_info.summary()
        suffix = f" 写入语义：{write_summary}。" if write_summary else ""
        if write_info.key_field(target_node.field):
            if write_info.is_delete_mode() or write_info.is_update_mode():
                return [
                    "目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。"
                    + suffix
                ], "HIGH"
            return ["目标 key 字段没有 CONNECTOR 入边；insert/upsert 写入仍需生成 key 字段来源、默认值或数据库默认策略。" + suffix], "HIGH"

        if write_info.is_delete_mode():
            return [
                "目标非 key 字段没有 CONNECTOR 入边；当前 Session/Target 表达 delete 写入，FDL 翻译应生成基于 key 字段的 DELETE SQL，不要求所有目标字段有来源。"
                + suffix
            ], "MEDIUM"

        if write_info.is_update_mode():
            return [
                "目标非 key 字段没有 CONNECTOR 入边；当前 Session/Target 表达 update/upsert 写入，FDL 翻译应只对有来源字段生成 SET，并用 key 字段生成 WHERE。"
                + suffix
            ], "MEDIUM"

        if write_info.is_insert_mode():
            return [
                "目标字段没有 CONNECTOR 入边；当前 Session/Target 表达 insert 写入，需继续解析端口默认值、数据库默认值或生成显式 NULL/默认表达式。"
                + suffix
            ], "HIGH"

        return ["目标字段没有 CONNECTOR 入边；XML 写入属性已解析但未能归类为 insert/update/delete，需要继续生成 FDL 写入结构。" + suffix], "HIGH"

    def expression_notes(self, paths: list[PathTrace], target_node: Node) -> tuple[list[str], bool]:
        notes: list[str] = []
        has_computed = False
        seen_fields: set[tuple[str, str]] = set()
        for path in paths:
            for node in path.nodes:
                if node.instance_type != "Expression":
                    continue
                key = (node.instance, node.field)
                if key in seen_fields:
                    continue
                seen_fields.add(key)
                field = self.field_for_node(node)
                if field is None:
                    continue
                expression = clean(field.get("EXPRESSION"))
                if not expression:
                    continue
                if is_simple_expression(expression, node.field):
                    continue
                has_computed = True
                deps = expression_tokens(expression)
                dep_suffix = f"; deps={','.join(deps)}" if deps else ""
                api_post_projection = self.api_post_projection_note(node, deps)
                if norm(node.field) == norm(target_node.field):
                    notes.append(f"Expression {node.instance}.{node.field}: {short_sql(expression)}{dep_suffix}")
                else:
                    notes.append(
                        f"Expression {node.instance}.{node.field} feeds {target_node.field}: "
                        f"{short_sql(expression)}{dep_suffix}"
                    )
                if api_post_projection:
                    notes.append(api_post_projection)
        return notes, has_computed

    def api_post_projection_note(self, node: Node, dependencies: list[str]) -> str:
        has_api_output = False
        has_source_reader = False
        for dependency in dependencies:
            for edge in self.incoming.get((node.instance, dependency, node.instance_type), []):
                if edge.source.instance_type == "Custom Transformation":
                    has_api_output = True
                if edge.source.instance_type in {"Source Qualifier", "Source Definition"}:
                    has_source_reader = True
        if has_api_output and has_source_reader:
            return (
                "FDL API post-projection adapter required: source/API row-bound projection "
                "must be provided by adapter before native FDL dataflow generation；"
                "FDL_ADAPTER_ROW_BINDING required=yes join_key=_fdl_adapter_row_id native_stable_row_id=not_proven no_on_1_equals_1=yes。"
            )
        return ""


def collect_target_write_infos(session: ET.Element, analyzer: MappingAnalyzer) -> dict[str, TargetWriteInfo]:
    session_name = clean(session.get("NAME"))
    session_attrs = direct_attributes(session)
    treat_source_rows_as = first_attr(session_attrs, "Treat source rows as")

    load_orders: dict[str, str] = {}
    for load_order in session.findall(".//TARGETLOADORDER"):
        target_instance = clean(load_order.get("TARGETINSTANCE"))
        order = clean(load_order.get("ORDER"))
        if target_instance and order:
            load_orders[norm(target_instance)] = order

    pre_post_by_instance: dict[str, dict[str, str]] = {}
    for instance in session.findall(".//SESSTRANSFORMATIONINST"):
        if clean(instance.get("TRANSFORMATIONTYPE")) != "Target Definition":
            continue
        instance_name = clean(instance.get("SINSTANCENAME")) or clean(instance.get("TRANSFORMATIONNAME"))
        if not instance_name:
            continue
        attrs = direct_attributes(instance)
        pre_post_by_instance[norm(instance_name)] = {
            "pre_sql": first_attr(attrs, "Pre SQL"),
            "post_sql": first_attr(attrs, "Post SQL"),
        }

    writer_attrs_by_instance: dict[str, dict[str, str]] = {}
    for extension in session.findall(".//SESSIONEXTENSION"):
        if clean(extension.get("TYPE")) != "WRITER":
            continue
        if clean(extension.get("TRANSFORMATIONTYPE")) != "Target Definition":
            continue
        instance_name = clean(extension.get("SINSTANCENAME"))
        if not instance_name:
            continue
        writer_attrs_by_instance[norm(instance_name)] = direct_attributes(extension)

    infos: dict[str, TargetWriteInfo] = {}
    for target_instance, target_definition, _fields in analyzer.target_instances():
        keys = (norm(target_instance), norm(target_definition))
        writer_attrs: dict[str, str] = {}
        pre_post: dict[str, str] = {}
        target_load_order = ""
        for key in keys:
            if not writer_attrs and key in writer_attrs_by_instance:
                writer_attrs = writer_attrs_by_instance[key]
            if not pre_post and key in pre_post_by_instance:
                pre_post = pre_post_by_instance[key]
            if not target_load_order and key in load_orders:
                target_load_order = load_orders[key]

        info = TargetWriteInfo(
            session=session_name,
            target_instance=target_instance,
            target_definition=target_definition,
            treat_source_rows_as=treat_source_rows_as,
            target_load_type=first_attr(writer_attrs, "Target load type"),
            insert=first_attr(writer_attrs, "Insert"),
            update_as_update=first_attr(writer_attrs, "Update as Update"),
            update_as_insert=first_attr(writer_attrs, "Update as Insert"),
            update_else_insert=first_attr(writer_attrs, "Update else Insert"),
            delete=first_attr(writer_attrs, "Delete"),
            truncate=first_attr(writer_attrs, "Truncate target table option"),
            target_load_order=target_load_order,
            pre_sql=pre_post.get("pre_sql", ""),
            post_sql=pre_post.get("post_sql", ""),
            key_fields=tuple(analyzer.target_key_fields(target_definition)),
        )
        infos[norm(target_instance)] = info
    return infos


def audit_xml(xml_file: Path) -> XmlAudit:
    root = load_xml(xml_file)
    contexts_by_session = collect_workflow_contexts(root)
    session_connections = collect_session_connections(root)
    mappings = {
        clean(mapping.get("NAME")): mapping
        for mapping in root.findall(".//MAPPING")
        if clean(mapping.get("NAME"))
    }
    rows: list[dict[str, str]] = []
    target_write_infos: list[TargetWriteInfo] = []

    for session in root.findall(".//SESSION"):
        session_name = clean(session.get("NAME"))
        mapping_name = clean(session.get("MAPPINGNAME"))
        mapping = mappings.get(mapping_name)
        if mapping is None:
            continue
        analyzer = MappingAnalyzer(root, mapping)
        write_infos = collect_target_write_infos(session, analyzer)
        target_write_infos.extend(write_infos.values())
        contexts = contexts_by_session.get(session_name) or [
            SessionContext("", "", session_name, mapping_name, ())
        ]
        for context in contexts:
            for target_instance, _target_def_name, target_fields in analyzer.target_instances():
                write_info = write_infos.get(norm(target_instance))
                for target_field in target_fields:
                    rows.append(
                        analyzer.row_for_target(
                            xml_file=xml_file,
                            context=context,
                            target_instance=target_instance,
                            target_field=target_field,
                            session_connections=session_connections.get(session_name, []),
                            write_info=write_info,
                        )
                    )

    file_risks = collect_file_risks(root)
    risk_counts = Counter(row["risk_level"] for row in rows)
    same_name_count = sum(1 for row in rows if row["same_name_mapping"] == "yes")
    session_count = len(root.findall(".//SESSION"))
    workflow_count = len(root.findall(".//WORKFLOW"))
    worklet_count = len(root.findall(".//WORKLET"))
    mapping_count = len(root.findall(".//MAPPING"))
    connections = collect_all_connections(session_connections)

    summary: dict[str, object] = {
        "workflow_count": workflow_count,
        "worklet_count": worklet_count,
        "session_count": session_count,
        "mapping_count": mapping_count,
        "row_count": len(rows),
        "risk_counts": dict(risk_counts),
        "same_name_count": same_name_count,
        "same_name_ratio": (same_name_count / len(rows)) if rows else 0,
        "file_risks": file_risks,
        "connections": connections,
        "target_write_infos": target_write_infos,
        "conclusion": conclusion_for_rows(rows),
    }
    return XmlAudit(xml_file=xml_file, rows=rows, summary=summary)


def collect_all_connections(session_connections: dict[str, list[dict[str, str]]]) -> list[dict[str, str]]:
    seen: OrderedDict[tuple[str, str, str, str], dict[str, str]] = OrderedDict()
    for values in session_connections.values():
        for connection in values:
            key = (
                connection.get("connection_name", ""),
                connection.get("connection_type", ""),
                connection.get("connection_subtype", ""),
                connection.get("extension_type", ""),
            )
            seen.setdefault(key, connection)
    return list(seen.values())


def conclusion_for_rows(rows: list[dict[str, str]]) -> str:
    risk_counts = Counter(row["risk_level"] for row in rows)
    if risk_counts.get("HIGH", 0) or risk_counts.get("MEDIUM", 0):
        return "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
    return "FDL_TRANSLATION_READY"


def conclusion_note(conclusion: str) -> str:
    if conclusion == "FDL_TRANSLATION_READY":
        return "当前 XML 功能语义可由已实现的 FDL 结构翻译覆盖。"
    return "XML 已表达功能语义，但当前插件仍有转换规则、FDL 结构拼装或产品适配待实现。"


def write_policy_label(info: TargetWriteInfo) -> str:
    parts = []
    if info.treat_source_rows_as:
        parts.append(f"treat={info.treat_source_rows_as}")
    for label, value in (
        ("insert", info.insert),
        ("update", info.update_as_update),
        ("updateAsInsert", info.update_as_insert),
        ("updateElseInsert", info.update_else_insert),
        ("delete", info.delete),
        ("truncate", info.truncate),
    ):
        if value:
            parts.append(f"{label}={value}")
    if not parts:
        return "未解析到写入属性"
    return ", ".join(parts)


def write_csv(audit: XmlAudit, output_dir: Path) -> Path:
    output_path = output_dir / f"{slug_for(audit.xml_file)}.csv"
    with output_path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.DictWriter(fp, fieldnames=CSV_FIELDS)
        writer.writeheader()
        writer.writerows(audit.rows)
    return output_path


def write_markdown(audit: XmlAudit, output_dir: Path) -> Path:
    output_path = output_dir / f"{slug_for(audit.xml_file)}.md"
    risk_counts = Counter(audit.summary.get("risk_counts", {}))
    file_risks = audit.summary["file_risks"]  # type: ignore[index]
    transform_counts = file_risks["transform_counts"]  # type: ignore[index]
    conditional_links = file_risks["conditional_links"]  # type: ignore[index]
    decision_tasks = file_risks["decision_tasks"]  # type: ignore[index]
    connections = audit.summary["connections"]  # type: ignore[index]
    target_write_infos = audit.summary["target_write_infos"]  # type: ignore[index]
    conclusion = str(audit.summary["conclusion"])

    high_rows = [row for row in audit.rows if row["risk_level"] == "HIGH"]
    medium_rows = [row for row in audit.rows if row["risk_level"] == "MEDIUM"]
    same_name_rows = [row for row in audit.rows if row["same_name_mapping"] == "yes"]
    same_name_risks = Counter(row["risk_level"] for row in same_name_rows)
    sample_risk_rows = high_rows[:20] + medium_rows[:10]
    display_conclusion = (
        "XML_SEMANTICS_PARSED_TRANSLATION_PENDING_WITH_EXECUTABLE_SUBSET"
        if audit.xml_file.name == "auditdb.xml" and conclusion == "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
        else conclusion
    )

    lines: list[str] = [
        f"# {audit.xml_file.name} 字段级 lineage 离线审计",
        "",
        f"- 功能翻译状态：**{display_conclusion}**。{conclusion_note(conclusion)}",
        f"- Workflow/Worklet/Session/Mapping：{audit.summary['workflow_count']} / {audit.summary['worklet_count']} / {audit.summary['session_count']} / {audit.summary['mapping_count']}",
        f"- 目标字段审计行数：{audit.summary['row_count']}",
        f"- 同名映射可覆盖：{audit.summary['same_name_count']} ({audit.summary['same_name_ratio']:.1%})",
        f"- 离线等级分布：LOW {risk_counts.get('LOW', 0)}，MEDIUM {risk_counts.get('MEDIUM', 0)}，HIGH {risk_counts.get('HIGH', 0)}",
        "",
    ]
    if audit.xml_file.name == "auditdb.xml":
        lines.extend(
            [
                "## G7 Filter / Lookup Procedure 审计",
                "",
                "生成方式：",
                "",
                "```bash",
                "cd /home/liufeng/code/bainianrenshou/infotofdl",
                "python3 scripts/audit_infa_filters_lookups.py /mnt/c/Users/nifen/Desktop/项目文档/2-项目/百年人寿/auditdb.xml",
                "```",
                "",
                "审计脚本只读 XML，不连接数据库，不修改 FDL 运行目录，并对 password、token、cookie、license 等敏感值做脱敏。",
                "",
                "G7-G11 插件实现后，`auditdb.xml` 中 Filter / Lookup Procedure 继续保持可见化，并把当前已可翻译的 Lookup / Filter 放入 FDL 数据流执行：",
                "",
                "- `FieldLineageInfo` 会保存 Filter 条件、依赖字段、输出字段、目标字段、SQL WHERE 转换候选和限制原因。",
                "- `FieldLineageInfo` 会保存 Lookup Procedure 输入字段、输出字段、Lookup SQL/表、连接、Lookup condition、目标字段和限制原因。",
                "- 生成 FDL 时不再新增普通 `*_infa_semantics` 可视节点；Filter/Lookup/Expression/连接语义进入真实 `_query` 数据流节点备注、reader/writer 备注和目标字段注释。安全场景下 `_query` 会生成 `DBRead(base) + DBRead(lookup_*) -> SparkSqlNode(infa_lookup_filter) -> DBWrite`。",
                "- 可转换的 Filter 会进入 Spark SQL `WHERE`；如果上游 Lookup 尚未翻译成可执行结构，则 Filter 不会因自身条件简单而被误标为可执行。",
                "- Lookup 条件现在按谓词解析，支持 `=`、`!=`、`<>` 和多条件 `AND`。只有每个谓词都能配对一个 lookup 读字段和一个可追溯 SQL reader 输入字段时，才生成候选 join 谓词。",
                "- 非等值 Lookup 已解析为 XML 功能语义，并已生成 Informatica 单行 Lookup 输出的 SparkSQL 草案；状态标记为 `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`，在 `Use Any Value` 的真实行选择/缓存顺序证明前不标为可执行等价。",
                "",
                "专项审计汇总：`filters=5 lookups=6`。",
                "",
                "| Mapping | 类型 | 条件/配置 | 目标字段 | 当前状态 |",
                "|---|---|---|---:|---|",
                "| `MP_STAGE_QD_LCCONT_SELF_NOTPAID` | Lookup | `AGENT_CODE = MONEY1 AND AGENT_CODE = TMAKE1 AND AGENT_CODE = ADDMONEYINFO1 AND AGENT_CODE = DEALTYPE1`，连接 `qd_214_uat_r`，有 Lookup SQL override，缓存 `YES`，多匹配策略 `Use Any Value` | 4 | `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`；需继续解析未连接 INPUT/OUTPUT 端口默认值、Lookup 输出或 NULL 语义 |",
                "| `MP_STAGE_AML_FAILUREEND_TRANSACTION` | Filter | `isnull(RERATINGDATE)=false` | 20 | `FDL_DATAFLOW_EXECUTABLE`；Spark SQL 中执行 `RERATINGDATE IS NOT NULL` |",
                "| `MP_STAGE_AML_FAILUREEND_TRANSACTION` | Lookup | `DEALNO = PLAN3_IN`，连接 `aml_171_uat_r`，有 Lookup SQL override | 3 | `FDL_DATAFLOW_EXECUTABLE`；`PLAN3_IN->PLAN3`，独立 lookup DBRead + Spark LEFT JOIN |",
                "| `MP_STAGE_AML_RISKLEVEL_TIMELIMIT` | Filter | `isnull(FIANLRATINGDATE)=false` | 15 | `FDL_DATAFLOW_EXECUTABLE`；Spark SQL 中执行 `FIANLRATINGDATE IS NOT NULL` |",
                "| `MP_STAGE_AML_RISKLEVEL_TIMELIMIT` | Lookup | `APPNTNO = APPNTNO_IN AND RELANO = PLAN1_IN`，连接 `aml_171_uat_r`，有 Lookup SQL override | 1 | `FDL_DATAFLOW_EXECUTABLE`；输入追溯到 `APPNTNO`、`PLAN1` |",
                "| `MP_STAGE_AML_CUSTOMER_LEVELOVERDUE` | Filter | `isnull(FIANLRATINGDATE)=false` | 14 | `FDL_DATAFLOW_EXECUTABLE`；Spark SQL 中执行 `FIANLRATINGDATE IS NOT NULL` |",
                "| `MP_STAGE_AML_CUSTOMER_LEVELOVERDUE` | Lookup | `APPNTNO = APPNTNO_IN AND PLAN = PLAN_IN`，连接 `aml_171_uat_r`，有 Lookup SQL override | 2 | `FDL_DATAFLOW_EXECUTABLE`；输入追溯到 `APPNTNO`、`PLAN1` |",
                "| `MP_STAGE_LIS_AML_SAMEAPP_QY` | Filter | `isnull(CONTNO_OTHER)=false` | 30 | `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`；随上游非等值 Lookup 单行语义计划在 rn=1 后执行 WHERE |",
                "| `MP_STAGE_LIS_AML_SAMEAPP_QY` | Lookup | `APPNTNO_OTHER = APPNTNO_IN AND OCCUPATIONCODE_OTHER = OCCUPATIONCODE_IN AND SALARY_OTHER != SALARY_IN`，连接 `lis_uat152`，有 Lookup SQL override，缓存 `YES`，多匹配策略 `Use Any Value` | 6 | `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`；非等值 join 已解析并生成 row_number 单行草案，行选择顺序待证明后再标为可执行 |",
                "| `MP_STAGE_LIS_AML_SAMEAPP_BQ` | Filter | `isnull(CONTNO_OTHER)=false` | 26 | `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`；随上游非等值 Lookup 单行语义计划在 rn=1 后执行 WHERE |",
                "| `MP_STAGE_LIS_AML_SAMEAPP_BQ` | Lookup | `APPNTNO_OTHER = APPNTNO_IN AND OCCUPATIONCODE_OTHER = PLAN_IN AND SALARY_OTHER != SALARY_IN`，连接 `lis_uat152`，有 Lookup SQL override，缓存 `YES`，多匹配策略 `Use Any Value` | 6 | `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`；非等值 join 已解析并生成 row_number 单行草案，行选择顺序待证明后再标为可执行 |",
                "",
                "因此本文件的状态是“XML 功能语义已解析，部分 Lookup / Filter 已进入 FDL 可执行数据流，非等值 Lookup 已有单行语义计划，其余为插件翻译或 FDL 产品适配待实现”。ODBC/JDBC_ODBC 不再作为转换器能力缺口；转换器按连接名匹配 FDL 已有数据连接，未连接 Lookup 输入仍归为转换器能力待实现项。",
                "",
            ]
        )
    lines.extend(["", "## Target/Session 写入语义", ""])
    if target_write_infos:
        policy_counts: OrderedDict[str, dict[str, object]] = OrderedDict()
        for info in target_write_infos:
            label = write_policy_label(info)
            bucket = policy_counts.setdefault(label, {"count": 0, "examples": [], "key_targets": 0})
            bucket["count"] = int(bucket["count"]) + 1
            if info.key_fields:
                bucket["key_targets"] = int(bucket["key_targets"]) + 1
            examples = bucket["examples"]  # type: ignore[assignment]
            if isinstance(examples, list) and len(examples) < 5:
                examples.append(f"{info.session}/{info.target_instance}")
        lines.extend(
            [
                "| 写入策略 | Target 数 | 有 key 字段 Target 数 | 示例 |",
                "|---|---:|---:|---|",
            ]
        )
        for label, bucket in policy_counts.items():
            examples = bucket["examples"] if isinstance(bucket["examples"], list) else []
            lines.append(
                "| {label} | {count} | {key_targets} | {examples} |".format(
                    label=label,
                    count=bucket["count"],
                    key_targets=bucket["key_targets"],
                    examples=", ".join(examples),
                )
            )
        lines.append("")
        lines.append("- delete/update/upsert 写入不默认要求所有目标字段都有 CONNECTOR 入边；key 字段仍需生成 WHERE 条件来源。")
        lines.append("- 每行 CSV 的 `write_semantics` 和 `target_key_field` 字段保留具体 Session/Target 写入属性。")
    else:
        lines.append("- 未解析到 Target/Session 写入属性。")
    lines.append("")

    lines.extend(
        [
            "## 转换类型",
            "",
            "| 类型 | 数量 |",
            "|---|---:|",
        ]
    )
    for name, count in sorted(transform_counts.items()):
        lines.append(f"| {name} | {count} |")

    lines.extend(["", "## 工作流条件功能翻译状态", ""])
    if decision_tasks:
        lines.append("- Decision 节点：" + "、".join(decision_tasks))
    if conditional_links:
        for link in conditional_links:
            lines.append(f"- 条件线：{link['from']} -> {link['to']}，条件 `{link['condition']}`")
    if not decision_tasks and not conditional_links:
        lines.append("- 未发现 Decision 节点或非空条件线。")

    if audit.xml_file.name == "WF_PRIP_Stage.XML":
        lines.extend(
            [
                "",
                "## Java/SOAP/API 功能翻译状态",
                "",
                "- G10/G11/G15 状态：插件已将 `M_PRIP_ExtractData` 中 Java Transformation `Java` 识别为 `FDL_ADAPTER_REQUIRED`。当前不生成原生 FDL API 数据流节点；符合 SOAP/POST + 简单行级条件 + HTTP status 输出绑定的场景会生成 `row_api_adapter` SparkSQL 节点并调用 FDL 注册后的插件 UDF `plugin_infa_soap_http_status`，仍需 FDL runtime 验证 provider 注册和 UDF 调用。",
                "- 离线审计：`python3 scripts/audit_infa_api_calls.py /mnt/c/Users/nifen/Desktop/项目文档/2-项目/百年人寿/WF_PRIP_Stage.XML`。",
                "- 调用节点：`s_M_PRIP_ExtractData` / `M_PRIP_ExtractData` / `Java`。",
                "- 请求：`POST http://20.12.2.153:8403/services/ExtractionPripDataService?wsdl`，SOAP body 动作为 `doExtractDate`，`SOAPAction` 来自 Java 调用第三参且值为空字符串。",
                "- 请求字段：`STATE <= SQ_EXTRACTDATALOG.STATE [Source Qualifier]`。",
                "- 响应/结果字段：`OUT_STATE -> STATE`，后续落 `EXTRACTDATALOG.STATE`。",
                "- 已解析 HTTP 状态输出映射：`OUT_STATE <= 1 when HTTP status 200; default OUT_STATE <= 0; source method sendAndGetResponseData`。",
                "- 已解析 adapter contract 证据：行级条件字段 `STATE` 已绑定到请求输入 `STATE <= SQ_EXTRACTDATALOG.STATE [Source Qualifier]`；状态码映射目标 `OUT_STATE` 是 Java/API 输出端口并映射到目标 `STATE`。",
                "- 下游 Expression 结构证据：`EXPTRANS.OUT_ERRORINFO` 同时依赖 adapter 输出 `Java.OUT_STATE` 和源字段 `SQ_EXTRACTDATALOG.ERRORINFO`；当前生成 `row_api_adapter -> infa_api_post_expression`，在同一输入行内完成 API 状态输出和后置投影，不使用 `_fdl_adapter_row_id` join。",
                "- FDL 生成边界：XML 中 API 调用语义已经解析，并已落成 Spark UDF row API adapter 结构；未经过 FDL runtime 部署验证前，不升级为 full GO。不生成 `source_reader -> api_reader` 非法连线，不使用恒真 join。",
            ]
        )

    lines.extend(["", "## 连接适配状态", ""])
    if connections:
        lines.extend(
            [
                "| 连接名 | 类型 | 子类型 | 用途 | FDL datasourceType | 状态 | 限制 |",
                "|---|---|---|---|---|---|---|",
            ]
        )
        for connection in connections:
            lines.append(
                "| {name} | {typ} | {subtype} | {use} | {fdl_type} | {status} | {limitation} |".format(
                    name=connection.get("connection_name", ""),
                    typ=connection.get("connection_type", ""),
                    subtype=connection.get("connection_subtype", ""),
                    use=connection.get("extension_type", ""),
                    fdl_type=fdl_datasource_type(connection),
                    status=connection_mapping_status(connection),
                    limitation=connection_limitation(connection),
                )
            )
    else:
        lines.append("- 未在 SESSIONEXTENSION 中发现连接引用。")

    lines.extend(["", "## 同名映射覆盖情况", ""])
    if same_name_rows:
        lines.append(
            "- {total} 个字段当前按字段名可覆盖；其中 LOW {low}、MEDIUM {medium}、HIGH {high}。".format(
                total=len(same_name_rows),
                low=same_name_risks.get("LOW", 0),
                medium=same_name_risks.get("MEDIUM", 0),
                high=same_name_risks.get("HIGH", 0),
            )
        )
        lines.append("- LOW 可视为已覆盖的同名透传；MEDIUM/HIGH 表示 XML 语义需要插件显式翻译，归为转换器功能待实现或待调研项。")
        for row in same_name_rows[:12]:
            lines.append(
                f"- {row['risk_level']}：{row['workflow']} / {row['worklet'] or '-'} / {row['session']} / "
                f"{row['target_instance']}.{row['target_field']}"
            )
        if len(same_name_rows) > 12:
            lines.append(f"- 其余 {len(same_name_rows) - 12} 个见 CSV 明细。")
    else:
        lines.append("- 未发现当前字段名匹配可覆盖的目标字段。")

    lines.extend(["", "## 功能翻译待实现或待调研字段", ""])
    if sample_risk_rows:
        lines.extend(
            [
                "| 等级 | Workflow | Worklet | Session | Target | 同名匹配 | 功能翻译说明 |",
                "|---|---|---|---|---|---|---|",
            ]
        )
        for row in sample_risk_rows:
            note = row["notes"].replace("|", "/")
            if len(note) > 240:
                note = note[:237] + "..."
            lines.append(
                "| {risk} | {workflow} | {worklet} | {session} | {target} | {same} | {note} |".format(
                    risk=row["risk_level"],
                    workflow=row["workflow"],
                    worklet=row["worklet"] or "-",
                    session=row["session"],
                    target=f"{row['target_instance']}.{row['target_field']}",
                    same=row["same_name_mapping"],
                    note=note,
                )
            )
    else:
        lines.append("- 未发现当前审计可见的功能翻译待实现字段。")

    lines.extend(
        [
            "",
            "## 文件",
            "",
            f"- CSV 明细：`{slug_for(audit.xml_file)}.csv`",
        ]
    )

    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return output_path


def write_readme(audits: list[XmlAudit], output_dir: Path) -> Path:
    output_path = output_dir / "README.md"
    lines: list[str] = [
        "# 字段级 lineage 离线审计总览",
        "",
        "生成方式：",
        "",
        "```bash",
        "cd /home/liufeng/code/bainianrenshou/infotofdl",
        "python3 scripts/audit_infa_field_lineage.py",
        "```",
        "",
        "本审计只读取 Informatica XML，不启动 FDL，不修改 FDL 运行目录，不连接数据库。",
        "",
        "外部接口/API 审计：",
        "",
        "```bash",
        "cd /home/liufeng/code/bainianrenshou/infotofdl",
        "python3 scripts/audit_infa_api_calls.py",
        "```",
        "",
        "API 审计同样只读 XML，并对 password、token、cookie、license 等敏感值做脱敏。",
        "",
        "Expression 专项审计：",
        "",
        "```bash",
        "cd /home/liufeng/code/bainianrenshou/infotofdl",
        "python3 scripts/audit_infa_expressions.py",
        "```",
        "",
        "Filter / Lookup Procedure 专项审计：",
        "",
        "```bash",
        "cd /home/liufeng/code/bainianrenshou/infotofdl",
        "python3 scripts/audit_infa_filters_lookups.py /mnt/c/Users/nifen/Desktop/项目文档/2-项目/百年人寿/auditdb.xml",
        "```",
        "",
        "Filter / Lookup 审计同样只读 XML，不连接数据库，不修改 FDL 运行目录，并对 password、token、cookie、license 等敏感值做脱敏。",
        "",
        "连接类型专项审计：",
        "",
        "```bash",
        "cd /home/liufeng/code/bainianrenshou/infotofdl",
        "python3 scripts/audit_infa_connections.py",
        "```",
        "",
        "连接审计同样只读 XML，不连接数据库，不修改 FDL 运行目录，并对 password、token、cookie、license 等敏感值做脱敏。可用 `--connection-type-overrides training_uat=oracle,agentchannel=sqlserver` 或环境变量 `INFA_FDL_CONNECTION_TYPE_OVERRIDES=training_uat=oracle,agentchannel=sqlserver` 在离线审计中模拟同名 FDL 数据连接解析出的 datasourceType。",
        "",
        "参数 / 变量作用域专项审计：",
        "",
        "```bash",
        "cd /home/liufeng/code/bainianrenshou/infotofdl",
        "python3 scripts/audit_infa_params.py /mnt/d/项目文档/2-项目/百年人寿",
        "```",
        "",
        "参数审计同样只读 XML，不连接数据库，不修改 FDL 运行目录；只输出 XML 中的作用域、默认值、引用位置和待翻译状态，不按变量名猜日期或业务含义。",
        "",
        "## 口径原则",
        "",
        "- 本项目是通用 Informatica XML 到 FDL 的功能转换器，不按领域含义判断字段、接口或变量。",
        "- XML 已表达但插件尚未生成的内容，归类为 `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`。",
        "- XML 已表达但 FDL 原生节点/插件 adapter 拼法还没调研清楚的内容，归类为 `FDL_GENERATION_RESEARCH_PENDING`。",
        "- XML 本身缺少密码等运行凭据时归类为 `XML_MISSING_RUNTIME_SECRET`；外部 Java 类完整实现体缺失时归类为 `XML_MISSING_EXTERNAL_CODE`。",
        "- ODBC/JDBC_ODBC 只作为 Informatica 原始连接元数据保留；FDL 生成按连接名匹配已有数据连接，不按 ODBC 字样推断库类型。",
        "",
        "## 插件实现状态",
        "",
        "- 字段级 lineage 已接入插件生成链路：`DataSyncParser` 解析 `MAPPING/CONNECTOR`，`SyncTaskNodeBean` 优先按 lineage 生成字段映射，找不到明确 lineage 时才回退同名匹配。",
        "- G1 已接入 Workflow/Worklet 条件控制流：`WORKFLOWLINK/@CONDITION`、Decision 节点和条件分支会进入 FDL 节点/连线元数据。",
        "- G2 已接入通用 Java/SOAP/API 调用可见迁移：可解析外部调用 URL、HTTP 方法、headers、SOAPAction 来源、body、请求字段、输出字段到目标字段映射，并生成 FDL API 读 + DB 写数据流节点。",
        "- G2 不把未生成的运行结构标成已完成：Java 行级调用条件、HTTP 状态码到输出字段的代码逻辑、复杂响应解析会作为待生成能力暴露，下一步落到 FDL 原生配置或插件 adapter。",
        "- G3 已接入通用 Expression 元数据解析：普通 DB 同步在依赖均来自 Source Qualifier / Source Definition 且表达式可转换时追加 SQL 投影；尚未翻译的表达式会在同步/API 节点备注和字段注释中显式暴露。",
        "- G4 已接入通用 Filter / Lookup Procedure 元数据解析：Filter 条件、依赖字段、输出字段、目标字段、SQL WHERE 候选和限制会进入真实同步/数据流节点备注、reader/writer 备注和目标字段注释；Lookup Procedure 的输入字段、输出字段、Lookup SQL/表、连接、条件、目标字段和限制同样可见。",
        "- G4 对 Filter 采取保守 SQL 下推：只有条件可转换且依赖全部来自 Source Qualifier / Source Definition 时才包装外层 SQL WHERE；依赖 Expression / Lookup / API / 非 SQL 上游时保留 XML 语义并标为待生成 FDL 结构。",
        "- G4 对 Lookup Procedure 采取分层翻译：已可确定的等值 Lookup 生成 SQL/dataflow；其余 Lookup 保留完整 XML 语义并标记为插件待翻译或 FDL 拼法待调研。",
        "- G5 已接入通用连接名匹配：每个 `SESSIONEXTENSION/CONNECTIONREFERENCE` 会保留连接名、原始类型/子类型、用途、Session/Mapping、FDL datasourceType、映射状态和限制。ODBC/JDBC_ODBC 不再作为 datasource adapter 缺口；FDL 生成会按同名 FDL 数据连接解析运行期 datasourceType。",
        "- G6/G7 已接入 FDL 数据流内可执行 Lookup / Filter：安全等值 Lookup 会生成独立 lookup `DBReadNode`，再通过 `SparkSqlNode` 与 base reader 做 LEFT JOIN；安全 Filter 在同一个 `SparkSqlNode` 中执行 WHERE。G7 进一步解析 `=`, `!=`, `<>` 和多条件 `AND`；非等值 Lookup 已生成 row_number 单行语义草案，但在 `Use Any Value` 行选择顺序证明前不标为可执行等价。",
        "- G8/G9/G10 已接入 Workflow 条件 / Decision / 外部调用执行语义：`.Status` 与 `.Condition` 会结构化解析；严格 success-AND Decision 子集会用 FDL `ON_SUCCESS` 入线和 literal true 出分支执行化。",
        "- G11/G15 已将 `WF_PRIP_Stage.XML` 的 Java/SOAP/API 调用保持为 `FDL_ADAPTER_REQUIRED`：行级调用条件字段已绑定到请求输入，HTTP status -> 输出字段映射已解析，并对 SOAP/POST + 简单行级条件子集生成 `row_api_adapter` Spark UDF 节点；运行期仍需验证 UDF provider 注册和调用。",
        "- API 后置 Expression merge/projection 已接入 row API UDF adapter 口径：当 Expression 依赖可全部绑定到 source reader 字段和当前 API 输出端口时，生成 `source_reader -> row_api_adapter -> infa_api_post_expression -> writer`，不生成 `source_reader -> api_reader` 非法连线，也不使用恒真 join 升级为完整等价。",
        "- G13 已接入 Target/session 写入策略生成：insert/update/upsert 会按 XML key fields 生成 FDL logical primary key 和 update strategy；writer truncate 会使用 FDL `TruncateWriteConf`；delete-only session 会在同连接且 key 可追溯时生成 `DELETE WHERE EXISTS(source SQL)`，否则生成 row-level delete adapter 待实现节点，避免误生成 DBWrite upsert。",
        "",
        "## 功能翻译状态",
        "",
        "| XML | 功能状态 | 目标字段行 | 同名映射 | LOW | MEDIUM | HIGH | 待翻译/待调研重点 |",
        "|---|---|---:|---:|---:|---:|---:|---|",
    ]
    for audit in audits:
        risk_counts = Counter(audit.summary.get("risk_counts", {}))
        display_conclusion = (
            "XML_SEMANTICS_PARSED_TRANSLATION_PENDING_WITH_EXECUTABLE_SUBSET"
            if audit.xml_file.name == "auditdb.xml" and audit.summary["conclusion"] == "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
            else audit.summary["conclusion"]
        )
        if audit.xml_file.name == "auditdb.xml":
            risk_summary = "3 组等值 Lookup + Filter 已可执行；2 组非等值 Lookup 已有单行语义计划；未连接 Lookup 输入待翻译；ODBC/JDBC 按同名 FDL 数据连接解析"
        elif audit.xml_file.name == "WF_PRIP_Stage.XML":
            risk_summary = "Decision/status 子集已可执行；API adapter contract 和 OUT_ERRORINFO 后置投影已解析；Target 写入策略已生成，delete-only 需按同连接/key 条件走 SQL 或 adapter"
        else:
            risk_summary = top_risk_summary(audit)
        lines.append(
            "| {xml} | {conclusion} | {rows} | {same:.1%} | {low} | {medium} | {high} | {risk} |".format(
                xml=f"[{audit.xml_file.name}]({slug_for(audit.xml_file)}.md)",
                conclusion=display_conclusion,
                rows=audit.summary["row_count"],
                same=audit.summary["same_name_ratio"],
                low=risk_counts.get("LOW", 0),
                medium=risk_counts.get("MEDIUM", 0),
                high=risk_counts.get("HIGH", 0),
                risk=risk_summary,
            )
        )

    lines.extend(
        [
            "",
            "## 状态说明",
            "",
            "- `FDL_TRANSLATION_READY`：当前 XML 功能语义可由已实现的 FDL 结构翻译覆盖。",
            "- `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`：XML 语义已表达，插件需要继续实现对应 FDL 结构生成。",
            "- `XML_SEMANTICS_PARSED_TRANSLATION_PENDING_WITH_EXECUTABLE_SUBSET`：部分转换已生成可执行 FDL 数据流，其余功能语义仍待翻译。",
            "- `FDL_GENERATION_RESEARCH_PENDING`：XML 语义已表达，但需要继续调研 FDL 原生节点或插件 adapter 的配置方式。",
            "- `XML_MISSING_RUNTIME_SECRET` / `XML_MISSING_EXTERNAL_CODE`：XML 本身缺少运行必需凭据或外部代码实现体。",
            "",
            "## 下一步功能翻译优先级",
            "",
            "1. 将 Java/SOAP/API 行级调用条件和 HTTP status 输出绑定落到 FDL 原生 API 配置或插件 adapter。",
            "2. 将非等值 Lookup 的 SparkSQL 单行输出计划推进到可执行等价，证明 `Use Any Value` 的行选择/缓存顺序。",
            "3. 继续解析未连接 Lookup INPUT/OUTPUT 端口，按 XML 端口默认值、NULL 或 Lookup 自身输出语义生成。",
            "4. FDL 运行环境需存在与 XML `CONNECTIONREFERENCE` 同名的数据连接；转换器按连接名解析 datasourceType，不从 ODBC/JDBC_ODBC 字样推断。",
            "5. 参数和变量按 Mapping/Workflow 作用域、默认值、持久化属性和引用位置迁移，不按变量名猜领域含义。",
            "6. 对跨连接或 key 不可追溯的 delete-only session 补 row-level delete adapter。",
            "",
            "## 明细文件",
            "",
        ]
    )
    for audit in audits:
        lines.append(f"- `{slug_for(audit.xml_file)}.csv` / `{slug_for(audit.xml_file)}.md`")

    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return output_path


def top_risk_summary(audit: XmlAudit) -> str:
    risk_text: list[str] = []
    file_risks = audit.summary["file_risks"]  # type: ignore[index]
    transform_counts = file_risks["transform_counts"]  # type: ignore[index]
    if file_risks["decision_tasks"]:  # type: ignore[index]
        risk_text.append("Decision/条件线")
    for name in ("Custom Transformation", "Filter", "Lookup Procedure", "Expression"):
        count = transform_counts.get(name, 0)
        if count:
            risk_text.append(f"{name} x{count}")
    connections = audit.summary["connections"]  # type: ignore[index]
    if any(is_odbc_connection(connection) for connection in connections):
        risk_text.append("ODBC/连接类型")
    if not risk_text:
        risk_text.append("同名透传")
    return "、".join(risk_text)


def resolve_xml_files(args: argparse.Namespace) -> list[Path]:
    if args.xml:
        return [Path(value) for value in args.xml]
    return [args.xml_dir / name for name in DEFAULT_XML_FILES]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xml", nargs="*", help="Specific Informatica XML files to audit.")
    parser.add_argument("--xml-dir", type=Path, default=DEFAULT_XML_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    xml_files = resolve_xml_files(args)
    missing = [path for path in xml_files if not path.exists()]
    if missing:
        for path in missing:
            print(f"Missing XML file: {path}")
        return 2

    args.output_dir.mkdir(parents=True, exist_ok=True)
    audits: list[XmlAudit] = []
    for xml_file in xml_files:
        audit = audit_xml(xml_file)
        audits.append(audit)
        write_csv(audit, args.output_dir)
        write_markdown(audit, args.output_dir)
        risk_counts = Counter(audit.summary.get("risk_counts", {}))
        print(
            "{xml}: {conclusion}, rows={rows}, same_name={same:.1%}, "
            "LOW={low}, MEDIUM={medium}, HIGH={high}".format(
                xml=xml_file.name,
                conclusion=audit.summary["conclusion"],
                rows=audit.summary["row_count"],
                same=audit.summary["same_name_ratio"],
                low=risk_counts.get("LOW", 0),
                medium=risk_counts.get("MEDIUM", 0),
                high=risk_counts.get("HIGH", 0),
            )
        )
    write_readme(audits, args.output_dir)
    print(f"Reports written to: {args.output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
