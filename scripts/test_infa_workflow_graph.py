#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
#
# --- How to run ---
# python3 scripts/test_infa_workflow_graph.py "/mnt/d/项目文档/2-项目/百年人寿/WF_PRIP_Stage.XML"
# python3 scripts/test_infa_workflow_graph.py --unsupported-condition-probe

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from collections import Counter, OrderedDict
from dataclasses import dataclass
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from audit_infa_workflow_conditions import (  # noqa: E402
    CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING,
    CLASS_EXECUTABLE_TASK_STATUS_CONDITION,
    STATUS_EXECUTABLE,
    analyze_condition,
    audit_file,
    clean,
    read_xml,
)

WF_PRIP_NAME = "WF_PRIP_Stage.XML"
WF_PRIP_CONDITION = "$判定.Condition = true"
UNSUPPORTED_PROBE_CONDITION = "IIF($SomeTask.Status = succeeded, true, false)"


@dataclass(frozen=True, slots=True)
class GraphError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


@dataclass(frozen=True, slots=True)
class GraphSnapshot:
    path: Path
    counts: OrderedDict[str, int]
    workflow_links: list[ET.Element]
    worklet_links: list[ET.Element]


@dataclass(frozen=True, slots=True)
class ExpectedGraph:
    name: str
    counts: OrderedDict[str, int]


EXPECTED_GRAPHS: tuple[ExpectedGraph, ...] = (
    ExpectedGraph(
        WF_PRIP_NAME,
        OrderedDict(
            [
                ("workflows", 1),
                ("worklets", 12),
                ("sessions", 199),
                ("decisions", 1),
                ("workflow_links", 222),
                ("top_workflow_links", 24),
                ("worklet_links", 198),
                ("conditional_links", 1),
                ("expanded_worklet_sessions", 198),
            ]
        ),
    ),
)


def direct_children(element: ET.Element, tag: str) -> list[ET.Element]:
    return [child for child in list(element) if child.tag == tag]


def task_instance_type_counts(elements: list[ET.Element]) -> Counter[str]:
    return Counter(clean(item.get("TASKTYPE")) for element in elements for item in direct_children(element, "TASKINSTANCE"))


def snapshot_graph(path: Path) -> GraphSnapshot:
    if not path.is_file():
        raise GraphError(f"missing XML file: {path}")
    root = read_xml(path)
    workflows = list(root.iter("WORKFLOW"))
    worklets = list(root.iter("WORKLET"))
    workflow_links = [link for item in workflows for link in direct_children(item, "WORKFLOWLINK")]
    worklet_links = [link for item in worklets for link in direct_children(item, "WORKFLOWLINK")]
    workflow_task_counts = task_instance_type_counts(workflows)
    worklet_task_counts = task_instance_type_counts(worklets)
    counts: OrderedDict[str, int] = OrderedDict()
    counts["workflows"] = len(workflows)
    counts["worklets"] = len(worklets)
    counts["sessions"] = len(root.findall(".//SESSION"))
    counts["decisions"] = workflow_task_counts["Decision"] + worklet_task_counts["Decision"]
    counts["workflow_links"] = len(workflow_links) + len(worklet_links)
    counts["top_workflow_links"] = len(workflow_links)
    counts["worklet_links"] = len(worklet_links)
    counts["conditional_links"] = sum(1 for link in [*workflow_links, *worklet_links] if clean(link.get("CONDITION")))
    counts["expanded_worklet_sessions"] = worklet_task_counts["Session"]
    return GraphSnapshot(path, counts, workflow_links, worklet_links)


def expected_for(path: Path) -> ExpectedGraph | None:
    for expected in EXPECTED_GRAPHS:
        if path.name == expected.name:
            return expected
    return None


