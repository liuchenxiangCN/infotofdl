#!/usr/bin/env python3
"""Fail if known sample identifiers leak into production plugin code."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


DEFAULT_ROOTS = (Path("plugin-src/src"),)

SAMPLE_IDENTIFIERS = (
    "cc_lis_20260521",
    "WF_HNC",
    "WF_PRIP_Stage",
    "auditdb",
    "MP_STAGE_QD_LCCONT_SELF_NOTPAID",
    "MP_STAGE_AML_FAILUREEND_TRANSACTION",
    "MP_STAGE_AML_RISKLEVEL_TIMELIMIT",
    "MP_STAGE_AML_CUSTOMER_LEVELOVERDUE",
    "MP_STAGE_LIS_AML_SAMEAPP_QY",
    "MP_STAGE_LIS_AML_SAMEAPP_BQ",
    "s_MP_STAGE_QD_LCCONT_SELF_NOTPAID",
    "s_MP_STAGE_AML_FAILUREEND_TRANSACTION",
    "s_MP_STAGE_AML_RISKLEVEL_TIMELIMIT",
    "s_MP_STAGE_AML_CUSTOMER_LEVELOVERDUE",
    "s_MP_STAGE_LIS_AML_SAMEAPP_QY",
    "s_MP_STAGE_LIS_AML_SAMEAPP_BQ",
    "SALARY_OTHER",
    "SALARY_IN",
    "APPNTNO_OTHER",
    "APPNTNO_IN",
    "OCCUPATIONCODE_OTHER",
    "OCCUPATIONCODE_IN",
    "PLAN3_IN",
    "RERATINGDATE",
    "FIANLRATINGDATE",
    "CONTNO_OTHER",
    "判定",
    "WL_PRIP_CRM_Customer",
    "WL_PRIP_Stage_Agent",
    "WL_PRIP_Stage_Claim",
    "WL_PRIP_Stage_Cont",
    "WL_PRIP_Stage_Finance",
    "WL_PRIP_Stage_Pos",
    "WL_PRIP_Stage_Product",
    "WL_PRIP_Stage_Public",
    "WL_PRIP_Stage_RN",
    "WL_PRIP_XQGL_Mission",
    "WL_PRIP_EAST",
    "M_PRIP_ExtractData",
    "s_M_PRIP_ExtractData",
    "doExtractDate",
    "ExtractionPripDataService",
    "EXTRACTDATALOG",
    "OUT_STATE",
    "ERRORINFO",
    "informatic通知失败",
    "$$StartDate",
    "$$EndDate",
    "$$StartDate1",
    "$$MySqlStartDate",
    "$$MySqlEndDate",
    "$$NEWVARIABLE3",
)


def iter_files(root: Path):
    if root.is_file():
        yield root
        return
    if not root.exists():
        return
    for path in root.rglob("*"):
        if path.is_file() and path.suffix in {".java", ".xml", ".properties"}:
            yield path


def scan(root: Path) -> list[tuple[Path, int, str]]:
    findings: list[tuple[Path, int, str]] = []
    for path in iter_files(root):
        try:
            lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
        except OSError:
            continue
        for line_no, line in enumerate(lines, 1):
            lower = line.lower()
            for identifier in SAMPLE_IDENTIFIERS:
                if identifier.lower() in lower:
                    findings.append((path, line_no, identifier))
    return findings


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("roots", nargs="*", type=Path, default=list(DEFAULT_ROOTS))
    args = parser.parse_args(argv)

    findings: list[tuple[Path, int, str]] = []
    for root in args.roots:
        findings.extend(scan(root))

    if findings:
        print("sample identifier hardcoding found in production code:")
        for path, line_no, identifier in findings:
            print(f"{path}:{line_no}: {identifier}")
        return 1
    print("sample identifier hardcoding check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
