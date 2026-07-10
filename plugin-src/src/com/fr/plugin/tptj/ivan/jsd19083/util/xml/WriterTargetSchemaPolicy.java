package com.fr.plugin.tptj.ivan.jsd19083.util.xml;

/**
 * Selects the FDL writer schema without allowing source-side metadata or an
 * unreliable connection default schema to override the Oracle login owner.
 */
public final class WriterTargetSchemaPolicy {
    private static final String FDL_EMPTY_SCHEMA = "__EMPTY__";

    private WriterTargetSchemaPolicy() {
    }

    public static String select(String explicitTargetOwner, String oracleConnectionUserName, String connectionDefaultSchema) {
        String selected = firstNonBlank(explicitTargetOwner, oracleConnectionUserName, connectionDefaultSchema);
        return selected == null ? "" : selected;
    }

    private static String firstNonBlank(String ... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String candidate = value == null ? "" : value.trim();
            if (!candidate.isEmpty() && !FDL_EMPTY_SCHEMA.equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return "";
    }
}
