#!/usr/bin/env python3
"""Read-only Informatica connection reference audit."""

from __future__ import annotations

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path


DEFAULT_XML_DIR = Path("/mnt/c/Users/nifen/Desktop/项目文档/2-项目/百年人寿")
DEFAULT_XML_FILES = [
    "cc_lis_20260521.xml",
    "WF_HNC.XML",
    "WF_PRIP_Stage.XML",
    "auditdb.xml",
]
OVERRIDE_ENV = "INFA_FDL_CONNECTION_TYPE_OVERRIDES"
STATUS_XML_SEMANTICS_PARSED_TRANSLATION_PENDING = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
STATUS_FDL_GENERATION_RESEARCH_PENDING = "FDL_GENERATION_RESEARCH_PENDING"
STATUS_FDL_ADAPTER_REQUIRED = "FDL_ADAPTER_REQUIRED"
STATUS_FDL_TRANSLATION_READY = "FDL_TRANSLATION_READY"
SQL_ATTRIBUTE_NAMES = ("Sql Query", "Pre SQL", "Post SQL")
SENSITIVE_RE = re.compile(r"(?i)(password|passwd|pwd|token|cookie|license|secret)(\s*[=:]\s*)[^\s&\"'<>]+")


@dataclass(frozen=True)
class ConnectionRef:
    xml_file: str
    session_name: str
    mapping_name: str
    connection_name: str
    connection_type: str
    connection_subtype: str
    raw_sql: str
    extension_type: str
    extension_name: str
    transformation_name: str
    transformation_type: str
    fdl_datasource_type: str
    mapping_status: str
    mapping_basis: str
    limitation: str


def redact(value: str | None) -> str:
    if not value:
        return ""
    return SENSITIVE_RE.sub(r"\1\2[REDACTED]", value)


def parse_xml(path: Path) -> ET.Element:
    raw = path.read_bytes()
    head = raw[:200].decode("ascii", "ignore")
    match = re.search(r'encoding=["\']([^"\']+)["\']', head, re.I)
    encoding = match.group(1) if match else "utf-8"
    codec = "gb18030" if encoding.lower() in {"gb2312", "gbk", "gb18030"} else encoding
    text = raw.decode(codec)
    text = re.sub(r'encoding=["\'][^"\']+["\']', 'encoding="utf-8"', text, count=1, flags=re.I)
    return ET.fromstring(text.encode("utf-8"))


def normalize_key(value: str | None) -> str:
    return re.sub(r"[^A-Za-z0-9]+", "", (value or "").strip()).upper()


def map_known_fdl_datasource_type(raw_type: str | None) -> str:
    normalized = normalize_key(raw_type)
    if not normalized:
        return ""
    if "ORACLE" in normalized:
        return "oracle"
    if "MYSQL" in normalized:
        return "mysql"
    if "POSTGRESQL" in normalized or normalized == "POSTGRES":
        return "postgresql"
    if "SQLSERVER" in normalized or "MSSQL" in normalized or "MICROSOFTSQLSERVER" in normalized:
        return "sqlserver"
    return ""


def normalize_fdl_datasource_type(raw_type: str | None) -> str:
    known = map_known_fdl_datasource_type(raw_type)
    if known:
        return known
    normalized = re.sub(r"[^a-z0-9]+", "", (raw_type or "").strip().lower())
    return normalized


def is_odbc(connection_type: str | None, connection_subtype: str | None) -> bool:
    normalized = f"{normalize_key(connection_type)} {normalize_key(connection_subtype)}"
    return "ODBC" in normalized or "JDBCODBC" in normalized


def override_map(spec: str | None) -> dict[str, str]:
    result: dict[str, str] = {}
    if not spec:
        return result
    for entry in re.split(r"[,;]", spec):
        entry = entry.strip()
        if not entry:
            continue
        if "=" in entry:
            name, value = entry.split("=", 1)
        elif ":" in entry:
            name, value = entry.split(":", 1)
        else:
            continue
        result[normalize_key(name)] = value.strip()
    return result


def sql_attribute_value(element: ET.Element | None) -> str:
    if element is None:
        return ""
    for attr_name in SQL_ATTRIBUTE_NAMES:
        for child in element.iter():
            if child.tag not in {"ATTRIBUTE", "TABLEATTRIBUTE"}:
                continue
            if child.get("NAME") == attr_name:
                value = child.get("VALUE", "")
                if value:
                    return value
    return ""


