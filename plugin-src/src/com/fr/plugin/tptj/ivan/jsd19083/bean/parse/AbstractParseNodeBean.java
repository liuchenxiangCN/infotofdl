package com.fr.plugin.tptj.ivan.jsd19083.bean.parse;

import com.fr.dp.catalog.dto.NodeDTO;
import com.fr.dp.devwork.work.component.dto.ComponentDTO;
import com.fr.dp.devwork.work.graph.GraphOffset;
import com.fr.dp.devwork.work.line.dto.LineDTO;
import com.fr.dp.devwork.work.note.Note;
import com.fr.dp.devwork.work.param.ParamEntity;
import com.fr.dp.offline.work.dto.OfflineWorkDTO;
import com.fr.dp.offline.work.dto.OfflineWorkFileDTO;
import com.fr.dp.workstatus.valueobject.CheckState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class AbstractParseNodeBean implements BaseParseNodeBean {
    private String folder;
    private String workName;

    public AbstractParseNodeBean(String folder, String workName) {
        this.folder = folder;
        this.workName = workName;
    }

    @Override
    public OfflineWorkDTO updateFDLWorkDTO(NodeDTO nodeDTO) {
        String id = nodeDTO.getId();
        String name = nodeDTO.getName();
        OfflineWorkFileDTO fileDTO = new OfflineWorkFileDTO();
        fileDTO.setId(id);
        fileDTO.setName(name);
        fileDTO.setParams(this.getFDLParams());
        fileDTO.setNotes(this.getFDLNotes());
        fileDTO.setGraph(this.getFDLGraph());
        fileDTO.setLines(this.getFDLLines());
        fileDTO.setNodes(this.getFDLNodes());
        return new OfflineWorkDTO(id, CheckState.UNKNOWN, fileDTO, new HashMap());
    }

    protected List<ComponentDTO> getFDLNodes() {
        return new ArrayList<ComponentDTO>();
    }

    protected List<LineDTO> getFDLLines() {
        return new ArrayList<LineDTO>();
    }

    protected GraphOffset getFDLGraph() {
        return new GraphOffset();
    }

    protected List<Note> getFDLNotes() {
        return new ArrayList<Note>();
    }

    protected List<ParamEntity> getFDLParams() {
        return new ArrayList<ParamEntity>();
    }

    public String getFolder() {
        return folder;
    }

    public String getWorkName() {
        return workName;
    }
}
