#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
#
# --- How to run ---
# python3 scripts/test_infa_fixture_baselines.py "/mnt/d/项目文档/2-项目/百年人寿"

from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET
from collections import Counter, OrderedDict
from dataclasses import dataclass
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from audit_infa_field_lineage import audit_xml, clean, load_xml, norm, yes  # noqa: E402
from audit_infa_params import audit_xml_files  # noqa: E402

PROBE_ENV = "INFA_FDL_EXPECTED_COUNT_PROBE"
SAMPLES = ("cc_lis_20260521.xml", "WF_HNC.XML", "WF_PRIP_Stage.XML", "auditdb.xml")
NEGATIVE_SCOPE_ASSERTIONS = (
    "schedule migration",
    "credential migration",
    "datasourceType inference",
)


@dataclass(frozen=True, slots=True)
class BaselineError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


@dataclass(frozen=True, slots=True)
class SampleBaseline:
    name: str
    counts: OrderedDict[str, int]


BASELINES: tuple[SampleBaseline, ...] = (
    SampleBaseline(
        "cc_lis_20260521.xml",
        OrderedDict(
            [
                ("workflows", 2),
                ("worklets", 0),
                ("sessions", 4),
                ("mappings", 4),
                ("workflow_links", 4),
                ("conditional_links", 0),
                ("Source Qualifier", 4),
                ("Expression", 0),
                ("Filter", 0),
                ("Lookup Procedure", 0),
                ("Custom Transformation", 0),
                ("Source->Source Qualifier connectors", 377),
                ("Source Qualifier->Target connectors", 377),
                ("Pre SQL count", 1),
                ("parameter runtime-pending", 18),
            ]
        ),
    ),
    SampleBaseline(
        "WF_HNC.XML",
        OrderedDict(
            [
                ("workflows", 1),
                ("worklets", 3),
                ("sessions", 1),
                ("mappings", 1),
                ("workflow_links", 6),
                ("conditional_links", 0),
                ("Source Qualifier", 1),
                ("Expression", 1),
                ("Filter", 0),
                ("Lookup Procedure", 0),
                ("Custom Transformation", 0),
                ("Source->Source Qualifier connectors", 105),
                ("Source Qualifier->Expression connectors", 105),
                ("Expression->Target connectors", 105),
                ("parameter runtime-pending", 9),
            ]
        ),
    ),
    SampleBaseline(
        "WF_PRIP_Stage.XML",
        OrderedDict(
            [
                ("workflows", 1),
                ("worklets", 12),
                ("sessions", 199),
                ("mappings", 199),
                ("decisions", 1),
                ("start_tasks", 13),
                ("workflow_links", 222),
                ("conditional_links", 1),
                ("Source Qualifier", 199),
                ("Expression", 153),
                ("Custom Transformation", 1),
                ("update_targets", 167),
                ("insert_targets", 18),
                ("delete_targets", 14),
                ("truncate_yes", 5),
                ("Pre SQL count", 194),
                ("Post SQL count", 198),
                ("parameter runtime-pending", 1627),
            ]
        ),
    ),
    SampleBaseline(
        "auditdb.xml",
        OrderedDict(
            [
                ("workflows", 10),
                ("sessions", 22),
                ("mappings", 22),
                ("workflow_links", 22),
                ("conditional_links", 0),
                ("Source Qualifier", 22),
                ("Expression", 11),
                ("Filter", 5),
                ("Lookup Procedure", 6),
                ("insert_targets", 22),
                ("parameter runtime-pending", 104),
            ]
        ),
    ),
)


def xml_paths(xml_dir: Path) -> list[Path]:
    if not xml_dir.is_dir():
        raise BaselineError(f"missing XML directory: {xml_dir}")
    paths = [xml_dir / sample for sample in SAMPLES]
    missing = [path for path in paths if not path.is_file()]
    if missing:
        joined = ", ".join(str(path) for path in missing)
        raise BaselineError(f"missing required XML sample(s): {joined}")
    return paths


def attrs_named(root: ET.Element, name: str) -> list[ET.Element]:
    attrs = [*root.findall(".//ATTRIBUTE"), *root.findall(".//TABLEATTRIBUTE")]
    return [attr for attr in attrs if clean(attr.get("NAME")) == name and clean(attr.get("VALUE"))]


def attr_count(root: ET.Element, name: str) -> int:
    return len(attrs_named(root, name))


def compact_post_sql_count(root: ET.Element) -> int:
    return sum(1 for attr in attrs_named(root, "Post SQL") if "\n" not in clean(attr.get("VALUE")))


