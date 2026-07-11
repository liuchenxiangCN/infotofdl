#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
#
# --- How to run ---
# python3 scripts/test_infa_migration_e2e.py --xml-dir "/mnt/d/项目文档/2-项目/百年人寿"
# python3 scripts/test_infa_migration_e2e.py --xml-dir "/mnt/d/项目文档/2-项目/百年人寿" --negative-probes

from __future__ import annotations

import argparse
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Final

SCRIPT_DIR: Final = Path(__file__).resolve().parent
REPO_ROOT: Final = SCRIPT_DIR.parent
REPORT_PATH: Final = REPO_ROOT / "docs/migration-report/README.md"
CONTRACT_PATH: Final = REPO_ROOT / "docs/informatica-fdl-migration-contract.md"
SAMPLES: Final = ("cc_lis_20260521.xml", "WF_HNC.XML", "WF_PRIP_Stage.XML", "auditdb.xml")
ALL_CONNECTIONS: Final = (
    "agentchannel",
    "aml_171_uat_r",
    "auditdb_99_uat_rw_sjods_user",
    "cc_133_uat_rw",
    "cem_214_uat_r",
    "crm133",
    "east",
    "lis_190_uat_newcrm_rw",
    "lis_uat152",
    "prip_155",
    "qd_214_uat_r",
    "training_uat",
    "xqgl119",
)


@dataclass(frozen=True, slots=True)
class E2EError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


@dataclass(frozen=True, slots=True)
class Gate:
    name: str
    command: tuple[str, ...]
    required_tokens: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class GateResult:
    name: str
    stdout: str


def require_xml_dir(xml_dir: Path) -> None:
    if not xml_dir.is_dir():
        raise E2EError(f"missing XML directory: {xml_dir}")
    missing = [xml_dir / name for name in SAMPLES if not (xml_dir / name).is_file()]
    if missing:
        joined = ", ".join(str(path) for path in missing)
        raise E2EError(f"missing required XML sample(s): {joined}")


