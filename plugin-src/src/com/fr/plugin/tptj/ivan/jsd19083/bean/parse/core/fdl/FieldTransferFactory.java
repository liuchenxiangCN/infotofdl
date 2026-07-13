package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl;

import com.fr.dp.datasource.dto.DBFieldTransferBean;
import com.fr.dp.datasource.dto.FieldTransferBean;
import com.fr.dp.datasource.general.base.SqlFieldBean;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.FieldLineageInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.InfaFieldMeta;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.MappingFieldBinding;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.TargetWriteInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Creates typed FDL mappings from XML connector bindings. */
public final class FieldTransferFactory {
    private FieldTransferFactory() {
    }

    public static List<FieldTransferBean> createForSync(DBFieldTransferBean runtime,
                                                        FieldLineageInfo lineage,
                                                        TargetWriteInfo targetWriteInfo) {
        List<FieldTransferBean> result = createFromBindings(runtime, lineage, targetWriteInfo, null, false);
        if (!result.isEmpty()) {
            return result;
        }
        return createRuntimeOnlyMappings(runtime);
    }

    public static List<FieldTransferBean> createForTransform(FieldLineageInfo lineage,
                                                             TargetWriteInfo targetWriteInfo,
                                                             Collection<String> outputFields) {
        return createFromBindings(null, lineage, targetWriteInfo, outputFields, true);
    }

    public static SqlFieldBean createLogicalField(String name, String infaType) {
        InfaFieldMeta meta = new InfaFieldMeta();
        meta.setFieldName(name);
        meta.setFieldKind(InfaFieldMeta.KIND_TRANSFORM);
        meta.setDataType(infaType);
        return meta.toSqlFieldBean(name);
    }

    public static SqlFieldBean createTargetField(FieldLineageInfo lineage,
                                                 TargetWriteInfo targetWriteInfo,
                                                 String targetField,
                                                 SqlFieldBean runtimeFallback) {
        MappingFieldBinding binding = lineage == null ? null : lineage.findBinding(targetWriteInfo, targetField);
        if (binding != null && binding.getWriteMeta() != null) {
            return binding.getWriteMeta().toSqlFieldBean(targetField);
        }
        return runtimeFallback == null ? unsupportedField(targetField, "Target XML metadata is unavailable") : copy(runtimeFallback, targetField);
    }

    private static List<FieldTransferBean> createFromBindings(DBFieldTransferBean runtime,
                                                               FieldLineageInfo lineage,
                                                               TargetWriteInfo targetWriteInfo,
                                                               Collection<String> outputFields,
                                                               boolean readUsesTargetName) {
        List<FieldTransferBean> result = new ArrayList<FieldTransferBean>();
        if (lineage == null) {
            return result;
        }
        Set<String> wanted = normalizedSet(outputFields);
        Set<String> emittedReadNames = new HashSet<String>();
        for (MappingFieldBinding binding : lineage.getFieldBindings(targetWriteInfo)) {
            if (MappingFieldBinding.UNRESOLVED.equals(binding.getStatus())
                || MappingFieldBinding.UNSUPPORTED.equals(binding.getStatus())
                || binding.getReadField().length() == 0) {
                continue;
            }
            if (!wanted.isEmpty() && !wanted.contains(normalize(binding.getTargetField()))) {
                continue;
            }
            if (!lineage.isReaderFieldAvailable(binding.getReadField())) {
                continue;
            }
            String readName = readUsesTargetName ? binding.getTargetField() : binding.getReadField();
            if (!emittedReadNames.add(normalize(readName))) {
                continue;
            }
            SqlFieldBean runtimeRead = runtime == null ? null : findByName(runtime.getOrigin(), binding.getReadField());
            SqlFieldBean runtimeWrite = runtime == null ? null : findByName(runtime.getTarget(), binding.getTargetField());
            SqlFieldBean readColumn = typedXmlField(binding.getReadMeta(), readName, runtimeRead);
            SqlFieldBean writeColumn = typedXmlField(binding.getWriteMeta(), binding.getTargetField(), runtimeWrite);
            if (readColumn == null || writeColumn == null || readColumn.getType() == 0 || writeColumn.getType() == 0) {
                continue;
            }
            result.add(new FieldTransferBean(readColumn, writeColumn, false));
        }
        return result;
    }

    private static List<FieldTransferBean> createRuntimeOnlyMappings(DBFieldTransferBean runtime) {
        List<FieldTransferBean> result = new ArrayList<FieldTransferBean>();
        if (runtime == null || runtime.getOrigin() == null || runtime.getTarget() == null) {
            return result;
        }
        Set<Integer> usedOriginIndexes = new HashSet<Integer>();
        for (SqlFieldBean target : runtime.getTarget()) {
            int index = findUnusedByName(runtime.getOrigin(), target.getName(), usedOriginIndexes);
            if (index < 0) {
                continue;
            }
            usedOriginIndexes.add(index);
            result.add(new FieldTransferBean(copy(runtime.getOrigin().get(index), runtime.getOrigin().get(index).getName()),
                copy(target, target.getName()), false));
        }
        return result;
    }

    private static int findUnusedByName(List<SqlFieldBean> fields, String name, Set<Integer> used) {
        for (int i = 0; i < fields.size(); i++) {
            if (!used.contains(i) && normalize(fields.get(i).getName()).equals(normalize(name))) {
                return i;
            }
        }
        return -1;
    }

    private static SqlFieldBean findByName(List<SqlFieldBean> fields, String name) {
        if (fields == null) {
            return null;
        }
        for (SqlFieldBean field : fields) {
            if (normalize(field.getName()).equals(normalize(name))) {
                return field;
            }
        }
        return null;
    }

    private static SqlFieldBean typedXmlField(InfaFieldMeta xmlMeta, String name, SqlFieldBean runtimeFallback) {
        if (xmlMeta != null && xmlMeta.isTypeProven()) {
            return xmlMeta.toSqlFieldBean(name);
        }
        if (xmlMeta != null && xmlMeta.getDataType() != null && xmlMeta.getDataType().trim().length() > 0) {
            return null;
        }
        if (runtimeFallback == null || runtimeFallback.getType() == 0) {
            return null;
        }
        return copy(runtimeFallback, name);
    }

    private static SqlFieldBean copy(SqlFieldBean source, String name) {
        SqlFieldBean field = new SqlFieldBean();
        field.setName(name);
        field.setLabel(source.getLabel());
        field.setType(source.getType());
        field.setTypeName(source.getTypeName());
        field.setDialectTypeName(source.getDialectTypeName());
        field.setSize(source.getSize());
        field.setScale(source.getScale());
        field.setNullable(source.isNullable());
        field.setPk(source.isPk());
        field.setLogicalPk(source.isLogicalPk());
        field.setEditable(source.isEditable());
        field.setComment(source.getComment());
        field.setInvalidWarning(source.getInvalidWarning());
        return field;
    }

    private static SqlFieldBean unsupportedField(String name, String warning) {
        SqlFieldBean field = new SqlFieldBean();
        field.setName(name);
        field.setLabel(name);
        field.setType(java.sql.Types.OTHER);
        field.setTypeName("UNKNOWN");
        field.setInvalidWarning(warning);
        return field;
    }

    private static Set<String> normalizedSet(Collection<String> values) {
        Set<String> result = new HashSet<String>();
        if (values != null) {
            for (String value : values) {
                result.add(normalize(value));
            }
        }
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace("\"", "").replace("`", "").replaceAll("\\s+", "").toUpperCase();
    }
}