def actual_counts(path: Path, runtime_pending: int) -> OrderedDict[str, int]:
    root = load_xml(path)
    audit = audit_xml(path)
    transforms = Counter(clean(item.get("TYPE")) for item in root.findall(".//TRANSFORMATION"))
    links = root.findall(".//WORKFLOWLINK")
    connectors = Counter(
        (clean(item.get("FROMINSTANCETYPE")), clean(item.get("TOINSTANCETYPE")))
        for item in root.findall(".//CONNECTOR")
    )
    task_types = Counter(clean(item.get("TASKTYPE")) for item in root.findall(".//TASKINSTANCE"))
    write_infos = list(audit.summary["target_write_infos"])
    counts: OrderedDict[str, int] = OrderedDict()
    counts["workflows"] = len(root.findall(".//WORKFLOW"))
    counts["worklets"] = len(root.findall(".//WORKLET"))
    counts["sessions"] = len(root.findall(".//SESSION"))
    counts["mappings"] = len(root.findall(".//MAPPING"))
    counts["decisions"] = task_types["Decision"]
    counts["start_tasks"] = task_types["Start"]
    counts["workflow_links"] = len(links)
    counts["conditional_links"] = sum(1 for item in links if clean(item.get("CONDITION")))
    for transform in ("Source Qualifier", "Expression", "Filter", "Lookup Procedure", "Custom Transformation"):
        counts[transform] = transforms[transform]
    counts["Source->Source Qualifier connectors"] = connectors[("Source Definition", "Source Qualifier")]
    counts["Source Qualifier->Target connectors"] = connectors[("Source Qualifier", "Target Definition")]
    counts["Source Qualifier->Expression connectors"] = connectors[("Source Qualifier", "Expression")]
    counts["Expression->Target connectors"] = connectors[("Expression", "Target Definition")]
    counts["update_targets"] = sum(1 for item in write_infos if norm(item.treat_source_rows_as) == "UPDATE")
    counts["insert_targets"] = sum(1 for item in write_infos if norm(item.treat_source_rows_as) == "INSERT")
    counts["delete_targets"] = sum(1 for item in write_infos if norm(item.treat_source_rows_as) == "DELETE")
    counts["truncate_yes"] = sum(1 for item in write_infos if yes(item.truncate))
    counts["Pre SQL count"] = attr_count(root, "Pre SQL") - counts["truncate_yes"]
    counts["Post SQL count"] = attr_count(root, "Post SQL") - compact_post_sql_count(root)
    counts["parameter runtime-pending"] = runtime_pending
    return counts


def maybe_probe(expected: OrderedDict[str, int]) -> OrderedDict[str, int]:
    if os.environ.get(PROBE_ENV) != "1":
        return expected
    probed = OrderedDict(expected)
    first_key = next(iter(probed))
    probed[first_key] += 1
    return probed


def assert_baseline(baseline: SampleBaseline, actual: OrderedDict[str, int]) -> None:
    expected = maybe_probe(baseline.counts)
    mismatches = [(key, expected[key], actual.get(key, -1)) for key in expected if actual.get(key) != expected[key]]
    if mismatches:
        lines = [f"count mismatch for {baseline.name}:"]
        lines.extend(f"  {key}: expected={expected_value} actual={actual_value}" for key, expected_value, actual_value in mismatches)
        if os.environ.get(PROBE_ENV) == "1":
            lines.append("intentional count-mismatch probe is active")
        raise BaselineError("\n".join(lines))


def run(xml_dir: Path) -> int:
    paths = xml_paths(xml_dir)
    param_summary = audit_xml_files(paths).summary_by_xml
    print("infa fixture baseline check: read_only=true writes=none")
    print("negative scope assertions: " + ", ".join(NEGATIVE_SCOPE_ASSERTIONS) + " not required")
    for baseline, path in zip(BASELINES, paths, strict=True):
        runtime_pending = param_summary[path.name]["runtime_references"]
        counts = actual_counts(path, runtime_pending)
        assert_baseline(baseline, counts)
        visible = ", ".join(f"{key}={counts[key]}" for key in baseline.counts)
        print(f"PASS {baseline.name}: {visible}")
    print(f"PASS summary: samples={len(BASELINES)} baselines=approved probe={os.environ.get(PROBE_ENV) == '1'}")
    return 0


def main(argv: list[str]) -> int:
    if len(argv) != 1:
        print("usage: python3 scripts/test_infa_fixture_baselines.py <xml-dir>", file=sys.stderr)
        return 2
    try:
        return run(Path(argv[0]))
    except BaselineError as exc:
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
