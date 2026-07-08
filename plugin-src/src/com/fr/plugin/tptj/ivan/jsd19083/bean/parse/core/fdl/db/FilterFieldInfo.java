package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db;

import java.util.ArrayList;
import java.util.List;

public class FilterFieldInfo {
    private String mappingName;
    private String transformationName;
    private String condition;
    private String sqlCondition;
    private String limitation;
    private boolean sqlConvertible;
    private boolean sqlPushdownSupported;
    private boolean sqlPushdownApplied;
    private boolean fdlDataFlowTransformSupported;
    private boolean fdlDataFlowTransformApplied;
    private List<String> dependencies = new ArrayList<String>();
    private List<String> dependencySources = new ArrayList<String>();
    private List<String> outputFields = new ArrayList<String>();
    private List<String> targetFields = new ArrayList<String>();

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

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = safe(condition);
    }

    public String getSqlCondition() {
        return sqlCondition;
    }

    public void setSqlCondition(String sqlCondition) {
        this.sqlCondition = safe(sqlCondition);
    }

    public String getLimitation() {
        return limitation;
    }

    public void setLimitation(String limitation) {
        this.limitation = safe(limitation);
    }

    public boolean isSqlConvertible() {
        return sqlConvertible;
    }

    public void setSqlConvertible(boolean sqlConvertible) {
        this.sqlConvertible = sqlConvertible;
    }

    public boolean isSqlPushdownSupported() {
        return sqlPushdownSupported;
    }

    public void setSqlPushdownSupported(boolean sqlPushdownSupported) {
        this.sqlPushdownSupported = sqlPushdownSupported;
    }

    public boolean isSqlPushdownApplied() {
        return sqlPushdownApplied;
    }

    public void setSqlPushdownApplied(boolean sqlPushdownApplied) {
        this.sqlPushdownApplied = sqlPushdownApplied;
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

    public List<String> getDependencies() {
        return new ArrayList<String>(dependencies);
    }

    public void addDependency(String dependency) {
        addUnique(dependencies, dependency);
    }

    public List<String> getDependencySources() {
        return new ArrayList<String>(dependencySources);
    }

    public void addDependencySource(String dependencySource) {
        addUnique(dependencySources, dependencySource);
    }

    public List<String> getOutputFields() {
        return new ArrayList<String>(outputFields);
    }

    public void addOutputField(String outputField) {
        addUnique(outputFields, outputField);
    }

    public List<String> getTargetFields() {
        return new ArrayList<String>(targetFields);
    }

    public void addTargetField(String targetField) {
        addUnique(targetFields, targetField);
    }

    public String dedupeKey() {
        return safe(mappingName) + "\u0001" + safe(transformationName) + "\u0001" + safe(condition);
    }

    public String toVisibleLine() {
        StringBuilder line = new StringBuilder();
        line.append(transformationName);
        if (!targetFields.isEmpty()) {
            line.append(" -> targets=").append(join(targetFields));
        }
        if (condition.length() > 0) {
            line.append("; condition=").append(condition);
        }
        if (!dependencies.isEmpty()) {
            line.append("; deps=").append(join(dependencies));
        }
        if (!dependencySources.isEmpty()) {
            line.append("; sources=").append(join(dependencySources));
        }
        if (fdlDataFlowTransformApplied) {
            line.append("; FDL dataflow filter applied=").append(sqlCondition);
        } else if (fdlDataFlowTransformSupported) {
            line.append("; FDL dataflow filter candidate=").append(sqlCondition);
        } else if (sqlPushdownApplied) {
            line.append("; SQL WHERE applied=").append(sqlCondition);
        } else if (sqlPushdownSupported) {
            line.append("; SQL WHERE candidate=").append(sqlCondition);
        } else if (limitation.length() > 0) {
            line.append("; limitation=").append(limitation);
        }
        return line.toString();
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

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
