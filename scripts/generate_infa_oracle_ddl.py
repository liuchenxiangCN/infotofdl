#!/usr/bin/env python3
"""Generate Oracle DDL from Informatica PowerCenter XML definitions.

The output is meant for local FDL UI validation: create enough source/target
tables for FDL to refresh metadata and field mappings.
"""

from __future__ import annotations

import argparse
import re
import xml.etree.ElementTree as ET
from collections import OrderedDict
from pathlib import Path

SQL_ALIAS_STOP_WORDS = {
    "WHERE",
    "JOIN",
    "INNER",
    "LEFT",
    "RIGHT",
    "FULL",
    "CROSS",
    "ON",
    "GROUP",
    "ORDER",
    "UNION",
    "MINUS",
    "INTERSECT",
    "CONNECT",
    "START",
}


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


def direct_children(element: ET.Element, tag: str) -> list[ET.Element]:
    return [child for child in list(element) if child.tag == tag]


def ident(name: str) -> str:
    return name.strip().upper()


def safe_constraint_name(prefix: str, table_name: str) -> str:
    clean = re.sub(r"[^A-Z0-9_]", "_", ident(table_name))
    return (prefix + "_" + clean)[:30]


def oracle_type(field: ET.Element) -> str:
    raw_type = (field.get("DATATYPE") or "").lower()
    precision = field.get("PRECISION") or ""
    scale = field.get("SCALE") or ""

    def int_or_none(value: str) -> int | None:
        try:
            return int(value)
        except (TypeError, ValueError):
            return None

    p = int_or_none(precision)
    s = int_or_none(scale)

    if "date" == raw_type:
        return "DATE"
    if "timestamp" in raw_type:
        return "TIMESTAMP"
    if raw_type in ("varchar2", "nvarchar2", "varchar", "string"):
        length = p or 255
        if length <= 0:
            length = 255
        if length > 4000:
            return "CLOB"
        return f"VARCHAR2({length})"
    if raw_type == "char" or raw_type == "nchar":
        length = p or 1
        if length <= 0:
            length = 1
        return f"CHAR({length})"
    if raw_type.startswith("number") or raw_type in ("integer", "int", "decimal", "double"):
        if p and p > 0 and p <= 38:
            if s and s > 0:
                return f"NUMBER({p},{s})"
            return f"NUMBER({p})"
        return "NUMBER"
    if raw_type in ("long", "clob", "text"):
        return "CLOB"
    if raw_type == "raw":
        length = p or 2000
        return f"RAW({length})"
    return "VARCHAR2(4000)"


def find_by_name(elements: list[ET.Element], name: str | None) -> ET.Element | None:
    if not name:
        return None
    for element in elements:
        if element.get("NAME") == name:
            return element
    return None


def table_name(table_element: ET.Element) -> str:
    return table_element.get("TABLE_NAME") or table_element.get("NAME") or ""


def fields_for_table(table_element: ET.Element) -> list[ET.Element]:
    fields = direct_children(table_element, "SOURCEFIELD")
    if fields:
        return fields
    return direct_children(table_element, "TARGETFIELD")


def create_table_statement(table_element: ET.Element, override_name: str | None = None) -> str:
    name = ident(override_name or table_name(table_element))
    fields = fields_for_table(table_element)
    if not fields:
        return f"-- No fields found for {name}"

    lines: list[str] = []
    pk_columns: list[str] = []
    for field in fields:
        field_name = ident(field.get("NAME") or "")
        if not field_name:
            continue
        nullable = " NOT NULL" if field.get("KEYTYPE") == "PRIMARY KEY" else ""
        lines.append(f"    {field_name} {oracle_type(field)}{nullable}")
        if field.get("KEYTYPE") == "PRIMARY KEY":
            pk_columns.append(field_name)

    if pk_columns:
        constraint = safe_constraint_name("PK", name)
        lines.append(f"    CONSTRAINT {constraint} PRIMARY KEY ({', '.join(pk_columns)})")

    return "CREATE TABLE {name} (\n{columns}\n);".format(
        name=name,
        columns=",\n".join(lines),
    )


def inferred_oracle_type(column_name: str) -> str:
    name = ident(column_name)
    if "DATE" in name or name.endswith("DT"):
        return "DATE"
    if "TIME" in name:
        return "VARCHAR2(8)"
    if any(token in name for token in ("AMNT", "AMOUNT", "PREM", "FEE", "RATE")):
        return "NUMBER"
    if name.endswith("FLAG") or name.endswith("TYPE"):
        return "VARCHAR2(10)"
    if name.endswith("NO") or name.endswith("CODE") or name.endswith("ID"):
        return "VARCHAR2(50)"
    return "VARCHAR2(4000)"


def create_dependency_table_statement(table_name_value: str, columns: list[str]) -> str:
    name = ident(table_name_value)
    local_columns = columns or ["DUMMY_ID"]
    lines = [
        f"    {ident(column)} {inferred_oracle_type(column)}"
        for column in local_columns
    ]
    return "CREATE TABLE {name} (\n{columns}\n);".format(
        name=name,
        columns=",\n".join(lines),
    )


