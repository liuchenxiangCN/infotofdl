#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
#
# --- How to run ---
# python3 scripts/test_infa_dataflow_status.py --unsupported-expression-probe
# python3 scripts/test_infa_dataflow_status.py --xml-dir "/mnt/d/项目文档/2-项目/百年人寿"

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from audit_infa_expressions import audit_file as audit_expressions  # noqa: E402
from audit_infa_expressions import classify  # noqa: E402
from audit_infa_field_lineage import audit_xml, clean, load_xml, norm, yes  # noqa: E402

DEFAULT_XML_DIR = Path("/mnt/d/项目文档/2-项目/百年人寿")
CC_LIS = "cc_lis_20260521.xml"
WF_HNC = "WF_HNC.XML"
STATUS_READY = "FDL_TRANSLATION_READY"
STATUS_PENDING = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"


@dataclass(frozen=True, slots=True)
class HarnessError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


@dataclass(frozen=True, slots=True)
class ExpectedDataflow:
    xml_name: str
    status: str
    expression_fields: int
    source_qualifiers: int
    expressions: int
    pre_sql: int
    post_sql: int
    truncate_yes: int
    update_targets: int
    insert_targets: int
    delete_targets: int


EXPECTED: tuple[ExpectedDataflow, ...] = (
    ExpectedDataflow(CC_LIS, STATUS_READY, 0, 4, 0, 1, 0, 0, 4, 0, 0),
    ExpectedDataflow(WF_HNC, STATUS_PENDING, 105, 1, 1, 1, 1, 0, 1, 0, 0),
)


def attrs_named(root: ET.Element, name: str) -> list[ET.Element]:
    attrs = [*root.findall(".//ATTRIBUTE"), *root.findall(".//TABLEATTRIBUTE")]
    return [item for item in attrs if clean(item.get("NAME")) == name and clean(item.get("VALUE"))]


def compact_post_sql_count(root: ET.Element) -> int:
    return sum(1 for item in attrs_named(root, "Post SQL") if "\n" not in clean(item.get("VALUE")))


def path_for(xml_dir: Path, xml_name: str) -> Path:
    path = xml_dir / xml_name
    if not path.is_file():
        raise HarnessError(f"missing XML file: {path}")
    return path


def assert_equal(label: str, actual: int | str, expected: int | str) -> None:
    if actual != expected:
        raise HarnessError(f"{label}: expected={expected} actual={actual}")


def assert_file_status(xml_dir: Path, expected: ExpectedDataflow) -> None:
    path = path_for(xml_dir, expected.xml_name)
    root = load_xml(path)
    lineage = audit_xml(path)
    expression_rows = audit_expressions(path)
    transforms = Counter(clean(item.get("TYPE")) for item in root.findall(".//TRANSFORMATION"))
    write_infos = list(lineage.summary["target_write_infos"])

    assert_equal(f"{expected.xml_name} status", str(lineage.summary["conclusion"]), expected.status)
    assert_equal(f"{expected.xml_name} expression_fields", len(expression_rows), expected.expression_fields)
    assert_equal(f"{expected.xml_name} Source Qualifier", transforms["Source Qualifier"], expected.source_qualifiers)
    assert_equal(f"{expected.xml_name} Expression", transforms["Expression"], expected.expressions)
    assert_equal(f"{expected.xml_name} Pre SQL count", len(attrs_named(root, "Pre SQL")), expected.pre_sql)
    assert_equal(
        f"{expected.xml_name} Post SQL count",
        len(attrs_named(root, "Post SQL")) - compact_post_sql_count(root),
        expected.post_sql,
    )
    assert_equal(f"{expected.xml_name} truncate_yes", sum(1 for item in write_infos if yes(item.truncate)), expected.truncate_yes)
    assert_equal(f"{expected.xml_name} update_targets", sum(1 for item in write_infos if norm(item.treat_source_rows_as) == "UPDATE"), expected.update_targets)
    assert_equal(f"{expected.xml_name} insert_targets", sum(1 for item in write_infos if norm(item.treat_source_rows_as) == "INSERT"), expected.insert_targets)
    assert_equal(f"{expected.xml_name} delete_targets", sum(1 for item in write_infos if norm(item.treat_source_rows_as) == "DELETE"), expected.delete_targets)
    print(
        f"PASS {expected.xml_name}: status={expected.status} expression_fields={len(expression_rows)} "
        f"source_qualifiers={transforms['Source Qualifier']} expressions={transforms['Expression']} "
        f"pre_sql={expected.pre_sql} post_sql={expected.post_sql} truncate_yes={expected.truncate_yes} "
        f"write_modes=insert:{expected.insert_targets},update:{expected.update_targets},delete:{expected.delete_targets}"
    )


def run_core(xml_dir: Path) -> int:
    print("infa dataflow status harness: read_only=true writes=none")
    for expected in EXPECTED:
        assert_file_status(xml_dir, expected)
    print(f"PASS summary: dataflow_statuses={len(EXPECTED)} unsupported_probe=available")
    return 0


def run_unsupported_expression_probe() -> int:
    kind, sql_expression, sql_convertible, limitation = classify("OUT_UNSUPPORTED", "REG_REPLACE(IN_COL, 'x', 'y')")
    assert_equal("unsupported expression kind", kind, "UNSUPPORTED")
    assert_equal("unsupported expression sql expression", sql_expression, "")
    assert_equal("unsupported expression sql convertible", "yes" if sql_convertible else "no", "no")
    if "unsupported Informatica function" not in limitation:
        raise HarnessError(f"unsupported expression limitation missing: {limitation}")
    print(
        "PASS unsupported-expression-probe: "
        f"status={STATUS_PENDING} executable=no pending=yes kind={kind} limitation={limitation}"
    )
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Assert core Informatica data-flow compiler status baselines.")
    parser.add_argument("--xml-dir", type=Path, default=DEFAULT_XML_DIR)
    parser.add_argument("--unsupported-expression-probe", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    try:
        if args.unsupported_expression_probe:
            return run_unsupported_expression_probe()
        return run_core(args.xml_dir)
    except HarnessError as exc:
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
