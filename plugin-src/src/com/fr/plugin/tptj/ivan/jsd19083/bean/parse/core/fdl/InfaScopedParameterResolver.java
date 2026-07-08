package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class InfaScopedParameterResolver {
    public static final String KIND_MAPPING_VARIABLE = "MAPPING_VARIABLE";
    public static final String KIND_MAPPING_PARAMETER = "MAPPING_PARAMETER";
    public static final String KIND_WORKFLOW_VARIABLE = "WORKFLOW_VARIABLE";
    public static final String KIND_BRACED_PARAMETER = "BRACED_PARAMETER";
    public static final String KIND_PM_BUILTIN = "PM_BUILTIN";
    public static final String KIND_SESSION_RUNTIME_ALIAS = "SESSION_RUNTIME_ALIAS";
    public static final String KIND_TASK_STATUS = "TASK_STATUS";
    public static final String KIND_DECISION_OUTPUT = "DECISION_OUTPUT";
    public static final String KIND_UNKNOWN_RUNTIME = "UNKNOWN_RUNTIME_REFERENCE";

    public static final String SCOPE_MAPPING = "MAPPING";
    public static final String SCOPE_WORKFLOW = "WORKFLOW";
    public static final String SCOPE_WORKLET = "WORKLET";
    public static final String SCOPE_SESSION = "SESSION";
    public static final String SCOPE_RUNTIME = "RUNTIME";
    public static final String SCOPE_PARAMETER_FILE = "PARAMETER_FILE";

    public static final String DEFAULT_XML_VALUE = "XML_DEFAULT_VALUE";
    public static final String DEFAULT_XML_EMPTY = "XML_DEFAULT_EMPTY";
    public static final String DEFAULT_XML_MISSING = "XML_MISSING_PARAMETER_DEFAULT";
    public static final String DEFAULT_RUNTIME_PLACEHOLDER = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING";
    public static final String DEFAULT_PARAMETER_FILE_REFERENCE = "PARAMETER_FILE_REFERENCE";

    public static final String STATUS_RESOLVED_IN_XML_SCOPE = "RESOLVED_IN_XML_SCOPE";
    public static final String STATUS_RESOLVED_IN_XML_UNIQUE_NAME = "RESOLVED_IN_XML_UNIQUE_NAME";
    public static final String STATUS_AMBIGUOUS_MAPPING_SCOPE = "AMBIGUOUS_MAPPING_SCOPE";
    public static final String STATUS_AMBIGUOUS_WORKFLOW_SCOPE = "AMBIGUOUS_WORKFLOW_SCOPE";
    public static final String STATUS_UNDECLARED_XML_REFERENCE = "UNDECLARED_XML_REFERENCE";
    public static final String STATUS_RUNTIME_PLACEHOLDER = "XML_SEMANTICS_PARSED_TRANSLATION_PENDING";
    public static final String STATUS_PARAMETER_FILE_REFERENCE = "PARAMETER_FILE_REFERENCE";

    private static final Pattern REFERENCE_PATTERN = Pattern.compile(
        "\\$\\$[A-Za-z_][A-Za-z0-9_]*"
            + "|\\$\\{[^}\\s\\\"'<>]+\\}"
            + "|\\$PM[A-Za-z_][A-Za-z0-9_]*"
            + "|\\$[^\\s\\\"'<>;,()=!&|\\\\/]+(?:\\.[^\\s\\\"'<>;,()=!&|\\\\/]+)*"
    );

    private final List<Declaration> declarations = new ArrayList<Declaration>();
    private final Map<String, List<Declaration>> mappingIndex = new LinkedHashMap<String, List<Declaration>>();
    private final Map<String, List<Declaration>> scopedIndex = new LinkedHashMap<String, List<Declaration>>();
    private final List<ParameterFileReference> parameterFiles = new ArrayList<ParameterFileReference>();

    public static InfaScopedParameterResolver fromDocument(Document document) {
        InfaScopedParameterResolver resolver = new InfaScopedParameterResolver();
        if (document == null) {
            return resolver;
        }
        resolver.collectMappingVariables(document);
        resolver.collectWorkflowVariables(document);
        resolver.collectParameterFiles(document);
        return resolver;
    }

    public static Pattern referencePattern() {
        return REFERENCE_PATTERN;
    }

    public static String runtimePlaceholder(String kind, String reference) {
        String cleanKind = isNotEmpty(kind) ? kind.trim() : KIND_UNKNOWN_RUNTIME;
        String cleanReference = isNotEmpty(reference) ? reference.trim() : "UNKNOWN";
        return "${INFA_RUNTIME:" + cleanKind + ":" + cleanReference + "}";
    }

    public static ReferenceToken classifyReference(String reference) {
        String raw = reference == null ? "" : reference.trim();
        if (raw.length() == 0) {
            return ReferenceToken.empty(raw);
        }
        if (raw.startsWith("$$")) {
            return new ReferenceToken(raw, KIND_MAPPING_VARIABLE, raw.substring(2), "", "", true, false);
        }
        if (raw.startsWith("${") && raw.endsWith("}")) {
            return new ReferenceToken(raw, KIND_BRACED_PARAMETER, raw.substring(2, raw.length() - 1), "", "", true, false);
        }
        if (raw.startsWith("$PM")) {
            return new ReferenceToken(raw, KIND_PM_BUILTIN, raw.substring(1), "", "", true, false);
        }
        if ("$Source".equals(raw) || "$Target".equals(raw)) {
            return new ReferenceToken(raw, KIND_SESSION_RUNTIME_ALIAS, raw.substring(1), "", "", true, false);
        }
        if (raw.startsWith("$") && raw.indexOf('.') > 1) {
            String withoutDollar = raw.substring(1);
            String owner = withoutDollar.substring(0, withoutDollar.lastIndexOf('.'));
            String property = withoutDollar.substring(withoutDollar.lastIndexOf('.') + 1);
            if ("Status".equalsIgnoreCase(property)) {
                return new ReferenceToken(raw, KIND_TASK_STATUS, withoutDollar, owner, property, true, true);
            }
            if ("Condition".equalsIgnoreCase(property)) {
                return new ReferenceToken(raw, KIND_DECISION_OUTPUT, withoutDollar, owner, property, true, true);
            }
        }
        if (raw.startsWith("$")) {
            return new ReferenceToken(raw, KIND_WORKFLOW_VARIABLE, raw.substring(1), "", "", true, false);
        }
        return ReferenceToken.empty(raw);
    }

    public void addDeclaration(Declaration declaration) {
        if (declaration == null || !isNotEmpty(declaration.getName())) {
            return;
        }
        declarations.add(declaration);
        if (KIND_MAPPING_VARIABLE.equals(declaration.getKind()) || KIND_MAPPING_PARAMETER.equals(declaration.getKind())) {
            for (String name : declaration.indexNames()) {
                addToIndex(mappingIndex, key(declaration.getScopeType(), declaration.getScopeName(), name), declaration);
            }
            return;
        }
        if (KIND_WORKFLOW_VARIABLE.equals(declaration.getKind())) {
            for (String name : declaration.indexNames()) {
                addToIndex(scopedIndex, key(declaration.getScopeType(), declaration.getScopeName(), name), declaration);
            }
        }
    }

    public void addParameterFileReference(ParameterFileReference reference) {
        if (reference != null) {
            parameterFiles.add(reference);
        }
    }

    public List<Declaration> getDeclarations() {
        return new ArrayList<Declaration>(declarations);
    }

    public List<ParameterFileReference> getParameterFiles() {
        return new ArrayList<ParameterFileReference>(parameterFiles);
    }

    public Resolution resolve(String reference, Scope scope) {
        ReferenceToken token = classifyReference(reference);
        if (!token.isRecognized()) {
            return Resolution.unresolved(reference, KIND_UNKNOWN_RUNTIME, STATUS_UNDECLARED_XML_REFERENCE);
        }
        if (token.isRuntimeOnly()) {
            return Resolution.runtime(token);
        }
        if (KIND_BRACED_PARAMETER.equals(token.getKind())) {
            return Resolution.parameterFile(token);
        }
        Scope effectiveScope = scope == null ? Scope.empty() : scope;
        if (KIND_MAPPING_VARIABLE.equals(token.getKind())) {
            return resolveMappingReference(token, effectiveScope);
        }
        if (KIND_WORKFLOW_VARIABLE.equals(token.getKind())) {
            return resolveWorkflowReference(token, effectiveScope);
        }
        return Resolution.runtime(token);
    }

    public Replacement replaceReferences(String text, Scope scope) {
        String source = text == null ? "" : text;
        Matcher matcher = REFERENCE_PATTERN.matcher(source);
        StringBuffer buffer = new StringBuffer();
        List<Resolution> resolutions = new ArrayList<Resolution>();
        while (matcher.find()) {
            Resolution resolution = resolve(matcher.group(), scope);
            resolutions.add(resolution);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(resolution.getReplacementText()));
        }
        matcher.appendTail(buffer);
        return new Replacement(source, buffer.toString(), resolutions);
    }

    private Resolution resolveMappingReference(ReferenceToken token, Scope scope) {
        List<Declaration> candidates = lookup(mappingIndex, SCOPE_MAPPING, scope.getMapping(), token.getRaw());
        if (candidates.isEmpty()) {
            candidates = lookup(mappingIndex, SCOPE_MAPPING, scope.getMapping(), token.getName());
        }
        if (candidates.size() == 1) {
            return Resolution.resolved(token, candidates.get(0), STATUS_RESOLVED_IN_XML_SCOPE);
        }
        List<Declaration> allCandidates = findByName(token);
        if (allCandidates.size() == 1) {
            return Resolution.resolved(token, allCandidates.get(0), STATUS_RESOLVED_IN_XML_UNIQUE_NAME);
        }
        if (allCandidates.size() > 1) {
            return Resolution.ambiguous(token, STATUS_AMBIGUOUS_MAPPING_SCOPE, scopes(allCandidates));
        }
        return Resolution.unresolved(token.getRaw(), token.getKind(), STATUS_UNDECLARED_XML_REFERENCE);
    }

    private Resolution resolveWorkflowReference(ReferenceToken token, Scope scope) {
        List<Declaration> candidates = new ArrayList<Declaration>();
        if (isNotEmpty(scope.getWorklet())) {
            candidates.addAll(lookup(scopedIndex, SCOPE_WORKLET, scope.getWorklet(), token.getRaw()));
            candidates.addAll(lookup(scopedIndex, SCOPE_WORKLET, scope.getWorklet(), token.getName()));
        }
        if (candidates.isEmpty() && isNotEmpty(scope.getWorkflow())) {
            candidates.addAll(lookup(scopedIndex, SCOPE_WORKFLOW, scope.getWorkflow(), token.getRaw()));
            candidates.addAll(lookup(scopedIndex, SCOPE_WORKFLOW, scope.getWorkflow(), token.getName()));
        }
        if (candidates.size() == 1) {
            return Resolution.resolved(token, candidates.get(0), STATUS_RESOLVED_IN_XML_SCOPE);
        }
        List<Declaration> allCandidates = findByName(token);
        if (allCandidates.size() == 1) {
            return Resolution.resolved(token, allCandidates.get(0), STATUS_RESOLVED_IN_XML_UNIQUE_NAME);
        }
        if (allCandidates.size() > 1 || candidates.size() > 1) {
            List<Declaration> ambiguous = allCandidates.size() > 1 ? allCandidates : candidates;
            return Resolution.ambiguous(token, STATUS_AMBIGUOUS_WORKFLOW_SCOPE, scopes(ambiguous));
        }
        return Resolution.unresolved(token.getRaw(), token.getKind(), STATUS_UNDECLARED_XML_REFERENCE);
    }

    private List<Declaration> findByName(ReferenceToken token) {
        List<Declaration> result = new ArrayList<Declaration>();
        for (Declaration declaration : declarations) {
            if (declaration.matchesName(token.getRaw()) || declaration.matchesName(token.getName())) {
                result.add(declaration);
            }
        }
        return result;
    }

    private void collectMappingVariables(Document document) {
        NodeList mappingNodes = document.getElementsByTagName("MAPPING");
        for (int i = 0; i < mappingNodes.getLength(); i++) {
            Element mapping = (Element) mappingNodes.item(i);
            String mappingName = attr(mapping, "NAME");
            NodeList variables = mapping.getElementsByTagName("MAPPINGVARIABLE");
            for (int j = 0; j < variables.getLength(); j++) {
                Element variable = (Element) variables.item(j);
                String isParameter = attr(variable, "ISPARAM");
                String kind = "YES".equalsIgnoreCase(isParameter) ? KIND_MAPPING_PARAMETER : KIND_MAPPING_VARIABLE;
                addDeclaration(Declaration.fromElement(kind, SCOPE_MAPPING, mappingName, variable,
                    Scope.withMapping(mappingName)));
            }
        }
    }

    private void collectWorkflowVariables(Document document) {
        NodeList variables = document.getElementsByTagName("WORKFLOWVARIABLE");
        for (int i = 0; i < variables.getLength(); i++) {
            Element variable = (Element) variables.item(i);
            Element worklet = nearestAncestor(variable, "WORKLET");
            Element workflow = nearestAncestor(variable, "WORKFLOW");
            String scopeType = worklet != null ? SCOPE_WORKLET : SCOPE_WORKFLOW;
            String scopeName = worklet != null ? attr(worklet, "NAME") : workflow != null ? attr(workflow, "NAME") : "";
            Scope scope = new Scope(workflow != null ? attr(workflow, "NAME") : "",
                worklet != null ? attr(worklet, "NAME") : "", "", "");
            addDeclaration(Declaration.fromElement(KIND_WORKFLOW_VARIABLE, scopeType, scopeName, variable, scope));
        }
    }

    private void collectParameterFiles(Document document) {
        collectParameterFiles(document.getElementsByTagName("ATTRIBUTE"));
        collectParameterFiles(document.getElementsByTagName("TABLEATTRIBUTE"));
    }

    private void collectParameterFiles(NodeList nodes) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (!"parameter filename".equalsIgnoreCase(attr(element, "NAME"))) {
                continue;
            }
            Scope scope = Scope.fromElement(element);
            addParameterFileReference(new ParameterFileReference(scope, attr(element, "VALUE"),
                isNotEmpty(attr(element, "VALUE")) ? DEFAULT_PARAMETER_FILE_REFERENCE : DEFAULT_XML_EMPTY));
        }
    }

    private static Element nearestAncestor(Element element, String tagName) {
        Node current = element == null ? null : element.getParentNode();
        while (current != null) {
            if (current instanceof Element && tagName.equals(((Element) current).getTagName())) {
                return (Element) current;
            }
            current = current.getParentNode();
        }
        return null;
    }

    private static void addToIndex(Map<String, List<Declaration>> index, String key, Declaration declaration) {
        List<Declaration> values = index.get(key);
        if (values == null) {
            values = new ArrayList<Declaration>();
            index.put(key, values);
        }
        values.add(declaration);
    }

    private static List<Declaration> lookup(Map<String, List<Declaration>> index, String scopeType, String scopeName, String name) {
        if (!isNotEmpty(scopeName) || !isNotEmpty(name)) {
            return Collections.emptyList();
        }
        List<Declaration> values = index.get(key(scopeType, scopeName, name));
        return values == null ? Collections.<Declaration>emptyList() : values;
    }

    private static String key(String scopeType, String scopeName, String name) {
        return clean(scopeType).toUpperCase() + "|" + clean(scopeName) + "|" + clean(name);
    }

    private static String scopes(List<Declaration> declarations) {
        Set<String> result = new LinkedHashSet<String>();
        for (Declaration declaration : declarations) {
            result.add(declaration.getScope());
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (String scope : result) {
            if (index > 0) {
                builder.append("; ");
            }
            builder.append(scope);
            index++;
        }
        return builder.toString();
    }

    private static String attr(Element element, String name) {
        return element == null ? "" : clean(element.getAttribute(name));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isNotEmpty(String value) {
        return value != null && value.trim().length() > 0;
    }

    public static class Scope {
        private final String workflow;
        private final String worklet;
        private final String session;
        private final String mapping;

        public Scope(String workflow, String worklet, String session, String mapping) {
            this.workflow = clean(workflow);
            this.worklet = clean(worklet);
            this.session = clean(session);
            this.mapping = clean(mapping);
        }

        public static Scope empty() {
            return new Scope("", "", "", "");
        }

        public static Scope withMapping(String mapping) {
            return new Scope("", "", "", mapping);
        }

        public static Scope fromElement(Element element) {
            Element workflow = nearestAncestor(element, "WORKFLOW");
            Element worklet = nearestAncestor(element, "WORKLET");
            Element session = nearestAncestor(element, "SESSION");
            Element mapping = nearestAncestor(element, "MAPPING");
            return new Scope(attr(workflow, "NAME"), attr(worklet, "NAME"), attr(session, "NAME"),
                isNotEmpty(attr(mapping, "NAME")) ? attr(mapping, "NAME") : attr(session, "MAPPINGNAME"));
        }

        public String getWorkflow() {
            return workflow;
        }

        public String getWorklet() {
            return worklet;
        }

        public String getSession() {
            return session;
        }

        public String getMapping() {
            return mapping;
        }

        public String toText() {
            List<String> parts = new ArrayList<String>();
            if (isNotEmpty(workflow)) {
                parts.add(SCOPE_WORKFLOW + ":" + workflow);
            }
            if (isNotEmpty(worklet)) {
                parts.add(SCOPE_WORKLET + ":" + worklet);
            }
            if (isNotEmpty(session)) {
                parts.add(SCOPE_SESSION + ":" + session);
            }
            if (isNotEmpty(mapping)) {
                parts.add(SCOPE_MAPPING + ":" + mapping);
            }
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    result.append(" / ");
                }
                result.append(parts.get(i));
            }
            return result.toString();
        }
    }

    public static class Declaration {
        private final String kind;
        private final String scopeType;
        private final String scopeName;
        private final String name;
        private final String dataType;
        private final String defaultValue;
        private final String defaultSource;
        private final String persistent;
        private final String description;
        private final String isParameter;
        private final String userDefined;
        private final Scope context;

        public Declaration(String kind, String scopeType, String scopeName, String name,
            String dataType, String defaultValue, String defaultSource, String persistent,
            String description, String isParameter, String userDefined, Scope context) {
            this.kind = clean(kind);
            this.scopeType = clean(scopeType);
            this.scopeName = clean(scopeName);
            this.name = clean(name);
            this.dataType = clean(dataType);
            this.defaultValue = defaultValue == null ? "" : defaultValue;
            this.defaultSource = clean(defaultSource);
            this.persistent = clean(persistent);
            this.description = clean(description);
            this.isParameter = clean(isParameter);
            this.userDefined = clean(userDefined);
            this.context = context == null ? Scope.empty() : context;
        }

        public static Declaration fromElement(String kind, String scopeType, String scopeName, Element element, Scope context) {
            String defaultSource = element != null && element.hasAttribute("DEFAULTVALUE")
                ? clean(element.getAttribute("DEFAULTVALUE")).length() == 0 ? DEFAULT_XML_EMPTY : DEFAULT_XML_VALUE
                : DEFAULT_XML_MISSING;
            String defaultValue = DEFAULT_XML_MISSING.equals(defaultSource) ? "" : clean(element.getAttribute("DEFAULTVALUE"));
            return new Declaration(kind, scopeType, scopeName, attr(element, "NAME"), attr(element, "DATATYPE"),
                defaultValue, defaultSource, attr(element, "ISPERSISTENT"), attr(element, "DESCRIPTION"),
                attr(element, "ISPARAM"), attr(element, "USERDEFINED"), context);
        }

        public List<String> indexNames() {
            List<String> result = new ArrayList<String>();
            addUnique(result, name);
            if (name.startsWith("$$")) {
                addUnique(result, name.substring(2));
            } else if (name.startsWith("$")) {
                addUnique(result, name.substring(1));
            } else {
                addUnique(result, "$$" + name);
                addUnique(result, "$" + name);
            }
            return result;
        }

        public boolean matchesName(String candidate) {
            String cleanCandidate = clean(candidate);
            for (String value : indexNames()) {
                if (value.equals(cleanCandidate)) {
                    return true;
                }
            }
            return false;
        }

        private static void addUnique(List<String> values, String value) {
            if (isNotEmpty(value) && !values.contains(value)) {
                values.add(value);
            }
        }

        public boolean hasXmlDefault() {
            return DEFAULT_XML_VALUE.equals(defaultSource) || DEFAULT_XML_EMPTY.equals(defaultSource);
        }

        public String getReplacementText() {
            if (hasXmlDefault()) {
                return defaultValue;
            }
            return runtimePlaceholder(kind, name);
        }

        public String getKind() {
            return kind;
        }

        public String getScopeType() {
            return scopeType;
        }

        public String getScopeName() {
            return scopeName;
        }

        public String getScope() {
            return isNotEmpty(scopeName) ? scopeType + ":" + scopeName : scopeType;
        }

        public String getName() {
            return name;
        }

        public String getDataType() {
            return dataType;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public String getDefaultSource() {
            return defaultSource;
        }

        public String getPersistent() {
            return persistent;
        }

        public String getDescription() {
            return description;
        }

        public String getIsParameter() {
            return isParameter;
        }

        public String getUserDefined() {
            return userDefined;
        }

        public Scope getContext() {
            return context;
        }
    }

    public static class ReferenceToken {
        private final String raw;
        private final String kind;
        private final String name;
        private final String owner;
        private final String property;
        private final boolean recognized;
        private final boolean runtimeOnly;

        private ReferenceToken(String raw, String kind, String name, String owner, String property,
            boolean recognized, boolean runtimeOnly) {
            this.raw = clean(raw);
            this.kind = clean(kind);
            this.name = clean(name);
            this.owner = clean(owner);
            this.property = clean(property);
            this.recognized = recognized;
            this.runtimeOnly = runtimeOnly
                || KIND_PM_BUILTIN.equals(kind)
                || KIND_SESSION_RUNTIME_ALIAS.equals(kind)
                || KIND_TASK_STATUS.equals(kind)
                || KIND_DECISION_OUTPUT.equals(kind);
        }

        static ReferenceToken empty(String raw) {
            return new ReferenceToken(raw, "", raw, "", "", false, false);
        }

        public boolean isRecognized() {
            return recognized;
        }

        public boolean isRuntimeOnly() {
            return runtimeOnly;
        }

        public boolean isScopedParameterReference() {
            return KIND_MAPPING_VARIABLE.equals(kind)
                || KIND_WORKFLOW_VARIABLE.equals(kind)
                || KIND_MAPPING_PARAMETER.equals(kind)
                || KIND_BRACED_PARAMETER.equals(kind);
        }

        public boolean isTaskStatusReference() {
            return KIND_TASK_STATUS.equals(kind);
        }

        public boolean isDecisionOutputReference() {
            return KIND_DECISION_OUTPUT.equals(kind);
        }

        public String getRaw() {
            return raw;
        }

        public String getKind() {
            return kind;
        }

        public String getName() {
            return name;
        }

        public String getOwner() {
            return owner;
        }

        public String getProperty() {
            return property;
        }

        public String getConditionValueName() {
            return name;
        }
    }

    public static class Resolution {
        private final String reference;
        private final String kind;
        private final String status;
        private final String resolvedScope;
        private final Declaration declaration;
        private final String replacementText;
        private final String limitation;

        private Resolution(String reference, String kind, String status, String resolvedScope,
            Declaration declaration, String replacementText, String limitation) {
            this.reference = clean(reference);
            this.kind = clean(kind);
            this.status = clean(status);
            this.resolvedScope = clean(resolvedScope);
            this.declaration = declaration;
            this.replacementText = replacementText == null ? "" : replacementText;
            this.limitation = clean(limitation);
        }

        static Resolution resolved(ReferenceToken token, Declaration declaration, String status) {
            String limitation = declaration.hasXmlDefault() ? "" : "XML declaration has no DEFAULTVALUE; runtime value must be supplied";
            return new Resolution(token.getRaw(), declaration.getKind(), status, declaration.getScope(), declaration,
                declaration.getReplacementText(), limitation);
        }

        static Resolution runtime(ReferenceToken token) {
            return new Resolution(token.getRaw(), token.getKind(), STATUS_RUNTIME_PLACEHOLDER, SCOPE_RUNTIME,
                null, runtimePlaceholder(token.getKind(), token.getRaw()),
                "Informatica runtime reference requires adapter/runtime binding");
        }

        static Resolution parameterFile(ReferenceToken token) {
            return new Resolution(token.getRaw(), token.getKind(), STATUS_PARAMETER_FILE_REFERENCE, SCOPE_PARAMETER_FILE,
                null, runtimePlaceholder(token.getKind(), token.getRaw()),
                "Parameter-file reference requires external parameter file binding");
        }

        static Resolution ambiguous(ReferenceToken token, String status, String scopes) {
            return new Resolution(token.getRaw(), token.getKind(), status, scopes, null,
                runtimePlaceholder(token.getKind(), token.getRaw()),
                "Reference is ambiguous across scopes: " + scopes);
        }

        static Resolution unresolved(String reference, String kind, String status) {
            return new Resolution(reference, kind, status, "", null, runtimePlaceholder(kind, reference),
                "Reference has no declaration in XML scope");
        }

        public boolean requiresRuntimeBinding() {
            return !STATUS_RESOLVED_IN_XML_SCOPE.equals(status)
                && !STATUS_RESOLVED_IN_XML_UNIQUE_NAME.equals(status)
                || declaration != null && !declaration.hasXmlDefault();
        }

        public String requiredBinding() {
            if (declaration != null && !declaration.hasXmlDefault()) {
                return "runtime value for " + declaration.getScope() + " " + declaration.getName();
            }
            if (STATUS_PARAMETER_FILE_REFERENCE.equals(status)) {
                return "parameter file value for " + reference;
            }
            if (STATUS_RUNTIME_PLACEHOLDER.equals(status)) {
                return "runtime adapter value for " + reference;
            }
            if (status.startsWith("AMBIGUOUS")) {
                return "scope disambiguation for " + reference;
            }
            if (STATUS_UNDECLARED_XML_REFERENCE.equals(status)) {
                return "XML declaration or runtime binding for " + reference;
            }
            return "";
        }

        public String getReference() {
            return reference;
        }

        public String getKind() {
            return kind;
        }

        public String getStatus() {
            return status;
        }

        public String getResolvedScope() {
            return resolvedScope;
        }

        public Declaration getDeclaration() {
            return declaration;
        }

        public String getReplacementText() {
            return replacementText;
        }

        public String getLimitation() {
            return limitation;
        }
    }

    public static class Replacement {
        private final String sourceText;
        private final String replacedText;
        private final List<Resolution> resolutions;

        Replacement(String sourceText, String replacedText, List<Resolution> resolutions) {
            this.sourceText = sourceText == null ? "" : sourceText;
            this.replacedText = replacedText == null ? "" : replacedText;
            this.resolutions = resolutions == null ? new ArrayList<Resolution>() : new ArrayList<Resolution>(resolutions);
        }

        public boolean requiresRuntimeBinding() {
            for (Resolution resolution : resolutions) {
                if (resolution.requiresRuntimeBinding()) {
                    return true;
                }
            }
            return false;
        }

        public List<String> requiredBindings() {
            List<String> result = new ArrayList<String>();
            for (Resolution resolution : resolutions) {
                String binding = resolution.requiredBinding();
                if (isNotEmpty(binding) && !result.contains(binding)) {
                    result.add(binding);
                }
            }
            return result;
        }

        public String limitationsText() {
            List<String> result = new ArrayList<String>();
            for (Resolution resolution : resolutions) {
                String limitation = resolution.getLimitation();
                if (isNotEmpty(limitation) && !result.contains(limitation)) {
                    result.add(limitation);
                }
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < result.size(); i++) {
                if (i > 0) {
                    builder.append("; ");
                }
                builder.append(result.get(i));
            }
            return builder.toString();
        }

        public String getSourceText() {
            return sourceText;
        }

        public String getReplacedText() {
            return replacedText;
        }

        public List<Resolution> getResolutions() {
            return new ArrayList<Resolution>(resolutions);
        }
    }

    public static class ParameterFileReference {
        private final Scope scope;
        private final String value;
        private final String valueSource;

        public ParameterFileReference(Scope scope, String value, String valueSource) {
            this.scope = scope == null ? Scope.empty() : scope;
            this.value = clean(value);
            this.valueSource = clean(valueSource);
        }

        public Scope getScope() {
            return scope;
        }

        public String getValue() {
            return value;
        }

        public String getValueSource() {
            return valueSource;
        }
    }
}
