package com.fr.plugin.tptj.ivan.jsd19083;

import com.fr.plugin.context.PluginContext;
import com.fr.plugin.observer.inner.AbstractPluginLifecycleMonitor;
import com.fr.plugin.tptj.ivan.jsd19083.constant.PluginConstants;

public class LifecycleMonitorEK extends AbstractPluginLifecycleMonitor {
    @Override
    public void afterRun(PluginContext pluginContext) {
        System.out.println("afterRun." + PluginConstants.PLUGIN.AVAILABLE);
    }

    @Override
    public void beforeStop(PluginContext pluginContext) {
        System.out.println("beforeStop." + PluginConstants.PLUGIN.AVAILABLE);
    }

    @Override
    public int currentAPILevel() {
        return 1;
    }
}
