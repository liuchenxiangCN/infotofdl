package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl;

import com.fr.dp.component.domain.entities.InvokeTaskComponent;
import com.fr.dp.devwork.work.component.api.Component;
import com.fr.dp.devwork.work.component.dto.ComponentDTO;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.single.DefaultSingleXMLNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.service.FDLService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InvokeTaskNodeBean extends DefaultSingleXMLNodeBean {
    private String targetTaskName;
    private String targetTaskID;
    private boolean spreadParam;
    private List<Map<String, String>> workParams = new ArrayList<Map<String, String>>();
    private List<Map<String, String>> nodeParams = new ArrayList<Map<String, String>>();

    public InvokeTaskNodeBean(int x, int y, String name, String id, String targetTaskName, String targetTaskID) {
        super(x, y, name, id);
        this.targetTaskName = targetTaskName;
        this.targetTaskID = targetTaskID;
    }

    public InvokeTaskNodeBean(int x, int y, String name, String id, String targetTaskName) {
        super(x, y, name, id);
        this.targetTaskName = targetTaskName;
    }

    public String getTargetTaskName() {
        return targetTaskName;
    }

    public String getTargetTaskID() {
        try {
            if (targetTaskID == null || targetTaskID.trim().length() == 0) {
                targetTaskID = FDLService.INSTANCE.getTaskId(targetTaskName);
            }
            return targetTaskID;
        } catch (Exception e) {
            System.err.println("getTargetTaskID error. name:" + targetTaskName);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public ComponentDTO getFDLComponentDTO() {
        return FDLService.INSTANCE.createComponentDTO(this.getX(), this.getY(), this.getFDLNodeType(), this.getFDLComponent());
    }

    public void setSpreadParam(boolean spreadParam) {
        this.spreadParam = spreadParam;
    }

    public void setWorkParams(List<Map<String, String>> workParams) {
        this.workParams = workParams == null ? new ArrayList<Map<String, String>>() : workParams;
    }

    public void setNodeParams(List<Map<String, String>> nodeParams) {
        this.nodeParams = nodeParams == null ? new ArrayList<Map<String, String>>() : nodeParams;
    }

    @Override
    public Component getFDLComponent() {
        InvokeTaskComponent result = new InvokeTaskComponent();
        result.setId(this.getId());
        result.setName(this.getName());
        result.setNote("");
        result.setExecuteLogic("AND");
        result.setDisabled(false);
        result.setTargetWorkId(this.getTargetTaskID());
        result.setSpreadParam(this.spreadParam);
        result.setNodeParams(this.nodeParams);
        result.setWorkParams(this.workParams);
        return result;
    }

    @Override
    public String getFDLNodeType() {
        return "CALL_TASK";
    }
}
