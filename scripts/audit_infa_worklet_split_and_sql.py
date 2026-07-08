#!/usr/bin/env python3
import argparse
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter, OrderedDict


def direct_children(elem, tag):
    return [child for child in list(elem) if child.tag == tag]


def direct_task_instances(elem):
    return direct_children(elem, "TASKINSTANCE")


def direct_workflow_links(elem):
    return direct_children(elem, "WORKFLOWLINK")


def task_name(task_instance):
    return task_instance.attrib.get("TASKNAME", "").strip()


def task_type(task_instance):
    return task_instance.attrib.get("TASKTYPE", "").strip()


def workflow_worklets(workflow):
    result = OrderedDict()
    for inst in direct_task_instances(workflow):
        if task_type(inst) == "Worklet" and task_name(inst):
            result[task_name(inst)] = None
    return list(result.keys())


def worklet_session_refs(worklet):
    result = OrderedDict()
    for inst in worklet.iter("TASKINSTANCE"):
        if task_type(inst) == "Session" and task_name(inst):
            result[task_name(inst)] = None
    return list(result.keys())


def has_condition_or_decision(container):
    for link in direct_workflow_links(container):
        if link.attrib.get("CONDITION", "").strip():
            return True
    return any(task_type(inst) == "Decision" for inst in direct_task_instances(container))


def top_level_task_types(container):
    return [task_type(inst) for inst in direct_task_instances(container) if task_type(inst)]


def has_api_candidate(session_index, session_refs):
    for session_name in session_refs:
        session = session_index.get(session_name)
        if session is None:
            continue
        for ext in session.iter("SESSIONEXTENSION"):
            subtype = ext.attrib.get("SUBTYPE", "").lower()
            ext_type = ext.attrib.get("TYPE", "").lower()
            if "web" in subtype or "http" in subtype or "web" in ext_type or "http" in ext_type:
                return True
    return False


def split_reasons(worklet, session_refs, use_count, parent_large, threshold, session_index):
    reasons = []
    task_types = top_level_task_types(worklet)
    if parent_large:
        reasons.append("parent_large")
    if len(session_refs) >= threshold:
        reasons.append("session_count")
    if len(task_types) >= threshold:
        reasons.append("task_count")
    if use_count > 1:
        reasons.append("reused")
    if "Worklet" in task_types:
        reasons.append("nested_worklet")
    if has_condition_or_decision(worklet):
        reasons.append("condition_or_decision")
    if has_api_candidate(session_index, session_refs):
        reasons.append("api_call")
    return reasons


def find_top_level_word(source, word, start=0):
    depth = 0
    in_single = False
    in_double = False
    word_upper = word.upper()
    i = start
    while i <= len(source) - len(word):
        c = source[i]
        if c == "'" and not in_double:
            in_single = not in_single
            i += 1
            continue
        if c == '"' and not in_single:
            in_double = not in_double
            i += 1
            continue
        if in_single or in_double:
            i += 1
            continue
        if c == "(":
            depth += 1
            i += 1
            continue
        if c == ")" and depth > 0:
            depth -= 1
            i += 1
            continue
        if depth == 0 and source[i:i + len(word)].upper() == word_upper:
            before = source[i - 1] if i > 0 else " "
            after = source[i + len(word)] if i + len(word) < len(source) else " "
            if not (before.isalnum() or before == "_") and not (after.isalnum() or after == "_"):
                return i
        i += 1
    return -1


def split_select_items(select_list):
    result = []
    current = []
    depth = 0
    in_single = False
    in_double = False
    for c in select_list:
        if c == "'" and not in_double:
            in_single = not in_single
            current.append(c)
            continue
        if c == '"' and not in_single:
            in_double = not in_double
            current.append(c)
            continue
        if not in_single and not in_double:
            if c == "(":
                depth += 1
            elif c == ")" and depth > 0:
                depth -= 1
            elif c == "," and depth == 0:
                item = "".join(current).strip()
                if item:
                    result.append(item)
                current = []
                continue
        current.append(c)
    item = "".join(current).strip()
    if item:
        result.append(item)
    return result


def validate_reader_sql(sql, context):
    issues = []
    source = (sql or "").strip()
    if not source.upper().startswith("SELECT"):
        return issues
    select_at = find_top_level_word(source, "SELECT")
    from_at = find_top_level_word(source, "FROM", select_at + len("SELECT") if select_at >= 0 else 0)
    if from_at < 0:
        return issues
    select_list = source[select_at + len("SELECT"):from_at].strip()
    if select_list.endswith(","):
        issues.append((context, "select_list_trailing_comma"))
    for item in split_select_items(select_list):
        if find_top_level_word(item, "FROM") >= 0:
            issues.append((context, "top_level_from_inside_select_item"))
    return issues


