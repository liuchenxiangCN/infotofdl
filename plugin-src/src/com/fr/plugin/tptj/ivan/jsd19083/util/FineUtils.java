/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.fr.decision.webservice.bean.datasource.JDBCConnectionBean
 *  com.fr.decision.webservice.v10.datasource.connection.ConnectionService
 *  com.fr.decision.webservice.v10.user.UserService
 *  com.fr.stable.StringUtils
 */
package com.fr.plugin.tptj.ivan.jsd19083.util;

import com.fr.decision.webservice.bean.datasource.JDBCConnectionBean;
import com.fr.decision.webservice.v10.datasource.connection.ConnectionService;
import com.fr.decision.webservice.v10.user.UserService;
import com.fr.stable.StringUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FineUtils {
    public static String getAdminUserId() throws Exception {
        return (String)UserService.getInstance().getAdminUserIdList().get(0);
    }

    public static String getDefaultSchema(String connectionName) throws Exception {
        List<?> connections = ConnectionService.getInstance().getJDBCDatabaseConnections(FineUtils.getAdminUserId());
        for (Object obj : connections) {
            JDBCConnectionBean connection = (JDBCConnectionBean) obj;
            if (!StringUtils.equals((String)connection.getConnectionName(), (String)connectionName)) continue;
            return connection.getSchema();
        }
        return "";
    }

    public static String getDefaultDatabase(String connectionName) throws Exception {
        List<?> connections = ConnectionService.getInstance().getJDBCDatabaseConnections(FineUtils.getAdminUserId());
        for (Object obj : connections) {
            JDBCConnectionBean connection = (JDBCConnectionBean) obj;
            if (!StringUtils.equals((String)connection.getConnectionName(), (String)connectionName)) continue;
            String url = connection.getUrl();
            Map<String, String> jdbcUrlMap = FineUtils.parseJdbcUrl(url);
            return jdbcUrlMap.get("databaseName");
        }
        return "";
    }

    public static String getConnectionUserName(String connectionName) throws Exception {
        List<?> connections = ConnectionService.getInstance().getJDBCDatabaseConnections(FineUtils.getAdminUserId());
        for (Object obj : connections) {
            JDBCConnectionBean connection = (JDBCConnectionBean) obj;
            if (!StringUtils.equals((String)connection.getConnectionName(), (String)connectionName)) continue;
            return connection.getUser();
        }
        return "";
    }

    public static String getRandomJDBCConnectionName() throws Exception {
        List<?> connections = ConnectionService.getInstance().getJDBCDatabaseConnections(FineUtils.getAdminUserId());
        if (connections == null || connections.isEmpty()) {
            return "";
        }
        int randomIndex = new Random().nextInt(connections.size());
        JDBCConnectionBean connection = (JDBCConnectionBean) connections.get(randomIndex);
        return connection.getConnectionName();
    }

    public static String getConnectionType(String connectionName) throws Exception {
        List<?> connections = ConnectionService.getInstance().getJDBCDatabaseConnections(FineUtils.getAdminUserId());
        for (Object obj : connections) {
            JDBCConnectionBean connection = (JDBCConnectionBean) obj;
            if (!StringUtils.equals((String)connection.getConnectionName(), (String)connectionName)) continue;
            String declaredType = FineUtils.getDeclaredConnectionType(connection);
            if (StringUtils.isNotEmpty((String)declaredType)) {
                return declaredType;
            }
            String url = connection.getUrl();
            if (url != null) {
                String lower = url.toLowerCase();
                if (lower.contains("oracle")) return "Oracle";
                if (lower.contains("mysql")) return "MySQL";
                if (lower.contains("sqlserver")) return "SQL Server";
                if (lower.contains("postgresql")) return "PostgreSQL";
                if (lower.contains("sqlite")) return "SQLite";
                if (lower.contains("db2")) return "DB2";
                if (lower.contains("informix")) return "Informix";
                if (lower.contains("sybase")) return "Sybase";
                if (lower.contains("h2")) return "H2";
                if (lower.contains("opensearch")) return "OpenSearch";
                if (lower.contains("impala")) return "Impala";
                if (lower.contains("kylin")) return "Kylin";
                if (lower.contains("presto")) return "Presto";
            }
            return "";
        }
        return "";
    }

    private static String getDeclaredConnectionType(Object connection) {
        if (connection == null) {
            return "";
        }
        String[] methodNames = new String[] {
            "getDatasourceType", "getDataSourceType", "getDatabaseType", "getDataBaseType",
            "getDbType", "getDBType", "getType", "getDriverType"
        };
        for (String methodName : methodNames) {
            try {
                Object value = connection.getClass().getMethod(methodName).invoke(connection);
                if (value != null && StringUtils.isNotEmpty((String)value.toString())) {
                    return value.toString();
                }
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    public static Map<String, String> parseJdbcUrl(String jdbcUrl) {
        String[] patterns;
        HashMap<String, String> result = new HashMap<String, String>();
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            return result;
        }
        for (String pattern : patterns = new String[]{"^jdbc:mysql://([^:]+):(\\d+)/([^?&]+)", "^jdbc:postgresql://([^:]+):(\\d+)/([^?&]+)", "^jdbc:sqlserver://([^:]+):(\\d+);databaseName=([^;&]+)", "^jdbc:sqlserver://([^:]+):(\\d+)/([^?&]+)", "^jdbc:oracle:thin:@([^:]+):(\\d+)[:/]([^?&]+)", "^jdbc:db2://([^:]+):(\\d+)/([^?&]+)", "^jdbc:h2:tcp://([^:]+):(\\d+)/([^?&]+)", "^jdbc:opensearch://([^:]+):(\\d+)(?:/([^?&]+))?", "^jdbc:opensearch://https?://([^:]+):(\\d+)(?:/([^?&]+))?", "^jdbc:lk://([^:]+):(\\d+)/[^/]+/([^?&]+)", "^jdbc:lk://([^:]+):(\\d+)/([^/?&]+)", "^jdbc:impala://([^:]+):(\\d+)/([^,;?&]+)", "^jdbc:kylin://([^:]+):(\\d+)/([^?&]+)", "^jdbc:presto://([^:]+):(\\d+)/[^/]+/([^?&]+)", "^jdbc:presto://([^:]+):(\\d+)/([^/?&]+)"}) {
            Pattern regex = Pattern.compile(pattern);
            Matcher matcher = regex.matcher(jdbcUrl);
            if (!matcher.find()) continue;
            result.put("host", matcher.group(1));
            result.put("port", matcher.group(2));
            if (matcher.groupCount() >= 3 && matcher.group(3) != null) {
                result.put("databaseName", matcher.group(3));
                break;
            }
            result.put("databaseName", "");
            break;
        }
        return result;
    }
}
