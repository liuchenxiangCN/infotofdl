#!/usr/bin/env python3
"""Audit Informatica Workflow/Worklet Decision nodes and conditional links."""

from __future__ import annotations

import argparse
import re
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path


DEFAULT_XML_DIR = Path("/mnt/c/Users/nifen/Desktop/项目文档/2-项目/百年人寿")

STATUS_EXECUTABLE = "FDL_TRANSLATION_READY"
STATUS_FDL_GENERATION_RESEARCH_PENDING = "FDL_GENERATION_RESEARCH_PENDING"
STATUS_VISIBLE_ONLY = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING"
STATUS_UNSUPPORTED = "FDL_GENERATION_RESEARCH_PENDING_UNSUPPORTED"
CLASS_EXECUTABLE_TASK_STATUS_CONDITION = "EXECUTABLE_TASK_STATUS_CONDITION"
CLASS_FDL_GENERATION_RESEARCH_PENDING_TASK_STATUS_CONDITION = "FDL_GENERATION_RESEARCH_PENDING_TASK_STATUS_CONDITION"
CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING = "EXECUTABLE_DECISION_OUTPUT_BINDING"
CLASS_FDL_GENERATION_RESEARCH_PENDING_DECISION_OUTPUT_BINDING = "FDL_GENERATION_RESEARCH_PENDING_DECISION_OUTPUT_BINDING"
CLASS_FDL_GENERATION_RESEARCH_PENDING_RUNTIME_SEMANTICS = "FDL_GENERATION_RESEARCH_PENDING_RUNTIME_SEMANTICS"


@dataclass
class TaskStatusPredicate:
    task_name: str
    operator: str
    status_literal: str
    normalized_status: str
    executable: bool = False

    @property
    def limitation(self) -> str:
        parts = ["task status predicate requires FDL runtime-status binding generation"]
        if self.status_literal and not self.normalized_status:
            parts.append(f"status literal {self.status_literal} is not a known generic Informatica status")
        return "; ".join(parts)

    @property
    def required_bindings(self) -> list[str]:
        return [
            f"runtime status source for task {self.task_name}",
            "Informatica status literal to FDL runtime status mapping",
            f"status aggregation timing for task {self.task_name}",
        ]

    def visible(self) -> str:
        normalized = self.normalized_status or "UNKNOWN_STATUS_LITERAL"
        return (
            f"{self.task_name}.Status {self.operator} {self.status_literal} "
            f"normalized={normalized} executable={str(self.executable).lower()}"
        )


@dataclass
class DecisionOutputReference:
    decision_name: str
    operator: str
    expected_value: str
    executable: bool = False

    @property
    def limitation(self) -> str:
        parts = ["Decision output binding requires FDL storage for Decision expression result"]
        if self.expected_value.lower() not in {"true", "false"}:
            parts.append(f"expected value {self.expected_value} is not a boolean literal")
        return "; ".join(parts)

    @property
    def required_bindings(self) -> list[str]:
        return [
            f"runtime boolean output for Decision {self.decision_name}",
            f"Decision expression result storage for {self.decision_name}",
            f"outgoing branch condition binding for Decision {self.decision_name}",
        ]

    def visible(self) -> str:
        return (
            f"{self.decision_name}.Condition {self.operator} {self.expected_value} "
            f"executable={str(self.executable).lower()}"
        )


@dataclass
class ConditionAnalysis:
    status: str
    limitation: str = ""
    classification: str = ""
    variables: list[str] = field(default_factory=list)
    task_status_predicates: list[TaskStatusPredicate] = field(default_factory=list)
    decision_output_refs: list[DecisionOutputReference] = field(default_factory=list)
    required_runtime_bindings: list[str] = field(default_factory=list)

    def __post_init__(self) -> None:
        if not self.classification:
            self.classification = classify_analysis(
                self.status,
                self.task_status_predicates,
                self.decision_output_refs,
            )
        self.required_runtime_bindings = unique(self.required_runtime_bindings)


@dataclass
class DecisionAudit:
    container_tag: str
    container_name: str
    task_name: str
    expression: str
    analysis: ConditionAnalysis


@dataclass
class LinkAudit:
    container_tag: str
    container_name: str
    from_task: str
    to_task: str
    condition: str
    analysis: ConditionAnalysis