def run_gate(gate: Gate) -> GateResult:
    completed = subprocess.run(
        gate.command,
        cwd=REPO_ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    combined = completed.stdout + completed.stderr
    if completed.returncode != 0:
        raise E2EError(f"{gate.name} failed exit={completed.returncode}\n{combined}")
    missing = [token for token in gate.required_tokens if token not in combined]
    if missing:
        raise E2EError(f"{gate.name} missing token(s): {', '.join(missing)}\n{combined}")
    return GateResult(gate.name, completed.stdout.strip())


def print_pass(result: GateResult, label: str) -> None:
    first_line = result.stdout.splitlines()[0] if result.stdout else "no-output"
    print(f"PASS {label}: gate={result.name}; observed={first_line}")


def manual_qa_summary_text(xml_dir: Path, results: tuple[GateResult, ...]) -> str:
    lines = [
        "manual QA artifact summary",
        "read_only=true real_fdl_sdk=false network_calls=none",
        f"xml_dir={xml_dir}",
        f"samples={', '.join(SAMPLES)}",
        f"positive_gates={', '.join(result.name for result in results)}",
        "observed_first_line_per_gate:",
    ]
    for result in results:
        first_line = result.stdout.splitlines()[0] if result.stdout else "no-output"
        lines.append(f"- {result.name}: {first_line}")
    lines.append(
        "PASS E2E positive gates: fixtures, preflight, workflow, native-condition, dataflow, lookup/filter, adapter, params, report, save-route"
    )
    return "\n".join(lines) + "\n"


def manual_qa_gate_text(results: tuple[GateResult, ...]) -> str:
    lines = ["manual QA gate results"]
    for result in results:
        first_line = result.stdout.splitlines()[0] if result.stdout else "no-output"
        lines.append(f"{result.name}: {first_line}")
    return "\n".join(lines) + "\n"


def write_manual_qa_artifacts(artifact_dir: Path, xml_dir: Path, results: tuple[GateResult, ...]) -> None:
    artifact_dir.mkdir(parents=True, exist_ok=True)
    (artifact_dir / "manual-qa-summary.md").write_text(manual_qa_summary_text(xml_dir, results), encoding="utf-8")
    (artifact_dir / "manual-qa-gates.txt").write_text(manual_qa_gate_text(results), encoding="utf-8")


def positive_gates(xml_dir: Path) -> tuple[Gate, ...]:
    cc_lis = xml_dir / "cc_lis_20260521.xml"
    wf_prip = xml_dir / "WF_PRIP_Stage.XML"
    auditdb = xml_dir / "auditdb.xml"
    return (
        Gate("fixture baselines", ("python3", "scripts/test_infa_fixture_baselines.py", str(xml_dir)), SAMPLES),
        Gate(
            "connection preflight happy path",
            ("python3", "scripts/test_infa_connection_preflight.py", str(cc_lis), "--available", *ALL_CONNECTIONS),
            ("missing_connections=0", "PASS: all XML CONNECTIONNAME values"),
        ),
        Gate("workflow graph", ("python3", "scripts/test_infa_workflow_graph.py", str(wf_prip)), ("EXECUTABLE_TASK_STATUS_CONDITION", "EXECUTABLE_DECISION_OUTPUT_BINDING")),
        Gate("FDL native condition execution", ("bash", "scripts/test_fdl_native_conditions.sh"), ("PASS FDL native condition probe",)),
        Gate("target schema resolution", ("python3", "scripts/test_infa_target_schema_resolution.py", str(wf_prip)), ("SOURCE PRIP cannot override", "TARGET owner > Oracle writer user")),
        Gate("field mapping types", ("python3", "scripts/test_infa_field_mapping_types.py"), ("generated field mapping type regression passed",)),
        Gate("dataflow status", ("python3", "scripts/test_infa_dataflow_status.py", "--xml-dir", str(xml_dir)), ("FDL_TRANSLATION_READY", "XML_SEMANTICS_PARSED_TRANSLATION_PENDING")),
        Gate("lookup/filter status", ("python3", "scripts/test_infa_lookup_filter_status.py", "--non-equality-not-executable", str(auditdb)), ("equality_executable_cases=3", "non_equality_plan_ready_cases=2")),
        Gate("adapter status", ("python3", "scripts/test_infa_adapter_status.py", "--runtime-validation-missing", str(wf_prip)), ("FDL_ADAPTER_REQUIRED", "FDL_ADAPTER_ROW_BINDING")),
        Gate("params status", ("python3", "scripts/test_infa_params_status.py", "--unresolved-runtime-probe", str(xml_dir)), ("runtime_pending=1627", "RUNTIME_BINDING_REQUIRED")),
        Gate("report generation", ("python3", "scripts/generate_infa_migration_report.py", "--xml-dir", str(xml_dir), "--output-dir", "docs/migration-report"), ("status: deterministic migration report generated",)),
        Gate("report probe", ("python3", "scripts/test_infa_migration_report.py", "--unresolved-lookup-probe", str(REPORT_PATH)), ("unresolved lookup is blocked/pending",)),
        Gate("save route happy path", ("python3", "scripts/test_infa_save_route.py", "--xml-dir", str(xml_dir), "--stub-services"), ("saveWork accepted=4", "real_fdl_sdk=false")),
    )


def negative_gates(xml_dir: Path) -> tuple[Gate, ...]:
    wf_prip = xml_dir / "WF_PRIP_Stage.XML"
    auditdb = xml_dir / "auditdb.xml"
    return (
        Gate("missing connection", ("python3", "scripts/test_infa_save_route.py", "--xml-dir", str(xml_dir), "--stub-services", "--missing-connection", "lis_190_uat_newcrm_rw"), ("missing: lis_190_uat_newcrm_rw", "saveWork calls=0")),
        Gate("unresolved parameter", ("python3", "scripts/test_infa_params_status.py", "--unresolved-runtime-probe", str(xml_dir)), ("RUNTIME_BINDING_REQUIRED", "not guessed")),
        Gate("unresolved lookup", ("python3", "scripts/test_infa_migration_report.py", "--unresolved-lookup-probe", str(REPORT_PATH)), ("MP_STAGE_QD_LCCONT_SELF_NOTPAID", "blocked/pending")),
        Gate("adapter-required Java", ("python3", "scripts/test_infa_adapter_status.py", "--runtime-validation-missing", str(wf_prip)), ("FDL_ADAPTER_REQUIRED", "native_runtime_equivalent=no")),
        Gate("lookup non-executable guard", ("python3", "scripts/test_infa_lookup_filter_status.py", "--non-equality-not-executable", str(auditdb)), ("non-equality lookup cases are not executable",)),
    )


def assert_scope_exclusions() -> None:
    report = REPORT_PATH.read_text(encoding="utf-8")
    contract = CONTRACT_PATH.read_text(encoding="utf-8")
    text = report + "\n" + contract
    tokens = ("datasourceType inference", "credential", "schedule", "OUT_OF_SCOPE", "FDL connection creation")
    missing = [token for token in tokens if token not in text]
    if missing:
        raise E2EError(f"scope exclusion token(s) missing: {', '.join(missing)}")
    print("PASS scope exclusions: datasourceType inference, credentials, schedules, and FDL connection creation remain OUT_OF_SCOPE")


def run_positive(xml_dir: Path, manual_qa_artifacts: Path | None = None) -> int:
    print("infa migration E2E: read_only=true real_fdl_sdk=false network_calls=none")
    results = []
    for gate in positive_gates(xml_dir):
        result = run_gate(gate)
        results.append(result)
        print_pass(result, "positive")
    if manual_qa_artifacts is not None:
        write_manual_qa_artifacts(manual_qa_artifacts, xml_dir, tuple(results))
    print("PASS E2E positive gates: fixtures, preflight, workflow, native-condition, dataflow, lookup/filter, adapter, params, report, save-route")
    return 0


def run_negative(xml_dir: Path) -> int:
    print("infa migration E2E negative probes: read_only=true real_fdl_sdk=false network_calls=none")
    for gate in negative_gates(xml_dir):
        print_pass(run_gate(gate), "negative")
    assert_scope_exclusions()
    print("PASS E2E negative probes: missing connection, unresolved parameter, unresolved lookup, adapter-required Java, scope exclusions")
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run local end-to-end Informatica migration validation gates.")
    parser.add_argument("--xml-dir", type=Path, required=True)
    parser.add_argument("--negative-probes", action="store_true")
    parser.add_argument("--manual-qa-artifacts", type=Path)
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    try:
        require_xml_dir(args.xml_dir)
        if args.negative_probes:
            return run_negative(args.xml_dir)
        return run_positive(args.xml_dir, args.manual_qa_artifacts)
    except E2EError as exc:
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
