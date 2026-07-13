package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db;

/** A connector-proven target field binding with separate read/write metadata. */
public class MappingFieldBinding {
    public static final String PROVEN = "PROVEN";
    public static final String RUNTIME_REQUIRED = "RUNTIME_REQUIRED";
    public static final String UNRESOLVED = "UNRESOLVED";
    public static final String UNSUPPORTED = "UNSUPPORTED";

    private String mappingName;
    private String targetInstance;
    private String targetDefinition;
    private int targetOrdinal;
    private String targetField;
    private String producerInstance;
    private String producerType;
    private int producerOrdinal;
    private String readField;
    private InfaFieldMeta readMeta;
    private InfaFieldMeta writeMeta;
    private String status = UNRESOLVED;
    private String limitation;

    public String getMappingName() { return mappingName; }
    public void setMappingName(String value) { mappingName = safe(value); }
    public String getTargetInstance() { return targetInstance; }
    public void setTargetInstance(String value) { targetInstance = safe(value); }
    public String getTargetDefinition() { return targetDefinition; }
    public void setTargetDefinition(String value) { targetDefinition = safe(value); }
    public int getTargetOrdinal() { return targetOrdinal; }
    public void setTargetOrdinal(int value) { targetOrdinal = value; }
    public String getTargetField() { return targetField; }
    public void setTargetField(String value) { targetField = safe(value); }
    public String getProducerInstance() { return producerInstance; }
    public void setProducerInstance(String value) { producerInstance = safe(value); }
    public String getProducerType() { return producerType; }
    public void setProducerType(String value) { producerType = safe(value); }
    public int getProducerOrdinal() { return producerOrdinal; }
    public void setProducerOrdinal(int value) { producerOrdinal = value; }
    public String getReadField() { return readField; }
    public void setReadField(String value) { readField = safe(value); }
    public InfaFieldMeta getReadMeta() { return readMeta; }
    public void setReadMeta(InfaFieldMeta value) { readMeta = value; }
    public InfaFieldMeta getWriteMeta() { return writeMeta; }
    public void setWriteMeta(InfaFieldMeta value) { writeMeta = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = safe(value); }
    public String getLimitation() { return limitation; }
    public void setLimitation(String value) { limitation = safe(value); }

    public boolean isProven() {
        return PROVEN.equals(status) && readMeta != null && writeMeta != null
            && readMeta.isTypeProven() && writeMeta.isTypeProven() && readField.length() > 0;
    }

    public String identityKey() {
        return normalize(targetInstance) + "#" + targetOrdinal + "#" + normalize(targetField);
    }

    private static String normalize(String value) {
        return safe(value).replaceAll("\\s+", "").toUpperCase();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
