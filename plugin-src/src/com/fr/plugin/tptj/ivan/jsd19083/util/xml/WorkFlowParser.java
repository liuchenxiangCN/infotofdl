package com.fr.plugin.tptj.ivan.jsd19083.util.xml;

import com.fr.plugin.tptj.ivan.jsd19083.bean.TaskEvalBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.InvokeTaskNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.VirtualNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.single.SingleNodeInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.parse.WorkFlowParseNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.util.XmlParserUtil;
import com.fr.stable.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class WorkFlowParser {
    public static final int NODE_GAP = 200;

    public static WorkFlowParseNodeBean parse(Document doc, XPath xpath, TaskEvalBean extraParam) throws Exception {
        NodeList workflowNodes = (NodeList) xpath.evaluate("//WORKFLOW", doc, XPathConstants.NODESET);
        if (workflowNodes.getLength() != 1) {
            throw new UnsupportedOperationException("Multiple WORKFLOW elements are not supported by WorkFlowParser");
        }
        Element workflowElem = (Element) xpath.evaluate("//WORKFLOW", doc, XPathConstants.NODE);
        AtomicInteger currentX = new AtomicInteger(0);
        AtomicInteger currentY = new AtomicInteger(0);
        String toFolder = extraParam.getToFolder();
        String folder = XmlParserUtil.extractExpression(doc, xpath, "/POWERMART/REPOSITORY/FOLDER/@NAME");
        if (StringUtils.isNotEmpty(toFolder)) {
            folder = toFolder + "/" + folder;
        }
        String workName = XmlParserUtil.extractExpression(doc, xpath, "/POWERMART/REPOSITORY/FOLDER/WORKFLOW/@NAME");
        return WorkFlowParseNodeBean.create(folder, workName)
            .addNodes(getAllTaskNode(workflowElem.getElementsByTagName("TASK"), currentX, currentY))
            .addLinks(XmlParserUtil.getDirectWorkflowLinks(workflowElem));
    }

    private static List<SingleNodeInfoBean> getAllTaskNode(NodeList taskNodes, AtomicInteger currentX, AtomicInteger currentY) {
        ArrayList<SingleNodeInfoBean> nodes = new ArrayList<SingleNodeInfoBean>();
        for (int i = 0; i < taskNodes.getLength(); i++) {
            currentX.set(currentX.get() + NODE_GAP);
            Element task = (Element) taskNodes.item(i);
            String name = task.getAttribute("NAME");
            String targetTaskName = extractWaitTaskName(task);
            if (StringUtils.isEmpty(targetTaskName)) {
                nodes.add(new VirtualNodeBean(currentX.get(), currentY.get(), name, UUID.randomUUID().toString()));
            } else {
                nodes.add(new InvokeTaskNodeBean(currentX.get(), currentY.get(), name, UUID.randomUUID().toString(), targetTaskName));
            }
        }
        return nodes;
    }

    private static String extractWaitTaskName(Element task) {
        NodeList valuePairs = task.getElementsByTagName("VALUEPAIR");
        Pattern waitPattern = Pattern.compile("-wait\\s+(\\S+)");
        for (int i = 0; i < valuePairs.getLength(); i++) {
            Element valuePair = (Element) valuePairs.item(i);
            String value = valuePair.getAttribute("VALUE");
            if (!StringUtils.isNotEmpty(value)) {
                continue;
            }
            Matcher matcher = waitPattern.matcher(value);
            if (matcher.find() && StringUtils.isNotEmpty(matcher.group(1))) {
                return matcher.group(1);
            }
        }
        return "";
    }
}