def iter_source_qualifier_sql(root):
    seen = set()
    for mapping in root.iter("MAPPING"):
        mapping_name = mapping.attrib.get("NAME", "")
        for transform in mapping.iter("TRANSFORMATION"):
            if transform.attrib.get("TYPE") != "Source Qualifier":
                continue
            transform_name = transform.attrib.get("NAME", "")
            for attr in transform.iter("TABLEATTRIBUTE"):
                if attr.attrib.get("NAME") == "Sql Query":
                    value = attr.attrib.get("VALUE", "")
                    key = (mapping_name, transform_name, value)
                    if value.strip() and key not in seen:
                        seen.add(key)
                        yield value, "mapping=%s transformation=%s" % (mapping_name, transform_name)
        for inst in mapping.iter("INSTANCE"):
            for attr in inst.iter("TABLEATTRIBUTE"):
                if attr.attrib.get("NAME") == "Sql Query":
                    value = attr.attrib.get("VALUE", "")
                    key = (mapping_name, inst.attrib.get("NAME", ""), value)
                    if value.strip() and key not in seen:
                        seen.add(key)
                        yield value, "mapping=%s instance=%s" % (mapping_name, inst.attrib.get("NAME", ""))


def load_xml_root(path):
    data = open(path, "rb").read()
    match = re.search(br'encoding=["\']([^"\']+)["\']', data[:256], re.IGNORECASE)
    encodings = []
    if match:
        encodings.append(match.group(1).decode("ascii", "ignore"))
    encodings.extend(["utf-8-sig", "gb18030", "latin1"])
    last_error = None
    for encoding in encodings:
        try:
            text = data.decode(encoding)
            text = re.sub(r'(<\?xml[^>]*?)\s+encoding=["\'][^"\']+["\']', r"\1", text, count=1, flags=re.IGNORECASE)
            return ET.fromstring(text)
        except Exception as exc:
            last_error = exc
    raise last_error


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("xml", help="Informatica XML export")
    parser.add_argument("--threshold", type=int, default=8)
    args = parser.parse_args()

    root = load_xml_root(args.xml)
    workflows = list(root.iter("WORKFLOW"))
    worklets = {worklet.attrib.get("NAME", ""): worklet for worklet in root.iter("WORKLET") if worklet.attrib.get("NAME", "")}
    sessions = {session.attrib.get("NAME", ""): session for session in root.iter("SESSION") if session.attrib.get("NAME", "")}
    uses = Counter()
    for workflow in workflows:
        uses.update(workflow_worklets(workflow))

    split_total = 0
    inline_total = 0
    expected_generated_tasks = 0
    expected_worklet_session_tasks = 0
    expected_direct_session_tasks = 0
    expected_call_only_wrappers = 0
    print("XML=%s" % args.xml)
    print("threshold=%s workflows=%s worklets=%s sessions=%s" % (args.threshold, len(workflows), len(worklets), len(sessions)))
    for workflow in workflows:
        wf_name = workflow.attrib.get("NAME", "")
        names = workflow_worklets(workflow)
        direct_sessions = [
            task_name(inst)
            for inst in direct_task_instances(workflow)
            if task_type(inst) == "Session" and task_name(inst)
        ]
        expanded = sum(len(worklet_session_refs(worklets[name])) for name in names if name in worklets)
        parent_large = len(names) >= args.threshold or expanded >= args.threshold
        split_names = []
        inline_names = []
        worklet_session_tasks = 0
        for name in names:
            worklet = worklets.get(name)
            if worklet is None:
                inline_names.append(name)
                continue
            refs = worklet_session_refs(worklet)
            reasons = split_reasons(worklet, refs, uses[name], parent_large, args.threshold, sessions)
            if refs and reasons:
                split_names.append((name, reasons, len(refs)))
                if len(refs) >= args.threshold:
                    worklet_session_tasks += len(refs)
            else:
                inline_names.append(name)
        split_total += len(split_names)
        inline_total += len(inline_names)
        direct_session_tasks = len(direct_sessions) if split_names else 0
        workflow_expected = 1 + direct_session_tasks + len(split_names) + worklet_session_tasks
        expected_generated_tasks += workflow_expected
        expected_worklet_session_tasks += worklet_session_tasks
        expected_direct_session_tasks += direct_session_tasks
        expected_call_only_wrappers += 1 + sum(1 for _, _, ref_count in split_names if ref_count >= args.threshold)
        print("WORKFLOW name=%s worklet_calls=%s direct_sessions=%s expanded_sessions=%s split=%s inline=%s expected_tasks=%s" % (
            wf_name, len(names), len(direct_sessions), expanded, len(split_names), len(inline_names), workflow_expected))
        for name, reasons, ref_count in split_names:
            print("  SPLIT worklet=%s sessions=%s reasons=%s" % (name, ref_count, ",".join(reasons)))

    sql_count = 0
    sql_issues = []
    for sql, context in iter_source_qualifier_sql(root):
        sql_count += 1
        sql_issues.extend(validate_reader_sql(sql, context))
    if sql_issues:
        print("SQL_SHAPE FAIL queries=%s issues=%s" % (sql_count, len(sql_issues)))
        for context, issue in sql_issues:
            print("  SQL_ISSUE context=%s issue=%s" % (context, issue))
    else:
        print("SQL_SHAPE PASS queries=%s issues=0" % sql_count)
    print("SUMMARY split_worklets=%s inline_worklets=%s expected_generated_tasks=%s direct_session_tasks=%s worklet_session_tasks=%s call_only_wrappers=%s" % (
        split_total, inline_total, expected_generated_tasks, expected_direct_session_tasks, expected_worklet_session_tasks, expected_call_only_wrappers))
    return 1 if sql_issues else 0


if __name__ == "__main__":
    sys.exit(main())
