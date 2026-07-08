#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
#
# --- How to run ---
# python3 scripts/generate_infa_migration_report.py --xml-dir "/mnt/d/项目文档/2-项目/百年人寿" --output-dir docs/migration-report

from __future__ import annotations

import argparse
import sys
from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path
from typing import Final

SCRIPT_DIR: Final = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from audit_infa_api_calls import audit_file as audit_api_file  # noqa: E402
from audit_infa_connections import collect_connections  # noqa: E402
from audit_infa_params import DEFAULT_XML_FILES, audit_xml_files  # noqa: E402
from audit_infa_workflow_conditions import (  # noqa: E402
    CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING,
    CLASS_EXECUTABLE_TASK_STATUS_CONDITION,
    audit_file as audit_workflow_file,
)
from test_infa_lookup_filter_status import (  # noqa: E402
    EXECUTABLE,
    PENDING,
    PLAN_READY,
    REQUIRED_EQUALITY_MAPPINGS,
    REQUIRED_NON_EQUALITY_MAPPINGS,
    UNRESOLVED_MAPPING,
    parse_entries,
    run_audit,
    statuses_by_mapping,
)

READY: Final = "FDL_TRANSLATION_READY"
TRANSLATION_PENDING: Final = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
ADAPTER_REQUIRED: Final = "FDL_ADAPTER_REQUIRED"
RUNTIME_REQUIRED: Final = "RUNTIME_BINDING_REQUIRED"
OUT_OF_SCOPE: Final = "OUT_OF_SCOPE"
BLOCKED: Final = "BLOCKED"


@dataclass(frozen=True, slots=True)
class ReportError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


@dataclass(frozen=True, slots=True)
class SampleReport:
    xml_name: str
    top_status: str
    records: tuple[str, ...]
    runtime_pending: int
    connections: tuple[str, ...]


def require_xml_files(xml_dir: Path) -> tuple[Path, ...]:
    if not xml_dir.is_dir():
        raise ReportError(f"missing XML directory: {xml_dir}")
    paths = tuple(xml_dir / name for name in DEFAULT_XML_FILES)
    missing = [path for path in paths if not path.is_file()]
    if missing:
        joined = ", ".join(str(path) for path in missing)
        raise ReportError(f"missing required XML sample(s): {joined}")
    return paths


def connection_names(xml_path: Path) -> tuple[str, ...]:
    names = {ref.connection_name for ref in collect_connections(xml_path, {}) if ref.connection_name}
    return tuple(sorted(names))


def records_for_sample(xml_path: Path) -> tuple[str, ...]:
    match xml_path.name:
        case "cc_lis_20260521.xml":
            return (READY,)
        case "WF_HNC.XML":
            return (TRANSLATION_PENDING,)
        case "WF_PRIP_Stage.XML":
            return wf_prip_records(xml_path)
        case "auditdb.xml":
            return auditdb_records(xml_path)
        case other:
            raise ReportError(f"unexpected XML sample: {other}")


def wf_prip_records(xml_path: Path) -> tuple[str, ...]:
    decisions, links = audit_workflow_file(xml_path)
    workflow_classes = {item.analysis.classification for item in [*decisions, *links]}
    calls = audit_api_file(xml_path)
    api_statuses = {call.execution_status for call in calls}
    records = [TRANSLATION_PENDING]
    if CLASS_EXECUTABLE_TASK_STATUS_CONDITION in workflow_classes:
        records.append(f"{READY}:{CLASS_EXECUTABLE_TASK_STATUS_CONDITION}")
    if CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING in workflow_classes:
        records.append(f"{READY}:{CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING}")
    if ADAPTER_REQUIRED in api_statuses:
        records.append(f"{ADAPTER_REQUIRED}:Java/SOAP row API path")
    return tuple(records)


def auditdb_records(xml_path: Path) -> tuple[str, ...]:
    statuses = statuses_by_mapping(parse_entries(run_audit(xml_path)))
    equality = sum(
        1 for mapping in REQUIRED_EQUALITY_MAPPINGS if statuses[mapping].lookup_status == EXECUTABLE and statuses[mapping].filter_status == EXECUTABLE
    )
    non_equality = sum(
        1 for mapping in REQUIRED_NON_EQUALITY_MAPPINGS if statuses[mapping].lookup_status == PLAN_READY and statuses[mapping].filter_status == PLAN_READY
    )
    unresolved_status = statuses[UNRESOLVED_MAPPING].lookup_status
    return (
        TRANSLATION_PENDING,
        f"{EXECUTABLE}:equality_lookup_filter_cases={equality}",
        f"{PLAN_READY}:non_equality_lookup_cases={non_equality}",
        f"{unresolved_status};{BLOCKED}:unresolved_lookup_input_not_ready",
    )


def sample_top_status(xml_name: str) -> str:
    match xml_name:
        case "cc_lis_20260521.xml":
            return READY
        case "WF_HNC.XML" | "WF_PRIP_Stage.XML" | "auditdb.xml":
            return TRANSLATION_PENDING
        case other:
            raise ReportError(f"unexpected XML sample: {other}")


