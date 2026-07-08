/*
 * Decompiled with CFR 0.152.
 */
package com.fr.plugin.tptj.ivan.jsd19083.util.xml;

import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.api.ApiNodeFromInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.ConnectionMappingInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.FieldLineageInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.TargetWriteInfo;
import com.fr.plugin.tptj.ivan.jsd19083.util.xml.SQLInfo;
import java.util.ArrayList;
import java.util.List;

class GroupSQLInfo {
    private String groupName;
    private String name;
    private List<SQLInfo> sqlInfos = new ArrayList<SQLInfo>();
    private String fromDBName;
    private String fromDBType;
    private String toDBName;
    private String toDBType;
    private String targetTable;
    private String targetSchema;
    private ApiNodeFromInfoBean apiConfig;
    private FieldLineageInfo fieldLineageInfo;
    private TargetWriteInfo targetWriteInfo;
    private List<ConnectionMappingInfo> connectionInfos = new ArrayList<ConnectionMappingInfo>();

    GroupSQLInfo() {
    }

    public String getGroupName() {
        return this.groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<SQLInfo> getSqlInfos() {
        return this.sqlInfos;
    }

    public void setSqlInfos(List<SQLInfo> sqlInfos) {
        this.sqlInfos = sqlInfos;
    }

    public String getFromDBName() {
        return this.fromDBName;
    }

    public void setFromDBName(String fromDBName) {
        this.fromDBName = fromDBName;
    }

    public String getFromDBType() {
        return this.fromDBType;
    }

    public void setFromDBType(String fromDBType) {
        this.fromDBType = fromDBType;
    }

    public String getToDBName() {
        return this.toDBName;
    }

    public void setToDBName(String toDBName) {
        this.toDBName = toDBName;
    }

    public String getToDBType() {
        return this.toDBType;
    }

    public void setToDBType(String toDBType) {
        this.toDBType = toDBType;
    }

    public String getTargetTable() {
        return this.targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public String getTargetSchema() {
        return this.targetSchema;
    }

    public void setTargetSchema(String targetSchema) {
        this.targetSchema = targetSchema;
    }

    public ApiNodeFromInfoBean getApiConfig() {
        return this.apiConfig;
    }

    public void setApiConfig(ApiNodeFromInfoBean apiConfig) {
        this.apiConfig = apiConfig;
    }

    public FieldLineageInfo getFieldLineageInfo() {
        return this.fieldLineageInfo;
    }

    public void setFieldLineageInfo(FieldLineageInfo fieldLineageInfo) {
        this.fieldLineageInfo = fieldLineageInfo;
    }

    public TargetWriteInfo getTargetWriteInfo() {
        return this.targetWriteInfo;
    }

    public void setTargetWriteInfo(TargetWriteInfo targetWriteInfo) {
        this.targetWriteInfo = targetWriteInfo;
    }

    public List<ConnectionMappingInfo> getConnectionInfos() {
        return new ArrayList<ConnectionMappingInfo>(this.connectionInfos);
    }

    public void addConnectionInfo(ConnectionMappingInfo connectionInfo) {
        if (connectionInfo == null) {
            return;
        }
        for (ConnectionMappingInfo existing : this.connectionInfos) {
            if (existing.dedupeKey().equals(connectionInfo.dedupeKey())) {
                return;
            }
        }
        this.connectionInfos.add(connectionInfo);
    }
}
