package com.fr.plugin.tptj.ivan.jsd19083.constant;

import com.fr.plugin.pluginkit.helper.PluginHelper;

public class PluginConstants {
    public static final class PLUGIN {
        public static final String ID = "com.fr.plugin.tptj.ivan.jsd19083";
        public static final String JSD = "JSD-19083";
        public static final String NAME = "FDL XML批量生成任务_EK";
        public static final Boolean AVAILABLE;

        static {
            boolean available;
            try {
                available = PluginHelper.checkPluginLic();
            } catch (Throwable t) {
                available = true;
            }
            AVAILABLE = Boolean.valueOf(available);
        }
    }

    public static final class OPTION {
        public static final String ID = "decision-management-jsd19083-ek";
        public static final String NAME = "批量生成任务";
    }

    public static final class TASK {
        public static final String ROOT = "_DP_ROOT_CATALOG_";
    }
}
