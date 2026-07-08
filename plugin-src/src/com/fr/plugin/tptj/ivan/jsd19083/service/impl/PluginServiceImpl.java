package com.fr.plugin.tptj.ivan.jsd19083.service.impl;

import com.fr.dp.catalog.domain.entity.node.EntityNode;
import com.fr.dp.catalog.domain.entity.node.PackageNode;
import com.fr.dp.catalog.dto.NodeDTO;
import com.fr.dp.offline.work.dto.OfflineWorkDTO;
import com.fr.plugin.tptj.ivan.jsd19083.bean.ResultBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.TaskEvalBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.BaseParseNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.service.PluginService;
import com.fr.plugin.tptj.ivan.jsd19083.service.SDKService;
import com.fr.plugin.tptj.ivan.jsd19083.util.LogUtil;
import com.fr.stable.StringUtils;
import java.util.List;

public class PluginServiceImpl extends AbstractPluginServiceImpl {
    private PluginServiceImpl() {
    }

    public static PluginService getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    protected ResultBean dealWork(String userId, TaskEvalBean taskEvalBean, BaseParseNodeBean parseNodeBean) throws Exception {
        try {
            PackageNode folderNode = ensureFolder(userId, parseNodeBean.getFolder());
            NodeDTO taskNode = findEntityNode(folderNode, parseNodeBean.getWorkName());
            if (taskNode == null) {
                taskNode = addTaskNode(userId, parseNodeBean.getWorkName(), folderNode);
            } else {
                LogUtil.info("Task already exists, updating: [" + parseNodeBean.getWorkName() + "] folder=" + parseNodeBean.getFolder());
            }
            OfflineWorkDTO workDTO = parseNodeBean.updateFDLWorkDTO(taskNode);
            SDKService.INSTANCE.getOfflineDefinitionService().saveWork(userId, workDTO);
            LogUtil.info("Task saved OK: [" + parseNodeBean.getWorkName() + "] folder=" + parseNodeBean.getFolder());
            return ResultBean.builder().filename(taskEvalBean.getAttachmentInfo().getFileName()).success();
        } catch (Exception e) {
            LogUtil.error("saveWork failed: [" + parseNodeBean.getWorkName() + "] folder=" + parseNodeBean.getFolder() + " - " + e.getMessage(), e);
            return ResultBean.builder()
                .filename(taskEvalBean.getAttachmentInfo().getFileName())
                .error(String.format("任务名称：[%s], 文件夹路径: [%s]. 任务生成失败: [%s]",
                    parseNodeBean.getWorkName(), parseNodeBean.getFolder(), e.getMessage()));
        }
    }

    private PackageNode ensureFolder(String userId, String folderPath) {
        if (StringUtils.isEmpty(folderPath)) {
            return getFolderByPath("").orElse(null);
        }
        PackageNode existing = getFolderByPath(folderPath).orElse(null);
        if (existing != null) {
            return existing;
        }
        PackageNode parent = null;
        String currentPath = "";
        String[] parts = folderPath.split("/");
        for (String part : parts) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            currentPath = StringUtils.isEmpty(currentPath) ? part : currentPath + "/" + part;
            PackageNode current = getFolderByPath(currentPath).orElse(null);
            if (current == null) {
                current = createPackageNode(userId, part, currentPath, parent);
            }
            parent = current;
        }
        if (parent == null) {
            throw new RuntimeException("Folder path is empty: " + folderPath);
        }
        return parent;
    }

    private NodeDTO findEntityNode(PackageNode folderNode, String taskName) {
        if (folderNode == null || StringUtils.isEmpty(taskName)) {
            return null;
        }
        List<EntityNode> entityNodes = folderNode.getEntityNodes();
        if (entityNodes == null) {
            return null;
        }
        for (EntityNode entityNode : entityNodes) {
            if (entityNode != null && taskName.equals(entityNode.getName())) {
                return new NodeDTO(entityNode);
            }
        }
        return null;
    }

    private static class Holder {
        private static final PluginServiceImpl INSTANCE = new PluginServiceImpl();
    }
}
