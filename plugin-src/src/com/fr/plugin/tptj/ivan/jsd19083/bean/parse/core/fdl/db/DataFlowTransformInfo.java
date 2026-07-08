package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db;

import java.util.ArrayList;
import java.util.List;

public class DataFlowTransformInfo {
    public static class LookupReadInfo {
        private String nodeName;
        private String datasourceType;
        private String connectionName;
        private String sql;
        private String note;

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = safe(nodeName);
        }

        public String getDatasourceType() {
            return datasourceType;
        }

        public void setDatasourceType(String datasourceType) {
            this.datasourceType = safe(datasourceType);
        }

        public String getConnectionName() {
            return connectionName;
        }

        public void setConnectionName(String connectionName) {
            this.connectionName = safe(connectionName);
        }

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = safe(sql);
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = safe(note);
        }
    }

    private String sparkSql;
    private String note;
    private String transformNodeName = "infa_lookup_filter";
    private boolean apiPostProjection;
    private boolean rowApiUdfAdapter;
    private String apiMergeRelation;
    private boolean singleRowLookupMode;
    private String singleRowLookupPolicy;
    private String singleRowLookupRowSelector;
    private String singleRowLookupOrderStatus;
    private String singleRowLookupSqlDraft;
    private String singleRowLookupSourcePlan;
    private String singleRowLookupPredicatePlan;
    private String singleRowLookupPlanLimitation;
    private String singleRowLookupEquivalenceProof;
    private boolean singleRowLookupExecutable;
    private List<String> outputFields = new ArrayList<String>();
    private List<LookupReadInfo> lookupReads = new ArrayList<LookupReadInfo>();

    public String getSparkSql() {
        return sparkSql;
    }

    public void setSparkSql(String sparkSql) {
        this.sparkSql = safe(sparkSql);
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = safe(note);
    }

    public String getTransformNodeName() {
        return transformNodeName;
    }

    public void setTransformNodeName(String transformNodeName) {
        this.transformNodeName = safe(transformNodeName);
    }

    public boolean isApiPostProjection() {
        return apiPostProjection;
    }

    public void setApiPostProjection(boolean apiPostProjection) {
        this.apiPostProjection = apiPostProjection;
    }

    public boolean isRowApiUdfAdapter() {
        return rowApiUdfAdapter;
    }

    public void setRowApiUdfAdapter(boolean rowApiUdfAdapter) {
        this.rowApiUdfAdapter = rowApiUdfAdapter;
    }

    public String getApiMergeRelation() {
        return apiMergeRelation;
    }

    public void setApiMergeRelation(String apiMergeRelation) {
        this.apiMergeRelation = safe(apiMergeRelation);
    }

    public boolean isSingleRowLookupMode() {
        return singleRowLookupMode;
    }

    public void setSingleRowLookupMode(boolean singleRowLookupMode) {
        this.singleRowLookupMode = singleRowLookupMode;
    }

    public String getSingleRowLookupPolicy() {
        return singleRowLookupPolicy;
    }

    public void setSingleRowLookupPolicy(String singleRowLookupPolicy) {
        this.singleRowLookupPolicy = safe(singleRowLookupPolicy);
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

    public String getSingleRowLookupSqlDraft() {
        return singleRowLookupSqlDraft;
    }

    public void setSingleRowLookupSqlDraft(String singleRowLookupSqlDraft) {
        this.singleRowLookupSqlDraft = safe(singleRowLookupSqlDraft);
    }

    public boolean isSingleRowLookupExecutable() {
        return singleRowLookupExecutable && singleRowLookupEquivalenceProof != null && singleRowLookupEquivalenceProof.trim().length() > 0;
    }

    public void setSingleRowLookupExecutable(boolean singleRowLookupExecutable) {
        this.singleRowLookupExecutable = singleRowLookupExecutable;
        if (!singleRowLookupExecutable) {
            this.singleRowLookupEquivalenceProof = "";
        }
    }

    public String getSingleRowLookupSourcePlan() {
        return singleRowLookupSourcePlan;
    }

    public void setSingleRowLookupSourcePlan(String singleRowLookupSourcePlan) {
        this.singleRowLookupSourcePlan = safe(singleRowLookupSourcePlan);
    }

    public String getSingleRowLookupPredicatePlan() {
        return singleRowLookupPredicatePlan;
    }

    public void setSingleRowLookupPredicatePlan(String singleRowLookupPredicatePlan) {
        this.singleRowLookupPredicatePlan = safe(singleRowLookupPredicatePlan);
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

    public List<String> getOutputFields() {
        return new ArrayList<String>(outputFields);
    }

    public void addOutputField(String outputField) {
        addUnique(outputFields, outputField);
    }

    public List<LookupReadInfo> getLookupReads() {
        return new ArrayList<LookupReadInfo>(lookupReads);
    }

    public void addLookupRead(LookupReadInfo lookupRead) {
        if (lookupRead != null) {
            lookupReads.add(lookupRead);
        }
    }

    public boolean isExecutable() {
        if (singleRowLookupMode && !isSingleRowLookupExecutable()) {
            return false;
        }
        return sparkSql != null && sparkSql.trim().length() > 0;
    }

    public boolean isPlanOnly() {
        return singleRowLookupMode && !isSingleRowLookupExecutable()
            && sparkSql != null && sparkSql.trim().length() > 0;
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

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
