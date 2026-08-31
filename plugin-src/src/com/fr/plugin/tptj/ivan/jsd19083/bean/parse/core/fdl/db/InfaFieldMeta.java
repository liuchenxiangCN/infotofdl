package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db;

import com.fr.dp.datasource.general.base.SqlFieldBean;
import java.sql.Types;
import java.util.Locale;

/**
 * Field metadata declared by Informatica XML.
 *
 * <p>The XML is the authority for the logical/physical field type. Runtime
 * datasource preview may enrich metadata that is absent from the XML, but it
 * must not silently replace a proven XML type.</p>
 */
public class InfaFieldMeta {
    public static final String KIND_TRANSFORM = "TRANSFORMFIELD";
    public static final String KIND_SOURCE = "SOURCEFIELD";
    public static final String KIND_TARGET = "TARGETFIELD";

    private String instanceName;
    private String fieldName;
    private String fieldKind;
    private int ordinal;
    private String dataType;
    private int precision;
    private int scale;
    private boolean nullable = true;
    private boolean primaryKey;

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = safe(instanceName);
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = safe(fieldName);
    }

    public String getFieldKind() {
        return fieldKind;
    }

    public void setFieldKind(String fieldKind) {
        this.fieldKind = safe(fieldKind);
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = safe(dataType);
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = Math.max(precision, 0);
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = Math.max(scale, 0);
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public boolean isTypeProven() {
        return jdbcType() != Types.OTHER;
    }

    public SqlFieldBean toSqlFieldBean(String nameOverride) {
        SqlFieldBean field = new SqlFieldBean();
        String name = safe(nameOverride).length() > 0 ? safe(nameOverride) : fieldName;
        int jdbcType = jdbcType();
        field.setName(name);
        field.setLabel(name);
        field.setType(jdbcType);
        field.setTypeName(fdlTypeName(jdbcType));
        field.setDialectTypeName(dialectTypeName());
        field.setSize(precision);
        field.setScale(scale);
        field.setNullable(nullable);
        field.setPk(primaryKey);
        field.setLogicalPk(primaryKey);
        field.setEditable(true);
        if (jdbcType == Types.OTHER) {
            field.setInvalidWarning("Unsupported Informatica field type: " + safe(dataType));
        }
        return field;
    }

    private int jdbcType() {
        String type = normalizedType();
        if (KIND_TRANSFORM.equals(fieldKind)) {
            if ("string".equals(type)) return Types.VARCHAR;
            if ("decimal".equals(type)) return Types.DECIMAL;
            if ("integer".equals(type)) return Types.INTEGER;
            if ("double".equals(type)) return Types.DOUBLE;
            if ("date/time".equals(type) || "datetime".equals(type)) return Types.TIMESTAMP;
            return Types.OTHER;
        }
        if ("varchar2".equals(type) || "varchar".equals(type)) return Types.VARCHAR;
        if ("char".equals(type)) return Types.CHAR;
        if ("number".equals(type) || "number(p,s)".equals(type)) return Types.NUMERIC;
        if ("decimal".equals(type)) return Types.DECIMAL;
        if ("integer".equals(type)) return Types.INTEGER;
        if ("double".equals(type)) return Types.DOUBLE;
        if ("date".equals(type) || "timestamp".equals(type) || "date/time".equals(type)) return Types.TIMESTAMP;
        return Types.OTHER;
    }

    private String fdlTypeName(int jdbcType) {
        switch (jdbcType) {
            case Types.VARCHAR: return "VARCHAR";
            case Types.CHAR: return "CHAR";
            case Types.NUMERIC: return "NUMBER";
            case Types.DECIMAL: return "DECIMAL";
            case Types.INTEGER: return "INTEGER";
            case Types.DOUBLE: return "DOUBLE";
            case Types.TIMESTAMP: return KIND_TARGET.equals(fieldKind) && "date".equals(normalizedType()) ? "DATE" : "TIMESTAMP";
            default: return safe(dataType).length() > 0 ? safe(dataType).toUpperCase(Locale.ROOT) : "UNKNOWN";
        }
    }

    private String dialectTypeName() {
        String type = normalizedType();
        if (type.length() == 0) return "";
        if (("varchar2".equals(type) || "varchar".equals(type) || "char".equals(type)) && precision > 0) {
            return type + "(" + precision + ")";
        }
        if (("number".equals(type) || "number(p,s)".equals(type) || "decimal".equals(type)) && precision > 0) {
            return type.replace("(p,s)", "") + "(" + precision + "," + scale + ")";
        }
        return type;
    }

    private String normalizedType() {
        return safe(dataType).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
