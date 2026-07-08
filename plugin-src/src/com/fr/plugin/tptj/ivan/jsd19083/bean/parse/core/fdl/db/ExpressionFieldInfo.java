package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db;

import java.util.ArrayList;
import java.util.List;

public class ExpressionFieldInfo {
    public static final String KIND_PASS_THROUGH = "PASS_THROUGH";
    public static final String KIND_RENAME = "RENAME";
    public static final String KIND_CONSTANT = "CONSTANT";
    public static final String KIND_SQL_EXPRESSION = "SQL_EXPRESSION";
    public static final String KIND_CONDITIONAL = "CONDITIONAL";
    public static final String KIND_ERROR = "ERROR";
    public static final String KIND_UNSUPPORTED = "UNSUPPORTED";

    private String mappingName;
    private String transformationName;
    private String fieldName;
    private String targetField;
    private String targetInstance;
    private String expression;
    private String kind = KIND_UNSUPPORTED;
    private String sqlExpression;
    private boolean sqlConvertible;
    private boolean sqlPushdownSupported;
    private boolean fdlDataFlowPostProjectionSupported;
    private boolean fdlDataFlowPostProjectionApplied;
    private String limitation;
    private List<String> dependencies = new ArrayList<String>();
    private List<String> dependencySources = new ArrayList<String>();

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

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = safe(fieldName);
    }

    public String getTargetField() {
        return targetField;
    }

    public void setTargetField(String targetField) {
        this.targetField = safe(targetField);
    }

    public String getTargetInstance() {
        return targetInstance;
    }

    public void setTargetInstance(String targetInstance) {
        this.targetInstance = safe(targetInstance);
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = safe(expression);
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = safe(kind);
    }

    public String getSqlExpression() {
        return sqlExpression;
    }

    public void setSqlExpression(String sqlExpression) {
        this.sqlExpression = safe(sqlExpression);
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

    public boolean isFdlDataFlowPostProjectionSupported() {
        return fdlDataFlowPostProjectionSupported;
    }

    public void setFdlDataFlowPostProjectionSupported(boolean fdlDataFlowPostProjectionSupported) {
        this.fdlDataFlowPostProjectionSupported = fdlDataFlowPostProjectionSupported;
    }

    public boolean isFdlDataFlowPostProjectionApplied() {
        return fdlDataFlowPostProjectionApplied;
    }

    public void setFdlDataFlowPostProjectionApplied(boolean fdlDataFlowPostProjectionApplied) {
        this.fdlDataFlowPostProjectionApplied = fdlDataFlowPostProjectionApplied;
    }

    public String getLimitation() {
        return limitation;
    }

    public void setLimitation(String limitation) {
        this.limitation = safe(limitation);
    }

    public List<String> getDependencies() {
        return new ArrayList<String>(dependencies);
    }

    public void addDependency(String dependency) {
        dependency = safe(dependency);
        if (dependency.length() == 0 || dependencies.contains(dependency)) {
            return;
        }
        dependencies.add(dependency);
    }

    public List<String> getDependencySources() {
        return new ArrayList<String>(dependencySources);
    }

    public void addDependencySource(String dependencySource) {
        dependencySource = safe(dependencySource);
        if (dependencySource.length() == 0 || dependencySources.contains(dependencySource)) {
            return;
        }
        dependencySources.add(dependencySource);
    }

    public boolean isComputed() {
        return !KIND_PASS_THROUGH.equals(kind) && !KIND_RENAME.equals(kind);
    }

    public String getReadAlias() {
        return safe(fieldName);
    }

    public String toVisibleLine() {
        StringBuilder line = new StringBuilder();
        line.append(transformationName).append(".").append(fieldName);
        if (targetField.length() > 0) {
            line.append(" -> ").append(targetField);
        }
        line.append(" [").append(kind).append("]");
        if (expression.length() > 0) {
            line.append(": ").append(expression);
        }
        if (!dependencies.isEmpty()) {
            line.append("; deps=").append(join(dependencies));
        }
        if (sqlPushdownSupported) {
            line.append("; SQL pushdown=").append(sqlExpression);
        } else if (fdlDataFlowPostProjectionApplied) {
            line.append("; FDL API post-projection adapter contract=").append(sqlExpression);
        } else if (limitation.length() > 0) {
            line.append("; limitation=").append(limitation);
        }
        return line.toString();
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
