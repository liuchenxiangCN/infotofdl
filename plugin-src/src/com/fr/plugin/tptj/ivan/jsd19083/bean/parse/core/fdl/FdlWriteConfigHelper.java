package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl;

import com.fr.dp.datasource.dto.FieldTransferBean;
import com.fr.dp.integration.core.writeconf.AppendWriteConf;
import com.fr.dp.integration.core.writeconf.TruncateWriteConf;
import com.fr.dp.integration.core.writeconf.WriteConf;
import com.fr.dp.node.domain.entities.DBWriteNode;
import com.fr.dp.node.domain.valueobject.DataFlowWriteType;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.TargetWriteInfo;
import com.fr.stable.StringUtils;
import java.util.ArrayList;
import java.util.List;

public class FdlWriteConfigHelper {
    private FdlWriteConfigHelper() {
    }

    public static void applyWriteConfig(DBWriteNode writeNode, List<FieldTransferBean> fieldTransferItems, TargetWriteInfo targetWriteInfo) {
        List<List<String>> logicalPrimaryKey = createLogicalPrimaryKey(fieldTransferItems, targetWriteInfo);
        if (targetWriteInfo != null && targetWriteInfo.isTruncateEnabled()) {
            TruncateWriteConf writeConf = new TruncateWriteConf();
            writeConf.setCoverType(TruncateWriteConf.TRUNCATE);
            writeConf.setLogicalPrimaryKey(logicalPrimaryKey);
            writeConf.setUpdateStrategy(resolveUpdateStrategy(targetWriteInfo, logicalPrimaryKey).getType());
            writeNode.setWriteType(DataFlowWriteType.DELETE_AND_WRITE.getType());
            writeNode.setWriteConfig((WriteConf)writeConf);
            return;
        }
        AppendWriteConf writeConf = new AppendWriteConf();
        writeConf.setLogicalPrimaryKey(logicalPrimaryKey);
        writeConf.setUpdateStrategy(resolveUpdateStrategy(targetWriteInfo, logicalPrimaryKey).getType());
        writeNode.setWriteType(DataFlowWriteType.APPEND.getType());
        writeNode.setWriteConfig((WriteConf)writeConf);
    }

    public static List<List<String>> createLogicalPrimaryKey(List<FieldTransferBean> fieldTransferItems, TargetWriteInfo targetWriteInfo) {
        ArrayList<List<String>> result = new ArrayList<List<String>>();
        if (fieldTransferItems == null) {
            return result;
        }
        for (FieldTransferBean fieldTransferItem : fieldTransferItems) {
            if (fieldTransferItem == null || fieldTransferItem.getWriteColumn() == null) {
                continue;
            }
            String writeColumnName = fieldTransferItem.getWriteColumn().getName();
            if (!fieldTransferItem.getWriteColumn().isPk() && (targetWriteInfo == null || !targetWriteInfo.isKeyField(writeColumnName))) {
                continue;
            }
            ArrayList<String> pk = new ArrayList<String>();
            pk.add(fieldTransferItem.getReadColumn() == null ? writeColumnName : fieldTransferItem.getReadColumn().getName());
            pk.add(writeColumnName);
            result.add(pk);
        }
        return result;
    }

    public static boolean shouldUseDeleteAdapter(TargetWriteInfo targetWriteInfo) {
        return targetWriteInfo != null && targetWriteInfo.isDeleteMode();
    }

    public static String targetWriteNote(TargetWriteInfo targetWriteInfo) {
        if (targetWriteInfo == null || !StringUtils.isNotEmpty(targetWriteInfo.toVisibleLine())) {
            return "";
        }
        StringBuilder note = new StringBuilder();
        note.append("Informatica target write semantics: ").append(targetWriteInfo.toVisibleLine());
        if (targetWriteInfo.isTruncateEnabled()) {
            note.append("\nFDL writer mode: truncate target then write rows.");
        } else if (targetWriteInfo.isDeleteMode()) {
            note.append("\nFDL writer mode: row-level delete requires generated DELETE SQL or adapter; DBWrite upsert is not equivalent.");
        } else if (targetWriteInfo.isUpdateMode()) {
            note.append("\nFDL writer mode: append writer with update-same-key strategy when key fields are available.");
        } else {
            note.append("\nFDL writer mode: append writer without key update strategy.");
        }
        return note.toString();
    }

    private static AppendWriteConf.UpdateStrategy resolveUpdateStrategy(TargetWriteInfo targetWriteInfo, List<List<String>> logicalPrimaryKey) {
        if (logicalPrimaryKey == null || logicalPrimaryKey.isEmpty()) {
            return AppendWriteConf.UpdateStrategy.NONE_KEY;
        }
        if (targetWriteInfo == null) {
            return AppendWriteConf.UpdateStrategy.UPDATE_SAME_KEY;
        }
        if (targetWriteInfo.isUpdateMode()) {
            return AppendWriteConf.UpdateStrategy.UPDATE_SAME_KEY;
        }
        return AppendWriteConf.UpdateStrategy.NONE_KEY;
    }
}
