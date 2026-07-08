package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.api;

import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.ExpressionFieldInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiNodeFromInfoBean {
    public static final String STATUS_FDL_TRANSLATION_READY = "FDL_TRANSLATION_READY";
    public static final String STATUS_FDL_ADAPTER_REQUIRED = "FDL_ADAPTER_REQUIRED";
    public static final String STATUS_FDL_GENERATION_RESEARCH_PENDING = "FDL_GENERATION_RESEARCH_PENDING";
    public static final String STATUS_ADAPTER_REQUIRED = "FDL_ADAPTER_REQUIRED_METADATA_INCOMPLETE";
    public static final String STATUS_VISIBLE_ONLY = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING";
    public static final String STATUS_UNSUPPORTED = "FDL_GENERATION_RESEARCH_PENDING_UNSUPPORTED";
    public static final String ADAPTER_ROW_ID_FIELD = "_fdl_adapter_row_id";
    public static final String ROW_API_UDF_PROVIDER_FUNCTION = "infa_soap_http_status";
    public static final String ROW_API_UDF_SPARK_FUNCTION = "plugin_" + ROW_API_UDF_PROVIDER_FUNCTION;

    private String url;
    private String method = "POST";
    private List<Map<String, String>> headers = new ArrayList<Map<String, String>>();
    private String bodyTemplate;
    private String bodyType = "TEXT";
    private String charset = "UTF-8";
    private String responseFormat = "TEXT";
    private String responseBodyName = "response";
    private List<String> responseOptions = new ArrayList<String>();
    private List<String> outputFields = new ArrayList<String>();
    private Map<String, String> outputFieldTypes = new LinkedHashMap<String, String>();
    private Map<String, String> outputFieldTargets = new LinkedHashMap<String, String>();
    private Map<String, String> inputFieldSources = new LinkedHashMap<String, String>();
    private List<String> limitations = new ArrayList<String>();
    private String sourceTransformationName;
    private String sourceTransformationType;
    private String sourceTemplateName;
    private String actionName;
    private String soapAction;
    private String soapActionSource;
    private String invocationCondition;
    private String protocol;
    private String javaClassName;
    private String javaMethodName;
    private String executionStatus = STATUS_VISIBLE_ONLY;
    private String executionStatusReason = "";
    private List<String> requiredConfigFields = new ArrayList<String>();
    private Map<String, String> adapterMetadata = new LinkedHashMap<String, String>();
    private List<String> responseStatusMappings = new ArrayList<String>();
    private List<String> invocationConditionFields = new ArrayList<String>();
    private RowInvocationBinding rowInvocationBinding = RowInvocationBinding.empty();
    private List<HttpStatusOutputBinding> httpStatusOutputBindings = new ArrayList<HttpStatusOutputBinding>();
    private boolean rowLevelInvocationAdapterSupported;
    private boolean httpStatusOutputAdapterSupported;
    private List<ExpressionFieldInfo> expressionInfos = new ArrayList<ExpressionFieldInfo>();
    private List<String> postProjectionExpressions = new ArrayList<String>();

    private ApiNodeFromInfoBean() {
    }

    public static ApiNodeFromInfoBean create() {
        return new ApiNodeFromInfoBean();
    }

    public ApiNodeFromInfoBean url(String url) {
        this.url = url;
        return this;
    }

    public ApiNodeFromInfoBean method(String method) {
        this.method = method;
        return this;
    }

    public ApiNodeFromInfoBean addHeader(String key, String value) {
        Map<String, String> header = new HashMap<String, String>();
        header.put("name", key);
        header.put("value", value);
        this.headers.add(header);
        return this;
    }

    public ApiNodeFromInfoBean bodyTemplate(String bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
        return this;
    }

    public ApiNodeFromInfoBean bodyType(String bodyType) {
        this.bodyType = bodyType;
        return this;
    }

    public ApiNodeFromInfoBean charset(String charset) {
        this.charset = charset;
        return this;
    }

    public ApiNodeFromInfoBean responseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
        return this;
    }

    public ApiNodeFromInfoBean responseBodyName(String responseBodyName) {
        this.responseBodyName = responseBodyName;
        return this;
    }

    public ApiNodeFromInfoBean responseOptions(List<String> responseOptions) {
        this.responseOptions = responseOptions;
        return this;
    }

    public ApiNodeFromInfoBean outputFields(List<String> outputFields) {
        this.outputFields = outputFields;
        return this;
    }

    public ApiNodeFromInfoBean addOutputField(String fieldName, String fieldType) {
        if (fieldName != null && fieldName.length() > 0 && !this.outputFields.contains(fieldName)) {
            this.outputFields.add(fieldName);
        }
        if (fieldName != null && fieldName.length() > 0 && fieldType != null && fieldType.length() > 0) {
            this.outputFieldTypes.put(fieldName, fieldType);
        }
        return this;
    }

    public ApiNodeFromInfoBean addOutputFieldTarget(String outputField, String targetField) {
        if (outputField != null && outputField.length() > 0 && targetField != null && targetField.length() > 0) {
            this.outputFieldTargets.put(outputField, targetField);
        }
        return this;
    }

    public ApiNodeFromInfoBean addInputFieldSource(String inputField, String sourceField) {
        if (inputField != null && inputField.length() > 0) {
            this.inputFieldSources.put(inputField, sourceField == null ? "" : sourceField);
        }
        return this;
    }

    public ApiNodeFromInfoBean addLimitation(String limitation) {
        if (limitation != null && limitation.length() > 0 && !this.limitations.contains(limitation)) {
            this.limitations.add(limitation);
        }
        return this;
    }

    public ApiNodeFromInfoBean sourceTransformation(String name, String type, String templateName) {
        this.sourceTransformationName = name;
        this.sourceTransformationType = type;
        this.sourceTemplateName = templateName;
        return this;
    }

    public ApiNodeFromInfoBean actionName(String actionName) {
        this.actionName = actionName;
        return this;
    }

    public ApiNodeFromInfoBean soapAction(String soapAction) {
        this.soapAction = soapAction;
        return this;
    }

    public ApiNodeFromInfoBean soapActionSource(String soapActionSource) {
        this.soapActionSource = soapActionSource;
        return this;
    }

    public ApiNodeFromInfoBean invocationCondition(String invocationCondition) {
        this.invocationCondition = invocationCondition;
        return this;
    }

    public ApiNodeFromInfoBean protocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    public ApiNodeFromInfoBean javaClassName(String javaClassName) {
        this.javaClassName = javaClassName;
        return this;
    }

    public ApiNodeFromInfoBean javaMethodName(String javaMethodName) {
        this.javaMethodName = javaMethodName;
        return this;
    }

    public ApiNodeFromInfoBean addRequiredConfigField(String fieldName) {
        if (fieldName != null && fieldName.length() > 0 && !this.requiredConfigFields.contains(fieldName)) {
            this.requiredConfigFields.add(fieldName);
        }
        return this;
    }

    public ApiNodeFromInfoBean addAdapterMetadata(String key, String value) {
        if (key != null && key.length() > 0 && value != null && value.length() > 0) {
            this.adapterMetadata.put(key, value);
        }
        return this;
    }

    public ApiNodeFromInfoBean addResponseStatusMapping(String mapping) {
        if (mapping != null && mapping.length() > 0 && !this.responseStatusMappings.contains(mapping)) {
            this.responseStatusMappings.add(mapping);
        }
        return this;
    }

    public ApiNodeFromInfoBean addInvocationConditionField(String fieldName) {
        if (fieldName != null && fieldName.length() > 0 && !this.invocationConditionFields.contains(fieldName)) {
            this.invocationConditionFields.add(fieldName);
        }
        return this;
    }

    public ApiNodeFromInfoBean rowInvocationBinding(String condition, List<String> fields, String mode, boolean supported) {
        this.rowInvocationBinding = new RowInvocationBinding(condition, fields, mode, supported);
        this.rowLevelInvocationAdapterSupported = supported;
        return this;
    }

    public ApiNodeFromInfoBean addHttpStatusOutputBinding(String outputField, String statusCode, String outputValue,
                                                          String defaultValue, String sourceMethod) {
        HttpStatusOutputBinding binding = new HttpStatusOutputBinding(
            outputField, statusCode, outputValue, defaultValue, sourceMethod, false);
        String visible = binding.toVisibleLine();
        boolean exists = false;
        for (HttpStatusOutputBinding item : this.httpStatusOutputBindings) {
            if (item.toVisibleLine().equals(visible)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            this.httpStatusOutputBindings.add(binding);
        }
        this.addResponseStatusMapping(visible);
        return this;
    }

    public ApiNodeFromInfoBean rowLevelInvocationAdapterSupported(boolean supported) {
        this.rowLevelInvocationAdapterSupported = supported;
        if (this.rowInvocationBinding.hasCondition()) {
            this.rowInvocationBinding = this.rowInvocationBinding.withSupported(supported);
        }
        return this;
    }

    public ApiNodeFromInfoBean httpStatusOutputAdapterSupported(boolean supported) {
        this.httpStatusOutputAdapterSupported = supported;
        List<HttpStatusOutputBinding> updated = new ArrayList<HttpStatusOutputBinding>();
        for (HttpStatusOutputBinding binding : this.httpStatusOutputBindings) {
            updated.add(binding.withSupported(supported));
        }
        this.httpStatusOutputBindings = updated;
        return this;
    }

    public ApiNodeFromInfoBean addExpressionInfo(ExpressionFieldInfo expressionInfo) {
        if (expressionInfo != null && expressionInfo.isComputed()) {
            this.expressionInfos.add(expressionInfo);
        }
        return this;
    }

    public ApiNodeFromInfoBean addPostProjectionExpression(ExpressionFieldInfo expressionInfo, String evidence) {
        if (expressionInfo == null) {
            return this;
        }
        String expressionName = expressionInfo.getTransformationName() + "." + expressionInfo.getFieldName();
        String line = expressionName;
        if (evidence != null && evidence.length() > 0) {
            line += " - " + evidence;
        }
        if (!postProjectionExpressions.contains(line)) {
            postProjectionExpressions.add(line);
        }
        String prefix = "Downstream Expression " + expressionName + " depends on";
        List<String> remaining = new ArrayList<String>();
        for (String limitation : limitations) {
            if (limitation == null || !limitation.startsWith(prefix)) {
                remaining.add(limitation);
            }
        }
        limitations = remaining;
        return this;
    }

    public ApiNodeFromInfoBean resolveExecutionStatus() {
        this.requiredConfigFields.clear();
        boolean hasUrl = url != null && url.length() > 0;
        boolean hasMethod = method != null && method.length() > 0;
        boolean hasProtocol = protocol != null && protocol.length() > 0;
        boolean hasOutputs = !outputFields.isEmpty();
        boolean hasMappedOutput = !outputFieldTargets.isEmpty();
        boolean hasBodyOrGet = "GET".equalsIgnoreCase(method) || (bodyTemplate != null && bodyTemplate.length() > 0);
        boolean hasInvocationCondition = invocationCondition != null && invocationCondition.length() > 0;
        boolean hasStatusMappings = !responseStatusMappings.isEmpty() || !httpStatusOutputBindings.isEmpty();
        if (!hasUrl) {
            addRequiredConfigField("endpoint URL");
        }
        if (!hasMethod) {
            addRequiredConfigField("HTTP method/protocol");
        }
        if (!hasBodyOrGet) {
            addRequiredConfigField("request body or GET semantics");
        }
        if (!hasOutputs) {
            addRequiredConfigField("response/output fields");
        }
        if (!hasMappedOutput) {
            addRequiredConfigField("response-to-target field mapping");
        }
        if (hasInvocationCondition) {
            addRequiredConfigField("row-level invocation condition binding");
        }
        if (hasStatusMappings) {
            addRequiredConfigField("HTTP status-code output binding adapter");
        }
        boolean apiNodeSupported = hasUrl && hasMethod && hasProtocol && hasOutputs && hasMappedOutput && hasBodyOrGet;
        boolean adapterRequired = hasInvocationCondition || hasStatusMappings;
        boolean adapterBindingsSupported = (!hasInvocationCondition || rowLevelInvocationAdapterSupported || rowInvocationBinding.isSupported())
            && (!hasStatusMappings || httpStatusOutputAdapterSupported);
        if (apiNodeSupported) {
            if (limitations.isEmpty()) {
                if (adapterRequired) {
                    if (adapterBindingsSupported) {
                        this.executionStatus = STATUS_FDL_ADAPTER_REQUIRED;
                        this.executionStatusReason = "adapter contract contains parsed row-level invocation and HTTP status output bindings; native FDL API dataflow is not generated until the adapter provides row context";
                    } else {
                        this.executionStatus = STATUS_FDL_GENERATION_RESEARCH_PENDING;
                        this.executionStatusReason = "adapter bindings still need FDL/plugin generation research before generating runnable API dataflow";
                    }
                } else {
                    this.executionStatus = STATUS_FDL_TRANSLATION_READY;
                    this.executionStatusReason = "protocol, endpoint, request mapping, and response mapping are present";
                }
            } else {
                this.executionStatus = STATUS_FDL_GENERATION_RESEARCH_PENDING;
                this.executionStatusReason = "preserved limitations require FDL/plugin generation work before generating runnable API dataflow";
            }
        } else if (hasUrl || hasOutputs || !adapterMetadata.isEmpty()) {
            this.executionStatus = STATUS_ADAPTER_REQUIRED;
            this.executionStatusReason = "external call metadata is incomplete for an executable FDL API node";
        } else {
            this.executionStatus = STATUS_VISIBLE_ONLY;
            this.executionStatusReason = "external call marker is visible, but executable metadata was not derived";
        }
        refreshExpressionDependencyLimitations();
        return this;
    }

    private void refreshExpressionDependencyLimitations() {
        if (expressionInfos.isEmpty()) {
            return;
        }
        if (STATUS_FDL_TRANSLATION_READY.equals(executionStatus)) {
            return;
        }
        for (ExpressionFieldInfo info : expressionInfos) {
            if (STATUS_FDL_ADAPTER_REQUIRED.equals(executionStatus) && info.getLimitation().contains("external-call output")) {
                addLimitation("Downstream Expression " + info.getTransformationName() + "." + info.getFieldName()
                    + " depends on adapter-required external-call output; expression remains translation-pending until the adapter provides row-bound output and projection.");
            } else {
                addLimitation("Downstream Expression " + info.getTransformationName() + "." + info.getFieldName()
                    + " depends on external-call output and remains translation-pending while call status is " + executionStatus + ".");
            }
        }
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public List<Map<String, String>> getHeaders() {
        return headers;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public String getBodyType() {
        return bodyType;
    }

    public String getCharset() {
        return charset;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    public String getResponseBodyName() {
        return responseBodyName;
    }

    public List<String> getResponseOptions() {
        return responseOptions;
    }

    public List<String> getOutputFields() {
        return outputFields;
    }

    public String getOutputFieldType(String outputField) {
        return this.outputFieldTypes.get(outputField);
    }

    public String getOutputFieldTarget(String outputField) {
        return this.outputFieldTargets.get(outputField);
    }

    public Map<String, String> getOutputFieldTargets() {
        return outputFieldTargets;
    }

    public Map<String, String> getInputFieldSources() {
        return inputFieldSources;
    }

    public List<String> getLimitations() {
        return limitations;
    }

    public String getSourceTransformationName() {
        return sourceTransformationName;
    }

    public String getSourceTransformationType() {
        return sourceTransformationType;
    }

    public String getSourceTemplateName() {
        return sourceTemplateName;
    }

    public String getActionName() {
        return actionName;
    }

    public String getSoapAction() {
        return soapAction;
    }

    public String getSoapActionSource() {
        return soapActionSource;
    }

    public String getInvocationCondition() {
        return invocationCondition;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getJavaClassName() {
        return javaClassName;
    }

    public String getJavaMethodName() {
        return javaMethodName;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public String getExecutionStatusReason() {
        return executionStatusReason;
    }

    public List<String> getRequiredConfigFields() {
        return new ArrayList<String>(requiredConfigFields);
    }

    public Map<String, String> getAdapterMetadata() {
        return new LinkedHashMap<String, String>(adapterMetadata);
    }

    public List<String> getResponseStatusMappings() {
        return new ArrayList<String>(responseStatusMappings);
    }

    public List<String> getInvocationConditionFields() {
        return new ArrayList<String>(invocationConditionFields);
    }

    public RowInvocationBinding getRowInvocationBinding() {
        return rowInvocationBinding;
    }

    public List<HttpStatusOutputBinding> getHttpStatusOutputBindings() {
        return new ArrayList<HttpStatusOutputBinding>(httpStatusOutputBindings);
    }

    public boolean isRowLevelInvocationAdapterSupported() {
        return rowLevelInvocationAdapterSupported;
    }

    public boolean isHttpStatusOutputAdapterSupported() {
        return httpStatusOutputAdapterSupported;
    }

    public boolean isFdlApiNodeSupported() {
        return STATUS_FDL_TRANSLATION_READY.equals(executionStatus);
    }

    public boolean isFdlRowApiUdfSupported() {
        return STATUS_FDL_ADAPTER_REQUIRED.equals(executionStatus)
            && "POST".equalsIgnoreCase(method)
            && isSoapProtocolOrBody()
            && url != null && url.length() > 0
            && bodyTemplate != null && bodyTemplate.length() > 0
            && rowInvocationBinding.hasCondition()
            && rowInvocationBinding.isSupported()
            && isSimpleEqualsCondition(rowInvocationBinding.getCondition())
            && httpStatusOutputAdapterSupported
            && httpStatusOutputBindings.size() == 1;
    }

    public List<ExpressionFieldInfo> getExpressionInfos() {
        return new ArrayList<ExpressionFieldInfo>(expressionInfos);
    }

    public List<String> getPostProjectionExpressions() {
        return new ArrayList<String>(postProjectionExpressions);
    }

    public List<String> getAdapterContractMetadataLines() {
        List<String> lines = new ArrayList<String>();
        lines.add("FDL_ADAPTER_CONTRACT api.status=" + metadataValue(executionStatus)
            + "; api.protocol=" + metadataValue(protocol)
            + "; api.method=" + metadataValue(method)
            + "; api.bodyType=" + metadataValue(bodyType)
            + "; api.responseFormat=" + metadataValue(responseFormat));
        lines.add("FDL_API_NATIVE_CONTRACT status=" + metadataValue(executionStatus)
            + "; nativeNode=" + (isFdlApiNodeSupported() ? "ApiReadNode" : "not_generated")
            + "; adapterNode=" + (isFdlRowApiUdfSupported() ? "SparkSqlNode+SparkUDF" : "not_generated")
            + "; adapter=" + (isFdlRowApiUdfSupported() ? "row_api_adapter" : "")
            + "; function=" + (isFdlRowApiUdfSupported() ? ROW_API_UDF_SPARK_FUNCTION : "")
            + "; providerFunction=" + (isFdlRowApiUdfSupported() ? ROW_API_UDF_PROVIDER_FUNCTION : "")
            + "; runtimeValidationRequired=" + (isFdlRowApiUdfSupported() ? "yes" : "no")
            + "; adapterRequired=" + (requiresAdapterContract() ? "yes" : "no")
            + "; rowContextKey=" + ADAPTER_ROW_ID_FIELD
            + "; noOnOneEqualsOne=yes");
        addRequestBindingContractLines(lines);
        if (rowInvocationBinding.hasCondition()) {
            lines.add("FDL_ADAPTER_CONTRACT rowInvocation.mode=" + metadataValue(rowInvocationBinding.getMode())
                + "; rowInvocation.fields=" + metadataValue(joinValues(rowInvocationBinding.getFields()))
                + "; rowInvocation.supported=" + (rowInvocationBinding.isSupported() ? "yes" : "no")
                + "; rowInvocation.condition=" + metadataValue(rowInvocationBinding.getCondition()));
            lines.add("FDL_API_ROW_CONDITION mode=" + metadataValue(rowInvocationBinding.getMode())
                + "; fields=" + metadataValue(joinValues(rowInvocationBinding.getFields()))
                + "; supported=" + (rowInvocationBinding.isSupported() ? "yes" : "no")
                + "; rowContextKey=" + ADAPTER_ROW_ID_FIELD
                + "; condition=" + metadataValue(rowInvocationBinding.getCondition()));
        }
        for (HttpStatusOutputBinding binding : httpStatusOutputBindings) {
            lines.add("FDL_ADAPTER_CONTRACT httpStatusOutput.output=" + metadataValue(binding.getOutputField())
                + "; httpStatusOutput.status=" + metadataValue(binding.getStatusCode())
                + "; httpStatusOutput.value=" + metadataValue(binding.getOutputValue())
                + "; httpStatusOutput.default=" + metadataValue(binding.getDefaultValue())
                + "; httpStatusOutput.sourceMethod=" + metadataValue(binding.getSourceMethod())
                + "; httpStatusOutput.supported=" + (binding.isSupported() ? "yes" : "no"));
            lines.add("FDL_API_HTTP_STATUS_OUTPUT output=" + metadataValue(binding.getOutputField())
                + "; target=" + metadataValue(outputFieldTargets.get(binding.getOutputField()))
                + "; status=" + metadataValue(binding.getStatusCode())
                + "; value=" + metadataValue(binding.getOutputValue())
                + "; default=" + metadataValue(binding.getDefaultValue())
                + "; sourceMethod=" + metadataValue(binding.getSourceMethod())
                + "; supported=" + (binding.isSupported() ? "yes" : "no"));
        }
        if (isSoapProtocolOrBody()) {
            lines.add("FDL_API_SOAP_BODY bodyType=" + metadataValue(bodyType)
                + "; contentType=" + metadataValue(findHeaderValue("Content-Type"))
                + "; action=" + metadataValue(actionName)
                + "; soapAction=" + metadataValue(soapAction)
                + "; soapActionSource=" + metadataValue(soapActionSource)
                + "; bodyTemplate=" + (isFdlApiNodeSupported() ? "preserved-in-api-node" : "preserved-in-adapter-contract")
                + "; requestParamMode=upstream-field-driven");
        }
        if (!postProjectionExpressions.isEmpty()) {
            lines.add("FDL_ADAPTER_CONTRACT postProjection.rowBinding=ADAPTER_ROW_BINDING_REQUIRED"
                + "; postProjection.joinKey=" + ADAPTER_ROW_ID_FIELD
                + "; postProjection.nativeStableRowId=not_proven"
                + "; postProjection.noOnOneEqualsOne=yes");
            lines.add("FDL_API_POST_PROJECTION rowBinding=required"
                + "; joinKey=" + ADAPTER_ROW_ID_FIELD
                + "; nativeStableRowId=not_proven"
                + "; noOnOneEqualsOne=yes");
        }
        return lines;
    }

    public String getAdapterContractMetadataBlock() {
        return joinValues(getAdapterContractMetadataLines(), "\n");
    }

    public String getAdapterContractFieldMetadata(String outputField) {
        if (outputField == null || outputField.length() == 0) {
            return "";
        }
        List<String> lines = new ArrayList<String>();
        for (HttpStatusOutputBinding binding : httpStatusOutputBindings) {
            if (outputField.equalsIgnoreCase(binding.getOutputField())) {
                lines.add("FDL_ADAPTER_CONTRACT_FIELD output=" + metadataValue(binding.getOutputField())
                    + "; httpStatus=" + metadataValue(binding.getStatusCode())
                    + "; value=" + metadataValue(binding.getOutputValue())
                    + "; default=" + metadataValue(binding.getDefaultValue())
                    + "; sourceMethod=" + metadataValue(binding.getSourceMethod())
                    + "; supported=" + (binding.isSupported() ? "yes" : "no"));
                lines.add("FDL_API_HTTP_STATUS_OUTPUT_FIELD output=" + metadataValue(binding.getOutputField())
                    + "; target=" + metadataValue(outputFieldTargets.get(binding.getOutputField()))
                    + "; status=" + metadataValue(binding.getStatusCode())
                    + "; value=" + metadataValue(binding.getOutputValue())
                    + "; default=" + metadataValue(binding.getDefaultValue())
                    + "; sourceMethod=" + metadataValue(binding.getSourceMethod())
                    + "; supported=" + (binding.isSupported() ? "yes" : "no"));
            }
        }
        String target = outputFieldTargets.get(outputField);
        if (target != null && target.length() > 0) {
            lines.add("FDL_ADAPTER_CONTRACT_FIELD output=" + metadataValue(outputField)
                + "; target=" + metadataValue(target));
        }
        return joinValues(lines, "\n");
    }

    public String getAdapterRowBindingMetadataLine() {
        if (!rowInvocationBinding.hasCondition()) {
            return "";
        }
        return "FDL_ADAPTER_ROW_BINDING required=yes"
            + "; joinKey=" + ADAPTER_ROW_ID_FIELD
            + "; nativeStableRowId=not_proven"
            + "; noOnOneEqualsOne=yes"
            + "; invocationMode=" + metadataValue(rowInvocationBinding.getMode())
            + "; invocationFields=" + metadataValue(joinValues(rowInvocationBinding.getFields()))
            + "; supported=" + (rowInvocationBinding.isSupported() ? "yes" : "no")
            + "; condition=" + metadataValue(rowInvocationBinding.getCondition());
    }

    public String getVisibleNote() {
        StringBuilder note = new StringBuilder();
        note.append("Informatica external API/SOAP call");
        note.append("\nExecution status: ").append(executionStatus);
        if (executionStatusReason != null && executionStatusReason.length() > 0) {
            note.append(" - ").append(executionStatusReason);
        }
        if (sourceTransformationName != null && sourceTransformationName.length() > 0) {
            note.append("\nTransformation: ").append(sourceTransformationName);
            if (sourceTransformationType != null && sourceTransformationType.length() > 0) {
                note.append(" [").append(sourceTransformationType).append("]");
            }
            if (sourceTemplateName != null && sourceTemplateName.length() > 0) {
                note.append(" / ").append(sourceTemplateName);
            }
        }
        if (method != null && method.length() > 0) {
            note.append("\nMethod: ").append(method);
        }
        if (protocol != null && protocol.length() > 0) {
            note.append("\nProtocol: ").append(protocol);
        }
        if (url != null && url.length() > 0) {
            note.append("\nURL: ").append(url);
        }
        if (javaClassName != null && javaClassName.length() > 0) {
            note.append("\nJava class: ").append(javaClassName);
        }
        if (javaMethodName != null && javaMethodName.length() > 0) {
            note.append("\nJava method/procedure: ").append(javaMethodName);
        }
        if (actionName != null && actionName.length() > 0) {
            note.append("\nAction: ").append(actionName);
        }
        if (soapAction != null || soapActionSource != null) {
            note.append("\nSOAPAction: ").append(soapAction == null ? "" : soapAction);
            if (soapActionSource != null && soapActionSource.length() > 0) {
                note.append(" (source: ").append(soapActionSource).append(")");
            }
        }
        if (invocationCondition != null && invocationCondition.length() > 0) {
            note.append("\nInvocation condition: ").append(invocationCondition);
            List<String> visibleFields = rowInvocationBinding.hasFields() ? rowInvocationBinding.getFields() : invocationConditionFields;
            if (!visibleFields.isEmpty()) {
                note.append(" (fields: ");
                for (int i = 0; i < visibleFields.size(); i++) {
                    if (i > 0) {
                        note.append(",");
                    }
                    note.append(visibleFields.get(i));
                }
                note.append(")");
            }
        }
        if (rowInvocationBinding.hasCondition()) {
            note.append("\nRow invocation binding: ").append(rowInvocationBinding.toStructuredLine());
        }
        if (!inputFieldSources.isEmpty()) {
            note.append("\nRequest fields: ");
            boolean first = true;
            for (Map.Entry<String, String> entry : inputFieldSources.entrySet()) {
                if (!first) {
                    note.append("; ");
                }
                note.append(entry.getKey());
                if (entry.getValue() != null && entry.getValue().length() > 0) {
                    note.append(" <= ").append(entry.getValue());
                }
                first = false;
            }
        }
        if (!outputFields.isEmpty()) {
            note.append("\nResponse/result fields: ");
            for (int i = 0; i < outputFields.size(); i++) {
                String outputField = outputFields.get(i);
                if (i > 0) {
                    note.append("; ");
                }
                note.append(outputField);
                String target = outputFieldTargets.get(outputField);
                if (target != null && target.length() > 0) {
                    note.append(" -> ").append(target);
                }
            }
        }
        if (!expressionInfos.isEmpty()) {
            note.append("\nDownstream Expression fields: ");
            for (int i = 0; i < expressionInfos.size(); i++) {
                if (i > 0) {
                    note.append("; ");
                }
                note.append(expressionInfos.get(i).toVisibleLine());
            }
        }
        if (!postProjectionExpressions.isEmpty()) {
            note.append("\nFDL post-API expression projections: ");
            for (int i = 0; i < postProjectionExpressions.size(); i++) {
                if (i > 0) {
                    note.append("; ");
                }
                note.append(postProjectionExpressions.get(i));
            }
        }
        if (bodyTemplate != null && bodyTemplate.length() > 0) {
            note.append("\nRequest body preserved in ")
                .append(isFdlApiNodeSupported() ? "API node body" : "adapter contract")
                .append(" (").append(bodyType).append(").");
        }
        if (!adapterMetadata.isEmpty()) {
            note.append("\nAdapter metadata: ");
            boolean first = true;
            for (Map.Entry<String, String> entry : adapterMetadata.entrySet()) {
                if (!first) {
                    note.append("; ");
                }
                note.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }
        if (!responseStatusMappings.isEmpty()) {
            note.append("\nResponse status mappings: ");
            for (int i = 0; i < responseStatusMappings.size(); i++) {
                if (i > 0) {
                    note.append("; ");
                }
                note.append(responseStatusMappings.get(i));
            }
        }
        if (!httpStatusOutputBindings.isEmpty()) {
            note.append("\nHTTP status output bindings: ");
            for (int i = 0; i < httpStatusOutputBindings.size(); i++) {
                if (i > 0) {
                    note.append("; ");
                }
                note.append(httpStatusOutputBindings.get(i).toStructuredLine());
            }
        }
        if (rowLevelInvocationAdapterSupported || httpStatusOutputAdapterSupported) {
            note.append("\nParsed FDL API adapter contract bindings: ");
            boolean first = true;
            if (rowLevelInvocationAdapterSupported) {
                note.append("row-level invocation condition");
                first = false;
            }
            if (httpStatusOutputAdapterSupported) {
                if (!first) {
                    note.append("; ");
                }
                note.append("HTTP status-code output binding");
            }
        }
        if (!requiredConfigFields.isEmpty()) {
            note.append("\nRequired adapter config: ");
            for (int i = 0; i < requiredConfigFields.size(); i++) {
                if (i > 0) {
                    note.append("; ");
                }
                note.append(requiredConfigFields.get(i));
            }
        }
        if (!limitations.isEmpty()) {
            note.append("\nLimitations: ");
            for (int i = 0; i < limitations.size(); i++) {
                if (i > 0) {
                    note.append("; ");
                }
                note.append(limitations.get(i));
            }
        }
        String metadataBlock = getAdapterContractMetadataBlock();
        if (metadataBlock.length() > 0) {
            note.append("\n").append(metadataBlock);
        }
        return note.toString();
    }

    private static String joinValues(List<String> values) {
        return joinValues(values, ",");
    }

    private static String joinValues(List<String> values, String delimiter) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append(delimiter);
            }
            result.append(values.get(i));
        }
        return result.toString();
    }

    private static String metadataValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", " ").replace("\n", " ").replace(";", "\\;");
    }

    private boolean requiresAdapterContract() {
        return rowInvocationBinding.hasCondition()
            || !httpStatusOutputBindings.isEmpty()
            || !inputFieldSources.isEmpty()
            || isSoapProtocolOrBody()
            || !postProjectionExpressions.isEmpty();
    }

    private void addRequestBindingContractLines(List<String> lines) {
        for (Map.Entry<String, String> entry : inputFieldSources.entrySet()) {
            lines.add("FDL_API_REQUEST_BINDING input=" + metadataValue(entry.getKey())
                + "; source=" + metadataValue(entry.getValue())
                + "; binding=upstream-field"
                + "; rowContextKey=" + ADAPTER_ROW_ID_FIELD
                + "; required=yes");
        }
    }

    private boolean isSoapProtocolOrBody() {
        String contentType = findHeaderValue("Content-Type");
        String marker = ((protocol == null ? "" : protocol) + " "
            + (bodyTemplate == null ? "" : bodyTemplate) + " "
            + (actionName == null ? "" : actionName) + " "
            + (soapAction == null ? "" : soapAction) + " "
            + (contentType == null ? "" : contentType)).toLowerCase();
        return marker.contains("soap") || marker.contains("envelope");
    }

    private String findHeaderValue(String headerName) {
        if (headerName == null) {
            return "";
        }
        for (Map<String, String> header : headers) {
            if (headerName.equalsIgnoreCase(header.get("name"))) {
                return header.get("value");
            }
        }
        return "";
    }

    private static boolean isSimpleEqualsCondition(String condition) {
        if (condition == null) {
            return false;
        }
        String trimmed = condition.trim();
        return java.util.regex.Pattern.compile("^\"(?:\\\\.|[^\"])*\"\\s*\\.\\s*equals\\s*\\(\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\)$").matcher(trimmed).matches()
            || java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*equals\\s*\\(\\s*\"(?:\\\\.|[^\"])*\"\\s*\\)$").matcher(trimmed).matches()
            || java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*\\s*(?:==|=)\\s*\"(?:\\\\.|[^\"])*\"$").matcher(trimmed).matches();
    }

    public static class RowInvocationBinding {
        private final String condition;
        private final List<String> fields;
        private final String mode;
        private final boolean supported;

        private RowInvocationBinding(String condition, List<String> fields, String mode, boolean supported) {
            this.condition = condition == null ? "" : condition;
            this.fields = fields == null ? new ArrayList<String>() : new ArrayList<String>(fields);
            this.mode = mode == null ? "" : mode;
            this.supported = supported;
        }

        public static RowInvocationBinding empty() {
            return new RowInvocationBinding("", new ArrayList<String>(), "", false);
        }

        public boolean hasCondition() {
            return condition.length() > 0;
        }

        public boolean hasFields() {
            return !fields.isEmpty();
        }

        public String getCondition() {
            return condition;
        }

        public List<String> getFields() {
            return new ArrayList<String>(fields);
        }

        public String getMode() {
            return mode;
        }

        public boolean isSupported() {
            return supported;
        }

        public RowInvocationBinding withSupported(boolean supported) {
            return new RowInvocationBinding(condition, fields, mode, supported);
        }

        public String toStructuredLine() {
            StringBuilder line = new StringBuilder();
            line.append("mode=").append(mode);
            line.append("; fields=");
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    line.append(",");
                }
                line.append(fields.get(i));
            }
            line.append("; supported=").append(supported ? "yes" : "no");
            line.append("; condition=").append(condition);
            return line.toString();
        }
    }

    public static class HttpStatusOutputBinding {
        private final String outputField;
        private final String statusCode;
        private final String outputValue;
        private final String defaultValue;
        private final String sourceMethod;
        private final boolean supported;

        public HttpStatusOutputBinding(String outputField, String statusCode, String outputValue,
                                       String defaultValue, String sourceMethod, boolean supported) {
            this.outputField = outputField == null ? "" : outputField;
            this.statusCode = statusCode == null ? "" : statusCode;
            this.outputValue = outputValue == null ? "" : outputValue;
            this.defaultValue = defaultValue == null ? "" : defaultValue;
            this.sourceMethod = sourceMethod == null ? "" : sourceMethod;
            this.supported = supported;
        }

        public String getOutputField() {
            return outputField;
        }

        public String getStatusCode() {
            return statusCode;
        }

        public String getOutputValue() {
            return outputValue;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public String getSourceMethod() {
            return sourceMethod;
        }

        public boolean isSupported() {
            return supported;
        }

        public HttpStatusOutputBinding withSupported(boolean supported) {
            return new HttpStatusOutputBinding(outputField, statusCode, outputValue, defaultValue, sourceMethod, supported);
        }

        public String toVisibleLine() {
            StringBuilder visible = new StringBuilder();
            visible.append(outputField).append(" <= ").append(outputValue);
            visible.append(" when HTTP status ").append(statusCode);
            if (defaultValue.length() > 0) {
                visible.append("; default ").append(outputField).append(" <= ").append(defaultValue);
            }
            if (sourceMethod.length() > 0) {
                visible.append("; source method ").append(sourceMethod);
            }
            return visible.toString();
        }

        public String toStructuredLine() {
            StringBuilder line = new StringBuilder();
            line.append("output=").append(outputField);
            line.append("; status=").append(statusCode);
            line.append("; value=").append(outputValue);
            if (defaultValue.length() > 0) {
                line.append("; default=").append(defaultValue);
            }
            if (sourceMethod.length() > 0) {
                line.append("; sourceMethod=").append(sourceMethod);
            }
            line.append("; supported=").append(supported ? "yes" : "no");
            return line.toString();
        }
    }
}
