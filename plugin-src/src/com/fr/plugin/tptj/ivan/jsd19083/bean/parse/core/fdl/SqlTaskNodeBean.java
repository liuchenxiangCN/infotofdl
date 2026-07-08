package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl;

import com.fr.dp.component.domain.entities.SqlComponent;
import com.fr.dp.datasource.entity.SQLConfig;
import com.fr.dp.datasource.executor.SqlExecutor;
import com.fr.dp.devwork.work.component.api.Component;
import com.fr.dp.devwork.work.component.dto.ComponentDTO;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.single.DefaultSingleXMLNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.service.FDLService;

public class SqlTaskNodeBean extends DefaultSingleXMLNodeBean {
    private String datasourceType;
    private String dbName;
    private String sql;

    public SqlTaskNodeBean(int x, int y, String name, String id, String datasourceType, String dbName, String sql) {
        super(x, y, name, id);
        this.datasourceType = datasourceType;
        this.dbName = dbName;
        this.sql = sql;
    }

    public String getDatasourceType() {
        return datasourceType;
    }

    public String getDbName() {
        return dbName;
    }

    public String getSql() {
        return sql;
    }

    @Override
    public ComponentDTO getFDLComponentDTO() {
        return FDLService.INSTANCE.createComponentDTO(this.getX(), this.getY(), this.getFDLNodeType(), this.getFDLComponent());
    }

    @Override
    public Component getFDLComponent() {
        SqlComponent result = new SqlComponent();
        result.setDatasourceType(this.getDatasourceType());
        result.setDbName(this.getDbName());
        SQLConfig databaseConfig = new SQLConfig();
        databaseConfig.setSql(this.getSql());
        databaseConfig.setSqlType(SqlExecutor.SqlType.SINGLE_SQL.getSqlTypeName());
        result.setDataBaseConfig(databaseConfig);
        result.setId(this.getId());
        result.setName(this.getName());
        result.setNote("");
        result.setExecuteLogic("AND");
        result.setDisabled(false);
        return result;
    }

    @Override
    public String getFDLNodeType() {
        return "SQL_DEVELOP";
    }
}
