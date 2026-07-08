/*
 * Decompiled with CFR 0.152.
 */
package com.fr.plugin.tptj.ivan.jsd19083.service;

import com.fr.dp.catalog.dto.NodeFindDTO;
import com.fr.dp.devwork.work.component.ComponentLineType;
import com.fr.dp.devwork.work.component.api.Component;
import com.fr.dp.devwork.work.component.dto.ComponentDTO;
import com.fr.dp.devwork.work.line.dto.LineDTO;
import com.fr.dp.work.manage.dto.BetweenFilterDTO;
import com.fr.dp.work.manage.dto.WorkManageResultDTO;
import com.fr.dp.work.manage.offline.dto.OfflineWorkConfigDTO;
import com.fr.dp.work.manage.offline.dto.OfflineWorkSearchDTO;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.Link;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.ConditionNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.util.FineUtils;
import com.fr.stable.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public enum FDLService {
    INSTANCE;


    public String getTaskId(String taskName) throws Exception {
        if (StringUtils.isEmpty((String)taskName)) {
            throw new RuntimeException(String.format("任务名: [%s]. 任务名不能为空.", taskName));
        }
        OfflineWorkSearchDTO taskSearchDTO = new OfflineWorkSearchDTO();
        taskSearchDTO.setKeyword(taskName);
        taskSearchDTO.setSearchColumn("taskName");
        taskSearchDTO.setCreateTime(new BetweenFilterDTO());
        HashMap<String, List<String>> configFilters = new HashMap<String, List<String>>();
        configFilters.put("emptySchedule", Collections.singletonList("NOT_EXIST"));
        configFilters.put("timeSchedule", Collections.singletonList("NOT_EXIST"));
        configFilters.put("eventSchedule", Collections.singletonList("NOT_EXIST"));
        configFilters.put("timeout", Collections.emptyList());
        configFilters.put("retry", Collections.emptyList());
        configFilters.put("notice", Collections.emptyList());
        configFilters.put("creator", Collections.emptyList());
        configFilters.put("taskPriority", Collections.emptyList());
        taskSearchDTO.setConfigFilters(configFilters);
        taskSearchDTO.setSortColumn("");
        taskSearchDTO.setDesc(true);
        taskSearchDTO.setNumPerPage(Integer.MAX_VALUE);
        taskSearchDTO.setPage(1);
        NodeFindDTO nodeFindDTO = new NodeFindDTO();
        nodeFindDTO.setId("_DP_ROOT_CATALOG_");
        nodeFindDTO.setParentId("");
        nodeFindDTO.setParentPath(Collections.emptyList());
        taskSearchDTO.setNodeFindDTO(nodeFindDTO);
        WorkManageResultDTO<OfflineWorkConfigDTO> result = SDKService.INSTANCE.getWorkManageService().search(FineUtils.getAdminUserId(), taskSearchDTO);
        List<OfflineWorkConfigDTO> records = result.getRecords();
        if (records != null) {
            for (OfflineWorkConfigDTO task : records) {
                if (!StringUtils.equals((String)taskName, (String)task.getWorkName())) continue;
                return task.getWorkId();
            }
        }
        throw new RuntimeException(String.format("任务名: [%s]. 未找到任务.", taskName));
    }

    public LineDTO generateLineDTO(String from, String to, String lineType, String lineCondition) {
        return this.generateLineDTO(from, to, lineType, lineCondition, "");
    }

    public LineDTO generateLineDTO(String from, String to, String lineType, String lineCondition, String infaCondition) {
        LineDTO lineDTO = new LineDTO();
        lineDTO.setLineType(lineType);
        HashMap<String, String> value = new HashMap<String, String>();
        value.put("from", from);
        value.put("to", to);
        value.put("lineCondition", lineCondition);
        value.put("lineType", lineType);
        if (StringUtils.isNotEmpty((String)infaCondition)) {
            value.put("infaCondition", infaCondition);
            value.put("conditionString", infaCondition);
        }
        lineDTO.setValue(value);
        return lineDTO;
    }

    public LineDTO generateLineDTO(String from, String to) {
        return this.generateLineDTO(from, to, ComponentLineType.getInstance().type(), "ON_SUCCESS");
    }

    public LineDTO generateLineDTO(String from, String to, Link link) {
        String infaCondition = link == null ? "" : link.getCondition();
        String lineCondition = link == null
            ? ConditionNodeBean.FDL_LINE_ON_SUCCESS
            : ConditionNodeBean.resolveFdlLineCondition(infaCondition, link.getFrom());
        return this.generateLineDTO(from, to, ComponentLineType.getInstance().type(), lineCondition, infaCondition);
    }

    public ComponentDTO createComponentDTO(double x, double y, String type, Component value) {
        ComponentDTO result = new ComponentDTO();
        result.setX(x);
        result.setY(y);
        result.setType(type);
        result.setCompareId(UUID.randomUUID().toString());
        result.setDefaultParams(null);
        result.setValue(value.toEntity());
        return result;
    }

    public List<LineDTO> getLines(Set<String> fromIds, Set<String> toIds) {
        ArrayList<LineDTO> lines = new ArrayList<LineDTO>();
        for (String fromId : fromIds) {
            for (String toId : toIds) {
                LineDTO lineDTO = INSTANCE.generateLineDTO(fromId, toId);
                lines.add(lineDTO);
            }
        }
        return lines;
    }

    public List<LineDTO> getLines(List<Link> links, Map<String, Set<String>> fromIds, Map<String, Set<String>> toIds) {
        ArrayList<LineDTO> result = new ArrayList<LineDTO>();
        for (Link link : links) {
            String linkFrom = link.getFrom();
            String linkTo = link.getTo();
            Set<String> fromNodeIds = fromIds.get(linkFrom);
            Set<String> toNodeIds = toIds.get(linkTo);
            if (fromNodeIds == null || fromNodeIds.isEmpty() || toNodeIds == null || toNodeIds.isEmpty()) continue;
            for (String fromNodeId : fromNodeIds) {
                for (String toNodeId : toNodeIds) {
                    result.add(INSTANCE.generateLineDTO(fromNodeId, toNodeId, link));
                }
            }
        }
        return result;
    }
}
