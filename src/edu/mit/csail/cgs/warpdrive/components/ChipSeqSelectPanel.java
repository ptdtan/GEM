/*
 * Created on Jan 11, 2008
 *
 * TODO 
 * 
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.mit.csail.cgs.warpdrive.components;

import java.util.*;
import java.util.regex.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.sql.*;
import edu.mit.csail.cgs.utils.Pair;
import edu.mit.csail.cgs.datasets.chipseq.*;
import edu.mit.csail.cgs.viz.components.GenericSelectPanel;

public class ChipSeqSelectPanel extends GenericSelectPanel<ChipSeqLocator> {
    
    private ChipSeqLoader chipSeqLoader;
    private TreeSet<ChipSeqLocator> locators;
    private JTextField regex;
    private ChipSeqTableModel selectedModel, filteredModel;

    public ChipSeqSelectPanel() { 
        try {
            chipSeqLoader = new ChipSeqLoader();
        } catch (Exception e) {
            e.printStackTrace();
            chipSeqLoader = null;
        }
        locators = new TreeSet<ChipSeqLocator>();
        selectedModel = new ChipSeqTableModel();
        filteredModel = new ChipSeqTableModel();
        init(filteredModel,selectedModel);
    }
    public JPanel getInputsPanel() {
        JPanel inputPanel = new JPanel(); inputPanel.setLayout(new BorderLayout());
        inputPanel.setLayout(new BorderLayout());
        regex = new JTextField();
        inputPanel.add(new JLabel("pattern to filter alignments"), BorderLayout.WEST);
        inputPanel.add(regex, BorderLayout.CENTER);        
        return inputPanel;
    }

    /* this is different than CollapseLocatorsByName because it
       keys on the name and alignment
    */
    public Collection<ChipSeqLocator> getFilteredForSelected() {
        Map<Pair<String,String>, Set<String>> experiments = new HashMap<Pair<String,String>, Set<String>>();
        for (ChipSeqLocator l : super.getFilteredForSelected()) {
            Pair<String,String> key = new Pair<String,String>(l.getExptName(), l.getAlignName());
            if (!experiments.containsKey(key)) {
                experiments.put(key, new HashSet<String>());
            }
            Set<String> reps = experiments.get(key);
            reps.addAll(l.getReplicates());
        }
        ArrayList<ChipSeqLocator> output = new ArrayList<ChipSeqLocator>();
        for (Pair<String,String> nv : experiments.keySet()) {
            output.add(new ChipSeqLocator(nv.car(),
                                          experiments.get(nv),
                                          nv.cdr()));
        }
        return output;
    }

    public void retrieveData() {
        locators.clear();
        try {
            synchronized(locators) {
                Collection<ChipSeqAlignment> aligns = chipSeqLoader.loadAlignments(getGenome());
                for(ChipSeqAlignment align : aligns) { 
                    locators.add(new ChipSeqLocator(align.getExpt().getName(),
                                                    align.getExpt().getReplicate(),
                                                    align.getName()));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }
    public void updateComponents() {
        selectedModel.clear();
        filteredModel.clear();
        synchronized(locators) {
            for (ChipSeqLocator l : locators) {
                filteredModel.addObject(l);
            }
        }
    }
    public void filter() {
        String reg = regex.getText().trim();
        Pattern patt = null;
        if(reg != null && reg.length() > 0) {
            patt = Pattern.compile(reg);
        }
        synchronized(locators) {
            locators.clear();
            try {
                for (ChipSeqAlignment align : chipSeqLoader.loadAlignments(getGenome())){
                    if (patt == null || patt.matcher(align.toString()).find()) {
                        locators.add(new ChipSeqLocator(align.getExpt().getName(),
                                                        align.getExpt().getReplicate(),
                                                        align.getName()));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
            filteredModel.clear();
            for (ChipSeqLocator l : locators) {
                filteredModel.addObject(l);
            }
        }
    }

    
    public static Collection<ChipSeqLocator> collapseLocatorsByName(Collection<ChipSeqLocator> locs) { 
        LinkedHashMap<String,Map<String,Set<String>>> map = 
            new LinkedHashMap<String,Map<String,Set<String>>>();
        
        for(ChipSeqLocator loc : locs) { 
            String exptName = loc.getExptName();
            String alignName = loc.getAlignName();
            if(!map.containsKey(exptName)) { map.put(exptName, new LinkedHashMap<String,Set<String>>()); }
            if(!map.get(exptName).containsKey(alignName)) { map.get(exptName).put(alignName, new TreeSet<String>()); }
            map.get(exptName).get(alignName).addAll(loc.getReplicates());
        }
        
        LinkedList<ChipSeqLocator> collapsed = new LinkedList<ChipSeqLocator>();
        
        for(String exptName : map.keySet()) { 
            for(String alignName : map.get(exptName).keySet()) { 
                ChipSeqLocator newloc = new ChipSeqLocator(exptName, map.get(exptName).get(alignName), alignName);
                collapsed.add(newloc);
            }
        }
        
        return collapsed;
    }

    public void close() {
        super.close();
        chipSeqLoader.close();
    }
}