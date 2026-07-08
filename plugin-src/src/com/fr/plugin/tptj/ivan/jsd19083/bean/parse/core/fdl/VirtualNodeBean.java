/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.fr.dp.component.domain.entities.VirtualComponent
 *  com.fr.dp.devwork.work.component.api.Component
 *  com.fr.dp.devwork.work.component.dto.ComponentDTO
 */
package com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.fdl;

import com.fr.dp.component.domain.entities.VirtualComponent;
import com.fr.dp.devwork.work.component.api.Component;
import com.fr.dp.devwork.work.component.dto.ComponentDTO;
import com.fr.plugin.tptj.ivan.jsd19083.bean.parse.core.single.DefaultSingleXMLNodeBean;
import com.fr.plugin.tptj.ivan.jsd19083.service.FDLService;

public class VirtualNodeBean
extends DefaultSingleXMLNodeBean {
    private String note;

    public VirtualNodeBean(int x, int y, String name, String id) {
        this(x, y, name, id, "");
    }

    public VirtualNodeBean(int x, int y, String name, String id, String note) {
        super(x, y, name, id);
        this.note = note == null ? "" : note;
    }

    @Override
    public ComponentDTO getFDLComponentDTO() {
        return FDLService.INSTANCE.createComponentDTO(this.getX(), this.getY(), this.getFDLNodeType(), this.getFDLComponent());
    }

    @Override
    public Component getFDLComponent() {
        VirtualComponent result = new VirtualComponent();
        result.setId(this.getId());
        result.setName(this.getName());
        result.setNote(this.note);
        result.setExecuteLogic("AND");
        result.setDisabled(false);
        return result;
    }

    @Override
    public String getFDLNodeType() {
        return "VIRTUAL_NODE";
    }
}