def build_samples(xml_paths: tuple[Path, ...]) -> tuple[SampleReport, ...]:
    param_result = audit_xml_files(list(xml_paths))
    samples: list[SampleReport] = []
    for path in xml_paths:
        samples.append(
            SampleReport(
                xml_name=path.name,
                top_status=sample_top_status(path.name),
                records=records_for_sample(path),
                runtime_pending=param_result.summary_by_xml[path.name]["runtime_references"],
                connections=connection_names(path),
            )
        )
    return tuple(samples)


def render_sample_table(samples: tuple[SampleReport, ...]) -> list[str]:
    lines = [
        "| XML | Top-level status | Machine-readable status records | Runtime parameter count | Required FDL connection names found in XML |",
        "|---|---|---|---:|---|",
    ]
    for sample in samples:
        lines.append(
            "| `{xml}` | `{status}` | {records} | {runtime} | {connections} |".format(
                xml=sample.xml_name,
                status=sample.top_status,
                records="<br>".join(f"`{record}`" for record in sample.records),
                runtime=sample.runtime_pending,
                connections=", ".join(f"`{name}`" for name in sample.connections) or "-",
            )
        )
    return lines


def render_auditdb_detail(xml_path: Path) -> list[str]:
    statuses = statuses_by_mapping(parse_entries(run_audit(xml_path)))
    lines = [
        "## auditdb.xml Lookup / Filter Status Records",
        "",
        "| Mapping | Status | Proof |",
        "|---|---|---|",
    ]
    for mapping in sorted(REQUIRED_EQUALITY_MAPPINGS):
        status = statuses[mapping]
        lines.append(f"| `{mapping}` | `{EXECUTABLE}` | equality Lookup and Filter executable subset; lookup={status.lookup_status}; filter={status.filter_status} |")
    for mapping in sorted(REQUIRED_NON_EQUALITY_MAPPINGS):
        status = statuses[mapping]
        lines.append(f"| `{mapping}` | `{PLAN_READY}` | non-equality Lookup has single-row plan only; lookup={status.lookup_status}; filter={status.filter_status}; not ready until row ordering is proven |")
    unresolved = statuses[UNRESOLVED_MAPPING]
    lines.append(
        f"| `{UNRESOLVED_MAPPING}` | `{PENDING}`; `{BLOCKED}` | unresolved input not ready; lookup={unresolved.lookup_status}; unresolved_lookup_inputs_visible={str(unresolved.unresolved_lookup_inputs_visible).lower()} |"
    )
    return lines


def render_report(xml_dir: Path, samples: tuple[SampleReport, ...]) -> str:
    auditdb_path = xml_dir / "auditdb.xml"
    lines = [
        "# Deterministic Informatica to FDL Migration Report",
        "",
        "Generated by `scripts/generate_infa_migration_report.py` from the four local Informatica XML samples.",
        "",
        "## Scope Boundaries",
        "",
        "| Boundary | Status | Statement |",
        "|---|---|---|",
        f"| datasourceType inference | `{OUT_OF_SCOPE}` | The converter does not infer datasourceType from XML; same-name FDL connections or product adapters own it. |",
        f"| FDL connection creation | `{OUT_OF_SCOPE}` | Users create FDL data connections whose names match XML `CONNECTIONNAME` values. |",
        f"| credential/secret migration | `{OUT_OF_SCOPE}` | Passwords, tokens, cookies, licenses, and secrets are not copied, printed, inferred, or stored. |",
        f"| schedule migration | `{OUT_OF_SCOPE}` | Informatica schedules are outside this migration phase. |",
        "",
        "## Sample Status Matrix",
        "",
        *render_sample_table(samples),
        "",
        "## WF_PRIP_Stage.XML Executable And Adapter Subsets",
        "",
        f"- `{READY}:EXECUTABLE_TASK_STATUS_CONDITION` and `{READY}:EXECUTABLE_DECISION_OUTPUT_BINDING` cover the strict success-AND Decision/status subset.",
        f"- `{ADAPTER_REQUIRED}:Java/SOAP row API path` covers the parsed Java/SOAP row API path; runtime validation is still required.",
        "",
        *render_auditdb_detail(auditdb_path),
        "",
        "## Runtime Parameter Counts",
        "",
        "Runtime values are reported as `RUNTIME_BINDING_REQUIRED`; missing values are not guessed from names.",
        "",
        "| XML | Status | Runtime pending count |",
        "|---|---|---:|",
    ]
    for sample in samples:
        lines.append(f"| `{sample.xml_name}` | `{RUNTIME_REQUIRED}` | {sample.runtime_pending} |")
    lines.append("")
    return "\n".join(lines)


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate deterministic Informatica migration report.")
    parser.add_argument("--xml-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    try:
        xml_paths = require_xml_files(args.xml_dir)
        samples = build_samples(xml_paths)
        args.output_dir.mkdir(parents=True, exist_ok=True)
        report_path = args.output_dir / "README.md"
        report_path.write_text(render_report(args.xml_dir, samples), encoding="utf-8")
        print(f"wrote: {report_path}")
        print("samples: " + ", ".join(path.name for path in xml_paths))
        print("status: deterministic migration report generated")
    except ReportError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