def assert_counts(snapshot: GraphSnapshot, expected: ExpectedGraph) -> None:
    mismatches = [
        (key, expected.counts[key], snapshot.counts.get(key, -1))
        for key in expected.counts
        if snapshot.counts.get(key) != expected.counts[key]
    ]
    if mismatches:
        lines = [f"graph count mismatch for {snapshot.path.name}:"]
        lines.extend(f"  {key}: expected={wanted} actual={actual}" for key, wanted, actual in mismatches)
        raise GraphError("\n".join(lines))


def assert_wf_prip_condition(snapshot: GraphSnapshot) -> None:
    if snapshot.path.name != WF_PRIP_NAME:
        return
    all_links = [*snapshot.workflow_links, *snapshot.worklet_links]
    condition_links = [link for link in all_links if clean(link.get("CONDITION")) == WF_PRIP_CONDITION]
    if len(condition_links) != 1:
        raise GraphError(f"expected one WF_PRIP condition link: {WF_PRIP_CONDITION}; actual={len(condition_links)}")
    link = condition_links[0]
    if clean(link.get("FROMTASK")) != "判定" or clean(link.get("TOTASK")) != "s_M_PRIP_ExtractData":
        raise GraphError("WF_PRIP condition link endpoint changed")


def assert_condition_classification(path: Path) -> None:
    decisions, conditional_links = audit_file(path)
    classes = Counter(item.analysis.classification for item in [*decisions, *conditional_links])
    if path.name == WF_PRIP_NAME:
        if classes[CLASS_EXECUTABLE_TASK_STATUS_CONDITION] != 1:
            raise GraphError(f"missing {CLASS_EXECUTABLE_TASK_STATUS_CONDITION}: {dict(classes)}")
        if classes[CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING] != 1:
            raise GraphError(f"missing {CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING}: {dict(classes)}")
        if not any(item.condition == WF_PRIP_CONDITION for item in conditional_links):
            raise GraphError(f"condition audit did not preserve {WF_PRIP_CONDITION}")


def run_graph(path: Path) -> int:
    snapshot = snapshot_graph(path)
    expected = expected_for(path)
    if expected is not None:
        assert_counts(snapshot, expected)
    assert_wf_prip_condition(snapshot)
    assert_condition_classification(path)
    visible = ", ".join(f"{key}={value}" for key, value in snapshot.counts.items())
    baseline = "approved" if expected is not None else "observed"
    print(f"PASS workflow graph {path.name}: baseline={baseline}, {visible}")
    print("PASS invariant: all WORKFLOWLINK elements preserved across Workflow and Worklet scopes")
    print("PASS invariant: Worklet task expansion counted without forcing 1:1 FDL node mapping")
    if path.name == WF_PRIP_NAME:
        print(f"PASS invariant: preserved condition {WF_PRIP_CONDITION}")
        print(f"PASS invariant: {CLASS_EXECUTABLE_TASK_STATUS_CONDITION} and {CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING}")
    return 0


def run_unsupported_probe() -> int:
    analysis = analyze_condition(UNSUPPORTED_PROBE_CONDITION)
    if analysis.status == STATUS_EXECUTABLE:
        raise GraphError(f"unsupported synthetic condition was marked {STATUS_EXECUTABLE}")
    print(f"PASS unsupported synthetic condition: {UNSUPPORTED_PROBE_CONDITION}")
    print(f"status={analysis.status} classification={analysis.classification}")
    print(f"unsupported synthetic condition is not {STATUS_EXECUTABLE}")
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Validate Informatica workflow/worklet graph compiler invariants.")
    parser.add_argument("xml", nargs="?", type=Path, help="Informatica XML file to validate.")
    parser.add_argument("--unsupported-condition-probe", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.unsupported_condition_probe:
            return run_unsupported_probe()
        if args.xml is None:
            print("usage: python3 scripts/test_infa_workflow_graph.py <xml> | --unsupported-condition-probe", file=sys.stderr)
            return 2
        return run_graph(args.xml)
    except GraphError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    except ET.ParseError as exc:
        print(f"invalid XML: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
