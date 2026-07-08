package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db;

public class ConnectionMappingInfo {
    public static final String STATUS_XML_SEMANTICS_PARSED_TRANSLATION_PENDING = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING";
    public static final String STATUS_FDL_GENERATION_RESEARCH_PENDING = "FDL_GENERATION_RESEARCH_PENDING";
    public static final String STATUS_FDL_ADAPTER_REQUIRED = "FDL_ADAPTER_REQUIRED";
    public static final String STATUS_FDL_TRANSLATION_READY = "FDL_TRANSLATION_READY";

    public static final String STATUS_SAFE = STATUS_FDL_TRANSLATION_READY;
    public static final String STATUS_CONFIGURED = STATUS_FDL_TRANSLATION_READY;
    public static final String STATUS_ADAPTER_REQUIRED = STATUS_FDL_ADAPTER_REQUIRED;
    public static final String STATUS_UNKNOWN_ODBC = STATUS_FDL_ADAPTER_REQUIRED;
    public static final String STATUS_UNVERIFIED = STATUS_FDL_GENERATION_RESEARCH_PENDING;

    private String connectionName = "";
    private String connectionType = "";
    private String connectionSubtype = "";
    private String rawSql = "";
    private String extensionType = "";
    private String extensionName = "";
    private String transformationName = "";
    private String transformationType = "";
    private String sessionName = "";
    private String mappingName = "";
    private String fdlDatasourceType = "";
    private String mappingStatus = "";
    private String mappingBasis = "";
    private String limitation = "";

    public String getConnectionName() {
        return connectionName;
    }

    public void setConnectionName(String connectionName) {
        this.connectionName = safe(connectionName);
    }

    public String getConnectionType() {
        return connectionType;
    }

    public void setConnectionType(String connectionType) {
        this.connectionType = safe(connectionType);
    }

    public String getConnectionSubtype() {
        return connectionSubtype;
    }

    public void setConnectionSubtype(String connectionSubtype) {
        this.connectionSubtype = safe(connectionSubtype);
    }

    public String getRawSql() {
        return rawSql;
    }

    public void setRawSql(String rawSql) {
        this.rawSql = safe(rawSql);
    }

    public boolean hasRawSql() {
        return rawSql.length() > 0;
    }

    public String getExtensionType() {
        return extensionType;
    }

    public void setExtensionType(String extensionType) {
        this.extensionType = safe(extensionType);
    }

    public String getExtensionName() {
        return extensionName;
    }

    public void setExtensionName(String extensionName) {
        this.extensionName = safe(extensionName);
    }

    public String getTransformationName() {
        return transformationName;
    }

    public void setTransformationName(String transformationName) {
        this.transformationName = safe(transformationName);
    }

    public String getTransformationType() {
        return transformationType;
    }

    public void setTransformationType(String transformationType) {
        this.transformationType = safe(transformationType);
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = safe(sessionName);
    }

    public String getMappingName() {
        return mappingName;
    }

    public void setMappingName(String mappingName) {
        this.mappingName = safe(mappingName);
    }

    public String getFdlDatasourceType() {
        return fdlDatasourceType;
    }

    public void setFdlDatasourceType(String fdlDatasourceType) {
        this.fdlDatasourceType = safe(fdlDatasourceType);
    }

    public String getMappingStatus() {
        return mappingStatus;
    }

    public void setMappingStatus(String mappingStatus) {
        this.mappingStatus = normalizeStatus(mappingStatus);
    }

    public String getMappingBasis() {
        return mappingBasis;
    }

    public void setMappingBasis(String mappingBasis) {
        String value = safe(mappingBasis);
        if (isOdbcOrJdbcOdbc() && isLegacyOdbcFallbackText(value)) {
            this.mappingBasis = "FDL data connection name match; datasourceType resolved from the existing FDL connection";
            return;
        }
        this.mappingBasis = value;
    }

    public String getLimitation() {
        return limitation;
    }

    public void setLimitation(String limitation) {
        String value = safe(limitation);
        if (isLegacyOdbcFallbackText(value)) {
            this.limitation = "XML expresses connectionName, connectionType/connectionSubtype, and raw SQL when available; FDL generation resolves datasourceType by matching the existing FDL data connection name, not by inferring from ODBC/JDBC_ODBC.";
            return;
        }
        this.limitation = value;
    }

    public boolean hasRisk() {
        return limitation.length() > 0
            || !STATUS_FDL_TRANSLATION_READY.equals(mappingStatus);
    }

    public boolean requiresAdapter() {
        return requiresFdlAdapter();
    }

    public boolean isFdlTranslationReady() {
        return STATUS_FDL_TRANSLATION_READY.equals(mappingStatus) && fdlDatasourceType.length() > 0;
    }

    public boolean requiresFdlAdapter() {
        return STATUS_FDL_ADAPTER_REQUIRED.equals(mappingStatus);
    }

