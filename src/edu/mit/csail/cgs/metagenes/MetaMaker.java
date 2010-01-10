package edu.mit.csail.cgs.metagenes;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import edu.mit.csail.cgs.datasets.chipseq.ChipSeqLocator;
import edu.mit.csail.cgs.datasets.general.Point;
import edu.mit.csail.cgs.datasets.locators.ChipChipLocator;
import edu.mit.csail.cgs.datasets.species.Genome;
import edu.mit.csail.cgs.datasets.species.Organism;
import edu.mit.csail.cgs.ewok.verbs.chipseq.ChipSeqExpander;
import edu.mit.csail.cgs.metagenes.swing.MetaFrame;
import edu.mit.csail.cgs.tools.utils.Args;
import edu.mit.csail.cgs.utils.NotFoundException;
import edu.mit.csail.cgs.utils.Pair;

public class MetaMaker {
	private static boolean batchRun = false;
	private static boolean cluster = false;
	
	public static void main(String[] args) {
		try {
			if(args.length < 2){ printError();}
			
			Pair<Organism, Genome> pair = Args.parseGenome(args);
			Genome gen = pair.cdr();
			int winLen = Args.parseInteger(args,"win", 10000);
			int bins = Args.parseInteger(args,"bins", 100);
			double lineMin = Args.parseDouble(args,"linemin", 0);
			double lineMax = Args.parseDouble(args,"linemax", 100);
			int lineThick = Args.parseInteger(args,"linethick", 1);
			String profilerType = Args.parseString(args, "profiler", "simplechipseq");	
			List<String> expts = (List<String>) Args.parseStrings(args,"expt");
			List<String> backs = (List<String>) Args.parseStrings(args,"back");
			String peakFile = Args.parseString(args, "peaks", null);
			String outName = Args.parseString(args, "out", "meta");
			if(Args.parseFlags(args).contains("batch")){batchRun=true;}
			if(Args.parseFlags(args).contains("cluster")){cluster=true;}
			Color c = Color.blue;
			String newCol = Args.parseString(args, "color", "blue");
			if(newCol.equals("red"))
				c=Color.red;
			if(newCol.equals("green"))
				c=Color.green;
		
			
			if(gen==null || expts.size()==0){printError();}
	
			BinningParameters params = new BinningParameters(winLen, bins);
			System.out.println("Binding Parameters:\tWindow size: "+params.getWindowSize()+"\tBins: "+params.getNumBins());
		
			PointProfiler profiler=null;
			boolean normalizeProfile=false;
			if(profilerType.equals("simplechipseq")){
				List<ChipSeqLocator> exptlocs = Args.parseChipSeq(args,"expt");
				ArrayList<ChipSeqExpander> exptexps = new ArrayList<ChipSeqExpander>();
				for(ChipSeqLocator loc : exptlocs){
					System.out.println(loc.getExptName()+"\t"+loc.getAlignName());
					exptexps.add(new ChipSeqExpander(loc));
				}
				System.out.println("Loading data...");
				profiler = new SimpleChipSeqProfiler(params, exptexps, 174);
			}else if(profilerType.equals("chipseq5prime")){
				List<ChipSeqLocator> exptlocs = Args.parseChipSeq(args,"expt");
				ArrayList<ChipSeqExpander> exptexps = new ArrayList<ChipSeqExpander>();
				for(ChipSeqLocator loc : exptlocs){
					exptexps.add(new ChipSeqExpander(loc));
				}
				System.out.println("Loading data...");
				profiler = new ChipSeq5PrimeProfiler(params, exptexps, '-');
			}else if(profilerType.equals("chipseq")){
				normalizeProfile=true;
				ArrayList<ChipSeqLocator> exptlocs = (ArrayList<ChipSeqLocator>) Args.parseChipSeq(args,"expt");
				ArrayList<ChipSeqLocator> backlocs = backs.size()==0 ? null : (ArrayList<ChipSeqLocator>) Args.parseChipSeq(args,"back");
				System.out.println("Loading data...");
				profiler = new ChipSeqProfiler(params, gen, exptlocs,backlocs, 26, 174);
			}else if(profilerType.equals("chipseqz")){
				normalizeProfile=true;
				ArrayList<ChipSeqLocator> exptlocs = (ArrayList<ChipSeqLocator>) Args.parseChipSeq(args,"expt");
				ArrayList<ChipSeqLocator> backlocs = backs.size()==0 ? null : (ArrayList<ChipSeqLocator>) Args.parseChipSeq(args,"back");
				System.out.println("Loading data...");
				profiler = new ChipSeqProfiler(params, gen, exptlocs,backlocs, 26, 174, true);
			}else if(profilerType.equals("chipchip") || profilerType.equals("chipchipip") || profilerType.equals("chipchipwce")){
				normalizeProfile=true;
				ArrayList<ChipChipLocator> exptlocs = (ArrayList<ChipChipLocator>) Args.parseChipChip(gen, args, "expt");
				if(exptlocs.size()>0){
					System.out.println("Loading data...");
					if(profilerType.equals("chipchipip"))
						profiler = new ChipChipProfiler(params, gen, exptlocs.get(0), true, false);
					else if(profilerType.equals("chipchipwce"))
						profiler = new ChipChipProfiler(params, gen, exptlocs.get(0), false, true);
					else
						profiler = new ChipChipProfiler(params, gen, exptlocs.get(0));
				}
			}
			
			if(batchRun){
				System.out.println("Batch running...");
				MetaNonFrame nonframe = new MetaNonFrame(gen, params, profiler, normalizeProfile);
				nonframe.setColor(c);
				MetaProfileHandler handler = nonframe.getHandler();
				if(peakFile != null){
					Vector<Point> points = nonframe.getUtils().loadPoints(new File(peakFile));
					handler.addPoints(points);
				}else{
					Iterator<Point> points = nonframe.getUtils().loadTSSs();
					handler.addPoints(points);
				}
				while(handler.addingPoints()){}
				if(cluster)
					nonframe.clusterLinePanel();
				//Set the panel sizes here...
				nonframe.setLineMin(lineMin);
				nonframe.setLineMax(lineMax);
				nonframe.setLineThick(lineThick);
				nonframe.saveImages(outName);
				nonframe.savePointsToFile(outName);
				System.out.println("Finished");
				if(profiler!=null)
					profiler.cleanup();
			}else{
				System.out.println("Initializing Meta-point frame...");
				MetaFrame frame = new MetaFrame(gen, params, profiler, normalizeProfile);
				frame.setColor(c);
				frame.setLineMax(lineMax);
				frame.setLineMin(lineMin);
				frame.setLineThick(lineThick);
				frame.startup();
				if(peakFile != null){
					MetaProfileHandler handler = frame.getHandler();
					Vector<Point> points = frame.getUtils().loadPoints(new File(peakFile));
					handler.addPoints(points);					
				}
				frame.setLineMax(lineMax);
				frame.setLineMin(lineMin);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (NotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void printError(){
		System.err.println("Usage: MetaMaker --species <organism;genome> \n" +
				"--win <profile width> --bins <num bins> \n" +
				"--linemin <min>  --linemax <max> \n" +
				"--profiler <simplechipseq/chipseq/chipseqz/chipchip> \n" +
				"--expt <experiment names> --back <control experiment names (only applies to chipSeq)> \n" +
				"--peaks <peaks file name> --out <output root name> \n" +
				"--color <red/green/blue> \n" +
				"--cluster [flag to cluster in batch mode] \n" +
				"--batch [a flag to run without displaying the window]");
		System.exit(1);
	}
}