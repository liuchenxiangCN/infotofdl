package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl;

import com.fr.dp.component.domain.entities.ConditionComponent;
import com.fr.dp.component.domain.entities.entity.DPConditionItem;
import com.fr.dp.condition.entity.AndOrConditionEntity;
import com.fr.dp.condition.entity.CompareConditionEntity;
import com.fr.dp.condition.entity.ConditionType;
import com.fr.dp.condition.entity.DPConditionEntity;
import com.fr.dp.condition.entity.value.ConditionParamValue;
import com.fr.dp.condition.entity.value.ConditionVariableValue;
import com.fr.dp.condition.entity.value.SelfConditionEntity;
import com.fr.dp.condition.type.ConditionCompareType;
import com.fr.dp.condition.type.ConditionValueType;
import com.fr.dp.devwork.work.component.api.Component;
import com.fr.dp.devwork.work.component.dto.ComponentDTO;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.single.DefaultSingleXMLNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.service.FDLService;
import com.fr.stable.StringUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionNodeBean
extends DefaultSingleXMLNodeBean {
    public static final String STATUS_EXECUTABLE = "FDL_TRANSLATION_READY";
    public static final String STATUS_REVIEW_REQUIRED = "FDL_GENERATION_RESEARCH_PENDING";
    public static final String STATUS_VISIBLE_ONLY = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING";
    public static final String STATUS_UNSUPPORTED = "FDL_GENERATION_RESEARCH_PENDING_UNSUPPORTED";
    public static final String CLASSIFICATION_EXECUTABLE_TASK_STATUS_CONDITION = "EXECUTABLE_TASK_STATUS_CONDITION";
    public static final String CLASSIFICATION_REVIEW_REQUIRED_TASK_STATUS_CONDITION = "FDL_GENERATION_RESEARCH_PENDING_TASK_STATUS_CONDITION";
    public static final String CLASSIFICATION_EXECUTABLE_DECISION_OUTPUT_BINDING = "EXECUTABLE_DECISION_OUTPUT_BINDING";
    public static final String CLASSIFICATION_REVIEW_REQUIRED_DECISION_OUTPUT_BINDING = "FDL_GENERATION_RESEARCH_PENDING_DECISION_OUTPUT_BINDING";
    public static final String CLASSIFICATION_REVIEW_REQUIRED_RUNTIME_SEMANTICS = "FDL_GENERATION_RESEARCH_PENDING_RUNTIME_SEMANTICS";
    public static final String FDL_LINE_ON_SUCCESS = "ON_SUCCESS";
    public static final String FDL_LINE_ON_FAIL = "ON_FAIL";
    public static final String FDL_LINE_ON_UNCONDITIONAL = "ON_UNCONDITIONAL";

    private final String sourceDescription;
    private final boolean nativeDecisionOutputExecutable;
    private final String nativeDecisionOutputName;
    private final String nativeDecisionOutputReason;
    private final List<ConditionBranch> branches = new ArrayList<ConditionBranch>();

    public ConditionNodeBean(int x, int y, String name, String id, String sourceDescription) {
        this(x, y, name, id, sourceDescription, false, "", "");
    }

    public ConditionNodeBean(int x, int y, String name, String id, String sourceDescription,
        boolean nativeDecisionOutputExecutable, String nativeDecisionOutputName, String nativeDecisionOutputReason) {
        super(x, y, name, id);
        this.sourceDescription = sourceDescription == null ? "" : sourceDescription;
        this.nativeDecisionOutputExecutable = nativeDecisionOutputExecutable;
        this.nativeDecisionOutputName = nativeDecisionOutputName == null ? "" : nativeDecisionOutputName;
        this.nativeDecisionOutputReason = nativeDecisionOutputReason == null ? "" : nativeDecisionOutputReason;
    }

    public void clearBranches() {
        this.branches.clear();
    }

    public void addBranch(String nextNodeId, String nextNodeName, String condition) {
        if (!StringUtils.isNotEmpty(nextNodeId)) {
            return;
        }
        this.branches.add(new ConditionBranch(nextNodeId, nextNodeName, condition, this.analyzeBranchCondition(condition)));
    }

    @Override
    public ComponentDTO getFDLComponentDTO() {
        return FDLService.INSTANCE.createComponentDTO(this.getX(), this.getY(), this.getFDLNodeType(), this.getFDLComponent());
    }

    @Override
    public Component getFDLComponent() {
        ConditionComponent result = new ConditionComponent();
        result.setId(this.getId());
        result.setName(this.getName());
        result.setNote(this.createNote());
        result.setExecuteLogic("AND");
        result.setDisabled(false);
        List<DPConditionItem> conditionItems = new ArrayList<DPConditionItem>();
        for (ConditionBranch branch : this.branches) {
            DPConditionItem item = new DPConditionItem();
            item.setNextNodeId(branch.nextNodeId);
            item.setNextNodeName(branch.nextNodeName);
            item.setConditionString(branch.condition);
            item.setDescription("Informatica condition: " + branch.condition + " [" + branch.analysis.getClassification() + "]");
            if (STATUS_EXECUTABLE.equals(branch.analysis.getStatus()) && branch.analysis.getConditionEntity() != null) {
                item.setConditionEntity(branch.analysis.getConditionEntity());
            }
            conditionItems.add(item);
        }
        result.setConditionItems(conditionItems);
        return result;
    }

    @Override
    public String getFDLNodeType() {
        return "CONDITION_NODE";
    }

    private String createNote() {
        StringBuilder note = new StringBuilder();
        if (StringUtils.isNotEmpty(this.sourceDescription)) {
            note.append(this.sourceDescription).append("\n");
        }
        note.append("Migrated from Informatica Decision/WORKFLOWLINK condition. Verify runtime equivalence in FDL.");
        for (ConditionBranch branch : this.branches) {
            note.append("\n").append("To ").append(branch.nextNodeName).append(": ").append(branch.condition);
            note.append(" [").append(branch.analysis.getStatus()).append("]");
            if (!branch.analysis.getClassification().equals(branch.analysis.getStatus())) {
                note.append(" classification=").append(branch.analysis.getClassification());
            }
            if (StringUtils.isNotEmpty(branch.analysis.getLimitation())) {
                note.append(" ").append(branch.analysis.getLimitation());
            }
            if (StringUtils.isNotEmpty(branch.analysis.getRuntimeDetails())) {
                note.append("\n  Parsed runtime semantics: ").append(branch.analysis.getRuntimeDetails());
            }
            if (StringUtils.isNotEmpty(branch.analysis.getRequiredRuntimeBindingsText())) {
                note.append("\n  Required runtime bindings: ").append(branch.analysis.getRequiredRuntimeBindingsText());
            }
        }
        return note.toString();
    }

    public static ConditionAnalysis analyzeCondition(String condition) {
        if (!StringUtils.isNotEmpty(condition)) {
            return ConditionAnalysis.visibleOnly("empty condition");
        }
        String trimmed = condition.trim();
        ConditionAnalysis logical = parseLogicalCondition(trimmed);
        if (logical != null) {
            return logical;
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            CompareConditionEntity entity = new CompareConditionEntity();
            entity.setSource(new ConditionParamValue("true"));
            entity.setTarget(new ConditionParamValue(trimmed.toLowerCase()));
            entity.setValueType(ConditionValueType.BOOLEAN);
            entity.setConditionCompareType(ConditionCompareType.EQUAL);
            return ConditionAnalysis.executable(entity);
        }
        ParsedOperand bareOperand = parseOperand(trimmed, true);
        if (bareOperand.variable && bareOperand.safeForFdlCondition()) {
            SelfConditionEntity entity = new SelfConditionEntity();
            entity.setSource(new ConditionVariableValue(bareOperand.value));
            entity.setValueType(ConditionValueType.BOOLEAN);
            entity.setConditionCompareType(ConditionCompareType.IS_TRUE);
            return ConditionAnalysis.executable(entity);
        }
        if (bareOperand.variable && bareOperand.taskRuntime) {
            return ConditionAnalysis.runtimeReviewRequired(
                null,
                bareOperand.runtimeLimitation("IS_TRUE", "true"),
                bareOperand.createTaskStatusPredicate("IS_TRUE", "true"),
                bareOperand.createDecisionOutputReference("IS_TRUE", "true"));
        }
        if (bareOperand.variable) {
            return ConditionAnalysis.reviewRequired(bareOperand.reviewReason());
        }
        ParsedCondition parsed = parseSimpleCondition(condition);
        if (parsed == null) {
            return ConditionAnalysis.unsupported("condition is not a structurally safe boolean/comparison expression");
        }
        CompareConditionEntity entity = new CompareConditionEntity();
        entity.setSource(parsed.left.toConditionValue());
        entity.setTarget(parsed.right.toConditionValue());
        entity.setValueType(parsed.valueType);
        entity.setConditionCompareType(parsed.compareType);
        ConditionAnalysis runtimeAnalysis = parsed.createRuntimeAnalysis(entity);
        if (runtimeAnalysis != null) {
            return runtimeAnalysis;
        }
        if (parsed.safeForFdlCondition()) {
            return ConditionAnalysis.executable(entity);
        }
        return ConditionAnalysis.reviewRequired(entity, parsed.reviewReason());
    }

    public static boolean isNativeFdlLineConditionSupported(String condition, String fromTaskName) {
        return StringUtils.isNotEmpty(resolveNativeFdlLineCondition(condition, fromTaskName));
    }

    public static String resolveFdlLineCondition(String condition, String fromTaskName) {
        String nativeCondition = resolveNativeFdlLineCondition(condition, fromTaskName);
        return StringUtils.isNotEmpty(nativeCondition) ? nativeCondition : FDL_LINE_ON_SUCCESS;
    }

    private static String resolveNativeFdlLineCondition(String condition, String fromTaskName) {
        if (!StringUtils.isNotEmpty(condition) || !StringUtils.isNotEmpty(fromTaskName)) {
            return "";
        }
        ConditionAnalysis analysis = analyzeCondition(condition);
        if (!analysis.getDecisionOutputReferences().isEmpty() || analysis.getTaskStatusPredicates().size() != 1) {
            return "";
        }
        TaskStatusPredicate predicate = analysis.getTaskStatusPredicates().get(0);
        if (!fromTaskName.equalsIgnoreCase(predicate.getTaskName())) {
            return "";
        }
        if (!"=".equals(predicate.getOperator()) && !"==".equals(predicate.getOperator())) {
            return "";
        }
        if ("SUCCEEDED".equals(predicate.getNormalizedStatusLiteral())) {
            return FDL_LINE_ON_SUCCESS;
        }
        if ("FAILED".equals(predicate.getNormalizedStatusLiteral())) {
            return FDL_LINE_ON_FAIL;
        }
        return "";
    }

    private ConditionAnalysis analyzeBranchCondition(String condition) {
        ConditionAnalysis analysis = analyzeCondition(condition);
        if (!nativeDecisionOutputExecutable) {
            return analysis;
        }
        List<DecisionOutputReference> references = analysis.getDecisionOutputReferences();
        if (references.size() != 1 || !analysis.getTaskStatusPredicates().isEmpty()) {
            return analysis;
        }
        DecisionOutputReference reference = references.get(0);
        if (!nativeDecisionOutputName.equalsIgnoreCase(reference.getDecisionName())) {
            return analysis;
        }
        if (!"=".equals(reference.getOperator()) && !"==".equals(reference.getOperator())) {
            return analysis;
        }
        if (!"true".equalsIgnoreCase(reference.getExpectedValue())) {
            return analysis;
        }
        CompareConditionEntity entity = new CompareConditionEntity();
        entity.setSource(new ConditionParamValue("true"));
        entity.setTarget(new ConditionParamValue("true"));
        entity.setValueType(ConditionValueType.BOOLEAN);
        entity.setConditionCompareType(ConditionCompareType.EQUAL);
        return ConditionAnalysis.executableDecisionOutput(entity, nativeDecisionOutputReason, reference);
    }

    private static DPConditionEntity createConditionEntity(String condition) {
        ConditionAnalysis analysis = analyzeCondition(condition);
        return analysis == null ? null : analysis.getConditionEntity();
    }

    private static ConditionAnalysis parseLogicalCondition(String condition) {
        ConditionAnalysis orAnalysis = parseLogicalCondition(condition, "OR", ConditionType.OR);
        if (orAnalysis != null) {
            return orAnalysis;
        }
        return parseLogicalCondition(condition, "AND", ConditionType.AND);
    }

    private static ConditionAnalysis parseLogicalCondition(String condition, String operator, ConditionType conditionType) {
        List<String> parts = splitTopLevel(condition, operator);
        if (parts.size() <= 1) {
            return null;
        }
        List<DPConditionEntity> entities = new ArrayList<DPConditionEntity>();
        List<String> limitations = new ArrayList<String>();
        List<TaskStatusPredicate> taskStatusPredicates = new ArrayList<TaskStatusPredicate>();
        List<DecisionOutputReference> decisionOutputReferences = new ArrayList<DecisionOutputReference>();
        List<String> requiredRuntimeBindings = new ArrayList<String>();
        boolean allExecutable = true;
        for (String part : parts) {
            ConditionAnalysis analysis = analyzeCondition(stripOuterParens(part));
            if (!STATUS_EXECUTABLE.equals(analysis.getStatus()) || analysis.getConditionEntity() == null) {
                allExecutable = false;
            }
            if (analysis.getConditionEntity() != null) {
                entities.add(analysis.getConditionEntity());
            }
            if (StringUtils.isNotEmpty(analysis.getLimitation())) {
                limitations.add(analysis.getLimitation());
            }
            taskStatusPredicates.addAll(analysis.getTaskStatusPredicates());
            decisionOutputReferences.addAll(analysis.getDecisionOutputReferences());
            requiredRuntimeBindings.addAll(analysis.getRequiredRuntimeBindings());
        }
        if (entities.size() != parts.size()) {
            return ConditionAnalysis.unsupported("compound " + operator + " condition contains unsupported operands"
                + (limitations.isEmpty() ? "" : ": " + joinUnique(limitations)),
                taskStatusPredicates, decisionOutputReferences, requiredRuntimeBindings);
        }
        AndOrConditionEntity entity = new AndOrConditionEntity();
        entity.setConditionType(conditionType);
        entity.setConditions(entities);
        if (allExecutable) {
            return ConditionAnalysis.executable(entity);
        }
        return ConditionAnalysis.reviewRequired(entity, limitations.isEmpty()
            ? "compound " + operator + " condition requires runtime variable binding verification"
            : joinUnique(limitations), taskStatusPredicates, decisionOutputReferences, requiredRuntimeBindings);
    }

    private static List<String> splitTopLevel(String condition, String operator) {
        List<String> result = new ArrayList<String>();
        String source = condition == null ? "" : condition;
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int last = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (inSingleQuote || inDoubleQuote) {
                continue;
            }
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')' && depth > 0) {
                depth--;
                continue;
            }
            if (depth == 0 && startsWithWord(source, i, operator)) {
                result.add(source.substring(last, i).trim());
                i += operator.length() - 1;
                last = i + 1;
            }
        }
        if (last == 0) {
            result.add(source.trim());
        } else {
            result.add(source.substring(last).trim());
        }
        return result;
    }

    private static boolean startsWithWord(String source, int index, String word) {
        int end = index + word.length();
        if (end > source.length() || !source.regionMatches(true, index, word, 0, word.length())) {
            return false;
        }
        boolean leftOk = index == 0 || !isWordChar(source.charAt(index - 1));
        boolean rightOk = end == source.length() || !isWordChar(source.charAt(end));
        return leftOk && rightOk;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.';
    }

    private static String stripOuterParens(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.startsWith("(") && trimmed.endsWith(")") && outerParensWrap(trimmed)) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static boolean outerParensWrap(String value) {
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (inSingleQuote || inDoubleQuote) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && i < value.length() - 1) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    private static ParsedCondition parseSimpleCondition(String condition) {
        Matcher matcher = Pattern.compile("^\\s*\\$?(.+?)\\s*(==|=|!=|<>|>=|<=|>|<)\\s*(.+?)\\s*$").matcher(condition);
        if (!matcher.matches()) {
            return null;
        }
        ParsedOperand left = parseOperand(matcher.group(1).trim(), true);
        String op = matcher.group(2).trim();
        ParsedOperand right = parseOperand(matcher.group(3).trim(), false);
        if (!StringUtils.isNotEmpty(left.value) || !StringUtils.isNotEmpty(right.value)) {
            return null;
        }
        ConditionCompareType compareType = mapCompareType(op);
        if (compareType == null) {
            return null;
        }
        ConditionValueType valueType = inferValueType(right.value);
        return new ParsedCondition(left, right, op, compareType, valueType);
    }

    private static ConditionCompareType mapCompareType(String op) {
        if ("=".equals(op) || "==".equals(op)) {
            return ConditionCompareType.EQUAL;
        }
        if ("!=".equals(op) || "<>".equals(op)) {
            return ConditionCompareType.NOT_EQUAL;
        }
        if (">".equals(op)) {
            return ConditionCompareType.GREATER;
        }
        if ("<".equals(op)) {
            return ConditionCompareType.LESS;
        }
        if (">=".equals(op)) {
            return ConditionCompareType.GREATER_EQUAL;
        }
        if ("<=".equals(op)) {
            return ConditionCompareType.LESS_EQUAL;
        }
        return null;
    }

    private static ConditionValueType inferValueType(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return ConditionValueType.BOOLEAN;
        }
        if (value.matches("-?\\d+(\\.\\d+)?")) {
            return ConditionValueType.NUMBER;
        }
        return ConditionValueType.STRING;
    }

    private static boolean isEqualityCompare(ConditionCompareType compareType) {
        return compareType == ConditionCompareType.EQUAL || compareType == ConditionCompareType.NOT_EQUAL;
    }

    private static String normalizeStatusLiteral(String value) {
        if (!StringUtils.isNotEmpty(value)) {
            return "";
        }
        String normalized = stripQuotes(value).trim().toLowerCase();
        if ("succeeded".equals(normalized) || "success".equals(normalized) || "successful".equals(normalized)) {
            return "SUCCEEDED";
        }
        if ("failed".equals(normalized) || "fail".equals(normalized) || "failure".equals(normalized) || "error".equals(normalized)) {
            return "FAILED";
        }
        if ("disabled".equals(normalized)) {
            return "DISABLED";
        }
        if ("running".equals(normalized) || "started".equals(normalized)) {
            return "RUNNING";
        }
        if ("aborted".equals(normalized) || "stopped".equals(normalized) || "terminated".equals(normalized)) {
            return "STOPPED";
        }
        if ("waiting".equals(normalized) || "scheduled".equals(normalized) || "queued".equals(normalized)) {
            return "WAITING";
        }
        if ("skipped".equals(normalized)) {
            return "SKIPPED";
        }
        return "";
    }

    private static String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static ParsedOperand parseOperand(String rawValue, boolean bareWordIsVariable) {
        String raw = rawValue == null ? "" : rawValue.trim();
        String stripped = stripQuotes(raw);
        boolean quoted = !raw.equals(stripped);
        boolean variable = false;
        boolean scoped = false;
        boolean taskRuntime = false;
        boolean infaReference = false;
        String referenceKind = "";
        String rawReference = "";
        String value = stripped;
        InfaScopedParameterResolver.ReferenceToken referenceToken = InfaScopedParameterResolver.classifyReference(raw);
        if (!quoted && referenceToken.isRecognized()) {
            variable = true;
            value = referenceToken.getConditionValueName();
            scoped = referenceToken.isScopedParameterReference();
            taskRuntime = referenceToken.isTaskStatusReference() || referenceToken.isDecisionOutputReference();
            infaReference = true;
            referenceKind = referenceToken.getKind();
            rawReference = referenceToken.getRaw();
        } else if (bareWordIsVariable && !quoted && raw.matches("[A-Za-z_\\u4e00-\\u9fa5][A-Za-z0-9_\\.\\u4e00-\\u9fa5]*")) {
            variable = true;
            if (raw.indexOf('.') >= 0) {
                scoped = true;
                String suffix = raw.substring(raw.lastIndexOf('.') + 1);
                taskRuntime = "Status".equalsIgnoreCase(suffix) || "Condition".equalsIgnoreCase(suffix);
            }
        }
        return new ParsedOperand(value, variable, quoted, scoped, taskRuntime, infaReference, referenceKind, rawReference);
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append("; ");
            }
            result.append(values.get(i));
        }
        return result.toString();
    }

    private static class ConditionBranch {
        private final String nextNodeId;
        private final String nextNodeName;
        private final String condition;
        private final ConditionAnalysis analysis;

        ConditionBranch(String nextNodeId, String nextNodeName, String condition, ConditionAnalysis analysis) {
            this.nextNodeId = nextNodeId;
            this.nextNodeName = StringUtils.isNotEmpty(nextNodeName) ? nextNodeName : nextNodeId;
            this.condition = StringUtils.isNotEmpty(condition) ? condition : "";
            this.analysis = analysis == null ? ConditionNodeBean.analyzeCondition(this.condition) : analysis;
        }
    }

    private static class ParsedCondition {
        private final ParsedOperand left;
        private final ParsedOperand right;
        private final String operator;
        private final ConditionCompareType compareType;
        private final ConditionValueType valueType;

        ParsedCondition(ParsedOperand left, ParsedOperand right, String operator, ConditionCompareType compareType, ConditionValueType valueType) {
            this.left = left;
            this.right = right;
            this.operator = operator == null ? "" : operator;
            this.compareType = compareType;
            this.valueType = valueType;
        }

        boolean isBooleanSelfCheck() {
            if (this.valueType != ConditionValueType.BOOLEAN) {
                return false;
            }
            if (this.compareType == ConditionCompareType.EQUAL) {
                return true;
            }
            if (this.compareType == ConditionCompareType.NOT_EQUAL) {
                return true;
            }
            return false;
        }

        boolean expectedTrue() {
            boolean rightIsTrue = "true".equalsIgnoreCase(this.right.value);
            if (this.compareType == ConditionCompareType.NOT_EQUAL) {
                return !rightIsTrue;
            }
            return rightIsTrue;
        }

        boolean safeForFdlCondition() {
            return left.safeForFdlCondition() && right.safeForFdlCondition();
        }

        ConditionAnalysis createRuntimeAnalysis(DPConditionEntity entity) {
            if (!left.taskRuntime && !right.taskRuntime) {
                return null;
            }
            List<TaskStatusPredicate> taskStatusPredicates = new ArrayList<TaskStatusPredicate>();
            List<DecisionOutputReference> decisionOutputReferences = new ArrayList<DecisionOutputReference>();
            List<String> requiredRuntimeBindings = new ArrayList<String>();
            List<String> limitations = new ArrayList<String>();
            boolean unsupported = false;
            if (left.taskRuntime) {
                if (left.isStatusRuntime() && !isEqualityCompare(compareType)) {
                    unsupported = true;
                    limitations.add("task status predicates support only =, ==, !=, <> comparisons; found " + compareType);
                }
                TaskStatusPredicate predicate = left.createTaskStatusPredicate(operator, right.value);
                if (predicate != null) {
                    taskStatusPredicates.add(predicate);
                    requiredRuntimeBindings.addAll(predicate.requiredBindings());
                    if (StringUtils.isNotEmpty(predicate.getLimitation())) {
                        limitations.add(predicate.getLimitation());
                    }
                }
                DecisionOutputReference reference = left.createDecisionOutputReference(operator, right.value);
                if (reference != null) {
                    decisionOutputReferences.add(reference);
                    requiredRuntimeBindings.addAll(reference.requiredBindings());
                    if (StringUtils.isNotEmpty(reference.getLimitation())) {
                        limitations.add(reference.getLimitation());
                    }
                }
            }
            if (right.taskRuntime) {
                if (right.isStatusRuntime() && !isEqualityCompare(compareType)) {
                    unsupported = true;
                    limitations.add("task status predicates support only =, ==, !=, <> comparisons; found " + compareType);
                }
                TaskStatusPredicate predicate = right.createTaskStatusPredicate(operator, left.value);
                if (predicate != null) {
                    taskStatusPredicates.add(predicate);
                    requiredRuntimeBindings.addAll(predicate.requiredBindings());
                    if (StringUtils.isNotEmpty(predicate.getLimitation())) {
                        limitations.add(predicate.getLimitation());
                    }
                }
                DecisionOutputReference reference = right.createDecisionOutputReference(operator, left.value);
                if (reference != null) {
                    decisionOutputReferences.add(reference);
                    requiredRuntimeBindings.addAll(reference.requiredBindings());
                    if (StringUtils.isNotEmpty(reference.getLimitation())) {
                        limitations.add(reference.getLimitation());
                    }
                }
            }
            String limitation = joinUnique(limitations);
            if (!StringUtils.isNotEmpty(limitation)) {
                limitation = "Informatica runtime variable binding requires an FDL runtime adapter";
            }
            if (unsupported) {
                return ConditionAnalysis.unsupported(limitation, taskStatusPredicates, decisionOutputReferences, requiredRuntimeBindings);
            }
            return ConditionAnalysis.reviewRequired(entity, limitation, taskStatusPredicates, decisionOutputReferences, requiredRuntimeBindings);
        }

        String reviewReason() {
            String leftReason = left.reviewReason();
            String rightReason = right.reviewReason();
            if (StringUtils.isNotEmpty(leftReason) && StringUtils.isNotEmpty(rightReason)) {
                return leftReason + "; " + rightReason;
            }
            return StringUtils.isNotEmpty(leftReason) ? leftReason : rightReason;
        }
    }

    private static class ParsedOperand {
        private final String value;
        private final boolean variable;
        private final boolean quoted;
        private final boolean scoped;
        private final boolean taskRuntime;
        private final boolean infaReference;
        private final String referenceKind;
        private final String rawReference;
        private final String runtimeOwner;
        private final String runtimeProperty;

        ParsedOperand(String value, boolean variable, boolean quoted, boolean scoped, boolean taskRuntime,
            boolean infaReference, String referenceKind, String rawReference) {
            this.value = value == null ? "" : value.trim();
            this.variable = variable;
            this.quoted = quoted;
            this.scoped = scoped;
            this.taskRuntime = taskRuntime;
            this.infaReference = infaReference;
            this.referenceKind = referenceKind == null ? "" : referenceKind;
            this.rawReference = rawReference == null ? "" : rawReference;
            String owner = "";
            String property = "";
            if (this.value.indexOf('.') >= 0) {
                owner = this.value.substring(0, this.value.lastIndexOf('.'));
                property = this.value.substring(this.value.lastIndexOf('.') + 1);
            }
            this.runtimeOwner = owner;
            this.runtimeProperty = property;
        }

        com.fr.dp.condition.entity.value.ConditionValue toConditionValue() {
            if (variable) {
                return new ConditionVariableValue(value);
            }
            return new ConditionParamValue(value);
        }

        boolean safeForFdlCondition() {
            if (!variable) {
                return true;
            }
            if (infaReference) {
                return false;
            }
            return !scoped && !taskRuntime;
        }

        String reviewReason() {
            if (taskRuntime) {
                return runtimeLimitation("", "");
            }
            if (infaReference) {
                if (InfaScopedParameterResolver.KIND_BRACED_PARAMETER.equals(referenceKind)) {
                    return "references Informatica parameter-file token " + rawReference
                        + "; requires parameter file binding or explicit runtime placeholder";
                }
                return "references Informatica scoped/runtime token " + rawReference + " [" + referenceKind
                    + "]; requires scoped resolver binding before executable FDL condition generation";
            }
            if (scoped) {
                return "references scoped Informatica variable " + value + "; verify mapping/workflow parameter binding";
            }
            return "";
        }

        boolean isStatusRuntime() {
            return taskRuntime && "Status".equalsIgnoreCase(runtimeProperty);
        }

        boolean isDecisionOutputRuntime() {
            return taskRuntime && "Condition".equalsIgnoreCase(runtimeProperty);
        }

        String runtimeLimitation(String operator, String expectedValue) {
            if (isStatusRuntime()) {
                return "references Informatica task status variable " + value
                    + "; FDL condition entity has no proven upstream task-status binding";
            }
            if (isDecisionOutputRuntime()) {
                return "references Informatica Decision output variable " + value
                    + "; FDL condition entity has no proven Decision output binding";
            }
            if (taskRuntime) {
                return "references Informatica runtime variable " + value + "; verify FDL runtime variable binding";
            }
            return "";
        }

        TaskStatusPredicate createTaskStatusPredicate(String operator, String expectedValue) {
            if (!isStatusRuntime()) {
                return null;
            }
            return new TaskStatusPredicate(runtimeOwner, operator, expectedValue);
        }

        DecisionOutputReference createDecisionOutputReference(String operator, String expectedValue) {
            if (!isDecisionOutputRuntime()) {
                return null;
            }
            return new DecisionOutputReference(runtimeOwner, operator, expectedValue);
        }
    }

    public static class TaskStatusPredicate {
        private final String taskName;
        private final String operator;
        private final String statusLiteral;
        private final String normalizedStatusLiteral;

        TaskStatusPredicate(String taskName, String operator, String statusLiteral) {
            this.taskName = taskName == null ? "" : taskName.trim();
            this.operator = operator == null ? "" : operator.trim();
            this.statusLiteral = statusLiteral == null ? "" : stripQuotes(statusLiteral.trim());
            this.normalizedStatusLiteral = normalizeStatusLiteral(this.statusLiteral);
        }

        public String getTaskName() {
            return taskName;
        }

        public String getOperator() {
            return operator;
        }

        public String getStatusLiteral() {
            return statusLiteral;
        }

        public String getNormalizedStatusLiteral() {
            return normalizedStatusLiteral;
        }

        public String getLimitation() {
            StringBuilder limitation = new StringBuilder();
            limitation.append("task status predicate requires adapter binding for upstream task runtime status");
            if (!StringUtils.isNotEmpty(normalizedStatusLiteral) && StringUtils.isNotEmpty(statusLiteral)) {
                limitation.append("; status literal ").append(statusLiteral).append(" is not a known generic Informatica status");
            }
            return limitation.toString();
        }

        List<String> requiredBindings() {
            List<String> result = new ArrayList<String>();
            result.add("runtime status source for task " + taskName);
            result.add("Informatica status literal to FDL runtime status mapping");
            result.add("status aggregation timing for task " + taskName);
            return result;
        }

        String toVisibleLine() {
            StringBuilder result = new StringBuilder();
            result.append(taskName).append(".Status ").append(operator).append(" ").append(statusLiteral);
            result.append(" normalized=");
            result.append(StringUtils.isNotEmpty(normalizedStatusLiteral) ? normalizedStatusLiteral : "UNKNOWN_STATUS_LITERAL");
            result.append(" executable=false");
            return result.toString();
        }
    }

    public static class DecisionOutputReference {
        private final String decisionName;
        private final String operator;
        private final String expectedValue;

        DecisionOutputReference(String decisionName, String operator, String expectedValue) {
            this.decisionName = decisionName == null ? "" : decisionName.trim();
            this.operator = operator == null ? "" : operator.trim();
            this.expectedValue = expectedValue == null ? "" : stripQuotes(expectedValue.trim());
        }

        public String getDecisionName() {
            return decisionName;
        }

        public String getOperator() {
            return operator;
        }

        public String getExpectedValue() {
            return expectedValue;
        }

        public String getLimitation() {
            StringBuilder limitation = new StringBuilder();
            limitation.append("Decision output binding requires adapter storage for Decision expression result");
            if (!"true".equalsIgnoreCase(expectedValue) && !"false".equalsIgnoreCase(expectedValue)) {
                limitation.append("; expected value ").append(expectedValue).append(" is not a boolean literal");
            }
            return limitation.toString();
        }

        List<String> requiredBindings() {
            List<String> result = new ArrayList<String>();
            result.add("runtime boolean output for Decision " + decisionName);
            result.add("Decision expression result storage for " + decisionName);
            result.add("outgoing branch condition binding for Decision " + decisionName);
            return result;
        }

        String toVisibleLine() {
            StringBuilder result = new StringBuilder();
            result.append(decisionName).append(".Condition ").append(operator).append(" ").append(expectedValue);
            result.append(" executable=false");
            return result.toString();
        }
    }

    public static class ConditionAnalysis {
        private final String status;
        private final String classification;
        private final String limitation;
        private final DPConditionEntity conditionEntity;
        private final List<TaskStatusPredicate> taskStatusPredicates;
        private final List<DecisionOutputReference> decisionOutputReferences;
        private final List<String> requiredRuntimeBindings;

        private ConditionAnalysis(String status, String limitation, DPConditionEntity conditionEntity) {
            this(status, status, limitation, conditionEntity,
                new ArrayList<TaskStatusPredicate>(),
                new ArrayList<DecisionOutputReference>(),
                new ArrayList<String>());
        }

        private ConditionAnalysis(String status, String classification, String limitation, DPConditionEntity conditionEntity,
            List<TaskStatusPredicate> taskStatusPredicates,
            List<DecisionOutputReference> decisionOutputReferences,
            List<String> requiredRuntimeBindings) {
            this.status = status;
            this.classification = classification == null ? status : classification;
            this.limitation = limitation == null ? "" : limitation;
            this.conditionEntity = conditionEntity;
            this.taskStatusPredicates = taskStatusPredicates == null
                ? new ArrayList<TaskStatusPredicate>() : new ArrayList<TaskStatusPredicate>(taskStatusPredicates);
            this.decisionOutputReferences = decisionOutputReferences == null
                ? new ArrayList<DecisionOutputReference>() : new ArrayList<DecisionOutputReference>(decisionOutputReferences);
            this.requiredRuntimeBindings = unique(requiredRuntimeBindings);
        }

        static ConditionAnalysis executable(DPConditionEntity conditionEntity) {
            return new ConditionAnalysis(STATUS_EXECUTABLE, "", conditionEntity);
        }

        static ConditionAnalysis executableDecisionOutput(DPConditionEntity conditionEntity, String reason,
            DecisionOutputReference decisionOutputReference) {
            List<DecisionOutputReference> decisionOutputReferences = new ArrayList<DecisionOutputReference>();
            if (decisionOutputReference != null) {
                decisionOutputReferences.add(decisionOutputReference);
            }
            return new ConditionAnalysis(STATUS_EXECUTABLE,
                CLASSIFICATION_EXECUTABLE_DECISION_OUTPUT_BINDING,
                reason, conditionEntity,
                new ArrayList<TaskStatusPredicate>(),
                decisionOutputReferences,
                new ArrayList<String>());
        }

        static ConditionAnalysis reviewRequired(String limitation) {
            return new ConditionAnalysis(STATUS_REVIEW_REQUIRED, limitation, null);
        }

        static ConditionAnalysis reviewRequired(DPConditionEntity conditionEntity, String limitation) {
            return new ConditionAnalysis(STATUS_REVIEW_REQUIRED, limitation, conditionEntity);
        }

        static ConditionAnalysis reviewRequired(DPConditionEntity conditionEntity, String limitation,
            List<TaskStatusPredicate> taskStatusPredicates,
            List<DecisionOutputReference> decisionOutputReferences,
            List<String> requiredRuntimeBindings) {
            return new ConditionAnalysis(STATUS_REVIEW_REQUIRED,
                classify(STATUS_REVIEW_REQUIRED, taskStatusPredicates, decisionOutputReferences),
                limitation, conditionEntity, taskStatusPredicates, decisionOutputReferences, requiredRuntimeBindings);
        }

        static ConditionAnalysis runtimeReviewRequired(DPConditionEntity conditionEntity, String limitation,
            TaskStatusPredicate taskStatusPredicate, DecisionOutputReference decisionOutputReference) {
            List<TaskStatusPredicate> taskStatusPredicates = new ArrayList<TaskStatusPredicate>();
            List<DecisionOutputReference> decisionOutputReferences = new ArrayList<DecisionOutputReference>();
            List<String> requiredRuntimeBindings = new ArrayList<String>();
            if (taskStatusPredicate != null) {
                taskStatusPredicates.add(taskStatusPredicate);
                requiredRuntimeBindings.addAll(taskStatusPredicate.requiredBindings());
            }
            if (decisionOutputReference != null) {
                decisionOutputReferences.add(decisionOutputReference);
                requiredRuntimeBindings.addAll(decisionOutputReference.requiredBindings());
            }
            return reviewRequired(conditionEntity, limitation, taskStatusPredicates, decisionOutputReferences, requiredRuntimeBindings);
        }

        static ConditionAnalysis visibleOnly(String limitation) {
            return new ConditionAnalysis(STATUS_VISIBLE_ONLY, limitation, null);
        }

        static ConditionAnalysis unsupported(String limitation) {
            return new ConditionAnalysis(STATUS_UNSUPPORTED, limitation, null);
        }

        static ConditionAnalysis unsupported(String limitation,
            List<TaskStatusPredicate> taskStatusPredicates,
            List<DecisionOutputReference> decisionOutputReferences,
            List<String> requiredRuntimeBindings) {
            return new ConditionAnalysis(STATUS_UNSUPPORTED, STATUS_UNSUPPORTED, limitation, null,
                taskStatusPredicates, decisionOutputReferences, requiredRuntimeBindings);
        }

        private static String classify(String status, List<TaskStatusPredicate> taskStatusPredicates,
            List<DecisionOutputReference> decisionOutputReferences) {
            boolean hasTaskStatus = taskStatusPredicates != null && !taskStatusPredicates.isEmpty();
            boolean hasDecisionOutput = decisionOutputReferences != null && !decisionOutputReferences.isEmpty();
            if (STATUS_UNSUPPORTED.equals(status) || STATUS_VISIBLE_ONLY.equals(status)) {
                return status;
            }
            if (hasTaskStatus && hasDecisionOutput) {
                return CLASSIFICATION_REVIEW_REQUIRED_RUNTIME_SEMANTICS;
            }
            if (hasTaskStatus) {
                return STATUS_EXECUTABLE.equals(status)
                    ? CLASSIFICATION_EXECUTABLE_TASK_STATUS_CONDITION
                    : CLASSIFICATION_REVIEW_REQUIRED_TASK_STATUS_CONDITION;
            }
            if (hasDecisionOutput) {
                return STATUS_EXECUTABLE.equals(status)
                    ? CLASSIFICATION_EXECUTABLE_DECISION_OUTPUT_BINDING
                    : CLASSIFICATION_REVIEW_REQUIRED_DECISION_OUTPUT_BINDING;
            }
            return status;
        }

        private static List<String> unique(List<String> values) {
            List<String> result = new ArrayList<String>();
            if (values == null) {
                return result;
            }
            Set<String> seen = new LinkedHashSet<String>();
            for (String value : values) {
                if (StringUtils.isNotEmpty(value) && seen.add(value)) {
                    result.add(value);
                }
            }
            return result;
        }

        public String getStatus() {
            return status;
        }

        public String getClassification() {
            return classification;
        }

        public String getLimitation() {
            return limitation;
        }

        public DPConditionEntity getConditionEntity() {
            return conditionEntity;
        }

        public List<TaskStatusPredicate> getTaskStatusPredicates() {
            return new ArrayList<TaskStatusPredicate>(taskStatusPredicates);
        }

        public List<DecisionOutputReference> getDecisionOutputReferences() {
            return new ArrayList<DecisionOutputReference>(decisionOutputReferences);
        }

        public List<String> getRequiredRuntimeBindings() {
            return new ArrayList<String>(requiredRuntimeBindings);
        }

        public String getRuntimeDetails() {
            List<String> details = new ArrayList<String>();
            for (TaskStatusPredicate predicate : taskStatusPredicates) {
                details.add(predicate.toVisibleLine());
            }
            for (DecisionOutputReference reference : decisionOutputReferences) {
                details.add(reference.toVisibleLine());
            }
            return join(details);
        }

        public String getRequiredRuntimeBindingsText() {
            return join(requiredRuntimeBindings);
        }
    }

    private static String joinUnique(List<String> values) {
        return join(ConditionAnalysis.unique(values));
    }
}
