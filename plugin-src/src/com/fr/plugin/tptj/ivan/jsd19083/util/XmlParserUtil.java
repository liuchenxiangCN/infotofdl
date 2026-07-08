/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.fr.stable.StringUtils
 */
package com.fr.plugin.tptj.ivan.jsd19083.util;

import com.fr.plugin.tptj.ivan.jsd19083.bean.TaskEvalBean;
import com.fr.plugin.tptj.ivan.jsd19083.util.LogUtil;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.BaseParseNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.Link;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.parse.DataSyncParseNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.util.xml.DataSyncParser;
import com.fr.plugin.tptj.ivan.jsd19083.util.xml.WorkFlowParser;
import com.fr.stable.StringUtils;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class XmlParserUtil {
    public static final String DTD_FEATURE_NAME = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    public static final String WORKFLOW_EXPRESSION = "//WORKFLOW";
    public static final String WORKFLOWLINK = "WORKFLOWLINK";
    public static final String FOLDER_NAME_EXPRESSION = "/POWERMART/REPOSITORY/FOLDER/@NAME";
    public static final String TASK_NAME_EXPRESSION = "/POWERMART/REPOSITORY/FOLDER/WORKFLOW/@NAME";

    public static BaseParseNodeBean parse(InputStream in, TaskEvalBean extraParam) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        factory.setValidating(false);
        factory.setFeature(DTD_FEATURE_NAME, false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(in);
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xpath = xPathFactory.newXPath();
        DataSyncParseNodeBean dataSyncParseNodeBean = DataSyncParser.parse(doc, xpath, extraParam);
        if (dataSyncParseNodeBean != null && dataSyncParseNodeBean.getGroups() != null && !dataSyncParseNodeBean.getGroups().isEmpty()) {
            return dataSyncParseNodeBean;
        }
        return WorkFlowParser.parse(doc, xpath, extraParam);
    }

    public static List<BaseParseNodeBean> parseAll(InputStream in, TaskEvalBean extraParam) throws Exception {
        LogUtil.info("XML parsing started");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        factory.setValidating(false);
        factory.setFeature(DTD_FEATURE_NAME, false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(in);
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xpath = xPathFactory.newXPath();
        LogUtil.info("XML document parsed, extracting workflows...");
        List<DataSyncParseNodeBean> tasks = DataSyncParser.parseAllWorkflows(doc, xpath, extraParam);
        if (tasks != null && !tasks.isEmpty()) {
            LogUtil.info("Parsing complete, " + tasks.size() + " tasks generated");
            return new ArrayList<BaseParseNodeBean>(tasks);
        }
        LogUtil.warn("No DataSync tasks found, falling back to WorkFlowParser");
        ArrayList<BaseParseNodeBean> fallback = new ArrayList<BaseParseNodeBean>();
        fallback.add(WorkFlowParser.parse(doc, xpath, extraParam));
        return fallback;
    }

    public static List<Link> getAllLinks(NodeList linkNodes) {
        ArrayList<Link> links = new ArrayList<Link>();
        for (int i = 0; i < linkNodes.getLength(); ++i) {
            Element linkElem = (Element)linkNodes.item(i);
            String from = linkElem.getAttribute("FROMTASK");
            String to = linkElem.getAttribute("TOTASK");
            String condition = linkElem.getAttribute("CONDITION");
            if (XmlParserUtil.isVirtualStartEnd(from) || XmlParserUtil.isVirtualStartEnd(to)) {
                continue;
            }
            links.add(new Link(from, to, condition));
        }
        return links;
    }

    public static List<Link> getDirectWorkflowLinks(Element workflowElem) {
        ArrayList<Link> links = new ArrayList<Link>();
        NodeList children = workflowElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElem = (Element) child;
            if (!WORKFLOWLINK.equals(childElem.getTagName())) {
                continue;
            }
            String from = childElem.getAttribute("FROMTASK");
            String to = childElem.getAttribute("TOTASK");
            String condition = childElem.getAttribute("CONDITION");
            if (XmlParserUtil.isVirtualStartEnd(from) || XmlParserUtil.isVirtualStartEnd(to)) {
                continue;
            }
            links.add(new Link(from, to, condition));
        }
        return links;
    }

    private static boolean isVirtualStartEnd(String name) {
        return "开始".equals(name) || "结束".equals(name)
            || "启动".equals(name)
            || "start".equalsIgnoreCase(name) || "end".equalsIgnoreCase(name);
    }

    private static List<String> getAttributeValues(Document doc, XPath xpath, String expression) throws Exception {
        ArrayList<String> results = new ArrayList<String>();
        NodeList nodes = (NodeList)xpath.evaluate(expression, doc, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); ++i) {
            String nodeValue = nodes.item(i).getNodeValue();
            if (StringUtils.isEmpty((String)nodeValue)) continue;
            results.add(nodeValue);
        }
        return results;
    }

    public static String extractExpression(Document doc, XPath xpath, String expression) throws Exception {
        List<String> attributeValues = XmlParserUtil.getAttributeValues(doc, xpath, expression);
        for (String attributeValue : attributeValues) {
            if (!StringUtils.isNotEmpty((String)attributeValue)) continue;
            return attributeValue;
        }
        return "";
    }
}
