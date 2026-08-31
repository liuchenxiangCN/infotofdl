package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl;

import com.fr.dp.component.domain.entities.VisualizedComponent;
import com.fr.dp.datasource.ds.utils.SampleUtils;
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
import com.fr.dp.node.domain.entities.DataFlowLine;
import com.fr.dp.node.domain.entities.SparkSqlNode;
import com.fr.dp.node.domain.entities.SparkSqlNodeType;
import com.fr.dp.node.domain.entities.api.DataFlowNode;
import com.fr.dp.node.domain.entities.api.DataFlowNodeType;
import com.fr.dp.node.domain.entities.api.ICoordinateDataFlowNode;
import com.fr.dp.node.domain.entities.api.IDataFlowLine;
import com.fr.dp.node.domain.entities.transaction.DefaultTransactionStrategy;
import com.fr.dp.datasource.dto.FieldTransferBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.DBNodeFromInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.DBNodeToInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.DataFlowTransformInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.ExpressionFieldInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.FieldLineageInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.FilterFieldInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.LookupFieldInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.single.DefaultSingleXMLNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.service.FDLService;
import com.fr.stable.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TransformSyncTaskNodeBean extends DefaultSingleXMLNodeBean {
    private DBNodeFromInfoBean fromInfo;
    private DBNodeToInfoBean toInfo;
    private FieldLineageInfo fieldLineageInfo;
    private DataFlowTransformInfo transformInfo;

    public TransformSyncTaskNodeBean(int x, int y, String name, String id,
                                     DBNodeFromInfoBean fromInfo,
                                     DBNodeToInfoBean toInfo,
                                     FieldLineageInfo fieldLineageInfo,
                                     DataFlowTransformInfo transformInfo) {
        super(x, y, name, id);
        this.fromInfo = fromInfo;
        this.toInfo = toInfo;
        this.fieldLineageInfo = fieldLineageInfo;
        this.transformInfo = transformInfo;
    }

    @Override
    public ComponentDTO getFDLComponentDTO() {
        return FDLService.INSTANCE.createComponentDTO(this.getX(), this.getY(), this.getFDLNodeType(), this.getFDLComponent());
    }

    @Override
    public Component getFDLComponent() {
        VisualizedComponent result = new VisualizedComponent();
        result.setId(this.getId());
        result.setName(this.getName());
        result.setNote(this.getVisibleLineageNote());
        result.setExecuteLogic("AND");
        result.setDisabled(false);

        List<ICoordinateDataFlowNode> nodes = new ArrayList<ICoordinateDataFlowNode>();
        List<IDataFlowLine> lines = new ArrayList<IDataFlowLine>();

        CoordinateDataFlowNode reader = this.createDBReaderNode("reader", this.fromInfo, this.getVisibleLineageNote());
        reader.setX(100);
        reader.setY(120);
        nodes.add(reader);

        int lookupY = 20;
        for (DataFlowTransformInfo.LookupReadInfo lookupRead : this.transformInfo.getLookupReads()) {
            DBNodeFromInfoBean lookupFrom = DBNodeFromInfoBean.create()
                .datasourceType(lookupRead.getDatasourceType())
                .connectionName(lookupRead.getConnectionName())
                .sql(lookupRead.getSql());
            CoordinateDataFlowNode lookupNode = this.createDBReaderNode(lookupRead.getNodeName(), lookupFrom, lookupRead.getNote());
            lookupNode.setX(100);
            lookupNode.setY(lookupY);
            lookupY += 120;
            nodes.add(lookupNode);
        }

        CoordinateDataFlowNode transform = this.createSparkSqlNode();
        transform.setX(440);
        transform.setY(120);
        nodes.add(transform);

        CoordinateDataFlowNode writer = this.createDBWriteNode();
        writer.setX(780);
        writer.setY(120);
        nodes.add(writer);

        String transformId = transform.getNodeContent().getId();
        for (ICoordinateDataFlowNode node : nodes) {
            String nodeId = node.getNodeContent().getId();
            if (nodeId.equals(transformId) || nodeId.equals(writer.getNodeContent().getId())) {
                continue;
            }
            lines.add(this.createLine(nodeId, transformId));
        }
        lines.add(this.createLine(transformId, writer.getNodeContent().getId()));

        result.setNodes(nodes);
        result.setLines(lines);
        return result;
    }

    private CoordinateDataFlowNode createDBReaderNode(String nodeName, DBNodeFromInfoBean info, String note) {
        CoordinateDataFlowNode reader = new CoordinateDataFlowNode();
        reader.setCompareId(UUID.randomUUID().toString());
        reader.setNodeType((DataFlowNodeType)new DBReadNodeType());
        DBReadNode readNode = new DBReadNode();
        readNode.setFromDatasourceType(info.getDatasourceType());
        readNode.setFromConnectionName(info.getConnectionName());
        SQLConfig databaseConfig = new SQLConfig();
        databaseConfig.setSql(info.getSql());
        databaseConfig.setSqlType(SqlExecutor.SqlType.SINGLE_SQL.getSqlTypeName());
        readNode.setDataBaseConfig((DataBaseConfig)databaseConfig);
        readNode.setSamples(SampleUtils.parseSampleUtils(new ArrayList()));
        readNode.setId(UUID.randomUUID().toString());
        readNode.setName(nodeName);
        readNode.setNote(note);
        reader.setNodeContent((DataFlowNode)readNode);
        reader.setCompareId(UUID.randomUUID().toString());
        return reader;
    }

    private CoordinateDataFlowNode createSparkSqlNode() {
        CoordinateDataFlowNode transformer = new CoordinateDataFlowNode();
        transformer.setCompareId(UUID.randomUUID().toString());
        transformer.setNodeType((DataFlowNodeType)new SparkSqlNodeType());
        SparkSqlNode node = new SparkSqlNode();
        node.setSql(this.transformInfo.getSparkSql());
        node.setId(UUID.randomUUID().toString());
        node.setName(this.transformInfo.getTransformNodeName());
        node.setNote(this.transformInfo.getNote());
        transformer.setNodeContent((DataFlowNode)node);
        return transformer;
    }

    private CoordinateDataFlowNode createDBWriteNode() {
        CoordinateDataFlowNode writer = new CoordinateDataFlowNode();
        writer.setNodeType((DataFlowNodeType)new DBWriteNodeType());
        DBWriteNode writeNode = new DBWriteNode();
        writeNode.setToDatasourceType(this.toInfo.getDatasourceType());
        writeNode.setToConnectionName(this.toInfo.getConnectionName());
        List<FieldTransferBean> fieldTransferItems = this.createFieldTransferItems();
        writeNode.setFieldTransferItems(fieldTransferItems);
        FdlWriteConfigHelper.applyWriteConfig(writeNode, fieldTransferItems, this.toInfo.getTargetWriteInfo());
        writeNode.setToDatabase(this.toInfo.getDatabaseName());
        writeNode.setToSchema(this.toInfo.getSchema());
        writeNode.setToTableMode(0);
        writeNode.setToTable(this.toInfo.getTableName());
        writeNode.setComment("");
        writeNode.setMapType("NAME");
        writeNode.setTransaction(new DefaultTransactionStrategy());
        writeNode.setId(UUID.randomUUID().toString());
        writeNode.setName("writer");
        writeNode.setNote(this.getVisibleLineageNote());
        writer.setNodeContent((DataFlowNode)writeNode);
        writer.setCompareId(UUID.randomUUID().toString());
        return writer;
    }

    private List<FieldTransferBean> createFieldTransferItems() {
        List<FieldTransferBean> result = FieldTransferFactory.createForTransform(this.fieldLineageInfo,
            this.toInfo == null ? null : this.toInfo.getTargetWriteInfo(), this.transformInfo.getOutputFields());
        for (FieldTransferBean item : result) {
            this.applyLineageComment(item.getWriteColumn());
        }
        return result;
    }

    private void applyLineageComment(SqlFieldBean targetFieldBean) {
        if (targetFieldBean == null || this.fieldLineageInfo == null || !this.fieldLineageInfo.hasVisibleInfos()) {
            return;
        }
        List<ExpressionFieldInfo> expressionInfos = this.fieldLineageInfo.getExpressionInfos(targetFieldBean.getName());
        List<FilterFieldInfo> filterInfos = this.fieldLineageInfo.getFilterInfos(targetFieldBean.getName());
        List<LookupFieldInfo> lookupInfos = this.fieldLineageInfo.getLookupInfos(targetFieldBean.getName());
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

    private DataFlowLine createLine(String from, String to) {
        DataFlowLine line = new DataFlowLine();
        line.setFrom(from);
        line.setTo(to);
        return line;
    }

    @Override
    public String getFDLNodeType() {
        return "DATA_FLOW";
    }

    private String getVisibleLineageNote() {
        StringBuilder note = new StringBuilder();
        if (this.fieldLineageInfo != null && StringUtils.isNotEmpty(this.fieldLineageInfo.getVisibleNote())) {
            note.append(this.fieldLineageInfo.getVisibleNote());
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