def find_mapping(root: ET.Element, mapping_name: str) -> ET.Element | None:
    if not mapping_name:
        return None
    for mapping in root.findall(".//MAPPING"):
        if mapping.get("NAME", "") == mapping_name:
            return mapping
    return None


def transformation_matches(element: ET.Element, transformation_name: str) -> bool:
    if not transformation_name:
        return False
    candidates = {
        element.get("NAME", ""),
        element.get("TRANSFORMATIONNAME", ""),
        element.get("TRANSFORMATION_NAME", ""),
        element.get("SINSTANCENAME", ""),
        element.get("DSQINSTNAME", ""),
    }
    return transformation_name in candidates


def mapping_transformation_sql(mapping: ET.Element | None, transformation_name: str) -> str:
    if mapping is None:
        return ""
    if transformation_name:
        for child in mapping.iter():
            if child.tag in {"TRANSFORMATION", "INSTANCE"} and transformation_matches(child, transformation_name):
                value = sql_attribute_value(child)
                if value:
                    return value
    for trans in mapping.findall(".//TRANSFORMATION"):
        if trans.get("TYPE", "") == "Source Qualifier":
            value = sql_attribute_value(trans)
            if value:
                return value
    return sql_attribute_value(mapping)


def extract_raw_sql(root: ET.Element, mapping_name: str, transformation_name: str, extension: ET.Element) -> str:
    value = sql_attribute_value(extension)
    if value:
        return value
    extension_type = extension.get("TYPE", "")
    transformation_type = extension.get("TRANSFORMATIONTYPE", "")
    if extension_type not in {"READER", "LOOKUPEXTENSION"} and transformation_type != "Source Qualifier":
        return ""
    return mapping_transformation_sql(find_mapping(root, mapping_name), transformation_name)


def markdown_cell(value: str | None) -> str:
    text = redact(value or "").strip()
    if not text:
        return "-"
    return text.replace("|", "\\|").replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>")


def resolve_mapping(connection_name: str, connection_type: str, connection_subtype: str, overrides: dict[str, str]) -> tuple[str, str, str, str]:
    configured = overrides.get(normalize_key(connection_name), "")
    if configured:
        configured_fdl = map_known_fdl_datasource_type(configured)
        if configured_fdl:
            return (
                configured_fdl,
                STATUS_FDL_TRANSLATION_READY,
                "connection-name override; FDL datasourceType ready",
                "Connection type is supplied by explicit connection-name override; verify the FDL connection points to the same real database.",
            )
        return (
            normalize_fdl_datasource_type(configured),
            STATUS_FDL_GENERATION_RESEARCH_PENDING,
            "connection-name override with unrecognized datasource type",
            "Configured datasource type is not in the safe mapping list; add an FDL datasourceType mapping or generic JDBC adapter before running.",
        )

    safe = map_known_fdl_datasource_type(connection_subtype) or map_known_fdl_datasource_type(connection_type)
    if safe:
        return safe, STATUS_FDL_TRANSLATION_READY, "recognized Informatica connection subtype; FDL datasourceType ready", ""

    if is_odbc(connection_type, connection_subtype):
        return (
            "runtime_connection_by_name",
            STATUS_FDL_TRANSLATION_READY,
            "FDL data connection name match; datasourceType resolved from the existing FDL connection at import/runtime",
            "",
        )

    raw_type = connection_subtype or connection_type
    if not raw_type:
        return (
            "",
            STATUS_XML_SEMANTICS_PARSED_TRANSLATION_PENDING,
            "connection reference has no datasource type/subtype in XML",
            "XML connection reference was parsed, but datasource type/subtype is missing; preserve connection name and SQL while adding mapping evidence.",
        )
    return (
        normalize_fdl_datasource_type(raw_type),
        STATUS_FDL_GENERATION_RESEARCH_PENDING,
        "unrecognized Informatica connection type",
        "Original connection type is not in the safe mapping list; implement an FDL datasourceType mapping before running.",
    )


