/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.fr.dp.component.domain.entities.DataSyncComponent
 *  com.fr.dp.datasource.ds.utils.SampleUtils
 *  com.fr.dp.datasource.dto.DBFieldTransferBean
 *  com.fr.dp.datasource.dto.FieldTransferBean
 *  com.fr.dp.datasource.entity.DataBaseConfig
 *  com.fr.dp.datasource.entity.SQLConfig
 *  com.fr.dp.datasource.executor.SqlExecutor$SqlType
 *  com.fr.dp.datasource.general.base.SqlFieldBean
 *  com.fr.dp.devwork.work.component.api.Component
 *  com.fr.dp.devwork.work.component.dto.ComponentDTO
 *  com.fr.dp.integration.core.writeconf.AppendWriteConf
 *  com.fr.dp.integration.core.writeconf.AppendWriteConf$UpdateStrategy
 *  com.fr.dp.integration.core.writeconf.WriteConf
 *  com.fr.dp.node.domain.entities.CoordinateDataFlowNode
 *  com.fr.dp.node.domain.entities.DBReadNode
 *  com.fr.dp.node.domain.entities.DBReadNodeType
 *  com.fr.dp.node.domain.entities.DBWriteNode
 *  com.fr.dp.node.domain.entities.DBWriteNodeType
 *  com.fr.dp.node.domain.entities.api.DataFlowNode
 *  com.fr.dp.node.domain.entities.api.DataFlowNodeType
 *  com.fr.dp.node.domain.entities.api.ICoordinateDataFlowNode
 *  com.fr.dp.node.domain.entities.transaction.DefaultTransactionStrategy
 *  com.fr.stable.StringUtils
 *  org.jetbrains.annotations.NotNull
 */
package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl;

