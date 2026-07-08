package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FieldLineageInfo {
    private Map<String, List<String>> targetToReadCandidates = new LinkedHashMap<String, List<String>>();
    private Map<String, List<ExpressionFieldInfo>> targetToExpressionInfos = new LinkedHashMap<String, List<ExpressionFieldInfo>>();
    private Map<String, List<FilterFieldInfo>> targetToFilterInfos = new LinkedHashMap<String, List<FilterFieldInfo>>();
    private Map<String, List<LookupFieldInfo>> targetToLookupInfos = new LinkedHashMap<String, List<LookupFieldInfo>>();
    private List<ExpressionFieldInfo> expressionInfos = new ArrayList<ExpressionFieldInfo>();
    private List<FilterFieldInfo> filterInfos = new ArrayList<FilterFieldInfo>();
    private List<LookupFieldInfo> lookupInfos = new ArrayList<LookupFieldInfo>();
    private List<ConnectionMappingInfo> connectionInfos = new ArrayList<ConnectionMappingInfo>();
    private Set<String> knownTargetFields = new LinkedHashSet<String>();
    private Map<String, String> knownTargetFieldNames = new LinkedHashMap<String, String>();

    public void markTargetField(String targetField) {
        String targetKey = normalize(targetField);
        if (targetKey.length() > 0) {
            knownTargetFields.add(targetKey);
            if (!knownTargetFieldNames.containsKey(targetKey)) {
                knownTargetFieldNames.put(targetKey, safe(targetField));
            }
        }
    }

    public void addReadCandidate(String targetField, String readField) {
        String targetKey = normalize(targetField);
        String readName = safe(readField);
        if (targetKey.length() == 0 || readName.length() == 0) {
            return;
        }
        knownTargetFields.add(targetKey);
        if (!knownTargetFieldNames.containsKey(targetKey)) {
            knownTargetFieldNames.put(targetKey, safe(targetField));
        }
        List<String> candidates = targetToReadCandidates.get(targetKey);
        if (candidates == null) {
            candidates = new ArrayList<String>();
            targetToReadCandidates.put(targetKey, candidates);
        }
        for (String candidate : candidates) {
            if (normalize(candidate).equals(normalize(readName))) {
                return;
            }
        }
        candidates.add(readName);
    }

    public List<String> getReadCandidates(String targetField) {
        List<String> candidates = targetToReadCandidates.get(normalize(targetField));
        if (candidates == null) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(candidates);
    }

    public void addExpressionInfo(ExpressionFieldInfo expressionInfo) {
        if (expressionInfo == null || !expressionInfo.isComputed()) {
            return;
        }
        expressionInfos.add(expressionInfo);
        String targetKey = normalize(expressionInfo.getTargetField());
        if (targetKey.length() > 0) {
            knownTargetFields.add(targetKey);
            if (!knownTargetFieldNames.containsKey(targetKey)) {
                knownTargetFieldNames.put(targetKey, safe(expressionInfo.getTargetField()));
            }
            List<ExpressionFieldInfo> infos = targetToExpressionInfos.get(targetKey);
            if (infos == null) {
                infos = new ArrayList<ExpressionFieldInfo>();
                targetToExpressionInfos.put(targetKey, infos);
            }
            infos.add(expressionInfo);
        }
    }

    public void addFilterInfo(FilterFieldInfo filterInfo) {
        if (filterInfo == null) {
            return;
        }
        FilterFieldInfo existing = findFilterInfo(filterInfo.dedupeKey());
        if (existing == null) {
            filterInfos.add(filterInfo);
            existing = filterInfo;
        } else {
            for (String outputField : filterInfo.getOutputFields()) {
                existing.addOutputField(outputField);
            }
            for (String targetField : filterInfo.getTargetFields()) {
                existing.addTargetField(targetField);
            }
        }
        for (String targetField : filterInfo.getTargetFields()) {
            addFilterInfoForTarget(targetField, existing);
        }
    }

    public void addLookupInfo(LookupFieldInfo lookupInfo) {
        if (lookupInfo == null) {
            return;
        }
        LookupFieldInfo existing = findLookupInfo(lookupInfo.dedupeKey());
        if (existing == null) {
            lookupInfos.add(lookupInfo);
            existing = lookupInfo;
        } else {
            for (String inputField : lookupInfo.getInputFields()) {
                existing.addInputField(inputField);
            }
            for (String outputField : lookupInfo.getOutputFields()) {
                existing.addOutputField(outputField);
            }
            for (String inputMapping : lookupInfo.getInputMappings()) {
                int split = inputMapping.indexOf(" <= ");
                if (split > 0) {
                    existing.addInputMapping(inputMapping.substring(0, split), inputMapping.substring(split + 4), "");
                }
            }
            for (Map.Entry<String, String> entry : lookupInfo.getInputReadAliases().entrySet()) {
                existing.addInputMapping(entry.getKey(), "", entry.getValue());
            }
            for (Map.Entry<String, List<String>> entry : lookupInfo.getOutputToTargetFields().entrySet()) {
                for (String targetField : entry.getValue()) {
                    existing.addOutputTargetField(entry.getKey(), targetField);
                }
            }
        }
        for (Map.Entry<String, List<String>> entry : lookupInfo.getOutputToTargetFields().entrySet()) {
            for (String targetField : entry.getValue()) {
                addLookupInfoForTarget(targetField, existing);
            }
        }
    }

    public List<ExpressionFieldInfo> getExpressionInfos() {
        return new ArrayList<ExpressionFieldInfo>(expressionInfos);
    }

    public List<ExpressionFieldInfo> getExpressionInfos(String targetField) {
        List<ExpressionFieldInfo> infos = targetToExpressionInfos.get(normalize(targetField));
        if (infos == null) {
            return new ArrayList<ExpressionFieldInfo>();
        }
        return new ArrayList<ExpressionFieldInfo>(infos);
    }

    public boolean hasExpressionInfos() {
        return !expressionInfos.isEmpty();
    }

    public List<FilterFieldInfo> getFilterInfos() {
        return new ArrayList<FilterFieldInfo>(filterInfos);
    }

    public List<FilterFieldInfo> getFilterInfos(String targetField) {
        List<FilterFieldInfo> infos = targetToFilterInfos.get(normalize(targetField));
        if (infos == null) {
            return new ArrayList<FilterFieldInfo>();
        }
        return new ArrayList<FilterFieldInfo>(infos);
    }

    public boolean hasFilterInfos() {
        return !filterInfos.isEmpty();
    }

    public List<LookupFieldInfo> getLookupInfos() {
        return new ArrayList<LookupFieldInfo>(lookupInfos);
    }

    public List<LookupFieldInfo> getLookupInfos(String targetField) {
        List<LookupFieldInfo> infos = targetToLookupInfos.get(normalize(targetField));
        if (infos == null) {
            return new ArrayList<LookupFieldInfo>();
        }
        return new ArrayList<LookupFieldInfo>(infos);
    }

    public boolean hasLookupInfos() {
        return !lookupInfos.isEmpty();
    }

    public void addConnectionInfo(ConnectionMappingInfo connectionInfo) {
        if (connectionInfo == null) {
            return;
        }
        for (ConnectionMappingInfo existing : connectionInfos) {
            if (existing.dedupeKey().equals(connectionInfo.dedupeKey())) {
                return;
            }
        }
        connectionInfos.add(connectionInfo);
    }

    public List<ConnectionMappingInfo> getConnectionInfos() {
        return new ArrayList<ConnectionMappingInfo>(connectionInfos);
    }

    public boolean hasConnectionInfos() {
        return !connectionInfos.isEmpty();
    }

    public boolean hasConnectionRisks() {
        for (ConnectionMappingInfo info : connectionInfos) {
            if (info.hasRisk()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasVisibleInfos() {
        return hasExpressionInfos() || hasFilterInfos() || hasLookupInfos() || hasConnectionInfos();
    }

    public String getVisibleNote() {
        if (!hasVisibleInfos()) {
            return "";
        }
        StringBuilder note = new StringBuilder();
        note.append("Informatica transformation semantics parsed. Verify limitations before running.");
        int index = 0;
        for (ExpressionFieldInfo info : expressionInfos) {
            note.append("\nExpression ").append(++index).append(": ").append(info.toVisibleLine());
        }
        index = 0;
        for (FilterFieldInfo info : filterInfos) {
            note.append("\nFilter ").append(++index).append(": ").append(info.toVisibleLine());
        }
        index = 0;
        for (LookupFieldInfo info : lookupInfos) {
            note.append("\nLookup ").append(++index).append(": ").append(info.toVisibleLine());
        }
        index = 0;
        for (ConnectionMappingInfo info : connectionInfos) {
            note.append("\nConnection ").append(++index).append(": ").append(info.toVisibleLine());
        }
        return note.toString();
    }

    public boolean isEmpty() {
        return knownTargetFields.isEmpty() && targetToReadCandidates.isEmpty() && !hasVisibleInfos();
    }

    public int size() {
        return knownTargetFields.size();
    }

    public boolean hasTargetField(String targetField) {
        return knownTargetFields.contains(normalize(targetField));
    }

    public List<String> getTargetFields() {
        return new ArrayList<String>(knownTargetFieldNames.values());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        return safe(value).replace("\"", "").replace("`", "").replace("[", "").replace("]", "").replaceAll("\\s+", "").toUpperCase();
    }

    private FilterFieldInfo findFilterInfo(String key) {
        for (FilterFieldInfo info : filterInfos) {
            if (info.dedupeKey().equals(key)) {
                return info;
            }
        }
        return null;
    }

    private LookupFieldInfo findLookupInfo(String key) {
        for (LookupFieldInfo info : lookupInfos) {
            if (info.dedupeKey().equals(key)) {
                return info;
            }
        }
        return null;
    }

    private void addFilterInfoForTarget(String targetField, FilterFieldInfo filterInfo) {
        String targetKey = normalize(targetField);
        if (targetKey.length() == 0) {
            return;
        }
        knownTargetFields.add(targetKey);
        if (!knownTargetFieldNames.containsKey(targetKey)) {
            knownTargetFieldNames.put(targetKey, safe(targetField));
        }
        List<FilterFieldInfo> infos = targetToFilterInfos.get(targetKey);
        if (infos == null) {
            infos = new ArrayList<FilterFieldInfo>();
            targetToFilterInfos.put(targetKey, infos);
        }
        if (!infos.contains(filterInfo)) {
            infos.add(filterInfo);
        }
    }

    private void addLookupInfoForTarget(String targetField, LookupFieldInfo lookupInfo) {
        String targetKey = normalize(targetField);
        if (targetKey.length() == 0) {
            return;
        }
        knownTargetFields.add(targetKey);
        if (!knownTargetFieldNames.containsKey(targetKey)) {
            knownTargetFieldNames.put(targetKey, safe(targetField));
        }
        List<LookupFieldInfo> infos = targetToLookupInfos.get(targetKey);
        if (infos == null) {
            infos = new ArrayList<LookupFieldInfo>();
            targetToLookupInfos.put(targetKey, infos);
        }
        if (!infos.contains(lookupInfo)) {
            infos.add(lookupInfo);
        }
    }
}
