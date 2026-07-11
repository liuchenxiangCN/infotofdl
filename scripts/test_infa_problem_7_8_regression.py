#!/usr/bin/env python3
"""Regression checks for the 2026-07-09 customer issues 7 and 8.

The production Java logic must stay generic. This script uses WF_PRIP_Stage.XML
only as a regression fixture for two generic contracts:
- unresolved/empty Informatica SQL parameters remain FDL runtime placeholders;
- converted tasks keep FDL native name-based global parameter lookup available;
- executable Decision output links get an executable FDL display condition.
"""

from __future__ import annotations

import argparse
import html
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
sys.path.insert(0, str(SCRIPT_DIR))

from audit_infa_workflow_conditions import (  # noqa: E402
    CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING,
    STATUS_EXECUTABLE,
    audit_file,
    clean,
)

DEFAULT_XML = (
    Path("/mnt/d")
    / "\u9879\u76ee\u6587\u6863"
    / "2-\u9879\u76ee"
    / "\u767e\u5e74\u4eba\u5bff"
    / "WF_PRIP_Stage.XML"
)
DATA_SYNC_PARSER = REPO_ROOT / "plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/util/xml/DataSyncParser.java"
CONDITION_NODE_BEAN = REPO_ROOT / "plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/ConditionNodeBean.java"
LPDUTY_MAPPING_MARKER = 'NAME ="M_PRIP_Stage_LPDuty"'
SQL_MARKER = 'TABLEATTRIBUTE NAME ="Sql Query" VALUE ="'
WF_PRIP_DECISION_CONDITION = "$\u5224\u5b9a.Condition = true"
PARAM_REF_RE = re.compile(r"\$\$[A-Za-z_][A-Za-z0-9_]*|\$\{[^}\s\"'<>]+\}")
WORKFLOW_PARAM_REF_RE = re.compile(r"\$[A-Za-z_][A-Za-z0-9_]*")


class RegressionError(Exception):
    pass


def read_xml_text(path: Path) -> str:
    raw = path.read_bytes()
    return raw.decode("gb18030" if b"encoding =\"GB2312\"" in raw[:256] else "utf-8", errors="replace")


def extract_sql_after_marker(text: str, marker: str) -> str:
    marker_index = text.find(marker)
    if marker_index < 0:
        raise RegressionError(f"missing marker: {marker}")
    sql_start = text.find(SQL_MARKER, marker_index)
    if sql_start < 0:
        raise RegressionError(f"missing SQL after marker: {marker}")
    sql_start += len(SQL_MARKER)
    sql_end = text.find('"/>', sql_start)
    if sql_end < 0:
        raise RegressionError(f"unterminated SQL after marker: {marker}")
    return html.unescape(text[sql_start:sql_end])


def safe_placeholder_name(name: str) -> bool:
    if not name or not (name[0].isalpha() or name[0] == "_"):
        return False
    return all(ch.isalnum() or ch == "_" for ch in name)


def fdl_placeholder(reference: str) -> str:
    if reference.startswith("$$"):
        name = reference[2:]
    elif reference.startswith("${") and reference.endswith("}"):
        name = reference[2:-1].strip()
    elif reference.startswith("$"):
        name = reference[1:]
    else:
        name = reference
    if not safe_placeholder_name(name):
        return "NULL"
    return "${" + name + "}"


def substitute_sql_runtime_params(sql: str) -> str:
    rendered = PARAM_REF_RE.sub(lambda match: fdl_placeholder(match.group(0)), sql)
    return WORKFLOW_PARAM_REF_RE.sub(lambda match: fdl_placeholder(match.group(0)), rendered)


def assert_problem7_sql_contract(xml_path: Path) -> None:
    sql = extract_sql_after_marker(read_xml_text(xml_path), LPDUTY_MAPPING_MARKER)
    if "$$StartDate" not in sql or "$$EndDate" not in sql:
        raise RegressionError("issue 7 fixture no longer contains $$StartDate/$$EndDate SQL parameters")
    rendered = substitute_sql_runtime_params(sql)
    for expected in ("trunc(${StartDate})", "trunc(${EndDate})"):
        if expected not in rendered:
            raise RegressionError(f"runtime parameter placeholder missing from SQL: {expected}")
    if "trunc(NULL)" in rendered or "$$StartDate" in rendered or "$$EndDate" in rendered:
        raise RegressionError("SQL runtime parameter substitution regressed to NULL or raw Informatica tokens")
    wf_text = read_xml_text(xml_path)
    if "$MySqlStartDate" in wf_text:
        rendered_workflow_param = substitute_sql_runtime_params("$MySqlStartDate and $MySqlEndDate")
        if rendered_workflow_param != "${MySqlStartDate} and ${MySqlEndDate}":
            raise RegressionError("single-$ workflow SQL parameters do not render as FDL placeholders")


