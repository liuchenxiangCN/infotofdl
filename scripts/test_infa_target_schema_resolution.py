#!/usr/bin/env python3
"""Regression checks for writer target-schema resolution.

The production rule is generic:
TARGET OWNERNAME > Oracle writer connection user. Non-Oracle connections may
use the FDL writer connection default schema.
SOURCE OWNERNAME is reader metadata and must never populate DBWrite.toSchema.
"""

from __future__ import annotations

import argparse
import xml.etree.ElementTree as ET
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_XML = Path("/mnt/d/项目文档/2-项目/百年人寿/WF_PRIP_Stage.XML")
DATA_SYNC_PARSER = REPO_ROOT / "plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/util/xml/DataSyncParser.java"
SYNC_TASK_NODE = REPO_ROOT / "plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/SyncTaskNodeBean.java"
TRANSFORM_SYNC_TASK_NODE = REPO_ROOT / "plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/TransformSyncTaskNodeBean.java"
API_SYNC_TASK_NODE = REPO_ROOT / "plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/ApiSyncTaskNodeBean.java"
MAPPING_NAME = "M_PRIP_Stage_LLCommendHospital"
SESSION_NAME = "s_M_PRIP_Stage_LLCommendHospital"
TABLE_NAME = "LLCOMMENDHOSPITAL"


def parse_xml(path: Path) -> ET.Element:
    raw = path.read_bytes()
    text = raw.decode("gb18030")
    declaration_end = text.find("?>")
    if declaration_end >= 0:
        text = text[declaration_end + 2 :]
    return ET.fromstring(text)


def find_named(root: ET.Element, tag: str, name: str) -> ET.Element:
    for element in root.iter(tag):
        if element.attrib.get("NAME") == name:
            return element
    raise AssertionError(f"missing {tag} NAME={name}")


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1 : index]
    raise AssertionError(f"unterminated Java method: {signature}")


def assert_fixture_contract(root: ET.Element) -> None:
    source = find_named(root, "SOURCE", TABLE_NAME)
    target = find_named(root, "TARGET", TABLE_NAME)
    mapping = find_named(root, "MAPPING", MAPPING_NAME)
    session = find_named(root, "SESSION", SESSION_NAME)

    if source.attrib.get("OWNERNAME") != "PRIP":
        raise AssertionError("fixture source owner must remain PRIP")
    if target.attrib.get("OWNERNAME", ""):
        raise AssertionError("fixture target must not declare OWNERNAME")

    instance_types = {
        item.attrib.get("TYPE")
        for item in mapping.findall("INSTANCE")
        if item.attrib.get("TRANSFORMATION_NAME") == TABLE_NAME
    }
    if instance_types != {"SOURCE", "TARGET"}:
        raise AssertionError(f"fixture mapping source/target instances changed: {instance_types}")

    writer_connections = []
    reader_connections = []
    for extension in session.findall("SESSIONEXTENSION"):
        references = extension.findall("CONNECTIONREFERENCE")
        names = [item.attrib.get("CONNECTIONNAME", "") for item in references]
        if extension.attrib.get("TYPE") == "WRITER":
            writer_connections.extend(names)
        elif extension.attrib.get("TYPE") == "READER":
            reader_connections.extend(names)
    if writer_connections != ["prip_155"]:
        raise AssertionError(f"fixture writer connection changed: {writer_connections}")
    if "lis_uat152" not in reader_connections:
        raise AssertionError(f"fixture reader connection changed: {reader_connections}")


def assert_java_contract() -> None:
    source = DATA_SYNC_PARSER.read_text(encoding="utf-8")
    explicit_method = method_body(source, "private static void extractExplicitTargetSchemaFromMapping")
    target_info_method = method_body(source, "private static void extractTargetInfoFromMapping")
    target_resolve_method = method_body(source, "private static String resolveTargetSchema")
    resolve_method = method_body(source, "private static String resolveConnectionSchema")
    oracle_resolve_block = method_body(resolve_method, "if (oracle)")

    if "extractOwnerNameFromSource" in explicit_method or '"SOURCE".equals' in explicit_method:
        raise AssertionError("explicit target schema extraction still reads SOURCE metadata")
    if "extractOwnerNameFromSource" in target_info_method or '"SOURCE".equals' in target_info_method:
        raise AssertionError("target-info fallback still copies SOURCE owner into target schema")
    if "FineUtils.getDefaultSchema(connectionName)" not in resolve_method:
        raise AssertionError("writer connection default schema is not consulted")
    if "FineUtils.getConnectionUserName(connectionName)" not in resolve_method:
        raise AssertionError("writer connection user fallback is missing")
    if resolve_method.index("FineUtils.getConnectionUserName") > resolve_method.index("FineUtils.getDefaultSchema"):
        raise AssertionError("Oracle writer connection user does not precede unreliable default schema")
    if "FineUtils.getDefaultSchema" in oracle_resolve_block:
        raise AssertionError("Oracle writer mode still consults unreliable FDL default schema")
    if "FineUtils.getConnectionUserName" not in oracle_resolve_block:
        raise AssertionError("Oracle writer mode does not read the target connection user")
    if 'WriterTargetSchemaPolicy.select("", userName, "")' not in resolve_method:
        raise AssertionError("Oracle writer mode is not selected from the target connection user")
    if 'WriterTargetSchemaPolicy.select("", "", defaultSchema)' not in resolve_method:
        raise AssertionError("non-Oracle writer default schema path is missing")
    if "WriterTargetSchemaPolicy.select" not in source:
        raise AssertionError("writer target schema policy is not wired into DataSyncParser")
    if '"oracle".equalsIgnoreCase(toDBType)' not in source:
        raise AssertionError("writer connection user fallback is not restricted to Oracle")
    if target_resolve_method.index("explicitTargetOwner") > target_resolve_method.index("resolveSchema"):
        raise AssertionError("explicit TARGET owner does not short-circuit connection schema resolution")
    if ".schema(targetSchema)" not in source:
        raise AssertionError("resolved target schema is not passed to DBNodeToInfoBean")

    for path in (SYNC_TASK_NODE, TRANSFORM_SYNC_TASK_NODE, API_SYNC_TASK_NODE):
        node_source = path.read_text(encoding="utf-8")
        if "setToSchema" not in node_source or "getSchema()" not in node_source:
            raise AssertionError(f"writer schema wiring missing from {path.name}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate writer target-schema resolution guardrails.")
    parser.add_argument("xml", nargs="?", type=Path, default=DEFAULT_XML)
    args = parser.parse_args()
    assert_fixture_contract(parse_xml(args.xml))
    assert_java_contract()
    print("PASS target schema fixture: SOURCE PRIP cannot override writer connection prip_155")
    print("PASS target schema Java contract: TARGET owner > Oracle writer user; non-Oracle default schema")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