def read_xml(path: Path) -> ET.Element:
    raw = path.read_bytes()
    header = raw[:256].lower()
    encoding = "gb18030" if b"gb2312" in header or b"ms936" in header else "utf-8"
    text = raw.decode(encoding, errors="replace")
    text = re.sub(r"<\?xml[^>]*\?>", "<?xml version=\"1.0\"?>", text, count=1)
    return ET.fromstring(text)


def clean(value: str | None) -> str:
    return (value or "").strip()


def short(value: str, limit: int = 240) -> str:
    compact = re.sub(r"\s+", " ", clean(value))
    return compact if len(compact) <= limit else compact[: limit - 3] + "..."


def direct_children(element: ET.Element, tag: str) -> list[ET.Element]:
    return [child for child in list(element) if child.tag == tag]


def is_word_char(ch: str) -> bool:
    return ch.isalnum() or ch in {"_", "$", "."}


def starts_with_word(source: str, index: int, word: str) -> bool:
    end = index + len(word)
    if end > len(source) or source[index:end].upper() != word:
        return False
    left_ok = index == 0 or not is_word_char(source[index - 1])
    right_ok = end == len(source) or not is_word_char(source[end])
    return left_ok and right_ok


def split_top_level(source: str, operator: str) -> list[str]:
    depth = 0
    in_single = False
    in_double = False
    last = 0
    parts: list[str] = []
    i = 0
    while i < len(source):
        ch = source[i]
        if ch == "'" and not in_double:
            in_single = not in_single
        elif ch == '"' and not in_single:
            in_double = not in_double
        elif not in_single and not in_double:
            if ch == "(":
                depth += 1
            elif ch == ")" and depth > 0:
                depth -= 1
            elif depth == 0 and starts_with_word(source, i, operator):
                parts.append(source[last:i].strip())
                i += len(operator)
                last = i
                continue
        i += 1
    if last == 0:
        return [source.strip()]
    parts.append(source[last:].strip())
    return parts


def strip_outer_parens(value: str) -> str:
    trimmed = clean(value)
    while trimmed.startswith("(") and trimmed.endswith(")") and wraps_outer_parens(trimmed):
        trimmed = trimmed[1:-1].strip()
    return trimmed


def wraps_outer_parens(value: str) -> bool:
    depth = 0
    in_single = False
    in_double = False
    for index, ch in enumerate(value):
        if ch == "'" and not in_double:
            in_single = not in_single
        elif ch == '"' and not in_single:
            in_double = not in_double
        elif not in_single and not in_double:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0 and index < len(value) - 1:
                    return False
    return depth == 0


def strip_quotes(value: str) -> tuple[str, bool]:
    value = clean(value)
    if len(value) >= 2 and ((value.startswith("'") and value.endswith("'")) or (value.startswith('"') and value.endswith('"'))):
        return value[1:-1], True
    return value, False


