#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
#
# --- How to run ---
# python3 scripts/test_infa_save_route.py --xml-dir "/mnt/d/项目文档/2-项目/百年人寿" --stub-services
# python3 scripts/test_infa_save_route.py --xml-dir "/mnt/d/项目文档/2-项目/百年人寿" --stub-services --missing-connection lis_190_uat_newcrm_rw

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from collections import OrderedDict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Final

SCRIPT_DIR: Final = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from audit_infa_connections import parse_xml  # noqa: E402

SAMPLES: Final = ("cc_lis_20260521.xml", "WF_HNC.XML", "WF_PRIP_Stage.XML", "auditdb.xml")
STATUS_READY: Final = "FDL_TRANSLATION_READY"
STATUS_BLOCKED: Final = "BLOCKED"


@dataclass(frozen=True, slots=True)
class SaveRouteError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


@dataclass(frozen=True, slots=True)
class FixtureSummary:
    xml_name: str
    work_name: str
    folder: str
    workflows: int
    worklets: int
    sessions: int
    mappings: int
    connection_names: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class StubOfflineWorkDTO:
    work_id: str
    work_name: str
    folder: str
    xml_name: str
    component_count: int
    line_count: int
    connection_names: tuple[str, ...]


@dataclass(slots=True)
class StubOfflineDefinitionService:
    calls: list[StubOfflineWorkDTO] = field(default_factory=list)

    def save_work(self, payload: StubOfflineWorkDTO) -> None:
        self.calls.append(payload)


@dataclass(frozen=True, slots=True)
class PreflightResult:
    required_names: tuple[str, ...]
    available_names: tuple[str, ...]
    missing_names: tuple[str, ...]

    @property
    def status(self) -> str:
        if self.missing_names:
            return STATUS_BLOCKED
        return STATUS_READY


@dataclass(frozen=True, slots=True)
class SaveRouteResult:
    summaries: tuple[FixtureSummary, ...]
    preflight: PreflightResult
    save_calls: int


def clean(value: str | None) -> str:
    return (value or "").strip()


def xml_paths(xml_dir: Path) -> tuple[Path, ...]:
    if not xml_dir.is_dir():
        raise SaveRouteError(f"missing XML directory: {xml_dir}")
    paths = tuple(xml_dir / name for name in SAMPLES)
    missing = tuple(path for path in paths if not path.is_file())
    if missing:
        joined = ", ".join(str(path) for path in missing)
        raise SaveRouteError(f"missing required XML sample(s): {joined}")
    return paths


def direct_children(element: ET.Element, tag: str) -> list[ET.Element]:
    return [child for child in list(element) if child.tag == tag]


def first_folder(root: ET.Element, fallback: str) -> str:
    for folder in root.findall(".//FOLDER"):
        name = clean(folder.get("NAME"))
        if name:
            return name
    return fallback


def connection_names(root: ET.Element) -> tuple[str, ...]:
    names = {
        value
        for ref in root.findall(".//CONNECTIONREFERENCE")
        if (value := clean(ref.get("CONNECTIONNAME")))
    }
    return tuple(sorted(names))


def summarize_fixture(path: Path) -> FixtureSummary:
    root = parse_xml(path)
    workflows = root.findall(".//WORKFLOW")
    worklets = root.findall(".//WORKLET")
    folder = first_folder(root, path.stem)
    first_workflow = clean(workflows[0].get("NAME")) if workflows else path.stem
    return FixtureSummary(
        xml_name=path.name,
        work_name=first_workflow,
        folder=folder,
        workflows=len(workflows),
        worklets=len(worklets),
        sessions=len(root.findall(".//SESSION")),
        mappings=len(root.findall(".//MAPPING")),
        connection_names=connection_names(root),
    )


