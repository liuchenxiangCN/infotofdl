package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LookupFieldInfo {
    private String mappingName;
    private String transformationName;
    private String lookupCondition;
    private String lookupSqlOverride;
    private String lookupTableName;
    private String lookupSourceFilter;
    private String connectionInformation;
    private String sourceType;
    private String cachingEnabled;
    private String multipleMatchPolicy;
    private String limitation;
    private String sqlJoinCondition;
    private boolean sqlJoinSupported;
    private boolean sqlJoinApplied;
    private boolean fdlDataFlowTransformSupported;
    private boolean fdlDataFlowTransformApplied;
    private boolean fdlDataFlowReviewRequired;
    private boolean fdlDataFlowSingleRowPlanReady;
    private boolean fdlDataFlowSingleRowExecutable;
    private String singleRowLookupRowId = "";
    private String singleRowLookupRowSelector = "";
    private String singleRowLookupOrderStatus = "";
    private String singleRowLookupSparkSqlDraft = "";
    private String singleRowLookupDownstreamFilter = "";
    private String singleRowLookupSourcePlan = "";
    private String singleRowLookupPredicatePlan = "";
    private String singleRowLookupPlanLimitation = "";
    private String singleRowLookupEquivalenceProof = "";
    private List<String> singleRowLookupPredicates = new ArrayList<String>();
    private List<String> unresolvedInputSemantics = new ArrayList<String>();
    private List<String> inputFields = new ArrayList<String>();
    private List<String> outputFields = new ArrayList<String>();
    private List<String> inputMappings = new ArrayList<String>();
    private Map<String, String> inputFieldReadAliases = new LinkedHashMap<String, String>();
    private Map<String, List<String>> outputToTargetFields = new LinkedHashMap<String, List<String>>();

    public String getMappingName() {
        return mappingName;
    }

    public void setMappingName(String mappingName) {
        this.mappingName = safe(mappingName);
    }

    public String getTransformationName() {
        return transformationName;
    }

    public void setTransformationName(String transformationName) {
        this.transformationName = safe(transformationName);
    }

    public String getLookupCondition() {
        return lookupCondition;
    }

    public void setLookupCondition(String lookupCondition) {
        this.lookupCondition = safe(lookupCondition);
    }

    public String getLookupSqlOverride() {
        return lookupSqlOverride;
    }

    public void setLookupSqlOverride(String lookupSqlOverride) {
        this.lookupSqlOverride = safe(lookupSqlOverride);
    }

    public String getLookupTableName() {
        return lookupTableName;
    }

    public void setLookupTableName(String lookupTableName) {
        this.lookupTableName = safe(lookupTableName);
    }

    public String getLookupSourceFilter() {
        return lookupSourceFilter;
    }

    public void setLookupSourceFilter(String lookupSourceFilter) {
        this.lookupSourceFilter = safe(lookupSourceFilter);
    }

    public String getConnectionInformation() {
        return connectionInformation;
    }

    public void setConnectionInformation(String connectionInformation) {
        this.connectionInformation = safe(connectionInformation);
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = safe(sourceType);
    }

    public String getCachingEnabled() {
        return cachingEnabled;
    }

    public void setCachingEnabled(String cachingEnabled) {
        this.cachingEnabled = safe(cachingEnabled);
    }

    public String getMultipleMatchPolicy() {
        return multipleMatchPolicy;
    }

    public void setMultipleMatchPolicy(String multipleMatchPolicy) {
        this.multipleMatchPolicy = safe(multipleMatchPolicy);
    }

    public String getLimitation() {
        return limitation;
    }

    public void setLimitation(String limitation) {
        this.limitation = safe(limitation);
    }

    public void addLimitation(String extra) {
        extra = safe(extra);
        if (extra.length() == 0 || limitationContains(extra)) {
            return;
        }
        if (limitation == null || limitation.length() == 0) {
            limitation = extra;
        } else {
            limitation += "; " + extra;
        }
    }

    public String getSqlJoinCondition() {
        return sqlJoinCondition;
    }

    public void setSqlJoinCondition(String sqlJoinCondition) {
        this.sqlJoinCondition = safe(sqlJoinCondition);
    }

    public boolean isSqlJoinSupported() {
        return sqlJoinSupported;
    }

    public void setSqlJoinSupported(boolean sqlJoinSupported) {
        this.sqlJoinSupported = sqlJoinSupported;
    }

    public boolean isSqlJoinApplied() {
        return sqlJoinApplied;
    }

    public void setSqlJoinApplied(boolean sqlJoinApplied) {
        this.sqlJoinApplied = sqlJoinApplied;
    }

    public boolean isFdlDataFlowTransformSupported() {
        return fdlDataFlowTransformSupported;
    }

    public void setFdlDataFlowTransformSupported(boolean fdlDataFlowTransformSupported) {
        this.fdlDataFlowTransformSupported = fdlDataFlowTransformSupported;
    }

    public boolean isFdlDataFlowTransformApplied() {
        return fdlDataFlowTransformApplied;
    }

    public void setFdlDataFlowTransformApplied(boolean fdlDataFlowTransformApplied) {
        this.fdlDataFlowTransformApplied = fdlDataFlowTransformApplied;
    }

    public boolean isFdlDataFlowReviewRequired() {
        return fdlDataFlowReviewRequired;
    }

    public void setFdlDataFlowReviewRequired(boolean fdlDataFlowReviewRequired) {
        this.fdlDataFlowReviewRequired = fdlDataFlowReviewRequired;
    }

    public boolean isFdlDataFlowSingleRowPlanReady() {
        return fdlDataFlowSingleRowPlanReady;
    }

    public void setFdlDataFlowSingleRowPlanReady(boolean fdlDataFlowSingleRowPlanReady) {
        this.fdlDataFlowSingleRowPlanReady = fdlDataFlowSingleRowPlanReady;
    }

    public boolean isFdlDataFlowSingleRowExecutable() {
        return fdlDataFlowSingleRowExecutable && singleRowLookupEquivalenceProof.length() > 0;
    }

    public void setFdlDataFlowSingleRowExecutable(boolean fdlDataFlowSingleRowExecutable) {
        this.fdlDataFlowSingleRowExecutable = fdlDataFlowSingleRowExecutable;
        if (!fdlDataFlowSingleRowExecutable) {
            this.singleRowLookupEquivalenceProof = "";
        }
    }

    public String getSingleRowLookupRowId() {
        return singleRowLookupRowId;
    }

    public void setSingleRowLookupRowId(String singleRowLookupRowId) {
        this.singleRowLookupRowId = safe(singleRowLookupRowId);
    }

    public String getSingleRowLookupRowSelector() {
        return singleRowLookupRowSelector;
    }

    public void setSingleRowLookupRowSelector(String singleRowLookupRowSelector) {
        this.singleRowLookupRowSelector = safe(singleRowLookupRowSelector);
    }

    public String getSingleRowLookupOrderStatus() {
        return singleRowLookupOrderStatus;
    }

    public void setSingleRowLookupOrderStatus(String singleRowLookupOrderStatus) {
        this.singleRowLookupOrderStatus = safe(singleRowLookupOrderStatus);
    }

    public String getSingleRowLookupSparkSqlDraft() {
        return singleRowLookupSparkSqlDraft;
    }

    public void setSingleRowLookupSparkSqlDraft(String singleRowLookupSparkSqlDraft) {
        this.singleRowLookupSparkSqlDraft = safe(singleRowLookupSparkSqlDraft);
    }

    public String getSingleRowLookupDownstreamFilter() {
        return singleRowLookupDownstreamFilter;
    }

    public void setSingleRowLookupDownstreamFilter(String singleRowLookupDownstreamFilter) {
        this.singleRowLookupDownstreamFilter = safe(singleRowLookupDownstreamFilter);
    }

    public String getSingleRowLookupSourcePlan() {
        return singleRowLookupSourcePlan;
    }

    public void setSingleRowLookupSourcePlan(String singleRowLookupSourcePlan) {
        this.singleRowLookupSourcePlan = safe(singleRowLookupSourcePlan);
    }

    public String getSingleRowLookupPredicatePlan() {
        if (singleRowLookupPredicatePlan.length() > 0) {
            return singleRowLookupPredicatePlan;
        }
        return join(singleRowLookupPredicates);
    }

    public void setSingleRowLookupPredicatePlan(String singleRowLookupPredicatePlan) {
        this.singleRowLookupPredicatePlan = safe(singleRowLookupPredicatePlan);
    }

    public List<String> getSingleRowLookupPredicates() {
        return new ArrayList<String>(singleRowLookupPredicates);
    }

    public void addSingleRowLookupPredicate(String predicate) {
        addUnique(singleRowLookupPredicates, predicate);
    }

    public String getSingleRowLookupPlanLimitation() {
        return singleRowLookupPlanLimitation;
    }

    public void setSingleRowLookupPlanLimitation(String singleRowLookupPlanLimitation) {
        this.singleRowLookupPlanLimitation = safe(singleRowLookupPlanLimitation);
    }

    public String getSingleRowLookupEquivalenceProof() {
        return singleRowLookupEquivalenceProof;
    }

    public void setSingleRowLookupEquivalenceProof(String singleRowLookupEquivalenceProof) {
        this.singleRowLookupEquivalenceProof = safe(singleRowLookupEquivalenceProof);
    }

    public List<String> getUnresolvedInputSemantics() {
        return new ArrayList<String>(unresolvedInputSemantics);
    }

    public void addUnresolvedInputSemantic(String unresolvedInputSemantic) {
        addUnique(unresolvedInputSemantics, unresolvedInputSemantic);
    }

    public List<String> getInputFields() {
        return new ArrayList<String>(inputFields);
    }

    public void addInputField(String inputField) {
        addUnique(inputFields, inputField);
    }

    public List<String> getOutputFields() {
        return new ArrayList<String>(outputFields);
    }

    public void addOutputField(String outputField) {
        addUnique(outputFields, outputField);
    }

    public List<String> getInputMappings() {
        return new ArrayList<String>(inputMappings);
    }

    public void addInputMapping(String inputField, String source, String readAlias) {
        inputField = safe(inputField);
        source = safe(source);
        readAlias = safe(readAlias);
        if (inputField.length() == 0) {
            return;
        }
        if (source.length() > 0) {
            addUnique(inputMappings, inputField + " <= " + source);
        }
        if (readAlias.length() > 0) {
            inputFieldReadAliases.put(normalize(inputField), readAlias);
        }
    }

    public String getInputReadAlias(String inputField) {
        String value = inputFieldReadAliases.get(normalize(inputField));
        return value == null ? "" : value;
    }

    public Map<String, String> getInputReadAliases() {
        return new LinkedHashMap<String, String>(inputFieldReadAliases);
    }

    public void addOutputTargetField(String outputField, String targetField) {
        outputField = safe(outputField);
        targetField = safe(targetField);
        if (outputField.length() == 0 || targetField.length() == 0) {
            return;
        }
        String key = normalize(outputField);
        List<String> targets = outputToTargetFields.get(key);
        if (targets == null) {
            targets = new ArrayList<String>();
            outputToTargetFields.put(key, targets);
        }
        addUnique(targets, targetField);
    }

    public List<String> getTargetFieldsForOutput(String outputField) {
        List<String> targets = outputToTargetFields.get(normalize(outputField));
        if (targets == null) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(targets);
    }

    public Map<String, List<String>> getOutputToTargetFields() {
        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> entry : outputToTargetFields.entrySet()) {
            result.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
        }
        return result;
    }

    public List<String> getAllTargetFields() {
        List<String> result = new ArrayList<String>();
        for (List<String> targets : outputToTargetFields.values()) {
            for (String target : targets) {
                addUnique(result, target);
            }
        }
        return result;
    }

    public String dedupeKey() {
        return safe(mappingName) + "\u0001" + safe(transformationName);
    }

    public String toVisibleLine() {
        StringBuilder line = new StringBuilder();
        line.append(transformationName);
        if (connectionInformation.length() > 0) {
            line.append("; connection=").append(connectionInformation);
        }
        if (lookupTableName.length() > 0) {
            line.append("; table=").append(lookupTableName);
        }
        if (lookupCondition.length() > 0) {
            line.append("; condition=").append(lookupCondition);
        }
        if (!inputMappings.isEmpty()) {
            line.append("; inputs=").append(join(inputMappings));
        } else if (!inputFields.isEmpty()) {
            line.append("; inputs=").append(join(inputFields));
        }
        if (!outputFields.isEmpty()) {
            line.append("; outputs=").append(join(outputFields));
        }
        List<String> targets = getAllTargetFields();
        if (!targets.isEmpty()) {
            line.append("; targets=").append(join(targets));
        }
        if (lookupSqlOverride.length() > 0) {
            line.append("; lookupSqlOverride=").append(abbreviate(lookupSqlOverride, 360));
        }
        if (lookupSourceFilter.length() > 0) {
            line.append("; lookupSourceFilter=").append(lookupSourceFilter);
        }
        if (fdlDataFlowTransformApplied) {
            line.append("; FDL dataflow lookup applied=").append(sqlJoinCondition);
        } else if (fdlDataFlowSingleRowPlanReady) {
            line.append("; FDL dataflow single-row lookup plan ready=").append(sqlJoinCondition);
            line.append("; sourcePlan=").append(singleRowLookupSourcePlan.length() > 0 ? singleRowLookupSourcePlan : defaultLookupSourcePlan());
            String predicatePlan = getSingleRowLookupPredicatePlan();
            if (predicatePlan.length() > 0) {
                line.append("; predicates=").append(predicatePlan);
            }
            line.append("; cache=").append(cachingEnabled.length() > 0 ? cachingEnabled : "-");
            line.append("; multipleMatchPolicy=").append(multipleMatchPolicy.length() > 0 ? multipleMatchPolicy : "-");
            line.append("; executable=").append(isFdlDataFlowSingleRowExecutable());
            line.append("; adapterOrOrderingRequired=").append(!isFdlDataFlowSingleRowExecutable());
            if (singleRowLookupEquivalenceProof.length() > 0) {
                line.append("; equivalenceProof=").append(singleRowLookupEquivalenceProof);
            }
            if (singleRowLookupPlanLimitation.length() > 0) {
                line.append("; planLimitation=").append(singleRowLookupPlanLimitation);
            }
            if (singleRowLookupRowId.length() > 0) {
                line.append("; rowId=").append(singleRowLookupRowId);
            }
            if (singleRowLookupRowSelector.length() > 0) {
                line.append("; rowSelector=").append(singleRowLookupRowSelector);
            }
            if (singleRowLookupDownstreamFilter.length() > 0) {
                line.append("; downstreamFilterAfterRn1=").append(singleRowLookupDownstreamFilter);
            }
            if (singleRowLookupOrderStatus.length() > 0) {
                line.append("; orderingStatus=").append(singleRowLookupOrderStatus);
            }
            if (singleRowLookupSparkSqlDraft.length() > 0) {
                line.append("; SparkSqlNodeDraft=").append(abbreviate(singleRowLookupSparkSqlDraft, 520));
            }
            if (!unresolvedInputSemantics.isEmpty()) {
                line.append("; unresolvedInputSemantics=").append(abbreviate(join(unresolvedInputSemantics), 520));
            }
        } else if (fdlDataFlowReviewRequired) {
            line.append("; FDL dataflow lookup candidate with review=").append(sqlJoinCondition);
        } else if (fdlDataFlowTransformSupported) {
            line.append("; FDL dataflow lookup candidate=").append(sqlJoinCondition);
        } else if (sqlJoinApplied) {
            line.append("; SQL join applied=").append(sqlJoinCondition);
        } else if (sqlJoinSupported) {
            line.append("; SQL join candidate=").append(sqlJoinCondition);
        }
        if (limitation != null && limitation.length() > 0) {
            line.append("; limitation=").append(limitation);
        }
        return line.toString();
    }

    private String defaultLookupSourcePlan() {
        if (lookupSqlOverride.length() > 0) {
            return "lookup_sql_override";
        }
        if (lookupTableName.length() > 0) {
            return "lookup_table";
        }
        return "missing_lookup_source";
    }

    private boolean limitationContains(String value) {
        return limitation != null && limitation.toLowerCase().contains(value.toLowerCase());
    }

    private static void addUnique(List<String> values, String value) {
        value = safe(value);
        if (value.length() == 0) {
            return;
        }
        for (String existing : values) {
            if (existing.equalsIgnoreCase(value)) {
                return;
            }
        }
        values.add(value);
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append(",");
            }
            result.append(values.get(i));
        }
        return result.toString();
    }

    private static String abbreviate(String value, int max) {
        value = safe(value).replaceAll("\\s+", " ");
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    private static String normalize(String value) {
        return safe(value).replace("\"", "").replace("`", "").replace("[", "").replace("]", "").replaceAll("\\s+", "").toUpperCase();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
