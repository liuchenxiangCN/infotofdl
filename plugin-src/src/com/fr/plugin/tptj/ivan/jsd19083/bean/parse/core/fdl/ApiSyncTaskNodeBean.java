package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl;

import com.fr.dp.component.domain.entities.VisualizedComponent;
import com.fr.dp.datasource.ds.utils.SampleUtils;
import com.fr.dp.datasource.dto.FieldTransferBean;
import com.fr.dp.datasource.entity.DataBaseConfig;
import com.fr.dp.datasource.entity.SQLConfig;
import com.fr.dp.datasource.executor.SqlExecutor;
import com.fr.dp.datasource.general.base.SqlFieldBean;
import com.fr.dp.devwork.work.component.api.Component;
import com.fr.dp.devwork.work.component.dto.ComponentDTO;
import com.fr.dp.http.conf.EmptyRequestBodyContent;
import com.fr.dp.http.conf.FDLApiReadHttpResponseConf;
import com.fr.dp.http.conf.FDLHttpRequestConf;
import com.fr.dp.http.conf.FormatType;
import com.fr.dp.http.conf.RequestAuthorization;
import com.fr.dp.http.conf.RequestBody;
import com.fr.dp.http.conf.RequestBodyStringContent;
import com.fr.dp.http.conf.RequestFrequency;
import com.fr.dp.http.conf.SSLConf;
import com.fr.dp.http.conf.SeniorConf;
import com.fr.dp.http.conf.pagination.Pagination;
import com.fr.dp.node.domain.entities.ApiReadNode;
import com.fr.dp.node.domain.entities.ApiReadNodeType;
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
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.api.ApiNodeFromInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.DBNodeFromInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.DBNodeToInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.DataFlowTransformInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.ExpressionFieldInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.FieldLineageInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.FilterFieldInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.LookupFieldInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.single.DefaultSingleXMLNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.service.FDLService;
import com.fr.plugin.tptj.ivan.jsd19083.util.DBFieldTransferUtils;
import com.fr.stable.StringUtils;
import com.fr.third.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiSyncTaskNodeBean extends DefaultSingleXMLNodeBean {
    private static final String ORACLE_NEXTVAL_ADAPTER_FUNCTION = "INFA_ORACLE_NEXTVAL";
    private static final Pattern ORACLE_SEQUENCE_NEXTVAL_PATTERN = Pattern.compile(
        "(?i)(?<![A-Za-z0-9_$#])((?:[A-Za-z_][A-Za-z0-9_$#]*\\.)?[A-Za-z_][A-Za-z0-9_$#]*)\\s*\\.\\s*nextval\\b");

    private ApiNodeFromInfoBean fromInfo;
    private DBNodeToInfoBean toInfo;
    private DBNodeFromInfoBean sourceInfo;
    private FieldLineageInfo fieldLineageInfo;
    private DataFlowTransformInfo postTransformInfo;

    public ApiSyncTaskNodeBean(int x, int y, String name, String id,
                               ApiNodeFromInfoBean fromInfo, DBNodeToInfoBean toInfo) {
        super(x, y, name, id);
        this.fromInfo = fromInfo;
        this.toInfo = toInfo;
    }

    public ApiSyncTaskNodeBean(int x, int y, String name, String id,
                               ApiNodeFromInfoBean fromInfo,
                               DBNodeToInfoBean toInfo,
                               DBNodeFromInfoBean sourceInfo,
                               FieldLineageInfo fieldLineageInfo,
                               DataFlowTransformInfo postTransformInfo) {
        this(x, y, name, id, fromInfo, toInfo);
        this.sourceInfo = sourceInfo;
        this.fieldLineageInfo = fieldLineageInfo;
        this.postTransformInfo = postTransformInfo;
    }

    public ApiNodeFromInfoBean getFromInfo() {
        return fromInfo;
    }

    public DBNodeToInfoBean getToInfo() {
        return toInfo;
    }

    @Override
    public ComponentDTO getFDLComponentDTO() {
        return FDLService.INSTANCE.createComponentDTO(
            this.getX(), this.getY(), this.getFDLNodeType(), this.getFDLComponent());
    }

    @Override
    public Component getFDLComponent() {
        if (this.hasRowApiUdfProjectionTransform()) {
            return this.getRowApiUdfProjectionFDLComponent();
        }
        if (this.hasPostProjectionTransform()) {
            return this.getPostProjectionFDLComponent();
        }
        return this.getDirectApiFDLComponent();
    }

    private Component getDirectApiFDLComponent() {
        VisualizedComponent result = new VisualizedComponent();
        result.setId(this.getId());
        result.setName(this.getName());
        result.setNote(this.getDirectApiNote());
        result.setExecuteLogic("AND");
        result.setDisabled(false);

        CoordinateDataFlowNode reader = this.getApiReaderNode();
        reader.setX(100);
        reader.setY(100);
        CoordinateDataFlowNode writer = this.getDBWriteNode();
        writer.setX(400);
        writer.setY(100);

        // Set field transfer items from read columns to target columns
        DBWriteNode dbWriteNode = (DBWriteNode) writer.getNodeContent();
        ApiReadNode apiReadNode = (ApiReadNode) reader.getNodeContent();
        List<SqlFieldBean> readColumns = apiReadNode.getReadColumns();
        if (readColumns != null && !readColumns.isEmpty()) {
            List<FieldTransferBean> fieldTransferItems = new ArrayList<FieldTransferBean>();
            for (SqlFieldBean readCol : readColumns) {
                String targetFieldName = fromInfo.getOutputFieldTarget(readCol.getName());
                if (!StringUtils.isNotEmpty(targetFieldName)) {
                    targetFieldName = readCol.getName();
                }
                SqlFieldBean writeCol = FieldTransferFactory.createTargetField(this.fieldLineageInfo,
                    this.toInfo == null ? null : this.toInfo.getTargetWriteInfo(), targetFieldName, readCol);
                this.appendFieldComment(writeCol, this.getAdapterFieldMetadataForOutput(readCol.getName()));
                fieldTransferItems.add(new FieldTransferBean(readCol, writeCol, false));
            }
            dbWriteNode.setFieldTransferItems(fieldTransferItems);
        }
        FdlWriteConfigHelper.applyWriteConfig(dbWriteNode, dbWriteNode.getFieldTransferItems(), this.getToInfo().getTargetWriteInfo());
        writer.setNodeContent((DataFlowNode) dbWriteNode);

        List<ICoordinateDataFlowNode> nodes = new ArrayList<ICoordinateDataFlowNode>();
        nodes.add(reader);
        nodes.add(writer);
        result.setNodes(nodes);

        DataFlowLine line = new DataFlowLine();
        line.setFrom(reader.getNodeContent().getId());
        line.setTo(writer.getNodeContent().getId());
        result.setLines(java.util.Collections.singletonList(line));

        return result;
    }

    private Component getRowApiUdfProjectionFDLComponent() {
        VisualizedComponent result = new VisualizedComponent();
        result.setId(this.getId());
        result.setName(this.getName());
        result.setNote(this.getPostProjectionNote());
        result.setExecuteLogic("AND");
        result.setDisabled(false);

        List<ICoordinateDataFlowNode> nodes = new ArrayList<ICoordinateDataFlowNode>();
        List<IDataFlowLine> lines = new ArrayList<IDataFlowLine>();

        CoordinateDataFlowNode sourceReader = this.getSourceReaderNode();
        sourceReader.setX(100);
        sourceReader.setY(140);
        nodes.add(sourceReader);

        CoordinateDataFlowNode rowApiAdapter = this.getRowApiAdapterSparkSqlNode();
        rowApiAdapter.setX(400);
        rowApiAdapter.setY(140);
        nodes.add(rowApiAdapter);

        CoordinateDataFlowNode transform = this.getPostProjectionSparkSqlNode();
        transform.setX(700);
        transform.setY(140);
        nodes.add(transform);

        CoordinateDataFlowNode writer = this.getDBWriteNode();
        writer.setX(1000);
        writer.setY(140);
        DBWriteNode dbWriteNode = (DBWriteNode) writer.getNodeContent();
        dbWriteNode.setFieldTransferItems(this.createPostProjectionFieldTransferItems());
        writer.setNodeContent((DataFlowNode) dbWriteNode);
        nodes.add(writer);

        String sourceId = sourceReader.getNodeContent().getId();
        String rowApiAdapterId = rowApiAdapter.getNodeContent().getId();
        String transformId = transform.getNodeContent().getId();
        String writerId = writer.getNodeContent().getId();
        lines.add(this.createLine(sourceId, rowApiAdapterId));
        lines.add(this.createLine(rowApiAdapterId, transformId));
        lines.add(this.createLine(transformId, writerId));

        result.setNodes(nodes);
        result.setLines(lines);
        return result;
    }

    private Component getPostProjectionFDLComponent() {
        VisualizedComponent result = new VisualizedComponent();
        result.setId(this.getId());
        result.setName(this.getName());
        result.setNote(this.getPostProjectionNote());
        result.setExecuteLogic("AND");
        result.setDisabled(false);

        List<ICoordinateDataFlowNode> nodes = new ArrayList<ICoordinateDataFlowNode>();
        List<IDataFlowLine> lines = new ArrayList<IDataFlowLine>();

        CoordinateDataFlowNode sourceReader = this.getSourceReaderNode();
        sourceReader.setX(100);
        sourceReader.setY(180);
        nodes.add(sourceReader);

        CoordinateDataFlowNode apiReader = this.getApiReaderNode("api_reader");
        apiReader.setX(380);
        apiReader.setY(60);
        nodes.add(apiReader);

        CoordinateDataFlowNode transform = this.getPostProjectionSparkSqlNode();
        transform.setX(660);
        transform.setY(140);
        nodes.add(transform);

        CoordinateDataFlowNode writer = this.getDBWriteNode();
        writer.setX(960);
        writer.setY(140);
        DBWriteNode dbWriteNode = (DBWriteNode) writer.getNodeContent();
        dbWriteNode.setFieldTransferItems(this.createPostProjectionFieldTransferItems());
        writer.setNodeContent((DataFlowNode) dbWriteNode);
        nodes.add(writer);

        String sourceId = sourceReader.getNodeContent().getId();
        String apiId = apiReader.getNodeContent().getId();
        String transformId = transform.getNodeContent().getId();
        String writerId = writer.getNodeContent().getId();
        lines.add(this.createLine(sourceId, transformId));
        lines.add(this.createLine(apiId, transformId));
        lines.add(this.createLine(transformId, writerId));

        result.setNodes(nodes);
        result.setLines(lines);
        return result;
    }

    private CoordinateDataFlowNode getApiReaderNode() {
        String nodeName = StringUtils.isNotEmpty(fromInfo.getSourceTransformationName())
            ? fromInfo.getSourceTransformationName() : "api_reader";
        return this.getApiReaderNode(nodeName);
    }

    private CoordinateDataFlowNode getApiReaderNode(String nodeName) {
        CoordinateDataFlowNode reader = new CoordinateDataFlowNode();
        reader.setCompareId(UUID.randomUUID().toString());
        reader.setNodeType((DataFlowNodeType) new ApiReadNodeType());
        ApiReadNode readNode = new ApiReadNode();
        readNode.setAutoFetchInputField(false);

        FDLHttpRequestConf requestConf = new FDLHttpRequestConf();
        requestConf.setUrl(fromInfo.getUrl());
        requestConf.setRequestMethod(resolveRequestMethod(fromInfo.getMethod()));

        List<Map<String, String>> headers = new ArrayList<Map<String, String>>();
        for (Map<String, String> header : fromInfo.getHeaders()) {
            Map<String, String> h = new HashMap<String, String>();
            h.put("name", header.get("name"));
            h.put("value", header.get("value"));
            headers.add(h);
        }
        requestConf.setHeaders(headers);

        if (StringUtils.isNotEmpty(fromInfo.getBodyTemplate())) {
            RequestBodyStringContent bodyContent = new RequestBodyStringContent();
            bodyContent.setContent(fromInfo.getBodyTemplate());
            requestConf.setBody(new RequestBody(bodyContent, resolveRequestBodyType(fromInfo.getBodyType())));
        } else {
            requestConf.setBody(new RequestBody(new EmptyRequestBodyContent(), resolveRequestBodyType(fromInfo.getBodyType())));
        }
        requestConf.setAuthorization(new RequestAuthorization(RequestAuthorization.AuthMethod.NONE, null, null, null));
        requestConf.setSslConf(new SSLConf());
        SeniorConf seniorConf = new SeniorConf();
        seniorConf.setRequestFreq(RequestFrequency.createDefault());
        seniorConf.setPagination(Pagination.createDefault());
        requestConf.setSeniorConf(seniorConf);
        readNode.setRequestConf(requestConf);

        FDLApiReadHttpResponseConf responseConf = new FDLApiReadHttpResponseConf();
        FormatType responseFormatType = resolveResponseFormatType(fromInfo.getResponseFormat());
        if (responseFormatType != null) {
            responseConf.setFormatType(responseFormatType);
        }
        responseConf.setBodyName(fromInfo.getResponseBodyName());
        responseConf.setCharset(fromInfo.getCharset());
        responseConf.setResponseOptions(new LinkedHashSet<String>(fromInfo.getResponseOptions()));
        responseConf.setHeaderKey(new HashSet<String>());
        readNode.setResponseConf(responseConf);

        List<SqlFieldBean> readColumns = new ArrayList<SqlFieldBean>();
        for (String fieldName : fromInfo.getOutputFields()) {
            String typeName = fromInfo.getOutputFieldType(fieldName);
            SqlFieldBean field = FieldTransferFactory.createLogicalField(fieldName,
                StringUtils.isNotEmpty(typeName) ? typeName : "string");
            this.appendFieldComment(field, this.getAdapterFieldMetadataForOutput(fieldName));
            readColumns.add(field);
        }
        readNode.setReadColumns(readColumns);
        readNode.setRenameColumns(new HashMap<String, String>());
        readNode.setSamples(new ArrayList());

        readNode.setId(UUID.randomUUID().toString());
        readNode.setName(StringUtils.isNotEmpty(nodeName) ? nodeName : "api_reader");
        readNode.setNote(this.hasPostProjectionTransform() ? this.getPostProjectionNote() : fromInfo.getVisibleNote());
        reader.setNodeContent((DataFlowNode) readNode);
        reader.setCompareId(UUID.randomUUID().toString());
        return reader;
    }

    private CoordinateDataFlowNode getSourceReaderNode() {
        CoordinateDataFlowNode reader = new CoordinateDataFlowNode();
        reader.setCompareId(UUID.randomUUID().toString());
        reader.setNodeType((DataFlowNodeType)new DBReadNodeType());
        DBReadNode readNode = new DBReadNode();
        readNode.setFromDatasourceType(this.sourceInfo.getDatasourceType());
        readNode.setFromConnectionName(this.sourceInfo.getConnectionName());
        SQLConfig databaseConfig = new SQLConfig();
        databaseConfig.setSql(this.getFdlSafeSourceSql());
        databaseConfig.setSqlType(SqlExecutor.SqlType.SINGLE_SQL.getSqlTypeName());
        readNode.setDataBaseConfig((DataBaseConfig)databaseConfig);
        readNode.setSamples(SampleUtils.parseSampleUtils(new ArrayList()));
        readNode.setId(UUID.randomUUID().toString());
        readNode.setName("source_reader");
        readNode.setNote(this.getPostProjectionNote());
        reader.setNodeContent((DataFlowNode)readNode);
        reader.setCompareId(UUID.randomUUID().toString());
        return reader;
    }

    private CoordinateDataFlowNode getRowApiAdapterSparkSqlNode() {
        CoordinateDataFlowNode transformer = new CoordinateDataFlowNode();
        transformer.setCompareId(UUID.randomUUID().toString());
        transformer.setNodeType((DataFlowNodeType)new SparkSqlNodeType());
        SparkSqlNode node = new SparkSqlNode();
        node.setSql(this.createRowApiAdapterSparkSql());
        node.setId(UUID.randomUUID().toString());
        node.setName("row_api_adapter");
        node.setNote(this.getRowApiAdapterNote());
        transformer.setNodeContent((DataFlowNode)node);
        return transformer;
    }

    private CoordinateDataFlowNode getPostProjectionSparkSqlNode() {
        CoordinateDataFlowNode transformer = new CoordinateDataFlowNode();
        transformer.setCompareId(UUID.randomUUID().toString());
        transformer.setNodeType((DataFlowNodeType)new SparkSqlNodeType());
        SparkSqlNode node = new SparkSqlNode();
        node.setSql(this.postTransformInfo.getSparkSql());
        node.setId(UUID.randomUUID().toString());
        node.setName(this.postTransformInfo.getTransformNodeName());
        node.setNote(this.postTransformInfo.getNote());
        transformer.setNodeContent((DataFlowNode)node);
        return transformer;
    }

    private CoordinateDataFlowNode getDBWriteNode() {
        CoordinateDataFlowNode writer = new CoordinateDataFlowNode();
        writer.setNodeType((DataFlowNodeType) new DBWriteNodeType());
        DBWriteNode writeNode = new DBWriteNode();
        writeNode.setToDatasourceType(this.getToInfo().getDatasourceType());
        writeNode.setToConnectionName(this.getToInfo().getConnectionName());
        writeNode.setFieldTransferItems(new ArrayList());
        FdlWriteConfigHelper.applyWriteConfig(writeNode, new ArrayList<FieldTransferBean>(), this.getToInfo().getTargetWriteInfo());
        writeNode.setToDatabase(this.getToInfo().getDatabaseName());
        writeNode.setToSchema(this.getToInfo().getSchema());
        writeNode.setToTableMode(0);
        writeNode.setToTable(this.getToInfo().getTableName());
        writeNode.setComment("");
        writeNode.setMapType("NAME");
        writeNode.setTransaction(new DefaultTransactionStrategy());
        writeNode.setId(UUID.randomUUID().toString());
        writeNode.setName("writer");
        writeNode.setNote(this.hasPostProjectionTransform() ? this.getPostProjectionNote() : this.getDirectApiNote());
        writer.setNodeContent((DataFlowNode) writeNode);
        writer.setCompareId(UUID.randomUUID().toString());
        return writer;
    }

    private List<FieldTransferBean> createPostProjectionFieldTransferItems() {
        List<FieldTransferBean> result = FieldTransferFactory.createForTransform(this.fieldLineageInfo,
            this.toInfo == null ? null : this.toInfo.getTargetWriteInfo(), this.postTransformInfo.getOutputFields());
        for (FieldTransferBean item : result) {
            this.applyLineageComment(item.getWriteColumn());
            this.appendFieldComment(item.getWriteColumn(), this.getAdapterFieldMetadataForTarget(item.getWriteColumn().getName()));
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

    private boolean hasPostProjectionTransform() {
        return this.sourceInfo != null && this.fromInfo != null && this.fromInfo.isFdlApiNodeSupported()
            && this.postTransformInfo != null && this.postTransformInfo.isExecutable();
    }

    private boolean hasRowApiUdfProjectionTransform() {
        return this.sourceInfo != null && this.fromInfo != null && this.fromInfo.isFdlRowApiUdfSupported()
            && this.postTransformInfo != null && this.postTransformInfo.isExecutable()
            && this.postTransformInfo.isRowApiUdfAdapter();
    }

    private String getPostProjectionNote() {
        StringBuilder note = new StringBuilder();
        note.append(fromInfo.getVisibleNote());
        if (this.postTransformInfo != null && StringUtils.isNotEmpty(this.postTransformInfo.getNote())) {
            note.append("\n").append(this.postTransformInfo.getNote());
        }
        if (this.postTransformInfo != null && StringUtils.isNotEmpty(this.postTransformInfo.getApiMergeRelation())) {
            note.append("\nMerge relation: ").append(this.postTransformInfo.getApiMergeRelation());
        }
        String sequenceAdapterNote = this.getOracleSequenceAdapterNote();
        if (StringUtils.isNotEmpty(sequenceAdapterNote)) {
            note.append("\n").append(sequenceAdapterNote);
        }
        String targetWriteNote = FdlWriteConfigHelper.targetWriteNote(this.getToInfo() == null ? null : this.getToInfo().getTargetWriteInfo());
        if (StringUtils.isNotEmpty(targetWriteNote)) {
            note.append("\n").append(targetWriteNote);
        }
        return note.toString();
    }

    private String getDirectApiNote() {
        StringBuilder note = new StringBuilder();
        if (fromInfo != null && StringUtils.isNotEmpty(fromInfo.getVisibleNote())) {
            note.append(fromInfo.getVisibleNote());
        }
        String targetWriteNote = FdlWriteConfigHelper.targetWriteNote(this.getToInfo() == null ? null : this.getToInfo().getTargetWriteInfo());
        if (StringUtils.isNotEmpty(targetWriteNote)) {
            if (note.length() > 0) {
                note.append("\n");
            }
            note.append(targetWriteNote);
        }
        return note.toString();
    }

    private String getRowApiAdapterNote() {
        StringBuilder note = new StringBuilder();
        note.append("Informatica Java Transformation generated as FDL Spark UDF row API adapter.");
        note.append("\nFunction: ").append(ApiNodeFromInfoBean.ROW_API_UDF_SPARK_FUNCTION);
        note.append("\nProvider function: ").append(ApiNodeFromInfoBean.ROW_API_UDF_PROVIDER_FUNCTION);
        note.append("\nTransformation: ").append(fromInfo.getSourceTransformationName());
        note.append("\nInvocation condition: ").append(fromInfo.getInvocationCondition());
        note.append("\nMethod: ").append(fromInfo.getMethod());
        note.append("\nURL: ").append(fromInfo.getUrl());
        note.append("\nOutput fields: ");
        List<String> outputFields = fromInfo.getOutputFields();
        for (int i = 0; i < outputFields.size(); i++) {
            if (i > 0) {
                note.append(", ");
            }
            note.append(outputFields.get(i));
        }
        note.append("\nNo ApiReadNode is generated; this adapter executes inside SparkSqlNode per input row.");
        String sequenceAdapterNote = this.getOracleSequenceAdapterNote();
        if (StringUtils.isNotEmpty(sequenceAdapterNote)) {
            note.append("\n").append(sequenceAdapterNote);
        }
        return note.toString();
    }

    private String getFdlSafeSourceSql() {
        String sql = this.sourceInfo == null ? "" : this.sourceInfo.getSql();
        if (!this.requiresNestedOracleSequenceAdapter()) {
            return sql;
        }
        return rewriteOracleSequenceNextvalForNestedQuery(sql);
    }

    private String getOracleSequenceAdapterNote() {
        if (!this.requiresNestedOracleSequenceAdapter()) {
            return "";
        }
        return "FDL_ORACLE_SEQUENCE_ADAPTER function=" + ORACLE_NEXTVAL_ADAPTER_FUNCTION
            + "; reason=Oracle NEXTVAL cannot be evaluated from the nested DBRead query generated for downstream SparkSqlNode; install this helper function in the source schema.";
    }

    private boolean requiresNestedOracleSequenceAdapter() {
        return this.sourceInfo != null
            && "oracle".equalsIgnoreCase(this.sourceInfo.getDatasourceType())
            && this.hasRowApiUdfProjectionTransform()
            && containsOracleSequenceNextval(this.sourceInfo.getSql());
    }

    private static boolean containsOracleSequenceNextval(String sql) {
        return StringUtils.isNotEmpty(sql) && ORACLE_SEQUENCE_NEXTVAL_PATTERN.matcher(sql).find();
    }

    private static String rewriteOracleSequenceNextvalForNestedQuery(String sql) {
        if (!StringUtils.isNotEmpty(sql)) {
            return sql;
        }
        Matcher matcher = ORACLE_SEQUENCE_NEXTVAL_PATTERN.matcher(sql);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String sequenceName = matcher.group(1);
            String replacement = ORACLE_NEXTVAL_ADAPTER_FUNCTION + "('" + sequenceName.replace("'", "''") + "')";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String createRowApiAdapterSparkSql() {
        ApiNodeFromInfoBean.HttpStatusOutputBinding binding = this.fromInfo.getHttpStatusOutputBindings().get(0);
        String condition = this.toSparkInvocationCondition(this.fromInfo.getRowInvocationBinding().getCondition());
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT source_row_.*,");
        sql.append("\n       ").append(ApiNodeFromInfoBean.ROW_API_UDF_SPARK_FUNCTION).append("(");
        sql.append("\n         CASE WHEN ").append(condition).append(" THEN '1' ELSE '0' END,");
        sql.append("\n         ").append(sparkSqlLiteral(this.fromInfo.getMethod())).append(",");
        sql.append("\n         ").append(sparkSqlLiteral(this.fromInfo.getUrl())).append(",");
        sql.append("\n         ").append(sparkSqlLiteral(this.fromInfo.getBodyTemplate())).append(",");
        sql.append("\n         ").append(sparkSqlLiteral(this.findHeaderValue("Content-Type"))).append(",");
        sql.append("\n         ").append(sparkSqlLiteral(this.fromInfo.getSoapAction())).append(",");
        sql.append("\n         ").append(sparkSqlLiteral(this.fromInfo.getCharset())).append(",");
        sql.append("\n         ").append(sparkSqlLiteral(binding.getStatusCode())).append(",");
        sql.append("\n         ").append(sparkSqlLiteral(binding.getOutputValue())).append(",");
        sql.append("\n         ").append(sparkSqlLiteral(binding.getDefaultValue())).append(")");
        sql.append(" AS ").append(safeSparkIdentifier(binding.getOutputField()));
        sql.append("\n  FROM $[source_reader] source_row_");
        return sql.toString();
    }

    private String toSparkInvocationCondition(String condition) {
        String trimmed = condition == null ? "" : condition.trim();
        java.util.regex.Matcher literalEqualsField = java.util.regex.Pattern.compile(
            "^\"((?:\\\\.|[^\"])*)\"\\s*\\.\\s*equals\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)$").matcher(trimmed);
        if (literalEqualsField.matches()) {
            return "source_row_." + safeSparkIdentifier(literalEqualsField.group(2))
                + " = " + sparkSqlLiteral(unescapeJavaString(literalEqualsField.group(1)));
        }
        java.util.regex.Matcher fieldEqualsLiteral = java.util.regex.Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_]*)\\s*\\.\\s*equals\\s*\\(\\s*\"((?:\\\\.|[^\"])*)\"\\s*\\)$").matcher(trimmed);
        if (fieldEqualsLiteral.matches()) {
            return "source_row_." + safeSparkIdentifier(fieldEqualsLiteral.group(1))
                + " = " + sparkSqlLiteral(unescapeJavaString(fieldEqualsLiteral.group(2)));
        }
        java.util.regex.Matcher equality = java.util.regex.Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_]*)\\s*(?:==|=)\\s*\"((?:\\\\.|[^\"])*)\"$").matcher(trimmed);
        if (equality.matches()) {
            return "source_row_." + safeSparkIdentifier(equality.group(1))
                + " = " + sparkSqlLiteral(unescapeJavaString(equality.group(2)));
        }
        return "false";
    }

    private String findHeaderValue(String headerName) {
        if (headerName == null) {
            return "";
        }
        for (Map<String, String> header : fromInfo.getHeaders()) {
            if (headerName.equalsIgnoreCase(header.get("name"))) {
                return header.get("value");
            }
        }
        return "";
    }

    private static String safeSparkIdentifier(String value) {
        String safe = value == null ? "" : value.trim();
        return "`" + safe.replace("`", "``") + "`";
    }

    private static String sparkSqlLiteral(String value) {
        if (value == null) {
            value = "";
        }
        return "'" + value.replace("'", "''").replace("\r", "\\r").replace("\n", "\\n") + "'";
    }

    private static String unescapeJavaString(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\");
    }

    private String getAdapterFieldMetadataForOutput(String outputField) {
        return fromInfo == null ? "" : fromInfo.getAdapterContractFieldMetadata(outputField);
    }

    private String getAdapterFieldMetadataForTarget(String targetField) {
        if (fromInfo == null || !StringUtils.isNotEmpty(targetField)) {
            return "";
        }
        for (Map.Entry<String, String> entry : fromInfo.getOutputFieldTargets().entrySet()) {
            if (targetField.equalsIgnoreCase(entry.getValue())) {
                return fromInfo.getAdapterContractFieldMetadata(entry.getKey());
            }
        }
        return fromInfo.getAdapterContractFieldMetadata(targetField);
    }

    private void appendFieldComment(SqlFieldBean field, String metadata) {
        if (field == null || !StringUtils.isNotEmpty(metadata)) {
            return;
        }
        if (StringUtils.isNotEmpty(field.getComment())) {
            field.setComment(field.getComment() + "\n" + metadata);
        } else {
            field.setComment(metadata);
        }
    }

    @Override
    public String getFDLNodeType() {
        return "DATA_FLOW";
    }

    private static RequestMethod resolveRequestMethod(String method) {
        if (StringUtils.isNotEmpty(method)) {
            try {
                return RequestMethod.valueOf(method.toUpperCase());
            } catch (Exception ignored) {
            }
        }
        return RequestMethod.POST;
    }

    private static RequestBody.RequestBodyType resolveRequestBodyType(String bodyType) {
        String[] candidates = new String[] {
            bodyType,
            "TEXT",
            "RAW",
            "STRING",
            "PLAIN",
            "JSON"
        };
        for (String candidate : candidates) {
            if (!StringUtils.isNotEmpty(candidate)) {
                continue;
            }
            if ("XML".equalsIgnoreCase(candidate)) {
                continue;
            }
            try {
                return RequestBody.RequestBodyType.valueOf(candidate.toUpperCase());
            } catch (Exception ignored) {
            }
        }
        RequestBody.RequestBodyType[] values = RequestBody.RequestBodyType.values();
        for (RequestBody.RequestBodyType value : values) {
            if (value != null && !"XML".equalsIgnoreCase(value.name())) {
                return value;
            }
        }
        throw new IllegalStateException("No non-XML FDL request body type enum values are available");
    }

    private static FormatType resolveResponseFormatType(String responseFormat) {
        String[] candidates = new String[] {
            responseFormat,
            "TEXT",
            "RAW",
            "STRING",
            "JSON"
        };
        for (String candidate : candidates) {
            if (!StringUtils.isNotEmpty(candidate)) {
                continue;
            }
            if ("XML".equalsIgnoreCase(candidate)) {
                continue;
            }
            try {
                return FormatType.valueOf(candidate.toUpperCase());
            } catch (Exception ignored) {
            }
            if (!"JSON".equalsIgnoreCase(responseFormat)) {
                break;
            }
        }
        return null;
    }
}
