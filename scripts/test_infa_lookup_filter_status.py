#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
#
# --- How to run ---
# python3 scripts/test_infa_lookup_filter_status.py --non-equality-not-executable "/mnt/d/项目文档/2-项目/百年人寿/auditdb.xml"

from __future__ import annotations

import argparse
import io
import sys
from contextlib import redirect_stdout
from dataclasses import dataclass
from pathlib import Path
from typing import Final, Literal

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from audit_infa_filters_lookups import audit  # noqa: E402

EXECUTABLE: Final = "FDL_DATAFLOW_EXECUTABLE"
PLAN_READY: Final = "FDL_DATAFLOW_SINGLE_ROW_PLAN_READY"
PENDING: Final = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
REQUIRED_EQUALITY_MAPPINGS: Final = frozenset(
    {
        "MP_STAGE_AML_FAILUREEND_TRANSACTION",
        "MP_STAGE_AML_RISKLEVEL_TIMELIMIT",
        "MP_STAGE_AML_CUSTOMER_LEVELOVERDUE",
    }
)
REQUIRED_NON_EQUALITY_MAPPINGS: Final = frozenset(
    {
        "MP_STAGE_LIS_AML_SAMEAPP_QY",
        "MP_STAGE_LIS_AML_SAMEAPP_BQ",
    }
)
UNRESOLVED_MAPPING: Final = "MP_STAGE_QD_LCCONT_SELF_NOTPAID"


EntryKind = Literal["FILTER", "LOOKUP"]


@dataclass(frozen=True, slots=True)
class HarnessError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


@dataclass(frozen=True, slots=True)
class AuditEntry:
    mapping: str
    kind: EntryKind
    name: str
    condition: str
    status: str
    single_row_executable_false: bool
    unresolved_inputs_visible: bool


@dataclass(frozen=True, slots=True)
class MappingStatus:
    mapping: str
    filter_status: str
    lookup_status: str
    lookup_condition: str
    lookup_plan_not_executable: bool
    unresolved_lookup_inputs_visible: bool


def run_audit(xml_path: Path) -> str:
    if not xml_path.is_file():
        raise HarnessError(f"missing XML file: {xml_path}")
    output = io.StringIO()
    with redirect_stdout(output):
        exit_code = audit(xml_path)
    if exit_code != 0:
        raise HarnessError(f"audit failed with exit code {exit_code}")
    return output.getvalue()


def parse_entries(output: str) -> list[AuditEntry]:
    entries: list[AuditEntry] = []
    mapping = ""
    current: AuditEntry | None = None
    for raw_line in output.splitlines():
        line = raw_line.strip()
        if line.startswith("## MAPPING "):
            mapping = line.removeprefix("## MAPPING ")
            continue
        if line.startswith("- FILTER ") or line.startswith("- LOOKUP "):
            if current is not None:
                entries.append(current)
            kind: EntryKind = "FILTER" if line.startswith("- FILTER ") else "LOOKUP"
            prefix = f"- {kind} "
            current = AuditEntry(mapping, kind, line.removeprefix(prefix), "", "", False, False)
            continue
        if current is None:
            continue
        if line.startswith("condition: "):
            current = AuditEntry(
                current.mapping,
                current.kind,
                current.name,
                line.removeprefix("condition: "),
                current.status,
                current.single_row_executable_false,
                current.unresolved_inputs_visible,
            )
        elif line.startswith("conversion_status: "):
            current = AuditEntry(
                current.mapping,
                current.kind,
                current.name,
                current.condition,
                line.removeprefix("conversion_status: "),
                current.single_row_executable_false,
                current.unresolved_inputs_visible,
            )
        elif line == "executable=false":
            current = AuditEntry(
                current.mapping,
                current.kind,
                current.name,
                current.condition,
                current.status,
                True,
                current.unresolved_inputs_visible,
            )
        elif line.startswith("unresolved_input_port_semantics: ") and not line.endswith(": -"):
            current = AuditEntry(
                current.mapping,
                current.kind,
                current.name,
                current.condition,
                current.status,
                current.single_row_executable_false,
                True,
            )
    if current is not None:
        entries.append(current)
    return entries


def statuses_by_mapping(entries: list[AuditEntry]) -> dict[str, MappingStatus]:
    result: dict[str, MappingStatus] = {}
    for mapping in {entry.mapping for entry in entries}:
        filters = [entry for entry in entries if entry.mapping == mapping and entry.kind == "FILTER"]
        lookups = [entry for entry in entries if entry.mapping == mapping and entry.kind == "LOOKUP"]
        if not lookups:
            continue
        result[mapping] = MappingStatus(
            mapping=mapping,
            filter_status=filters[0].status if filters else "",
            lookup_status=lookups[0].status,
            lookup_condition=lookups[0].condition,
            lookup_plan_not_executable=lookups[0].single_row_executable_false,
            unresolved_lookup_inputs_visible=lookups[0].unresolved_inputs_visible,
        )
    return result


def require(condition: bool, message: str) -> None:
    if not condition:
        raise HarnessError(message)


def validate_non_equality_not_executable(output: str) -> str:
    entries = parse_entries(output)
    statuses = statuses_by_mapping(entries)
    require(len([entry for entry in entries if entry.kind == "FILTER"]) == 5, "expected 5 Filter audit records")
    require(len([entry for entry in entries if entry.kind == "LOOKUP"]) == 6, "expected 6 Lookup audit records")
    equality_ready = [
        mapping
        for mapping in REQUIRED_EQUALITY_MAPPINGS
        if statuses[mapping].filter_status == EXECUTABLE and statuses[mapping].lookup_status == EXECUTABLE
    ]
    require(len(equality_ready) == 3, f"expected 3 executable equality lookup/filter cases, got {len(equality_ready)}")
    non_equality_ready = [
        mapping
        for mapping in REQUIRED_NON_EQUALITY_MAPPINGS
        if statuses[mapping].filter_status == PLAN_READY
        and statuses[mapping].lookup_status == PLAN_READY
        and "!=" in statuses[mapping].lookup_condition
        and statuses[mapping].lookup_plan_not_executable
    ]
    require(len(non_equality_ready) == 2, f"expected 2 non-equality plan-ready non-executable cases, got {len(non_equality_ready)}")
    require(
        all(statuses[mapping].lookup_status != EXECUTABLE for mapping in REQUIRED_NON_EQUALITY_MAPPINGS),
        "non-equality lookup case was marked executable",
    )
    unresolved = statuses[UNRESOLVED_MAPPING]
    require(unresolved.lookup_status == PENDING, f"unresolved lookup input should remain pending, got {unresolved.lookup_status}")
    require(unresolved.unresolved_lookup_inputs_visible, "unresolved lookup input diagnostics were not visible")
    return (
        "PASS non-equality lookup cases are not executable: "
        f"equality_executable_cases=3 non_equality_plan_ready_cases=2 "
        f"unresolved_lookup_status={unresolved.lookup_status}"
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate auditdb Lookup/Filter status semantics.")
    parser.add_argument("--non-equality-not-executable", action="store_true")
    parser.add_argument("xml", type=Path)
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if not args.non_equality_not_executable:
        print("choose --non-equality-not-executable", file=sys.stderr)
        return 2
    try:
        print(validate_non_equality_not_executable(run_audit(args.xml)))
    except HarnessError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