def unique(values: list[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        if value and value not in seen:
            seen.add(value)
            result.append(value)
    return result


def classify_analysis(
    status: str,
    task_status_predicates: list[TaskStatusPredicate] | None,
    decision_output_refs: list[DecisionOutputReference] | None,
) -> str:
    if status in {STATUS_VISIBLE_ONLY, STATUS_UNSUPPORTED}:
        return status
    has_task_status = bool(task_status_predicates)
    has_decision_output = bool(decision_output_refs)
    if has_task_status and has_decision_output:
        return CLASS_FDL_GENERATION_RESEARCH_PENDING_RUNTIME_SEMANTICS
    if has_task_status:
        return CLASS_EXECUTABLE_TASK_STATUS_CONDITION if status == STATUS_EXECUTABLE else CLASS_FDL_GENERATION_RESEARCH_PENDING_TASK_STATUS_CONDITION
    if has_decision_output:
        return CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING if status == STATUS_EXECUTABLE else CLASS_FDL_GENERATION_RESEARCH_PENDING_DECISION_OUTPUT_BINDING
    return status


def normalize_status_literal(value: str) -> str:
    literal = strip_quotes(value)[0].strip().lower()
    if literal in {"succeeded", "success", "successful"}:
        return "SUCCEEDED"
    if literal in {"failed", "fail", "failure", "error"}:
        return "FAILED"
    if literal == "disabled":
        return "DISABLED"
    if literal in {"running", "started"}:
        return "RUNNING"
    if literal in {"aborted", "stopped", "terminated"}:
        return "STOPPED"
    if literal in {"waiting", "scheduled", "queued"}:
        return "WAITING"
    if literal == "skipped":
        return "SKIPPED"
    return ""


def runtime_ref(value: str) -> tuple[str, str]:
    if "." not in value:
        return "", ""
    owner, prop = value.rsplit(".", 1)
    prop_lower = prop.lower()
    if prop_lower in {"status", "condition"}:
        return owner, prop_lower
    return "", ""


def task_status_predicate(variable: str, operator: str, expected: str) -> TaskStatusPredicate | None:
    owner, prop = runtime_ref(variable)
    if prop != "status":
        return None
    literal = strip_quotes(expected)[0]
    return TaskStatusPredicate(owner, operator, literal, normalize_status_literal(literal))


def decision_output_ref(variable: str, operator: str, expected: str) -> DecisionOutputReference | None:
    owner, prop = runtime_ref(variable)
    if prop != "condition":
        return None
    return DecisionOutputReference(owner, operator, strip_quotes(expected)[0])


def equality_operator(operator: str) -> bool:
    return operator in {"=", "==", "!=", "<>"}


def parse_operand(raw: str, bare_word_is_variable: bool) -> tuple[str, bool, bool, str]:
    value, quoted = strip_quotes(raw)
    if quoted:
        return value, False, False, ""
    variable = False
    scoped = False
    runtime_ref = False
    if value.startswith("$$"):
        variable = True
        value = value[2:].strip()
    elif value.startswith("$"):
        variable = True
        value = value[1:].strip()
    elif bare_word_is_variable and re.fullmatch(r"[A-Za-z_\u4e00-\u9fa5][A-Za-z0-9_.\u4e00-\u9fa5]*", value):
        variable = True
    if variable and "." in value:
        scoped = True
        suffix = value.rsplit(".", 1)[1].lower()
        runtime_ref = suffix in {"status", "condition"}
    if runtime_ref:
        if value.rsplit(".", 1)[1].lower() == "status":
            return value, variable, scoped, f"references Informatica task status variable {value}; FDL condition entity has no proven upstream task-status binding"
        return value, variable, scoped, f"references Informatica Decision output variable {value}; FDL condition entity has no proven Decision output binding"
    if scoped:
        return value, variable, scoped, f"references scoped Informatica variable {value}; verify mapping/workflow parameter binding"
    return value, variable, scoped, ""


def merge_variables(*values: list[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for group in values:
        for item in group:
            key = item.upper()
            if item and key not in seen:
                seen.add(key)
                result.append(item)
    return result


def analyze_condition(condition: str) -> ConditionAnalysis:
    condition = clean(condition)
    if not condition:
        return ConditionAnalysis(STATUS_VISIBLE_ONLY, "empty condition")
    for operator in ("OR", "AND"):
        parts = split_top_level(condition, operator)
        if len(parts) > 1:
            child = [analyze_condition(strip_outer_parens(part)) for part in parts]
            variables = merge_variables(*[item.variables for item in child])
            task_status_predicates = [predicate for item in child for predicate in item.task_status_predicates]
            decision_output_refs = [reference for item in child for reference in item.decision_output_refs]
            required_runtime_bindings = [binding for item in child for binding in item.required_runtime_bindings]
            if any(item.status == STATUS_UNSUPPORTED for item in child):
                return ConditionAnalysis(
                    STATUS_UNSUPPORTED,
                    f"compound {operator} condition contains unsupported operands",
                    variables=variables,
                    task_status_predicates=task_status_predicates,
                    decision_output_refs=decision_output_refs,
                    required_runtime_bindings=required_runtime_bindings,
                )
            if any(item.status != STATUS_EXECUTABLE for item in child):
                limitations = "; ".join(unique([item.limitation for item in child if item.limitation]))
                return ConditionAnalysis(
                    STATUS_FDL_GENERATION_RESEARCH_PENDING,
                    limitations or f"compound {operator} condition requires FDL condition binding generation",
                    variables=variables,
                    task_status_predicates=task_status_predicates,
                    decision_output_refs=decision_output_refs,
                    required_runtime_bindings=required_runtime_bindings,
                )
            return ConditionAnalysis(STATUS_EXECUTABLE, variables=variables)
    if condition.lower() in {"true", "false"}:
        return ConditionAnalysis(STATUS_EXECUTABLE)
    left_bare, left_is_var, _, left_reason = parse_operand(condition, True)
    if left_is_var and not re.search(r"\s*(==|=|!=|<>|>=|<=|>|<)\s*", condition):
        predicate = task_status_predicate(left_bare, "IS_TRUE", "true")
        output_ref = decision_output_ref(left_bare, "IS_TRUE", "true")
        if predicate or output_ref:
            required = []
            if predicate:
                required.extend(predicate.required_bindings)
            if output_ref:
                required.extend(output_ref.required_bindings)
            return ConditionAnalysis(
                STATUS_FDL_GENERATION_RESEARCH_PENDING,
                left_reason,
                variables=[left_bare],
                task_status_predicates=[predicate] if predicate else [],
                decision_output_refs=[output_ref] if output_ref else [],
                required_runtime_bindings=required,
            )
        status = STATUS_FDL_GENERATION_RESEARCH_PENDING if left_reason else STATUS_EXECUTABLE
        return ConditionAnalysis(status, left_reason, variables=[left_bare])
    match = re.match(r"^\s*\$?(.+?)\s*(==|=|!=|<>|>=|<=|>|<)\s*(.+?)\s*$", condition)
    if not match:
        return ConditionAnalysis(STATUS_UNSUPPORTED, "condition is not a structurally safe boolean/comparison expression")
    left, left_var, _, left_reason = parse_operand(match.group(1), True)
    operator = match.group(2)
    right, right_var, _, right_reason = parse_operand(match.group(3), False)
    variables = []
    if left_var:
        variables.append(left)
    if right_var:
        variables.append(right)
    task_status_predicates: list[TaskStatusPredicate] = []
    decision_output_refs: list[DecisionOutputReference] = []
    required_runtime_bindings: list[str] = []
    limitations: list[str] = []
    unsupported_runtime = False
    for variable, expected in ((left, right), (right, left)):
        predicate = task_status_predicate(variable, operator, expected)
        if predicate:
            task_status_predicates.append(predicate)
            required_runtime_bindings.extend(predicate.required_bindings)
            limitations.append(predicate.limitation)
            if not equality_operator(operator):
                unsupported_runtime = True
                limitations.append(f"task status predicates support only =, ==, !=, <> comparisons; found {operator}")
        output_ref = decision_output_ref(variable, operator, expected)
        if output_ref:
            decision_output_refs.append(output_ref)
            required_runtime_bindings.extend(output_ref.required_bindings)
            limitations.append(output_ref.limitation)
    if task_status_predicates or decision_output_refs:
        limitation = "; ".join(unique([item for item in limitations if item]))
        if not limitation:
            limitation = "Informatica runtime variable binding requires FDL runtime adapter generation"
        return ConditionAnalysis(
            STATUS_UNSUPPORTED if unsupported_runtime else STATUS_FDL_GENERATION_RESEARCH_PENDING,
            limitation,
            variables=variables,
            task_status_predicates=task_status_predicates,
            decision_output_refs=decision_output_refs,
            required_runtime_bindings=required_runtime_bindings,
        )
    limitation = "; ".join(reason for reason in (left_reason, right_reason) if reason)
    status = STATUS_FDL_GENERATION_RESEARCH_PENDING if limitation else STATUS_EXECUTABLE
    return ConditionAnalysis(status, limitation, variables=variables)


def find_task_expression(container: ET.Element, task_name: str) -> str:
    for task in direct_children(container, "TASK"):
        if task.attrib.get("TYPE") != "Decision" or task.attrib.get("NAME") != task_name:
            continue
        for attr in direct_children(task, "ATTRIBUTE"):
            name = attr.attrib.get("NAME", "").lower()
            if name in {"decision name", "decision expression"}:
                return clean(attr.attrib.get("VALUE"))
    return ""


def canonical_name(value: str) -> str:
    return clean(value).casefold()


def find_task_instance(container: ET.Element, task_name: str) -> ET.Element | None:
    for task in direct_children(container, "TASKINSTANCE"):
        name = clean(task.attrib.get("NAME") or task.attrib.get("TASKNAME"))
        if name == task_name:
            return task
    return None


def native_decision_success_gate_reason(
    container: ET.Element,
    task_name: str,
    expression: str,
    analysis: ConditionAnalysis,
    links: list[ET.Element],
) -> str:
    if not expression or not analysis.task_status_predicates or analysis.decision_output_refs:
        return ""
    if len(split_top_level(expression, "OR")) > 1:
        return ""
    if len(split_top_level(expression, "AND")) != len(analysis.task_status_predicates):
        return ""
    predicate_tasks: set[str] = set()
    for predicate in analysis.task_status_predicates:
        if predicate.operator not in {"=", "=="}:
            return ""
        if predicate.normalized_status != "SUCCEEDED":
            return ""
        if not predicate.task_name:
            return ""
        predicate_tasks.add(canonical_name(predicate.task_name))
    if len(predicate_tasks) != len(analysis.task_status_predicates):
        return ""
    task_instance = find_task_instance(container, task_name)
    if task_instance is None:
        return ""
    input_logic = clean(task_instance.attrib.get("TREAT_INPUTLINK_AS_AND"))
    if input_logic and input_logic.upper() != "YES":
        return ""
    incoming_tasks: set[str] = set()
    for link in links:
        if clean(link.attrib.get("TOTASK")) != task_name:
            continue
        if clean(link.attrib.get("CONDITION")):
            return ""
        incoming_tasks.add(canonical_name(link.attrib.get("FROMTASK", "")))
    if not incoming_tasks or incoming_tasks != predicate_tasks:
        return ""
    return (
        "all Decision predicates are equality checks for upstream task succeeded status, "
        "all predicate tasks have direct empty incoming links, and the Decision TASKINSTANCE uses AND input semantics; "
        "FDL relations use ON_SUCCESS for those incoming links"
    )


def executable_task_status_analysis(condition: str, from_task: str) -> ConditionAnalysis | None:
    analysis = analyze_condition(condition)
    if analysis.decision_output_refs or len(analysis.task_status_predicates) != 1:
        return None
    predicate = analysis.task_status_predicates[0]
    if canonical_name(predicate.task_name) != canonical_name(from_task):
        return None
    if predicate.operator not in {"=", "=="}:
        return None
    line_condition = ""
    if predicate.normalized_status == "SUCCEEDED":
        line_condition = "ON_SUCCESS"
    elif predicate.normalized_status == "FAILED":
        line_condition = "ON_FAIL"
    if not line_condition:
        return None
    predicate.executable = True
    return ConditionAnalysis(
        STATUS_EXECUTABLE,
        f"native FDL relation condition {line_condition} represents this direct upstream task status branch",
        classification=CLASS_EXECUTABLE_TASK_STATUS_CONDITION,
        variables=analysis.variables,
        task_status_predicates=[predicate],
    )


def executable_decision_output_analysis(condition: str, decision_name: str, reason: str) -> ConditionAnalysis | None:
    analysis = analyze_condition(condition)
    if analysis.task_status_predicates or len(analysis.decision_output_refs) != 1:
        return None
    reference = analysis.decision_output_refs[0]
    if canonical_name(reference.decision_name) != canonical_name(decision_name):
        return None
    if reference.operator not in {"=", "=="}:
        return None
    if reference.expected_value.lower() != "true":
        return None
    reference.executable = True
    return ConditionAnalysis(
        STATUS_EXECUTABLE,
        reason,
        classification=CLASS_EXECUTABLE_DECISION_OUTPUT_BINDING,
        variables=analysis.variables,
        decision_output_refs=[reference],
    )


def audit_file(path: Path) -> tuple[list[DecisionAudit], list[LinkAudit]]:
    root = read_xml(path)
    decisions: list[DecisionAudit] = []
    conditional_links: list[LinkAudit] = []
    for container_tag in ("WORKFLOW", "WORKLET"):
        for container in root.iter(container_tag):
            container_name = clean(container.attrib.get("NAME"))
            direct_links = direct_children(container, "WORKFLOWLINK")
            native_decision_reasons: dict[str, str] = {}
            for task in direct_children(container, "TASKINSTANCE"):
                if task.attrib.get("TASKTYPE") != "Decision":
                    continue
                task_name = clean(task.attrib.get("NAME") or task.attrib.get("TASKNAME"))
                expression = find_task_expression(container, task_name)
                analysis = analyze_condition(expression) if expression else ConditionAnalysis(
                    STATUS_VISIBLE_ONLY,
                    "Decision TASK expression metadata was not parsed",
                )
                native_reason = native_decision_success_gate_reason(container, task_name, expression, analysis, direct_links)
                if native_reason:
                    for predicate in analysis.task_status_predicates:
                        predicate.executable = True
                    analysis = ConditionAnalysis(
                        STATUS_EXECUTABLE,
                        native_reason,
                        classification=CLASS_EXECUTABLE_TASK_STATUS_CONDITION,
                        variables=analysis.variables,
                        task_status_predicates=analysis.task_status_predicates,
                    )
                    native_decision_reasons[task_name] = native_reason
                decisions.append(DecisionAudit(container_tag, container_name, task_name, expression, analysis))
            for link in direct_links:
                condition = clean(link.attrib.get("CONDITION"))
                if condition:
                    from_task = clean(link.attrib.get("FROMTASK"))
                    analysis = None
                    if from_task in native_decision_reasons:
                        analysis = executable_decision_output_analysis(condition, from_task, native_decision_reasons[from_task])
                    if analysis is None:
                        analysis = executable_task_status_analysis(condition, from_task)
                    if analysis is None:
                        analysis = analyze_condition(condition)
                    conditional_links.append(
                        LinkAudit(
                            container_tag,
                            container_name,
                            from_task,
                            clean(link.attrib.get("TOTASK")),
                            condition,
                            analysis,
                        )
                    )
    return decisions, conditional_links


def resolve_files(args: argparse.Namespace) -> list[Path]:
    if args.xml:
        return [Path(item) for item in args.xml]
    return sorted(path for path in args.xml_dir.iterdir() if path.suffix.lower() == ".xml")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xml", nargs="*", help="Specific Informatica XML file(s) to audit.")
    parser.add_argument("--xml-dir", type=Path, default=DEFAULT_XML_DIR)
    args = parser.parse_args()

    exit_code = 0
    for path in resolve_files(args):
        decisions, conditional_links = audit_file(path)
        status_counts = Counter(item.analysis.classification for item in [*decisions, *conditional_links])
        print(
            f"{path.name}: decisions={len(decisions)} conditional_links={len(conditional_links)} "
            f"statuses={dict(status_counts)}"
        )
        for decision in decisions:
            print(
                f"  DECISION {decision.container_tag}/{decision.container_name}: "
                f"{decision.task_name} status={decision.analysis.status} classification={decision.analysis.classification}"
            )
            if decision.expression:
                print(f"    EXPRESSION {short(decision.expression)}")
            if decision.analysis.variables:
                print(f"    VARIABLES {', '.join(decision.analysis.variables)}")
            for predicate in decision.analysis.task_status_predicates:
                print(f"    TASK_STATUS_PREDICATE {predicate.visible()}")
            for reference in decision.analysis.decision_output_refs:
                print(f"    DECISION_OUTPUT_REFERENCE {reference.visible()}")
            if decision.analysis.required_runtime_bindings:
                print(f"    REQUIRED_BINDINGS {'; '.join(decision.analysis.required_runtime_bindings)}")
            if decision.analysis.limitation:
                print(f"    LIMITATION {decision.analysis.limitation}")
        for link in conditional_links:
            print(
                f"  CONDITION {link.container_tag}/{link.container_name}: "
                f"{link.from_task} -> {link.to_task}: {link.condition} status={link.analysis.status} classification={link.analysis.classification}"
            )
            if link.analysis.variables:
                print(f"    VARIABLES {', '.join(link.analysis.variables)}")
            for predicate in link.analysis.task_status_predicates:
                print(f"    TASK_STATUS_PREDICATE {predicate.visible()}")
            for reference in link.analysis.decision_output_refs:
                print(f"    DECISION_OUTPUT_REFERENCE {reference.visible()}")
            if link.analysis.required_runtime_bindings:
                print(f"    REQUIRED_BINDINGS {'; '.join(link.analysis.required_runtime_bindings)}")
            if link.analysis.limitation:
                print(f"    LIMITATION {link.analysis.limitation}")
        if path.name == "WF_PRIP_Stage.XML":
            expected = "$判定.Condition = true"
            if not any(item.condition == expected for item in conditional_links):
                exit_code = 1
                print(f"  ERROR expected condition not found: {expected}")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