    public boolean requiresFdlGenerationResearch() {
        return STATUS_FDL_GENERATION_RESEARCH_PENDING.equals(mappingStatus);
    }

    public boolean isXmlSemanticsParsedTranslationPending() {
        return STATUS_XML_SEMANTICS_PARSED_TRANSLATION_PENDING.equals(mappingStatus);
    }

    public boolean isOdbcOrJdbcOdbc() {
        return isOdbcOrJdbcOdbc(connectionType, connectionSubtype);
    }

    public String dedupeKey() {
        return normalize(sessionName) + "\u0001"
            + normalize(mappingName) + "\u0001"
            + normalize(connectionName) + "\u0001"
            + normalize(extensionType) + "\u0001"
            + normalize(transformationName) + "\u0001"
            + normalize(transformationType);
    }

    public String toVisibleLine() {
        StringBuilder line = new StringBuilder();
        line.append(connectionName.length() > 0 ? connectionName : "-");
        line.append(" [").append(extensionType.length() > 0 ? extensionType : "-");
        if (transformationType.length() > 0 || transformationName.length() > 0) {
            line.append(", ").append(transformationType.length() > 0 ? transformationType : "-");
            line.append("/").append(transformationName.length() > 0 ? transformationName : "-");
        }
        line.append("]");
        if (sessionName.length() > 0) {
            line.append("; session=").append(sessionName);
        }
        if (mappingName.length() > 0) {
            line.append("; mapping=").append(mappingName);
        }
        line.append("; original=").append(connectionType.length() > 0 ? connectionType : "-")
            .append("/").append(connectionSubtype.length() > 0 ? connectionSubtype : "-");
        line.append("; fdlDatasourceType=").append(fdlDatasourceType.length() > 0 ? fdlDatasourceType : "-");
        line.append("; status=").append(mappingStatus.length() > 0 ? mappingStatus : "-");
        if (rawSql.length() > 0) {
            line.append("; rawSql=").append(singleLine(redactSensitive(rawSql)));
        }
        if (mappingBasis.length() > 0) {
            line.append("; basis=").append(mappingBasis);
        }
        if (limitation.length() > 0) {
            line.append("; limitation=").append(limitation);
        }
        return line.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeStatus(String value) {
        String safeValue = safe(value);
        String normalized = normalize(safeValue);
        if (normalized.length() == 0) {
            return "";
        }
        if ("SAFE".equals(normalized)
            || "CONFIGUREDBYCONNECTIONNAME".equals(normalized)
            || normalize(STATUS_FDL_TRANSLATION_READY).equals(normalized)) {
            return STATUS_FDL_TRANSLATION_READY;
        }
        if ("UNKNOWNODBCFALLBACK".equals(normalized)
            || "ADAPTERREQUIRED".equals(normalized)
            || normalize(STATUS_FDL_ADAPTER_REQUIRED).equals(normalized)) {
            return STATUS_FDL_ADAPTER_REQUIRED;
        }
        if ("UNVERIFIED".equals(normalized)
            || "FDLSHAPERESEARCHREQUIRED".equals(normalized)
            || normalize(STATUS_FDL_GENERATION_RESEARCH_PENDING).equals(normalized)) {
            return STATUS_FDL_GENERATION_RESEARCH_PENDING;
        }
        if (normalize(STATUS_XML_SEMANTICS_PARSED_TRANSLATION_PENDING).equals(normalized)) {
            return STATUS_XML_SEMANTICS_PARSED_TRANSLATION_PENDING;
        }
        return safeValue;
    }

    private static boolean isLegacyOdbcFallbackText(String value) {
        String normalized = normalize(value);
        return normalized.contains("UNKNOWNODBCFALLBACK")
            || normalized.contains("VISIBLEFALLBACK")
            || normalized.contains("VISIBLESCAFFOLD")
            || normalized.contains("FDLDATASOURCETYPEMYSQL")
            || normalized.contains("MYSQLISONLY")
            || (normalized.contains("ODBC") && (normalized.contains("FALLBACK") || normalized.contains("MYSQL")));
    }

    public static boolean isOdbcOrJdbcOdbc(String connectionType, String connectionSubtype) {
        String normalizedType = normalize(connectionType);
        String normalizedSubtype = normalize(connectionSubtype);
        return normalizedType.contains("ODBC")
            || normalizedSubtype.contains("ODBC")
            || normalizedType.contains("JDBCODBC")
            || normalizedSubtype.contains("JDBCODBC");
    }

    private static String singleLine(String value) {
        return safe(value).replaceAll("\\s+", " ");
    }

    private static String redactSensitive(String value) {
        return safe(value).replaceAll("(?i)(password|passwd|pwd|token|cookie|license|secret)(\\s*[=:]\\s*)[^\\s&\\\"'<>]+", "$1$2[REDACTED]");
    }

    private static String normalize(String value) {
        return safe(value).replace("\"", "").replace("`", "").replace("[", "").replace("]", "").replaceAll("\\s+", "").toUpperCase();
    }
}
