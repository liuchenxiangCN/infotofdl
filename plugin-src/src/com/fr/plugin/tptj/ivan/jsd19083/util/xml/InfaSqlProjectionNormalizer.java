package com.fr.plugin.tptj.ivan.jsd19083.util.xml;

import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.FieldLineageInfo;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl.db.InfaFieldMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adds deterministic aliases to top-level SELECT items using connected SQ ports. */
public final class InfaSqlProjectionNormalizer {
    public static final String PROVEN = "PROVEN_UNIQUE_ALIASES";
    public static final String PREFIX_PROVEN = "PROVEN_PREFIX_WITH_UNRESOLVED_SUFFIX";
    public static final String RUNTIME_REQUIRED = "RUNTIME_REQUIRED";
    public static final String UNRESOLVED = "UNRESOLVED";

    private InfaSqlProjectionNormalizer() {
    }

    public static String normalize(String sql, FieldLineageInfo lineage) {
        if (sql == null || sql.trim().length() == 0 || lineage == null) {
            return sql;
        }
        lineage.setProjectionSql(sql, sql);
        List<InfaFieldMeta> fields = lineage.getSingleReaderProjectionFields();
        if (fields.isEmpty()) {
            lineage.setSqlProjectionResult(RUNTIME_REQUIRED,
                "A single Source Qualifier projection could not be proven from Mapping connectors.", null);
            return sql;
        }
        UnionParts union = splitTopLevelUnions(sql);
        List<String> normalizedBranches = new ArrayList<String>();
        int provenCount = fields.size();
        boolean prefixOnly = false;
        for (String branch : union.branches) {
            SelectProjection projection = parseProjection(branch);
            if (projection == null) {
                lineage.setSqlProjectionResult(UNRESOLVED, "Top-level SELECT projection could not be parsed safely.", null);
                return sql;
            }
            if (projection.hasStar) {
                lineage.setSqlProjectionResult(RUNTIME_REQUIRED, "SELECT * requires datasource metadata at runtime.", null);
                return sql;
            }
            if (projection.items.size() > fields.size()) {
                lineage.setSqlProjectionResult(UNRESOLVED,
                    "SQL projects more columns than the connected Source Qualifier ports.", null);
                return sql;
            }
            if (projection.items.size() < fields.size()) {
                prefixOnly = true;
                provenCount = Math.min(provenCount, projection.items.size());
            }
            normalizedBranches.add(rewriteProjection(branch, projection, fields));
        }
        List<String> unavailable = new ArrayList<String>();
        for (int i = provenCount; i < fields.size(); i++) {
            unavailable.add(fields.get(i).getFieldName());
        }
        lineage.setSqlProjectionResult(prefixOnly ? PREFIX_PROVEN : PROVEN,
            prefixOnly
                ? "Only the connected projection prefix is present in SQL; suffix fields are intentionally not mapped."
                : "Effective reader SQL aliases follow Mapping connector port order.", unavailable);
        String effectiveSql = union.join(normalizedBranches);
        lineage.setProjectionSql(sql, effectiveSql);
        return effectiveSql;
    }

    private static String rewriteProjection(String branch, SelectProjection projection, List<InfaFieldMeta> fields) {
        StringBuilder rewritten = new StringBuilder();
        rewritten.append(branch.substring(0, projection.start));
        for (int i = 0; i < projection.items.size(); i++) {
            if (i > 0) rewritten.append(", ");
            rewritten.append(stripAlias(projection.items.get(i))).append(" AS ").append(sqlIdentifier(fields.get(i).getFieldName()));
        }
        rewritten.append(branch.substring(projection.end));
        return rewritten.toString();
    }

    private static SelectProjection parseProjection(String sql) {
        int select = findKeyword(sql, "select", 0);
        if (select < 0) return null;
        int start = select + 6;
        int from = findKeyword(sql, "from", start);
        if (from < 0) return null;
        List<String> items = splitTopLevel(sql.substring(start, from), ',');
        if (items.isEmpty()) return null;
        boolean hasStar = false;
        for (String item : items) {
            String normalized = item.trim().replaceAll("\\s+", "");
            if ("*".equals(normalized) || normalized.endsWith(".*")) {
                hasStar = true;
            }
        }
        return new SelectProjection(start, from, items, hasStar);
    }

    private static String stripAlias(String item) {
        String value = item.trim();
        int as = lastTopLevelAs(value);
        if (as >= 0) {
            return value.substring(0, as).trim();
        }
        int whitespace = lastTopLevelWhitespace(value);
        if (whitespace < 0) return value;
        String prefix = value.substring(0, whitespace).trim();
        String suffix = value.substring(whitespace).trim();
        if (!isAliasToken(suffix) || isReserved(suffix) || prefix.length() == 0 || endsWithOperator(prefix)) {
            return value;
        }
        return prefix;
    }

    private static int lastTopLevelAs(String value) {
        int result = -1;
        ScanState state = new ScanState();
        for (int i = 0; i < value.length(); i++) {
            i = state.consume(value, i);
            if (state.isCodeAtTopLevel() && startsKeyword(value, i, "as")) {
                result = i;
            }
        }
        return result;
    }