def preflight(summaries: tuple[FixtureSummary, ...], missing_connection: str) -> PreflightResult:
    required = tuple(sorted({name for summary in summaries for name in summary.connection_names}))
    available = tuple(name for name in required if name != missing_connection)
    missing = tuple(name for name in required if name not in set(available))
    return PreflightResult(required, available, missing)


def compile_payload(summary: FixtureSummary, index: int) -> StubOfflineWorkDTO:
    component_count = summary.sessions + summary.worklets + summary.workflows
    line_count = max(summary.sessions + summary.worklets - 1, 0)
    return StubOfflineWorkDTO(
        work_id=f"stub-{index + 1}-{summary.xml_name}",
        work_name=summary.work_name,
        folder=summary.folder,
        xml_name=summary.xml_name,
        component_count=component_count,
        line_count=line_count,
        connection_names=summary.connection_names,
    )


def run_stub_route(xml_dir: Path, missing_connection: str) -> SaveRouteResult:
    summaries = tuple(summarize_fixture(path) for path in xml_paths(xml_dir))
    result = preflight(summaries, missing_connection)
    service = StubOfflineDefinitionService()
    if result.missing_names:
        return SaveRouteResult(summaries, result, len(service.calls))
    for index, summary in enumerate(summaries):
        service.save_work(compile_payload(summary, index))
    return SaveRouteResult(summaries, result, len(service.calls))


def print_report(result: SaveRouteResult) -> None:
    print("infa save-route harness: read_only=true stub_services=true real_fdl_sdk=false real_saveWork=false")
    print("route=PluginController.dealWork->PluginService.saveWork->dealWork->updateFDLWorkDTO->OfflineDefinitionService.saveWork")
    print("boundary=CONNECTIONNAME preflight only datasourceType_inference=false credential_parsing=false")
    print(
        "preflight: required_connections={required} available_connections={available} missing_connections={missing} status={status}".format(
            required=len(result.preflight.required_names),
            available=len(result.preflight.available_names),
            missing=len(result.preflight.missing_names),
            status=result.preflight.status,
        )
    )
    if result.preflight.missing_names:
        print("blocking_report: missing same-name FDL connection(s) before save route")
        print("missing: " + ", ".join(result.preflight.missing_names))
        print(f"saveWork calls={result.save_calls}")
        return
    for summary in result.summaries:
        counts: OrderedDict[str, int] = OrderedDict(
            [
                ("workflows", summary.workflows),
                ("worklets", summary.worklets),
                ("sessions", summary.sessions),
                ("mappings", summary.mappings),
                ("connections", len(summary.connection_names)),
            ]
        )
        visible = ", ".join(f"{key}={value}" for key, value in counts.items())
        print(f"PASS {summary.xml_name}: compiled_payload=accepted folder={summary.folder} work={summary.work_name} {visible}")
    print(f"saveWork accepted={result.save_calls}")


def assert_outcome(result: SaveRouteResult) -> None:
    if result.preflight.missing_names:
        if result.save_calls != 0:
            raise SaveRouteError(f"missing connection preflight reached saveWork: calls={result.save_calls}")
        return
    if result.save_calls != len(SAMPLES):
        raise SaveRouteError(f"saveWork accepted mismatch: expected={len(SAMPLES)} actual={result.save_calls}")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate the Informatica XML to FDL save route with read-only stub services.")
    parser.add_argument("--xml-dir", type=Path, required=True)
    parser.add_argument("--stub-services", action="store_true", help="Required: use read-only in-process service stubs instead of FDL SDK services.")
    parser.add_argument("--missing-connection", default="", help="Remove this same-name FDL connection from the stub preflight set.")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if not args.stub_services:
        print("refusing to run without --stub-services; real FDL SDK save route is out of scope", file=sys.stderr)
        return 2
    try:
        result = run_stub_route(args.xml_dir, args.missing_connection)
        assert_outcome(result)
    except SaveRouteError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    except ET.ParseError as exc:
        print(f"invalid XML: {exc}", file=sys.stderr)
        return 1
    print_report(result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