import com.fr.dp.component.domain.entities.DataSyncComponent;
import com.fr.dp.datasource.ds.utils.SampleUtils;
import com.fr.dp.datasource.dto.DBFieldTransferBean;
import com.fr.dp.datasource.dto.FieldTransferBean;
import com.fr.dp.datasource.entity.DataBaseConfig;
import com.fr.dp.datasource.entity.SQLConfig;
import com.fr.dp.datasource.executor.SqlExecutor;
import com.fr.dp.datasource.general.base.SqlFieldBean;
import com.fr.dp.devwork.work.component.api.Component;
import com.fr.dp.devwork.work.component.dto.ComponentDTO;
import com.fr.dp.node.domain.entities.CoordinateDataFlowNode;
import com.fr.dp.node.domain.entities.DBReadNode;
import com.fr.dp.node.domain.entities.DBReadNodeType;
import com.fr.dp.node.domain.entities.DBWriteNode;
import com.fr.dp.node.domain.entities.DBWriteNodeType;
import com.fr.dp.node.domain.entities.api.DataFlowNode;
import com.fr.dp.node.domain.entities.api.DataFlowNodeType;
import com.fr.dp.node.domain.entities.api.ICoordinateDataFlowNode;
import com.fr.dp.node.domain.entities.transaction.DefaultTransactionStrategy;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.DBNodeFromInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.DBNodeToInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.ExpressionFieldInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.FieldLineageInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.FilterFieldInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.LookupFieldInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.single.DefaultSingleXMLNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.service.FDLService;
import com.fr.plugin.tptj.ivan.jsd19083.util.DBFieldTransferUtils;
import com.fr.stable.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public class SyncTaskNodeBean
extends DefaultSingleXMLNodeBean {
    private DBNodeFromInfoBean fromInfo;
    private DBNodeToInfoBean toInfo;
    private FieldLineageInfo fieldLineageInfo;

    public SyncTaskNodeBean(int x, int y, String name, String id, DBNodeFromInfoBean fromInfo, DBNodeToInfoBean toInfo) {
        this(x, y, name, id, fromInfo, toInfo, null);
    }

    public SyncTaskNodeBean(int x, int y, String name, String id, DBNodeFromInfoBean fromInfo, DBNodeToInfoBean toInfo, FieldLineageInfo fieldLineageInfo) {
        super(x, y, name, id);
        this.fromInfo = fromInfo;
        this.toInfo = toInfo;
        this.fieldLineageInfo = fieldLineageInfo;
    }

    public DBNodeFromInfoBean getFromInfo() {
        return this.fromInfo;
    }

    public DBNodeToInfoBean getToInfo() {
        return this.toInfo;
    }

    @Override
    public ComponentDTO getFDLComponentDTO() {
        return FDLService.INSTANCE.createComponentDTO(this.getX(), this.getY(), this.getFDLNodeType(), this.getFDLComponent());
    }

    @Override
    public Component getFDLComponent() {
        DataSyncComponent result = new DataSyncComponent();
        result.setId(this.getId());
        result.setName(this.getName());
        result.setNote(this.getVisibleLineageNote());
        result.setExecuteLogic("AND");
        result.setDisabled(false);
        result.setReader((ICoordinateDataFlowNode)this.getDBReaderNode());
        result.setWriter((ICoordinateDataFlowNode)this.getDBWriteNode());
        DBWriteNode dbWriteNode = (DBWriteNode)result.getWriter().getNodeContent();
        DBFieldTransferBean refresh = DBFieldTransferUtils.refreshWithoutError(result);
        List<FieldTransferBean> fieldTransferItems = FieldTransferFactory.createForSync(refresh, this.fieldLineageInfo,
            this.toInfo == null ? null : this.toInfo.getTargetWriteInfo());
        for (FieldTransferBean item : fieldTransferItems) {
            SyncTaskNodeBean.applyLineageComment(item.getWriteColumn(), this.fieldLineageInfo);
        }
        dbWriteNode.setFieldTransferItems(fieldTransferItems);
        FdlWriteConfigHelper.applyWriteConfig(dbWriteNode, fieldTransferItems, this.toInfo == null ? null : this.toInfo.getTargetWriteInfo());
        result.getWriter().setNodeContent((DataFlowNode)dbWriteNode);
        return result;
    }

    private static void applyLineageComment(SqlFieldBean targetFieldBean, FieldLineageInfo fieldLineageInfo) {
        if (targetFieldBean == null || fieldLineageInfo == null || !fieldLineageInfo.hasVisibleInfos()) {
            return;
        }
        List<ExpressionFieldInfo> expressionInfos = fieldLineageInfo.getExpressionInfos(targetFieldBean.getName());
        List<FilterFieldInfo> filterInfos = fieldLineageInfo.getFilterInfos(targetFieldBean.getName());
        List<LookupFieldInfo> lookupInfos = fieldLineageInfo.getLookupInfos(targetFieldBean.getName());
        if (expressionInfos.isEmpty() && filterInfos.isEmpty() && lookupInfos.isEmpty()) {
            return;
        }
        StringBuilder comment = new StringBuilder();
        if (StringUtils.isNotEmpty(targetFieldBean.getComment())) {
            comment.append(targetFieldBean.getComment()).append("\n");
        }
        for (ExpressionFieldInfo info : expressionInfos) {
            comment.append("Informatica Expression: ").append(info.toVisibleLine()).append("\n");
        }
        for (FilterFieldInfo info : filterInfos) {
            comment.append("Informatica Filter: ").append(info.toVisibleLine()).append("\n");
        }
        for (LookupFieldInfo info : lookupInfos) {
            comment.append("Informatica Lookup Procedure: ").append(info.toVisibleLine()).append("\n");
        }
        targetFieldBean.setComment(comment.toString().trim());
    }

    @NotNull
    private CoordinateDataFlowNode getDBReaderNode() {
        CoordinateDataFlowNode reader = new CoordinateDataFlowNode();
        reader.setCompareId(UUID.randomUUID().toString());
        reader.setNodeType((DataFlowNodeType)new DBReadNodeType());
        DBReadNode readNode = new DBReadNode();
        readNode.setFromDatasourceType(this.getFromInfo().getDatasourceType());
        readNode.setFromConnectionName(this.getFromInfo().getConnectionName());
        SQLConfig databaseConfig = new SQLConfig();
        databaseConfig.setSql(this.getFromInfo().getSql());
        databaseConfig.setSqlType(SqlExecutor.SqlType.SINGLE_SQL.getSqlTypeName());
        readNode.setDataBaseConfig((DataBaseConfig)databaseConfig);
        readNode.setSamples(SampleUtils.parseSampleUtils(new ArrayList()));
        readNode.setId(UUID.randomUUID().toString());
        readNode.setName("reader");
        readNode.setNote(this.getVisibleLineageNote());
        reader.setNodeContent((DataFlowNode)readNode);
        reader.setCompareId(UUID.randomUUID().toString());
        return reader;
    }

    @NotNull
    private CoordinateDataFlowNode getDBWriteNode() {
        CoordinateDataFlowNode writer = new CoordinateDataFlowNode();
        writer.setNodeType((DataFlowNodeType)new DBWriteNodeType());
        DBWriteNode writeNode = new DBWriteNode();
        writeNode.setToDatasourceType(this.getToInfo().getDatasourceType());
        writeNode.setToConnectionName(this.getToInfo().getConnectionName());
        writeNode.setFieldTransferItems(new ArrayList());
        FdlWriteConfigHelper.applyWriteConfig(writeNode, new ArrayList<FieldTransferBean>(), this.toInfo == null ? null : this.toInfo.getTargetWriteInfo());
        writeNode.setToDatabase(this.getToInfo().getDatabaseName());
        writeNode.setToSchema(this.getToInfo().getSchema());
        writeNode.setToTableMode(0);
        writeNode.setToTable(this.getToInfo().getTableName());
        writeNode.setComment("");
        writeNode.setMapType("NAME");
        writeNode.setTransaction(new DefaultTransactionStrategy());
        writeNode.setId(UUID.randomUUID().toString());
        writeNode.setName("writer");
        writeNode.setNote(this.getVisibleLineageNote());
        writer.setNodeContent((DataFlowNode)writeNode);
        writer.setNodeContent((DataFlowNode)writeNode);
        writer.setCompareId(UUID.randomUUID().toString());
        return writer;
    }

    @Override
    public String getFDLNodeType() {
        return "DATA_SYNC";
    }

    private String getVisibleLineageNote() {
        StringBuilder note = new StringBuilder();
        if (this.fieldLineageInfo != null) {
            String lineageNote = this.fieldLineageInfo.getVisibleNote();
            if (StringUtils.isNotEmpty(lineageNote)) {
                note.append(lineageNote);
            }
        }
        String targetWriteNote = FdlWriteConfigHelper.targetWriteNote(this.toInfo == null ? null : this.toInfo.getTargetWriteInfo());
        if (StringUtils.isNotEmpty(targetWriteNote)) {
            if (note.length() > 0) {
                note.append("\n");
            }
            note.append(targetWriteNote);
        }
        return note.toString();
    }
}
