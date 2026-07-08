/*
 * Decompiled with CFR 0.152.
 */
package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core;

public class Link {
    private String from;
    private String to;
    private String condition;

    public Link(String from, String to) {
        this(from, to, "");
    }

    public Link(String from, String to, String condition) {
        this.from = from;
        this.to = to;
        this.condition = condition == null ? "" : condition.trim();
    }

    public String getFrom() {
        return this.from;
    }

    public String getTo() {
        return this.to;
    }

    public String getCondition() {
        return this.condition;
    }

    public boolean hasCondition() {
        return this.condition != null && this.condition.length() > 0;
    }

    public String toString() {
        if (this.hasCondition()) {
            return this.from + " -> " + this.to + " [" + this.condition + "]";
        }
        return this.from + " -> " + this.to;
    }
}