def collect_connections(path: Path, overrides: dict[str, str]) -> list[ConnectionRef]:
    root = parse_xml(path)
    refs: list[ConnectionRef] = []
    seen: set[tuple[str, ...]] = set()
    for session in root.findall(".//SESSION"):
        session_name = session.get("NAME", "")
        mapping_name = session.get("MAPPINGNAME", "")
        for extension in session.findall(".//SESSIONEXTENSION"):
            extension_type = extension.get("TYPE", "")
            extension_name = extension.get("NAME", "")
            transformation_name = extension.get("SINSTANCENAME") or extension.get("DSQINSTNAME") or ""
            transformation_type = extension.get("TRANSFORMATIONTYPE", "")
            raw_sql = extract_raw_sql(root, mapping_name, transformation_name, extension)
            for conn in extension.findall("CONNECTIONREFERENCE"):
                connection_name = redact(conn.get("CONNECTIONNAME", ""))
                if not connection_name:
                    continue
                connection_type = redact(conn.get("CONNECTIONTYPE", ""))
                connection_subtype = redact(conn.get("CONNECTIONSUBTYPE", ""))
                fdl_type, status, basis, limitation = resolve_mapping(
                    connection_name,
                    connection_type,
                    connection_subtype,
                    overrides,
                )
                key = (
                    session_name,
                    mapping_name,
                    connection_name,
                    extension_type,
                    transformation_name,
                    transformation_type,
                    connection_type,
                    connection_subtype,
                    raw_sql,
                )
                if key in seen:
                    continue
                seen.add(key)
                refs.append(
                    ConnectionRef(
                        xml_file=path.name,
                        session_name=session_name,
                        mapping_name=mapping_name,
                        connection_name=connection_name,
                        connection_type=connection_type,
                        connection_subtype=connection_subtype,
                        raw_sql=redact(raw_sql),
                        extension_type=extension_type,
                        extension_name=extension_name,
                        transformation_name=transformation_name,
                        transformation_type=transformation_type,
                        fdl_datasource_type=fdl_type,
                        mapping_status=status,
                        mapping_basis=basis,
                        limitation=redact(limitation),
                    )
                )
    return refs


def print_report(path: Path, refs: list[ConnectionRef]) -> None:
    risk_count = sum(1 for ref in refs if ref.limitation or ref.mapping_status != STATUS_FDL_TRANSLATION_READY)
    lookup_count = sum(1 for ref in refs if ref.extension_type == "LOOKUPEXTENSION")
    print(f"# Informatica connection audit: {path.name}")
    print("read_only=true sensitive_values=redacted")
    print(f"summary: connection_refs={len(refs)} lookup_refs={lookup_count} risks={risk_count}")
    if not refs:
        print()
        return
    print()
    print("| Session | Mapping | Extension | Transformation | Connection | Original type | Raw SQL | FDL datasourceType | Status | Basis | Limitation |")
    print("|---|---|---|---|---|---|---|---|---|---|---|")
    for ref in refs:
        original = f"{ref.connection_type or '-'}/{ref.connection_subtype or '-'}"
        extension = f"{ref.extension_type or '-'}"
        transformation = f"{ref.transformation_type or '-'}/{ref.transformation_name or '-'}"
        print(
            "| {session} | {mapping} | {extension} | {transformation} | {connection} | {original} | {raw_sql} | {fdl} | {status} | {basis} | {limitation} |".format(
                session=markdown_cell(ref.session_name),
                mapping=markdown_cell(ref.mapping_name),
                extension=markdown_cell(extension),
                transformation=markdown_cell(transformation),
                connection=markdown_cell(ref.connection_name),
                original=markdown_cell(original),
                raw_sql=markdown_cell(ref.raw_sql),
                fdl=markdown_cell(ref.fdl_datasource_type),
                status=markdown_cell(ref.mapping_status),
                basis=markdown_cell(ref.mapping_basis),
                limitation=markdown_cell(ref.limitation),
            )
        )
    print()


def resolve_xml_files(args: argparse.Namespace) -> list[Path]:
    if args.xml:
        return [Path(value) for value in args.xml]
    return [args.xml_dir / name for name in DEFAULT_XML_FILES]


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xml", nargs="*", help="Specific Informatica XML files to audit.")
    parser.add_argument("--xml-dir", type=Path, default=DEFAULT_XML_DIR)
    parser.add_argument(
        "--connection-type-overrides",
        default=os.environ.get(OVERRIDE_ENV, ""),
        help="Comma-separated connectionName=datasourceType overrides for offline audit only, e.g. training_uat=oracle,agentchannel=sqlserver.",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    xml_files = resolve_xml_files(args)
    missing = [path for path in xml_files if not path.exists()]
    if missing:
        for path in missing:
            print(f"missing XML: {path}", file=sys.stderr)
        return 2
    overrides = override_map(args.connection_type_overrides)
    for path in xml_files:
        print_report(path, collect_connections(path, overrides))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