def connection_for_session(session: ET.Element, role: str) -> str | None:
    for extension in session.findall(".//SESSIONEXTENSION"):
        if extension.get("TYPE") != role:
            continue
        refs = extension.findall(".//CONNECTIONREFERENCE")
        if refs:
            return refs[0].get("CONNECTIONNAME")
    return None


def mapping_by_name(root: ET.Element) -> dict[str, ET.Element]:
    return {mapping.get("NAME") or "": mapping for mapping in root.findall(".//MAPPING")}


def collect_tables(root: ET.Element) -> OrderedDict[str, OrderedDict[str, ET.Element]]:
    source_defs = list(root.findall(".//SOURCE"))
    target_defs = list(root.findall(".//TARGET"))
    mappings = mapping_by_name(root)
    by_connection: OrderedDict[str, OrderedDict[str, ET.Element]] = OrderedDict()

    for session in root.findall(".//SESSION"):
        mapping = mappings.get(session.get("MAPPINGNAME") or "")
        if mapping is None:
            continue

        reader = connection_for_session(session, "READER")
        writer = connection_for_session(session, "WRITER")

        for instance in direct_children(mapping, "INSTANCE"):
            instance_type = instance.get("TYPE")
            transformation_name = instance.get("TRANSFORMATION_NAME") or instance.get("NAME")
            if instance_type == "SOURCE" and reader:
                source = find_by_name(source_defs, transformation_name)
                if source is not None:
                    by_connection.setdefault(reader, OrderedDict())[ident(table_name(source))] = source
            if instance_type == "TARGET" and writer:
                target = find_by_name(target_defs, transformation_name)
                if target is not None:
                    instance_name = instance.get("NAME") or table_name(target)
                    by_connection.setdefault(writer, OrderedDict())[ident(instance_name)] = target

    return by_connection


def sql_query_attributes(element: ET.Element) -> list[str]:
    values: list[str] = []
    for child in element.findall(".//TABLEATTRIBUTE") + element.findall(".//ATTRIBUTE"):
        if (child.get("NAME") or "").lower() == "sql query":
            value = child.get("VALUE") or ""
            if value.strip():
                values.append(value)
    return values


def source_qualifier_sqls(mapping: ET.Element, session: ET.Element) -> list[str]:
    sqls: list[str] = []
    for transformation in direct_children(mapping, "TRANSFORMATION"):
        if transformation.get("TYPE") == "Source Qualifier":
            sqls.extend(sql_query_attributes(transformation))
    for instance in session.findall(".//SESSTRANSFORMATIONINST"):
        if instance.get("TRANSFORMATIONTYPE") == "Source Qualifier":
            sqls.extend(sql_query_attributes(instance))
    return sqls


def strip_schema(table_name_value: str) -> str:
    return table_name_value.split(".")[-1]


def known_table_names(root: ET.Element, tables: OrderedDict[str, OrderedDict[str, ET.Element]]) -> set[str]:
    known = set()
    for element in root.findall(".//SOURCE") + root.findall(".//TARGET"):
        known.add(ident(table_name(element)))
        known.add(ident(element.get("NAME") or ""))
    for table_map in tables.values():
        known.update(table_map.keys())
    return {name for name in known if name}


def extract_sql_table_dependencies(sql: str, known_names: set[str]) -> OrderedDict[str, list[str]]:
    dependencies: OrderedDict[str, list[str]] = OrderedDict()
    alias_to_table: OrderedDict[str, str] = OrderedDict()
    table_pattern = re.compile(
        r"\b(?:FROM|JOIN)\s+([A-Za-z_][\w$#]*(?:\.[A-Za-z_][\w$#]*)?)"
        r"(?:\s+(?:AS\s+)?([A-Za-z_][\w$#]*))?",
        re.IGNORECASE,
    )

    for match in table_pattern.finditer(sql):
        table = ident(strip_schema(match.group(1)))
        alias = ident(match.group(2) or table)
        if alias in SQL_ALIAS_STOP_WORDS:
            alias = table
        alias_to_table[alias] = table
        if table not in known_names:
            dependencies.setdefault(table, [])

    for alias, table in alias_to_table.items():
        if table not in dependencies:
            continue
        column_pattern = re.compile(
            r"\b" + re.escape(alias) + r"\.([A-Za-z_][\w$#]*)",
            re.IGNORECASE,
        )
        for column in column_pattern.findall(sql):
            column_name = ident(column)
            if column_name not in dependencies[table]:
                dependencies[table].append(column_name)

    return dependencies


