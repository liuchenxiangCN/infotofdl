package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db;

import java.util.ArrayList;
import java.util.List;

public class TargetWriteInfo {
    private String targetInstance;
    private String targetDefinition;
    private String treatSourceRowsAs;
    private String targetLoadType;
    private String insert;
    private String updateAsUpdate;
    private String updateAsInsert;
    private String updateElseInsert;
    private String delete;
    private String truncate;
    private String targetLoadOrder;
    private String preSql;
    private String postSql;
    private List<String> keyFields = new ArrayList<String>();

    public String getTargetInstance() {
        return targetInstance;
    }

    public void setTargetInstance(String targetInstance) {
        this.targetInstance = safe(targetInstance);
    }

    public String getTargetDefinition() {
        return targetDefinition;
    }

    public void setTargetDefinition(String targetDefinition) {
        this.targetDefinition = safe(targetDefinition);
    }

    public String getTreatSourceRowsAs() {
        return treatSourceRowsAs;
    }

    public void setTreatSourceRowsAs(String treatSourceRowsAs) {
        this.treatSourceRowsAs = safe(treatSourceRowsAs);
    }

    public String getTargetLoadType() {
        return targetLoadType;
    }

    public void setTargetLoadType(String targetLoadType) {
        this.targetLoadType = safe(targetLoadType);
    }

    public String getInsert() {
        return insert;
    }

    public void setInsert(String insert) {
        this.insert = safe(insert);
    }

    public String getUpdateAsUpdate() {
        return updateAsUpdate;
    }

    public void setUpdateAsUpdate(String updateAsUpdate) {
        this.updateAsUpdate = safe(updateAsUpdate);
    }

    public String getUpdateAsInsert() {
        return updateAsInsert;
    }

    public void setUpdateAsInsert(String updateAsInsert) {
        this.updateAsInsert = safe(updateAsInsert);
    }

    public String getUpdateElseInsert() {
        return updateElseInsert;
    }

    public void setUpdateElseInsert(String updateElseInsert) {
        this.updateElseInsert = safe(updateElseInsert);
    }

    public String getDelete() {
        return delete;
    }

    public void setDelete(String delete) {
        this.delete = safe(delete);
    }

    public String getTruncate() {
        return truncate;
    }

    public void setTruncate(String truncate) {
        this.truncate = safe(truncate);
    }

    public String getTargetLoadOrder() {
        return targetLoadOrder;
    }

    public void setTargetLoadOrder(String targetLoadOrder) {
        this.targetLoadOrder = safe(targetLoadOrder);
    }

    public String getPreSql() {
        return preSql;
    }

    public void setPreSql(String preSql) {
        this.preSql = safe(preSql);
    }

    public String getPostSql() {
        return postSql;
    }

    public void setPostSql(String postSql) {
        this.postSql = safe(postSql);
    }

    public List<String> getKeyFields() {
        return new ArrayList<String>(keyFields);
    }

    public void addKeyField(String keyField) {
        String value = safe(keyField);
        if (value.length() == 0 || isKeyField(value)) {
            return;
        }
        keyFields.add(value);
    }

    public boolean isKeyField(String fieldName) {
        String key = normalize(fieldName);
        for (String field : keyFields) {
            if (normalize(field).equals(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean isDeleteMode() {
        return "DELETE".equals(normalize(treatSourceRowsAs)) || yes(delete) && !yes(insert);
    }

    public boolean isUpdateMode() {
        return "UPDATE".equals(normalize(treatSourceRowsAs)) || yes(updateAsUpdate) || yes(updateAsInsert) || yes(updateElseInsert);
    }

    public boolean isInsertMode() {
        return "INSERT".equals(normalize(treatSourceRowsAs)) || yes(insert);
    }

    public boolean isTruncateEnabled() {
        return yes(truncate);
    }

    public String toVisibleLine() {
        StringBuilder builder = new StringBuilder();
        append(builder, "target", firstNonEmpty(targetInstance, targetDefinition));
        append(builder, "treat", treatSourceRowsAs);
        append(builder, "insert", insert);
        append(builder, "update", updateAsUpdate);
        append(builder, "updateAsInsert", updateAsInsert);
        append(builder, "updateElseInsert", updateElseInsert);
        append(builder, "delete", delete);
        append(builder, "truncate", truncate);
        if (!keyFields.isEmpty()) {
            append(builder, "keys", join(keyFields));
        }
        append(builder, "loadOrder", targetLoadOrder);
        return builder.toString();
    }

    private static void append(StringBuilder builder, String label, String value) {
        String safeValue = safe(value);
        if (safeValue.length() == 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(label).append("=").append(safeValue);
    }

    private static String firstNonEmpty(String first, String second) {
        return safe(first).length() > 0 ? safe(first) : safe(second);
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static boolean yes(String value) {
        String normalized = normalize(value);
        return "YES".equals(normalized) || "TRUE".equals(normalized) || "Y".equals(normalized) || "1".equals(normalized);
    }

    private static String normalize(String value) {
        return safe(value).replace("\"", "").replace("`", "").replace("[", "").replace("]", "").replaceAll("\\s+", "").toUpperCase();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
