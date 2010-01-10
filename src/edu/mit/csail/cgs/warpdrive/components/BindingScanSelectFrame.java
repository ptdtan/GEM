package edu.mit.csail.cgs.warpdrive.components;

import java.awt.*;
import java.sql.SQLException;
import java.util.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.Collection;
import java.util.Iterator;

import edu.mit.csail.cgs.datasets.binding.BindingEvent;
import edu.mit.csail.cgs.datasets.binding.BindingScan;
import edu.mit.csail.cgs.datasets.binding.BindingScanLoader;
import edu.mit.csail.cgs.datasets.species.Genome;
import edu.mit.csail.cgs.ewok.verbs.ChromRegionIterator;
import edu.mit.csail.cgs.ewok.verbs.binding.BindingExpander;
import edu.mit.csail.cgs.utils.database.UnknownRoleException;
import edu.mit.csail.cgs.viz.components.BindingScanSelectPanel;


public class BindingScanSelectFrame extends JFrame implements ActionListener {

    private Genome genome;
    private BindingScanSelectPanel bssp;
    private JButton ok, cancel;    
    private RegionList list;

    public static void main(String args[]) throws Exception {
        Genome genome = new Genome(args[0],args[1]);
        BindingEventAnnotationPanel beap = new BindingEventAnnotationPanel(null, new ArrayList<BindingEvent>());
        JFrame f = new BindingEventAnnotationPanel.Frame(beap);
        f.pack();
        new BindingScanSelectFrame(genome,beap);
    }
    
    public BindingScanSelectFrame (Genome g, RegionList list) {
        super();

        try {
			bssp = new BindingScanSelectPanel();
		} catch (UnknownRoleException e) {
			e.printStackTrace();
			throw new IllegalArgumentException(e.getMessage());
		} catch(SQLException e) { 
    		e.printStackTrace();
    		this.dispose();			
		}

        if(g != null ) { 
        	try {
        		bssp.setGenome(g);
        	} catch (Exception ex) {
        		ex.printStackTrace();
        		this.dispose();
        	}
        }
        
        this.list = list;
        this.genome = g;
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridBagLayout());
        Dimension buttonSize = new Dimension(30,20);
        ok = new JButton("OK");
        cancel = new JButton("Cancel");
        ok.setMaximumSize(buttonSize);
        cancel.setMaximumSize(buttonSize);
        ok.addActionListener(this);
        cancel.addActionListener(this);
        buttonPanel.add(ok);
        buttonPanel.add(cancel);
        Container content = getContentPane();
        content.setLayout(new BorderLayout());
        content.add(buttonPanel,BorderLayout.SOUTH);
        content.add(bssp,BorderLayout.CENTER);
        setSize(500,400);
        setLocation(50,50);
        setVisible(true);
    }

    public void actionPerformed (ActionEvent e) {
        if (e.getSource() == ok) {
            Collection<BindingScan> scans = bssp.getObjects();
            Populator p = new Populator(scans,genome);
            Thread t = new Thread(p);
            t.start();
            this.dispose();
        } else if (e.getSource() == cancel) {
            this.dispose();
        }
    }

    private class Populator implements Runnable {
        
        private Collection<BindingScan> scans;
        private Genome genome;
        
        public Populator(Collection<BindingScan> scans, Genome g) {
            this.scans = scans;
            genome = g;
        }
        
        public void run() {
            try {
                BindingScanLoader loader = new BindingScanLoader();
                
                for (BindingScan scan : scans) {
                    BindingExpander expander = new BindingExpander(loader,scan);

                    ChromRegionIterator iter = new ChromRegionIterator(genome);
                    while (iter.hasNext()) {
                        Iterator<BindingEvent> events = expander.execute(iter.next());
                        while (events.hasNext()) {
                            list.addRegion(events.next());
                        }
                    }
                }
                
                loader.close();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

    }
}
