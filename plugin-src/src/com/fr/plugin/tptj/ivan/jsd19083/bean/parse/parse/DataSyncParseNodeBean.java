/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.fr.dp.devwork.work.component.dto.ComponentDTO
 *  com.fr.dp.devwork.work.line.dto.LineDTO
 */
package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.parse;

import com.fr.dp.devwork.work.component.dto.ComponentDTO;
import com.fr.dp.devwork.work.line.dto.LineDTO;
import com.fr.dp.devwork.work.param.ParamEntity;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.AbstractParseNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.Link;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.ConditionNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.group.GroupNodeInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.single.SingleNodeInfoBean;
import com.fr.plugin.tptj.ivan.jsd19083.service.FDLService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DataSyncParseNodeBean
extends AbstractParseNodeBean {
    private List<GroupNodeInfoBean> groups = new ArrayList<GroupNodeInfoBean>();
    private List<Link> links = new ArrayList<Link>();
    private List<ParamEntity> params = new ArrayList<ParamEntity>();

    private DataSyncParseNodeBean(String folder, String workName) {
        super(folder, workName);
    }

    public static DataSyncParseNodeBean create(String folder, String workName) {
        return new DataSyncParseNodeBean(folder, workName);
    }

    public DataSyncParseNodeBean addGroup(GroupNodeInfoBean group) {
        this.groups.add(group);
        return this;
    }

    public DataSyncParseNodeBean addGroups(List<GroupNodeInfoBean> group) {
        this.groups.addAll(group);
        return this;
    }

    public DataSyncParseNodeBean addLinks(List<Link> link) {
        this.links.addAll(link);
        return this;
    }

    public DataSyncParseNodeBean addParams(List<ParamEntity> paramList) {
        this.params.addAll(paramList);
        return this;
    }

    @Override
    protected List<ParamEntity> getFDLParams() {
        return this.params;
    }

    public List<GroupNodeInfoBean> getGroups() {
        return this.groups;
    }

    public List<Link> getLinks() {
        return this.links;
    }

    @Override
    protected List<ComponentDTO> getFDLNodes() {
        ArrayList<ComponentDTO> nodes = new ArrayList<ComponentDTO>();
        for (GroupNodeInfoBean group : this.groups) {
            nodes.addAll(group.getFDLComponents());
        }
        return nodes;
    }

    @Override
    protected List<LineDTO> getFDLLines() {
        HashMap<String, Set<String>> fromIds = new HashMap<String, Set<String>>();
        HashMap<String, Set<String>> toIds = new HashMap<String, Set<String>>();
        HashMap<String, ConditionNodeBean> conditionNodes = new HashMap<String, ConditionNodeBean>();
        ArrayList<LineDTO> lines = new ArrayList<LineDTO>();
        for (GroupNodeInfoBean group : this.groups) {
            if (group == null || group.getNodes() == null || group.getNodes().isEmpty()) continue;
            lines.addAll(group.getFDLLinks());
            DataSyncParseNodeBean.addEndpointAlias(fromIds, toIds, group.getGroupName(), group);
            String inferredTaskName = DataSyncParseNodeBean.inferTaskName(group);
            if (inferredTaskName != null && inferredTaskName.length() > 0) {
                DataSyncParseNodeBean.addEndpointAlias(fromIds, toIds, inferredTaskName, group);
            }
            ConditionNodeBean conditionNode = DataSyncParseNodeBean.getConditionNode(group);
            if (conditionNode != null) {
                conditionNode.clearBranches();
                conditionNodes.put(group.getGroupName(), conditionNode);
                if (inferredTaskName != null && inferredTaskName.length() > 0) {
                    conditionNodes.put(inferredTaskName, conditionNode);
                }
            }
        }
        for (Link link : this.links) {
            if (conditionNodes.containsKey(link.getFrom())) {
                ConditionNodeBean conditionNode = conditionNodes.get(link.getFrom());
                Set<String> toNodeIds = toIds.get(link.getTo());
                if (toNodeIds != null) {
                    for (String toNodeId : toNodeIds) {
                        conditionNode.addBranch(toNodeId, link.getTo(), link.getCondition());
                    }
                }
            }
        }
        lines.addAll(FDLService.INSTANCE.getLines(this.links, fromIds, toIds));
        return lines;
    }

    private static void addEndpointAlias(HashMap<String, Set<String>> fromIds, HashMap<String, Set<String>> toIds, String alias, GroupNodeInfoBean group) {
        if (alias == null || alias.length() == 0) {
            return;
        }
        if (!fromIds.containsKey(alias)) {
            fromIds.put(alias, new HashSet());
        }
        ((Set)fromIds.get(alias)).add(group.getFromId());
        if (!toIds.containsKey(alias)) {
            toIds.put(alias, new HashSet());
        }
        ((Set)toIds.get(alias)).add(group.getToId());
    }

    private static ConditionNodeBean getConditionNode(GroupNodeInfoBean group) {
        if (group.getNodes().size() != 1) {
            return null;
        }
        SingleNodeInfoBean node = group.getNodes().get(0);
        if (node instanceof ConditionNodeBean) {
            return (ConditionNodeBean)node;
        }
        return null;
    }

    private static String inferTaskName(GroupNodeInfoBean group) {
        for (SingleNodeInfoBean node : group.getNodes()) {
            String name = node.getName();
            if (name == null || name.length() == 0) {
                continue;
            }
            if (name.matches(".+_pre_\\d+")) {
                return name.replaceFirst("_pre_\\d+$", "");
            }
            if (name.matches(".+_post_\\d+")) {
                return name.replaceFirst("_post_\\d+$", "");
            }
            if (name.endsWith("_truncate")) {
                return name.substring(0, name.length() - "_truncate".length());
            }
            if (name.endsWith("_query")) {
                return name.substring(0, name.length() - "_query".length());
            }
        }
        return "";
    }
}