def collect_sql_dependencies(
    root: ET.Element,
    tables: OrderedDict[str, OrderedDict[str, ET.Element]],
) -> OrderedDict[str, OrderedDict[str, list[str]]]:
    mappings = mapping_by_name(root)
    known_names = known_table_names(root, tables)
    by_connection: OrderedDict[str, OrderedDict[str, list[str]]] = OrderedDict()

    for session in root.findall(".//SESSION"):
        mapping = mappings.get(session.get("MAPPINGNAME") or "")
        if mapping is None:
            continue
        reader = connection_for_session(session, "READER")
        if not reader:
            continue
        for sql in source_qualifier_sqls(mapping, session):
            dependencies = extract_sql_table_dependencies(sql, known_names)
            for table, columns in dependencies.items():
                table_map = by_connection.setdefault(reader, OrderedDict())
                existing_columns = table_map.setdefault(table, [])
                for column in columns:
                    if column not in existing_columns:
                        existing_columns.append(column)

    return by_connection


def has_callcenter_package_pre_sql(root: ET.Element) -> bool:
    for attr in root.findall(".//ATTRIBUTE"):
        value = attr.get("VALUE") or ""
        if "PKG_newBusinessDealDetail.dealReturnResultInformatica" in value:
            return True
    return False


def package_stub_sql() -> str:
    return """-- Optional test-only stub for Pre SQL in cc_lis_20260521.xml.
-- Run this only in a disposable local verification schema if task execution
-- fails because PKG_newBusinessDealDetail is absent.

CREATE OR REPLACE PACKAGE PKG_newBusinessDealDetail AS
    FUNCTION dealReturnResultInformatica(p_date VARCHAR2, p_type VARCHAR2)
        RETURN VARCHAR2;
END PKG_newBusinessDealDetail;
/

CREATE OR REPLACE PACKAGE BODY PKG_newBusinessDealDetail AS
    FUNCTION dealReturnResultInformatica(p_date VARCHAR2, p_type VARCHAR2)
        RETURN VARCHAR2
    IS
    BEGIN
        RETURN 'OK';
    END;
END PKG_newBusinessDealDetail;
/
"""


def write_outputs(xml_path: Path, output_dir: Path) -> None:
    root = load_xml(xml_path)
    output_dir.mkdir(parents=True, exist_ok=True)
    for stale_dependency in output_dir.glob(f"{xml_path.stem}__*_sql_dependencies.sql"):
        stale_dependency.unlink()
    tables = collect_tables(root)
    sql_dependencies = collect_sql_dependencies(root, tables)

    readme_lines = [
        f"# Local DDL for {xml_path.name}",
        "",
        "These files are generated from Informatica SOURCE/TARGET metadata.",
        "Use them only in disposable local verification schemas.",
        "",
        "Purpose: let FDL refresh source/target fields and verify field mapping in UI.",
        "",
        "Generated SQL files:",
        "",
    ]

    for connection, table_map in tables.items():
        file_name = f"{xml_path.stem}__{connection}.sql"
        sql_path = output_dir / file_name
        statements = [
            f"-- Connection: {connection}",
            f"-- Source XML: {xml_path.name}",
            "-- Execute in the schema used by this FDL data connection.",
            "-- If tables already exist in a real environment, do not run this file.",
            "",
        ]
        for local_table_name, table in table_map.items():
            source_name = ident(table_name(table))
            if local_table_name != source_name:
                statements.append(f"-- Table: {local_table_name} (from target definition {source_name})")
            else:
                statements.append(f"-- Table: {local_table_name}")
            statements.append(create_table_statement(table, local_table_name))
            statements.append("")
        sql_path.write_text("\n".join(statements), encoding="utf-8")
        readme_lines.append(f"- `{file_name}`: {connection}, {len(table_map)} tables")

    for connection, dependency_map in sql_dependencies.items():
        file_name = f"{xml_path.stem}__{connection}_sql_dependencies.sql"
        sql_path = output_dir / file_name
        statements = [
            f"-- Connection: {connection}",
            f"-- Source XML: {xml_path.name}",
            "-- Extra tables referenced by Source Qualifier SQL override.",
            "-- Execute in the schema used by this FDL source data connection.",
            "",
        ]
        for dependency_table, columns in dependency_map.items():
            statements.append(f"-- Table: {dependency_table}")
            statements.append(create_dependency_table_statement(dependency_table, columns))
            statements.append("")
        sql_path.write_text("\n".join(statements), encoding="utf-8")
        readme_lines.append(
            f"- `{file_name}`: {connection}, {len(dependency_map)} SQL dependency tables"
        )

    if has_callcenter_package_pre_sql(root):
        stub_path = output_dir / f"{xml_path.stem}__optional_lis_package_stub.sql"
        stub_path.write_text(package_stub_sql(), encoding="utf-8")
        readme_lines.append(
            f"- `{stub_path.name}`: optional package stub for local execution only"
        )

    readme_lines.extend(
        [
            "",
            "Recommended UI verification sequence:",
            "",
            "1. Run the SQL for each connection against the database/schema backing that FDL connection.",
            "2. Recreate or reopen the generated FDL tasks.",
            "3. Open each data sync node and refresh/check field mapping.",
            "4. If task execution fails on missing package/function, use the optional stub only in a local test schema.",
            "",
        ]
    )
    (output_dir / "README.md").write_text("\n".join(readme_lines), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("xml", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    write_outputs(args.xml, args.out)


if __name__ == "__main__":
    main()
