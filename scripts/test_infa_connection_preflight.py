#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
#
# --- How to run ---
# python3 scripts/test_infa_connection_preflight.py <infa-xml> --available <fdl-connection-name> [<fdl-connection-name> ...]

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

from audit_infa_connections import parse_xml

STATUS_READY = "FDL_TRANSLATION_READY"
STATUS_BLOCKED = "BLOCKED"


@dataclass(frozen=True, slots=True)
class PreflightError(Exception):
    message: str

    def __str__(self) -> str:
        return self.message


@dataclass(frozen=True, slots=True)
class ConnectionPreflight:
    xml_file: str
    required_names: tuple[str, ...]
    available_names: tuple[str, ...]
    missing_names: tuple[str, ...]

    @property
    def status(self) -> str:
        if self.missing_names:
            return STATUS_BLOCKED
        return STATUS_READY


def connection_names(path: Path) -> tuple[str, ...]:
    root = parse_xml(path)
    names = {
        value.strip()
        for ref in root.findall(".//CONNECTIONREFERENCE")
        if (value := ref.get("CONNECTIONNAME", "").strip())
    }
    return tuple(sorted(names))


def normalize_available(raw_names: list[str]) -> tuple[str, ...]:
    names = {name.strip() for name in raw_names if name.strip()}
    if not names:
        raise PreflightError("missing available FDL connection names: --available requires at least one non-empty name")
    return tuple(sorted(names))


def preflight(xml_path: Path, raw_available: list[str]) -> ConnectionPreflight:
    if not xml_path.is_file():
        raise PreflightError(f"missing XML: {xml_path}")
    required = connection_names(xml_path)
    available = normalize_available(raw_available)
    missing = tuple(name for name in required if name not in set(available))
    return ConnectionPreflight(
        xml_file=xml_path.name,
        required_names=required,
        available_names=available,
        missing_names=missing,
    )


def print_report(result: ConnectionPreflight) -> None:
    print(f"# Informatica FDL connection preflight: {result.xml_file}")
    print("read_only=true save_route=not_called")
    print("boundary=name_match_only datasourceType_inference=false credential_parsing=false")
    print(
        "summary: required_connections={required} available_connections={available} missing_connections={missing} status={status}".format(
            required=len(result.required_names),
            available=len(result.available_names),
            missing=len(result.missing_names),
            status=result.status,
        )
    )
    print("required: " + ", ".join(result.required_names))
    print("available: " + ", ".join(result.available_names))
    if result.missing_names:
        print("blocking_report: missing same-name FDL connection(s) before save route")
        for name in result.missing_names:
            print(f"missing: {name}")
        return
    print("PASS: all XML CONNECTIONNAME values have same-name FDL connection prerequisites")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate same-name FDL connection prerequisites from Informatica XML CONNECTIONNAME values.")
    parser.add_argument("xml", type=Path, help="Informatica PowerCenter XML file to preflight.")
    parser.add_argument("--available", nargs="+", required=True, help="Available FDL data connection names supplied by the caller.")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    try:
        result = preflight(args.xml, args.available)
    except PreflightError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    except ET.ParseError as exc:
        print(f"invalid XML: {args.xml}: {exc}", file=sys.stderr)
        return 2
    print_report(result)
    if result.missing_names:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
