/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.fr.stable.StringUtils
 *  org.jetbrains.annotations.NotNull
 */
package com.fr.plugin.tptj.ivan.jsd19083.util.xml;

import com.fr.dp.devwork.work.param.ParamEntity;
import com.fr.plugin.tptj.ivan.jsd19083.bean.TaskEvalBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.Link;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.ApiSyncTaskNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.ConditionNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.InfaScopedParameterResolver;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.InvokeTaskNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.SqlTaskNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.SyncTaskNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.TransformSyncTaskNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.VirtualNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.api.ApiNodeFromInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.ConnectionMappingInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.DBNodeFromInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.DBNodeToInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.DataFlowTransformInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.ExpressionFieldInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.FieldLineageInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.FilterFieldInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.LookupFieldInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.TargetWriteInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.group.DefaultGroupNodeInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.group.GroupNodeInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.single.SingleNodeInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.parse.DataSyncParseNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.util.FineUtils;
import com.fr.plugin.tptj.ivan.jsd19083.util.LogUtil;
import com.fr.plugin.tptj.ivan.jsd19083.util.XmlParserUtil;
import com.fr.plugin.tptj.ivan.jsd19083.util.xml.GroupSQLInfo;
import com.fr.plugin.tptj.ivan.jsd19083.util.xml.SQLInfo;
import com.fr.stable.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class DataSyncParser {
    public static final String TO_DB_NAME_EXPRESSION = "/POWERMART/REPOSITORY/FOLDER/TARGET/@NAME";
    public static final String TAG_TYPE = "TYPE";
    public static final String TAG_NAME = "NAME";
    public static final String TAG_VALUE = "VALUE";
    public static final String TAG_MAPPING = "MAPPING";
    public static final String TAG_SESSION = "SESSION";
    public static final String TAG_TRANSFORMATION = "TRANSFORMATION";
    public static final String TAG_INSTANCE = "INSTANCE";
    public static final String TAG_SESSTRANSFORMATIONINST = "SESSTRANSFORMATIONINST";
    public static final String TAG_TABLEATTRIBUTE = "TABLEATTRIBUTE";
    public static final String TAG_ATTRIBUTE = "ATTRIBUTE";
    public static final String TAG_SOURCE_QUALIFIER = "Source Qualifier";
    public static final String TAG_SQL_QUERY = "Sql Query";
    public static final String TAG_PRE_SQL = "Pre SQL";
    public static final String TAG_POST_SQL = "Post SQL";
    public static final String TAG_TRUNCATE = "Truncate target table option";
    public static final String TAG_TRUNCATE_YES = "YES";
    public static final String TAG_SESSIONEXTENSION = "SESSIONEXTENSION";
    public static final String TAG_CONNECTIONREFERENCE = "CONNECTIONREFERENCE";
    public static final String TAG_CONNECTIONNAME = "CONNECTIONNAME";
    public static final String TAG_CONNECTIONTYPE = "CONNECTIONTYPE";
    public static final String TAG_CONNECTIONSUBTYPE = "CONNECTIONSUBTYPE";
    public static final int NODE_GAP = 200;
    private static final String FDL_DATASOURCE_ADAPTER_REQUIRED = "fdl_adapter_required";
    private static final String CONNECTION_TYPE_OVERRIDE_PROPERTY = "infa.fdl.connection.type.overrides";
    private static final String CONNECTION_TYPE_OVERRIDE_ENV = "INFA_FDL_CONNECTION_TYPE_OVERRIDES";
    private static final String CONNECTION_SCHEMA_OVERRIDE_PROPERTY = "infa.fdl.connection.schema.overrides";
    private static final String CONNECTION_SCHEMA_OVERRIDE_ENV = "INFA_FDL_CONNECTION_SCHEMA_OVERRIDES";
    private static final String EMIT_GENERATED_WORK_PARAMS_PROPERTY = "infa.fdl.params.emit.generated.work";
    private static final String EMIT_GENERATED_WORK_PARAMS_ENV = "INFA_FDL_PARAMS_EMIT_GENERATED_WORK";
    private static final String WORKLET_SPLIT_THRESHOLD_PROPERTY = "infa.fdl.worklet.split.task.threshold";
    private static final int DEFAULT_WORKLET_SPLIT_TASK_THRESHOLD = 8;
    private static final Map<String, String> CONNECTION_NAME_DATASOURCE_TYPE_OVERRIDES = new HashMap<String, String>();

    public static DataSyncParseNodeBean parse(Document doc, XPath xpath, TaskEvalBean extraParam) throws Exception {
        doc.getDocumentElement().normalize();
        NodeList workflowElems = (NodeList)xpath.evaluate("//WORKFLOW", doc, XPathConstants.NODESET);
        if (workflowElems == null || workflowElems.getLength() == 0) {
            throw new RuntimeException("No WORKFLOW element found");
        }
        AtomicInteger currentX = new AtomicInteger(-200);
        AtomicInteger currentY = new AtomicInteger(-200);
        String toFolder = extraParam.getToFolder();
        String folder = XmlParserUtil.extractExpression(doc, xpath, "/POWERMART/REPOSITORY/FOLDER/@NAME");
        if (StringUtils.isNotEmpty((String)toFolder)) {
            folder = toFolder + "/" + folder;
        }
        String workName = XmlParserUtil.extractExpression(doc, xpath, "/POWERMART/REPOSITORY/FOLDER/WORKFLOW/@NAME");
        List<com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.Link> allLinks = new ArrayList<com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.Link>();
        for (int i = 0; i < workflowElems.getLength(); i++) {
            Element workflowElem = (Element) workflowElems.item(i);
            allLinks.addAll(XmlParserUtil.getDirectWorkflowLinks(workflowElem));
        }
        return DataSyncParseNodeBean.create(folder, workName).addGroups(DataSyncParser.getMappingGroupNodeInfoBeans(doc, xpath, extraParam, currentX, currentY)).addGroups(DataSyncParser.getSessionGroupNodeInfoBeans(doc, xpath, extraParam, currentX, currentY)).addGroups(DataSyncParser.getWorkletGroupNodeInfoBeans(doc, xpath, extraParam, currentX, currentY)).addLinks(allLinks);
    }

    public static List<DataSyncParseNodeBean> parseAllWorkflows(Document doc, XPath xpath, TaskEvalBean extraParam) throws Exception {
        doc.getDocumentElement().normalize();
        NodeList workflowElems = (NodeList)xpath.evaluate("//WORKFLOW", doc, XPathConstants.NODESET);
        if (workflowElems == null || workflowElems.getLength() == 0) {
            return java.util.Collections.emptyList();
        }
        String toFolder = extraParam.getToFolder();
        String folderName = XmlParserUtil.extractExpression(doc, xpath, "/POWERMART/REPOSITORY/FOLDER/@NAME");
        String folder = StringUtils.isNotEmpty((String)toFolder) ? toFolder + "/" + folderName : folderName;
        String workName = XmlParserUtil.extractExpression(doc, xpath, "/POWERMART/REPOSITORY/FOLDER/WORKFLOW/@NAME");
        NodeList allSessionNodes = doc.getElementsByTagName(TAG_SESSION);
        LogUtil.info("Found " + allSessionNodes.getLength() + " SESSION elements");
        List<GroupSQLInfo> allSessionGroups = DataSyncParser.parseSession(doc, allSessionNodes);
        Map<String, GroupSQLInfo> sessionMap = new HashMap<String, GroupSQLInfo>();
        for (GroupSQLInfo g : allSessionGroups) {
            sessionMap.put(g.getName(), g);
        }
        LogUtil.info("Parsed " + allSessionGroups.size() + " session groups");
        InfaScopedParameterResolver parameterResolver = InfaScopedParameterResolver.fromDocument(doc);
        Map<String, List<ParamEntity>> mappingParamsMap = DataSyncParser.parseAllMappingVariables(parameterResolver);
        NodeList workletNodes = doc.getElementsByTagName("WORKLET");
        Map<String, List<GroupSQLInfo>> workletGroupMap = new HashMap<String, List<GroupSQLInfo>>();
        if (workletNodes.getLength() > 0) {
            workletGroupMap = DataSyncParser.buildWorkletGroups(doc, xpath, extraParam);
        }
        List<DataSyncParseNodeBean> subTasks = new ArrayList<DataSyncParseNodeBean>();
        Map<String, Integer> workletUseCounts = DataSyncParser.countWorkflowWorkletUses(workflowElems);
        Set<String> createdSplitTaskNames = new LinkedHashSet<String>();
        LogUtil.info("Processing " + workflowElems.getLength() + " WORKFLOW elements");
        for (int i = 0; i < workflowElems.getLength(); i++) {
            Element workflowElem = (Element)workflowElems.item(i);
            String wfName = workflowElem.getAttribute(TAG_NAME);
            Set<String> wfSessionNames = DataSyncParser.getWorkflowSessionNames(workflowElem);
            Set<String> wfInlineSessionNames = DataSyncParser.getInlineSessionNames(workflowElem);
            wfSessionNames.addAll(wfInlineSessionNames);
            Set<String> wfWorkletNames = DataSyncParser.getWorkflowWorkletNames(workflowElem);
            LogUtil.info("Workflow [" + wfName + "]: " + wfSessionNames.size() + " sessions (inline=" + wfInlineSessionNames.size() + "), " + wfWorkletNames.size() + " worklets");
            Set<String> splitWorkletNames = DataSyncParser.selectSplitWorklets(doc, wfWorkletNames, workletGroupMap, workletUseCounts);
            Set<String> inlineWorkletNames = new LinkedHashSet<String>(wfWorkletNames);
            inlineWorkletNames.removeAll(splitWorkletNames);
            for (String workletName : splitWorkletNames) {
                String childTaskName = DataSyncParser.splitWorkletTaskName(wfName, workletName, workletUseCounts);
                if (createdSplitTaskNames.contains(childTaskName)) {
                    continue;
                }
                List<GroupSQLInfo> childGroups = DataSyncParser.copyGroupSQLInfos(workletGroupMap.get(workletName));
                for (GroupSQLInfo childGroup : childGroups) {
                    DataSyncParser.substituteSqlParams(childGroup, wfName, workletName, parameterResolver,
                        DataSyncParser.scopeForGroup(doc, wfName, workletName, childGroup));
                }
                AtomicInteger childX = new AtomicInteger(-200);
                AtomicInteger childY = new AtomicInteger(-200);
                List<GroupNodeInfoBean> childNodes = DataSyncParser.getGroupNodeInfoBeans(doc, xpath, extraParam, childGroups, childX, childY, new ArrayList<GroupNodeInfoBean>());
                List<Link> childLinks = DataSyncParser.getSplitWorkletTaskLinks(doc, wfName, workletName, childNodes, childX, childY, parameterResolver);
                List<ParamEntity> childParams = DataSyncParser.createWorkletTaskParams(doc, parameterResolver, mappingParamsMap, wfName, workletName, childGroups);
                subTasks.add(DataSyncParseNodeBean.create(folder, childTaskName).addGroups(childNodes).addLinks(childLinks).addParams(childParams));
                createdSplitTaskNames.add(childTaskName);
                LogUtil.info("Workflow [" + wfName + "] split worklet [" + workletName + "] into CALL_TASK target [" + childTaskName + "]");
            }
            List<GroupSQLInfo> wfGroups = new ArrayList<GroupSQLInfo>();
            for (String name : wfSessionNames) {
                GroupSQLInfo g = sessionMap.get(name);
                if (g != null) {
                    wfGroups.add(DataSyncParser.copyGroupSQLInfo(g));
                }
            }
            for (String name : inlineWorkletNames) {
                List<GroupSQLInfo> wlGroups = workletGroupMap.get(name);
                if (wlGroups != null) {
                    wfGroups.addAll(DataSyncParser.copyGroupSQLInfos(wlGroups));
                }
            }
            for (GroupSQLInfo g : wfGroups) {
                String workletScope = inlineWorkletNames.contains(g.getGroupName()) ? g.getGroupName() : "";
                DataSyncParser.substituteSqlParams(g, wfName, workletScope, parameterResolver,
                    DataSyncParser.scopeForGroup(doc, wfName, workletScope, g));
            }
            AtomicInteger currentX = new AtomicInteger(-200);
            AtomicInteger currentY = new AtomicInteger(-200);
            List<GroupNodeInfoBean> nodes = DataSyncParser.getGroupNodeInfoBeans(doc, xpath, extraParam, wfGroups, currentX, currentY, new ArrayList<GroupNodeInfoBean>());
            for (String workletName : splitWorkletNames) {
                DataSyncParser.addInvokeTaskGroup(nodes, workletName,
                    DataSyncParser.splitWorkletTaskName(wfName, workletName, workletUseCounts), folder, currentX, currentY);
            }
            List<Link> links = XmlParserUtil.getDirectWorkflowLinks(workflowElem);
            Set<String> knownNames = new LinkedHashSet<String>();
            knownNames.addAll(wfSessionNames);
            knownNames.addAll(inlineWorkletNames);
            knownNames.addAll(splitWorkletNames);
            Set<String> conditionNodeNames = DataSyncParser.addDecisionConditionGroups(nodes, workflowElem, links, knownNames, currentX, currentY,
                "Workflow " + wfName, parameterResolver, new InfaScopedParameterResolver.Scope(wfName, "", "", ""));
            links.addAll(DataSyncParser.getConditionalWorkletLinks(doc, wfName, inlineWorkletNames, nodes, knownNames, conditionNodeNames, currentX, currentY, parameterResolver));
            links = DataSyncParser.resolveConnectorLinks(links, knownNames);
            links = DataSyncParser.materializeConditionalLinks(nodes, links, knownNames, conditionNodeNames, currentX, currentY, wfName);
            List<ParamEntity> wfParams = new ArrayList<ParamEntity>();
            wfParams.addAll(DataSyncParser.parseWorkflowVariables(parameterResolver, wfName, ""));
            Set<String> resolvedMappings = new HashSet<String>();
            DataSyncParser.addMappingParamsForSessions(doc, wfSessionNames, mappingParamsMap, resolvedMappings, wfParams);
            for (String workletName : inlineWorkletNames) {
                wfParams.addAll(DataSyncParser.parseWorkflowVariables(parameterResolver, wfName, workletName));
                List<GroupSQLInfo> wlGroups = workletGroupMap.get(workletName);
                if (wlGroups == null) continue;
                DataSyncParser.addMappingParamsForGroupSessions(doc, wlGroups, mappingParamsMap, resolvedMappings, wfParams);
            }
            DataSyncParseNodeBean subTask = DataSyncParseNodeBean.create(folder, wfName).addGroups(nodes).addLinks(links).addParams(DataSyncParser.filterGeneratedWorkParams(wfParams));
            subTasks.add(subTask);
        }
        return subTasks;
    }

    private static Map<String, Integer> countWorkflowWorkletUses(NodeList workflowElems) {
        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        if (workflowElems == null) {
            return result;
        }
        for (int i = 0; i < workflowElems.getLength(); i++) {
            Element workflowElem = (Element)workflowElems.item(i);
            for (String workletName : DataSyncParser.getWorkflowWorkletNames(workflowElem)) {
                Integer count = result.get(workletName);
                result.put(workletName, count == null ? 1 : count + 1);
            }
        }
        return result;
    }

    private static Set<String> selectSplitWorklets(Document doc, Set<String> workletNames,
        Map<String, List<GroupSQLInfo>> workletGroupMap, Map<String, Integer> workletUseCounts) {
        Set<String> result = new LinkedHashSet<String>();
        int threshold = DataSyncParser.getWorkletSplitThreshold();
        int expandedSessionCount = 0;
        for (String workletName : workletNames) {
            List<GroupSQLInfo> groups = workletGroupMap.get(workletName);
            expandedSessionCount += groups == null ? 0 : groups.size();
        }
        boolean parentLarge = workletNames.size() >= threshold || expandedSessionCount >= threshold;
        for (String workletName : workletNames) {
            Element workletElem = DataSyncParser.findWorkletElement(doc, workletName);
            List<GroupSQLInfo> groups = workletGroupMap.get(workletName);
            if (workletElem == null || groups == null || groups.isEmpty()) {
                continue;
            }
            int useCount = DataSyncParser.getMapCount(workletUseCounts, workletName);
            if (parentLarge || DataSyncParser.shouldSplitWorklet(workletElem, groups, useCount, threshold)) {
                result.add(workletName);
            }
        }
        return result;
    }

    private static boolean shouldSplitWorklet(Element workletElem, List<GroupSQLInfo> groups, int useCount, int threshold) {
        Map<String, String> directTaskTypes = DataSyncParser.getDirectTaskInstanceTypes(workletElem);
        if (groups.size() >= threshold || directTaskTypes.size() >= threshold) {
            return true;
        }
        if (useCount > 1) {
            return true;
        }
        if (directTaskTypes.containsValue("Worklet")) {
            return true;
        }
        if (DataSyncParser.hasConditionOrDecision(workletElem, XmlParserUtil.getDirectWorkflowLinks(workletElem))) {
            return true;
        }
        for (GroupSQLInfo group : groups) {
            if (group != null && group.getApiConfig() != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldSplitWorkletSessions(Element workletElem, List<GroupSQLInfo> groups, int threshold) {
        if (workletElem == null || groups == null || groups.isEmpty()) {
            return false;
        }
        if (groups.size() < threshold) {
            return false;
        }
        int directSessionCount = 0;
        Map<String, String> directTaskTypes = DataSyncParser.getDirectTaskInstanceTypes(workletElem);
        for (String taskType : directTaskTypes.values()) {
            if ("Session".equals(taskType)) {
                directSessionCount++;
            }
        }
        return directSessionCount >= threshold;
    }

    private static int getWorkletSplitThreshold() {
        Integer value = Integer.getInteger(WORKLET_SPLIT_THRESHOLD_PROPERTY, DEFAULT_WORKLET_SPLIT_TASK_THRESHOLD);
        if (value == null || value.intValue() < 1) {
            return DEFAULT_WORKLET_SPLIT_TASK_THRESHOLD;
        }
        return value.intValue();
    }

    private static int getMapCount(Map<String, Integer> values, String key) {
        Integer value = values.get(key);
        return value == null ? 0 : value.intValue();
    }

    private static String splitWorkletTaskName(String workflowName, String workletName, Map<String, Integer> workletUseCounts) {
        if (DataSyncParser.getMapCount(workletUseCounts, workletName) > 1 && StringUtils.isNotEmpty(workflowName)) {
            return workflowName + "__" + workletName;
        }
        return workletName;
    }

    private static void addInvokeTaskGroup(List<GroupNodeInfoBean> nodes, String workletName, String targetTaskName,
        AtomicInteger currentX, AtomicInteger currentY) {
        DataSyncParser.addInvokeTaskGroup(nodes, workletName, targetTaskName, "", currentX, currentY);
    }

    private static void addInvokeTaskGroup(List<GroupNodeInfoBean> nodes, String workletName, String targetTaskName,
        String targetFolder, AtomicInteger currentX, AtomicInteger currentY) {
        DataSyncParser.addInvokeTaskGroup(nodes, workletName, workletName, targetTaskName, targetFolder, currentX, currentY);
    }

    private static void addInvokeTaskGroup(List<GroupNodeInfoBean> nodes, String groupName, String nodeName, String targetTaskName,
        String targetFolder,
        AtomicInteger currentX, AtomicInteger currentY) {
        DefaultGroupNodeInfoBean group = new DefaultGroupNodeInfoBean(groupName);
        currentY.set(currentY.get() + 200);
        currentX.set(-200);
        currentX.set(currentX.get() + 240);
        InvokeTaskNodeBean invokeNode = new InvokeTaskNodeBean(currentX.get(), currentY.get(), nodeName, UUID.randomUUID().toString(), targetTaskName, "", targetFolder);
        invokeNode.setSpreadParam(false);
        group.addNode(invokeNode);
        nodes.add(group);
    }

    private static List<Link> getSplitWorkletTaskLinks(Document doc, String workflowName, String workletName,
        List<GroupNodeInfoBean> nodes, AtomicInteger currentX, AtomicInteger currentY,
        InfaScopedParameterResolver parameterResolver) {
        Element workletElem = DataSyncParser.findWorkletElement(doc, workletName);
        if (workletElem == null) {
            return new ArrayList<Link>();
        }
        List<Link> links = XmlParserUtil.getDirectWorkflowLinks(workletElem);
        Set<String> knownNames = DataSyncParser.getDirectTaskNames(workletElem);
        Set<String> conditionNodeNames = DataSyncParser.addDecisionConditionGroups(nodes, workletElem, links, knownNames,
            currentX, currentY, "Worklet " + workletName, parameterResolver,
            new InfaScopedParameterResolver.Scope(workflowName, workletName, "", ""));
        links = DataSyncParser.resolveConnectorLinks(links, knownNames);
        links = DataSyncParser.materializeConditionalLinks(nodes, links, knownNames, conditionNodeNames, currentX, currentY, workletName);
        return links;
    }

    private static List<ParamEntity> createWorkletTaskParams(Document doc, InfaScopedParameterResolver parameterResolver,
        Map<String, List<ParamEntity>> mappingParamsMap, String workflowName, String workletName, List<GroupSQLInfo> groups) {
        List<ParamEntity> params = new ArrayList<ParamEntity>();
        params.addAll(DataSyncParser.parseWorkflowVariables(parameterResolver, workflowName, ""));
        params.addAll(DataSyncParser.parseWorkflowVariables(parameterResolver, workflowName, workletName));
        Set<String> resolvedMappings = new HashSet<String>();
        DataSyncParser.addMappingParamsForGroupSessions(doc, groups, mappingParamsMap, resolvedMappings, params);
        return DataSyncParser.filterGeneratedWorkParams(params);
    }

    private static void addMappingParamsForGroupSessions(Document doc, List<GroupSQLInfo> groups,
        Map<String, List<ParamEntity>> mappingParamsMap, Set<String> resolvedMappings, List<ParamEntity> params) {
        Set<String> sessionNames = new LinkedHashSet<String>();
        if (groups != null) {
            for (GroupSQLInfo group : groups) {
                if (group != null && StringUtils.isNotEmpty(group.getName())) {
                    sessionNames.add(group.getName());
                }
            }
        }
        DataSyncParser.addMappingParamsForSessions(doc, sessionNames, mappingParamsMap, resolvedMappings, params);
    }

    private static void addMappingParamsForSessions(Document doc, Set<String> sessionNames,
        Map<String, List<ParamEntity>> mappingParamsMap, Set<String> resolvedMappings, List<ParamEntity> params) {
        NodeList allSessions = doc.getElementsByTagName(TAG_SESSION);
        for (String sessionName : sessionNames) {
            for (int s = 0; s < allSessions.getLength(); s++) {
                Element sessionEl = (Element) allSessions.item(s);
                if (!sessionName.equals(sessionEl.getAttribute(TAG_NAME))) {
                    continue;
                }
                String mappingName = sessionEl.getAttribute("MAPPINGNAME");
                if (StringUtils.isNotEmpty(mappingName) && resolvedMappings.add(mappingName)) {
                    List<ParamEntity> mappingParams = mappingParamsMap.get(mappingName);
                    if (mappingParams != null) {
                        params.addAll(mappingParams);
                    }
                }
                break;
            }
        }
    }

    private static List<GroupSQLInfo> copyGroupSQLInfos(List<GroupSQLInfo> source) {
        List<GroupSQLInfo> result = new ArrayList<GroupSQLInfo>();
        if (source == null) {
            return result;
        }
        for (GroupSQLInfo group : source) {
            GroupSQLInfo copy = DataSyncParser.copyGroupSQLInfo(group);
            if (copy != null) {
                result.add(copy);
            }
        }
        return result;
    }

    private static GroupSQLInfo copyGroupSQLInfo(GroupSQLInfo source) {
        if (source == null) {
            return null;
        }
        GroupSQLInfo copy = new GroupSQLInfo();
        copy.setGroupName(source.getGroupName());
        copy.setName(source.getName());
        copy.setFromDBName(source.getFromDBName());
        copy.setFromDBType(source.getFromDBType());
        copy.setToDBName(source.getToDBName());
        copy.setToDBType(source.getToDBType());
        copy.setTargetTable(source.getTargetTable());
        copy.setTargetSchema(source.getTargetSchema());
        copy.setApiConfig(source.getApiConfig());
        copy.setFieldLineageInfo(source.getFieldLineageInfo());
        copy.setTargetWriteInfo(source.getTargetWriteInfo());
        List<SQLInfo> sqlInfos = new ArrayList<SQLInfo>();
        for (SQLInfo sqlInfo : source.getSqlInfos()) {
            sqlInfos.add(DataSyncParser.copySQLInfo(sqlInfo));
        }
        copy.setSqlInfos(sqlInfos);
        for (ConnectionMappingInfo connectionInfo : source.getConnectionInfos()) {
            copy.addConnectionInfo(connectionInfo);
        }
        return copy;
    }

    private static SQLInfo copySQLInfo(SQLInfo source) {
        SQLInfo copy = new SQLInfo();
        if (source == null) {
            return copy;
        }
        copy.setSqlQuery(source.getSqlQuery());
        copy.setPreSQL(source.getPreSQL());
        copy.setPostSQL(source.getPostSQL());
        copy.setTruncate(source.getTruncate());
        return copy;
    }

    private static Set<String> getWorkflowSessionNames(Element workflowElem) {
        Set<String> names = new LinkedHashSet<String>();
        NodeList taskInstances = workflowElem.getElementsByTagName("TASKINSTANCE");
        for (int i = 0; i < taskInstances.getLength(); i++) {
            Element ti = (Element)taskInstances.item(i);
            if ("Session".equals(ti.getAttribute("TASKTYPE"))) {
                String taskName = ti.getAttribute("TASKNAME");
                if (StringUtils.isNotEmpty(taskName)) {
                    names.add(taskName);
                }
            }
        }
        return names;
    }

    private static Set<String> getInlineSessionNames(Element workflowElem) {
        Set<String> names = new HashSet<String>();
        NodeList children = workflowElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElem = (Element) child;
            if (!TAG_SESSION.equals(childElem.getTagName())) {
                continue;
            }
            String name = childElem.getAttribute(TAG_NAME);
            if (StringUtils.isNotEmpty(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private static Set<String> getWorkflowWorkletNames(Element workflowElem) {
        Set<String> names = new LinkedHashSet<String>();
        NodeList taskInstances = workflowElem.getElementsByTagName("TASKINSTANCE");
        for (int i = 0; i < taskInstances.getLength(); i++) {
            Element ti = (Element)taskInstances.item(i);
            if ("Worklet".equals(ti.getAttribute("TASKTYPE"))) {
                String taskName = ti.getAttribute("TASKNAME");
                if (StringUtils.isNotEmpty(taskName)) {
                    names.add(taskName);
                }
            }
        }
        return names;
    }

    private static List<Link> resolveConnectorLinks(List<Link> links, Set<String> knownGroupNames) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> connectorTasks = new LinkedHashSet<String>();
            for (Link link : links) {
                if (!knownGroupNames.contains(link.getFrom())) {
                    connectorTasks.add(link.getFrom());
                }
                if (!knownGroupNames.contains(link.getTo())) {
                    connectorTasks.add(link.getTo());
                }
            }
            if (connectorTasks.isEmpty()) {
                break;
            }
            Map<String, List<Link>> preds = new HashMap<String, List<Link>>();
            Map<String, List<Link>> succs = new HashMap<String, List<Link>>();
            for (Link link : links) {
                if (connectorTasks.contains(link.getFrom())) {
                    if (!succs.containsKey(link.getFrom())) {
                        succs.put(link.getFrom(), new ArrayList<Link>());
                    }
                    succs.get(link.getFrom()).add(link);
                }
                if (connectorTasks.contains(link.getTo())) {
                    if (!preds.containsKey(link.getTo())) {
                        preds.put(link.getTo(), new ArrayList<Link>());
                    }
                    preds.get(link.getTo()).add(link);
                }
            }
            List<Link> resolvedLinks = new ArrayList<Link>();
            for (Link link : links) {
                if (connectorTasks.contains(link.getFrom()) || connectorTasks.contains(link.getTo())) {
                    changed = true;
                    continue;
                }
                resolvedLinks.add(link);
            }
            for (String connector : connectorTasks) {
                List<Link> p = preds.get(connector);
                List<Link> s = succs.get(connector);
                if (p != null && s != null) {
                    for (Link pred : p) {
                        for (Link succ : s) {
                            resolvedLinks.add(new Link(pred.getFrom(), succ.getTo(), DataSyncParser.mergeConditions(pred.getCondition(), succ.getCondition())));
                        }
                    }
                }
            }
            links = resolvedLinks;
        }
        return links;
    }

    private static String mergeConditions(String left, String right) {
        boolean hasLeft = StringUtils.isNotEmpty(left);
        boolean hasRight = StringUtils.isNotEmpty(right);
        if (hasLeft && hasRight) {
            if (left.trim().equals(right.trim())) {
                return left.trim();
            }
            return "(" + left.trim() + ") AND (" + right.trim() + ")";
        }
        if (hasLeft) {
            return left.trim();
        }
        if (hasRight) {
            return right.trim();
        }
        return "";
    }

    private static Set<String> addDecisionConditionGroups(List<GroupNodeInfoBean> nodes, Element containerElem, List<Link> links,
        Set<String> knownNames, AtomicInteger currentX, AtomicInteger currentY, String scopeName,
        InfaScopedParameterResolver parameterResolver, InfaScopedParameterResolver.Scope parameterScope) {
        Set<String> conditionNodeNames = new LinkedHashSet<String>();
        Map<String, String> taskTypes = DataSyncParser.getDirectTaskInstanceTypes(containerElem);
        for (Map.Entry<String, String> entry : taskTypes.entrySet()) {
            String taskName = entry.getKey();
            String taskType = entry.getValue();
            if (!"Decision".equals(taskType) || !DataSyncParser.isReferencedByLinks(taskName, links)) {
                continue;
            }
            DecisionConditionContext context = DataSyncParser.createDecisionConditionContext(containerElem, taskName, scopeName, links,
                parameterResolver, parameterScope);
            DataSyncParser.addConditionNodeGroup(nodes, taskName, context.description, currentX, currentY,
                context.nativeDecisionOutputExecutable, context.nativeDecisionOutputName, context.nativeDecisionOutputReason);
            knownNames.add(taskName);
            conditionNodeNames.add(taskName);
        }
        return conditionNodeNames;
    }

    private static DecisionConditionContext createDecisionConditionContext(Element containerElem, String taskName, String scopeName, List<Link> links,
        InfaScopedParameterResolver parameterResolver, InfaScopedParameterResolver.Scope parameterScope) {
        StringBuilder description = new StringBuilder();
        description.append("Informatica Decision: ").append(scopeName).append(" / ").append(taskName);
        Element taskElement = DataSyncParser.findDirectTaskElement(containerElem, taskName, "Decision");
        if (taskElement == null) {
            description.append("\nDecision metadata status: ").append(ConditionNodeBean.STATUS_REVIEW_REQUIRED);
            description.append(" - TASK metadata was not found; condition branch is visible but Decision output equivalence must be reviewed.");
            return new DecisionConditionContext(description.toString());
        }
        String decisionExpression = DataSyncParser.findTaskAttribute(taskElement, "Decision Name");
        if (!StringUtils.isNotEmpty(decisionExpression)) {
            decisionExpression = DataSyncParser.findTaskAttribute(taskElement, "Decision Expression");
        }
        if (!StringUtils.isNotEmpty(decisionExpression)) {
            description.append("\nDecision metadata status: ").append(ConditionNodeBean.STATUS_VISIBLE_ONLY);
            description.append(" - no Decision expression attribute was parsed.");
            return new DecisionConditionContext(description.toString());
        }
        InfaScopedParameterResolver.Replacement replacement = DataSyncParser.replaceScopedParameters(parameterResolver, decisionExpression, parameterScope);
        String analysisExpression = replacement != null && !replacement.requiresRuntimeBinding()
            ? replacement.getReplacedText()
            : decisionExpression;
        ConditionNodeBean.ConditionAnalysis analysis = ConditionNodeBean.analyzeCondition(analysisExpression);
        description.append("\nDecision expression: ").append(DataSyncParser.sanitizeConfigValue(decisionExpression));
        if (replacement != null && !decisionExpression.equals(replacement.getReplacedText())) {
            description.append("\nDecision expression after XML scoped parameter replacement: ")
                .append(DataSyncParser.sanitizeConfigValue(replacement.getReplacedText()));
        }
        description.append("\nDecision expression status: ").append(analysis.getStatus());
        if (!analysis.getClassification().equals(analysis.getStatus())) {
            description.append(" classification=").append(analysis.getClassification());
        }
        if (StringUtils.isNotEmpty(analysis.getLimitation())) {
            description.append(" - ").append(analysis.getLimitation());
        }
        if (StringUtils.isNotEmpty(analysis.getRuntimeDetails())) {
            description.append("\nParsed task status predicates: ").append(analysis.getRuntimeDetails());
        }
        if (StringUtils.isNotEmpty(analysis.getRequiredRuntimeBindingsText())) {
            description.append("\nRequired Decision adapter bindings: ").append(analysis.getRequiredRuntimeBindingsText());
        }
        DataSyncParser.appendParameterReplacementEvidence(description, replacement);
        String nativeReason = DataSyncParser.resolveNativeDecisionSuccessGate(containerElem, taskName, decisionExpression, analysis, links);
        if (StringUtils.isNotEmpty(nativeReason)) {
            description.append("\nDecision native FDL gate status: ").append(ConditionNodeBean.STATUS_EXECUTABLE);
            description.append(" classification=").append(ConditionNodeBean.CLASSIFICATION_EXECUTABLE_TASK_STATUS_CONDITION);
            description.append(" - ").append(nativeReason);
            return new DecisionConditionContext(description.toString(), true, taskName, nativeReason);
        }
        if (!ConditionNodeBean.STATUS_EXECUTABLE.equals(analysis.getStatus())) {
            description.append("\nDecision adapter: review required before treating this as runnable-equivalent FDL branching.");
        }
        return new DecisionConditionContext(description.toString());
    }

    private static String resolveNativeDecisionSuccessGate(Element containerElem, String taskName, String decisionExpression,
        ConditionNodeBean.ConditionAnalysis analysis, List<Link> links) {
        if (containerElem == null || !StringUtils.isNotEmpty(taskName) || !StringUtils.isNotEmpty(decisionExpression) || analysis == null) {
            return "";
        }
        if (!analysis.getDecisionOutputReferences().isEmpty() || analysis.getTaskStatusPredicates().isEmpty()) {
            return "";
        }
        if (DataSyncParser.splitTopLevelCondition(decisionExpression, "OR").size() > 1) {
            return "";
        }
        List<String> andParts = DataSyncParser.splitTopLevelCondition(decisionExpression, "AND");
        if (andParts.size() != analysis.getTaskStatusPredicates().size()) {
            return "";
        }
        Set<String> predicateTasks = new LinkedHashSet<String>();
        for (ConditionNodeBean.TaskStatusPredicate predicate : analysis.getTaskStatusPredicates()) {
            if (!"=".equals(predicate.getOperator()) && !"==".equals(predicate.getOperator())) {
                return "";
            }
            if (!"SUCCEEDED".equals(predicate.getNormalizedStatusLiteral())) {
                return "";
            }
            if (!StringUtils.isNotEmpty(predicate.getTaskName())) {
                return "";
            }
            predicateTasks.add(DataSyncParser.canonicalName(predicate.getTaskName()));
        }
        if (predicateTasks.size() != analysis.getTaskStatusPredicates().size()) {
            return "";
        }
        Element taskInstance = DataSyncParser.findDirectTaskInstanceElement(containerElem, taskName);
        if (taskInstance == null) {
            return "";
        }
        String inputLogic = taskInstance.getAttribute("TREAT_INPUTLINK_AS_AND");
        if (StringUtils.isNotEmpty(inputLogic) && !"YES".equalsIgnoreCase(inputLogic)) {
            return "";
        }
        Set<String> incomingTasks = new LinkedHashSet<String>();
        for (Link link : links) {
            if (!taskName.equals(link.getTo())) {
                continue;
            }
            if (link.hasCondition()) {
                return "";
            }
            incomingTasks.add(DataSyncParser.canonicalName(link.getFrom()));
        }
        if (incomingTasks.isEmpty() || incomingTasks.size() != predicateTasks.size() || !incomingTasks.equals(predicateTasks)) {
            return "";
        }
        return "all Decision predicates are equality checks for upstream task succeeded status, all predicate tasks have direct empty incoming links, and the Decision TASKINSTANCE uses AND input semantics; FDL relations use ON_SUCCESS for those incoming links.";
    }

    private static Element findDirectTaskElement(Element containerElem, String taskName, String taskType) {
        NodeList children = containerElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElem = (Element) child;
            if (!"TASK".equals(childElem.getTagName())) {
                continue;
            }
            if (!taskName.equals(childElem.getAttribute(TAG_NAME))) {
                continue;
            }
            if (StringUtils.isNotEmpty(taskType) && !taskType.equals(childElem.getAttribute(TAG_TYPE))) {
                continue;
            }
            return childElem;
        }
        return null;
    }

    private static Element findDirectTaskInstanceElement(Element containerElem, String taskName) {
        NodeList children = containerElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElem = (Element) child;
            if (!"TASKINSTANCE".equals(childElem.getTagName())) {
                continue;
            }
            String name = childElem.getAttribute(TAG_NAME);
            if (!StringUtils.isNotEmpty(name)) {
                name = childElem.getAttribute("TASKNAME");
            }
            if (taskName.equals(name)) {
                return childElem;
            }
        }
        return null;
    }

    private static String findTaskAttribute(Element taskElement, String attributeName) {
        NodeList children = taskElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElem = (Element) child;
            if (!"ATTRIBUTE".equals(childElem.getTagName())) {
                continue;
            }
            if (attributeName.equalsIgnoreCase(childElem.getAttribute(TAG_NAME))) {
                return childElem.getAttribute(TAG_VALUE);
            }
        }
        return "";
    }

    private static List<Link> getConditionalWorkletLinks(Document doc, String workflowName, Set<String> workletNames,
        List<GroupNodeInfoBean> nodes, Set<String> knownNames, Set<String> conditionNodeNames,
        AtomicInteger currentX, AtomicInteger currentY, InfaScopedParameterResolver parameterResolver) {
        List<Link> result = new ArrayList<Link>();
        for (String workletName : workletNames) {
            Element workletElem = DataSyncParser.findWorkletElement(doc, workletName);
            if (workletElem == null) {
                continue;
            }
            List<Link> directLinks = XmlParserUtil.getDirectWorkflowLinks(workletElem);
            if (!DataSyncParser.hasConditionOrDecision(workletElem, directLinks)) {
                continue;
            }
            Set<String> workletKnownNames = DataSyncParser.getDirectTaskNames(workletElem);
            Set<String> addedConditionNodes = DataSyncParser.addDecisionConditionGroups(nodes, workletElem, directLinks, workletKnownNames,
                currentX, currentY, "Worklet " + workletName, parameterResolver,
                new InfaScopedParameterResolver.Scope(workflowName, workletName, "", ""));
            knownNames.addAll(workletKnownNames);
            conditionNodeNames.addAll(addedConditionNodes);
            result.addAll(DataSyncParser.resolveConnectorLinks(directLinks, workletKnownNames));
        }
        return result;
    }

    private static boolean hasConditionOrDecision(Element containerElem, List<Link> links) {
        for (Link link : links) {
            if (link.hasCondition()) {
                return true;
            }
        }
        Map<String, String> taskTypes = DataSyncParser.getDirectTaskInstanceTypes(containerElem);
        return taskTypes.containsValue("Decision");
    }

    private static List<Link> materializeConditionalLinks(List<GroupNodeInfoBean> nodes, List<Link> links, Set<String> knownNames, Set<String> conditionNodeNames, AtomicInteger currentX, AtomicInteger currentY, String scopeName) {
        List<Link> result = new ArrayList<Link>();
        int counter = 0;
        for (Link link : links) {
            if (link.hasCondition() && !conditionNodeNames.contains(link.getFrom())) {
                if (ConditionNodeBean.isNativeFdlLineConditionSupported(link.getCondition(), link.getFrom())) {
                    result.add(link);
                    continue;
                }
                String conditionNodeName = DataSyncParser.uniqueConditionNodeName(knownNames, scopeName, link, counter++);
                DataSyncParser.addConditionNodeGroup(nodes, conditionNodeName, "Informatica conditional link: " + link.getFrom() + " -> " + link.getTo(), currentX, currentY);
                knownNames.add(conditionNodeName);
                conditionNodeNames.add(conditionNodeName);
                result.add(new Link(link.getFrom(), conditionNodeName));
                result.add(new Link(conditionNodeName, link.getTo(), link.getCondition()));
                continue;
            }
            result.add(link);
        }
        return result;
    }

    private static List<String> splitTopLevelCondition(String condition, String operator) {
        List<String> result = new ArrayList<String>();
        String source = condition == null ? "" : condition;
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int last = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (inSingleQuote || inDoubleQuote) {
                continue;
            }
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')' && depth > 0) {
                depth--;
                continue;
            }
            if (depth == 0 && DataSyncParser.startsWithConditionWord(source, i, operator)) {
                result.add(source.substring(last, i).trim());
                i += operator.length() - 1;
                last = i + 1;
            }
        }
        if (last == 0) {
            result.add(source.trim());
        } else {
            result.add(source.substring(last).trim());
        }
        return result;
    }

    private static boolean startsWithConditionWord(String source, int index, String word) {
        int end = index + word.length();
        if (end > source.length() || !source.regionMatches(true, index, word, 0, word.length())) {
            return false;
        }
        boolean leftOk = index == 0 || !DataSyncParser.isConditionWordChar(source.charAt(index - 1));
        boolean rightOk = end == source.length() || !DataSyncParser.isConditionWordChar(source.charAt(end));
        return leftOk && rightOk;
    }

    private static boolean isConditionWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.';
    }

    private static String canonicalName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String uniqueConditionNodeName(Set<String> knownNames, String scopeName, Link link, int counter) {
        String base = "COND_" + DataSyncParser.safeNodeName(scopeName) + "_" + DataSyncParser.safeNodeName(link.getFrom()) + "_TO_" + DataSyncParser.safeNodeName(link.getTo());
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        String candidate = base;
        int suffix = counter;
        while (knownNames.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private static String safeNodeName(String value) {
        if (value == null) {
            return "condition";
        }
        String result = value.replaceAll("[\\s/\\\\:]+", "_");
        result = result.replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fa5.-]+", "");
        return result.length() == 0 ? "condition" : result;
    }

    private static void addConditionNodeGroup(List<GroupNodeInfoBean> nodes, String nodeName, String description, AtomicInteger currentX, AtomicInteger currentY) {
        DataSyncParser.addConditionNodeGroup(nodes, nodeName, description, currentX, currentY, false, "", "");
    }

    private static void addConditionNodeGroup(List<GroupNodeInfoBean> nodes, String nodeName, String description,
        AtomicInteger currentX, AtomicInteger currentY, boolean nativeDecisionOutputExecutable,
        String nativeDecisionOutputName, String nativeDecisionOutputReason) {
        currentX.set(currentX.get() + NODE_GAP);
        currentY.set(currentY.get() + NODE_GAP);
        DefaultGroupNodeInfoBean group = new DefaultGroupNodeInfoBean(nodeName);
        group.addNode(new ConditionNodeBean(currentX.get(), currentY.get(), nodeName, UUID.randomUUID().toString(), description,
            nativeDecisionOutputExecutable, nativeDecisionOutputName, nativeDecisionOutputReason));
        nodes.add(group);
    }

    private static class DecisionConditionContext {
        private final String description;
        private final boolean nativeDecisionOutputExecutable;
        private final String nativeDecisionOutputName;
        private final String nativeDecisionOutputReason;

        DecisionConditionContext(String description) {
            this(description, false, "", "");
        }

        DecisionConditionContext(String description, boolean nativeDecisionOutputExecutable,
            String nativeDecisionOutputName, String nativeDecisionOutputReason) {
            this.description = description == null ? "" : description;
            this.nativeDecisionOutputExecutable = nativeDecisionOutputExecutable;
            this.nativeDecisionOutputName = nativeDecisionOutputName == null ? "" : nativeDecisionOutputName;
            this.nativeDecisionOutputReason = nativeDecisionOutputReason == null ? "" : nativeDecisionOutputReason;
        }
    }

    private static boolean isReferencedByLinks(String taskName, List<Link> links) {
        for (Link link : links) {
            if (taskName.equals(link.getFrom()) || taskName.equals(link.getTo())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> getDirectTaskInstanceTypes(Element containerElem) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        NodeList children = containerElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElem = (Element)child;
            if (!"TASKINSTANCE".equals(childElem.getTagName())) {
                continue;
            }
            String name = childElem.getAttribute(TAG_NAME);
            if (!StringUtils.isNotEmpty(name)) {
                name = childElem.getAttribute("TASKNAME");
            }
            String taskType = childElem.getAttribute("TASKTYPE");
            if (StringUtils.isNotEmpty(name)) {
                result.put(name, taskType);
            }
        }
        return result;
    }

    private static Set<String> getDirectTaskNames(Element containerElem) {
        Set<String> result = new LinkedHashSet<String>();
        Map<String, String> taskTypes = DataSyncParser.getDirectTaskInstanceTypes(containerElem);
        for (String name : taskTypes.keySet()) {
            result.add(name);
        }
        return result;
    }

    private static Element findWorkletElement(Document doc, String workletName) {
        NodeList workletNodes = doc.getElementsByTagName("WORKLET");
        for (int i = 0; i < workletNodes.getLength(); i++) {
            Element workletElem = (Element)workletNodes.item(i);
            if (workletName.equals(workletElem.getAttribute(TAG_NAME))) {
                return workletElem;
            }
        }
        return null;
    }

    private static Map<String, List<GroupSQLInfo>> buildWorkletGroups(Document doc, XPath xpath, TaskEvalBean extraParam) {
        Map<String, List<GroupSQLInfo>> result = new HashMap<String, List<GroupSQLInfo>>();
        NodeList workletNodes = doc.getElementsByTagName("WORKLET");
        for (int i = 0; i < workletNodes.getLength(); i++) {
            Element workletEl = (Element)workletNodes.item(i);
            String workletName = workletEl.getAttribute("NAME");
            NodeList taskInstances = workletEl.getElementsByTagName("TASKINSTANCE");
            List<GroupSQLInfo> groups = new ArrayList<GroupSQLInfo>();
            for (int j = 0; j < taskInstances.getLength(); j++) {
                Element ti = (Element)taskInstances.item(j);
                String taskType = ti.getAttribute("TASKTYPE");
                if (!"Session".equals(taskType)) {
                    continue;
                }
                String sessionRefName = ti.getAttribute("TASKNAME");
                if (!StringUtils.isNotEmpty(sessionRefName)) {
                    continue;
                }
                NodeList allSessions = doc.getElementsByTagName("SESSION");
                for (int k = 0; k < allSessions.getLength(); k++) {
                    Element sessionEl = (Element)allSessions.item(k);
                    if (!sessionRefName.equals(sessionEl.getAttribute("NAME"))) {
                        continue;
                    }
                    GroupSQLInfo groupInfo = new GroupSQLInfo();
                    groupInfo.setGroupName(workletName);
                    groupInfo.setName(sessionRefName);
                    DataSyncParser.parseConnectionReferences(doc, sessionEl, groupInfo);
                    String mappingRefName = sessionEl.getAttribute("MAPPINGNAME");
                    DataSyncParser.applyFieldLineageInfo(groupInfo, doc, mappingRefName);
                    DataSyncParser.applyTargetWriteInfo(groupInfo, doc, sessionEl, mappingRefName);
                    NodeList transNodes = sessionEl.getElementsByTagName("SESSTRANSFORMATIONINST");
                    boolean foundSqlQuery = false;
                    for (int m = 0; m < transNodes.getLength(); m++) {
                        Element transEl = (Element)transNodes.item(m);
                        NodeList tableAttrs = transEl.getElementsByTagName("ATTRIBUTE");
                        SQLInfo sqlInfo = new SQLInfo();
                        for (int n = 0; n < tableAttrs.getLength(); n++) {
                            Element tableAttr = (Element)tableAttrs.item(n);
                            String attrName = tableAttr.getAttribute("NAME");
                            String attrValue = tableAttr.getAttribute("VALUE");
                            switch (attrName) {
                                case "Sql Query":
                                    sqlInfo.setSqlQuery(attrValue);
                                    if (StringUtils.isNotEmpty(attrValue)) {
                                        foundSqlQuery = true;
                                    }
                                    break;
                                case "Pre SQL":
                                    sqlInfo.setPreSQL(attrValue);
                                    break;
                                case "Post SQL":
                                    sqlInfo.setPostSQL(attrValue);
                                    break;
                                case "Truncate target table option":
                                    if (StringUtils.equals(attrValue, "YES")) {
                                        sqlInfo.setTruncate(true);
                                    }
                                    break;
                            }
                        }
                        groupInfo.getSqlInfos().add(sqlInfo);
                        if (!StringUtils.isNotEmpty(groupInfo.getTargetTable())) {
                            for (int n = 0; n < tableAttrs.getLength(); n++) {
                                Element tableAttr = (Element)tableAttrs.item(n);
                                if ("Target Table Name".equals(tableAttr.getAttribute("NAME"))) {
                                    String targetTableName = tableAttr.getAttribute("VALUE");
                                    if (StringUtils.isNotEmpty(targetTableName)) {
                                        groupInfo.setTargetTable(targetTableName);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    // Fallback 2: use TRANSFORMATIONNAME from Target Definition SESSTRANSFORMATIONINST
                    if (!StringUtils.isNotEmpty(groupInfo.getTargetTable())) {
                        NodeList wlTransNodes = sessionEl.getElementsByTagName("SESSTRANSFORMATIONINST");
                        for (int t = 0; t < wlTransNodes.getLength(); t++) {
                            Element transEl = (Element) wlTransNodes.item(t);
                            if ("Target Definition".equals(transEl.getAttribute("TRANSFORMATIONTYPE"))) {
                                String transName = transEl.getAttribute("TRANSFORMATIONNAME");
                                if (StringUtils.isNotEmpty(transName)) {
                                    groupInfo.setTargetTable(transName);
                                    LogUtil.info("buildWorkletGroups: [" + sessionRefName + "] targetTable from SESSTRANSFORMATIONINST TRANSFORMATIONNAME=" + transName);
                                }
                                break;
                            }
                        }
                    }
                    if (!StringUtils.isNotEmpty(groupInfo.getTargetTable()) && StringUtils.isNotEmpty(mappingRefName)) {
                        LogUtil.info("buildWorkletGroups: [" + sessionRefName + "] falling back to extractTargetInfoFromMapping, mappingRefName=" + mappingRefName);
                        DataSyncParser.extractTargetInfoFromMapping(groupInfo, doc, mappingRefName);
                        LogUtil.info("buildWorkletGroups: [" + sessionRefName + "] after extractTargetInfoFromMapping, targetTable=" + groupInfo.getTargetTable());
                    } else if (!StringUtils.isNotEmpty(groupInfo.getTargetTable())) {
                        LogUtil.warn("buildWorkletGroups: [" + sessionRefName + "] targetTable empty, mappingRefName=" + mappingRefName);
                    }
                    if (!StringUtils.isNotEmpty(groupInfo.getTargetSchema()) && StringUtils.isNotEmpty(mappingRefName)) {
                        DataSyncParser.extractTargetSchemaFromMapping(groupInfo, doc, mappingRefName);
                    }
                    if (!foundSqlQuery && StringUtils.isNotEmpty(mappingRefName)) {
                        String sqlQuery = DataSyncParser.extractSqlQueryFromMapping(doc, mappingRefName);
                        if (StringUtils.isNotEmpty(sqlQuery)) {
                            SQLInfo sqlInfo = new SQLInfo();
                            sqlInfo.setSqlQuery(sqlQuery);
                            groupInfo.getSqlInfos().add(sqlInfo);
                        }
                    }
                    ApiNodeFromInfoBean workletApiConfig = DataSyncParser.extractApiConfig(doc, sessionEl);
                    if (workletApiConfig != null) {
                        groupInfo.setApiConfig(workletApiConfig);
                        LogUtil.info("buildWorkletGroups: [" + sessionRefName + "] detected API config");
                    }
                    LogUtil.info("buildWorkletGroups: [" + sessionRefName + "] group created, targetTable=" + groupInfo.getTargetTable() + ", toDB=" + groupInfo.getToDBName());
                    groups.add(groupInfo);
                }
            }
            if (!groups.isEmpty()) {
                result.put(workletName, groups);
            }
        }
        return result;
    }

    private static DataSyncParseNodeBean createParentJobTask(String folder, String workName, List<String> subTaskNames) {
        ArrayList<GroupNodeInfoBean> groups = new ArrayList<GroupNodeInfoBean>();
        DefaultGroupNodeInfoBean group = new DefaultGroupNodeInfoBean(workName);
        int currentX = 0;
        int currentY = 0;
        for (String subTaskName : subTaskNames) {
            currentX += 200;
            InvokeTaskNodeBean invokeNode = new InvokeTaskNodeBean(currentX, currentY, subTaskName, UUID.randomUUID().toString(), subTaskName);
            invokeNode.setSpreadParam(true);
            group.addNode(invokeNode);
        }
        groups.add(group);
        return DataSyncParseNodeBean.create(folder, workName).addGroups(groups);
    }

    @NotNull
    private static List<GroupNodeInfoBean> getSessionGroupNodeInfoBeans(Document doc, XPath xpath, TaskEvalBean extraParam, AtomicInteger currentX, AtomicInteger currentY) {
        ArrayList<GroupNodeInfoBean> groups = new ArrayList<GroupNodeInfoBean>();
        NodeList mappingNodes = doc.getElementsByTagName(TAG_SESSION);
        List<GroupSQLInfo> mapping = DataSyncParser.parseSession(doc, mappingNodes);
        return DataSyncParser.getGroupNodeInfoBeans(doc, xpath, extraParam, mapping, currentX, currentY, groups);
    }

    @NotNull
    private static List<GroupNodeInfoBean> getWorkletGroupNodeInfoBeans(Document doc, XPath xpath, TaskEvalBean extraParam, AtomicInteger currentX, AtomicInteger currentY) {
        ArrayList<GroupNodeInfoBean> groups = new ArrayList<GroupNodeInfoBean>();
        NodeList workletNodes = doc.getElementsByTagName("WORKLET");
        if (workletNodes.getLength() == 0) {
            return groups;
        }
        for (int i = 0; i < workletNodes.getLength(); i++) {
            Element workletEl = (Element) workletNodes.item(i);
            String workletName = workletEl.getAttribute("NAME");
            NodeList taskInstances = workletEl.getElementsByTagName("TASKINSTANCE");
            for (int j = 0; j < taskInstances.getLength(); j++) {
                Element ti = (Element) taskInstances.item(j);
                String taskType = ti.getAttribute("TASKTYPE");
                if (!"Session".equals(taskType)) {
                    continue;
                }
                String sessionRefName = ti.getAttribute("TASKNAME");
                if (!StringUtils.isNotEmpty(sessionRefName)) {
                    continue;
                }
                // Find the referenced SESSION element in the document
                NodeList allSessions = doc.getElementsByTagName("SESSION");
                for (int k = 0; k < allSessions.getLength(); k++) {
                    Element sessionEl = (Element) allSessions.item(k);
                    if (!sessionRefName.equals(sessionEl.getAttribute("NAME"))) {
                        continue;
                    }
                    GroupSQLInfo groupInfo = new GroupSQLInfo();
                    groupInfo.setGroupName(workletName);
                    groupInfo.setName(sessionRefName);
                    DataSyncParser.parseConnectionReferences(doc, sessionEl, groupInfo);
                    String mappingRefName = sessionEl.getAttribute("MAPPINGNAME");
                    DataSyncParser.applyFieldLineageInfo(groupInfo, doc, mappingRefName);
                    DataSyncParser.applyTargetWriteInfo(groupInfo, doc, sessionEl, mappingRefName);
                    NodeList transNodes = sessionEl.getElementsByTagName("SESSTRANSFORMATIONINST");
                    boolean foundSqlQuery = false;
                    for (int m = 0; m < transNodes.getLength(); m++) {
                        Element transEl = (Element) transNodes.item(m);
                        NodeList tableAttrs = transEl.getElementsByTagName("ATTRIBUTE");
                        SQLInfo sqlInfo = new SQLInfo();
                        for (int n = 0; n < tableAttrs.getLength(); n++) {
                            Element tableAttr = (Element) tableAttrs.item(n);
                            String attrName = tableAttr.getAttribute("NAME");
                            String attrValue = tableAttr.getAttribute("VALUE");
                            switch (attrName) {
                                case "Sql Query":
                                    sqlInfo.setSqlQuery(attrValue);
                                    if (StringUtils.isNotEmpty(attrValue)) {
                                        foundSqlQuery = true;
                                    }
                                    break;
                                case "Pre SQL":
                                    sqlInfo.setPreSQL(attrValue);
                                    break;
                                case "Post SQL":
                                    sqlInfo.setPostSQL(attrValue);
                                    break;
                                case "Truncate target table option":
                                    if (StringUtils.equals(attrValue, "YES")) {
                                        sqlInfo.setTruncate(true);
                                    }
                                    break;
                            }
                        }
                        groupInfo.getSqlInfos().add(sqlInfo);
                        if (!StringUtils.isNotEmpty(groupInfo.getTargetTable())) {
                            for (int n = 0; n < tableAttrs.getLength(); n++) {
                                Element tableAttr = (Element) tableAttrs.item(n);
                                if ("Target Table Name".equals(tableAttr.getAttribute("NAME"))) {
                                    String targetTableName = tableAttr.getAttribute("VALUE");
                                    if (StringUtils.isNotEmpty(targetTableName)) {
                                        groupInfo.setTargetTable(targetTableName);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    // Fallback 2: use TRANSFORMATIONNAME from Target Definition SESSTRANSFORMATIONINST
                    if (!StringUtils.isNotEmpty(groupInfo.getTargetTable())) {
                        for (int m = 0; m < transNodes.getLength(); m++) {
                            Element transEl = (Element) transNodes.item(m);
                            if ("Target Definition".equals(transEl.getAttribute("TRANSFORMATIONTYPE"))) {
                                String transName = transEl.getAttribute("TRANSFORMATIONNAME");
                                if (StringUtils.isNotEmpty(transName)) {
                                    groupInfo.setTargetTable(transName);
                                }
                                break;
                            }
                        }
                    }
                    if (!StringUtils.isNotEmpty(groupInfo.getTargetTable()) && StringUtils.isNotEmpty(mappingRefName)) {
                        DataSyncParser.extractTargetInfoFromMapping(groupInfo, doc, mappingRefName);
                    }
                    if (!foundSqlQuery && StringUtils.isNotEmpty(mappingRefName)) {
                        String sqlQuery = DataSyncParser.extractSqlQueryFromMapping(doc, mappingRefName);
                        if (StringUtils.isNotEmpty(sqlQuery)) {
                            SQLInfo sqlInfo = new SQLInfo();
                            sqlInfo.setSqlQuery(sqlQuery);
                            groupInfo.getSqlInfos().add(sqlInfo);
                        }
                    }
                    ApiNodeFromInfoBean workletApiConfig = DataSyncParser.extractApiConfig(doc, sessionEl);
                    if (workletApiConfig != null) {
                        groupInfo.setApiConfig(workletApiConfig);
                    }
                    List<GroupSQLInfo> mapping = new ArrayList<GroupSQLInfo>();
                    mapping.add(groupInfo);
                    groups.addAll(DataSyncParser.getGroupNodeInfoBeans(doc, xpath, extraParam, mapping, currentX, currentY, new ArrayList<GroupNodeInfoBean>()));
                }
            }
        }
        return groups;
    }

    @NotNull
    private static List<GroupNodeInfoBean> getMappingGroupNodeInfoBeans(Document doc, XPath xpath, TaskEvalBean extraParam, AtomicInteger currentX, AtomicInteger currentY) {
        ArrayList<GroupNodeInfoBean> groups = new ArrayList<GroupNodeInfoBean>();
        NodeList mappingNodes = doc.getElementsByTagName(TAG_MAPPING);
        List<GroupSQLInfo> mapping = DataSyncParser.parseMapping(mappingNodes);
        return DataSyncParser.getGroupNodeInfoBeans(doc, xpath, extraParam, mapping, currentX, currentY, groups);
    }

    private static List<GroupNodeInfoBean> getGroupNodeInfoBeans(Document doc, XPath xpath, TaskEvalBean extraParam, List<GroupSQLInfo> mapping, AtomicInteger currentX, AtomicInteger currentY, List<GroupNodeInfoBean> groups) {
        mapping.forEach(groupSQLInfo -> {
            DefaultGroupNodeInfoBean group = new DefaultGroupNodeInfoBean(groupSQLInfo.getGroupName());
            ArrayList<SingleNodeInfoBean> preSQLList = new ArrayList<SingleNodeInfoBean>();
            ArrayList<SingleNodeInfoBean> postSQLList = new ArrayList<SingleNodeInfoBean>();
            ArrayList<String> sqlQueryList = new ArrayList<String>();
            ArrayList<String> datasourceAdapterNotes = new ArrayList<String>();
            currentY.set(currentY.get() + 200);
            currentX.set(-200);
            List<SQLInfo> sqlInfos = groupSQLInfo.getSqlInfos();
            String name = groupSQLInfo.getName();
            String fromDBName = StringUtils.isNotEmpty(extraParam.getFromDBName()) ? extraParam.getFromDBName() : groupSQLInfo.getFromDBName();
            String fromDBType = StringUtils.isNotEmpty(extraParam.getFromDBType()) ? extraParam.getFromDBType() : groupSQLInfo.getFromDBType();
            String toDBName = StringUtils.isNotEmpty(extraParam.getToDBName()) ? extraParam.getToDBName() : groupSQLInfo.getToDBName();
            String toDBType = StringUtils.isNotEmpty(extraParam.getToDBType()) ? extraParam.getToDBType() : groupSQLInfo.getToDBType();
            for (SQLInfo sqlInfo : sqlInfos) {
                String nodeId;
                String nodeName;
                String preSQL = sqlInfo.getPreSQL();
                String postSQL = sqlInfo.getPostSQL();
                String sqlQuery = sqlInfo.getSqlQuery();
                if (StringUtils.isNotEmpty((String)preSQL)) {
                    if (DataSyncParser.requiresDatasourceAdapter(toDBType)) {
                        datasourceAdapterNotes.add("Pre SQL retained from XML but not generated as SQLTask because target datasource adapter is required: " + DataSyncParser.summarizeSql(preSQL));
                    } else {
                        nodeName = name + "_pre_" + preSQLList.size();
                        nodeId = UUID.randomUUID().toString();
                        currentX.set(currentX.get() + 200);
                        preSQLList.add(new SqlTaskNodeBean(currentX.get(), currentY.get(), nodeName, nodeId, toDBType, toDBName, preSQL));
                    }
                }
                if (StringUtils.isNotEmpty((String)postSQL)) {
                    if (DataSyncParser.requiresDatasourceAdapter(toDBType)) {
                        datasourceAdapterNotes.add("Post SQL retained from XML but not generated as SQLTask because target datasource adapter is required: " + DataSyncParser.summarizeSql(postSQL));
                    } else {
                        nodeName = name + "_post_" + postSQLList.size();
                        nodeId = UUID.randomUUID().toString();
                        currentX.set(currentX.get() + 200);
                        postSQLList.add(new SqlTaskNodeBean(currentX.get(), currentY.get(), nodeName, nodeId, toDBType, toDBName, postSQL));
                    }
                }
                if (!StringUtils.isNotEmpty((String)sqlQuery)) continue;
                sqlQueryList.add(sqlQuery);
            }
            // Resolve target table before creating nodes that need it
            String targetTable = groupSQLInfo.getTargetTable();
            if (!StringUtils.isNotEmpty(targetTable)) {
                try {
                    targetTable = XmlParserUtil.extractExpression(doc, xpath, TO_DB_NAME_EXPRESSION);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            if (!StringUtils.isNotEmpty(targetTable)) {
                NodeList targetNodes = doc.getElementsByTagName("TARGET");
                if (targetNodes.getLength() > 0) {
                    Element firstTarget = (Element) targetNodes.item(0);
                    targetTable = firstTarget.getAttribute(TAG_NAME);
                    LogUtil.info("  Node [" + name + "]: final fallback TARGET/@NAME=" + targetTable);
                }
            }
            LogUtil.info("  Node [" + name + "]: resolved targetTable=[" + targetTable + "], from groupTargetTable=[" + groupSQLInfo.getTargetTable() + "], toDBName=[" + toDBName + "]");
            if ("oracle".equalsIgnoreCase(toDBType) && StringUtils.isNotEmpty(targetTable)) {
                targetTable = targetTable.toUpperCase();
            }
            group.addAllNode(preSQLList);
            boolean needTruncate = sqlInfos.stream().anyMatch(SQLInfo::getTruncate);
            if (needTruncate) {
                if (DataSyncParser.requiresDatasourceAdapter(toDBType)) {
                    datasourceAdapterNotes.add("Truncate target option retained from XML but not generated as SQLTask because target datasource adapter is required.");
                } else {
                    currentX.set(currentX.get() + 240);
                    group.addNode(new SqlTaskNodeBean(currentX.get(), currentY.get(), name + "_truncate", UUID.randomUUID().toString(), toDBType, toDBName, "truncate table " + targetTable));
                }
            }
            String sqlQuery = !sqlQueryList.isEmpty() ? String.join((CharSequence)" union all ", sqlQueryList) : "";
            sqlQuery = DataSyncParser.qualifyReaderSqlTableReferences(doc, groupSQLInfo, sqlQuery, fromDBName, fromDBType);
            DataFlowTransformInfo dataFlowTransformInfo = null;
            DataFlowTransformInfo apiPostProjectionTransformInfo = null;
            if (groupSQLInfo.getApiConfig() == null) {
                sqlQuery = DataSyncParser.applyExpressionSqlProjection(sqlQuery, groupSQLInfo.getFieldLineageInfo(), fromDBType);
                dataFlowTransformInfo = DataSyncParser.createFdlDataFlowTransformInfo(groupSQLInfo.getFieldLineageInfo(), fromDBName, fromDBType);
                if (dataFlowTransformInfo == null || !dataFlowTransformInfo.isExecutable()) {
                    sqlQuery = DataSyncParser.applyFilterSqlPushdown(sqlQuery, groupSQLInfo.getFieldLineageInfo());
                    sqlQuery = DataSyncParser.applyLookupSqlJoin(sqlQuery, groupSQLInfo.getFieldLineageInfo(), fromDBName);
                }
                } else {
                    apiPostProjectionTransformInfo = DataSyncParser.createApiPostExpressionTransformInfo(
                        groupSQLInfo.getFieldLineageInfo(), groupSQLInfo.getApiConfig(), sqlQuery);
                }
                DataSyncParser.validateReaderSqlShape(sqlQuery, name);
                DBNodeFromInfoBean fromInfo = DBNodeFromInfoBean.create().datasourceType(fromDBType).connectionName(fromDBName).sql(sqlQuery);
            DBNodeToInfoBean toInfo;
            try {
                String targetSchema = DataSyncParser.firstNonEmpty(groupSQLInfo.getTargetSchema(), DataSyncParser.resolveSchema(toDBName, extraParam.getToDBName()));
                toInfo = DBNodeToInfoBean.create().datasourceType(toDBType).connectionName(toDBName).tableName(targetTable).databaseName("").schema(targetSchema).targetWriteInfo(groupSQLInfo.getTargetWriteInfo());
                toInfo.connectionMappingInfo(DataSyncParser.findConnectionMappingInfo(groupSQLInfo, "WRITER", toDBName));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            currentX.set(currentX.get() + 240);
            TargetDeletePlan targetDeletePlan = DataSyncParser.createTargetDeletePlan(groupSQLInfo, sqlQuery, fromDBName, fromDBType, toDBName, toDBType, toInfo);
            if (targetDeletePlan.isDeleteMode()) {
                if (targetDeletePlan.isExecutable()) {
                    group.addNode(new SqlTaskNodeBean(currentX.get(), currentY.get(), name + "_delete", UUID.randomUUID().toString(),
                        toDBType, toDBName, targetDeletePlan.getSql()));
                } else {
                    group.addNode(new VirtualNodeBean(currentX.get(), currentY.get(), name + "_delete_adapter", UUID.randomUUID().toString(),
                        targetDeletePlan.getLimitation()));
                }
            } else if (groupSQLInfo.getApiConfig() != null) {
                if (groupSQLInfo.getApiConfig().isFdlApiNodeSupported()) {
                    if (apiPostProjectionTransformInfo != null && apiPostProjectionTransformInfo.isExecutable()) {
                        group.addNode(new ApiSyncTaskNodeBean(currentX.get(), currentY.get(), name + "_query", UUID.randomUUID().toString(),
                            groupSQLInfo.getApiConfig(), toInfo, fromInfo, groupSQLInfo.getFieldLineageInfo(), apiPostProjectionTransformInfo));
                    } else {
                        group.addNode(new ApiSyncTaskNodeBean(currentX.get(), currentY.get(), name + "_query", UUID.randomUUID().toString(),
                            groupSQLInfo.getApiConfig(), toInfo));
                    }
                } else if (groupSQLInfo.getApiConfig().isFdlRowApiUdfSupported()
                    && apiPostProjectionTransformInfo != null && apiPostProjectionTransformInfo.isExecutable()) {
                    group.addNode(new ApiSyncTaskNodeBean(currentX.get(), currentY.get(), name + "_query", UUID.randomUUID().toString(),
                        groupSQLInfo.getApiConfig(), toInfo, fromInfo, groupSQLInfo.getFieldLineageInfo(), apiPostProjectionTransformInfo));
                } else {
                    group.addNode(new VirtualNodeBean(currentX.get(), currentY.get(), name + "_query", UUID.randomUUID().toString(),
                        groupSQLInfo.getApiConfig().getVisibleNote()));
                }
            } else if (dataFlowTransformInfo != null && dataFlowTransformInfo.isExecutable()) {
                group.addNode(new TransformSyncTaskNodeBean(currentX.get(), currentY.get(), name + "_query", UUID.randomUUID().toString(),
                    fromInfo, toInfo, groupSQLInfo.getFieldLineageInfo(), dataFlowTransformInfo));
            } else {
                group.addNode(new SyncTaskNodeBean(currentX.get(), currentY.get(), name + "_query", UUID.randomUUID().toString(), fromInfo, toInfo, groupSQLInfo.getFieldLineageInfo()));
            }
            group.addAllNode(postSQLList);
            groups.add(group);
            LogUtil.info("  Node [" + name + "]: table=" + toInfo.getTableName()
                + ", schema=" + toInfo.getSchema()
                + ", toDB=" + toDBName
                + ", fromDB=" + fromDBName);
        });
        return groups;
    }

    private static TargetDeletePlan createTargetDeletePlan(GroupSQLInfo groupSQLInfo, String sourceSql, String fromDBName, String fromDBType,
                                                           String toDBName, String toDBType, DBNodeToInfoBean toInfo) {
        TargetWriteInfo targetWriteInfo = groupSQLInfo == null ? null : groupSQLInfo.getTargetWriteInfo();
        if (targetWriteInfo == null || !targetWriteInfo.isDeleteMode()) {
            return TargetDeletePlan.notDelete();
        }
        String visibleLine = targetWriteInfo.toVisibleLine();
        String baseNote = "Informatica target write semantics: " + visibleLine
            + "\nTarget delete mode must be translated as keyed DELETE SQL or a row-level delete adapter; DBWrite upsert is not equivalent.";
        if (!StringUtils.isNotEmpty(sourceSql) || !DataSyncParser.isSelectOnlySql(sourceSql)) {
            return TargetDeletePlan.pending(baseNote + "\nXML source SQL is not a read-only SELECT that can be embedded in DELETE WHERE EXISTS.");
        }
        if (!DataSyncParser.equalsIgnoreCaseNonEmpty(fromDBName, toDBName) || !DataSyncParser.equalsIgnoreCaseNonEmpty(fromDBType, toDBType)) {
            return TargetDeletePlan.pending(baseNote + "\nSource and target connections differ; generate a row-level delete adapter instead of cross-connection SQL.");
        }
        List<String> keyFields = targetWriteInfo.getKeyFields();
        if (keyFields.isEmpty()) {
            return TargetDeletePlan.pending(baseNote + "\nTarget XML does not expose key fields for DELETE WHERE clause generation.");
        }
        FieldLineageInfo fieldLineageInfo = groupSQLInfo.getFieldLineageInfo();
        if (fieldLineageInfo == null) {
            return TargetDeletePlan.pending(baseNote + "\nField lineage is missing, so target key fields cannot be mapped to source SQL aliases.");
        }
        List<String[]> keyPairs = new ArrayList<String[]>();
        for (String keyField : keyFields) {
            List<String> candidates = fieldLineageInfo.getReadCandidates(keyField);
            if (candidates.isEmpty()) {
                return TargetDeletePlan.pending(baseNote + "\nTarget key field has no source SQL candidate: " + keyField);
            }
            String targetKey = DataSyncParser.safeSqlIdentifier(keyField);
            String sourceKey = DataSyncParser.safeSqlIdentifier(candidates.get(0));
            if (!StringUtils.isNotEmpty(targetKey) || !StringUtils.isNotEmpty(sourceKey)) {
                return TargetDeletePlan.pending(baseNote + "\nTarget key field or source alias is not a simple SQL identifier: " + keyField + " <= " + candidates.get(0));
            }
            keyPairs.add(new String[]{targetKey, sourceKey});
        }
        String qualifiedTargetTable = DataSyncParser.qualifiedTableName(toInfo == null ? "" : toInfo.getSchema(), toInfo == null ? "" : toInfo.getTableName());
        if (!StringUtils.isNotEmpty(qualifiedTargetTable)) {
            return TargetDeletePlan.pending(baseNote + "\nTarget table name is missing.");
        }
        StringBuilder sql = new StringBuilder();
        sql.append("/* ").append(DataSyncParser.sanitizeSqlComment(baseNote)).append(" */\n");
        sql.append("DELETE FROM ").append(qualifiedTargetTable).append(" infa_tgt_\n");
        sql.append(" WHERE EXISTS (\n");
        sql.append("       SELECT 1\n");
        sql.append("         FROM (\n").append(sourceSql.trim()).append("\n        ) infa_src_\n");
        sql.append("        WHERE ");
        for (int i = 0; i < keyPairs.size(); i++) {
            if (i > 0) {
                sql.append("\n          AND ");
            }
            sql.append("infa_tgt_.").append(keyPairs.get(i)[0]).append(" = infa_src_.").append(keyPairs.get(i)[1]);
        }
        sql.append("\n )");
        return TargetDeletePlan.executable(sql.toString());
    }

    private static ConnectionMappingInfo findConnectionMappingInfo(GroupSQLInfo groupSQLInfo, String extensionType, String connectionName) {
        if (groupSQLInfo == null) {
            return null;
        }
        for (ConnectionMappingInfo info : groupSQLInfo.getConnectionInfos()) {
            if (info == null) {
                continue;
            }
            boolean extensionMatches = !StringUtils.isNotEmpty(extensionType) || extensionType.equalsIgnoreCase(info.getExtensionType());
            boolean connectionMatches = !StringUtils.isNotEmpty(connectionName) || connectionName.equalsIgnoreCase(info.getConnectionName());
            if (extensionMatches && connectionMatches) {
                return info;
            }
        }
        return null;
    }

    private static String qualifiedTableName(String schema, String tableName) {
        String table = DataSyncParser.safeSqlIdentifierPath(tableName);
        if (!StringUtils.isNotEmpty(table)) {
            return "";
        }
        if (table.indexOf('.') >= 0) {
            return table;
        }
        String safeSchema = DataSyncParser.safeSqlIdentifierPath(schema);
        if (StringUtils.isNotEmpty(safeSchema)) {
            return safeSchema + "." + table;
        }
        return table;
    }

    private static String safeSqlIdentifierPath(String value) {
        String safeValue = value == null ? "" : value.trim();
        if (!StringUtils.isNotEmpty(safeValue)) {
            return "";
        }
        String[] parts = safeValue.split("\\.");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            String safePart = DataSyncParser.safeSqlIdentifier(part);
            if (!StringUtils.isNotEmpty(safePart)) {
                return "";
            }
            if (result.length() > 0) {
                result.append(".");
            }
            result.append(safePart);
        }
        return result.toString();
    }

    private static String safeSqlIdentifier(String value) {
        String safeValue = value == null ? "" : value.trim();
        if (!safeValue.matches("[A-Za-z_][A-Za-z0-9_#$]*")) {
            return "";
        }
        return safeValue;
    }

    private static String sanitizeSqlComment(String value) {
        return value == null ? "" : value.replace("/*", "/ *").replace("*/", "* /").replace('\n', ' ');
    }

    private static String qualifyReaderSqlTableReferences(Document doc, GroupSQLInfo groupSQLInfo,
                                                          String sql, String connectionName, String datasourceType) {
        return sql;
    }

    private static boolean supportsSqlTableQualification(String datasourceType) {
        String normalized = DataSyncParser.normalizeConnectionKey(datasourceType);
        return normalized.contains("ORACLE")
            || normalized.contains("POSTGRESQL")
            || normalized.contains("POSTGRES")
            || normalized.contains("SQLSERVER")
            || normalized.contains("MSSQL")
            || normalized.contains("MYSQL")
            || normalized.contains("MARIADB")
            || normalized.contains("DB2");
    }

    private static String resolveReaderSqlConnectionSchema(String connectionName) {
        String configured = DataSyncParser.resolveConfiguredConnectionSchema(connectionName);
        if (StringUtils.isNotEmpty(configured)) {
            return configured;
        }
        if (!StringUtils.isNotEmpty(connectionName)) {
            return "";
        }
        try {
            String defaultSchema = FineUtils.getDefaultSchema(connectionName);
            if (StringUtils.isNotEmpty(defaultSchema)) {
                return defaultSchema;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String resolveConfiguredConnectionSchema(String connectionName) {
        String configured = DataSyncParser.findConnectionTypeOverride(System.getProperty(CONNECTION_SCHEMA_OVERRIDE_PROPERTY), connectionName);
        if (StringUtils.isNotEmpty(configured)) {
            return configured;
        }
        try {
            configured = DataSyncParser.findConnectionTypeOverride(System.getenv(CONNECTION_SCHEMA_OVERRIDE_ENV), connectionName);
        } catch (Throwable ignored) {
            configured = "";
        }
        return configured;
    }

    private static Map<String, String> collectSourceOwnerByTable(Document doc, String mappingName) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        Element mappingEl = DataSyncParser.findMappingElement(doc, mappingName);
        if (mappingEl == null) {
            return result;
        }
        NodeList instances = mappingEl.getElementsByTagName(TAG_INSTANCE);
        for (int i = 0; i < instances.getLength(); i++) {
            Element instEl = (Element) instances.item(i);
            if (!"SOURCE".equals(instEl.getAttribute(TAG_TYPE))) {
                continue;
            }
            String tableName = DataSyncParser.firstNonEmpty(instEl.getAttribute("TRANSFORMATION_NAME"), instEl.getAttribute(TAG_NAME));
            String ownerName = DataSyncParser.extractOwnerNameFromSource(doc, tableName);
            if (!StringUtils.isNotEmpty(tableName) || !StringUtils.isNotEmpty(ownerName)) {
                continue;
            }
            DataSyncParser.putUniqueSourceOwner(result, tableName, ownerName);
            String instanceName = instEl.getAttribute(TAG_NAME);
            if (StringUtils.isNotEmpty(instanceName)) {
                DataSyncParser.putUniqueSourceOwner(result, instanceName, ownerName);
            }
        }
        return result;
    }

    private static void putUniqueSourceOwner(Map<String, String> owners, String tableName, String ownerName) {
        String key = DataSyncParser.normalizeSqlObjectKey(tableName);
        if (!StringUtils.isNotEmpty(key) || !StringUtils.isNotEmpty(ownerName)) {
            return;
        }
        if (!owners.containsKey(key)) {
            owners.put(key, ownerName);
            return;
        }
        String existing = owners.get(key);
        if (!existing.equalsIgnoreCase(ownerName)) {
            owners.put(key, "");
        }
    }

    private static String qualifySqlTableReferences(String sql, String connectionSchema, Map<String, String> sourceOwners) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        int depth = 0;
        int fromListDepth = -1;
        boolean expectTable = false;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'') {
                int end = DataSyncParser.copySqlQuotedString(sql, i, result);
                i = end;
                continue;
            }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int end = DataSyncParser.copySqlLineComment(sql, i, result);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int end = DataSyncParser.copySqlBlockComment(sql, i, result);
                i = end;
                continue;
            }
            if (expectTable) {
                if (Character.isWhitespace(c)) {
                    result.append(c);
                    i++;
                    continue;
                }
                if (c == '(') {
                    result.append(c);
                    depth++;
                    expectTable = false;
                    i++;
                    continue;
                }
                SqlIdentifierToken token = DataSyncParser.readSqlIdentifierToken(sql, i);
                if (token.isValid()) {
                    result.append(DataSyncParser.qualifySqlTableToken(token.text, sql, token.end, connectionSchema, sourceOwners));
                    i = token.end;
                    expectTable = false;
                    continue;
                }
                expectTable = false;
            }
            if (c == '(') {
                depth++;
                result.append(c);
                i++;
                continue;
            }
            if (c == ')') {
                if (depth > 0) {
                    depth--;
                }
                if (fromListDepth > depth) {
                    fromListDepth = -1;
                }
                result.append(c);
                i++;
                continue;
            }
            if (c == ',' && fromListDepth == depth) {
                expectTable = true;
                result.append(c);
                i++;
                continue;
            }
            SqlIdentifierToken word = DataSyncParser.readSqlIdentifierToken(sql, i);
            if (word.isValid()) {
                String upper = word.text.toUpperCase(Locale.ROOT);
                result.append(word.text);
                if (DataSyncParser.isSqlTableReferenceKeyword(upper)) {
                    expectTable = true;
                    if ("FROM".equals(upper)) {
                        fromListDepth = depth;
                    }
                } else if (fromListDepth == depth && DataSyncParser.isSqlFromListTerminator(upper)) {
                    fromListDepth = -1;
                    expectTable = false;
                }
                i = word.end;
                continue;
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }

    private static String qualifySqlTableToken(String token, String sql, int tokenEnd,
                                               String connectionSchema, Map<String, String> sourceOwners) {
        if (!StringUtils.isNotEmpty(token) || token.indexOf('.') >= 0 || token.indexOf('@') >= 0 || token.startsWith("$")) {
            return token;
        }
        if (DataSyncParser.isPseudoTableName(token) || DataSyncParser.nextNonWhitespaceChar(sql, tokenEnd) == '(') {
            return token;
        }
        String safeTable = DataSyncParser.safeSqlIdentifier(token);
        if (!StringUtils.isNotEmpty(safeTable)) {
            return token;
        }
        String schema = StringUtils.isNotEmpty(connectionSchema)
            ? connectionSchema
            : sourceOwners.get(DataSyncParser.normalizeSqlObjectKey(token));
        String qualified = DataSyncParser.qualifiedTableName(schema, safeTable);
        return StringUtils.isNotEmpty(qualified) ? qualified : token;
    }

    private static boolean isSqlTableReferenceKeyword(String upper) {
        return "FROM".equals(upper)
            || "JOIN".equals(upper);
    }

    private static boolean isSqlFromListTerminator(String upper) {
        return "WHERE".equals(upper)
            || "GROUP".equals(upper)
            || "ORDER".equals(upper)
            || "HAVING".equals(upper)
            || "UNION".equals(upper)
            || "MINUS".equals(upper)
            || "INTERSECT".equals(upper)
            || "CONNECT".equals(upper)
            || "START".equals(upper)
            || "MODEL".equals(upper);
    }

    private static boolean isPseudoTableName(String token) {
        return "DUAL".equalsIgnoreCase(token);
    }

    private static int copySqlQuotedString(String sql, int start, StringBuilder result) {
        int i = start;
        result.append(sql.charAt(i++));
        while (i < sql.length()) {
            char c = sql.charAt(i);
            result.append(c);
            i++;
            if (c == '\'') {
                if (i < sql.length() && sql.charAt(i) == '\'') {
                    result.append(sql.charAt(i));
                    i++;
                    continue;
                }
                break;
            }
        }
        return i;
    }

    private static int copySqlLineComment(String sql, int start, StringBuilder result) {
        int i = start;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            result.append(c);
            i++;
            if (c == '\n' || c == '\r') {
                break;
            }
        }
        return i;
    }

    private static int copySqlBlockComment(String sql, int start, StringBuilder result) {
        int i = start;
        result.append(sql.charAt(i++));
        result.append(sql.charAt(i++));
        while (i < sql.length()) {
            char c = sql.charAt(i);
            result.append(c);
            i++;
            if (c == '*' && i < sql.length() && sql.charAt(i) == '/') {
                result.append(sql.charAt(i));
                i++;
                break;
            }
        }
        return i;
    }

    private static SqlIdentifierToken readSqlIdentifierToken(String sql, int start) {
        if (start >= sql.length() || !DataSyncParser.isSqlIdentifierStart(sql.charAt(start))) {
            return SqlIdentifierToken.invalid(start);
        }
        int i = start + 1;
        while (i < sql.length() && DataSyncParser.isSqlIdentifierPart(sql.charAt(i))) {
            i++;
        }
        return new SqlIdentifierToken(sql.substring(start, i), i);
    }

    private static boolean isSqlIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isSqlIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#' || c == '.';
    }

    private static char nextNonWhitespaceChar(String text, int start) {
        int i = start;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i < text.length() ? text.charAt(i) : '\0';
    }

    private static String normalizeSqlObjectKey(String value) {
        return value == null ? "" : value.trim().replace("\"", "").replace("`", "").replace("[", "").replace("]", "").toUpperCase(Locale.ROOT);
    }

    private static class SqlIdentifierToken {
        private final String text;
        private final int end;

        private SqlIdentifierToken(String text, int end) {
            this.text = text == null ? "" : text;
            this.end = end;
        }

        static SqlIdentifierToken invalid(int start) {
            return new SqlIdentifierToken("", start);
        }

        boolean isValid() {
            return StringUtils.isNotEmpty(text) && end >= 0;
        }
    }

    private static class TargetDeletePlan {
        private final boolean deleteMode;
        private final boolean executable;
        private final String sql;
        private final String limitation;

        private TargetDeletePlan(boolean deleteMode, boolean executable, String sql, String limitation) {
            this.deleteMode = deleteMode;
            this.executable = executable;
            this.sql = sql == null ? "" : sql;
            this.limitation = limitation == null ? "" : limitation;
        }

        static TargetDeletePlan notDelete() {
            return new TargetDeletePlan(false, false, "", "");
        }

        static TargetDeletePlan executable(String sql) {
            return new TargetDeletePlan(true, true, sql, "");
        }

        static TargetDeletePlan pending(String limitation) {
            return new TargetDeletePlan(true, false, "", limitation);
        }

        boolean isDeleteMode() {
            return deleteMode;
        }

        boolean isExecutable() {
            return executable;
        }

        String getSql() {
            return sql;
        }

        String getLimitation() {
            return limitation;
        }
    }

    private static String applyExpressionSqlProjection(String sqlQuery, FieldLineageInfo fieldLineageInfo, String datasourceType) {
        if (!StringUtils.isNotEmpty(sqlQuery) || fieldLineageInfo == null || !fieldLineageInfo.hasExpressionInfos()) {
            return sqlQuery;
        }
        List<String> projections = new ArrayList<String>();
        Set<String> aliases = new HashSet<String>();
        for (ExpressionFieldInfo info : fieldLineageInfo.getExpressionInfos()) {
            if (!info.isSqlPushdownSupported()) {
                continue;
            }
            String alias = info.getReadAlias();
            String expression = info.getSqlExpression();
            if (!StringUtils.isNotEmpty(alias) || !StringUtils.isNotEmpty(expression)) {
                continue;
            }
            String aliasKey = DataSyncParser.normalizeLineageName(alias);
            if (aliases.contains(aliasKey)) {
                continue;
            }
            aliases.add(aliasKey);
            projections.add(expression + " AS " + DataSyncParser.safeSqlAlias(alias));
        }
        if (projections.isEmpty()) {
            return sqlQuery;
        }
        StringBuilder result = new StringBuilder();
        result.append("SELECT infa_base_.*");
        for (String projection : projections) {
            result.append(",\n       ").append(projection);
        }
        result.append("\n  FROM (\n").append(sqlQuery).append("\n) infa_base_");
        return result.toString();
    }

    private static String applyFilterSqlPushdown(String sqlQuery, FieldLineageInfo fieldLineageInfo) {
        if (!StringUtils.isNotEmpty(sqlQuery) || fieldLineageInfo == null || !fieldLineageInfo.hasFilterInfos()) {
            return sqlQuery;
        }
        List<String> conditions = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (FilterFieldInfo info : fieldLineageInfo.getFilterInfos()) {
            if (!info.isSqlPushdownSupported()) {
                continue;
            }
            String condition = info.getSqlCondition();
            if (!StringUtils.isNotEmpty(condition)) {
                continue;
            }
            String key = DataSyncParser.normalizeLineageName(condition);
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            conditions.add(condition);
            info.setSqlPushdownApplied(true);
        }
        if (conditions.isEmpty()) {
            return sqlQuery;
        }
        StringBuilder result = new StringBuilder();
        result.append("SELECT infa_filter_.*\n  FROM (\n")
            .append(sqlQuery)
            .append("\n) infa_filter_\n WHERE ");
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) {
                result.append("\n   AND ");
            }
            result.append(conditions.get(i));
        }
        return result.toString();
    }

    private static String applyLookupSqlJoin(String sqlQuery, FieldLineageInfo fieldLineageInfo, String fromDBName) {
        if (!StringUtils.isNotEmpty(sqlQuery) || fieldLineageInfo == null || !fieldLineageInfo.hasLookupInfos()) {
            return sqlQuery;
        }
        String result = sqlQuery;
        int index = 0;
        for (LookupFieldInfo info : fieldLineageInfo.getLookupInfos()) {
            if (!info.isSqlJoinSupported()) {
                continue;
            }
            String lookupConnection = info.getConnectionInformation();
            if (StringUtils.isNotEmpty(lookupConnection) && StringUtils.isNotEmpty(fromDBName) && !lookupConnection.equalsIgnoreCase(fromDBName)) {
                info.setSqlJoinSupported(false);
                info.addLimitation("lookup connection differs from reader connection; generated as visible note instead of SQL join");
                continue;
            }
            String lookupSql = DataSyncParser.createLookupSql(info);
            if (!StringUtils.isNotEmpty(lookupSql) || !StringUtils.isNotEmpty(info.getSqlJoinCondition())) {
                info.setSqlJoinSupported(false);
                info.addLimitation("lookup SQL or join condition is incomplete; generated as visible note instead of SQL join");
                continue;
            }
            List<String> projections = DataSyncParser.createLookupJoinProjections(info);
            if (projections.isEmpty()) {
                info.setSqlJoinSupported(false);
                info.addLimitation("lookup output fields do not feed target fields; generated as visible note instead of SQL join");
                continue;
            }
            String lookupAlias = "infa_lookup_" + (++index);
            StringBuilder joined = new StringBuilder();
            joined.append("SELECT infa_lookup_base_.*");
            for (String projection : projections) {
                joined.append(",\n       ").append(lookupAlias).append(".").append(DataSyncParser.safeSqlAlias(projection))
                    .append(" AS ").append(DataSyncParser.safeSqlAlias(projection));
            }
            joined.append("\n  FROM (\n").append(result).append("\n) infa_lookup_base_\n  LEFT JOIN (\n")
                .append(lookupSql).append("\n) ").append(lookupAlias)
                .append("\n    ON ").append(DataSyncParser.qualifyLookupJoinCondition(info.getSqlJoinCondition(), lookupAlias));
            result = joined.toString();
            info.setSqlJoinApplied(true);
            for (String outputField : info.getOutputFields()) {
                for (String targetField : info.getTargetFieldsForOutput(outputField)) {
                    fieldLineageInfo.addReadCandidate(targetField, outputField);
                }
            }
        }
        return result;
    }

    private static String createLookupSql(LookupFieldInfo info) {
        if (StringUtils.isNotEmpty(info.getLookupSqlOverride())) {
            return info.getLookupSqlOverride();
        }
        if (!StringUtils.isNotEmpty(info.getLookupTableName())) {
            return "";
        }
        List<String> fields = info.getOutputFields();
        if (fields.isEmpty()) {
            fields = info.getInputFields();
        }
        if (fields.isEmpty()) {
            return "";
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(DataSyncParser.safeSqlAlias(fields.get(i)));
        }
        sql.append(" FROM ").append(info.getLookupTableName());
        if (StringUtils.isNotEmpty(info.getLookupSourceFilter())) {
            sql.append(" WHERE ").append(info.getLookupSourceFilter());
        }
        return sql.toString();
    }

    private static List<String> createLookupJoinProjections(LookupFieldInfo info) {
        List<String> result = new ArrayList<String>();
        for (String outputField : info.getOutputFields()) {
            if (!info.getTargetFieldsForOutput(outputField).isEmpty()) {
                result.add(outputField);
            }
        }
        return result;
    }

    private static String qualifyLookupJoinCondition(String condition, String lookupAlias) {
        if (!StringUtils.isNotEmpty(condition)) {
            return "";
        }
        return condition.replace("lookup_.", lookupAlias + ".").replace("base_.", "infa_lookup_base_.");
    }

    private static DataFlowTransformInfo createFdlDataFlowTransformInfo(FieldLineageInfo fieldLineageInfo, String fromDBName, String fromDBType) {
        if (fieldLineageInfo == null || (!fieldLineageInfo.hasLookupInfos() && !fieldLineageInfo.hasFilterInfos())) {
            return null;
        }
        DataFlowTransformInfo singleRowPlanInfo = DataSyncParser.createFdlSingleRowLookupPlanInfo(fieldLineageInfo, fromDBName, fromDBType);
        if (singleRowPlanInfo != null) {
            return singleRowPlanInfo;
        }
        DataFlowTransformInfo result = new DataFlowTransformInfo();
        Map<LookupFieldInfo, String> lookupAliases = new LinkedHashMap<LookupFieldInfo, String>();
        int lookupIndex = 0;
        for (LookupFieldInfo lookupInfo : fieldLineageInfo.getLookupInfos()) {
            if (!DataSyncParser.isLookupFdlDataFlowSupported(lookupInfo, fieldLineageInfo, fromDBName, fromDBType)) {
                continue;
            }
            String lookupSql = DataSyncParser.createLookupSql(lookupInfo);
            String lookupConnection = DataSyncParser.resolveLookupConnectionName(lookupInfo, fromDBName);
            String lookupDatasourceType = DataSyncParser.resolveLookupDatasourceType(lookupInfo, fieldLineageInfo, fromDBName, fromDBType);
            String nodeName = DataSyncParser.uniqueLookupNodeName(lookupInfo.getTransformationName(), ++lookupIndex);
            DataFlowTransformInfo.LookupReadInfo lookupRead = new DataFlowTransformInfo.LookupReadInfo();
            lookupRead.setNodeName(nodeName);
            lookupRead.setConnectionName(lookupConnection);
            lookupRead.setDatasourceType(lookupDatasourceType);
            lookupRead.setSql(lookupSql);
            lookupRead.setNote("Informatica Lookup Procedure DBRead: " + lookupInfo.toVisibleLine());
            result.addLookupRead(lookupRead);
            lookupAliases.put(lookupInfo, "lkp_" + lookupIndex);
            lookupInfo.setFdlDataFlowTransformSupported(true);
            lookupInfo.setFdlDataFlowTransformApplied(true);
        }

        Map<String, String> fieldExpressions = DataSyncParser.createFdlTransformFieldExpressions(fieldLineageInfo, lookupAliases);
        List<String> targetFields = fieldLineageInfo.getTargetFields();
        List<String> projections = new ArrayList<String>();
        for (String targetField : targetFields) {
            String expression = fieldExpressions.get(DataSyncParser.normalizeLineageName(targetField));
            if (!StringUtils.isNotEmpty(expression)) {
                continue;
            }
            projections.add(expression + " AS " + DataSyncParser.safeSparkIdentifier(targetField));
            result.addOutputField(targetField);
        }

        List<String> filterConditions = new ArrayList<String>();
        for (FilterFieldInfo filterInfo : fieldLineageInfo.getFilterInfos()) {
            String condition = DataSyncParser.createFdlFilterCondition(filterInfo, fieldExpressions);
            if (!StringUtils.isNotEmpty(condition)) {
                continue;
            }
            filterInfo.setFdlDataFlowTransformSupported(true);
            filterInfo.setFdlDataFlowTransformApplied(true);
            filterConditions.add(condition);
        }

        if (lookupAliases.isEmpty() && filterConditions.isEmpty()) {
            return null;
        }
        if (projections.isEmpty()) {
            return null;
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        for (int i = 0; i < projections.size(); i++) {
            if (i > 0) {
                sql.append(",\n       ");
            }
            sql.append(projections.get(i));
        }
        sql.append("\n  FROM $[reader] base_");
        for (Map.Entry<LookupFieldInfo, String> entry : lookupAliases.entrySet()) {
            LookupFieldInfo lookupInfo = entry.getKey();
            String lookupAlias = entry.getValue();
            String lookupNodeName = DataSyncParser.findLookupNodeName(result, lookupAlias);
            String joinCondition = DataSyncParser.createSparkLookupJoinCondition(lookupInfo, lookupAlias);
            sql.append("\n  LEFT JOIN $[").append(lookupNodeName).append("] ").append(lookupAlias)
                .append("\n    ON ").append(joinCondition);
        }
        if (!filterConditions.isEmpty()) {
            sql.append("\n WHERE ");
            for (int i = 0; i < filterConditions.size(); i++) {
                if (i > 0) {
                    sql.append("\n   AND ");
                }
                sql.append(filterConditions.get(i));
            }
        }
        result.setSparkSql(sql.toString());
        result.setNote(DataSyncParser.createFdlTransformNote(fieldLineageInfo, result));
        return result;
    }

    private static DataFlowTransformInfo createFdlSingleRowLookupPlanInfo(FieldLineageInfo fieldLineageInfo, String fromDBName, String fromDBType) {
        if (fieldLineageInfo == null || !fieldLineageInfo.hasLookupInfos()) {
            return null;
        }
        DataFlowTransformInfo result = null;
        StringBuilder sparkSqlDrafts = new StringBuilder();
        int lookupIndex = 0;
        for (LookupFieldInfo lookupInfo : fieldLineageInfo.getLookupInfos()) {
            if (!DataSyncParser.isLookupFdlSingleRowPlanReady(lookupInfo, fieldLineageInfo, fromDBName, fromDBType)) {
                continue;
            }
            String lookupNodeName = DataSyncParser.uniqueLookupNodeName(lookupInfo.getTransformationName(), ++lookupIndex);
            Map<String, String> fieldExpressions = DataSyncParser.createSingleRowPlanFieldExpressions(fieldLineageInfo, lookupInfo);
            List<String> filterConditions = DataSyncParser.createSingleRowPlanFilterConditions(fieldLineageInfo, fieldExpressions);
            String sparkSqlDraft = DataSyncParser.buildSingleRowLookupSparkSqlDraft(fieldLineageInfo, lookupInfo, lookupNodeName, fieldExpressions, filterConditions);
            if (!StringUtils.isNotEmpty(sparkSqlDraft)) {
                lookupInfo.addLimitation("single-row Lookup Spark SQL draft could not be built safely; generated as visible note only");
                continue;
            }
            if (result == null) {
                result = new DataFlowTransformInfo();
                result.setTransformNodeName("infa_lookup_single_row_plan");
                result.setSingleRowLookupMode(true);
                result.setSingleRowLookupExecutable(false);
                result.setSingleRowLookupPolicy(DataSyncParser.describeLookupMultipleMatchPolicy(lookupInfo));
                result.setSingleRowLookupRowSelector("ROW_NUMBER() OVER (PARTITION BY infa_base_row_id ORDER BY unresolved Informatica lookup/cache order) = 1");
                result.setSingleRowLookupOrderStatus("ORDERING_REQUIRED: Informatica cache/multiple-match row selection is not proven equivalent in FDL/Spark");
            }

            String lookupConnection = DataSyncParser.resolveLookupConnectionName(lookupInfo, fromDBName);
            String lookupDatasourceType = DataSyncParser.resolveLookupDatasourceType(lookupInfo, fieldLineageInfo, fromDBName, fromDBType);
            DataFlowTransformInfo.LookupReadInfo lookupRead = new DataFlowTransformInfo.LookupReadInfo();
            lookupRead.setNodeName(lookupNodeName);
            lookupRead.setConnectionName(lookupConnection);
            lookupRead.setDatasourceType(lookupDatasourceType);
            lookupRead.setSql(DataSyncParser.createLookupSql(lookupInfo));
            lookupRead.setNote("Informatica non-equality Lookup Procedure DBRead plan: " + lookupInfo.toVisibleLine());
            result.addLookupRead(lookupRead);

            lookupInfo.setFdlDataFlowSingleRowPlanReady(true);
            lookupInfo.setFdlDataFlowSingleRowExecutable(false);
            lookupInfo.setSingleRowLookupRowId("infa_base_row_id");
            lookupInfo.setSingleRowLookupRowSelector("ROW_NUMBER() OVER (PARTITION BY infa_base_row_id ORDER BY unresolved Informatica lookup/cache order) = 1");
            lookupInfo.setSingleRowLookupOrderStatus("ORDERING_REQUIRED: " + DataSyncParser.describeLookupMultipleMatchPolicy(lookupInfo)
                + "/cache row selection order is not proven; keep executable=false");
            lookupInfo.setSingleRowLookupDownstreamFilter(DataSyncParser.joinList(filterConditions));
            lookupInfo.setSingleRowLookupSparkSqlDraft(sparkSqlDraft);
            lookupInfo.addLimitation("single-row Lookup SparkSqlNode draft is generated with rn=1, but executable=false until Informatica "
                + DataSyncParser.describeLookupMultipleMatchPolicy(lookupInfo) + " ordering/cache semantics are proven or handled by adapter");

            if (sparkSqlDrafts.length() > 0) {
                sparkSqlDrafts.append("\n\n");
            }
            sparkSqlDrafts.append("/* Lookup ").append(lookupInfo.getTransformationName()).append(" */\n").append(sparkSqlDraft);
            for (String targetField : lookupInfo.getAllTargetFields()) {
                result.addOutputField(targetField);
            }
        }
        if (result == null) {
            return null;
        }
        result.setSingleRowLookupSqlDraft(sparkSqlDrafts.toString());
        result.setSparkSql(sparkSqlDrafts.toString());
        result.setNote(DataSyncParser.createFdlSingleRowLookupPlanNote(fieldLineageInfo, result));
        return result;
    }

    private static String describeLookupMultipleMatchPolicy(LookupFieldInfo lookupInfo) {
        if (lookupInfo != null && StringUtils.isNotEmpty(lookupInfo.getMultipleMatchPolicy())) {
            return lookupInfo.getMultipleMatchPolicy();
        }
        return "unresolved multiple-match policy";
    }

    private static boolean isLookupFdlSingleRowPlanReady(LookupFieldInfo lookupInfo, FieldLineageInfo fieldLineageInfo, String fromDBName, String fromDBType) {
        if (lookupInfo == null || !lookupInfo.isFdlDataFlowReviewRequired()) {
            return false;
        }
        List<LookupConditionPredicate> predicates = DataSyncParser.parseLookupConditionPredicates(lookupInfo.getLookupCondition());
        if (predicates.isEmpty() || !DataSyncParser.hasNonEqualityPredicate(predicates)) {
            return false;
        }
        String lookupSql = DataSyncParser.createLookupSql(lookupInfo);
        if (!DataSyncParser.isSelectOnlySql(lookupSql)) {
            lookupInfo.addLimitation("lookup SQL is not a read-only SELECT, so single-row FDL dataflow plan is not generated");
            return false;
        }
        String lookupConnection = DataSyncParser.resolveLookupConnectionName(lookupInfo, fromDBName);
        String lookupDatasourceType = DataSyncParser.resolveLookupDatasourceType(lookupInfo, fieldLineageInfo, fromDBName, fromDBType);
        if (!StringUtils.isNotEmpty(lookupConnection) || !StringUtils.isNotEmpty(lookupDatasourceType)
            || DataSyncParser.requiresDatasourceAdapter(lookupDatasourceType)) {
            lookupInfo.addLimitation("lookup connection is XML-expressed but requires an FDL datasource adapter, so single-row FDL dataflow plan stays adapter-required");
            return false;
        }
        if (lookupInfo.getOutputFields().isEmpty()) {
            lookupInfo.addLimitation("lookup output fields are empty, so single-row FDL dataflow plan is not generated");
            return false;
        }
        if (!DataSyncParser.hasLookupOutputTargetField(lookupInfo)) {
            lookupInfo.addLimitation("lookup output fields do not feed target fields, so single-row FDL dataflow plan is not generated");
            return false;
        }
        for (LookupConditionPredicate predicate : predicates) {
            if (!StringUtils.isNotEmpty(DataSyncParser.createLookupPredicateSql(lookupInfo, predicate, "lookup_row", "base_row", true))) {
                lookupInfo.addLimitation(DataSyncParser.lookupPredicateLimitation(lookupInfo, predicate));
                return false;
            }
        }
        return true;
    }

    private static boolean hasNonEqualityPredicate(List<LookupConditionPredicate> predicates) {
        for (LookupConditionPredicate predicate : predicates) {
            if (!predicate.isEquality()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLookupOutputTargetField(LookupFieldInfo lookupInfo) {
        if (lookupInfo == null) {
            return false;
        }
        for (String outputField : lookupInfo.getOutputFields()) {
            if (!lookupInfo.getTargetFieldsForOutput(outputField).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> createSingleRowPlanFieldExpressions(FieldLineageInfo fieldLineageInfo, LookupFieldInfo lookupInfo) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (fieldLineageInfo == null || lookupInfo == null) {
            return result;
        }
        for (String targetField : fieldLineageInfo.getTargetFields()) {
            List<String> candidates = fieldLineageInfo.getReadCandidates(targetField);
            if (!candidates.isEmpty()) {
                String candidate = candidates.get(0);
                String expression = "lookup_single." + DataSyncParser.safeSparkIdentifier(candidate);
                result.put(DataSyncParser.normalizeLineageName(targetField), expression);
                result.put(DataSyncParser.normalizeLineageName(candidate), expression);
            }
        }
        for (String outputField : lookupInfo.getOutputFields()) {
            String expression = "lookup_single." + DataSyncParser.safeSparkIdentifier(outputField);
            result.put(DataSyncParser.normalizeLineageName(outputField), expression);
            for (String targetField : lookupInfo.getTargetFieldsForOutput(outputField)) {
                result.put(DataSyncParser.normalizeLineageName(targetField), expression);
            }
        }
        return result;
    }

    private static List<String> createSingleRowPlanFilterConditions(FieldLineageInfo fieldLineageInfo, Map<String, String> fieldExpressions) {
        List<String> result = new ArrayList<String>();
        if (fieldLineageInfo == null || fieldExpressions == null || fieldExpressions.isEmpty()) {
            return result;
        }
        for (FilterFieldInfo filterInfo : fieldLineageInfo.getFilterInfos()) {
            String condition = DataSyncParser.createFdlFilterCondition(filterInfo, fieldExpressions);
            if (!StringUtils.isNotEmpty(condition)) {
                continue;
            }
            filterInfo.setFdlDataFlowTransformSupported(true);
            filterInfo.setLimitation("upstream non-equality Lookup has a single-row SparkSqlNode plan; apply this Filter after infa_lookup_rn = 1, but executable=false until Lookup ordering semantics are proven");
            result.add(condition);
        }
        return result;
    }

    private static String buildSingleRowLookupSparkSqlDraft(FieldLineageInfo fieldLineageInfo, LookupFieldInfo lookupInfo,
                                                            String lookupNodeName, Map<String, String> fieldExpressions,
                                                            List<String> filterConditions) {
        List<String> lookupProjections = DataSyncParser.createLookupJoinProjections(lookupInfo);
        if (fieldLineageInfo == null || lookupInfo == null || lookupProjections.isEmpty()
            || fieldExpressions == null || fieldExpressions.isEmpty() || !StringUtils.isNotEmpty(lookupNodeName)) {
            return "";
        }
        String joinCondition = DataSyncParser.createSingleRowLookupJoinCondition(lookupInfo);
        if (!StringUtils.isNotEmpty(joinCondition)) {
            return "";
        }
        List<String> projections = new ArrayList<String>();
        for (String targetField : fieldLineageInfo.getTargetFields()) {
            String expression = fieldExpressions.get(DataSyncParser.normalizeLineageName(targetField));
            if (!StringUtils.isNotEmpty(expression)) {
                continue;
            }
            projections.add(expression + " AS " + DataSyncParser.safeSparkIdentifier(targetField));
        }
        if (projections.isEmpty()) {
            return "";
        }
        StringBuilder sql = new StringBuilder();
        sql.append("/* executable=false; ordering required before running as equivalent FDL dataflow */\n");
        sql.append("WITH base_row AS (\n");
        sql.append("  SELECT base_.*, monotonically_increasing_id() AS infa_base_row_id\n");
        sql.append("    FROM $[reader] base_\n");
        sql.append("),\n");
        sql.append("lookup_candidates AS (\n");
        sql.append("  SELECT base_row.*");
        for (String outputField : lookupProjections) {
            sql.append(",\n         lookup_row.").append(DataSyncParser.safeSparkIdentifier(outputField))
                .append(" AS ").append(DataSyncParser.safeSparkIdentifier(outputField));
        }
        sql.append(",\n         ROW_NUMBER() OVER (\n");
        sql.append("           PARTITION BY base_row.infa_base_row_id\n");
        sql.append("           ORDER BY 1 /* ORDERING_REQUIRED: preserve Informatica Lookup cache/")
            .append(DataSyncParser.describeLookupMultipleMatchPolicy(lookupInfo)).append(" row order */\n");
        sql.append("         ) AS infa_lookup_rn\n");
        sql.append("    FROM base_row\n");
        sql.append("    LEFT JOIN $[").append(lookupNodeName).append("] lookup_row\n");
        sql.append("      ON ").append(joinCondition).append("\n");
        sql.append("),\n");
        sql.append("lookup_single AS (\n");
        sql.append("  SELECT * FROM lookup_candidates WHERE infa_lookup_rn = 1\n");
        sql.append(")\n");
        sql.append("SELECT ");
        for (int i = 0; i < projections.size(); i++) {
            if (i > 0) {
                sql.append(",\n       ");
            }
            sql.append(projections.get(i));
        }
        sql.append("\n  FROM lookup_single");
        if (filterConditions != null && !filterConditions.isEmpty()) {
            sql.append("\n WHERE ");
            for (int i = 0; i < filterConditions.size(); i++) {
                if (i > 0) {
                    sql.append("\n   AND ");
                }
                sql.append(filterConditions.get(i));
            }
        }
        return sql.toString();
    }

    private static String createSingleRowLookupJoinCondition(LookupFieldInfo lookupInfo) {
        List<String> sqlConditions = new ArrayList<String>();
        for (LookupConditionPredicate predicate : DataSyncParser.parseLookupConditionPredicates(lookupInfo.getLookupCondition())) {
            String sqlCondition = DataSyncParser.createLookupPredicateSql(lookupInfo, predicate, "lookup_row", "base_row", true);
            if (StringUtils.isNotEmpty(sqlCondition)) {
                sqlConditions.add(sqlCondition);
            }
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < sqlConditions.size(); i++) {
            if (i > 0) {
                result.append(" AND ");
            }
            result.append(sqlConditions.get(i));
        }
        return result.toString();
    }

    private static String createFdlSingleRowLookupPlanNote(FieldLineageInfo fieldLineageInfo, DataFlowTransformInfo transformInfo) {
        StringBuilder note = new StringBuilder();
        note.append("Informatica non-equality Lookup single-row plan captured as SparkSqlNode draft.");
        note.append("\nexecutable=false; adapter/order proof required before running as equivalent FDL dataflow.");
        note.append("\nrowId=infa_base_row_id; rowSelector=").append(transformInfo.getSingleRowLookupRowSelector());
        note.append("\norderingStatus=").append(transformInfo.getSingleRowLookupOrderStatus());
        note.append("\nlookup read nodes=").append(transformInfo.getLookupReads().size());
        note.append("; output fields=").append(DataSyncParser.joinList(transformInfo.getOutputFields()));
        if (fieldLineageInfo != null) {
            for (LookupFieldInfo lookupInfo : fieldLineageInfo.getLookupInfos()) {
                if (lookupInfo.isFdlDataFlowSingleRowPlanReady()) {
                    note.append("\nFDL single-row Lookup plan: ").append(lookupInfo.toVisibleLine());
                }
            }
        }
        return note.toString();
    }

    private static DataFlowTransformInfo createApiPostExpressionTransformInfo(FieldLineageInfo fieldLineageInfo,
                                                                              ApiNodeFromInfoBean apiConfig,
                                                                              String sourceSqlQuery) {
        boolean rowApiUdfSupported = apiConfig != null && apiConfig.isFdlRowApiUdfSupported();
        if (fieldLineageInfo == null || apiConfig == null || !fieldLineageInfo.hasExpressionInfos()
            || !StringUtils.isNotEmpty(sourceSqlQuery)
            || (!rowApiUdfSupported && !DataSyncParser.isApiPostProjectionApiStatusSupported(apiConfig))) {
            return null;
        }
        Map<String, String> fieldExpressions = DataSyncParser.createApiPostProjectionFieldExpressions(fieldLineageInfo, apiConfig, rowApiUdfSupported);
        DataFlowTransformInfo result = new DataFlowTransformInfo();
        result.setApiPostProjection(true);
        result.setRowApiUdfAdapter(rowApiUdfSupported);
        result.setTransformNodeName("infa_api_post_expression");
        if (rowApiUdfSupported) {
            result.setApiMergeRelation("FDL_ROW_API_UDF adapter=row_api_adapter; nativeNode=SparkSqlNode+SparkUDF; noOnOneEqualsOne=yes");
        } else {
            result.setApiMergeRelation("FDL_ADAPTER_ROW_BINDING required=yes; joinKey=_fdl_adapter_row_id; nativeStableRowId=not_proven; adapter must expose source/API row context before native FDL dataflow generation");
        }

        List<String> projections = new ArrayList<String>();
        boolean hasPostExpression = false;
        for (String targetField : fieldLineageInfo.getTargetFields()) {
            ExpressionFieldInfo expressionInfo = DataSyncParser.findApiPostProjectionExpression(
                fieldLineageInfo.getExpressionInfos(targetField), fieldExpressions, apiConfig);
            String projectionExpression = "";
            if (expressionInfo != null) {
                projectionExpression = DataSyncParser.qualifyConditionWithExpressions(expressionInfo.getSqlExpression(), fieldExpressions);
                expressionInfo.setFdlDataFlowPostProjectionSupported(true);
                expressionInfo.setFdlDataFlowPostProjectionApplied(true);
                expressionInfo.setLimitation("");
                apiConfig.addPostProjectionExpression(expressionInfo,
                    rowApiUdfSupported
                        ? "API post-projection generated through Spark UDF row API adapter; adapter=row_api_adapter; function=" + ApiNodeFromInfoBean.ROW_API_UDF_SPARK_FUNCTION + "; providerFunction=" + ApiNodeFromInfoBean.ROW_API_UDF_PROVIDER_FUNCTION + "; rowBinding=inline-row-transform; runtimeValidationRequired=yes"
                        : "API post-projection adapter contract; FDL_ADAPTER_ROW_BINDING joinKey=_fdl_adapter_row_id nativeStableRowId=not_proven");
                hasPostExpression = true;
            } else {
                projectionExpression = fieldExpressions.get(DataSyncParser.normalizeLineageName(targetField));
            }
            if (!StringUtils.isNotEmpty(projectionExpression)) {
                continue;
            }
            projections.add(projectionExpression + " AS " + DataSyncParser.safeSparkIdentifier(targetField));
            result.addOutputField(targetField);
        }
        if (!hasPostExpression || projections.isEmpty()) {
            return null;
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        for (int i = 0; i < projections.size(); i++) {
            if (i > 0) {
                sql.append(",\n       ");
            }
            sql.append(projections.get(i));
        }
        if (rowApiUdfSupported) {
            sql.append("\n  FROM $[row_api_adapter] row_api_row_");
        } else {
            sql.append("\n  FROM $[source_reader] source_row_");
            sql.append("\n  LEFT JOIN $[api_reader] api_result_");
            sql.append("\n    ON source_row_._fdl_adapter_row_id = api_result_._fdl_adapter_row_id");
        }
        result.setSparkSql(sql.toString());
        result.setNote(DataSyncParser.createApiPostExpressionTransformNote(fieldLineageInfo, apiConfig, result));
        return result;
    }

    private static boolean isApiPostProjectionApiStatusSupported(ApiNodeFromInfoBean apiConfig) {
        // FDL input nodes cannot consume upstream rows. Post-API projection needs a
        // generated adapter row context before it can become a runnable dataflow.
        return false;
    }

    private static Map<String, String> createApiPostProjectionFieldExpressions(FieldLineageInfo fieldLineageInfo,
                                                                               ApiNodeFromInfoBean apiConfig,
                                                                               boolean rowApiUdfSupported) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (fieldLineageInfo == null || apiConfig == null) {
            return result;
        }
        String sourceAlias = rowApiUdfSupported ? "row_api_row_" : "source_row_";
        String apiAlias = rowApiUdfSupported ? "row_api_row_" : "api_result_";
        for (String targetField : fieldLineageInfo.getTargetFields()) {
            List<String> candidates = fieldLineageInfo.getReadCandidates(targetField);
            if (!candidates.isEmpty()) {
                String candidate = candidates.get(0);
                String expression = sourceAlias + "." + DataSyncParser.safeSparkIdentifier(candidate);
                result.put(DataSyncParser.normalizeLineageName(targetField), expression);
                result.put(DataSyncParser.normalizeLineageName(candidate), expression);
            }
        }
        for (Map.Entry<String, String> entry : apiConfig.getOutputFieldTargets().entrySet()) {
            String outputField = entry.getKey();
            String targetField = entry.getValue();
            String expression = apiAlias + "." + DataSyncParser.safeSparkIdentifier(outputField);
            result.put(DataSyncParser.normalizeLineageName(outputField), expression);
            if (StringUtils.isNotEmpty(targetField)) {
                result.put(DataSyncParser.normalizeLineageName(targetField), expression);
            }
        }
        for (ExpressionFieldInfo info : fieldLineageInfo.getExpressionInfos()) {
            List<String> dependencies = info.getDependencies();
            List<String> dependencySources = info.getDependencySources();
            for (int i = 0; i < dependencySources.size(); i++) {
                ParsedDependencySource source = DataSyncParser.parseDependencySource(dependencySources.get(i));
                if (!source.isValid()) {
                    continue;
                }
                String dependency = source.dependency;
                if (!StringUtils.isNotEmpty(dependency) && i < dependencies.size()) {
                    dependency = dependencies.get(i);
                }
                if (source.isSqlReaderSource()) {
                    String expression = sourceAlias + "." + DataSyncParser.safeSparkIdentifier(source.field);
                    result.put(DataSyncParser.normalizeLineageName(source.field), expression);
                    if (StringUtils.isNotEmpty(dependency)) {
                        result.put(DataSyncParser.normalizeLineageName(dependency), expression);
                    }
                } else if (DataSyncParser.isApiOutputSource(apiConfig, source)) {
                    String expression = apiAlias + "." + DataSyncParser.safeSparkIdentifier(source.field);
                    result.put(DataSyncParser.normalizeLineageName(source.field), expression);
                    if (StringUtils.isNotEmpty(dependency)) {
                        result.put(DataSyncParser.normalizeLineageName(dependency), expression);
                    }
                }
            }
        }
        return result;
    }

    private static ExpressionFieldInfo findApiPostProjectionExpression(List<ExpressionFieldInfo> expressionInfos,
                                                                       Map<String, String> fieldExpressions,
                                                                       ApiNodeFromInfoBean apiConfig) {
        for (ExpressionFieldInfo info : expressionInfos) {
            if (DataSyncParser.isApiPostProjectionExpressionSupported(info, fieldExpressions, apiConfig)) {
                return info;
            }
        }
        return null;
    }

    private static boolean isApiPostProjectionExpressionSupported(ExpressionFieldInfo info,
                                                                  Map<String, String> fieldExpressions,
                                                                  ApiNodeFromInfoBean apiConfig) {
        if (info == null || !info.isComputed() || !info.isSqlConvertible()
            || !StringUtils.isNotEmpty(info.getSqlExpression())) {
            return false;
        }
        boolean hasApiDependency = false;
        for (String dependency : info.getDependencies()) {
            if (!fieldExpressions.containsKey(DataSyncParser.normalizeLineageName(dependency))) {
                info.setLimitation("post-API expression dependency is not available in source/API dataflow: " + dependency);
                return false;
            }
        }
        for (String dependencySource : info.getDependencySources()) {
            ParsedDependencySource source = DataSyncParser.parseDependencySource(dependencySource);
            if (!source.isValid()) {
                info.setLimitation("post-API expression dependency source is unresolved: " + dependencySource);
                return false;
            }
            if (source.isSqlReaderSource()) {
                continue;
            }
            if (DataSyncParser.isApiOutputSource(apiConfig, source)) {
                hasApiDependency = true;
                continue;
            }
            info.setLimitation("post-API expression dependency is not a source reader field or current API output: " + dependencySource);
            return false;
        }
        if (!hasApiDependency) {
            return false;
        }
        return true;
    }

    private static boolean isApiOutputSource(ApiNodeFromInfoBean apiConfig, ParsedDependencySource source) {
        if (apiConfig == null || source == null || !source.isValid()) {
            return false;
        }
        if (StringUtils.isNotEmpty(apiConfig.getSourceTransformationName())
            && !apiConfig.getSourceTransformationName().equalsIgnoreCase(source.instance)) {
            return false;
        }
        for (String outputField : apiConfig.getOutputFields()) {
            if (outputField.equalsIgnoreCase(source.field)) {
                return true;
            }
        }
        return false;
    }

    private static ParsedDependencySource parseDependencySource(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^\\s*([^<]+?)\\s*<=\\s*([^.\\s]+)\\.([^\\s]+)\\s+\\[([^\\]]+)\\]\\s*$").matcher(value == null ? "" : value);
        if (!matcher.matches()) {
            return new ParsedDependencySource("", "", "", "");
        }
        return new ParsedDependencySource(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
    }

    private static String createApiPostExpressionTransformNote(FieldLineageInfo fieldLineageInfo,
                                                               ApiNodeFromInfoBean apiConfig,
                                                               DataFlowTransformInfo transformInfo) {
        StringBuilder note = new StringBuilder();
        if (transformInfo.isRowApiUdfAdapter()) {
            note.append("Informatica API downstream Expression is generated with the plugin Spark UDF row API adapter.");
            note.append("\nAdapter execution path: DBRead -> SparkSqlNode(row_api_adapter/")
                .append(ApiNodeFromInfoBean.ROW_API_UDF_SPARK_FUNCTION)
                .append(") -> SparkSqlNode(post expression) -> DBWrite.");
            note.append("\nAPI execution status remains ").append(apiConfig.getExecutionStatus())
                .append("; full GO still requires FDL runtime validation that the Spark UDF provider is registered and callable.");
        } else {
            note.append("Informatica API downstream Expression requires adapter row-bound projection before generating FDL dataflow.");
            note.append("\nAPI execution status remains ").append(apiConfig.getExecutionStatus())
                .append("; full GO still requires deployment/runtime validation of the adapter contract.");
        }
        note.append("\nMerge relation: ").append(transformInfo.getApiMergeRelation());
        String rowBindingLine = apiConfig.getAdapterRowBindingMetadataLine();
        if (StringUtils.isNotEmpty(rowBindingLine)) {
            note.append("\n").append(rowBindingLine);
        }
        if (transformInfo.isRowApiUdfAdapter()) {
            note.append("\nFDL_ROW_API_UDF function=").append(ApiNodeFromInfoBean.ROW_API_UDF_SPARK_FUNCTION)
                .append("; providerFunction=").append(ApiNodeFromInfoBean.ROW_API_UDF_PROVIDER_FUNCTION)
                .append("; postProjection.rowBinding=inline-row-transform; postProjection.noOnOneEqualsOne=yes");
        } else {
            note.append("\nFDL_ADAPTER_CONTRACT postProjection.rowBinding=ADAPTER_ROW_BINDING_REQUIRED; postProjection.joinKey=_fdl_adapter_row_id; postProjection.nativeStableRowId=not_proven; postProjection.noOnOneEqualsOne=yes");
        }
        note.append("\nOutput fields=").append(DataSyncParser.joinList(transformInfo.getOutputFields()));
        if (fieldLineageInfo != null) {
            for (ExpressionFieldInfo info : fieldLineageInfo.getExpressionInfos()) {
                if (info.isFdlDataFlowPostProjectionApplied()) {
                    note.append("\nFDL post-API Expression: ").append(info.toVisibleLine());
                }
            }
        }
        return note.toString();
    }

    private static boolean isLookupFdlDataFlowSupported(LookupFieldInfo lookupInfo, FieldLineageInfo fieldLineageInfo, String fromDBName, String fromDBType) {
        if (lookupInfo == null) {
            return false;
        }
        if (!lookupInfo.isSqlJoinSupported()) {
            return false;
        }
        String lookupSql = DataSyncParser.createLookupSql(lookupInfo);
        if (!DataSyncParser.isSelectOnlySql(lookupSql)) {
            lookupInfo.addLimitation("lookup SQL is not a read-only SELECT, so FDL dataflow execution is not enabled");
            lookupInfo.setSqlJoinSupported(false);
            return false;
        }
        String lookupConnection = DataSyncParser.resolveLookupConnectionName(lookupInfo, fromDBName);
        String lookupDatasourceType = DataSyncParser.resolveLookupDatasourceType(lookupInfo, fieldLineageInfo, fromDBName, fromDBType);
        if (!StringUtils.isNotEmpty(lookupConnection) || !StringUtils.isNotEmpty(lookupDatasourceType)
            || DataSyncParser.requiresDatasourceAdapter(lookupDatasourceType)) {
            lookupInfo.addLimitation("lookup connection is XML-expressed but requires an FDL datasource adapter, so FDL dataflow execution is not enabled");
            lookupInfo.setSqlJoinSupported(false);
            return false;
        }
        if (lookupInfo.getOutputFields().isEmpty()) {
            lookupInfo.addLimitation("lookup output fields are empty, so FDL dataflow execution is not enabled");
            lookupInfo.setSqlJoinSupported(false);
            return false;
        }
        return true;
    }

    private static String resolveLookupConnectionName(LookupFieldInfo lookupInfo, String fromDBName) {
        if (lookupInfo != null && StringUtils.isNotEmpty(lookupInfo.getConnectionInformation())) {
            return lookupInfo.getConnectionInformation();
        }
        return fromDBName;
    }

    private static String resolveLookupDatasourceType(LookupFieldInfo lookupInfo, FieldLineageInfo fieldLineageInfo, String fromDBName, String fromDBType) {
        String lookupConnection = DataSyncParser.resolveLookupConnectionName(lookupInfo, fromDBName);
        if (!StringUtils.isNotEmpty(lookupConnection)) {
            return "";
        }
        if (fieldLineageInfo != null) {
            for (ConnectionMappingInfo info : fieldLineageInfo.getConnectionInfos()) {
                if (!lookupConnection.equalsIgnoreCase(info.getConnectionName())) {
                    continue;
                }
                boolean sameLookup = "LOOKUPEXTENSION".equalsIgnoreCase(info.getExtensionType())
                    || (lookupInfo != null && lookupInfo.getTransformationName().equalsIgnoreCase(info.getTransformationName()));
                if (!sameLookup && !lookupConnection.equalsIgnoreCase(fromDBName)) {
                    continue;
                }
                if (ConnectionMappingInfo.STATUS_SAFE.equals(info.getMappingStatus())
                    || ConnectionMappingInfo.STATUS_CONFIGURED.equals(info.getMappingStatus())) {
                    return info.getFdlDatasourceType();
                }
                if (lookupInfo != null) {
                    lookupInfo.addLimitation("lookup connection " + lookupConnection + " is " + info.getMappingStatus() + ", so FDL dataflow execution is not enabled");
                }
                return "";
            }
        }
        if (lookupConnection.equalsIgnoreCase(fromDBName)) {
            return fromDBType;
        }
        return "";
    }

    private static Map<String, String> createFdlTransformFieldExpressions(FieldLineageInfo fieldLineageInfo, Map<LookupFieldInfo, String> lookupAliases) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (fieldLineageInfo == null) {
            return result;
        }
        for (String targetField : fieldLineageInfo.getTargetFields()) {
            List<String> candidates = fieldLineageInfo.getReadCandidates(targetField);
            if (!candidates.isEmpty()) {
                String candidate = candidates.get(0);
                String expression = "base_." + DataSyncParser.safeSparkIdentifier(candidate);
                result.put(DataSyncParser.normalizeLineageName(targetField), expression);
                result.put(DataSyncParser.normalizeLineageName(candidate), expression);
            }
        }
        for (Map.Entry<LookupFieldInfo, String> entry : lookupAliases.entrySet()) {
            LookupFieldInfo lookupInfo = entry.getKey();
            String lookupAlias = entry.getValue();
            for (String outputField : lookupInfo.getOutputFields()) {
                String expression = lookupAlias + "." + DataSyncParser.safeSparkIdentifier(outputField);
                result.put(DataSyncParser.normalizeLineageName(outputField), expression);
                for (String targetField : lookupInfo.getTargetFieldsForOutput(outputField)) {
                    result.put(DataSyncParser.normalizeLineageName(targetField), expression);
                }
            }
        }
        return result;
    }

    private static String createFdlFilterCondition(FilterFieldInfo filterInfo, Map<String, String> fieldExpressions) {
        if (filterInfo == null || !filterInfo.isSqlConvertible() || !StringUtils.isNotEmpty(filterInfo.getSqlCondition())) {
            return "";
        }
        for (String dependency : filterInfo.getDependencies()) {
            if (!fieldExpressions.containsKey(DataSyncParser.normalizeLineageName(dependency))) {
                filterInfo.setLimitation("filter dependency is not available after FDL dataflow lookup/base projection: " + dependency);
                return "";
            }
        }
        return DataSyncParser.qualifyConditionWithExpressions(filterInfo.getSqlCondition(), fieldExpressions);
    }

    private static String qualifyConditionWithExpressions(String condition, Map<String, String> fieldExpressions) {
        String source = condition == null ? "" : condition;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b").matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group();
            String upper = token.toUpperCase();
            int next = matcher.end();
            while (next < source.length() && Character.isWhitespace(source.charAt(next))) {
                next++;
            }
            String replacement = token;
            if (!DataSyncParser.isExpressionKeyword(upper) && !(next < source.length() && source.charAt(next) == '(')) {
                String expression = fieldExpressions.get(DataSyncParser.normalizeLineageName(token));
                if (StringUtils.isNotEmpty(expression)) {
                    replacement = expression;
                }
            }
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String createSparkLookupJoinCondition(LookupFieldInfo lookupInfo, String lookupAlias) {
        List<String> sqlConditions = new ArrayList<String>();
        for (LookupConditionPredicate predicate : DataSyncParser.parseLookupConditionPredicates(lookupInfo.getLookupCondition())) {
            String sqlCondition = DataSyncParser.createLookupPredicateSql(lookupInfo, predicate, lookupAlias, "base_", true);
            if (StringUtils.isNotEmpty(sqlCondition)) {
                sqlConditions.add(sqlCondition);
            }
        }
        if (sqlConditions.isEmpty()) {
            return lookupInfo.getSqlJoinCondition().replace("lookup_.", lookupAlias + ".").replace("base_.", "base_.");
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < sqlConditions.size(); i++) {
            if (i > 0) {
                result.append(" AND ");
            }
            result.append(sqlConditions.get(i));
        }
        return result.toString();
    }

    private static String findLookupNodeName(DataFlowTransformInfo transformInfo, String lookupAlias) {
        int index = 0;
        for (DataFlowTransformInfo.LookupReadInfo lookupRead : transformInfo.getLookupReads()) {
            index++;
            if (("lkp_" + index).equals(lookupAlias)) {
                return lookupRead.getNodeName();
            }
        }
        return lookupAlias;
    }

    private static String createFdlTransformNote(FieldLineageInfo fieldLineageInfo, DataFlowTransformInfo transformInfo) {
        StringBuilder note = new StringBuilder();
        note.append("Informatica Lookup/Filter executed in FDL dataflow via DBRead + Spark SQL transform.");
        note.append("\nLookup read nodes=").append(transformInfo.getLookupReads().size());
        note.append("; output fields=").append(DataSyncParser.joinList(transformInfo.getOutputFields()));
        if (fieldLineageInfo != null) {
            for (LookupFieldInfo lookupInfo : fieldLineageInfo.getLookupInfos()) {
                if (lookupInfo.isFdlDataFlowTransformApplied()) {
                    note.append("\nFDL Lookup: ").append(lookupInfo.toVisibleLine());
                }
            }
            for (FilterFieldInfo filterInfo : fieldLineageInfo.getFilterInfos()) {
                if (filterInfo.isFdlDataFlowTransformApplied()) {
                    note.append("\nFDL Filter: ").append(filterInfo.toVisibleLine());
                }
            }
        }
        return note.toString();
    }

    private static boolean isSelectOnlySql(String sql) {
        if (!StringUtils.isNotEmpty(sql)) {
            return false;
        }
        String trimmed = sql.trim();
        if (!trimmed.toUpperCase().startsWith("SELECT")) {
            return false;
        }
        String withoutStrings = trimmed.replaceAll("'([^']|'')*'", " ");
        if (withoutStrings.contains(";")) {
            return false;
        }
        return !java.util.regex.Pattern.compile("(?i)\\b(INSERT|UPDATE|DELETE|MERGE|CALL|EXEC|TRUNCATE|DROP|ALTER|CREATE)\\b").matcher(withoutStrings).find();
    }

    private static void validateReaderSqlShape(String sql, String nodeName) {
        if (!StringUtils.isNotEmpty(sql)) {
            return;
        }
        String trimmed = sql.trim();
        if (!trimmed.toUpperCase().startsWith("SELECT")) {
            return;
        }
        String selectList = DataSyncParser.extractTopLevelSelectList(trimmed);
        if (!StringUtils.isNotEmpty(selectList)) {
            return;
        }
        if (selectList.endsWith(",")) {
            throw new RuntimeException("Invalid reader SQL shape for node [" + nodeName + "]: SELECT list ends with comma before FROM");
        }
        for (String item : DataSyncParser.splitSqlSelectItems(selectList)) {
            if (DataSyncParser.containsTopLevelSqlWord(item, "FROM")) {
                throw new RuntimeException("Invalid reader SQL shape for node [" + nodeName + "]: SELECT item contains top-level FROM: " + item);
            }
        }
    }

    private static boolean containsTopLevelSqlWord(String text, String word) {
        String source = text == null ? "" : text;
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (inSingleQuote || inDoubleQuote) {
                continue;
            }
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')' && depth > 0) {
                depth--;
                continue;
            }
            if (depth == 0 && DataSyncParser.startsWithSqlWord(source, i, word)) {
                return true;
            }
        }
        return false;
    }

    private static String uniqueLookupNodeName(String transformationName, int index) {
        String base = transformationName == null ? "" : transformationName.trim().replaceAll("[^A-Za-z0-9_]+", "_");
        if (!StringUtils.isNotEmpty(base)) {
            base = "lookup";
        }
        return "lookup_" + base + "_" + index;
    }

    private static String safeSparkIdentifier(String value) {
        String safe = value == null ? "" : value.trim();
        return "`" + safe.replace("`", "``") + "`";
    }

    private static String safeSqlAlias(String alias) {
        if (alias != null && alias.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return alias;
        }
        return "\"" + (alias == null ? "" : alias.replace("\"", "\"\"")) + "\"";
    }

    private static String resolveSqlSelectAlias(String sql, String field) {
        if (!StringUtils.isNotEmpty(sql) || !StringUtils.isNotEmpty(field)) {
            return "";
        }
        String selectList = DataSyncParser.extractTopLevelSelectList(sql);
        if (!StringUtils.isNotEmpty(selectList)) {
            return "";
        }
        String fieldKey = DataSyncParser.normalizeLineageName(field);
        for (String item : DataSyncParser.splitSqlSelectItems(selectList)) {
            SqlSelectItem selectItem = DataSyncParser.parseSqlSelectItem(item);
            if (!StringUtils.isNotEmpty(selectItem.alias)) {
                continue;
            }
            if (fieldKey.equals(DataSyncParser.normalizeLineageName(selectItem.alias))) {
                return selectItem.alias;
            }
            List<String> identifiers = DataSyncParser.sqlExpressionIdentifiers(selectItem.expression);
            if (identifiers.size() == 1 && fieldKey.equals(DataSyncParser.normalizeLineageName(identifiers.get(0)))) {
                return selectItem.alias;
            }
        }
        return "";
    }

    private static String extractTopLevelSelectList(String sql) {
        String source = sql == null ? "" : sql.trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)\\bSELECT\\b").matcher(source);
        if (!matcher.find()) {
            return "";
        }
        int start = matcher.end();
        boolean inString = false;
        int depth = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\'') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')' && depth > 0) {
                depth--;
                continue;
            }
            if (depth == 0 && DataSyncParser.startsWithSqlWord(source, i, "FROM")) {
                return source.substring(start, i).trim();
            }
        }
        return "";
    }

    private static boolean startsWithSqlWord(String source, int index, String word) {
        if (source == null || index < 0 || index + word.length() > source.length()) {
            return false;
        }
        if (!source.regionMatches(true, index, word, 0, word.length())) {
            return false;
        }
        int before = index - 1;
        int after = index + word.length();
        boolean beforeOk = before < 0 || !Character.isLetterOrDigit(source.charAt(before)) && source.charAt(before) != '_';
        boolean afterOk = after >= source.length() || !Character.isLetterOrDigit(source.charAt(after)) && source.charAt(after) != '_';
        return beforeOk && afterOk;
    }

    private static List<String> splitSqlSelectItems(String selectList) {
        List<String> result = new ArrayList<String>();
        if (!StringUtils.isNotEmpty(selectList)) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        int depth = 0;
        for (int i = 0; i < selectList.length(); i++) {
            char c = selectList.charAt(i);
            if (c == '\'') {
                inString = !inString;
                current.append(c);
                continue;
            }
            if (!inString) {
                if (c == '(') {
                    depth++;
                } else if (c == ')' && depth > 0) {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    if (StringUtils.isNotEmpty(current.toString())) {
                        result.add(current.toString().trim());
                    }
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }
        if (StringUtils.isNotEmpty(current.toString())) {
            result.add(current.toString().trim());
        }
        return result;
    }

    private static SqlSelectItem parseSqlSelectItem(String item) {
        String source = item == null ? "" : item.trim();
        java.util.regex.Matcher asMatcher = java.util.regex.Pattern.compile("(?i)^(.+?)\\s+AS\\s+([\"`\\[]?[A-Za-z_][A-Za-z0-9_]*[\"`\\]]?)\\s*$").matcher(source);
        if (asMatcher.matches()) {
            return new SqlSelectItem(asMatcher.group(1).trim(), DataSyncParser.stripIdentifierQuotes(asMatcher.group(2)));
        }
        java.util.regex.Matcher aliasMatcher = java.util.regex.Pattern.compile("^(.+?)\\s+([\"`\\[]?[A-Za-z_][A-Za-z0-9_]*[\"`\\]]?)\\s*$").matcher(source);
        if (aliasMatcher.matches() && !source.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?")) {
            return new SqlSelectItem(aliasMatcher.group(1).trim(), DataSyncParser.stripIdentifierQuotes(aliasMatcher.group(2)));
        }
        List<String> identifiers = DataSyncParser.sqlExpressionIdentifiers(source);
        String alias = identifiers.isEmpty() ? "" : identifiers.get(identifiers.size() - 1);
        return new SqlSelectItem(source, alias);
    }

    private static List<String> sqlExpressionIdentifiers(String expression) {
        List<String> result = new ArrayList<String>();
        String scrubbed = (expression == null ? "" : expression).replaceAll("'([^']|'')*'", " ");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b").matcher(scrubbed);
        while (matcher.find()) {
            String token = matcher.group();
            String upper = token.toUpperCase();
            int next = matcher.end();
            while (next < scrubbed.length() && Character.isWhitespace(scrubbed.charAt(next))) {
                next++;
            }
            if (DataSyncParser.isExpressionKeyword(upper) || (next < scrubbed.length() && scrubbed.charAt(next) == '(')) {
                continue;
            }
            result.add(token);
        }
        return result;
    }

    private static String stripIdentifierQuotes(String value) {
        String result = value == null ? "" : value.trim();
        if (result.length() >= 2 && ((result.startsWith("\"") && result.endsWith("\""))
            || (result.startsWith("`") && result.endsWith("`"))
            || (result.startsWith("[") && result.endsWith("]")))) {
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }

    private static class SqlSelectItem {
        private final String expression;
        private final String alias;

        SqlSelectItem(String expression, String alias) {
            this.expression = expression == null ? "" : expression.trim();
            this.alias = alias == null ? "" : alias.trim();
        }
    }

    private static List<GroupSQLInfo> parseMapping(NodeList mappingNodes) {
        ArrayList<GroupSQLInfo> result = new ArrayList<GroupSQLInfo>();
        for (int i = 0; i < mappingNodes.getLength(); ++i) {
            Element mappingEl = (Element)mappingNodes.item(i);
            String mappingName = mappingEl.getAttribute(TAG_NAME);
            GroupSQLInfo group = new GroupSQLInfo();
            DataSyncParser.applyFieldLineageInfo(group, mappingEl.getOwnerDocument(), mappingName);
            if (mappingName.startsWith("m_")) {
                group.setGroupName("s_" + mappingName);
            } else {
                group.setGroupName(mappingName);
            }
            group.setName(mappingName);
            NodeList transNodes = mappingEl.getElementsByTagName(TAG_TRANSFORMATION);
            for (int j = 0; j < transNodes.getLength(); ++j) {
                Element transEl = (Element)transNodes.item(j);
                NodeList tableAttrs = transEl.getElementsByTagName(TAG_TABLEATTRIBUTE);
                SQLInfo sqlInfo = new SQLInfo();
                block26: for (int k = 0; k < tableAttrs.getLength(); ++k) {
                    Element tableAttr = (Element)tableAttrs.item(k);
                    String name = tableAttr.getAttribute(TAG_NAME);
                    String value = tableAttr.getAttribute(TAG_VALUE);
                    switch (name) {
                        case "Sql Query": {
                            sqlInfo.setSqlQuery(value);
                            continue block26;
                        }
                        case "Pre SQL": {
                            sqlInfo.setPreSQL(value);
                            continue block26;
                        }
                        case "Post SQL": {
                            sqlInfo.setPostSQL(value);
                            continue block26;
                        }
                        case "Truncate target table option": {
                            if (!StringUtils.equals((String)value, (String)TAG_TRUNCATE_YES)) continue block26;
                            sqlInfo.setTruncate(true);
                            continue block26;
                        }
                    }
                }
                group.getSqlInfos().add(sqlInfo);
            }
            NodeList transInstanceNodes = mappingEl.getElementsByTagName(TAG_INSTANCE);
            for (int j = 0; j < transInstanceNodes.getLength(); ++j) {
                Element transEl = (Element)transInstanceNodes.item(j);
                NodeList tableAttrs = transEl.getElementsByTagName(TAG_TABLEATTRIBUTE);
                SQLInfo sqlInfo = new SQLInfo();
                block28: for (int k = 0; k < tableAttrs.getLength(); ++k) {
                    Element tableAttr = (Element)tableAttrs.item(k);
                    String name = tableAttr.getAttribute(TAG_NAME);
                    String value = tableAttr.getAttribute(TAG_VALUE);
                    switch (name) {
                        case "Sql Query": {
                            sqlInfo.setSqlQuery(value);
                            continue block28;
                        }
                        case "Pre SQL": {
                            sqlInfo.setPreSQL(value);
                            continue block28;
                        }
                        case "Post SQL": {
                            sqlInfo.setPostSQL(value);
                            continue block28;
                        }
                        case "Truncate target table option": {
                            if (!StringUtils.equals((String)value, (String)TAG_TRUNCATE_YES)) continue block28;
                            sqlInfo.setTruncate(true);
                            continue block28;
                        }
                    }
                }
                group.getSqlInfos().add(sqlInfo);
            }
            result.add(group);
        }
        return result;
    }

    private static List<GroupSQLInfo> parseSession(Document doc, NodeList mappingNodes) {
        ArrayList<GroupSQLInfo> result = new ArrayList<GroupSQLInfo>();
        for (int i = 0; i < mappingNodes.getLength(); ++i) {
            Element mappingEl = (Element)mappingNodes.item(i);
            String mappingName = mappingEl.getAttribute(TAG_NAME);
            GroupSQLInfo group = new GroupSQLInfo();
            group.setGroupName(mappingName);
            group.setName(mappingName);
            DataSyncParser.parseConnectionReferences(doc, mappingEl, group);
            String mappingRefName = mappingEl.getAttribute("MAPPINGNAME");
            DataSyncParser.applyFieldLineageInfo(group, doc, mappingRefName);
            DataSyncParser.applyTargetWriteInfo(group, doc, mappingEl, mappingRefName);
            LogUtil.info("Session [" + mappingName + "]: mapping=" + mappingRefName
                + ", fromDB=" + group.getFromDBName()
                + ", toDB=" + group.getToDBName());
            NodeList transNodes = mappingEl.getElementsByTagName(TAG_SESSTRANSFORMATIONINST);
            LogUtil.info("Session [" + mappingName + "]: found " + transNodes.getLength() + " SESSTRANSFORMATIONINST elements");
            boolean foundSqlQuery = false;
            for (int j = 0; j < transNodes.getLength(); ++j) {
                Element transEl = (Element)transNodes.item(j);
                NodeList tableAttrs = transEl.getElementsByTagName(TAG_ATTRIBUTE);
                SQLInfo sqlInfo = new SQLInfo();
                block14: for (int k = 0; k < tableAttrs.getLength(); ++k) {
                    Element tableAttr = (Element)tableAttrs.item(k);
                    String name = tableAttr.getAttribute(TAG_NAME);
                    String value = tableAttr.getAttribute(TAG_VALUE);
                    switch (name) {
                        case "Sql Query": {
                            sqlInfo.setSqlQuery(value);
                            if (StringUtils.isNotEmpty(value)) {
                                foundSqlQuery = true;
                            }
                            continue block14;
                        }
                        case "Pre SQL": {
                            sqlInfo.setPreSQL(value);
                            continue block14;
                        }
                        case "Post SQL": {
                            sqlInfo.setPostSQL(value);
                            continue block14;
                        }
                        case "Truncate target table option": {
                            if (!StringUtils.equals((String)value, (String)TAG_TRUNCATE_YES)) continue block14;
                            sqlInfo.setTruncate(true);
                            continue block14;
                        }
                    }
                }
                group.getSqlInfos().add(sqlInfo);
                if (!StringUtils.isNotEmpty(group.getTargetTable())) {
                    for (int k = 0; k < tableAttrs.getLength(); ++k) {
                        Element tableAttr = (Element)tableAttrs.item(k);
                        if ("Target Table Name".equals(tableAttr.getAttribute(TAG_NAME))) {
                            String targetTableName = tableAttr.getAttribute(TAG_VALUE);
                            if (StringUtils.isNotEmpty(targetTableName)) {
                                group.setTargetTable(targetTableName);
                                LogUtil.info("Session [" + mappingName + "]: Target Table Name found in SESSTRANSFORMATIONINST: " + targetTableName);
                            }
                            break;
                        }
                    }
                }
            }
            // Fallback 2: use TRANSFORMATIONNAME from Target Definition SESSTRANSFORMATIONINST
            if (!StringUtils.isNotEmpty(group.getTargetTable())) {
                for (int j = 0; j < transNodes.getLength(); ++j) {
                    Element transEl = (Element) transNodes.item(j);
                    if ("Target Definition".equals(transEl.getAttribute("TRANSFORMATIONTYPE"))) {
                        String transName = transEl.getAttribute("TRANSFORMATIONNAME");
                        if (StringUtils.isNotEmpty(transName)) {
                            group.setTargetTable(transName);
                            LogUtil.info("Session [" + mappingName + "]: targetTable from SESSTRANSFORMATIONINST TRANSFORMATIONNAME=" + transName);
                        }
                        break;
                    }
                }
            }
            if (!StringUtils.isNotEmpty(group.getTargetTable()) && StringUtils.isNotEmpty(mappingRefName)) {
                LogUtil.info("Session [" + mappingName + "]: falling back to extractTargetInfoFromMapping with mappingRefName=" + mappingRefName);
                DataSyncParser.extractTargetInfoFromMapping(group, doc, mappingRefName);
                LogUtil.info("Session [" + mappingName + "]: after extractTargetInfoFromMapping, targetTable=" + group.getTargetTable());
            } else if (!StringUtils.isNotEmpty(group.getTargetTable())) {
                LogUtil.warn("Session [" + mappingName + "]: targetTable still empty, mappingRefName=" + mappingRefName + " (isNotEmpty=" + StringUtils.isNotEmpty(mappingRefName) + ")");
            }
            if (!StringUtils.isNotEmpty(group.getTargetSchema()) && StringUtils.isNotEmpty(mappingRefName)) {
                DataSyncParser.extractTargetSchemaFromMapping(group, doc, mappingRefName);
            }
            if (!foundSqlQuery && StringUtils.isNotEmpty(mappingRefName)) {
                String sqlQuery = DataSyncParser.extractSqlQueryFromMapping(doc, mappingRefName);
                if (StringUtils.isNotEmpty(sqlQuery)) {
                    SQLInfo sqlInfo = new SQLInfo();
                    sqlInfo.setSqlQuery(sqlQuery);
                    group.getSqlInfos().add(sqlInfo);
                }
            }
            // Check for API/WebService Java Transformation
            ApiNodeFromInfoBean apiConfig = DataSyncParser.extractApiConfig(doc, mappingEl);
            if (apiConfig != null) {
                group.setApiConfig(apiConfig);
                LogUtil.info("Session [" + mappingName + "]: detected API config, transformation="
                    + apiConfig.getSourceTransformationName() + ", action=" + apiConfig.getActionName());
            }
            LogUtil.info("Session result [" + mappingName + "]: targetTable=" + group.getTargetTable()
                + ", fromDB=" + group.getFromDBName()
                + ", toDB=" + group.getToDBName());
            result.add(group);
        }
        return result;
    }

    private static String extractSqlQueryFromMapping(Document doc, String mappingName) {
        Element mappingEl = DataSyncParser.findMappingElement(doc, mappingName);
        if (mappingEl == null) {
            return null;
        }
        NodeList transNodes = mappingEl.getElementsByTagName(TAG_TRANSFORMATION);
        for (int j = 0; j < transNodes.getLength(); j++) {
            Element transEl = (Element) transNodes.item(j);
            NodeList tableAttrs = transEl.getElementsByTagName(TAG_TABLEATTRIBUTE);
            for (int k = 0; k < tableAttrs.getLength(); k++) {
                Element tableAttr = (Element) tableAttrs.item(k);
                if ("Sql Query".equals(tableAttr.getAttribute(TAG_NAME))) {
                    String value = tableAttr.getAttribute(TAG_VALUE);
                    if (StringUtils.isNotEmpty(value)) {
                        return value;
                    }
                }
            }
        }
        NodeList instNodes = mappingEl.getElementsByTagName(TAG_INSTANCE);
        for (int j = 0; j < instNodes.getLength(); j++) {
            Element instEl = (Element) instNodes.item(j);
            NodeList tableAttrs = instEl.getElementsByTagName(TAG_TABLEATTRIBUTE);
            for (int k = 0; k < tableAttrs.getLength(); k++) {
                Element tableAttr = (Element) tableAttrs.item(k);
                if ("Sql Query".equals(tableAttr.getAttribute(TAG_NAME))) {
                    String value = tableAttr.getAttribute(TAG_VALUE);
                    if (StringUtils.isNotEmpty(value)) {
                        return value;
                    }
                }
            }
        }
        String generatedSql = DataSyncParser.generateDefaultSqlFromMapping(mappingEl);
        return generatedSql;
    }

    private static String generateDefaultSqlFromMapping(Element mappingEl) {
        Element sqTrans = null;
        NodeList transNodes = mappingEl.getElementsByTagName(TAG_TRANSFORMATION);
        for (int j = 0; j < transNodes.getLength(); j++) {
            Element transEl = (Element) transNodes.item(j);
            if ("Source Qualifier".equals(transEl.getAttribute("TYPE"))) {
                sqTrans = transEl;
                break;
            }
        }
        if (sqTrans == null) {
            return null;
        }
        NodeList fields = sqTrans.getElementsByTagName("TRANSFORMFIELD");
        if (fields.getLength() == 0) {
            return null;
        }
        StringBuilder fieldList = new StringBuilder();
        for (int j = 0; j < fields.getLength(); j++) {
            Element field = (Element) fields.item(j);
            String fieldName = field.getAttribute(TAG_NAME);
            if (j > 0) {
                fieldList.append(", ");
            }
            fieldList.append(fieldName);
        }
        String tableName = null;
        String schemaName = null;
        NodeList instNodes = mappingEl.getElementsByTagName(TAG_INSTANCE);
        for (int j = 0; j < instNodes.getLength(); j++) {
            Element instEl = (Element) instNodes.item(j);
            if ("SOURCE".equals(instEl.getAttribute("TYPE"))) {
                tableName = DataSyncParser.firstNonEmpty(instEl.getAttribute("TRANSFORMATION_NAME"), instEl.getAttribute(TAG_NAME));
                schemaName = DataSyncParser.extractOwnerNameFromSource(mappingEl.getOwnerDocument(), tableName);
                break;
            }
        }
        if (!StringUtils.isNotEmpty(tableName)) {
            return null;
        }
        return "SELECT " + fieldList.toString() + " FROM " + DataSyncParser.qualifiedTableName(schemaName, tableName);
    }

    private static void extractTargetInfoFromMapping(GroupSQLInfo group, Document doc, String mappingName) {
        Element mappingEl = DataSyncParser.findMappingElement(doc, mappingName);
        if (mappingEl == null) {
            LogUtil.warn("extractTargetInfoFromMapping: MAPPING element not found for name=" + mappingName);
            return;
        }
        LogUtil.info("extractTargetInfoFromMapping: found MAPPING " + mappingEl.getAttribute(TAG_NAME) + " for search name=" + mappingName);
        NodeList instances = mappingEl.getElementsByTagName(TAG_INSTANCE);
        boolean foundTarget = false;
        for (int j = 0; j < instances.getLength(); j++) {
            Element instEl = (Element) instances.item(j);
            String instType = instEl.getAttribute(TAG_TYPE);
            if ("TARGET".equals(instType)) {
                foundTarget = true;
                String targetName = instEl.getAttribute("TRANSFORMATION_NAME");
                if (!StringUtils.isNotEmpty(targetName)) {
                    targetName = instEl.getAttribute(TAG_NAME);
                }
                LogUtil.info("extractTargetInfoFromMapping: TARGET INSTANCE found, TRANSFORMATION_NAME=" + targetName);
                if (StringUtils.isNotEmpty(targetName)) {
                    String physicalTable = DataSyncParser.resolveTargetPhysicalName(doc, targetName);
                    String finalName = StringUtils.isNotEmpty(physicalTable) ? physicalTable : targetName;
                    group.setTargetTable(finalName);
                    LogUtil.info("extractTargetInfoFromMapping: resolved target table to " + finalName + " (physicalTable=" + physicalTable + ")");
                }
            }
            if ("SOURCE".equals(instType) && !StringUtils.isNotEmpty(group.getTargetSchema())) {
                String sourceTransName = instEl.getAttribute("TRANSFORMATION_NAME");
                if (StringUtils.isNotEmpty(sourceTransName)) {
                    String schema = DataSyncParser.extractOwnerNameFromSource(doc, sourceTransName);
                    if (StringUtils.isNotEmpty(schema)) {
                        group.setTargetSchema(schema);
                    }
                }
            }
        }
        if (!foundTarget) {
            LogUtil.warn("extractTargetInfoFromMapping: no TARGET INSTANCE found in MAPPING " + mappingEl.getAttribute(TAG_NAME));
        }
    }

    private static void extractTargetSchemaFromMapping(GroupSQLInfo group, Document doc, String mappingName) {
        if (group == null || StringUtils.isNotEmpty(group.getTargetSchema())) {
            return;
        }
        Element mappingEl = DataSyncParser.findMappingElement(doc, mappingName);
        if (mappingEl == null) {
            return;
        }
        NodeList instances = mappingEl.getElementsByTagName(TAG_INSTANCE);
        for (int j = 0; j < instances.getLength(); j++) {
            Element instEl = (Element) instances.item(j);
            String instType = instEl.getAttribute(TAG_TYPE);
            if (!"TARGET".equals(instType) && !"SOURCE".equals(instType)) {
                continue;
            }
            String transformationName = DataSyncParser.firstNonEmpty(instEl.getAttribute("TRANSFORMATION_NAME"), instEl.getAttribute(TAG_NAME));
            String schema = "TARGET".equals(instType)
                ? DataSyncParser.extractOwnerNameFromTarget(doc, transformationName)
                : DataSyncParser.extractOwnerNameFromSource(doc, transformationName);
            if (StringUtils.isNotEmpty(schema)) {
                group.setTargetSchema(schema);
                LogUtil.info("extractTargetSchemaFromMapping: mapping=" + mappingName + ", schema=" + schema + ", source=" + instType + "/" + transformationName);
                return;
            }
        }
    }

    private static Element findMappingElement(Document doc, String mappingName) {
        NodeList mappingNodes = doc.getElementsByTagName(TAG_MAPPING);
        for (int i = 0; i < mappingNodes.getLength(); i++) {
            Element el = (Element) mappingNodes.item(i);
            String elName = el.getAttribute(TAG_NAME);
            if (mappingName.equals(elName)) {
                return el;
            }
        }
        String withPrefix = "m_" + mappingName;
        for (int i = 0; i < mappingNodes.getLength(); i++) {
            Element el = (Element) mappingNodes.item(i);
            if (withPrefix.equals(el.getAttribute(TAG_NAME))) {
                return el;
            }
        }
        if (mappingName.startsWith("m_")) {
            String withoutPrefix = mappingName.substring(2);
            for (int i = 0; i < mappingNodes.getLength(); i++) {
                Element el = (Element) mappingNodes.item(i);
                if (withoutPrefix.equals(el.getAttribute(TAG_NAME))) {
                    return el;
                }
            }
        }
        return null;
    }

    private static String resolveTargetPhysicalName(Document doc, String transformationName) {
        NodeList targetNodes = doc.getElementsByTagName("TARGET");
        for (int i = 0; i < targetNodes.getLength(); i++) {
            Element targetEl = (Element) targetNodes.item(i);
            if (!transformationName.equals(targetEl.getAttribute(TAG_NAME))) {
                continue;
            }
            String tableName = targetEl.getAttribute("TABLE_NAME");
            if (StringUtils.isNotEmpty(tableName)) {
                return tableName;
            }
            String dbdName = targetEl.getAttribute("DBDNAME");
            if (StringUtils.isNotEmpty(dbdName)) {
                return dbdName;
            }
            return targetEl.getAttribute(TAG_NAME);
        }
        return null;
    }

    private static String extractOwnerNameFromSource(Document doc, String sourceName) {
        NodeList sourceNodes = doc.getElementsByTagName("SOURCE");
        for (int i = 0; i < sourceNodes.getLength(); i++) {
            Element sourceEl = (Element) sourceNodes.item(i);
            if (sourceName.equals(sourceEl.getAttribute(TAG_NAME))) {
                String ownerName = sourceEl.getAttribute("OWNERNAME");
                if (StringUtils.isNotEmpty(ownerName)) {
                    return ownerName;
                }
            }
        }
        return null;
    }

    private static String extractOwnerNameFromTarget(Document doc, String targetName) {
        NodeList targetNodes = doc.getElementsByTagName("TARGET");
        for (int i = 0; i < targetNodes.getLength(); i++) {
            Element targetEl = (Element) targetNodes.item(i);
            if (targetName.equals(targetEl.getAttribute(TAG_NAME))) {
                String ownerName = targetEl.getAttribute("OWNERNAME");
                if (StringUtils.isNotEmpty(ownerName)) {
                    return ownerName;
                }
            }
        }
        return null;
    }

    private static void applyTargetWriteInfo(GroupSQLInfo group, Document doc, Element sessionEl, String mappingName) {
        if (group == null || sessionEl == null) {
            return;
        }
        TargetWriteInfo info = new TargetWriteInfo();
        info.setTreatSourceRowsAs(DataSyncParser.getDirectMetadataAttribute(sessionEl, "Treat source rows as"));
        Element targetInstEl = DataSyncParser.findTargetSessionTransformation(sessionEl);
        if (targetInstEl != null) {
            String targetInstance = DataSyncParser.firstNonEmpty(
                targetInstEl.getAttribute("SINSTANCENAME"),
                targetInstEl.getAttribute(TAG_NAME),
                targetInstEl.getAttribute("INSTANCE")
            );
            String targetDefinition = DataSyncParser.firstNonEmpty(targetInstEl.getAttribute("TRANSFORMATIONNAME"), targetInstance);
            info.setTargetInstance(targetInstance);
            info.setTargetDefinition(targetDefinition);
            info.setPreSql(DataSyncParser.getDirectMetadataAttribute(targetInstEl, TAG_PRE_SQL));
            info.setPostSql(DataSyncParser.getDirectMetadataAttribute(targetInstEl, TAG_POST_SQL));
            info.setTruncate(DataSyncParser.getDirectMetadataAttribute(targetInstEl, TAG_TRUNCATE));
            String targetTableName = DataSyncParser.getDirectMetadataAttribute(targetInstEl, "Target Table Name");
            if (StringUtils.isNotEmpty(targetTableName) && !StringUtils.isNotEmpty(group.getTargetTable())) {
                group.setTargetTable(targetTableName);
            }
        } else if (StringUtils.isNotEmpty(mappingName)) {
            Element mappingEl = DataSyncParser.findMappingElement(doc, mappingName);
            if (mappingEl != null) {
                NodeList instances = mappingEl.getElementsByTagName(TAG_INSTANCE);
                for (int i = 0; i < instances.getLength(); i++) {
                    Element instEl = (Element) instances.item(i);
                    String instType = instEl.getAttribute("TRANSFORMATION_TYPE");
                    if (!"Target Definition".equals(instType)) {
                        continue;
                    }
                    String targetInstance = instEl.getAttribute(TAG_NAME);
                    String targetDefinition = DataSyncParser.firstNonEmpty(instEl.getAttribute("TRANSFORMATION_NAME"), targetInstance);
                    info.setTargetInstance(targetInstance);
                    info.setTargetDefinition(targetDefinition);
                    break;
                }
            }
        }
        Element writerExtEl = DataSyncParser.findWriterSessionExtension(sessionEl, info.getTargetInstance(), info.getTargetDefinition());
        if (writerExtEl != null) {
            info.setTargetLoadType(DataSyncParser.getDirectMetadataAttribute(writerExtEl, "Target load type"));
            info.setInsert(DataSyncParser.getDirectMetadataAttribute(writerExtEl, "Insert"));
            info.setUpdateAsUpdate(DataSyncParser.getDirectMetadataAttribute(writerExtEl, "Update as Update"));
            info.setUpdateAsInsert(DataSyncParser.getDirectMetadataAttribute(writerExtEl, "Update as Insert"));
            info.setUpdateElseInsert(DataSyncParser.getDirectMetadataAttribute(writerExtEl, "Update else Insert"));
            info.setDelete(DataSyncParser.getDirectMetadataAttribute(writerExtEl, "Delete"));
            String writerTruncate = DataSyncParser.getDirectMetadataAttribute(writerExtEl, TAG_TRUNCATE);
            if (StringUtils.isNotEmpty(writerTruncate)) {
                info.setTruncate(writerTruncate);
            }
        }
        DataSyncParser.applyTargetLoadOrder(info, sessionEl);
        DataSyncParser.addTargetKeyFields(info, doc, info.getTargetDefinition());
        if (StringUtils.isNotEmpty(info.toVisibleLine())) {
            group.setTargetWriteInfo(info);
            LogUtil.info("Target write semantics parsed for session [" + group.getName() + "]: " + info.toVisibleLine());
        }
    }

    private static Element findTargetSessionTransformation(Element sessionEl) {
        NodeList transNodes = sessionEl.getElementsByTagName(TAG_SESSTRANSFORMATIONINST);
        for (int i = 0; i < transNodes.getLength(); i++) {
            Element transEl = (Element) transNodes.item(i);
            if ("Target Definition".equals(transEl.getAttribute("TRANSFORMATIONTYPE"))) {
                return transEl;
            }
        }
        return null;
    }

    private static Element findWriterSessionExtension(Element sessionEl, String targetInstance, String targetDefinition) {
        Element fallback = null;
        NodeList extNodes = sessionEl.getElementsByTagName(TAG_SESSIONEXTENSION);
        for (int i = 0; i < extNodes.getLength(); i++) {
            Element extEl = (Element) extNodes.item(i);
            if (!"WRITER".equals(extEl.getAttribute(TAG_TYPE)) || !"Target Definition".equals(extEl.getAttribute("TRANSFORMATIONTYPE"))) {
                continue;
            }
            if (fallback == null) {
                fallback = extEl;
            }
            if (DataSyncParser.matchesTargetName(targetInstance, extEl) || DataSyncParser.matchesTargetName(targetDefinition, extEl)) {
                return extEl;
            }
        }
        return fallback;
    }

    private static boolean matchesTargetName(String expected, Element element) {
        if (!StringUtils.isNotEmpty(expected) || element == null) {
            return false;
        }
        return DataSyncParser.equalsIgnoreCaseNonEmpty(expected, element.getAttribute("SINSTANCENAME"))
            || DataSyncParser.equalsIgnoreCaseNonEmpty(expected, element.getAttribute("DSQINSTNAME"))
            || DataSyncParser.equalsIgnoreCaseNonEmpty(expected, element.getAttribute("TRANSFORMATIONNAME"))
            || DataSyncParser.equalsIgnoreCaseNonEmpty(expected, element.getAttribute(TAG_NAME));
    }

    private static void applyTargetLoadOrder(TargetWriteInfo info, Element sessionEl) {
        if (info == null || sessionEl == null) {
            return;
        }
        NodeList orderNodes = sessionEl.getElementsByTagName("TARGETLOADORDER");
        for (int i = 0; i < orderNodes.getLength(); i++) {
            Element orderEl = (Element) orderNodes.item(i);
            String targetInstance = DataSyncParser.firstNonEmpty(orderEl.getAttribute("SINSTANCENAME"), orderEl.getAttribute("TARGETINSTANCE"), orderEl.getAttribute("INSTANCE"));
            String targetDefinition = DataSyncParser.firstNonEmpty(orderEl.getAttribute("TRANSFORMATIONNAME"), orderEl.getAttribute("TARGETNAME"), orderEl.getAttribute(TAG_NAME));
            if (StringUtils.isNotEmpty(info.getTargetInstance()) && StringUtils.isNotEmpty(targetInstance)
                && !info.getTargetInstance().equalsIgnoreCase(targetInstance)) {
                continue;
            }
            if (!StringUtils.isNotEmpty(targetInstance) && StringUtils.isNotEmpty(info.getTargetDefinition()) && StringUtils.isNotEmpty(targetDefinition)
                && !info.getTargetDefinition().equalsIgnoreCase(targetDefinition)) {
                continue;
            }
            String order = DataSyncParser.firstNonEmpty(orderEl.getAttribute("ORDER"), orderEl.getAttribute("LOADORDER"), orderEl.getAttribute("TARGETLOADORDER"));
            String value = DataSyncParser.formatTargetLoadOrder(order, targetInstance, targetDefinition);
            if (StringUtils.isNotEmpty(value)) {
                info.setTargetLoadOrder(value);
                return;
            }
        }
    }

    private static String formatTargetLoadOrder(String order, String targetInstance, String targetDefinition) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.isNotEmpty(order)) {
            builder.append("order=").append(order);
        }
        if (StringUtils.isNotEmpty(targetInstance)) {
            if (builder.length() > 0) {
                builder.append(";");
            }
            builder.append("targetInstance=").append(targetInstance);
        }
        if (StringUtils.isNotEmpty(targetDefinition)) {
            if (builder.length() > 0) {
                builder.append(";");
            }
            builder.append("targetDefinition=").append(targetDefinition);
        }
        return builder.toString();
    }

    private static void addTargetKeyFields(TargetWriteInfo info, Document doc, String targetDefinition) {
        if (info == null || doc == null || !StringUtils.isNotEmpty(targetDefinition)) {
            return;
        }
        NodeList targetNodes = doc.getElementsByTagName("TARGET");
        for (int i = 0; i < targetNodes.getLength(); i++) {
            Element targetEl = (Element) targetNodes.item(i);
            if (!targetDefinition.equals(targetEl.getAttribute(TAG_NAME))) {
                continue;
            }
            NodeList fieldNodes = targetEl.getElementsByTagName("TARGETFIELD");
            for (int j = 0; j < fieldNodes.getLength(); j++) {
                Element fieldEl = (Element) fieldNodes.item(j);
                String keyType = fieldEl.getAttribute("KEYTYPE");
                String upperKeyType = keyType == null ? "" : keyType.toUpperCase(Locale.ROOT);
                if (upperKeyType.contains("KEY") && !upperKeyType.contains("NOT A KEY")) {
                    info.addKeyField(fieldEl.getAttribute(TAG_NAME));
                }
            }
            return;
        }
    }

    private static void addTargetTruncateFlag(GroupSQLInfo group) {
        for (SQLInfo sqlInfo : group.getSqlInfos()) {
            if (sqlInfo.getTruncate()) {
                return;
            }
        }
        SQLInfo sqlInfo = new SQLInfo();
        sqlInfo.setTruncate(true);
        group.getSqlInfos().add(sqlInfo);
    }

    private static String getDirectMetadataAttribute(Element parent, String attributeName) {
        if (parent == null || !StringUtils.isNotEmpty(attributeName)) {
            return "";
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childEl = (Element) child;
            if (!TAG_ATTRIBUTE.equals(childEl.getTagName()) && !TAG_TABLEATTRIBUTE.equals(childEl.getTagName())) {
                continue;
            }
            if (attributeName.equalsIgnoreCase(childEl.getAttribute(TAG_NAME))) {
                return childEl.getAttribute(TAG_VALUE);
            }
        }
        return "";
    }

    private static String firstNonEmpty(String ... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.isNotEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean equalsIgnoreCaseNonEmpty(String first, String second) {
        return StringUtils.isNotEmpty(first) && StringUtils.isNotEmpty(second) && first.equalsIgnoreCase(second);
    }

    private static void applyFieldLineageInfo(GroupSQLInfo group, Document doc, String mappingName) {
        if (!StringUtils.isNotEmpty(mappingName)) {
            DataSyncParser.applyConnectionInfosToFieldLineageInfo(group);
            return;
        }
        FieldLineageInfo info = DataSyncParser.extractFieldLineageInfo(doc, mappingName);
        if (info != null && !info.isEmpty()) {
            group.setFieldLineageInfo(info);
            LogUtil.info("Field lineage parsed for mapping [" + mappingName + "], targets=" + info.size());
        }
        DataSyncParser.applyConnectionInfosToFieldLineageInfo(group);
    }

    private static void applyConnectionInfosToFieldLineageInfo(GroupSQLInfo group) {
        if (group == null || group.getConnectionInfos().isEmpty()) {
            return;
        }
        FieldLineageInfo info = group.getFieldLineageInfo();
        if (info == null) {
            info = new FieldLineageInfo();
        }
        for (ConnectionMappingInfo connectionInfo : group.getConnectionInfos()) {
            info.addConnectionInfo(connectionInfo);
        }
        group.setFieldLineageInfo(info);
    }

    private static FieldLineageInfo extractFieldLineageInfo(Document doc, String mappingName) {
        Element mappingEl = DataSyncParser.findMappingElement(doc, mappingName);
        if (mappingEl == null) {
            return null;
        }
        FieldLineageContext context = new FieldLineageContext(mappingEl);
        FieldLineageInfo info = new FieldLineageInfo();
        NodeList instances = mappingEl.getElementsByTagName(TAG_INSTANCE);
        for (int i = 0; i < instances.getLength(); i++) {
            Element instEl = (Element) instances.item(i);
            String instType = instEl.getAttribute("TRANSFORMATION_TYPE");
            if (!"Target Definition".equals(instType)) {
                continue;
            }
            String targetInstance = instEl.getAttribute(TAG_NAME);
            if (!StringUtils.isNotEmpty(targetInstance)) {
                continue;
            }
            String targetDefinition = instEl.getAttribute("TRANSFORMATION_NAME");
            if (!StringUtils.isNotEmpty(targetDefinition)) {
                targetDefinition = targetInstance;
            }
            Set<String> targetFields = new LinkedHashSet<String>();
            targetFields.addAll(context.getTargetFields(targetInstance));
            targetFields.addAll(DataSyncParser.getTargetDefinitionFields(doc, targetDefinition));
            for (String targetField : targetFields) {
                info.markTargetField(targetField);
                LineageNode targetNode = new LineageNode(targetInstance, targetField, "Target Definition");
                Set<String> candidates = new LinkedHashSet<String>();
                context.collectReadCandidates(targetNode, candidates, new HashSet<String>(), 0);
                for (String candidate : candidates) {
                    info.addReadCandidate(targetField, candidate);
                }
                for (ExpressionFieldInfo expressionInfo : context.collectExpressionInfos(targetNode, new HashSet<String>(), 0)) {
                    expressionInfo.setMappingName(mappingEl.getAttribute(TAG_NAME));
                    expressionInfo.setTargetInstance(targetInstance);
                    expressionInfo.setTargetField(targetField);
                    info.addExpressionInfo(expressionInfo);
                    if (expressionInfo.isSqlPushdownSupported()) {
                        info.addReadCandidate(targetField, expressionInfo.getReadAlias());
                    }
                }
                for (FilterFieldInfo filterInfo : context.collectFilterInfos(targetNode, new HashSet<String>(), 0)) {
                    filterInfo.setMappingName(mappingEl.getAttribute(TAG_NAME));
                    filterInfo.addTargetField(targetField);
                    info.addFilterInfo(filterInfo);
                }
                for (LookupFieldInfo lookupInfo : context.collectLookupInfos(targetNode, new HashSet<String>(), 0)) {
                    lookupInfo.setMappingName(mappingEl.getAttribute(TAG_NAME));
                    lookupInfo.addOutputTargetField(context.findLookupOutputForTarget(targetNode, lookupInfo.getTransformationName()), targetField);
                    info.addLookupInfo(lookupInfo);
                }
            }
        }
        return info;
    }

    private static Set<String> getTargetDefinitionFields(Document doc, String targetDefinition) {
        Set<String> result = new LinkedHashSet<String>();
        if (doc == null || !StringUtils.isNotEmpty(targetDefinition)) {
            return result;
        }
        NodeList targetNodes = doc.getElementsByTagName("TARGET");
        for (int i = 0; i < targetNodes.getLength(); i++) {
            Element targetEl = (Element) targetNodes.item(i);
            if (!targetDefinition.equals(targetEl.getAttribute(TAG_NAME))) {
                continue;
            }
            NodeList fieldNodes = targetEl.getElementsByTagName("TARGETFIELD");
            for (int j = 0; j < fieldNodes.getLength(); j++) {
                Element fieldEl = (Element) fieldNodes.item(j);
                String fieldName = fieldEl.getAttribute(TAG_NAME);
                if (StringUtils.isNotEmpty(fieldName)) {
                    result.add(fieldName);
                }
            }
            break;
        }
        return result;
    }

    private static class FieldLineageContext {
        private final Map<String, String> instanceTypes = new HashMap<String, String>();
        private final Map<String, String> instanceTransformations = new HashMap<String, String>();
        private final Map<String, List<LineageEdge>> incoming = new HashMap<String, List<LineageEdge>>();
        private final Map<String, Element> transformationElements = new HashMap<String, Element>();
        private final Map<String, Map<String, Element>> transformFields = new HashMap<String, Map<String, Element>>();
        private final Map<String, Set<String>> targetFields = new HashMap<String, Set<String>>();

        FieldLineageContext(Element mappingEl) {
            NodeList transformations = mappingEl.getElementsByTagName(TAG_TRANSFORMATION);
            for (int i = 0; i < transformations.getLength(); i++) {
                Element transEl = (Element) transformations.item(i);
                String transName = transEl.getAttribute(TAG_NAME);
                if (!StringUtils.isNotEmpty(transName)) {
                    continue;
                }
                transformationElements.put(transName, transEl);
                Map<String, Element> fields = new HashMap<String, Element>();
                NodeList fieldNodes = transEl.getElementsByTagName("TRANSFORMFIELD");
                for (int j = 0; j < fieldNodes.getLength(); j++) {
                    Element fieldEl = (Element) fieldNodes.item(j);
                    String fieldName = fieldEl.getAttribute(TAG_NAME);
                    if (StringUtils.isNotEmpty(fieldName)) {
                        fields.put(DataSyncParser.normalizeLineageName(fieldName), fieldEl);
                    }
                }
                transformFields.put(transName, fields);
            }

            NodeList instances = mappingEl.getElementsByTagName(TAG_INSTANCE);
            for (int i = 0; i < instances.getLength(); i++) {
                Element instEl = (Element) instances.item(i);
                String instName = instEl.getAttribute(TAG_NAME);
                if (!StringUtils.isNotEmpty(instName)) {
                    continue;
                }
                String instType = instEl.getAttribute("TRANSFORMATION_TYPE");
                if (!StringUtils.isNotEmpty(instType)) {
                    instType = instEl.getAttribute(TAG_TYPE);
                }
                String transName = instEl.getAttribute("TRANSFORMATION_NAME");
                if (!StringUtils.isNotEmpty(transName)) {
                    transName = instName;
                }
                instanceTypes.put(instName, instType);
                instanceTransformations.put(instName, transName);
            }

            NodeList connectors = mappingEl.getElementsByTagName("CONNECTOR");
            for (int i = 0; i < connectors.getLength(); i++) {
                Element connectorEl = (Element) connectors.item(i);
                LineageNode from = new LineageNode(
                    connectorEl.getAttribute("FROMINSTANCE"),
                    connectorEl.getAttribute("FROMFIELD"),
                    connectorEl.getAttribute("FROMINSTANCETYPE")
                );
                LineageNode to = new LineageNode(
                    connectorEl.getAttribute("TOINSTANCE"),
                    connectorEl.getAttribute("TOFIELD"),
                    connectorEl.getAttribute("TOINSTANCETYPE")
                );
                if (!from.isValid() || !to.isValid()) {
                    continue;
                }
                List<LineageEdge> edges = incoming.get(to.key());
                if (edges == null) {
                    edges = new ArrayList<LineageEdge>();
                    incoming.put(to.key(), edges);
                }
                edges.add(new LineageEdge(from, to));
                if ("Target Definition".equals(to.instanceType)) {
                    Set<String> fields = targetFields.get(to.instance);
                    if (fields == null) {
                        fields = new LinkedHashSet<String>();
                        targetFields.put(to.instance, fields);
                    }
                    fields.add(to.field);
                }
            }
        }

        Set<String> getTargetFields(String targetInstance) {
            Set<String> fields = targetFields.get(targetInstance);
            if (fields == null) {
                return new LinkedHashSet<String>();
            }
            return fields;
        }

        List<ExpressionFieldInfo> collectExpressionInfos(LineageNode node, Set<String> visited, int depth) {
            List<ExpressionFieldInfo> result = new ArrayList<ExpressionFieldInfo>();
            if (depth > 32 || node == null || !node.isValid()) {
                return result;
            }
            String key = node.key();
            if (visited.contains(key)) {
                return result;
            }
            visited.add(key);
            List<LineageEdge> edges = incoming.get(key);
            if (edges == null || edges.isEmpty()) {
                return result;
            }
            for (LineageEdge edge : edges) {
                LineageNode source = edge.from;
                if ("Expression".equals(source.instanceType)) {
                    ExpressionFieldInfo info = createExpressionInfo(source);
                    if (info != null && info.isComputed()) {
                        result.add(info);
                    }
                }
                result.addAll(collectExpressionInfos(source, new HashSet<String>(visited), depth + 1));
            }
            return result;
        }

        List<FilterFieldInfo> collectFilterInfos(LineageNode node, Set<String> visited, int depth) {
            List<FilterFieldInfo> result = new ArrayList<FilterFieldInfo>();
            if (depth > 32 || node == null || !node.isValid()) {
                return result;
            }
            String key = node.key();
            if (visited.contains(key)) {
                return result;
            }
            visited.add(key);
            List<LineageEdge> edges = incoming.get(key);
            if (edges == null || edges.isEmpty()) {
                return result;
            }
            for (LineageEdge edge : edges) {
                LineageNode source = edge.from;
                if ("Filter".equals(source.instanceType)) {
                    FilterFieldInfo info = createFilterInfo(source);
                    if (info != null) {
                        result.add(info);
                    }
                }
                result.addAll(collectFilterInfos(source, new HashSet<String>(visited), depth + 1));
            }
            return result;
        }

        List<LookupFieldInfo> collectLookupInfos(LineageNode node, Set<String> visited, int depth) {
            List<LookupFieldInfo> result = new ArrayList<LookupFieldInfo>();
            if (depth > 32 || node == null || !node.isValid()) {
                return result;
            }
            String key = node.key();
            if (visited.contains(key)) {
                return result;
            }
            visited.add(key);
            List<LineageEdge> edges = incoming.get(key);
            if (edges == null || edges.isEmpty()) {
                return result;
            }
            for (LineageEdge edge : edges) {
                LineageNode source = edge.from;
                if ("Lookup Procedure".equals(source.instanceType)) {
                    LookupFieldInfo info = createLookupInfo(source);
                    if (info != null) {
                        result.add(info);
                    }
                }
                result.addAll(collectLookupInfos(source, new HashSet<String>(visited), depth + 1));
            }
            return result;
        }

        String findLookupOutputForTarget(LineageNode node, String lookupTransformationName) {
            return findLookupOutputForTarget(node, lookupTransformationName, new HashSet<String>(), 0);
        }

        private String findLookupOutputForTarget(LineageNode node, String lookupTransformationName, Set<String> visited, int depth) {
            if (depth > 32 || node == null || !node.isValid() || !StringUtils.isNotEmpty(lookupTransformationName)) {
                return "";
            }
            String key = node.key();
            if (visited.contains(key)) {
                return "";
            }
            visited.add(key);
            List<LineageEdge> edges = incoming.get(key);
            if (edges == null || edges.isEmpty()) {
                return "";
            }
            for (LineageEdge edge : edges) {
                LineageNode source = edge.from;
                if ("Lookup Procedure".equals(source.instanceType) && lookupTransformationName.equals(source.instance)) {
                    return source.field;
                }
                String nested = findLookupOutputForTarget(source, lookupTransformationName, new HashSet<String>(visited), depth + 1);
                if (StringUtils.isNotEmpty(nested)) {
                    return nested;
                }
            }
            return "";
        }

        private ExpressionFieldInfo createExpressionInfo(LineageNode node) {
            Element fieldEl = getTransformField(node);
            if (fieldEl == null) {
                return null;
            }
            String expression = fieldEl.getAttribute("EXPRESSION");
            if (!StringUtils.isNotEmpty(expression)) {
                return null;
            }
            ExpressionFieldInfo info = DataSyncParser.analyzeExpression(node.instance, node.field, expression);
            for (String dependency : info.getDependencies()) {
                List<LineageEdge> dependencyEdges = incoming.get(new LineageNode(node.instance, dependency, node.instanceType).key());
                if (dependencyEdges == null || dependencyEdges.isEmpty()) {
                    info.addDependencySource(dependency + " <= unresolved");
                    continue;
                }
                for (LineageEdge dependencyEdge : dependencyEdges) {
                    LineageNode from = dependencyEdge.from;
                    info.addDependencySource(dependency + " <= " + from.instance + "." + from.field + " [" + from.instanceType + "]");
                }
            }
            if (info.isSqlConvertible()) {
                if (DataSyncParser.hasOnlySqlPushdownDependencies(info.getDependencySources())) {
                    info.setSqlPushdownSupported(true);
                } else {
                    info.setSqlPushdownSupported(false);
                    info.setLimitation(DataSyncParser.describeNonSqlDependencyLimitation(info.getDependencySources()));
                }
            }
            return info;
        }

        private FilterFieldInfo createFilterInfo(LineageNode node) {
            Element transEl = getTransformationElement(node);
            if (transEl == null) {
                return null;
            }
            String condition = DataSyncParser.getTableAttribute(transEl, "Filter Condition");
            if (!StringUtils.isNotEmpty(condition)) {
                condition = DataSyncParser.getTableAttribute(transEl, "Filter condition");
            }
            FilterFieldInfo info = DataSyncParser.analyzeFilter(node.instance, condition);
            info.addOutputField(node.field);
            for (String dependency : info.getDependencies()) {
                List<LineageEdge> dependencyEdges = incoming.get(new LineageNode(node.instance, dependency, node.instanceType).key());
                if (dependencyEdges == null || dependencyEdges.isEmpty()) {
                    info.addDependencySource(dependency + " <= unresolved");
                    continue;
                }
                for (LineageEdge dependencyEdge : dependencyEdges) {
                    LineageNode from = dependencyEdge.from;
                    info.addDependencySource(dependency + " <= " + describeLineageSource(from));
                }
            }
            if (info.isSqlConvertible()) {
                if (DataSyncParser.hasOnlySqlPushdownDependencies(info.getDependencySources())) {
                    info.setSqlPushdownSupported(true);
                } else {
                    info.setSqlPushdownSupported(false);
                    info.setLimitation(DataSyncParser.describeNonSqlDependencyLimitation(info.getDependencySources()));
                }
            }
            return info;
        }

        private LookupFieldInfo createLookupInfo(LineageNode node) {
            Element transEl = getTransformationElement(node);
            if (transEl == null) {
                return null;
            }
            LookupFieldInfo info = DataSyncParser.analyzeLookup(node.instance, transEl);
            info.addOutputField(node.field);
            NodeList fields = transEl.getElementsByTagName("TRANSFORMFIELD");
            for (int i = 0; i < fields.getLength(); i++) {
                Element fieldEl = (Element) fields.item(i);
                String fieldName = fieldEl.getAttribute(TAG_NAME);
                if (!StringUtils.isNotEmpty(fieldName)) {
                    continue;
                }
                String portType = fieldEl.getAttribute("PORTTYPE");
                if (DataSyncParser.isInputPort(portType)) {
                    info.addInputField(fieldName);
                    List<LineageEdge> inputEdges = incoming.get(new LineageNode(node.instance, fieldName, node.instanceType).key());
                    if (inputEdges == null || inputEdges.isEmpty()) {
                        info.addInputMapping(fieldName, "unresolved", "");
                    } else {
                        for (LineageEdge inputEdge : inputEdges) {
                            LineageNode from = inputEdge.from;
                            info.addInputMapping(fieldName, describeLineageSource(from), directSqlReadAlias(from));
                        }
                    }
                }
                if (DataSyncParser.isOutputPort(portType) || portType.toUpperCase().contains("LOOKUP")) {
                    info.addOutputField(fieldName);
                }
            }
            DataSyncParser.updateLookupSqlJoinSupport(info);
            return info;
        }

        void collectReadCandidates(LineageNode node, Set<String> candidates, Set<String> visited, int depth) {
            if (depth > 32 || node == null || !node.isValid()) {
                return;
            }
            String key = node.key();
            if (visited.contains(key)) {
                return;
            }
            visited.add(key);
            List<LineageEdge> edges = incoming.get(key);
            if (edges == null || edges.isEmpty()) {
                if ("Source Qualifier".equals(node.instanceType) || "Source Definition".equals(node.instanceType)) {
                    candidates.add(node.field);
                }
                return;
            }
            for (LineageEdge edge : edges) {
                LineageNode source = edge.from;
                if ("Source Qualifier".equals(source.instanceType) || "Source Definition".equals(source.instanceType)) {
                    candidates.add(source.field);
                    continue;
                }
                if ("Expression".equals(source.instanceType)) {
                    if (!isComputedExpression(source)) {
                        String simpleCandidate = getSimpleExpressionCandidate(source);
                        if (StringUtils.isNotEmpty(simpleCandidate)) {
                            candidates.add(simpleCandidate);
                        }
                        collectReadCandidates(source, candidates, new HashSet<String>(visited), depth + 1);
                    }
                    continue;
                }
                if ("Filter".equals(source.instanceType)) {
                    collectReadCandidates(source, candidates, new HashSet<String>(visited), depth + 1);
                    continue;
                }
                if ("Lookup Procedure".equals(source.instanceType) || "Custom Transformation".equals(source.instanceType)) {
                    continue;
                }
                collectReadCandidates(source, candidates, new HashSet<String>(visited), depth + 1);
            }
        }

        private boolean isComputedExpression(LineageNode node) {
            Element fieldEl = getTransformField(node);
            if (fieldEl == null) {
                return false;
            }
            String expression = fieldEl.getAttribute("EXPRESSION");
            if (!StringUtils.isNotEmpty(expression)) {
                return false;
            }
            String normalizedExpression = DataSyncParser.normalizeLineageName(expression);
            if (normalizedExpression.equals(DataSyncParser.normalizeLineageName(node.field))) {
                return false;
            }
            // Pure field rename expressions are safe to trace through. Any
            // function/operator/literal expression needs a real FDL expression
            // or SQL rewrite, so field transfer alone must not claim support.
            return !expression.trim().matches("[A-Za-z_][A-Za-z0-9_]*");
        }

        private String getSimpleExpressionCandidate(LineageNode node) {
            Element fieldEl = getTransformField(node);
            if (fieldEl == null) {
                return "";
            }
            String expression = fieldEl.getAttribute("EXPRESSION");
            if (!StringUtils.isNotEmpty(expression)) {
                return "";
            }
            String trimmed = expression.trim();
            if (!trimmed.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                return "";
            }
            if (DataSyncParser.normalizeLineageName(trimmed).equals(DataSyncParser.normalizeLineageName(node.field))) {
                return "";
            }
            return trimmed;
        }

        private Element getTransformField(LineageNode node) {
            String transformationName = instanceTransformations.get(node.instance);
            if (!StringUtils.isNotEmpty(transformationName)) {
                transformationName = node.instance;
            }
            Map<String, Element> fields = transformFields.get(transformationName);
            if (fields == null) {
                return null;
            }
            return fields.get(DataSyncParser.normalizeLineageName(node.field));
        }

        private Element getTransformationElement(LineageNode node) {
            String transformationName = instanceTransformations.get(node.instance);
            if (!StringUtils.isNotEmpty(transformationName)) {
                transformationName = node.instance;
            }
            return transformationElements.get(transformationName);
        }

        private String describeLineageSource(LineageNode node) {
            String result = node.instance + "." + node.field;
            if (StringUtils.isNotEmpty(node.instanceType)) {
                result += " [" + node.instanceType + "]";
            }
            return result;
        }

        private String directSqlReadAlias(LineageNode node) {
            return directSqlReadAlias(node, new HashSet<String>(), 0);
        }

        private String directSqlReadAlias(LineageNode node, Set<String> visited, int depth) {
            if (depth > 32 || node == null || !node.isValid()) {
                return "";
            }
            String key = node.key();
            if (visited.contains(key)) {
                return "";
            }
            visited.add(key);
            if ("Source Qualifier".equals(node.instanceType)) {
                return sourceQualifierReadAlias(node);
            }
            if ("Source Definition".equals(node.instanceType)) {
                return node.field;
            }
            if ("Expression".equals(node.instanceType) && !isComputedExpression(node)) {
                Element fieldEl = getTransformField(node);
                String expression = fieldEl == null ? "" : fieldEl.getAttribute("EXPRESSION");
                String upstreamField = node.field;
                if (StringUtils.isNotEmpty(expression) && expression.trim().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    upstreamField = expression.trim();
                }
                List<LineageEdge> edges = incoming.get(new LineageNode(node.instance, upstreamField, node.instanceType).key());
                if (edges == null || edges.isEmpty()) {
                    edges = incoming.get(node.key());
                }
                if (edges == null || edges.isEmpty()) {
                    return "";
                }
                for (LineageEdge edge : edges) {
                    String alias = directSqlReadAlias(edge.from, new HashSet<String>(visited), depth + 1);
                    if (StringUtils.isNotEmpty(alias)) {
                        return alias;
                    }
                }
            }
            return "";
        }

        private String sourceQualifierReadAlias(LineageNode node) {
            Element transEl = getTransformationElement(node);
            if (transEl == null) {
                return node.field;
            }
            String sql = DataSyncParser.getTableAttribute(transEl, TAG_SQL_QUERY);
            String alias = DataSyncParser.resolveSqlSelectAlias(sql, node.field);
            if (StringUtils.isNotEmpty(alias)) {
                return alias;
            }
            return node.field;
        }
    }

    private static class LineageNode {
        private final String instance;
        private final String field;
        private final String instanceType;

        LineageNode(String instance, String field, String instanceType) {
            this.instance = instance == null ? "" : instance;
            this.field = field == null ? "" : field;
            this.instanceType = instanceType == null ? "" : instanceType;
        }

        boolean isValid() {
            return StringUtils.isNotEmpty(instance) && StringUtils.isNotEmpty(field);
        }

        String key() {
            return instance + "\u0001" + field + "\u0001" + instanceType;
        }
    }

    private static class LineageEdge {
        private final LineageNode from;
        private final LineageNode to;

        LineageEdge(LineageNode from, LineageNode to) {
            this.from = from;
            this.to = to;
        }
    }

    private static class LookupConditionPredicate {
        private final String left;
        private final String operator;
        private final String right;

        LookupConditionPredicate(String left, String operator, String right) {
            this.left = left == null ? "" : left.trim();
            this.operator = operator == null ? "" : operator.trim();
            this.right = right == null ? "" : right.trim();
        }

        String getLeft() {
            return left;
        }

        String getOperator() {
            return operator;
        }

        String getRight() {
            return right;
        }

        boolean isEquality() {
            return "=".equals(operator);
        }

        String toSource() {
            return left + " " + operator + " " + right;
        }
    }

    private static class ParsedDependencySource {
        private final String dependency;
        private final String instance;
        private final String field;
        private final String instanceType;

        ParsedDependencySource(String dependency, String instance, String field, String instanceType) {
            this.dependency = dependency == null ? "" : dependency.trim();
            this.instance = instance == null ? "" : instance.trim();
            this.field = field == null ? "" : field.trim();
            this.instanceType = instanceType == null ? "" : instanceType.trim();
        }

        boolean isValid() {
            return StringUtils.isNotEmpty(dependency) && StringUtils.isNotEmpty(instance)
                && StringUtils.isNotEmpty(field) && StringUtils.isNotEmpty(instanceType);
        }

        boolean isSqlReaderSource() {
            return "Source Qualifier".equals(instanceType) || "Source Definition".equals(instanceType);
        }
    }

    private static String getTableAttribute(Element transEl, String attrName) {
        if (transEl == null || !StringUtils.isNotEmpty(attrName)) {
            return "";
        }
        NodeList tableAttrs = transEl.getElementsByTagName(TAG_TABLEATTRIBUTE);
        for (int i = 0; i < tableAttrs.getLength(); i++) {
            Element attr = (Element) tableAttrs.item(i);
            if (attrName.equalsIgnoreCase(attr.getAttribute(TAG_NAME))) {
                return attr.getAttribute(TAG_VALUE);
            }
        }
        NodeList attrs = transEl.getElementsByTagName(TAG_ATTRIBUTE);
        for (int i = 0; i < attrs.getLength(); i++) {
            Element attr = (Element) attrs.item(i);
            if (attrName.equalsIgnoreCase(attr.getAttribute(TAG_NAME))) {
                return attr.getAttribute(TAG_VALUE);
            }
        }
        return "";
    }

    private static FilterFieldInfo analyzeFilter(String transformationName, String condition) {
        FilterFieldInfo info = new FilterFieldInfo();
        info.setTransformationName(transformationName);
        info.setCondition(DataSyncParser.sanitizeConfigValue(condition));
        String trimmed = condition == null ? "" : condition.trim();
        if (!StringUtils.isNotEmpty(trimmed)) {
            info.setLimitation("filter condition is empty");
            return info;
        }
        for (String dependency : DataSyncParser.expressionDependencies(trimmed)) {
            info.addDependency(dependency);
        }
        String sqlCondition = DataSyncParser.convertFilterConditionToSql(trimmed);
        if (!StringUtils.isNotEmpty(sqlCondition)) {
            info.setLimitation("filter condition could not be converted safely to SQL WHERE");
            return info;
        }
        info.setSqlCondition(sqlCondition);
        info.setSqlConvertible(true);
        return info;
    }

    private static String convertFilterConditionToSql(String condition) {
        if (!StringUtils.isNotEmpty(condition)) {
            return "";
        }
        String converted = condition.trim();
        converted = DataSyncParser.convertIsNullBooleanCondition(converted);
        if (!StringUtils.isNotEmpty(converted)) {
            return "";
        }
        if (DataSyncParser.findUnsupportedFunction(converted).length() > 0) {
            return "";
        }
        converted = DataSyncParser.convertInfaExpressionToOracleSql(converted);
        converted = converted.replaceAll("(?i)\\bTRUE\\b", "1 = 1");
        converted = converted.replaceAll("(?i)\\bFALSE\\b", "1 = 0");
        if (converted.matches("(?i).*\\bISNULL\\s*\\(.*")) {
            return "";
        }
        return converted;
    }

    private static String convertIsNullBooleanCondition(String condition) {
        String converted = condition;
        converted = converted.replaceAll("(?i)\\bISNULL\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)\\s*=\\s*(?:FALSE|0)", "$1 IS NOT NULL");
        converted = converted.replaceAll("(?i)\\bISNULL\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)\\s*(?:!=|<>)\\s*(?:TRUE|1)", "$1 IS NOT NULL");
        converted = converted.replaceAll("(?i)\\bISNULL\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)\\s*=\\s*(?:TRUE|1)", "$1 IS NULL");
        converted = converted.replaceAll("(?i)\\bISNULL\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)\\s*(?:!=|<>)\\s*(?:FALSE|0)", "$1 IS NULL");
        converted = converted.replaceAll("(?i)\\bNOT\\s+ISNULL\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)", "$1 IS NOT NULL");
        converted = converted.replaceAll("(?i)\\bISNULL\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)", "$1 IS NULL");
        return converted;
    }

    private static LookupFieldInfo analyzeLookup(String transformationName, Element transEl) {
        LookupFieldInfo info = new LookupFieldInfo();
        info.setTransformationName(transformationName);
        info.setLookupCondition(DataSyncParser.sanitizeConfigValue(DataSyncParser.getTableAttribute(transEl, "Lookup condition")));
        info.setLookupSqlOverride(DataSyncParser.sanitizeConfigValue(DataSyncParser.getTableAttribute(transEl, "Lookup Sql Override")));
        info.setLookupTableName(DataSyncParser.sanitizeConfigValue(DataSyncParser.getTableAttribute(transEl, "Lookup table name")));
        info.setLookupSourceFilter(DataSyncParser.sanitizeConfigValue(DataSyncParser.getTableAttribute(transEl, "Lookup Source Filter")));
        info.setConnectionInformation(DataSyncParser.sanitizeConfigValue(DataSyncParser.getTableAttribute(transEl, "Connection Information")));
        info.setSourceType(DataSyncParser.sanitizeConfigValue(DataSyncParser.getTableAttribute(transEl, "Source Type")));
        info.setCachingEnabled(DataSyncParser.sanitizeConfigValue(DataSyncParser.getTableAttribute(transEl, "Lookup caching enabled")));
        info.setMultipleMatchPolicy(DataSyncParser.sanitizeConfigValue(DataSyncParser.getTableAttribute(transEl, "Lookup policy on multiple match")));
        return info;
    }

    private static void updateLookupSqlJoinSupport(LookupFieldInfo info) {
        if (info == null) {
            return;
        }
        if (!StringUtils.isNotEmpty(info.getLookupSqlOverride()) && !StringUtils.isNotEmpty(info.getLookupTableName())) {
            info.addLimitation("lookup source SQL/table is empty");
            return;
        }
        if (!StringUtils.isNotEmpty(info.getLookupCondition())) {
            info.addLimitation("lookup condition is empty");
            return;
        }
        if (java.util.regex.Pattern.compile("(?i)\\s+OR\\s+").matcher(info.getLookupCondition()).find()) {
            info.addLimitation("lookup condition contains OR, so automatic SQL join is not safe");
            return;
        }
        List<LookupConditionPredicate> predicates = DataSyncParser.parseLookupConditionPredicates(info.getLookupCondition());
        if (predicates.isEmpty()) {
            info.addLimitation("lookup condition could not be parsed into simple =, !=, <> predicates");
            return;
        }
        List<String> sqlConditions = new ArrayList<String>();
        boolean hasNonEquality = false;
        for (LookupConditionPredicate predicate : predicates) {
            String sqlCondition = DataSyncParser.createLookupPredicateSql(info, predicate, "lookup_", "base_", false);
            if (!StringUtils.isNotEmpty(sqlCondition)) {
                info.addLimitation(DataSyncParser.lookupPredicateLimitation(info, predicate));
                return;
            }
            if (!predicate.isEquality()) {
                hasNonEquality = true;
            }
            sqlConditions.add(sqlCondition);
        }
        StringBuilder sqlJoinCondition = new StringBuilder();
        for (int i = 0; i < sqlConditions.size(); i++) {
            if (i > 0) {
                sqlJoinCondition.append(" AND ");
            }
            sqlJoinCondition.append(sqlConditions.get(i));
        }
        info.setSqlJoinCondition(sqlJoinCondition.toString());
        if (hasNonEquality) {
            info.setFdlDataFlowReviewRequired(true);
            info.addLimitation("non-equality lookup predicate was parsed, but automatic execution is review-required because SQL LEFT JOIN may multiply rows and Informatica lookup first-match/cache policy is not proven equivalent"
                + " (cache=" + (StringUtils.isNotEmpty(info.getCachingEnabled()) ? info.getCachingEnabled() : "-")
                + "; multipleMatchPolicy=" + (StringUtils.isNotEmpty(info.getMultipleMatchPolicy()) ? info.getMultipleMatchPolicy() : "-") + ")");
            return;
        }
        info.setSqlJoinSupported(true);
    }

    private static String createLookupPredicateSql(LookupFieldInfo info, LookupConditionPredicate predicate, String lookupAlias, String baseAlias, boolean sparkIdentifier) {
        if (info == null || predicate == null) {
            return "";
        }
        String leftInputAlias = info.getInputReadAlias(predicate.getLeft());
        String rightInputAlias = info.getInputReadAlias(predicate.getRight());
        if (StringUtils.isNotEmpty(leftInputAlias) == StringUtils.isNotEmpty(rightInputAlias)) {
            return "";
        }
        String lookupField;
        String baseField;
        if (StringUtils.isNotEmpty(leftInputAlias)) {
            lookupField = predicate.getRight();
            baseField = leftInputAlias;
        } else {
            lookupField = predicate.getLeft();
            baseField = rightInputAlias;
        }
        String lookupColumn = sparkIdentifier ? DataSyncParser.safeSparkIdentifier(lookupField) : DataSyncParser.safeSqlAlias(lookupField);
        String baseColumn = sparkIdentifier ? DataSyncParser.safeSparkIdentifier(baseField) : DataSyncParser.safeSqlAlias(baseField);
        return lookupAlias + "." + lookupColumn + " " + predicate.getOperator() + " " + baseAlias + "." + baseColumn;
    }

    private static String lookupPredicateLimitation(LookupFieldInfo info, LookupConditionPredicate predicate) {
        List<String> unresolvedInputs = new ArrayList<String>();
        if (DataSyncParser.containsIgnoreCase(info.getInputFields(), predicate.getLeft())
            && !StringUtils.isNotEmpty(info.getInputReadAlias(predicate.getLeft()))) {
            unresolvedInputs.add(predicate.getLeft());
        }
        if (DataSyncParser.containsIgnoreCase(info.getInputFields(), predicate.getRight())
            && !StringUtils.isNotEmpty(info.getInputReadAlias(predicate.getRight()))) {
            unresolvedInputs.add(predicate.getRight());
        }
        if (!unresolvedInputs.isEmpty()) {
            return "lookup input field(s) remain unresolved for predicate " + predicate.toSource() + ": " + DataSyncParser.joinList(unresolvedInputs);
        }
        return "lookup predicate does not pair one lookup reader field with one traceable SQL reader input field: " + predicate.toSource();
    }

    private static List<String> splitLookupCondition(String condition) {
        List<String> result = new ArrayList<String>();
        if (!StringUtils.isNotEmpty(condition)) {
            return result;
        }
        String[] parts = condition.split("(?i)\\s+AND\\s+");
        for (String part : parts) {
            if (StringUtils.isNotEmpty(part)) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private static List<LookupConditionPredicate> parseLookupConditionPredicates(String condition) {
        List<LookupConditionPredicate> result = new ArrayList<LookupConditionPredicate>();
        for (String part : DataSyncParser.splitLookupCondition(condition)) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(=|!=|<>)\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*$").matcher(part);
            if (!matcher.matches()) {
                return new ArrayList<LookupConditionPredicate>();
            }
            result.add(new LookupConditionPredicate(matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        return result;
    }

    private static ExpressionFieldInfo analyzeExpression(String transformationName, String fieldName, String expression) {
        ExpressionFieldInfo info = new ExpressionFieldInfo();
        info.setTransformationName(transformationName);
        info.setFieldName(fieldName);
        info.setExpression(expression);
        String trimmed = expression == null ? "" : expression.trim();
        if (!StringUtils.isNotEmpty(trimmed) || DataSyncParser.normalizeLineageName(trimmed).equals(DataSyncParser.normalizeLineageName(fieldName))) {
            info.setKind(ExpressionFieldInfo.KIND_PASS_THROUGH);
            return info;
        }
        if (trimmed.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            info.setKind(ExpressionFieldInfo.KIND_RENAME);
            info.addDependency(trimmed);
            return info;
        }
        for (String dependency : DataSyncParser.expressionDependencies(trimmed)) {
            info.addDependency(dependency);
        }
        if (DataSyncParser.isConstantExpression(trimmed)) {
            info.setKind(ExpressionFieldInfo.KIND_CONSTANT);
            info.setSqlExpression(trimmed);
            info.setSqlConvertible(true);
            return info;
        }
        if (trimmed.toUpperCase().startsWith("ERROR(")) {
            info.setKind(ExpressionFieldInfo.KIND_ERROR);
            info.setLimitation("Informatica ERROR(...) raises a transformation error and is not converted to runnable FDL/SQL.");
            return info;
        }
        String unsupportedFunction = DataSyncParser.findUnsupportedFunction(trimmed);
        if (StringUtils.isNotEmpty(unsupportedFunction)) {
            info.setKind(ExpressionFieldInfo.KIND_UNSUPPORTED);
            info.setLimitation("unsupported Informatica function: " + unsupportedFunction);
            return info;
        }
        String sqlExpression = DataSyncParser.convertInfaExpressionToOracleSql(trimmed);
        if (!StringUtils.isNotEmpty(sqlExpression)) {
            info.setKind(ExpressionFieldInfo.KIND_UNSUPPORTED);
            info.setLimitation("expression could not be converted safely");
            return info;
        }
        if (DataSyncParser.containsFunction(trimmed, "IIF")
            || DataSyncParser.containsFunction(trimmed, "DECODE")
            || DataSyncParser.containsFunction(trimmed, "ISNULL")) {
            info.setKind(ExpressionFieldInfo.KIND_CONDITIONAL);
        } else {
            info.setKind(ExpressionFieldInfo.KIND_SQL_EXPRESSION);
        }
        info.setSqlExpression(sqlExpression);
        info.setSqlConvertible(true);
        return info;
    }

    private static boolean isConstantExpression(String expression) {
        return expression.matches("'([^']|'')*'")
            || expression.matches("[+-]?\\d+(\\.\\d+)?")
            || "NULL".equalsIgnoreCase(expression);
    }

    private static List<String> expressionDependencies(String expression) {
        List<String> result = new ArrayList<String>();
        String scrubbed = expression.replaceAll("'([^']|'')*'", " ");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b").matcher(scrubbed);
        while (matcher.find()) {
            String token = matcher.group();
            String upper = token.toUpperCase();
            if (DataSyncParser.isExpressionKeyword(upper)) {
                continue;
            }
            int next = matcher.end();
            while (next < scrubbed.length() && Character.isWhitespace(scrubbed.charAt(next))) {
                next++;
            }
            if (next < scrubbed.length() && scrubbed.charAt(next) == '(') {
                continue;
            }
            if (!DataSyncParser.containsIgnoreCase(result, token)) {
                result.add(token);
            }
        }
        return result;
    }

    private static boolean isExpressionKeyword(String upper) {
        return "AND".equals(upper)
            || "OR".equals(upper)
            || "NOT".equals(upper)
            || "NULL".equals(upper)
            || "TRUE".equals(upper)
            || "FALSE".equals(upper)
            || "CASE".equals(upper)
            || "WHEN".equals(upper)
            || "THEN".equals(upper)
            || "ELSE".equals(upper)
            || "END".equals(upper)
            || "IS".equals(upper);
    }

    private static String findUnsupportedFunction(String expression) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(").matcher(expression);
        while (matcher.find()) {
            String function = matcher.group(1);
            if (!DataSyncParser.isSupportedExpressionFunction(function)) {
                return function;
            }
        }
        return "";
    }

    private static boolean isSupportedExpressionFunction(String function) {
        String upper = function == null ? "" : function.toUpperCase();
        return "DECODE".equals(upper)
            || "IIF".equals(upper)
            || "ISNULL".equals(upper)
            || "NVL".equals(upper)
            || "TO_CHAR".equals(upper)
            || "TO_DATE".equals(upper)
            || "TRUNC".equals(upper)
            || "SUBSTR".equals(upper)
            || "LTRIM".equals(upper)
            || "RTRIM".equals(upper)
            || "LPAD".equals(upper)
            || "RPAD".equals(upper)
            || "ROUND".equals(upper)
            || "ABS".equals(upper)
            || "UPPER".equals(upper)
            || "LOWER".equals(upper)
            || "LENGTH".equals(upper)
            || "SYSDATE".equals(upper);
    }

    private static String convertInfaExpressionToOracleSql(String expression) {
        String converted = DataSyncParser.convertIifExpression(expression);
        converted = DataSyncParser.convertDecodeExpression(converted);
        converted = DataSyncParser.convertIsNullExpression(converted);
        return converted;
    }

    private static String convertIifExpression(String expression) {
        String trimmed = expression == null ? "" : expression.trim();
        if (!DataSyncParser.startsWithFunction(trimmed, "IIF")) {
            return trimmed;
        }
        int open = trimmed.indexOf('(');
        int close = DataSyncParser.findMatchingParen(trimmed, open);
        if (open < 0 || close != trimmed.length() - 1) {
            return trimmed;
        }
        List<String> args = DataSyncParser.splitExpressionArguments(trimmed.substring(open + 1, close));
        if (args.size() != 3) {
            return trimmed;
        }
        return "CASE WHEN " + DataSyncParser.convertInfaExpressionToOracleSql(args.get(0))
            + " THEN " + DataSyncParser.convertInfaExpressionToOracleSql(args.get(1))
            + " ELSE " + DataSyncParser.convertInfaExpressionToOracleSql(args.get(2))
            + " END";
    }

    private static String convertDecodeExpression(String expression) {
        String trimmed = expression == null ? "" : expression.trim();
        if (!DataSyncParser.startsWithFunction(trimmed, "DECODE")) {
            return trimmed;
        }
        int open = trimmed.indexOf('(');
        int close = DataSyncParser.findMatchingParen(trimmed, open);
        if (open < 0 || close != trimmed.length() - 1) {
            return trimmed;
        }
        List<String> args = DataSyncParser.splitExpressionArguments(trimmed.substring(open + 1, close));
        if (args.size() < 3) {
            return trimmed;
        }
        String expressionValue = DataSyncParser.convertInfaExpressionToOracleSql(args.get(0));
        int pairEnd = args.size();
        String defaultValue = "NULL";
        if ((args.size() - 1) % 2 == 1) {
            defaultValue = DataSyncParser.convertInfaExpressionToOracleSql(args.get(args.size() - 1));
            pairEnd = args.size() - 1;
        }
        StringBuilder result = new StringBuilder();
        result.append("CASE");
        for (int i = 1; i + 1 < pairEnd; i += 2) {
            result.append(" WHEN ").append(expressionValue)
                .append(" = ").append(DataSyncParser.convertInfaExpressionToOracleSql(args.get(i)))
                .append(" THEN ").append(DataSyncParser.convertInfaExpressionToOracleSql(args.get(i + 1)));
        }
        result.append(" ELSE ").append(defaultValue).append(" END");
        return result.toString();
    }

    private static String convertIsNullExpression(String expression) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)\\bISNULL\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)").matcher(expression == null ? "" : expression);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, "(" + matcher.group(1) + " IS NULL)");
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean startsWithFunction(String expression, String function) {
        return expression != null && expression.toUpperCase().startsWith(function.toUpperCase() + "(");
    }

    private static boolean containsFunction(String expression, String function) {
        return java.util.regex.Pattern.compile("(?i)\\b" + function + "\\s*\\(").matcher(expression == null ? "" : expression).find();
    }

    private static int findMatchingParen(String value, int openIndex) {
        if (value == null || openIndex < 0 || openIndex >= value.length() || value.charAt(openIndex) != '(') {
            return -1;
        }
        boolean inString = false;
        int depth = 0;
        for (int i = openIndex; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'') {
                inString = !inString;
            }
            if (inString) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<String> splitExpressionArguments(String args) {
        List<String> result = new ArrayList<String>();
        if (args == null) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        int depth = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '\'') {
                inString = !inString;
                current.append(c);
                continue;
            }
            if (!inString) {
                if (c == '(') {
                    depth++;
                } else if (c == ')' && depth > 0) {
                    depth--;
                }
                if (c == ',' && depth == 0) {
                    result.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result;
    }

    private static boolean hasOnlySqlPushdownDependencies(List<String> dependencySources) {
        if (dependencySources == null || dependencySources.isEmpty()) {
            return true;
        }
        for (String source : dependencySources) {
            if (source == null || source.contains("unresolved")) {
                return false;
            }
            if (!(source.contains("[Source Qualifier]") || source.contains("[Source Definition]"))) {
                return false;
            }
        }
        return true;
    }

    private static String describeNonSqlDependencyLimitation(List<String> dependencySources) {
        String sources = DataSyncParser.joinList(dependencySources);
        if (sources.toLowerCase().contains("custom transformation")
            || sources.toLowerCase().contains("web service")
            || sources.toLowerCase().contains("external procedure")) {
            return "depends on external-call output; upstream call must be FDL translation ready or have generated adapter contract evidence before this expression can be runnable: " + sources;
        }
        return "depends on non-SQL upstream fields: " + sources;
    }

    private static String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append("; ");
            }
            result.append(values.get(i));
        }
        return result.toString();
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        for (String existing : values) {
            if (existing.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeLineageName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
            .replace("\"", "")
            .replace("`", "")
            .replace("[", "")
            .replace("]", "")
            .replaceAll("\\s+", "")
            .toUpperCase();
    }

    private static String resolveSchema(String toDBName, String extraToDBName) throws Exception {
        if (StringUtils.isNotEmpty(extraToDBName)) {
            String userName = FineUtils.getConnectionUserName(extraToDBName);
            if (StringUtils.isNotEmpty(userName)) {
                return userName.toUpperCase();
            }
        }
        if (StringUtils.isNotEmpty(toDBName)) {
            String userName = FineUtils.getConnectionUserName(toDBName);
            if (StringUtils.isNotEmpty(userName)) {
                return userName.toUpperCase();
            }
        }
        return "";
    }

    private static Map<String, List<ParamEntity>> parseAllMappingVariables(Document doc) {
        Map<String, List<ParamEntity>> result = new HashMap<String, List<ParamEntity>>();
        NodeList mappingNodes = doc.getElementsByTagName(TAG_MAPPING);
        for (int i = 0; i < mappingNodes.getLength(); i++) {
            Element mappingEl = (Element) mappingNodes.item(i);
            String mappingName = mappingEl.getAttribute(TAG_NAME);
            if (!StringUtils.isNotEmpty(mappingName)) continue;
            NodeList varNodes = mappingEl.getElementsByTagName("MAPPINGVARIABLE");
            for (int j = 0; j < varNodes.getLength(); j++) {
                Element varEl = (Element) varNodes.item(j);
                if (!"YES".equals(varEl.getAttribute("USERDEFINED"))) continue;
                String name = varEl.getAttribute(TAG_NAME);
                if (name != null && name.startsWith("$$")) {
                    name = name.substring(2);
                }
                String value = varEl.getAttribute("DEFAULTVALUE");
                String dataType = varEl.getAttribute("DATATYPE");
                String valueType = DataSyncParser.mapDataType(dataType);
                ParamEntity param = ParamEntity.Builder.aParamEntity().withName(name).withValue(value != null ? value : "").withValueType(valueType).withDescription(varEl.getAttribute("DESCRIPTION")).build();
                List<ParamEntity> params = result.get(mappingName);
                if (params == null) {
                    params = new ArrayList<ParamEntity>();
                    result.put(mappingName, params);
                }
                params.add(param);
            }
        }
        return result;
    }

    private static Map<String, List<ParamEntity>> parseAllMappingVariables(InfaScopedParameterResolver resolver) {
        Map<String, List<ParamEntity>> result = new HashMap<String, List<ParamEntity>>();
        if (resolver == null) {
            return result;
        }
        for (InfaScopedParameterResolver.Declaration declaration : resolver.getDeclarations()) {
            if (!InfaScopedParameterResolver.KIND_MAPPING_VARIABLE.equals(declaration.getKind())
                && !InfaScopedParameterResolver.KIND_MAPPING_PARAMETER.equals(declaration.getKind())) {
                continue;
            }
            String mappingName = declaration.getScopeName();
            if (!StringUtils.isNotEmpty(mappingName)) {
                continue;
            }
            List<ParamEntity> params = result.get(mappingName);
            if (params == null) {
                params = new ArrayList<ParamEntity>();
                result.put(mappingName, params);
            }
            params.add(DataSyncParser.toParamEntity(declaration));
        }
        return result;
    }

    private static List<ParamEntity> parseMappingVariablesForMapping(Document doc, String mappingName) {
        List<ParamEntity> result = new ArrayList<ParamEntity>();
        NodeList mappingNodes = doc.getElementsByTagName(TAG_MAPPING);
        for (int i = 0; i < mappingNodes.getLength(); i++) {
            Element mappingEl = (Element) mappingNodes.item(i);
            if (!mappingName.equals(mappingEl.getAttribute(TAG_NAME))) {
                continue;
            }
            NodeList varNodes = mappingEl.getElementsByTagName("MAPPINGVARIABLE");
            for (int j = 0; j < varNodes.getLength(); j++) {
                Element varEl = (Element) varNodes.item(j);
                if (!"YES".equals(varEl.getAttribute("USERDEFINED"))) {
                    continue;
                }
                String name = varEl.getAttribute(TAG_NAME);
                if (name != null && name.startsWith("$$")) {
                    name = name.substring(2);
                }
                String value = varEl.getAttribute("DEFAULTVALUE");
                String dataType = varEl.getAttribute("DATATYPE");
                String valueType = DataSyncParser.mapDataType(dataType);
                result.add(ParamEntity.Builder.aParamEntity().withName(name).withValue(value != null ? value : "").withValueType(valueType).withDescription(varEl.getAttribute("DESCRIPTION")).build());
            }
            break;
        }
        return result;
    }

    private static List<ParamEntity> parseWorkflowVariables(Element workflowElem, String sessionName) {
        List<ParamEntity> result = new ArrayList<ParamEntity>();
        NodeList varNodes = workflowElem.getElementsByTagName("WORKFLOWVARIABLE");
        for (int i = 0; i < varNodes.getLength(); i++) {
            Element varEl = (Element) varNodes.item(i);
            if (!"YES".equals(varEl.getAttribute("USERDEFINED"))) {
                continue;
            }
            String name = varEl.getAttribute(TAG_NAME);
            if (name != null && name.startsWith("$")) {
                int dotIdx = name.indexOf('.');
                if (dotIdx > 0) {
                    name = name.substring(dotIdx + 1);
                } else {
                    name = name.substring(1);
                }
            }
            String value = varEl.getAttribute("DEFAULTVALUE");
            String dataType = varEl.getAttribute("DATATYPE");
            String valueType = DataSyncParser.mapDataType(dataType);
            result.add(ParamEntity.Builder.aParamEntity().withName(name).withValue(value != null ? value : "").withValueType(valueType).withDescription(varEl.getAttribute("DESCRIPTION")).build());
        }
        return result;
    }

    private static List<ParamEntity> parseWorkflowVariables(InfaScopedParameterResolver resolver, String workflowName, String workletName) {
        List<ParamEntity> result = new ArrayList<ParamEntity>();
        if (resolver == null) {
            return result;
        }
        for (InfaScopedParameterResolver.Declaration declaration : resolver.getDeclarations()) {
            if (!InfaScopedParameterResolver.KIND_WORKFLOW_VARIABLE.equals(declaration.getKind())) {
                continue;
            }
            if (StringUtils.isNotEmpty(workletName)) {
                if (!InfaScopedParameterResolver.SCOPE_WORKLET.equals(declaration.getScopeType())
                    || !workletName.equals(declaration.getScopeName())) {
                    continue;
                }
            } else if (!InfaScopedParameterResolver.SCOPE_WORKFLOW.equals(declaration.getScopeType())
                || !workflowName.equals(declaration.getScopeName())) {
                continue;
            }
            result.add(DataSyncParser.toParamEntity(declaration));
        }
        return result;
    }

    private static ParamEntity toParamEntity(InfaScopedParameterResolver.Declaration declaration) {
        String value = declaration.hasXmlDefault()
            ? declaration.getDefaultValue()
            : InfaScopedParameterResolver.runtimePlaceholder(declaration.getKind(), declaration.getName());
        String description = declaration.getDescription();
        String statusText = "infa_scope=" + declaration.getScope()
            + "; default_source=" + declaration.getDefaultSource();
        if (StringUtils.isNotEmpty(description)) {
            description = description + "; " + statusText;
        } else {
            description = statusText;
        }
        return ParamEntity.Builder.aParamEntity()
            .withName(DataSyncParser.normalizeParamEntityName(declaration.getName()))
            .withValue(value)
            .withValueType(DataSyncParser.mapDataType(declaration.getDataType()))
            .withDescription(description)
            .build();
    }

    private static List<ParamEntity> filterGeneratedWorkParams(List<ParamEntity> params) {
        if (DataSyncParser.emitGeneratedWorkParams()) {
            return params;
        }
        // Keep converted tasks compatible with FDL's native name-based global parameter lookup.
        return new ArrayList<ParamEntity>();
    }

    private static boolean emitGeneratedWorkParams() {
        String configured = System.getProperty(EMIT_GENERATED_WORK_PARAMS_PROPERTY);
        if (!StringUtils.isNotEmpty(configured)) {
            try {
                configured = System.getenv(EMIT_GENERATED_WORK_PARAMS_ENV);
            } catch (Throwable ignored) {
                configured = "";
            }
        }
        return StringUtils.isNotEmpty(configured)
            && ("true".equalsIgnoreCase(configured.trim()) || "1".equals(configured.trim()));
    }

    private static String normalizeParamEntityName(String name) {
        if (name == null) {
            return "";
        }
        String result = name.trim();
        while (result.startsWith("$")) {
            result = result.substring(1);
        }
        return result;
    }

    private static String mapDataType(String informaticaType) {
        if (informaticaType == null) {
            return "STRING";
        }
        String lower = informaticaType.toLowerCase();
        if (lower.contains("integer") || lower.equals("int")) {
            return "INT";
        }
        if (lower.contains("double") || lower.contains("number") || lower.contains("decimal")) {
            return "DOUBLE";
        }
        if (lower.contains("date")) {
            return "DATE";
        }
        if (lower.contains("boolean")) {
            return "BOOLEAN";
        }
        return "STRING";
    }

    private static void substituteSqlParams(GroupSQLInfo group, String workflowName, String workletName,
        InfaScopedParameterResolver parameterResolver, InfaScopedParameterResolver.Scope parameterScope) {
        String sessionName = group.getName();
        for (SQLInfo sqlInfo : group.getSqlInfos()) {
            if (StringUtils.isNotEmpty(sqlInfo.getSqlQuery())) {
                sqlInfo.setSqlQuery(DataSyncParser.substituteParams(sqlInfo.getSqlQuery(), workflowName, sessionName, parameterResolver, parameterScope));
            }
            if (StringUtils.isNotEmpty(sqlInfo.getPreSQL())) {
                sqlInfo.setPreSQL(DataSyncParser.substituteParams(sqlInfo.getPreSQL(), workflowName, sessionName, parameterResolver, parameterScope));
            }
            if (StringUtils.isNotEmpty(sqlInfo.getPostSQL())) {
                sqlInfo.setPostSQL(DataSyncParser.substituteParams(sqlInfo.getPostSQL(), workflowName, sessionName, parameterResolver, parameterScope));
            }
        }
    }

    private static String substituteParams(String sql, String workflowName, String sessionName,
        InfaScopedParameterResolver parameterResolver, InfaScopedParameterResolver.Scope parameterScope) {
        if (sql == null) {
            return null;
        }
        sql = sql.replace("'$PMWorkflowName'", "'" + workflowName + "'");
        sql = sql.replace("$PMWorkflowName", workflowName);
        sql = sql.replace("'$PMSessionName'", "'" + sessionName + "'");
        sql = sql.replace("$PMSessionName", sessionName);
        String sqlBeforeScopedReplacement = sql;
        InfaScopedParameterResolver.Replacement replacement = DataSyncParser.replaceScopedParameters(parameterResolver, sql, parameterScope);
        if (replacement != null) {
            sql = DataSyncParser.replaceScopedParametersForExecutableSql(parameterResolver, sqlBeforeScopedReplacement, parameterScope);
        }
        return sql;
    }

    private static String replaceScopedParametersForExecutableSql(InfaScopedParameterResolver resolver,
                                                                  String sql,
                                                                  InfaScopedParameterResolver.Scope scope) {
        if (resolver == null || sql == null || !InfaScopedParameterResolver.referencePattern().matcher(sql).find()) {
            return sql;
        }
        java.util.regex.Matcher matcher = InfaScopedParameterResolver.referencePattern().matcher(sql);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            InfaScopedParameterResolver.Resolution resolution = resolver.resolve(matcher.group(), scope);
            String replacement = DataSyncParser.sqlReplacementText(sql, matcher.start(), matcher.end(), resolution);
            matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String sqlReplacementText(String sql,
                                             int start,
                                             int end,
                                             InfaScopedParameterResolver.Resolution resolution) {
        if (resolution == null) {
            return "NULL";
        }
        InfaScopedParameterResolver.Declaration declaration = resolution.getDeclaration();
        boolean quotedLiteral = DataSyncParser.isSingleQuotedToken(sql, start, end);
        if (declaration != null && InfaScopedParameterResolver.DEFAULT_XML_EMPTY.equals(declaration.getDefaultSource())) {
            String placeholder = DataSyncParser.sqlRuntimeParameterPlaceholder(resolution);
            if (StringUtils.isNotEmpty(placeholder)) {
                return placeholder;
            }
            return quotedLiteral ? "" : "NULL";
        }
        String replacementText = resolution.getReplacementText();
        if (StringUtils.isNotEmpty(replacementText) && !replacementText.startsWith("${INFA_RUNTIME:")) {
            return replacementText;
        }
        String placeholder = DataSyncParser.sqlRuntimeParameterPlaceholder(resolution);
        if (StringUtils.isNotEmpty(placeholder)) {
            return placeholder;
        }
        return quotedLiteral ? "" : "NULL";
    }

    private static String sqlRuntimeParameterPlaceholder(InfaScopedParameterResolver.Resolution resolution) {
        if (!DataSyncParser.isSqlRuntimeParameterResolution(resolution)) {
            return "";
        }
        String name = "";
        InfaScopedParameterResolver.Declaration declaration = resolution.getDeclaration();
        if (declaration != null) {
            name = DataSyncParser.normalizeParamEntityName(declaration.getName());
        }
        if (!StringUtils.isNotEmpty(name)) {
            name = DataSyncParser.runtimeParameterNameFromReference(resolution.getReference());
        }
        if (!DataSyncParser.isSafeFdlSqlParameterName(name)) {
            return "";
        }
        return "${" + name + "}";
    }

    private static boolean isSqlRuntimeParameterResolution(InfaScopedParameterResolver.Resolution resolution) {
        if (resolution == null) {
            return false;
        }
        String kind = resolution.getKind();
        return InfaScopedParameterResolver.KIND_MAPPING_VARIABLE.equals(kind)
            || InfaScopedParameterResolver.KIND_MAPPING_PARAMETER.equals(kind)
            || InfaScopedParameterResolver.KIND_WORKFLOW_VARIABLE.equals(kind)
            || InfaScopedParameterResolver.KIND_BRACED_PARAMETER.equals(kind);
    }

    private static String runtimeParameterNameFromReference(String reference) {
        String raw = reference == null ? "" : reference.trim();
        if (raw.startsWith("${") && raw.endsWith("}") && raw.length() > 3) {
            return raw.substring(2, raw.length() - 1).trim();
        }
        return DataSyncParser.normalizeParamEntityName(raw);
    }

    private static boolean isSafeFdlSqlParameterName(String name) {
        if (!StringUtils.isNotEmpty(name)) {
            return false;
        }
        char first = name.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    private static boolean isSingleQuotedToken(String text, int start, int end) {
        return text != null
            && start > 0
            && end < text.length()
            && text.charAt(start - 1) == '\''
            && text.charAt(end) == '\'';
    }

    private static InfaScopedParameterResolver.Scope scopeForGroup(Document doc, String workflowName, String workletName, GroupSQLInfo group) {
        String sessionName = group == null ? "" : group.getName();
        String mappingName = DataSyncParser.findSessionMappingName(doc, sessionName);
        return new InfaScopedParameterResolver.Scope(workflowName, workletName, sessionName, mappingName);
    }

    private static String findSessionMappingName(Document doc, String sessionName) {
        if (doc == null || !StringUtils.isNotEmpty(sessionName)) {
            return "";
        }
        NodeList sessions = doc.getElementsByTagName(TAG_SESSION);
        for (int i = 0; i < sessions.getLength(); i++) {
            Element sessionEl = (Element) sessions.item(i);
            if (sessionName.equals(sessionEl.getAttribute(TAG_NAME))) {
                return sessionEl.getAttribute("MAPPINGNAME");
            }
        }
        return "";
    }

    private static InfaScopedParameterResolver.Replacement replaceScopedParameters(InfaScopedParameterResolver resolver,
        String text, InfaScopedParameterResolver.Scope scope) {
        if (resolver == null || text == null || !InfaScopedParameterResolver.referencePattern().matcher(text).find()) {
            return null;
        }
        return resolver.replaceReferences(text, scope);
    }

    private static void appendParameterReplacementEvidence(StringBuilder description, InfaScopedParameterResolver.Replacement replacement) {
        if (description == null || replacement == null || replacement.getResolutions().isEmpty()) {
            return;
        }
        if (!replacement.requiredBindings().isEmpty()) {
            description.append("\nRequired scoped/runtime parameter bindings: ").append(DataSyncParser.joinList(replacement.requiredBindings()));
        }
        String limitations = replacement.limitationsText();
        if (StringUtils.isNotEmpty(limitations)) {
            description.append("\nParameter replacement limitations: ").append(DataSyncParser.sanitizeConfigValue(limitations));
        }
    }

    private static void parseConnectionReferences(Document doc, Element sessionEl, GroupSQLInfo group) {
        NodeList extNodes = sessionEl.getElementsByTagName(TAG_SESSIONEXTENSION);
        for (int i = 0; i < extNodes.getLength(); i++) {
            Element extEl = (Element) extNodes.item(i);
            String extType = extEl.getAttribute(TAG_TYPE);
            NodeList connRefs = extEl.getElementsByTagName(TAG_CONNECTIONREFERENCE);
            if (connRefs.getLength() == 0) {
                continue;
            }
            for (int j = 0; j < connRefs.getLength(); j++) {
                Element connRef = (Element) connRefs.item(j);
                String connName = connRef.getAttribute(TAG_CONNECTIONNAME);
                if (!StringUtils.isNotEmpty(connName)) {
                    continue;
                }
                ConnectionMappingInfo connectionInfo = DataSyncParser.createConnectionMappingInfo(doc, sessionEl, extEl, connRef);
                group.addConnectionInfo(connectionInfo);
                if ("READER".equals(extType)) {
                    if (!StringUtils.isNotEmpty(group.getFromDBName())) {
                        group.setFromDBName(connName);
                        group.setFromDBType(connectionInfo.getFdlDatasourceType());
                    }
                } else if ("WRITER".equals(extType)) {
                    if (!StringUtils.isNotEmpty(group.getToDBName())) {
                        group.setToDBName(connName);
                        group.setToDBType(connectionInfo.getFdlDatasourceType());
                    }
                }
            }
        }
        DataSyncParser.applyConnectionInfosToFieldLineageInfo(group);
    }

    private static ConnectionMappingInfo createConnectionMappingInfo(Document doc, Element sessionEl, Element extEl, Element connRef) {
        ConnectionMappingInfo info = new ConnectionMappingInfo();
        String connectionName = connRef.getAttribute(TAG_CONNECTIONNAME);
        String connectionType = connRef.getAttribute(TAG_CONNECTIONTYPE);
        String connectionSubtype = connRef.getAttribute(TAG_CONNECTIONSUBTYPE);
        String extensionType = extEl.getAttribute(TAG_TYPE);
        String transformationName = extEl.getAttribute("SINSTANCENAME");
        if (!StringUtils.isNotEmpty(transformationName)) {
            transformationName = extEl.getAttribute("DSQINSTNAME");
        }
        info.setConnectionName(connectionName);
        info.setConnectionType(connectionType);
        info.setConnectionSubtype(connectionSubtype);
        info.setExtensionType(extensionType);
        info.setExtensionName(extEl.getAttribute(TAG_NAME));
        info.setTransformationName(transformationName);
        info.setTransformationType(extEl.getAttribute("TRANSFORMATIONTYPE"));
        info.setSessionName(sessionEl.getAttribute(TAG_NAME));
        info.setMappingName(sessionEl.getAttribute("MAPPINGNAME"));
        info.setRawSql(DataSyncParser.extractConnectionRawSql(doc, sessionEl, extEl, transformationName));
        DataSyncParser.resolveFdlDatasourceMapping(info);
        return info;
    }

    private static String extractConnectionRawSql(Document doc, Element sessionEl, Element extEl, String transformationName) {
        LinkedHashSet<String> fragments = new LinkedHashSet<String>();
        DataSyncParser.addSqlMetadataFragments(fragments, extEl);
        NodeList sessionTransforms = sessionEl == null ? null : sessionEl.getElementsByTagName(TAG_SESSTRANSFORMATIONINST);
        if (sessionTransforms != null) {
            for (int i = 0; i < sessionTransforms.getLength(); i++) {
                Element transEl = (Element) sessionTransforms.item(i);
                if (DataSyncParser.matchesTransformationName(transEl, transformationName)) {
                    DataSyncParser.addSqlMetadataFragments(fragments, transEl);
                }
            }
        }
        String mappingName = sessionEl == null ? "" : sessionEl.getAttribute("MAPPINGNAME");
        Element mappingEl = DataSyncParser.findMappingElement(doc, mappingName);
        if (mappingEl != null) {
            DataSyncParser.addSqlFragmentsFromNamedElements(fragments, mappingEl, TAG_TRANSFORMATION, transformationName);
            DataSyncParser.addSqlFragmentsFromNamedElements(fragments, mappingEl, TAG_INSTANCE, transformationName);
        }
        return DataSyncParser.joinList(new ArrayList<String>(fragments));
    }

    private static void addSqlFragmentsFromNamedElements(Set<String> fragments, Element root, String tagName, String transformationName) {
        if (root == null || !StringUtils.isNotEmpty(tagName)) {
            return;
        }
        NodeList nodes = root.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (DataSyncParser.matchesTransformationName(element, transformationName)) {
                DataSyncParser.addSqlMetadataFragments(fragments, element);
            }
        }
    }

    private static boolean matchesTransformationName(Element element, String transformationName) {
        if (element == null || !StringUtils.isNotEmpty(transformationName)) {
            return false;
        }
        String[] attributeNames = new String[] {
            TAG_NAME, "SINSTANCENAME", "DSQINSTNAME", "TRANSFORMATIONNAME", "TRANSFORMATION_NAME", "INSTANCE"
        };
        for (String attributeName : attributeNames) {
            if (DataSyncParser.equalsIgnoreCaseNonEmpty(transformationName, element.getAttribute(attributeName))) {
                return true;
            }
        }
        return false;
    }

    private static void addSqlMetadataFragments(Set<String> fragments, Element element) {
        if (fragments == null || element == null) {
            return;
        }
        String[] attributeNames = new String[] {
            TAG_SQL_QUERY, "Lookup Sql Override", "Lookup Source Filter", TAG_PRE_SQL, TAG_POST_SQL
        };
        for (String attributeName : attributeNames) {
            String value = DataSyncParser.sanitizeConfigValue(DataSyncParser.getDirectMetadataAttribute(element, attributeName));
            if (StringUtils.isNotEmpty(value)) {
                fragments.add(attributeName + "=" + value);
            }
        }
    }

    private static void resolveFdlDatasourceMapping(ConnectionMappingInfo info) {
        String configuredType = DataSyncParser.resolveConfiguredConnectionType(info.getConnectionName());
        if (StringUtils.isNotEmpty(configuredType)) {
            String configuredDatasourceType = DataSyncParser.mapKnownFdlDatasourceType(configuredType);
            if (StringUtils.isNotEmpty(configuredDatasourceType)) {
                info.setFdlDatasourceType(configuredDatasourceType);
                info.setMappingStatus(ConnectionMappingInfo.STATUS_CONFIGURED);
                info.setMappingBasis("connection-name override");
                info.setLimitation("Connection type is supplied by explicit connection-name override; verify the FDL connection points to the same real database.");
                return;
            }
            info.setFdlDatasourceType(DataSyncParser.normalizeFdlDatasourceType(configuredType));
            info.setMappingStatus(ConnectionMappingInfo.STATUS_UNVERIFIED);
            info.setMappingBasis("connection-name override with unrecognized datasource type");
            info.setLimitation("Configured datasource type is not in the safe mapping list; implement an FDL datasourceType mapping or generic JDBC adapter before running.");
            return;
        }

        String runtimeConnectionType = DataSyncParser.resolveRuntimeConnectionType(info.getConnectionName());
        String runtimeDatasourceType = DataSyncParser.mapKnownFdlDatasourceType(runtimeConnectionType);
        if (StringUtils.isNotEmpty(runtimeDatasourceType)) {
            info.setFdlDatasourceType(runtimeDatasourceType);
            info.setMappingStatus(ConnectionMappingInfo.STATUS_CONFIGURED);
            info.setMappingBasis("existing FDL data connection matched by connectionName");
            if (DataSyncParser.isOdbcConnection(info.getConnectionType(), info.getConnectionSubtype())) {
                info.setLimitation("");
            } else {
                info.setLimitation("");
            }
            return;
        }
        if (StringUtils.isNotEmpty(runtimeConnectionType)) {
            info.setFdlDatasourceType(DataSyncParser.normalizeFdlDatasourceType(runtimeConnectionType));
            info.setMappingStatus(ConnectionMappingInfo.STATUS_CONFIGURED);
            info.setMappingBasis("existing FDL data connection matched by connectionName");
            info.setLimitation("");
            return;
        }

        String safeDatasourceType = DataSyncParser.mapKnownFdlDatasourceType(info.getConnectionSubtype());
        if (!StringUtils.isNotEmpty(safeDatasourceType)) {
            safeDatasourceType = DataSyncParser.mapKnownFdlDatasourceType(info.getConnectionType());
        }
        if (StringUtils.isNotEmpty(safeDatasourceType)) {
            info.setFdlDatasourceType(safeDatasourceType);
            info.setMappingStatus(ConnectionMappingInfo.STATUS_SAFE);
            info.setMappingBasis("recognized Informatica connection subtype");
            info.setLimitation("");
            return;
        }

        if (DataSyncParser.isOdbcConnection(info.getConnectionType(), info.getConnectionSubtype())) {
            info.setFdlDatasourceType("");
            info.setMappingStatus(ConnectionMappingInfo.STATUS_UNVERIFIED);
            info.setMappingBasis("FDL data connection name match required");
            info.setLimitation("XML expresses the data connection name, but the current runtime did not return a datasourceType for that name. Create or rename the FDL data connection to match before running; do not infer from ODBC/JDBC_ODBC.");
            return;
        }

        String rawType = StringUtils.isNotEmpty(info.getConnectionSubtype()) ? info.getConnectionSubtype() : info.getConnectionType();
        info.setFdlDatasourceType(DataSyncParser.normalizeFdlDatasourceType(rawType));
        info.setMappingStatus(ConnectionMappingInfo.STATUS_UNVERIFIED);
        info.setMappingBasis("unrecognized Informatica connection type");
        info.setLimitation("Original connection type is not in the safe mapping list; implement an FDL datasourceType mapping before running.");
    }

    private static String resolveRuntimeConnectionType(String connectionName) {
        if (!StringUtils.isNotEmpty(connectionName)) {
            return "";
        }
        try {
            return FineUtils.getConnectionType(connectionName);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String resolveConfiguredConnectionType(String connectionName) {
        String key = DataSyncParser.normalizeConnectionKey(connectionName);
        String configured = CONNECTION_NAME_DATASOURCE_TYPE_OVERRIDES.get(key);
        if (StringUtils.isNotEmpty(configured)) {
            return configured;
        }
        configured = DataSyncParser.findConnectionTypeOverride(System.getProperty(CONNECTION_TYPE_OVERRIDE_PROPERTY), connectionName);
        if (StringUtils.isNotEmpty(configured)) {
            return configured;
        }
        try {
            configured = DataSyncParser.findConnectionTypeOverride(System.getenv(CONNECTION_TYPE_OVERRIDE_ENV), connectionName);
        } catch (Throwable ignored) {
            configured = "";
        }
        return configured;
    }

    private static String findConnectionTypeOverride(String spec, String connectionName) {
        if (!StringUtils.isNotEmpty(spec) || !StringUtils.isNotEmpty(connectionName)) {
            return "";
        }
        String wantedKey = DataSyncParser.normalizeConnectionKey(connectionName);
        String[] entries = spec.split("[,;]");
        for (String entry : entries) {
            String trimmed = entry == null ? "" : entry.trim();
            if (!StringUtils.isNotEmpty(trimmed)) {
                continue;
            }
            int split = trimmed.indexOf('=');
            if (split < 0) {
                split = trimmed.indexOf(':');
            }
            if (split <= 0 || split >= trimmed.length() - 1) {
                continue;
            }
            String name = trimmed.substring(0, split).trim();
            String type = trimmed.substring(split + 1).trim();
            if (wantedKey.equals(DataSyncParser.normalizeConnectionKey(name))) {
                return type;
            }
        }
        return "";
    }

    private static String mapKnownFdlDatasourceType(String rawType) {
        if (!StringUtils.isNotEmpty(rawType)) {
            return "";
        }
        String normalized = DataSyncParser.normalizeConnectionKey(rawType);
        if (normalized.contains("ORACLE")) {
            return "oracle";
        }
        if (normalized.contains("MYSQL")) {
            return "mysql";
        }
        if (normalized.contains("POSTGRESQL") || normalized.equals("POSTGRES")) {
            return "postgresql";
        }
        if (normalized.contains("SQLSERVER") || normalized.contains("MSSQL") || normalized.contains("MICROSOFTSQLSERVER")) {
            return "sqlserver";
        }
        if (normalized.contains("SQLITE")) {
            return "sqlite";
        }
        if (normalized.contains("DB2")) {
            return "db2";
        }
        if (normalized.contains("INFORMIX") || normalized.equals("IFX")) {
            return "informix";
        }
        if (normalized.contains("SYBASE")) {
            return "sybase";
        }
        if (normalized.contains("H2")) {
            return "h2";
        }
        if (normalized.contains("OPENSEARCH")) {
            return "opensearch";
        }
        if (normalized.contains("IMPALA")) {
            return "impala";
        }
        if (normalized.contains("KYLIN")) {
            return "kylin";
        }
        if (normalized.contains("PRESTO")) {
            return "presto";
        }
        return "";
    }

    private static String normalizeFdlDatasourceType(String rawType) {
        String known = DataSyncParser.mapKnownFdlDatasourceType(rawType);
        if (StringUtils.isNotEmpty(known)) {
            return known;
        }
        String normalized = rawType == null ? "" : rawType.trim().toLowerCase().replaceAll("[^a-z0-9]+", "");
        return StringUtils.isNotEmpty(normalized) ? normalized : FDL_DATASOURCE_ADAPTER_REQUIRED;
    }

    private static boolean requiresDatasourceAdapter(String datasourceType) {
        return FDL_DATASOURCE_ADAPTER_REQUIRED.equalsIgnoreCase(datasourceType == null ? "" : datasourceType.trim());
    }

    private static String createDatasourceAdapterRequiredNote(GroupSQLInfo groupSQLInfo, String fromDBName, String fromDBType,
                                                             String toDBName, String toDBType, List<String> extraNotes) {
        StringBuilder note = new StringBuilder();
        note.append("FDL_ADAPTER_REQUIRED datasource adapter contract.");
        note.append("\nXML expressed datasource semantics; runnable FDL DB nodes are not generated until datasourceType/generic JDBC adapter support is implemented.");
        note.append("\nfrom=").append(fromDBName == null ? "" : fromDBName).append("/").append(fromDBType == null ? "" : fromDBType);
        note.append("; to=").append(toDBName == null ? "" : toDBName).append("/").append(toDBType == null ? "" : toDBType);
        if (groupSQLInfo != null && groupSQLInfo.getTargetWriteInfo() != null && StringUtils.isNotEmpty(groupSQLInfo.getTargetWriteInfo().toVisibleLine())) {
            note.append("\nTarget write semantics: ").append(groupSQLInfo.getTargetWriteInfo().toVisibleLine());
        }
        if (groupSQLInfo != null) {
            for (ConnectionMappingInfo info : groupSQLInfo.getConnectionInfos()) {
                if (info.requiresAdapter()) {
                    note.append("\nDatasource adapter: ").append(info.toVisibleLine());
                }
            }
        }
        if (extraNotes != null) {
            for (String extraNote : extraNotes) {
                if (StringUtils.isNotEmpty(extraNote)) {
                    note.append("\n").append(extraNote);
                }
            }
        }
        return note.toString();
    }

    private static String summarizeSql(String sql) {
        if (!StringUtils.isNotEmpty(sql)) {
            return "";
        }
        String singleLine = sql.replaceAll("\\s+", " ").trim();
        if (singleLine.length() > 180) {
            return singleLine.substring(0, 177) + "...";
        }
        return singleLine;
    }

    private static boolean isOdbcConnection(String connectionType, String connectionSubtype) {
        String normalizedType = DataSyncParser.normalizeConnectionKey(connectionType);
        String normalizedSubtype = DataSyncParser.normalizeConnectionKey(connectionSubtype);
        return normalizedType.contains("ODBC")
            || normalizedSubtype.contains("ODBC")
            || normalizedSubtype.contains("JDBCODBC")
            || normalizedType.contains("JDBCODBC");
    }

    private static String normalizeConnectionKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("[^A-Za-z0-9]+", "").toUpperCase();
    }

    private static ApiNodeFromInfoBean extractApiConfig(Document doc, Element sessionEl) {
        String mappingName = sessionEl.getAttribute("MAPPINGNAME");
        if (!StringUtils.isNotEmpty(mappingName)) {
            return null;
        }
        Element mappingEl = findMappingElement(doc, mappingName);
        if (mappingEl == null) {
            return null;
        }
        ApiNodeFromInfoBean firstConfig = null;
        int detectedCount = 0;
        NodeList transNodes = mappingEl.getElementsByTagName(TAG_TRANSFORMATION);
        for (int i = 0; i < transNodes.getLength(); i++) {
            Element transEl = (Element) transNodes.item(i);
            if (!DataSyncParser.isExternalApiTransformation(transEl)) {
                continue;
            }
            ApiNodeFromInfoBean apiConfig = DataSyncParser.createApiConfigFromTransformation(mappingEl, transEl);
            if (apiConfig == null) {
                continue;
            }
            detectedCount++;
            if (firstConfig == null) {
                firstConfig = apiConfig;
            }
        }
        if (firstConfig != null && detectedCount > 1) {
            firstConfig.addLimitation("Multiple external call transformations were detected in the mapping; current plugin creates one visible FDL API node for the first call only.");
        }
        return firstConfig;
    }

    private static boolean isExternalApiTransformation(Element transEl) {
        String type = transEl.getAttribute(TAG_TYPE);
        String templateName = transEl.getAttribute("TEMPLATENAME");
        StringBuilder marker = new StringBuilder();
        marker.append(type).append(' ').append(templateName).append(' ');
        NodeList metaExtensions = transEl.getElementsByTagName("METADATAEXTENSION");
        for (int i = 0; i < metaExtensions.getLength(); i++) {
            Element metaExt = (Element) metaExtensions.item(i);
            marker.append(metaExt.getAttribute(TAG_NAME)).append(' ');
            String value = metaExt.getAttribute(TAG_VALUE);
            if (value != null && value.length() > 0) {
                marker.append(value.length() > 2048 ? value.substring(0, 2048) : value).append(' ');
            }
        }
        NodeList tableAttrs = transEl.getElementsByTagName(TAG_TABLEATTRIBUTE);
        for (int i = 0; i < tableAttrs.getLength(); i++) {
            Element attr = (Element) tableAttrs.item(i);
            marker.append(attr.getAttribute(TAG_NAME)).append(' ').append(attr.getAttribute(TAG_VALUE)).append(' ');
        }
        String lower = marker.toString().toLowerCase();
        return "Custom Transformation".equals(type) && lower.contains("java transformation")
            || lower.contains("external procedure")
            || lower.contains("java class")
            || lower.contains("class name")
            || lower.contains("method name")
            || lower.contains("procedure")
            || lower.contains("web service")
            || lower.contains("soap")
            || lower.contains("soapaction")
            || lower.contains("endpoint")
            || lower.contains("wsdl")
            || lower.contains("httpurlconnection")
            || lower.contains("sendandgetresponsedata")
            || lower.contains("setrequestproperty")
            || lower.contains("://")
            || lower.contains(" rest ")
            || lower.contains(" api ");
    }

    private static ApiNodeFromInfoBean createApiConfigFromTransformation(Element mappingEl, Element transEl) {
        String name = transEl.getAttribute(TAG_NAME);
        String type = transEl.getAttribute(TAG_TYPE);
        String templateName = transEl.getAttribute("TEMPLATENAME");
        Map<String, String> snippets = DataSyncParser.getMetadataSnippets(transEl);
        String onInput = DataSyncParser.removeLineComments(snippets.get("OnInputRow_Method_Snippet"));
        String functions = DataSyncParser.removeLineComments(snippets.get("Infa_Functions_Snippet"));
        String allCode = onInput + "\n" + functions;
        Map<String, String> assignments = DataSyncParser.collectStringAssignments(allCode);
        List<String> callArgs = DataSyncParser.findExternalCallArguments(onInput);
        String url = DataSyncParser.resolveCallArgument(callArgs, 0, assignments);
        if (!StringUtils.isNotEmpty(url)) {
            url = DataSyncParser.findFirstUrl(allCode);
        }
        String bodyTemplate = DataSyncParser.resolveBodyTemplate(callArgs, assignments, functions, allCode);
        String method = DataSyncParser.extractRequestMethod(allCode);
        if (!StringUtils.isNotEmpty(method)) {
            method = StringUtils.isNotEmpty(bodyTemplate) ? "POST" : "GET";
        }
        Map<String, String> headers = DataSyncParser.extractRequestHeaders(allCode, assignments);
        String contentType = DataSyncParser.findHeaderValue(headers, "Content-Type");
        if (!StringUtils.isNotEmpty(contentType)) {
            contentType = DataSyncParser.detectDefaultContentType(bodyTemplate);
            if (StringUtils.isNotEmpty(contentType)) {
                headers.put("Content-Type", contentType);
            }
        }
        String soapActionSource = "";
        String soapAction = null;
        if (callArgs.size() > 2) {
            soapActionSource = callArgs.get(2);
            soapAction = DataSyncParser.resolveArgumentValue(soapActionSource, assignments);
        }
        String headerSoapAction = DataSyncParser.findHeaderValue(headers, "SOAPAction");
        if (soapAction == null && headerSoapAction != null) {
            soapAction = headerSoapAction;
        }
        if (callArgs.size() > 2) {
            DataSyncParser.removeHeader(headers, "SOAPAction");
            headers.put("SOAPAction", soapAction == null ? "" : soapAction);
        }
        if (DataSyncParser.looksLikeSoap(bodyTemplate) && !headers.containsKey("SOAPAction")) {
            headers.put("SOAPAction", soapAction == null ? "" : soapAction);
        }
        String bodyType = DataSyncParser.detectBodyType(bodyTemplate, contentType);
        String actionName = StringUtils.isNotEmpty(soapAction) ? soapAction : DataSyncParser.extractSoapOperation(bodyTemplate);
        String invocationCondition = DataSyncParser.extractInvocationCondition(onInput);
        String protocol = DataSyncParser.detectExternalCallProtocol(url, bodyTemplate, headers, type, templateName);
        String javaClassName = DataSyncParser.findExternalMetadataValue(transEl, snippets, "class");
        String javaMethodName = DataSyncParser.findExternalMetadataValue(transEl, snippets, "method");
        ApiNodeFromInfoBean apiConfig = ApiNodeFromInfoBean.create()
            .sourceTransformation(name, type, templateName)
            .url(DataSyncParser.sanitizeConfigValue(url))
            .method(method)
            .bodyTemplate(DataSyncParser.sanitizeMultilineValue(bodyTemplate))
            .bodyType(bodyType)
            .charset("UTF-8")
            .responseFormat("JSON".equals(bodyType) ? "JSON" : "TEXT")
            .responseBodyName("response")
            .actionName(DataSyncParser.sanitizeConfigValue(actionName))
            .soapAction(DataSyncParser.sanitizeConfigValue(soapAction))
            .soapActionSource(DataSyncParser.sanitizeConfigValue(soapActionSource))
            .invocationCondition(DataSyncParser.sanitizeConfigValue(invocationCondition))
            .protocol(protocol)
            .javaClassName(DataSyncParser.sanitizeConfigValue(javaClassName))
            .javaMethodName(DataSyncParser.sanitizeConfigValue(javaMethodName));
        DataSyncParser.addExternalAdapterMetadata(apiConfig, transEl, snippets);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            apiConfig.addHeader(header.getKey(), DataSyncParser.sanitizeHeaderValue(header.getKey(), header.getValue()));
        }
        DataSyncParser.addApiPortInfo(apiConfig, mappingEl, transEl);
        DataSyncParser.addApiExpressionInfo(apiConfig, mappingEl);
        DataSyncParser.extractResponseStatusOutputBindings(apiConfig, onInput, functions);
        DataSyncParser.reviewApiAdapterBindings(apiConfig, allCode);
        if (!StringUtils.isNotEmpty(url)) {
            apiConfig.addLimitation("Original external call was detected but URL could not be parsed; fill the URL manually before running.");
        }
        if (!StringUtils.isNotEmpty(protocol)) {
            apiConfig.addLimitation("Executable protocol could not be derived from external-call metadata; configure an adapter before running.");
        }
        if (!StringUtils.isNotEmpty(bodyTemplate) && !"GET".equalsIgnoreCase(method)) {
            apiConfig.addLimitation("Request body could not be parsed from the Informatica metadata; verify or fill the API body manually.");
        }
        if (apiConfig.getOutputFields().isEmpty()) {
            apiConfig.addOutputField("response", "STRING");
            apiConfig.addLimitation("No output ports were found; generated a placeholder response field for UI visibility.");
        }
        apiConfig.resolveExecutionStatus();
        LogUtil.info("extractApiConfig: external call transformation [" + name + "], action=" + apiConfig.getActionName()
            + ", status=" + apiConfig.getExecutionStatus() + ", outputs=" + apiConfig.getOutputFields().size());
        return apiConfig;
    }

    private static void reviewApiAdapterBindings(ApiNodeFromInfoBean apiConfig, String allCode) {
        if (apiConfig == null) {
            return;
        }
        String invocationCondition = apiConfig.getInvocationCondition();
        if (StringUtils.isNotEmpty(invocationCondition)) {
            List<String> conditionFields = DataSyncParser.extractInvocationConditionFields(invocationCondition);
            for (String field : conditionFields) {
                apiConfig.addInvocationConditionField(field);
            }
            if (conditionFields.isEmpty()) {
                apiConfig.rowInvocationBinding(invocationCondition, conditionFields, "predicate-before-request", false);
                apiConfig.addLimitation("Original Java row-level invocation condition could not be parsed into input fields; adapter review is required.");
            } else if (DataSyncParser.allApiConditionFieldsBound(apiConfig, conditionFields)) {
                apiConfig.rowInvocationBinding(invocationCondition, conditionFields, "predicate-before-request", true);
                apiConfig.rowLevelInvocationAdapterSupported(true);
                apiConfig.addAdapterMetadata("rowInvocationMode", "predicate-before-request");
                apiConfig.addAdapterMetadata("rowInvocationFields", DataSyncParser.joinList(conditionFields));
            } else {
                apiConfig.rowInvocationBinding(invocationCondition, conditionFields, "predicate-before-request", false);
                apiConfig.addLimitation("Original Java row-level invocation condition references fields not bound to API input ports: "
                    + DataSyncParser.joinList(conditionFields));
            }
        }

        boolean statusCodeUsed = (allCode != null && (allCode.contains("getResponseCode") || allCode.contains("statusCode")));
        if (statusCodeUsed) {
            List<ApiNodeFromInfoBean.HttpStatusOutputBinding> bindings = apiConfig.getHttpStatusOutputBindings();
            if (bindings.isEmpty()) {
                apiConfig.addLimitation("Original Java code maps HTTP response status to output fields; FDL API response/status mapping may need manual completion.");
            } else if (DataSyncParser.allStatusBindingsTargetApiOutputs(apiConfig, bindings)) {
                apiConfig.httpStatusOutputAdapterSupported(true);
                apiConfig.addAdapterMetadata("httpStatusOutputBinding", "parsed-from-java-getResponseCode");
            } else {
                apiConfig.addLimitation("Parsed HTTP status-to-output mapping references fields that are not API output ports.");
            }
        }
    }

    private static List<String> extractInvocationConditionFields(String condition) {
        List<String> result = new ArrayList<String>();
        if (!StringUtils.isNotEmpty(condition)) {
            return result;
        }
        String scrubbed = condition.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", " ");
        scrubbed = scrubbed.replaceAll("'(?:\\\\.|[^'\\\\])*'", " ");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b").matcher(scrubbed);
        while (matcher.find()) {
            String token = matcher.group();
            String lower = token.toLowerCase(Locale.ROOT);
            if ("equals".equals(lower) || "true".equals(lower) || "false".equals(lower)
                || "null".equals(lower) || "if".equals(lower) || "return".equals(lower)
                || "string".equals(lower) || "boolean".equals(lower) || "this".equals(lower)) {
                continue;
            }
            if (!DataSyncParser.containsIgnoreCase(result, token)) {
                result.add(token);
            }
        }
        return result;
    }

    private static boolean allApiConditionFieldsBound(ApiNodeFromInfoBean apiConfig, List<String> fields) {
        if (apiConfig == null || fields == null || fields.isEmpty()) {
            return false;
        }
        Map<String, String> inputSources = apiConfig.getInputFieldSources();
        for (String field : fields) {
            boolean found = false;
            for (String inputField : inputSources.keySet()) {
                if (field.equalsIgnoreCase(inputField)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean allStatusBindingsTargetApiOutputs(ApiNodeFromInfoBean apiConfig,
        List<ApiNodeFromInfoBean.HttpStatusOutputBinding> bindings) {
        if (apiConfig == null || bindings == null || bindings.isEmpty()) {
            return false;
        }
        List<String> outputFields = apiConfig.getOutputFields();
        for (ApiNodeFromInfoBean.HttpStatusOutputBinding binding : bindings) {
            String outputField = binding.getOutputField();
            if (!StringUtils.isNotEmpty(outputField) || !DataSyncParser.containsIgnoreCase(outputFields, outputField)) {
                return false;
            }
        }
        return true;
    }

    private static String detectExternalCallProtocol(String url, String bodyTemplate, Map<String, String> headers, String type, String templateName) {
        String marker = ((url == null ? "" : url) + " " + (bodyTemplate == null ? "" : bodyTemplate) + " "
            + (type == null ? "" : type) + " " + (templateName == null ? "" : templateName) + " " + headers.toString()).toLowerCase();
        if (marker.contains("soap") || marker.contains("wsdl") || marker.contains("envelope")) {
            return "SOAP_HTTP";
        }
        if (url != null && (url.toLowerCase().startsWith("http://") || url.toLowerCase().startsWith("https://"))) {
            return "HTTP";
        }
        if (marker.contains("external procedure")) {
            return "EXTERNAL_PROCEDURE";
        }
        if (marker.contains("java transformation") || marker.contains("java class")) {
            return "JAVA";
        }
        return "";
    }

    private static String findExternalMetadataValue(Element transEl, Map<String, String> snippets, String kind) {
        String value = DataSyncParser.findNamedExternalMetadata(snippets, kind);
        if (StringUtils.isNotEmpty(value)) {
            return value;
        }
        NodeList tableAttrs = transEl.getElementsByTagName(TAG_TABLEATTRIBUTE);
        Map<String, String> attrs = new LinkedHashMap<String, String>();
        for (int i = 0; i < tableAttrs.getLength(); i++) {
            Element attr = (Element) tableAttrs.item(i);
            attrs.put(attr.getAttribute(TAG_NAME), attr.getAttribute(TAG_VALUE));
        }
        return DataSyncParser.findNamedExternalMetadata(attrs, kind);
    }

    private static String findNamedExternalMetadata(Map<String, String> metadata, String kind) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().toLowerCase();
            String value = entry.getValue();
            if (!StringUtils.isNotEmpty(value) || name.contains("snippet") || name.contains("num_of_inner_classes")) {
                continue;
            }
            if ("class".equals(kind) && (name.equals("class") || name.contains("class name") || name.contains("classname") || name.contains("java class"))) {
                return value;
            }
            if ("method".equals(kind) && (name.contains("method") || name.contains("procedure"))) {
                return value;
            }
        }
        return "";
    }

    private static void addExternalAdapterMetadata(ApiNodeFromInfoBean apiConfig, Element transEl, Map<String, String> snippets) {
        DataSyncParser.addExternalAdapterMetadataMap(apiConfig, snippets);
        NodeList tableAttrs = transEl.getElementsByTagName(TAG_TABLEATTRIBUTE);
        Map<String, String> attrs = new LinkedHashMap<String, String>();
        for (int i = 0; i < tableAttrs.getLength(); i++) {
            Element attr = (Element) tableAttrs.item(i);
            attrs.put(attr.getAttribute(TAG_NAME), attr.getAttribute(TAG_VALUE));
        }
        DataSyncParser.addExternalAdapterMetadataMap(apiConfig, attrs);
    }

    private static void addExternalAdapterMetadataMap(ApiNodeFromInfoBean apiConfig, Map<String, String> metadata) {
        if (apiConfig == null || metadata == null) {
            return;
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().trim();
            String lower = name.toLowerCase();
            if (!StringUtils.isNotEmpty(name) || lower.contains("snippet")) {
                continue;
            }
            if (lower.contains("num_of_inner_classes")) {
                continue;
            }
            if (lower.equals("class") || lower.contains("class name") || lower.contains("java class")
                || lower.contains("method") || lower.contains("procedure")
                || lower.contains("endpoint") || lower.contains("url") || lower.contains("soapaction") || lower.equals("action")
                || lower.contains("protocol") || lower.contains("service")) {
                apiConfig.addAdapterMetadata(name, DataSyncParser.sanitizeConfigValue(entry.getValue()));
            }
        }
    }

    private static void addApiExpressionInfo(ApiNodeFromInfoBean apiConfig, Element mappingEl) {
        if (apiConfig == null || mappingEl == null) {
            return;
        }
        FieldLineageInfo lineageInfo = DataSyncParser.extractFieldLineageInfo(mappingEl.getOwnerDocument(), mappingEl.getAttribute(TAG_NAME));
        if (lineageInfo == null || !lineageInfo.hasExpressionInfos()) {
            return;
        }
        for (ExpressionFieldInfo info : lineageInfo.getExpressionInfos()) {
            apiConfig.addExpressionInfo(info);
        }
    }

    private static Map<String, String> getMetadataSnippets(Element transEl) {
        Map<String, String> snippets = new LinkedHashMap<String, String>();
        NodeList metaExtensions = transEl.getElementsByTagName("METADATAEXTENSION");
        for (int i = 0; i < metaExtensions.getLength(); i++) {
            Element metaExt = (Element) metaExtensions.item(i);
            snippets.put(metaExt.getAttribute(TAG_NAME), metaExt.getAttribute(TAG_VALUE));
        }
        return snippets;
    }

    private static String removeLineComments(String code) {
        if (code == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        String[] lines = code.split("\\r?\\n");
        for (String line : lines) {
            if (line.trim().startsWith("//")) {
                continue;
            }
            result.append(line).append('\n');
        }
        return result.toString();
    }

    private static Map<String, String> collectStringAssignments(String code) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?m)^\\s*(?:String\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*;").matcher(code == null ? "" : code);
        while (matcher.find()) {
            result.put(matcher.group(1), DataSyncParser.unescapeJavaString(matcher.group(2)));
        }
        return result;
    }

    private static List<String> findExternalCallArguments(String code) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^;]*?)\\)\\s*;", java.util.regex.Pattern.DOTALL).matcher(code == null ? "" : code);
        while (matcher.find()) {
            String methodName = matcher.group(1);
            String args = matcher.group(2);
            String lower = methodName.toLowerCase();
            if (lower.contains("send") || lower.contains("call") || lower.contains("request") || lower.contains("response")) {
                List<String> split = DataSyncParser.splitJavaArguments(args);
                if (split.size() >= 2) {
                    return split;
                }
            }
        }
        return new ArrayList<String>();
    }

    private static List<String> splitJavaArguments(String args) {
        List<String> result = new ArrayList<String>();
        if (args == null) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (inString) {
                current.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                current.append(c);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')' && depth > 0) {
                depth--;
            }
            if (c == ',' && depth == 0) {
                result.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result;
    }

    private static String resolveCallArgument(List<String> callArgs, int index, Map<String, String> assignments) {
        if (callArgs == null || callArgs.size() <= index) {
            return "";
        }
        String resolved = DataSyncParser.resolveArgumentValue(callArgs.get(index), assignments);
        return resolved == null ? "" : resolved;
    }

    private static String resolveArgumentValue(String raw, Map<String, String> assignments) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return DataSyncParser.unescapeJavaString(value.substring(1, value.length() - 1));
        }
        if (assignments.containsKey(value)) {
            return assignments.get(value);
        }
        return null;
    }

    private static String resolveBodyTemplate(List<String> callArgs, Map<String, String> assignments, String functions, String allCode) {
        String bodyArg = callArgs.size() > 1 ? callArgs.get(1).trim() : "";
        String direct = DataSyncParser.resolveArgumentValue(bodyArg, assignments);
        if (StringUtils.isNotEmpty(direct)) {
            return direct;
        }
        if (bodyArg.endsWith("()")) {
            String methodName = bodyArg.substring(0, bodyArg.length() - 2).trim();
            String fromMethod = DataSyncParser.extractReturnedStringTemplate(functions, methodName);
            if (StringUtils.isNotEmpty(fromMethod)) {
                return fromMethod;
            }
        }
        for (String value : assignments.values()) {
            if (DataSyncParser.looksLikePayload(value)) {
                return value;
            }
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"((?:\\\\.|[^\"\\\\])*(?:<soap|<\\?xml|\\{)(?:\\\\.|[^\"\\\\])*)\"", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(allCode == null ? "" : allCode);
        String last = "";
        while (matcher.find()) {
            last = DataSyncParser.unescapeJavaString(matcher.group(1));
        }
        return last;
    }

    private static String extractReturnedStringTemplate(String code, String methodName) {
        if (!StringUtils.isNotEmpty(methodName)) {
            return "";
        }
        java.util.regex.Matcher methodMatcher = java.util.regex.Pattern.compile(methodName + "\\s*\\(\\s*\\)\\s*\\{(.*?)\\n\\s*\\}", java.util.regex.Pattern.DOTALL).matcher(code == null ? "" : code);
        String methodBody = code;
        if (methodMatcher.find()) {
            methodBody = methodMatcher.group(1);
        }
        Map<String, String> assignments = DataSyncParser.collectStringAssignments(methodBody);
        String returnedVar = "";
        java.util.regex.Matcher returnMatcher = java.util.regex.Pattern.compile("return\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;").matcher(methodBody == null ? "" : methodBody);
        if (returnMatcher.find()) {
            returnedVar = returnMatcher.group(1);
        }
        if (StringUtils.isNotEmpty(returnedVar) && assignments.containsKey(returnedVar)) {
            return assignments.get(returnedVar);
        }
        String lastPayload = "";
        for (String value : assignments.values()) {
            if (DataSyncParser.looksLikePayload(value)) {
                lastPayload = value;
            }
        }
        return lastPayload;
    }

    private static Map<String, String> extractRequestHeaders(String code, Map<String, String> assignments) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("setRequestProperty\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*([^\\)]*?)\\s*\\)").matcher(code == null ? "" : code);
        while (matcher.find()) {
            String name = matcher.group(1);
            String rawValue = matcher.group(2).trim();
            String value = DataSyncParser.resolveArgumentValue(rawValue, assignments);
            if (value == null) {
                value = rawValue;
            }
            if ("Content-type".equalsIgnoreCase(name)) {
                name = "Content-Type";
            }
            headers.put(name, value);
        }
        return headers;
    }

    private static String findHeaderValue(Map<String, String> headers, String key) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void removeHeader(Map<String, String> headers, String key) {
        String foundKey = null;
        for (String headerKey : headers.keySet()) {
            if (headerKey != null && headerKey.equalsIgnoreCase(key)) {
                foundKey = headerKey;
                break;
            }
        }
        if (foundKey != null) {
            headers.remove(foundKey);
        }
    }

    private static String extractRequestMethod(String code) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("setRequestMethod\\s*\\(\\s*\"([A-Za-z]+)\"\\s*\\)").matcher(code == null ? "" : code);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return "";
    }

    private static String findFirstUrl(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("https?://[^\\s\"'<>]+").matcher(text == null ? "" : text);
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    private static boolean looksLikePayload(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.startsWith("<?xml") || trimmed.startsWith("<soap") || trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static boolean looksLikeSoap(String value) {
        return value != null && value.toLowerCase().contains("envelope");
    }

    private static String detectDefaultContentType(String bodyTemplate) {
        if (!StringUtils.isNotEmpty(bodyTemplate)) {
            return "";
        }
        if (DataSyncParser.looksLikeSoap(bodyTemplate)) {
            return "application/soap+xml;charset=utf-8";
        }
        String lower = bodyTemplate.trim().toLowerCase();
        if (lower.startsWith("{") || lower.startsWith("[")) {
            return "application/json;charset=utf-8";
        }
        if (lower.startsWith("<")) {
            return "application/xml;charset=utf-8";
        }
        return "text/plain;charset=utf-8";
    }

    private static String detectBodyType(String bodyTemplate, String contentType) {
        String lower = ((contentType == null ? "" : contentType) + " " + (bodyTemplate == null ? "" : bodyTemplate.trim())).toLowerCase();
        if (lower.contains("json") || lower.startsWith("{") || lower.startsWith("[")) {
            return "JSON";
        }
        return "TEXT";
    }

    private static String extractSoapOperation(String bodyTemplate) {
        if (!StringUtils.isNotEmpty(bodyTemplate)) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<(?:[A-Za-z0-9_]+:)?Body\\b[^>]*>\\s*<(?:(?:[A-Za-z0-9_]+):)?([A-Za-z_][A-Za-z0-9_.-]*)", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(bodyTemplate);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String extractInvocationCondition(String onInputCode) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("if\\s*\\((.*?)\\)\\s*\\{[^{}]*?(?:send|call|request|response)[A-Za-z0-9_]*\\s*\\(", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(onInputCode == null ? "" : onInputCode);
        if (matcher.find()) {
            return matcher.group(1).trim().replaceAll("\\s+", " ");
        }
        return "";
    }

    private static void extractResponseStatusOutputBindings(ApiNodeFromInfoBean apiConfig, String onInputCode, String functionsCode) {
        if (apiConfig == null) {
            return;
        }
        List<String> outputFields = apiConfig.getOutputFields();
        if (outputFields == null || outputFields.isEmpty()) {
            return;
        }
        Map<String, String> outputMethodCalls = DataSyncParser.findOutputMethodAssignments(onInputCode, outputFields);
        for (Map.Entry<String, String> entry : outputMethodCalls.entrySet()) {
            String methodBody = DataSyncParser.findJavaMethodBody(functionsCode, entry.getValue());
            HttpStatusReturnMapping mapping = DataSyncParser.parseHttpStatusReturnMapping(methodBody);
            if (mapping == null || mapping.statusValues.isEmpty()) {
                continue;
            }
            for (HttpStatusValue statusValue : mapping.statusValues) {
                apiConfig.addHttpStatusOutputBinding(
                    DataSyncParser.sanitizeConfigValue(entry.getKey()),
                    DataSyncParser.sanitizeConfigValue(statusValue.statusCode),
                    DataSyncParser.sanitizeConfigValue(statusValue.outputValue),
                    DataSyncParser.sanitizeConfigValue(mapping.defaultValue),
                    DataSyncParser.sanitizeConfigValue(entry.getValue()));
            }
        }
    }

    private static Map<String, String> findOutputMethodAssignments(String code, List<String> outputFields) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        Set<String> outputs = new LinkedHashSet<String>();
        for (String outputField : outputFields) {
            if (StringUtils.isNotEmpty(outputField)) {
                outputs.add(outputField);
            }
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\(").matcher(code == null ? "" : code);
        while (matcher.find()) {
            String outputField = matcher.group(1);
            String methodName = matcher.group(2);
            if (outputs.contains(outputField)) {
                result.put(outputField, methodName);
            }
        }
        return result;
    }

    private static String findJavaMethodBody(String code, String methodName) {
        if (!StringUtils.isNotEmpty(code) || !StringUtils.isNotEmpty(methodName)) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
            "\\b" + java.util.regex.Pattern.quote(methodName) + "\\s*\\([^)]*\\)\\s*\\{").matcher(code);
        if (!matcher.find()) {
            return "";
        }
        int bodyStart = matcher.end();
        int depth = 1;
        boolean inString = false;
        boolean inSingleQuote = false;
        boolean escaped = false;
        for (int i = bodyStart; i < code.length(); i++) {
            char c = code.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inString = !inString;
                continue;
            }
            if (c == '\'' && !inString) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (inString || inSingleQuote) {
                continue;
            }
            if (c == '{') {
                depth++;
                continue;
            }
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return code.substring(bodyStart, i);
                }
            }
        }
        return "";
    }

    private static HttpStatusReturnMapping parseHttpStatusReturnMapping(String methodBody) {
        if (!StringUtils.isNotEmpty(methodBody) || methodBody.indexOf("getResponseCode") < 0) {
            return null;
        }
        java.util.regex.Matcher returnMatcher = java.util.regex.Pattern.compile(
            "\\breturn\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;").matcher(methodBody);
        String returnVariable = "";
        while (returnMatcher.find()) {
            returnVariable = returnMatcher.group(1);
        }
        if (!StringUtils.isNotEmpty(returnVariable)) {
            return null;
        }
        String defaultValue = DataSyncParser.findFirstStringAssignment(methodBody, returnVariable);
        String statusVariable = DataSyncParser.findHttpStatusVariable(methodBody);
        if (!StringUtils.isNotEmpty(statusVariable)) {
            statusVariable = "statusCode";
        }
        List<HttpStatusValue> statusValues = new ArrayList<HttpStatusValue>();
        statusValues.addAll(DataSyncParser.findStatusAssignments(methodBody, statusVariable, returnVariable, false));
        statusValues.addAll(DataSyncParser.findStatusAssignments(methodBody, statusVariable, returnVariable, true));
        if (statusValues.isEmpty()) {
            return null;
        }
        return new HttpStatusReturnMapping(defaultValue, statusValues);
    }

    private static String findFirstStringAssignment(String code, String variableName) {
        if (!StringUtils.isNotEmpty(code) || !StringUtils.isNotEmpty(variableName)) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
            "(?:\\bString\\s+)?\\b" + java.util.regex.Pattern.quote(variableName) + "\\s*=\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*;").matcher(code);
        if (matcher.find()) {
            return DataSyncParser.unescapeJavaString(matcher.group(1));
        }
        return "";
    }

    private static String findHttpStatusVariable(String methodBody) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
            "(?:\\bint\\s+)?\\b([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*[^;]*\\.getResponseCode\\s*\\(").matcher(methodBody == null ? "" : methodBody);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static List<HttpStatusValue> findStatusAssignments(String methodBody, String statusVariable,
        String returnVariable, boolean reversed) {
        List<HttpStatusValue> result = new ArrayList<HttpStatusValue>();
        String condition;
        if (reversed) {
            condition = "(\\d{3})\\s*==\\s*" + java.util.regex.Pattern.quote(statusVariable);
        } else {
            condition = java.util.regex.Pattern.quote(statusVariable) + "\\s*==\\s*(\\d{3})";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
            "\\bif\\s*\\(\\s*" + condition + "\\s*\\)\\s*\\{(.*?)\\}",
            java.util.regex.Pattern.DOTALL).matcher(methodBody == null ? "" : methodBody);
        while (matcher.find()) {
            String statusCode = matcher.group(1);
            String block = matcher.group(2);
            String value = DataSyncParser.findFirstStringAssignment(block, returnVariable);
            if (StringUtils.isNotEmpty(value)) {
                result.add(new HttpStatusValue(statusCode, value));
            }
        }
        return result;
    }

    private static class HttpStatusReturnMapping {
        private final String defaultValue;
        private final List<HttpStatusValue> statusValues;

        HttpStatusReturnMapping(String defaultValue, List<HttpStatusValue> statusValues) {
            this.defaultValue = defaultValue == null ? "" : defaultValue;
            this.statusValues = statusValues == null ? new ArrayList<HttpStatusValue>() : statusValues;
        }
    }

    private static class HttpStatusValue {
        private final String statusCode;
        private final String outputValue;

        HttpStatusValue(String statusCode, String outputValue) {
            this.statusCode = statusCode == null ? "" : statusCode;
            this.outputValue = outputValue == null ? "" : outputValue;
        }
    }

    private static void addApiPortInfo(ApiNodeFromInfoBean apiConfig, Element mappingEl, Element transEl) {
        String transformationName = transEl.getAttribute(TAG_NAME);
        NodeList fields = transEl.getElementsByTagName("TRANSFORMFIELD");
        for (int i = 0; i < fields.getLength(); i++) {
            Element field = (Element) fields.item(i);
            String fieldName = field.getAttribute(TAG_NAME);
            if (!StringUtils.isNotEmpty(fieldName)) {
                continue;
            }
            String portType = field.getAttribute("PORTTYPE");
            String fieldType = DataSyncParser.mapDataType(field.getAttribute("DATATYPE"));
            if (DataSyncParser.isInputPort(portType)) {
                apiConfig.addInputFieldSource(fieldName, DataSyncParser.findIncomingFieldSource(mappingEl, transformationName, fieldName));
            }
            if (DataSyncParser.isOutputPort(portType)) {
                apiConfig.addOutputField(fieldName, fieldType);
                List<String> targets = DataSyncParser.findOutputTargetFields(mappingEl, transformationName, fieldName);
                if (!targets.isEmpty()) {
                    apiConfig.addOutputFieldTarget(fieldName, targets.get(0));
                    if (targets.size() > 1) {
                        apiConfig.addLimitation("Output field " + fieldName + " feeds multiple target fields: " + targets.toString());
                    }
                } else {
                    apiConfig.addLimitation("Output field " + fieldName + " has no target field connector parsed; verify field mapping manually.");
                }
            }
        }
    }

    private static boolean isInputPort(String portType) {
        return portType != null && portType.toUpperCase().contains("INPUT");
    }

    private static boolean isOutputPort(String portType) {
        return portType != null && portType.toUpperCase().contains("OUTPUT");
    }

    private static String findIncomingFieldSource(Element mappingEl, String transformationName, String fieldName) {
        NodeList connectors = mappingEl.getElementsByTagName("CONNECTOR");
        for (int i = 0; i < connectors.getLength(); i++) {
            Element connector = (Element) connectors.item(i);
            if (transformationName.equals(connector.getAttribute("TOINSTANCE")) && fieldName.equals(connector.getAttribute("TOFIELD"))) {
                String fromInstance = connector.getAttribute("FROMINSTANCE");
                String fromField = connector.getAttribute("FROMFIELD");
                String fromType = connector.getAttribute("FROMINSTANCETYPE");
                String result = fromInstance + "." + fromField;
                if (StringUtils.isNotEmpty(fromType)) {
                    result += " [" + fromType + "]";
                }
                return result;
            }
        }
        return "";
    }

    private static List<String> findOutputTargetFields(Element mappingEl, String transformationName, String fieldName) {
        List<String> result = new ArrayList<String>();
        DataSyncParser.collectOutputTargetFields(mappingEl, transformationName, fieldName, new HashSet<String>(), result, 0);
        return result;
    }

    private static void collectOutputTargetFields(Element mappingEl, String instanceName, String fieldName, Set<String> visited, List<String> result, int depth) {
        if (depth > 32 || !StringUtils.isNotEmpty(instanceName) || !StringUtils.isNotEmpty(fieldName)) {
            return;
        }
        String key = instanceName + "\u0001" + fieldName;
        if (visited.contains(key)) {
            return;
        }
        visited.add(key);
        NodeList connectors = mappingEl.getElementsByTagName("CONNECTOR");
        for (int i = 0; i < connectors.getLength(); i++) {
            Element connector = (Element) connectors.item(i);
            if (!instanceName.equals(connector.getAttribute("FROMINSTANCE")) || !fieldName.equals(connector.getAttribute("FROMFIELD"))) {
                continue;
            }
            String toField = connector.getAttribute("TOFIELD");
            String toInstance = connector.getAttribute("TOINSTANCE");
            String toType = connector.getAttribute("TOINSTANCETYPE");
            if ("Target Definition".equals(toType)) {
                if (!result.contains(toField)) {
                    result.add(toField);
                }
                continue;
            }
            DataSyncParser.collectOutputTargetFields(mappingEl, toInstance, toField, new HashSet<String>(visited), result, depth + 1);
        }
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

    private static String sanitizeHeaderValue(String headerName, String value) {
        if (DataSyncParser.isSensitiveText(headerName)) {
            return "[REDACTED]";
        }
        String sanitized = DataSyncParser.sanitizeConfigValue(value);
        if (DataSyncParser.isSensitiveText(sanitized)) {
            return "[REDACTED]";
        }
        return sanitized == null ? "" : sanitized;
    }

    private static String sanitizeConfigValue(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.replaceAll("(?i)(password|passwd|pwd|token|cookie|license|secret)(\\s*[=:]\\s*)[^&\\s\"'<>]+", "$1$2[REDACTED]");
        return sanitized;
    }

    private static String sanitizeMultilineValue(String value) {
        if (value == null) {
            return null;
        }
        String[] lines = value.split("\\r?\\n", -1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = DataSyncParser.sanitizeConfigValue(lines[i]);
            if (DataSyncParser.isSensitiveText(line)) {
                line = "[REDACTED sensitive line]";
            }
            if (i > 0) {
                result.append('\n');
            }
            result.append(line);
        }
        return result.toString();
    }

    private static boolean isSensitiveText(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.contains("password")
            || lower.contains("passwd")
            || lower.contains("pwd")
            || lower.contains("token")
            || lower.contains("cookie")
            || lower.contains("license")
            || lower.contains("secret");
    }

    private static void debugLog(String msg) {
        // Intentionally disabled: avoid writing XML/API/parameter details to local files.
    }

    private static void saveDateParamsAsGlobal(List<ParamEntity> wfParams) throws Exception {
        // Disabled intentionally: Informatica parameter values must come from XML defaults,
        // parameter files, or runtime bindings. Never infer dates from parameter names.
    }

    private static boolean isDateParam(ParamEntity param) {
        return false;
    }

}
