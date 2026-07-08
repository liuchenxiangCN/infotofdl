/*
 * Decompiled with CFR 0.152.
 */
package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db;

public class DBNodeToInfoBean {
    private String datasourceType;
    private String connectionName;
    private String connectionType;
    private String connectionSubtype;
    private String rawSql;
    private String mappingStatus;
    private String mappingBasis;
    private String limitation;
    private String tableName;
    private String schema;
    private String databaseName;
    private TargetWriteInfo targetWriteInfo;

    private DBNodeToInfoBean() {
    }

    public static DBNodeToInfoBean create() {
        return new DBNodeToInfoBean();
    }

    public DBNodeToInfoBean connectionName(String connectionName) {
        this.connectionName = connectionName;
        return this;
    }

    public DBNodeToInfoBean connectionType(String connectionType) {
        this.connectionType = connectionType;
        return this;
    }

    public DBNodeToInfoBean connectionSubtype(String connectionSubtype) {
        this.connectionSubtype = connectionSubtype;
        return this;
    }

    public DBNodeToInfoBean rawSql(String rawSql) {
        this.rawSql = rawSql;
        return this;
    }

    public DBNodeToInfoBean mappingStatus(String mappingStatus) {
        this.mappingStatus = mappingStatus;
        return this;
    }

    public DBNodeToInfoBean mappingBasis(String mappingBasis) {
        this.mappingBasis = mappingBasis;
        return this;
    }

    public DBNodeToInfoBean limitation(String limitation) {
        this.limitation = limitation;
        return this;
    }

    public DBNodeToInfoBean connectionMappingInfo(ConnectionMappingInfo info) {
        if (info == null) {
            return this;
        }
        return this.connectionName(info.getConnectionName())
            .connectionType(info.getConnectionType())
            .connectionSubtype(info.getConnectionSubtype())
            .rawSql(info.getRawSql())
            .datasourceType(info.getFdlDatasourceType())
            .mappingStatus(info.getMappingStatus())
            .mappingBasis(info.getMappingBasis())
            .limitation(info.getLimitation());
    }

    public DBNodeToInfoBean tableName(String tableName) {
        this.tableName = tableName;
        return this;
    }

    public DBNodeToInfoBean schema(String schema) {
        this.schema = schema;
        return this;
    }

    public DBNodeToInfoBean databaseName(String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    public DBNodeToInfoBean datasourceType(String datasourceType) {
        this.datasourceType = datasourceType;
        return this;
    }

    public DBNodeToInfoBean targetWriteInfo(TargetWriteInfo targetWriteInfo) {
        this.targetWriteInfo = targetWriteInfo;
        return this;
    }

    public String getDatasourceType() {
        return this.datasourceType;
    }

    public String getConnectionName() {
        return this.connectionName;
    }

    public String getConnectionType() {
        return this.connectionType;
    }

    public String getConnectionSubtype() {
        return this.connectionSubtype;
    }

    public String getRawSql() {
        return this.rawSql;
    }

    public String getMappingStatus() {
        return this.mappingStatus;
    }

    public String getMappingBasis() {
        return this.mappingBasis;
    }

    public String getLimitation() {
        return this.limitation;
    }

    public boolean requiresFdlAdapter() {
        return ConnectionMappingInfo.STATUS_FDL_ADAPTER_REQUIRED.equals(this.mappingStatus);
    }

    public boolean isFdlTranslationReady() {
        return ConnectionMappingInfo.STATUS_FDL_TRANSLATION_READY.equals(this.mappingStatus)
            && this.datasourceType != null
            && this.datasourceType.length() > 0;
    }

    public String getTableName() {
        return this.tableName;
    }

    public String getSchema() {
        return this.schema;
    }

    public String getDatabaseName() {
        return this.databaseName;
    }

    public TargetWriteInfo getTargetWriteInfo() {
        return this.targetWriteInfo;
    }
}
