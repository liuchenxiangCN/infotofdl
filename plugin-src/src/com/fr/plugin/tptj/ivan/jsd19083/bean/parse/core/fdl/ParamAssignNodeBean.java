package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl;

import com.fr.dp.component.domain.entities.ParamAssignmentComponent;
import com.fr.dp.datasource.ds.utils.SampleUtils;
import com.fr.dp.datasource.entity.DataBaseConfig;
import com.fr.dp.datasource.entity.SQLConfig;
import com.fr.dp.datasource.executor.SqlExecutor;
import com.fr.dp.devwork.work.component.api.Component;
import com.fr.dp.devwork.work.component.dto.ComponentDTO;
import com.fr.dp.node.domain.entities.CoordinateDataFlowNode;
import com.fr.dp.node.domain.entities.DBReadNode;
import com.fr.dp.node.domain.entities.DBReadNodeType;
import com.fr.dp.node.domain.entities.ParamWriteNode;
import com.fr.dp.node.domain.entities.ParamWriteNodeType;
import com.fr.dp.node.domain.entities.api.DataFlowNode;
import com.fr.dp.node.domain.entities.api.ICoordinateDataFlowNode;
import com.fr.dp.node.domain.valueobject.ParamOutputEntity;
import com.fr.dp.param.ParamMatchType;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.single.DefaultSingleXMLNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.service.FDLService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

public class ParamAssignNodeBean
extends DefaultSingleXMLNodeBean {
    private String connectionName;
    private String connectionType;
    private String sql;
    private String paramName;
    private String paramValue;
    private String paramValueStatus;
    private String paramValueSource;

    public ParamAssignNodeBean(int x, int y, String name, String id,
                               String connectionName, String connectionType,
                               String sql, String paramName) {
        this(x, y, name, id, connectionName, connectionType, sql, paramName,
            InfaScopedParameterResolver.runtimePlaceholder("PARAM_ASSIGN", paramName),
            InfaScopedParameterResolver.STATUS_RUNTIME_PLACEHOLDER,
            InfaScopedParameterResolver.DEFAULT_RUNTIME_PLACEHOLDER);
    }

    public ParamAssignNodeBean(int x, int y, String name, String id,
                               String connectionName, String connectionType,
                               String sql, String paramName, String paramValue,
                               String paramValueStatus, String paramValueSource) {
        super(x, y, name, id);
        this.connectionName = connectionName;
        this.connectionType = connectionType;
        this.sql = sql;
        this.paramName = paramName;
        this.paramValue = paramValue == null
            ? InfaScopedParameterResolver.runtimePlaceholder("PARAM_ASSIGN", paramName)
            : paramValue;
        this.paramValueStatus = isNotEmpty(paramValueStatus)
            ? paramValueStatus
            : InfaScopedParameterResolver.STATUS_RUNTIME_PLACEHOLDER;
        this.paramValueSource = isNotEmpty(paramValueSource)
            ? paramValueSource
            : InfaScopedParameterResolver.DEFAULT_RUNTIME_PLACEHOLDER;
    }

    @Override
    public ComponentDTO getFDLComponentDTO() {
        return FDLService.INSTANCE.createComponentDTO(this.getX(), this.getY(), this.getFDLNodeType(), this.getFDLComponent());
    }

    @Override
    public Component getFDLComponent() {
        ParamAssignmentComponent result = new ParamAssignmentComponent();
        result.setId(this.getId());
        result.setName(this.getName());
        result.setNote(createNote());
        result.setExecuteLogic("AND");
        result.setDisabled(false);
        result.setReader(this.getReaderNode());
        result.setOutput(this.getOutputNode());
        return result;
    }

    private String createNote() {
        StringBuilder note = new StringBuilder();
        note.append("Informatica parameter assignment.");
        note.append("\nParameter value status: ").append(this.paramValueStatus);
        note.append("\nParameter value source: ").append(this.paramValueSource);
        if (InfaScopedParameterResolver.STATUS_RUNTIME_PLACEHOLDER.equals(this.paramValueStatus)) {
            note.append("\nRuntime value must be supplied by scoped resolver or adapter binding; no value is inferred from the parameter name.");
        }
        return note.toString();
    }

    private ICoordinateDataFlowNode getReaderNode() {
        CoordinateDataFlowNode reader = new CoordinateDataFlowNode();
        reader.setCompareId(UUID.randomUUID().toString());
        reader.setNodeType(new DBReadNodeType());
        DBReadNode readNode = new DBReadNode();
        readNode.setFromDatasourceType(this.connectionType);
        readNode.setFromConnectionName(this.connectionName);
        SQLConfig databaseConfig = new SQLConfig();
        databaseConfig.setSql(this.sql);
        databaseConfig.setSqlType(SqlExecutor.SqlType.SINGLE_SQL.getSqlTypeName());
        readNode.setDataBaseConfig((DataBaseConfig) databaseConfig);
        readNode.setSamples(SampleUtils.parseSampleUtils(new ArrayList()));
        readNode.setId(UUID.randomUUID().toString());
        readNode.setName("param-reader");
        readNode.setNote("");
        reader.setNodeContent((DataFlowNode) readNode);
        return reader;
    }

    private ICoordinateDataFlowNode getOutputNode() {
        CoordinateDataFlowNode output = new CoordinateDataFlowNode();
        output.setCompareId(UUID.randomUUID().toString());
        output.setNodeType(new ParamWriteNodeType());
        ParamWriteNode writeNode = new ParamWriteNode();
        ParamOutputEntity entity = new ParamOutputEntity();
        entity.setParamName(this.paramName);
        entity.setParamValue(this.paramValue);
        entity.setColumn(1);
        entity.setRow(1);
        entity.setColumnMatchType(ParamMatchType.COLUMN_INDEX);
        writeNode.setOutputParamEntities(Collections.singletonList(entity));
        writeNode.setId(UUID.randomUUID().toString());
        writeNode.setName("param-writer");
        writeNode.setNote("");
        output.setNodeContent((DataFlowNode) writeNode);
        return output;
    }

    @Override
    public String getFDLNodeType() {
        return "PARAM_ASSIGN";
    }

    private static boolean isNotEmpty(String value) {
        return value != null && value.trim().length() > 0;
    }
}
