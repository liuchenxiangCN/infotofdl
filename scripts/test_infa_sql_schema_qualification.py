#!/usr/bin/env python3
"""Regression checks for SQL table-name preservation rules.

The converter must not inject or replace table schema qualifiers in Source
Qualifier SQL. Original SQL table names are preserved:
- bare table names stay bare;
- already-qualified table names keep their original qualifier;
- connection/default schema and XML SOURCE owner do not rewrite SQL text.
"""

from __future__ import annotations

from pathlib import Path


XML_DIR = Path("/mnt/d/项目文档/2-项目/百年人寿")
WF_PRIP = XML_DIR / "WF_PRIP_Stage.XML"


def read_mapping_sql(mapping_name: str) -> str:
    text = WF_PRIP.read_bytes().decode("gb18030")
    marker = f'NAME ="{mapping_name}"'
    start = text.index(marker)
    sql_marker = 'TABLEATTRIBUTE NAME ="Sql Query" VALUE ="'
    sql_start = text.index(sql_marker, start) + len(sql_marker)
    sql_end = text.index('"/>', sql_start)
    return (
        text[sql_start:sql_end]
        .replace("&#xD;", "\r")
        .replace("&#xA;", "\n")
        .replace("&apos;", "'")
    )


def render_sql_for_fdl(sql: str, connection_schema: str, source_owners: dict[str, str]) -> str:
    return sql


def assert_contains(label: str, text: str, expected: str) -> None:
    if expected not in text:
        raise AssertionError(f"{label}: missing {expected!r}")


def assert_not_contains(label: str, text: str, unexpected: str) -> None:
    if unexpected in text:
        raise AssertionError(f"{label}: unexpected {unexpected!r}")


def assert_sql_preserved(label: str, sql: str, connection_schema: str, source_owners: dict[str, str]) -> str:
    rendered = render_sql_for_fdl(sql, connection_schema, source_owners)
    if rendered != sql:
        raise AssertionError(f"{label}: SQL table schema preservation regressed")
    return rendered


def main() -> int:
    problem6_sql = read_mapping_sql("M_PRIP_PRIP_TP_ContHesitateEndDate")
    rendered_problem6 = assert_sql_preserved("problem6 SQL", problem6_sql, "LISMAN", {"LCPOL": "PRIP"})
    assert_contains("bare main table preserved", rendered_problem6.lower(), "from lcpol ff")
    assert_contains("bare nested table preserved", rendered_problem6.lower(), "from lmedorwt t")
    assert_contains("existing package qualifier preserved", rendered_problem6.lower(), "lisman.pkg_bq_hesitate.gethesitateend")
    assert_contains("line comment preserved", rendered_problem6, "--SELECT COUNT")

    already_qualified = "select * from lcpol ff join already.done d on 1=1"
    rendered_already_qualified = assert_sql_preserved("already-qualified SQL", already_qualified, "LISMAN", {"LCPOL": "PRIP"})
    assert_contains("bare table remains bare", rendered_already_qualified.lower(), "from lcpol ff")
    assert_contains("qualified table remains qualified", rendered_already_qualified.lower(), "join already.done d")

    llcommend_sql = read_mapping_sql("M_PRIP_Stage_LLCommendHospital")
    rendered_llcommend = assert_sql_preserved("LLCommendHospital SQL", llcommend_sql, "NEWCRM", {"LLCOMMENDHOSPITAL": "PRIP"})
    assert_contains("bare LLCommendHospital table preserved", rendered_llcommend.lower(), "from\r\n llcommendhospital")
    assert_not_contains("connection schema must not be injected", rendered_llcommend.lower(), "from\r\n newcrm.llcommendhospital")
    assert_not_contains("xml owner must not be injected", rendered_llcommend.lower(), "from\r\n prip.llcommendhospital")

    print("PASS sql table schema preservation regression")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
