#!/usr/bin/env python3
"""Regression checks for SQL table-name schema qualification rules.

This mirrors the conservative Java rules in DataSyncParser:
- qualify only table references after FROM/JOIN;
- skip strings, comments, subqueries, functions, DUAL, and already-qualified names;
- prefer connection/default schema over XML source owner evidence.
"""

from __future__ import annotations

from pathlib import Path


XML_DIR = Path("/mnt/d/项目文档/2-项目/百年人寿")
WF_PRIP = XML_DIR / "WF_PRIP_Stage.XML"


def read_problem6_sql() -> str:
    text = WF_PRIP.read_bytes().decode("gb18030")
    marker = 'NAME ="M_PRIP_PRIP_TP_ContHesitateEndDate"'
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


def qualify_sql(sql: str, connection_schema: str, source_owners: dict[str, str]) -> str:
    result: list[str] = []
    i = 0
    depth = 0
    from_list_depth = -1
    expect_table = False
    while i < len(sql):
        c = sql[i]
        if c == "'":
            end = i + 1
            while end < len(sql):
                if sql[end] == "'":
                    end += 1
                    if end < len(sql) and sql[end] == "'":
                        end += 1
                        continue
                    break
                end += 1
            result.append(sql[i:end])
            i = end
            continue
        if sql.startswith("--", i):
            end = sql.find("\n", i)
            if end < 0:
                end = len(sql)
            else:
                end += 1
            result.append(sql[i:end])
            i = end
            continue
        if sql.startswith("/*", i):
            end = sql.find("*/", i + 2)
            end = len(sql) if end < 0 else end + 2
            result.append(sql[i:end])
            i = end
            continue
        if expect_table:
            if c.isspace():
                result.append(c)
                i += 1
                continue
            if c == "(":
                result.append(c)
                depth += 1
                expect_table = False
                i += 1
                continue
            token, end = read_identifier(sql, i)
            if token:
                result.append(qualify_token(token, sql, end, connection_schema, source_owners))
                i = end
                expect_table = False
                continue
            expect_table = False
        if c == "(":
            depth += 1
            result.append(c)
            i += 1
            continue
        if c == ")":
            depth = max(0, depth - 1)
            if from_list_depth > depth:
                from_list_depth = -1
            result.append(c)
            i += 1
            continue
        if c == "," and from_list_depth == depth:
            expect_table = True
            result.append(c)
            i += 1
            continue
        word, end = read_identifier(sql, i)
        if word:
            upper = word.upper()
            result.append(word)
            if upper in {"FROM", "JOIN"}:
                expect_table = True
                if upper == "FROM":
                    from_list_depth = depth
            elif from_list_depth == depth and upper in {"WHERE", "GROUP", "ORDER", "HAVING", "UNION"}:
                expect_table = False
                from_list_depth = -1
            i = end
            continue
        result.append(c)
        i += 1
    return "".join(result)


def read_identifier(sql: str, start: int) -> tuple[str, int]:
    if start >= len(sql) or not (sql[start].isalpha() or sql[start] in "_$"):
        return "", start
    end = start + 1
    while end < len(sql) and (sql[end].isalnum() or sql[end] in "_$#."):
        end += 1
    return sql[start:end], end


def qualify_token(token: str, sql: str, token_end: int, connection_schema: str, source_owners: dict[str, str]) -> str:
    if "." in token or "@" in token or token.startswith("$") or token.upper() == "DUAL":
        return token
    index = token_end
    while index < len(sql) and sql[index].isspace():
        index += 1
    if index < len(sql) and sql[index] == "(":
        return token
    schema = connection_schema or source_owners.get(token.upper(), "")
    return f"{schema}.{token}" if schema else token


def assert_contains(label: str, text: str, expected: str) -> None:
    if expected not in text:
        raise AssertionError(f"{label}: missing {expected!r}")


def assert_not_contains(label: str, text: str, unexpected: str) -> None:
    if unexpected in text:
        raise AssertionError(f"{label}: unexpected {unexpected!r}")


def main() -> int:
    sql = read_problem6_sql()
    assert_contains("problem6 fixture", sql.lower(), "from lcpol ff")
    qualified = qualify_sql(sql, "LISMAN", {"LCPOL": "PRIP"})
    assert_contains("connection schema qualifies main table", qualified.lower(), "from lisman.lcpol ff")
    assert_contains("connection schema qualifies nested bare table", qualified.lower(), "from lisman.lmedorwt t")
    assert_contains("existing package qualifier preserved", qualified.lower(), "lisman.pkg_bq_hesitate.gethesitateend")
    assert_contains("line comment preserved", qualified, "--SELECT COUNT")
    assert_not_contains("DUAL pseudo table not qualified", qualify_sql("select 1 from dual", "LISMAN", {}), "LISMAN.dual")

    owner_only = qualify_sql("select * from lcpol ff join already.done d on 1=1", "", {"LCPOL": "PRIP"})
    assert_contains("xml owner fallback qualifies known source", owner_only.lower(), "from prip.lcpol ff")
    assert_contains("already-qualified table remains", owner_only.lower(), "join already.done d")
    print("PASS sql schema qualification regression")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
