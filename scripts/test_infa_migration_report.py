#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
#
# --- How to run ---
# python3 scripts/test_infa_migration_report.py --unresolved-lookup-probe docs/migration-report/README.md

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Final

UNRESOLVED_LOOKUP: Final = "MP_STAGE_QD_LCCONT_SELF_NOTPAID"
READY: Final = "FDL_TRANSLATION_READY"
PENDING: Final = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
BLOCKED: Final = "BLOCKED"
SINGLE_ROW_PLAN: Final = "FDL_DATAFLOW_SINGLE_ROW_PLAN_READY"
EXECUTABLE: Final = "FDL_DATAFLOW_EXECUTABLE"


@dataclass(frozen=True, slots=True)
class ReportProbeError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


def require_report(path: Path) -> str:
    if not path.is_file():
        raise ReportProbeError(f"missing report path: {path}")
    return path.read_text(encoding="utf-8")


def row_for_marker(report: str, marker: str) -> str:
    for line in report.splitlines():
        if marker in line:
            return line.strip()
    raise ReportProbeError(f"missing report marker: {marker}")


def assert_unresolved_lookup_blocked(report: str) -> str:
    row = row_for_marker(report, UNRESOLVED_LOOKUP)
    if READY in row:
        raise ReportProbeError(f"unresolved lookup was marked ready: {row}")
    if PENDING not in row and BLOCKED not in row:
        raise ReportProbeError(f"unresolved lookup row lacks pending/blocked status: {row}")
    required_tokens = ("unresolved", "input", "not ready")
    lower_row = row.lower()
    missing = [token for token in required_tokens if token not in lower_row]
    if missing:
        raise ReportProbeError(f"unresolved lookup row lacks proof token(s) {', '.join(missing)}: {row}")
    for token in (EXECUTABLE, SINGLE_ROW_PLAN):
        if token in row:
            raise ReportProbeError(f"unresolved lookup row used executable/plan-ready status {token}: {row}")
    return row


def run_unresolved_lookup_probe(path: Path) -> int:
    report = require_report(path)
    row = assert_unresolved_lookup_blocked(report)
    print("infa migration report probe: read_only=true writes=none")
    print(
        "PASS unresolved lookup is blocked/pending, not ready: "
        f"sample=auditdb.xml mapping={UNRESOLVED_LOOKUP} status_proof={row}"
    )
    print(f"PASS guarded claim: unresolved lookup row does not contain {READY}")
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate deterministic Informatica migration report guardrails.")
    parser.add_argument("--unresolved-lookup-probe", type=Path, help="Generated migration report Markdown path.")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if args.unresolved_lookup_probe is None:
        print("usage: python3 scripts/test_infa_migration_report.py --unresolved-lookup-probe <report.md>", file=sys.stderr)
        return 2
    try:
        return run_unresolved_lookup_probe(args.unresolved_lookup_probe)
    except ReportProbeError as exc:
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
