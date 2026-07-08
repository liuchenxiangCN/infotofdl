#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
#
# --- How to run ---
# python3 scripts/test_infa_params_status.py --unresolved-runtime-probe "/mnt/d/项目文档/2-项目/百年人寿"

from __future__ import annotations

import argparse
import sys
from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path
from typing import Final

SCRIPT_DIR: Final = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from audit_infa_params import (  # noqa: E402
    DEFAULT_XML_FILES,
    STATUS_RUNTIME_PLACEHOLDER,
    Reference,
    VariableDecl,
    audit_xml_files,
    clean,
    runtime_placeholder,
)

EXPECTED_RUNTIME_PENDING: Final[OrderedDict[str, int]] = OrderedDict(
    [
        ("cc_lis_20260521.xml", 18),
        ("WF_HNC.XML", 9),
        ("WF_PRIP_Stage.XML", 1627),
        ("auditdb.xml", 104),
    ]
)
FORBIDDEN_SECRET_WORDS: Final = ("password", "passwd", "pwd", "token", "cookie", "license", "secret")


@dataclass(frozen=True, slots=True)
class ParamStatusError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


def xml_paths(xml_dir: Path) -> list[Path]:
    if not xml_dir.is_dir():
        raise ParamStatusError(f"missing XML directory: {xml_dir}")
    paths = [xml_dir / name for name in DEFAULT_XML_FILES]
    missing = [path for path in paths if not path.is_file()]
    if missing:
        joined = ", ".join(str(path) for path in missing)
        raise ParamStatusError(f"missing required XML sample(s): {joined}")
    return paths


def assert_runtime_counts(summary_by_xml: OrderedDict[str, dict[str, int]]) -> None:
    mismatches = [
        (xml_file, expected, summary_by_xml[xml_file]["runtime_references"])
        for xml_file, expected in EXPECTED_RUNTIME_PENDING.items()
        if summary_by_xml[xml_file]["runtime_references"] != expected
    ]
    if mismatches:
        lines = ["runtime-pending count mismatch:"]
        lines.extend(f"  {xml_file}: expected={expected} actual={actual}" for xml_file, expected, actual in mismatches)
        raise ParamStatusError("\n".join(lines))


def contains_secret_word(*values: str) -> bool:
    text = " ".join(values).lower()
    return any(word in text for word in FORBIDDEN_SECRET_WORDS)


def assert_no_credential_parameters(result_variables: list[VariableDecl]) -> None:
    offenders = [
        variable
        for variable in result_variables
        if contains_secret_word(variable.name, variable.description, variable.default_value)
    ]
    if offenders:
        shown = ", ".join(f"{item.xml_file}:{item.name}" for item in offenders[:10])
        raise ParamStatusError(f"credential-like value classified as parameter: {shown}")


def assert_runtime_required_report(result_references: list[Reference]) -> tuple[int, str]:
    runtime_refs = [ref for ref in result_references if ref.resolution_status == STATUS_RUNTIME_PLACEHOLDER]
    if not runtime_refs:
        raise ParamStatusError("no runtime-pending references found")
    bad_contracts = [ref for ref in runtime_refs if ref.replacement_contract() != runtime_placeholder(ref.reference_kind, ref.reference)]
    if bad_contracts:
        shown = ", ".join(f"{ref.xml_file}:{ref.reference}" for ref in bad_contracts[:10])
        raise ParamStatusError(f"runtime placeholder contract mismatch: {shown}")
    missing_bindings = [ref for ref in runtime_refs if not clean(ref.required_binding())]
    if missing_bindings:
        shown = ", ".join(f"{ref.xml_file}:{ref.reference}" for ref in missing_bindings[:10])
        raise ParamStatusError(f"runtime reference lacks required binding: {shown}")
    guessed = [ref for ref in runtime_refs if ref.replacement_contract() == ref.reference]
    if guessed:
        shown = ", ".join(f"{ref.xml_file}:{ref.reference}" for ref in guessed[:10])
        raise ParamStatusError(f"runtime value was guessed instead of reported: {shown}")
    first = runtime_refs[0]
    detail = (
        f"sample={first.xml_file} reference={first.reference} "
        f"status=RUNTIME_BINDING_REQUIRED source_status={first.resolution_status} "
        f"contract={first.replacement_contract()} required_binding={first.required_binding()}"
    )
    return len(runtime_refs), detail


def run_unresolved_runtime_probe(xml_dir: Path) -> int:
    result = audit_xml_files(xml_paths(xml_dir))
    assert_runtime_counts(result.summary_by_xml)
    assert_no_credential_parameters(result.variables)
    runtime_count, detail = assert_runtime_required_report(result.references)
    print("infa parameter status probe: read_only=true writes=none")
    for xml_file, expected in EXPECTED_RUNTIME_PENDING.items():
        actual = result.summary_by_xml[xml_file]["runtime_references"]
        print(f"PASS {xml_file}: runtime_pending={actual} expected={expected}")
    print(f"PASS unresolved runtime values are reported as required, not guessed: runtime_refs={runtime_count}")
    print(f"PASS runtime binding detail: {detail}")
    print("PASS credentials/tokens/passwords/cookies/licenses/secrets are not classified as migratable parameters")
    print("PASS missing runtime values stay RUNTIME_BINDING_REQUIRED; no name-based inference")
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Informatica runtime parameter binding status reports.")
    parser.add_argument("--unresolved-runtime-probe", type=Path, help="XML fixture directory for the unresolved runtime probe.")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if args.unresolved_runtime_probe is None:
        print("usage: python3 scripts/test_infa_params_status.py --unresolved-runtime-probe <xml-dir>", file=sys.stderr)
        return 2
    try:
        return run_unresolved_runtime_probe(args.unresolved_runtime_probe)
    except ParamStatusError as exc:
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
