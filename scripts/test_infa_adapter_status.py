#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
#
# --- How to run ---
# python3 scripts/test_infa_adapter_status.py --runtime-validation-missing "/mnt/d/项目文档/2-项目/百年人寿/WF_PRIP_Stage.XML"

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Final

SCRIPT_DIR: Final = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from audit_infa_api_calls import (  # noqa: E402
    ROW_API_ADAPTER_NODE,
    ROW_API_UDF_FUNCTION,
    ROW_API_UDF_PROVIDER_FUNCTION,
    adapter_contract_lines,
    adapter_row_binding_line,
    audit_file as audit_api_file,
    row_api_udf_adapter_supported,
)
from audit_infa_workflow_conditions import (  # noqa: E402
    CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING,
    CLASS_EXECUTABLE_TASK_STATUS_CONDITION,
    audit_file as audit_workflow_file,
)

WF_PRIP_NAME: Final = "WF_PRIP_Stage.XML"
STATUS_ADAPTER_REQUIRED: Final = "FDL_ADAPTER_REQUIRED"
STATUS_TRANSLATION_READY: Final = "FDL_TRANSLATION_READY"
RUNTIME_EQUIVALENCE_MARKERS: Final = (
    "runtimeValidationRequired=no",
    "nativeNode=ApiReadNode",
    "adapterRequired=no",
)
DEFAULT_ADAPTER_FALLBACK_MARKERS: Final = (
    "FDL_API_NATIVE_CONTRACT",
    "nativeNode=not_generated",
    "adapterNode=SparkSqlNode+SparkUDF",
    ROW_API_ADAPTER_NODE,
)
FORBIDDEN_LOOP_NATIVE_MARKERS: Final = (
    "FOREACH_PARALLEL",
)


@dataclass(frozen=True, slots=True)
class AdapterStatusError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


def require_xml(path: Path) -> None:
    if not path.is_file():
        raise AdapterStatusError(f"missing XML file: {path}")
    if path.name != WF_PRIP_NAME:
        raise AdapterStatusError(f"runtime-validation-missing probe expects {WF_PRIP_NAME}; got {path.name}")


def assert_decision_subset(path: Path) -> None:
    decisions, conditional_links = audit_workflow_file(path)
    classes = Counter(item.analysis.classification for item in [*decisions, *conditional_links])
    task_count = classes[CLASS_EXECUTABLE_TASK_STATUS_CONDITION]
    decision_count = classes[CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING]
    if task_count < 1:
        raise AdapterStatusError(f"missing {CLASS_EXECUTABLE_TASK_STATUS_CONDITION}: {dict(classes)}")
    if decision_count < 1:
        raise AdapterStatusError(f"missing {CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING}: {dict(classes)}")
    print(
        "PASS WF_PRIP Decision/status subset: "
        f"{CLASS_EXECUTABLE_TASK_STATUS_CONDITION}={task_count} "
        f"{CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING}={decision_count}"
    )


def assert_adapter_contract(path: Path) -> None:
    calls = audit_api_file(path)
    if len(calls) != 1:
        raise AdapterStatusError(f"expected one Java/SOAP/API call; actual={len(calls)}")
    call = calls[0]
    if call.execution_status != STATUS_ADAPTER_REQUIRED:
        raise AdapterStatusError(f"expected {STATUS_ADAPTER_REQUIRED}; actual={call.execution_status}")
    if row_api_udf_adapter_supported(call) is not True:
        raise AdapterStatusError("row API UDF adapter support contract was not proven by static audit")
    row_binding = adapter_row_binding_line(call)
    if "required=yes" not in row_binding or "supported=yes" not in row_binding:
        raise AdapterStatusError(f"row adapter binding is incomplete: {row_binding}")
    contract_lines = adapter_contract_lines(call)
    contract_text = "\n".join(contract_lines)
    required_tokens = (
        "FDL_ROW_API_UDF",
        "FDL_API_NATIVE_CONTRACT",
        "runtimeValidationRequired=yes",
        "adapterRequired=yes",
        "noOnOneEqualsOne=yes",
        ROW_API_ADAPTER_NODE,
        ROW_API_UDF_FUNCTION,
        ROW_API_UDF_PROVIDER_FUNCTION,
        "nativeNode=not_generated",
        "adapterNode=SparkSqlNode+SparkUDF",
    )
    missing = [token for token in required_tokens if token not in contract_text]
    if missing:
        raise AdapterStatusError(f"adapter contract missing token(s): {', '.join(missing)}")
    misleading = [token for token in RUNTIME_EQUIVALENCE_MARKERS if token in contract_text]
    if misleading:
        raise AdapterStatusError(f"contract claims runtime equivalence before validation: {', '.join(misleading)}")
    fallback = [token for token in DEFAULT_ADAPTER_FALLBACK_MARKERS if token not in contract_text]
    if fallback:
        raise AdapterStatusError(f"adapter fallback evidence missing token(s): {', '.join(fallback)}")
    loop_native_markers = [token for token in FORBIDDEN_LOOP_NATIVE_MARKERS if token in contract_text]
    if loop_native_markers:
        raise AdapterStatusError(f"default adapter probe unexpectedly found forbidden loop marker(s): {', '.join(loop_native_markers)}")
    print(f"PASS Java/SOAP status: {call.execution_status}; native_runtime_equivalent=no")
    print(f"PASS row adapter binding: FDL_ADAPTER_ROW_BINDING {row_binding}")
    print("PASS default fallback: row_api_adapter remains the active row-level adapter evidence")
    print(
        "PASS runtime validation gate: Java/SOAP remains adapter-required before runtime validation; "
        f"adapter={ROW_API_ADAPTER_NODE}; function={ROW_API_UDF_FUNCTION}; "
        f"providerFunction={ROW_API_UDF_PROVIDER_FUNCTION}; runtimeValidationRequired=yes"
    )
    for line in contract_lines:
        print(f"PROOF {line}")


def run_runtime_validation_missing(path: Path) -> int:
    require_xml(path)
    print("infa adapter status harness: read_only=true writes=none network_calls=none")
    assert_decision_subset(path)
    assert_adapter_contract(path)
    print(f"PASS guarded claim: {STATUS_TRANSLATION_READY} is not claimed for Java/SOAP runtime equivalence")
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate WF_PRIP Decision and Java/SOAP adapter runtime status.")
    parser.add_argument(
        "--runtime-validation-missing",
        type=Path,
        help="WF_PRIP XML file; proves Java/SOAP remains adapter-required until runtime validation exists.",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if args.runtime_validation_missing is None:
        print(
            "usage: python3 scripts/test_infa_adapter_status.py --runtime-validation-missing <WF_PRIP_Stage.XML>",
            file=sys.stderr,
        )
        return 2
    try:
        return run_runtime_validation_missing(args.runtime_validation_missing)
    except AdapterStatusError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    except ET.ParseError as exc:
        print(f"invalid XML: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
