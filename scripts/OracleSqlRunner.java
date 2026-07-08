import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class OracleSqlRunner {
    private static final int ORA_NAME_ALREADY_USED = 955;

    public static void main(String[] args) throws Exception {
        String url = getenvRequired("ORACLE_JDBC_URL");
        String user = getenvRequired("ORACLE_USER");
        String password = getenvRequired("ORACLE_PASSWORD");
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: OracleSqlRunner <sql-file>...");
        }

        Class.forName("oracle.jdbc.OracleDriver");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(true);
            for (String path : args) {
                runFile(connection, path);
            }
        }
    }

    private static String getenvRequired(String key) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing environment variable: " + key);
        }
        return value;
    }

    private static void runFile(Connection connection, String path) throws IOException, SQLException {
        System.out.println("==> " + path);
        String sql = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        List<String> statements = splitStatements(sql);
        for (String statement : statements) {
            execute(connection, statement);
        }
    }

    private static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<String>();
        StringBuilder buffer = new StringBuilder();
        boolean plsql = false;

        String[] lines = sql.split("\\R", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }

            String lower = trimmed.toLowerCase();
            if (lower.startsWith("create or replace package")
                    || lower.startsWith("create or replace procedure")
                    || lower.startsWith("create or replace function")
                    || lower.startsWith("create or replace trigger")) {
                plsql = true;
            }

            if (plsql && "/".equals(trimmed)) {
                addStatement(statements, buffer.toString());
                buffer.setLength(0);
                plsql = false;
                continue;
            }

            buffer.append(line).append('\n');
            if (!plsql && trimmed.endsWith(";")) {
                addStatement(statements, buffer.toString());
                buffer.setLength(0);
            }
        }
        addStatement(statements, buffer.toString());
        return statements;
    }

    private static void addStatement(List<String> statements, String raw) {
        String statement = raw.trim();
        if (statement.endsWith(";")) {
            statement = statement.substring(0, statement.length() - 1).trim();
        }
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
    }

    private static void execute(Connection connection, String statement) throws SQLException {
        String summary = summarize(statement);
        try (Statement jdbcStatement = connection.createStatement()) {
            if (isQuery(statement)) {
                printQuery(jdbcStatement, statement);
            } else {
                jdbcStatement.execute(statement);
                System.out.println("OK   " + summary);
            }
        } catch (SQLException error) {
            if (error.getErrorCode() == ORA_NAME_ALREADY_USED) {
                System.out.println("SKIP " + summary + " (already exists)");
                return;
            }
            System.out.println("FAIL " + summary);
            throw error;
        }
    }

    private static String summarize(String statement) {
        String compact = statement.replaceAll("\\s+", " ").trim();
        if (compact.length() > 120) {
            return compact.substring(0, 120) + "...";
        }
        return compact;
    }

    private static boolean isQuery(String statement) {
        String lower = statement.trim().toLowerCase();
        return lower.startsWith("select") || lower.startsWith("with");
    }

    private static void printQuery(Statement jdbcStatement, String statement) throws SQLException {
        try (ResultSet resultSet = jdbcStatement.executeQuery(statement)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            StringBuilder header = new StringBuilder("ROW");
            for (int i = 1; i <= columnCount; i++) {
                header.append('\t').append(metaData.getColumnLabel(i));
            }
            System.out.println(header.toString());

            int rowNumber = 0;
            while (resultSet.next()) {
                rowNumber++;
                StringBuilder row = new StringBuilder(String.valueOf(rowNumber));
                for (int i = 1; i <= columnCount; i++) {
                    row.append('\t').append(String.valueOf(resultSet.getObject(i)));
                }
                System.out.println(row.toString());
            }
            System.out.println("OK   query rows=" + rowNumber);
        }
    }
}