    private static int lastTopLevelWhitespace(String value) {
        int result = -1;
        ScanState state = new ScanState();
        for (int i = 0; i < value.length(); i++) {
            i = state.consume(value, i);
            if (state.isCodeAtTopLevel() && Character.isWhitespace(value.charAt(i))) {
                result = i;
            }
        }
        return result;
    }

    private static boolean isAliasToken(String value) {
        return value.matches("[A-Za-z_][A-Za-z0-9_$#]*")
            || value.matches("[\\p{L}_][\\p{L}\\p{N}_$#]*")
            || value.matches("\"(?:\"\"|[^\"])+\"")
            || value.matches("`[^`]+`")
            || value.matches("\\[[^]]+]");
    }

    private static boolean isReserved(String value) {
        String word = value.replace("\"", "").replace("`", "").toUpperCase(Locale.ROOT);
        return "END".equals(word) || "NULL".equals(word) || "TRUE".equals(word) || "FALSE".equals(word)
            || "THEN".equals(word) || "ELSE".equals(word) || "WHEN".equals(word);
    }

    private static boolean endsWithOperator(String value) {
        return value.matches("(?s).*(?:\\+|-|\\*|/|=|<|>|\\|\\||,|\\.)\\s*$");
    }

    private static String sqlIdentifier(String value) {
        String name = value == null ? "" : value.trim();
        if (name.matches("[A-Za-z_][A-Za-z0-9_$#]*")) {
            return name;
        }
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private static int findKeyword(String sql, String keyword, int start) {
        ScanState state = new ScanState();
        for (int i = 0; i < sql.length(); i++) {
            i = state.consume(sql, i);
            if (i >= start && state.isCodeAtTopLevel() && startsKeyword(sql, i, keyword)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsKeyword(String value, int index, String keyword) {
        if (index < 0 || index + keyword.length() > value.length()
            || !value.regionMatches(true, index, keyword, 0, keyword.length())) {
            return false;
        }
        char before = index == 0 ? ' ' : value.charAt(index - 1);
        char after = index + keyword.length() >= value.length() ? ' ' : value.charAt(index + keyword.length());
        return !isWord(before) && !isWord(after);
    }

    private static boolean isWord(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$' || value == '#';
    }

    private static List<String> splitTopLevel(String value, char delimiter) {
        List<String> result = new ArrayList<String>();
        int start = 0;
        ScanState state = new ScanState();
        for (int i = 0; i < value.length(); i++) {
            i = state.consume(value, i);
            if (state.isCodeAtTopLevel() && value.charAt(i) == delimiter) {
                result.add(value.substring(start, i).trim());
                start = i + 1;
            }
        }
        result.add(value.substring(start).trim());
        return result;
    }

    private static UnionParts splitTopLevelUnions(String sql) {
        List<String> branches = new ArrayList<String>();
        List<String> delimiters = new ArrayList<String>();
        int start = 0;
        ScanState state = new ScanState();
        for (int i = 0; i < sql.length(); i++) {
            i = state.consume(sql, i);
            if (state.isCodeAtTopLevel() && startsKeyword(sql, i, "union")) {
                branches.add(sql.substring(start, i));
                int end = i + 5;
                while (end < sql.length() && Character.isWhitespace(sql.charAt(end))) end++;
                if (startsKeyword(sql, end, "all")) end += 3;
                delimiters.add(sql.substring(i, end));
                start = end;
                i = end - 1;
            }
        }
        branches.add(sql.substring(start));
        return new UnionParts(branches, delimiters);
    }

    private static final class SelectProjection {
        final int start;
        final int end;
        final List<String> items;
        final boolean hasStar;
        SelectProjection(int start, int end, List<String> items, boolean hasStar) {
            this.start = start;
            this.end = end;
            this.items = items;
            this.hasStar = hasStar;
        }
    }

    private static final class UnionParts {
        final List<String> branches;
        final List<String> delimiters;
        UnionParts(List<String> branches, List<String> delimiters) {
            this.branches = branches;
            this.delimiters = delimiters;
        }
        String join(List<String> values) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) result.append(delimiters.get(i - 1));
                result.append(values.get(i));
            }
            return result.toString();
        }
    }

    private static final class ScanState {
        int depth;
        char quote;
        boolean lineComment;
        boolean blockComment;

        int consume(String value, int index) {
            char ch = value.charAt(index);
            char next = index + 1 < value.length() ? value.charAt(index + 1) : '\0';
            if (lineComment) {
                if (ch == '\n' || ch == '\r') lineComment = false;
                return index;
            }
            if (blockComment) {
                if (ch == '*' && next == '/') {
                    blockComment = false;
                    return index + 1;
                }
                return index;
            }
            if (quote != 0) {
                if (ch == quote) {
                    if (next == quote) return index + 1;
                    quote = 0;
                }
                return index;
            }
            if (ch == '-' && next == '-') {
                lineComment = true;
                return index + 1;
            }
            if (ch == '/' && next == '*') {
                blockComment = true;
                return index + 1;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')' && depth > 0) {
                depth--;
            }
            return index;
        }

        boolean isCodeAtTopLevel() {
            return !lineComment && !blockComment && quote == 0 && depth == 0;
        }
    }
}
