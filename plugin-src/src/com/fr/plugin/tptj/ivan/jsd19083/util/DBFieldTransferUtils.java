package com.fr.plugin.tptj.ivan.jsd19083.util;

import com.fr.dp.component.domain.entities.DataSyncComponent;
import com.fr.dp.context.SpringContext;
import com.fr.dp.datasource.dto.DBFieldTransferBean;
import com.fr.dp.devwork.preview.PreviewEntityRequest;
import com.fr.dp.devwork.preview.executor.PreviewType;
import com.fr.dp.devwork.preview.service.PreviewService;
import com.fr.dp.devwork.work.component.dto.ComponentDTO;
import com.fr.dp.devwork.work.param.DevParamEntity;
import com.fr.dp.offline.execution.preview.OfflinePreviewService;
import com.fr.stable.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DBFieldTransferUtils {
    private static final PreviewService previewService = (PreviewService) SpringContext.getBean(OfflinePreviewService.class);

    public static DBFieldTransferBean refreshWithoutError(DataSyncComponent component) {
        try {
            return refresh(component);
        } catch (Exception e) {
            System.err.println("refresh error." + e.getMessage());
            e.printStackTrace();
            DBFieldTransferBean result = new DBFieldTransferBean();
            result.setOrigin(new ArrayList());
            result.setTarget(new ArrayList());
            result.setTargetAll(new ArrayList());
            return result;
        }
    }

    public static DBFieldTransferBean refresh(DataSyncComponent component) throws Exception {
        PreviewEntityRequest request = new PreviewEntityRequest();
        ComponentDTO node = new ComponentDTO();
        node.setType("DATA_SYNC");
        node.setValue(component.toEntity());
        request.setNode(node);

        DevParamEntity paramEntity = new DevParamEntity();
        paramEntity.setWorkParams(Collections.emptyList());
        paramEntity.setNodeParams(new ArrayList());
        request.setParamEntity(paramEntity);

        Map previewOptions = new HashMap();
        previewOptions.put("chosenNodeId", component.getId());
        previewOptions.put("previewType", "");
        request.setPreviewOptions(previewOptions);
        return refresh(request);
    }

    public static DBFieldTransferBean refresh(PreviewEntityRequest request) throws Exception {
        addPreviewMode(request, PreviewType.FIELD_TRANSFER);
        DBFieldTransferBean result = (DBFieldTransferBean) previewService.preview(request);
        if (result.getOrigin() != null && result.getTarget() != null) {
            return result;
        }
        throw new RuntimeException("Field transfer refresh failed");
    }

    private static void addPreviewMode(PreviewEntityRequest request, PreviewType previewType) {
        if (request.getPreviewOptions() == null) {
            Map previewOptions = new HashMap();
            previewOptions.put("previewType", previewType);
            request.setPreviewOptions(previewOptions);
            return;
        }
        String existing = (String) request.getPreviewOptions().get("previewType");
        if (StringUtils.isEmpty(existing)) {
            request.getPreviewOptions().put("previewType", previewType);
        }
    }
}