def assert_problem7_java_contract() -> None:
    source = DATA_SYNC_PARSER.read_text(encoding="utf-8")
    legacy_null_branch = (
        'DEFAULT_XML_EMPTY.equals(declaration.getDefaultSource())) {\n'
        '            return quotedLiteral ? "" : "NULL";'
    )
    required = (
        "sqlRuntimeParameterPlaceholder",
        "isSqlRuntimeParameterResolution",
        "filterGeneratedWorkParams",
        "emitGeneratedWorkParams",
        "infa.fdl.params.emit.generated.work",
        'return "${" + name + "}";',
    )
    if legacy_null_branch in source:
        raise RegressionError("DataSyncParser still maps XML-empty SQL parameters directly to NULL")
    for token in required:
        if token not in source:
            raise RegressionError(f"DataSyncParser missing SQL runtime placeholder contract token: {token}")


def assert_problem8_decision_contract(xml_path: Path) -> None:
    decisions, conditional_links = audit_file(xml_path)
    matches = [item for item in conditional_links if clean(item.condition) == WF_PRIP_DECISION_CONDITION]
    if len(matches) != 1:
        raise RegressionError(f"expected one decision condition link, actual={len(matches)}")
    analysis = matches[0].analysis
    if analysis.status != STATUS_EXECUTABLE:
        raise RegressionError(f"decision output link is not executable: {analysis.status}")
    if analysis.classification != CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING:
        raise RegressionError(f"decision output link classification changed: {analysis.classification}")
    decision_matches = [item for item in decisions if item.task_name == "\u5224\u5b9a"]
    if len(decision_matches) != 1:
        raise RegressionError(f"expected one Decision named \u5224\u5b9a, actual={len(decision_matches)}")
    predicates = decision_matches[0].analysis.task_status_predicates
    if len(predicates) != 11 or len({predicate.task_name for predicate in predicates}) != 11:
        raise RegressionError(f"expected 11 distinct predecessor success predicates, actual={len(predicates)}")
    if any(not predicate.executable or predicate.normalized_status != "SUCCEEDED" for predicate in predicates):
        raise RegressionError("all 11 predecessor predicates must remain executable SUCCEEDED checks")


def assert_problem8_java_contract() -> None:
    source = CONDITION_NODE_BEAN.read_text(encoding="utf-8")
    required = (
        "item.setConditionString(branch.getFdlConditionString())",
        'new ConditionParamValue("${" + taskName + ".opresult}")',
        'new ConditionParamValue("success")',
        "ConditionCompareType.BELONGS",
        "ConditionCompareType.NOT_BELONGS",
        "expectedDecisionResult ? ConditionType.AND : ConditionType.OR",
        "createNativeComparisonEntity(parsed)",
        "ConditionCompareType.IS_TRUE",
        "ConditionCompareType.IS_FALSE",
        "return this.analysis.getConditionEntity().toString()",
        "original Informatica condition",
    )
    for token in required:
        if token not in source:
            raise RegressionError(f"ConditionNodeBean missing executable display condition token: {token}")
    comparison_parse = source.find("ParsedCondition parsed = parseSimpleCondition(condition);")
    bare_operand_parse = source.find("ParsedOperand bareOperand = parseOperand(trimmed, true);")
    if comparison_parse < 0 or bare_operand_parse < 0 or comparison_parse > bare_operand_parse:
        raise RegressionError(
            "ConditionNodeBean must parse comparisons before treating the whole expression as a bare variable"
        )


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Validate issue 7/8 Informatica to FDL regressions.")
    parser.add_argument("xml", nargs="?", type=Path, default=DEFAULT_XML)
    args = parser.parse_args(argv)
    try:
        assert_problem7_sql_contract(args.xml)
        assert_problem7_java_contract()
        assert_problem8_decision_contract(args.xml)
        assert_problem8_java_contract()
    except RegressionError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print("PASS issue 7: SQL runtime parameters render as FDL placeholders, not NULL")
    print("PASS issue 8: executable Decision output link writes a visible FDL condition")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
