package com.fr.plugin.tptj.ivan.jsd19083.udf;

import com.fr.dp.spark.udf.SparkUDFPluginSign;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class InfaSoapHttpStatusUdf implements SparkUDFPluginSign<String> {
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    @Override
    public String functionName() {
        return "infa_soap_http_status";
    }

    @Override
    public String[] params() {
        return new String[] {
            "invokeFlag",
            "method",
            "url",
            "body",
            "contentType",
            "soapAction",
            "charset",
            "successStatus",
            "successValue",
            "defaultValue"
        };
    }

    @Override
    public DataType returnType() {
        return DataTypes.StringType;
    }

    @Override
    public String run(Object... args) throws Exception {
        String defaultValue = arg(args, 9, "0");
        if (!isInvokeEnabled(arg(args, 0, ""))) {
            return defaultValue;
        }

        String method = arg(args, 1, "POST");
        String url = arg(args, 2, "");
        String body = arg(args, 3, "");
        String contentType = arg(args, 4, "");
        String soapAction = arg(args, 5, "");
        String charset = arg(args, 6, "UTF-8");
        String successStatus = arg(args, 7, "200");
        String successValue = arg(args, 8, "1");

        if (url.length() == 0) {
            return defaultValue;
        }
        HttpURLConnection connection = null;
        OutputStream outputStream = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(DEFAULT_TIMEOUT_MS);
            connection.setReadTimeout(DEFAULT_TIMEOUT_MS);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestMethod(method.length() == 0 ? "POST" : method.toUpperCase());
            if (contentType.length() > 0) {
                connection.setRequestProperty("Content-Type", contentType);
            }
            if (soapAction.length() > 0) {
                connection.setRequestProperty("SOAPAction", soapAction);
            }
            if (charset.length() > 0) {
                connection.setRequestProperty("Encoding", charset);
            }
            if (!"GET".equalsIgnoreCase(method)) {
                connection.setDoOutput(true);
                outputStream = connection.getOutputStream();
                outputStream.write(body.getBytes(charset.length() == 0 ? "UTF-8" : charset));
                outputStream.flush();
            }
            int statusCode = connection.getResponseCode();
            return Integer.toString(statusCode).equals(successStatus) ? successValue : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String arg(Object[] args, int index, String defaultValue) {
        if (args == null || index < 0 || index >= args.length || args[index] == null) {
            return defaultValue;
        }
        return String.valueOf(args[index]);
    }

    private static boolean isInvokeEnabled(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        return "1".equals(normalized) || "true".equalsIgnoreCase(normalized) || "yes".equalsIgnoreCase(normalized);
    }
}
