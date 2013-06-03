package edu.mit.csail.cgs.deepseq.analysis;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import edu.mit.csail.cgs.clustering.affinitypropagation.CorrelationSimilarity;
import edu.mit.csail.cgs.datasets.chipseq.ChipSeqLocator;
import edu.mit.csail.cgs.datasets.general.Point;
import edu.mit.csail.cgs.datasets.general.Region;
import edu.mit.csail.cgs.datasets.general.StrandedPoint;
import edu.mit.csail.cgs.datasets.motifs.WeightMatrix;
import edu.mit.csail.cgs.datasets.motifs.WeightMatrixImport;
import edu.mit.csail.cgs.datasets.species.Genome;
import edu.mit.csail.cgs.datasets.species.Organism;
import edu.mit.csail.cgs.deepseq.DeepSeqExpt;
import edu.mit.csail.cgs.deepseq.StrandedBase;
import edu.mit.csail.cgs.deepseq.utilities.CommonUtils;
import edu.mit.csail.cgs.deepseq.utilities.ReadCache;
import edu.mit.csail.cgs.ewok.verbs.SequenceGenerator;
import edu.mit.csail.cgs.ewok.verbs.chipseq.GPSParser;
import edu.mit.csail.cgs.ewok.verbs.chipseq.GPSPeak;
import edu.mit.csail.cgs.ewok.verbs.motifs.WeightMatrixScorer;
import edu.mit.csail.cgs.tools.utils.Args;
import edu.mit.csail.cgs.utils.NotFoundException;
import edu.mit.csail.cgs.utils.Pair;
/**
 * Compute the spatial relationship among multiple TFs' binding sites<br>
 * Find the clusters of multiTF binding and the relative positions of each TF sites
 * @author Yuchun
 *
 */
public class TFBS_SpaitialAnalysis {
	private final int TARGET_WIDTH = 250;
	Genome genome=null;
	ArrayList<String> expts = new ArrayList<String>();
	ArrayList<String> names = new ArrayList<String>();
	ArrayList<String> readdb_names = new ArrayList<String>();
	
	ArrayList<WeightMatrix> pwms = new ArrayList<WeightMatrix>();
	ArrayList<ArrayList<Site>> all_sites = new ArrayList<ArrayList<Site>>();
	ArrayList<Point> all_TSS;
	ArrayList<Cluster> all_clusters;
	
	double gc = 0.42;//mouse		gc=0.41 for human
	int distance = 50;		// distance between TFBS within a cluster
	int range = 1000;		// the range around anchor site to search for targets
	double wm_factor = 0.6;	// PWM threshold, as fraction of max score
	double cutoff = 0.3;	// corr score cutoff
	File dir;
	boolean oldFormat =  false;
	boolean useDirectBindingOnly = false;
	private SequenceGenerator<Region> seqgen;
	boolean dev = false;
	
	String tss_file;
	String cluster_file;

	// command line option:  (the folder contains GEM result folders) 
	// --dir C:\Data\workspace\gse\TFBS_clusters --species "Mus musculus;mm9" --r 2 --pwm_factor 0.6 --expts expt_list.txt [--no_cache --old_format] 
	public static void main(String[] args) {
		TFBS_SpaitialAnalysis mtb = new TFBS_SpaitialAnalysis(args);
		int round = Args.parseInteger(args, "r", 2);
		int type = Args.parseInteger(args, "type", 0);
		switch(type){
		case 0:
			mtb.loadEventAndMotifs(round);
			mtb.findTfbsClusters();
			break;
		case 1:
//			mtb.loadEventAndMotifs(round);
			mtb.loadClusterAndTSS();
			mtb.computeCorrelations();
			break;
		case -1:
			mtb.mergedTSS();
			break;
		case -2:
			mtb.printTssSignal();
			break;
		}
		
	}
	
	public TFBS_SpaitialAnalysis(String[] args){
				
	    try {
	    	Pair<Organism, Genome> pair = Args.parseGenome(args);
	    	if(pair==null){
	    	  System.err.println("No genome provided; provide a Gifford lab DB genome name");
	    	  System.exit(1);
	    	}else{
	    		genome = pair.cdr();
	    	}
	    } catch (NotFoundException e) {
	      e.printStackTrace();
	    }

		Set<String> flags = Args.parseFlags(args);
		dev = flags.contains("dev");
		oldFormat = flags.contains("old_format");
		useDirectBindingOnly = flags.contains("direct");
		dir = new File(Args.parseString(args, "dir", "."));
		expts = new ArrayList<String>();
		names = new ArrayList<String>();
		ArrayList<String> info = CommonUtils.readTextFile(Args.parseString(args, "expts", null));
		for (String txt: info){
			if (!txt.equals("")){
				String[] f = txt.split("\t");
				expts.add(f[0]);
				names.add(f[2]);
				readdb_names.add(f[4]);
			}
		}
		tss_file = Args.parseString(args, "tss", null);
		cluster_file = Args.parseString(args, "cluster", null);
		
		distance = Args.parseInteger(args, "distance", distance);
		range = Args.parseInteger(args, "range", range);
		wm_factor = Args.parseDouble(args, "pwm_factor", wm_factor);
		cutoff = Args.parseDouble(args, "cutoff", cutoff);
		gc = Args.parseDouble(args, "gc", gc);
		seqgen = new SequenceGenerator<Region>();
		seqgen.useCache(!flags.contains("no_cache"));
	}
	
	private void loadEventAndMotifs(int round){

		for (int tf=0;tf<names.size();tf++){
			String expt = expts.get(tf);

			System.out.print(String.format("TF#%d: loading %s", tf, expt));
			
			// load motif files
			WeightMatrix wm = null;
			File dir2= new File(dir, expt);
			if (!oldFormat)
				dir2= new File(dir2, expt+"_outputs");
			final String suffix = expt+"_"+ (round>=2?round:1) +"_PFM";
			File[] files = dir2.listFiles(new FilenameFilter(){
				public boolean accept(File arg0, String arg1) {
					if (arg1.startsWith(suffix))
						return true;
					else
						return false;
				}
			});
			if (files.length==0){
				System.out.println(expt+" does not have a motif PFM file.");
				pwms.add(null);
			}
			else{				// if we have valid PFM file
				wm = CommonUtils.loadPWM_PFM_file(files[0].getAbsolutePath(), gc);
				pwms.add( wm );
			}
			
			// load binding event files 
			File gpsFile = new File(dir2, expt+"_"+ (round>=2?round:1) +
					(oldFormat?"_GPS_significant.txt":"_GEM_events.txt"));
			String filePath = gpsFile.getAbsolutePath();
			WeightMatrixScorer scorer = null;
			int posShift=0, negShift=0;
			if (round!=1&&round!=2&&round!=9&&wm!=null){	
				scorer = new WeightMatrixScorer(wm);
				posShift = wm.length()/2;				// shift to the middle of motif
				negShift = wm.length()-1-wm.length()/2;
			}
			try{
				List<GPSPeak> gpsPeaks = GPSParser.parseGPSOutput(filePath, genome);
				ArrayList<Site> sites = new ArrayList<Site>();
				for (GPSPeak p:gpsPeaks){
					Site site = new Site();
					site.tf_id = tf;
					site.signal = p.getStrength();
					site.motifStrand = p.getKmerStrand();
					
					if (round!=1&&round!=2&&round!=9&&wm!=null){		// use nearest motif as binding site
						Region region = p.expand(round);
						String seq = seqgen.execute(region).toUpperCase();	// here round is the range of window 
						int hit = CommonUtils.scanPWMoutwards(seq, wm, scorer, round, wm.getMaxScore()*wm_factor).car();
						if (hit!=-999){
							if (hit>=0)
								site.bs = new Point(p.getGenome(), p.getChrom(), region.getStart()+hit+posShift);
							else
								site.bs = new Point(p.getGenome(), p.getChrom(), region.getStart()-hit+negShift);
						}
						else
							site.bs = (Point)p;		// no motif found, still use original GPS call
					}
					else
						site.bs = (Point)p;
					
					sites.add(site);
				}
				System.out.println(", n="+sites.size());
				all_sites.add(sites);
			}
			catch (IOException e){
				System.out.println(expt+" does not have valid GPS/GEM event call file.");
				System.exit(1);
			}
		}
	}
	
	class Site implements Comparable<Site>{
		int tf_id;
		Point bs;
		int id;
		double signal;
		char motifStrand;								// motif match strand
		public int compareTo(Site s) {					// ascending coordinate
			return(bs.compareTo(s.bs));
		}
	}

	class Cluster{
		Region region;
		ArrayList<Integer> TFIDs = new ArrayList<Integer>();
		ArrayList<Double> TF_Signals = new ArrayList<Double>();
		ArrayList<Boolean> TF_hasMotifs = new ArrayList<Boolean>();
	}
	
	private void findTfbsClusters(){
		// classify sites by chrom
		TreeMap<String, ArrayList<Site>> chrom2sites = new TreeMap<String, ArrayList<Site>>();
		for (ArrayList<Site> sites:all_sites){
			for (Site s:sites){
				String chr = s.bs.getChrom();
				if (!chrom2sites.containsKey(chr))
					chrom2sites.put(chr, new ArrayList<Site>());
				chrom2sites.get(chr).add(s);
			}
		}
		
		// sort sites and form clusters
		ArrayList<ArrayList<Site>> clusters = new ArrayList<ArrayList<Site>>();		
		ArrayList<Site> cluster = new ArrayList<Site>();
		for (String chr: chrom2sites.keySet()){
			ArrayList<Site> sites = chrom2sites.get(chr);
			Collections.sort(sites);

			cluster.add(sites.get(0));
			for (int i=1;i<sites.size();i++){
				Site s = sites.get(i);
				Site p = cluster.get(cluster.size()-1);		//previous
				if (s.bs.getLocation()-p.bs.getLocation()<distance)
					cluster.add(s);
				else{
					cluster.trimToSize();
					clusters.add(cluster);
					cluster = new ArrayList<Site>();
					cluster.add(s);
				}					
			}
			// finish the chromosome
			cluster.trimToSize();
			clusters.add(cluster);
			cluster = new ArrayList<Site>();
		}
		// finish all the sites
		cluster.trimToSize();
		clusters.add(cluster);

		// output
		StringBuilder sb = new StringBuilder();
		sb.append("#Region\tLength\t#Sites\tTFs\tTFIDs\tSignals\tPos\tMotifs\t#Motif\n");
		for (ArrayList<Site> c:clusters){
			int numSite = c.size();
			if (c.isEmpty())
				continue;
			Region r = new Region(genome, c.get(0).bs.getChrom(), c.get(0).bs.getLocation(), c.get(c.size()-1).bs.getLocation());
			StringBuilder sb_tfs = new StringBuilder();
			StringBuilder sb_tfids = new StringBuilder();
			StringBuilder sb_tf_signals = new StringBuilder();
			StringBuilder sb_tf_positions = new StringBuilder();
			StringBuilder sb_tf_motifs = new StringBuilder();
			int totalMotifs = 0;
			for (Site s:c){
				sb_tfs.append(names.get(s.tf_id)).append(",");
				sb_tfids.append(s.tf_id).append(",");
				sb_tf_signals.append(String.format("%d", Math.round(s.signal))).append(",");
				sb_tf_positions.append(s.bs.getLocation()-r.getStart()).append(",");
				sb_tf_motifs.append(s.motifStrand).append(",");
				totalMotifs += s.motifStrand=='*'?0:1;
			}
			if (sb_tfs.length()!=0){
				sb_tfs.deleteCharAt(sb_tfs.length()-1);
				sb_tfids.deleteCharAt(sb_tfids.length()-1);
				sb_tf_signals.deleteCharAt(sb_tf_signals.length()-1);
				sb_tf_positions.deleteCharAt(sb_tf_positions.length()-1);
				sb_tf_motifs.deleteCharAt(sb_tf_motifs.length()-1);
			}
			sb.append(r.toString()).append("\t").append(r.getWidth()).append("\t").append(numSite).append("\t").
			append(sb_tfs.toString()).append("\t").append(sb_tfids.toString()).append("\t").
			append(sb_tf_signals.toString()).append("\t").append(sb_tf_positions.toString()).append("\t").
			append(sb_tf_motifs.toString()).append("\t").append(totalMotifs)
			.append("\n");
		}

		CommonUtils.writeFile("TF_clusters.txt", sb.toString());	
	}
	private void mergedTSS(){
		
		if (tss_file != null){
			ArrayList<Point> tsss = new ArrayList<Point>();
			ArrayList<String> text = CommonUtils.readTextFile(tss_file);
			for (String t: text){
				String[] f = t.split("\t");
				tsss.add(Point.fromString(genome, f[0]));
			}
			
			// classify tss by chrom
			TreeMap<String, ArrayList<Point>> chrom2sites = new TreeMap<String, ArrayList<Point>>();
			for (Point s:tsss){
				String chr = s.getChrom();
				if (!chrom2sites.containsKey(chr))
					chrom2sites.put(chr, new ArrayList<Point>());
				chrom2sites.get(chr).add(s);
			}
			
			// sort tss and form clusters
			ArrayList<ArrayList<Point>> clusters = new ArrayList<ArrayList<Point>>();		
			ArrayList<Point> cluster = new ArrayList<Point>();
			for (String chr: chrom2sites.keySet()){
				ArrayList<Point> sites = chrom2sites.get(chr);
				Collections.sort(sites);

				cluster.add(sites.get(0));
				for (int i=1;i<sites.size();i++){
					Point s = sites.get(i);
					Point p = cluster.get(cluster.size()-1);		//previous
					if (s.getLocation()-p.getLocation()<distance)
						cluster.add(s);
					else{
						cluster.trimToSize();
						clusters.add(cluster);
						cluster = new ArrayList<Point>();
						cluster.add(s);
					}					
				}
				// finish the chromosome
				cluster.trimToSize();
				clusters.add(cluster);
				cluster = new ArrayList<Point>();
			}
			// finish all the sites
			cluster.trimToSize();
			clusters.add(cluster);

			// merge multi-tss in a cluster into one point
			ArrayList<Region> merged = new ArrayList<Region>();			
			for (ArrayList<Point> c:clusters){
				if (c.isEmpty())
					continue;
				Region r = null;
				if (c.size()==1)
					r = c.get(0).expand(0);
				else
					r = new Region(genome, c.get(0).getChrom(), c.get(0).getLocation(), c.get(c.size()-1).getLocation());
				merged.add(r);
			}
			// output
			StringBuilder sb = new StringBuilder();
			for (Region r:merged)
				sb.append(r.getMidpoint().toString()+"\t"+r.getWidth()+"\n");
			CommonUtils.writeFile("mergedTSS.txt", sb.toString());
		}
	}
	private void printTssSignal(){

		final int MAXREAD = 1000000;
		
		if (tss_file != null){
			all_TSS = new ArrayList<Point>();
			ArrayList<String> text = CommonUtils.readTextFile(tss_file);
			for (String t: text){
				String[] f = t.split("\t");
				all_TSS.add(Point.fromString(genome, f[0]));
			}
			all_TSS.trimToSize();
		}
		int[][]signals = new int[all_TSS.size()][expts.size()];
		for (int i=0;i<expts.size();i++){
			String readdb_name = readdb_names.get(i);
			List<ChipSeqLocator> rdbexpts = new ArrayList<ChipSeqLocator>();
			String[] pieces = readdb_name.trim().split(";");
            if (pieces.length == 2) {
            	rdbexpts.add(new ChipSeqLocator(pieces[0], pieces[1]));
            } else if (pieces.length == 3) {
            	rdbexpts.add(new ChipSeqLocator(pieces[0], pieces[1], pieces[2]));
            } else {
                throw new RuntimeException("Couldn't parse a ChipSeqLocator from " + readdb_name);
            }
            DeepSeqExpt ip = new DeepSeqExpt(genome, rdbexpts, "readdb", -1);
            ReadCache ipCache = new ReadCache(genome, expts.get(i));
            
			// cache sorted start positions and counts of all positions
			long tic = System.currentTimeMillis();
			System.out.print("Loading "+ipCache.getName()+" data from ReadDB ... \t");
			List<String> chroms = genome.getChromList();
			if (dev){
				chroms = new ArrayList<String>();
				chroms.add("19");
			}
			for (String chrom: chroms ){
				// load  data for this chromosome.
				int length = genome.getChromLength(chrom);
				Region wholeChrom = new Region(genome, chrom, 0, length-1);
				int count = ip.countHits(wholeChrom);
				ArrayList<Region> chunks = new ArrayList<Region>();
				// if there are too many reads in a chrom, read smaller chunks
				if (count>MAXREAD){
					int chunkNum = count/MAXREAD*2+1;
					int chunkLength = length/chunkNum;
					int start = 0;
					while (start<=length){
						int end = Math.min(length, start+chunkLength-1);
						Region r = new Region(genome, chrom, start, end);
						start = end+1;
						chunks.add(r);
					}
				}else
					chunks.add(wholeChrom);

				for (Region chunk: chunks){
					Pair<ArrayList<Integer>,ArrayList<Float>> hits = ip.loadStrandedBaseCounts(chunk, '+');
					ipCache.addHits(chrom, '+', hits.car(), hits.cdr());
					hits = ip.loadStrandedBaseCounts(chunk, '-');
					ipCache.addHits(chrom, '-', hits.car(), hits.cdr());
				}
			} // for each chrom

			ipCache.populateArrays(true);
			ip.closeLoaders();
			ip=null;
			System.gc();
			ipCache.displayStats();
			System.out.println(CommonUtils.timeElapsed(tic));
            
			// now get the data from the cache
            for (int j=0;j<all_TSS.size();j++){
            	Region region = all_TSS.get(j).expand(TARGET_WIDTH);
            	List<StrandedBase> bases = ipCache.getStrandedBases(region, '+');
            	bases.addAll(ipCache.getStrandedBases(region, '-'));
            	signals[j][i] = (int)StrandedBase.countBaseHits(bases);
            }
		}
		StringBuilder sb = new StringBuilder("#Site\t");
		for (int i=0;i<expts.size();i++){
			sb.append(expts.get(i)).append("\t");
		}
		CommonUtils.replaceEnd(sb, '\n');
		for (int j=0;j<all_TSS.size();j++){
			sb.append(all_TSS.get(j).toString()).append("\t");
			for (int i=0;i<expts.size();i++){
				sb.append(signals[j][i]).append("\t");
			}
			CommonUtils.replaceEnd(sb, '\n');
		}
		CommonUtils.writeFile("TSS_signals.txt", sb.toString());
	}
	
	
	private void loadClusterAndTSS(){
		if (tss_file != null){
			all_TSS = new ArrayList<Point>();
			ArrayList<String> text = CommonUtils.readTextFile(tss_file);
			for (String t: text){
				String[] f = t.split("\t");
				all_TSS.add(Point.fromString(genome, f[0]));
			}
		}
		
		if (cluster_file != null){
			all_clusters = new ArrayList<Cluster>();
			ArrayList<String> text = CommonUtils.readTextFile(cluster_file);
			for (String t: text){
				if (t.startsWith("#"))
					continue;
				String[] f = t.split("\t");
				Cluster c = new Cluster();
				all_clusters.add(c);
				c.region = Region.fromString(genome, f[0]);
				String[] f_id = f[4].split(",");
				for (String s: f_id)
					c.TFIDs.add(Integer.parseInt(s));
				String[] f_signal = f[5].split(",");
				for (String s: f_signal)
					c.TF_Signals.add(Double.parseDouble(s));
				String[] f_motif = f[6].split(",");
				for (String s: f_motif)
					c.TF_hasMotifs.add(Integer.parseInt(s)==1);
			}
		}
		
	}
	private void computeCorrelations(){
		// prepare connections to readdb
		ArrayList<DeepSeqExpt> chipseqs = new ArrayList<DeepSeqExpt>();
		for (String readdb_str : readdb_names){
			List<ChipSeqLocator> rdbexpts = new ArrayList<ChipSeqLocator>();
			String[] pieces = readdb_str.trim().split(";");
            if (pieces.length == 2) {
            	rdbexpts.add(new ChipSeqLocator(pieces[0], pieces[1]));
            } else if (pieces.length == 3) {
            	rdbexpts.add(new ChipSeqLocator(pieces[0], pieces[1], pieces[2]));
            } else {
                throw new RuntimeException("Couldn't parse a ChipSeqLocator from " + readdb_str);
            }
            chipseqs.add( new DeepSeqExpt(genome, rdbexpts, "readdb", -1) );
		}
		
		for (Cluster c: all_clusters){
			
			Point anchor = c.region.getMidpoint();
			ArrayList<Point> targets = CommonUtils.getPointsWithinWindow(all_TSS, anchor, range);
			ArrayList<Site_target_corr> list = new ArrayList<Site_target_corr>();
			
			// get unique TF IDs for consideration
			HashSet<Integer> TF_IDs = new HashSet<Integer>();
			for (int i=0;i<c.TF_hasMotifs.size();i++){
				if ( !useDirectBindingOnly || c.TF_hasMotifs.get(i) ){
					TF_IDs.add(c.TFIDs.get(i));	
				}			
			}
			Integer[] TFIDs = new Integer[TF_IDs.size()];
			TF_IDs.toArray(TFIDs);
			if (TFIDs.length<=2)
				continue;
			
			// get signals at anchor site
			List<Double> signals = new ArrayList<Double>();
			for (int i=0;i<TFIDs.length;i++){
				int total = 0;
				for (int j=0;j<c.TFIDs.size();j++)
					if (c.TFIDs.get(j)==TFIDs[i])
						total += c.TF_Signals.get(j);
				signals.add(i, (double)total);
			}
			
			StringBuilder sb1 = new StringBuilder();
			for (int id : TFIDs)
				sb1.append(names.get(id)).append("\t");
			System.out.println("=================================\nchr"+c.region.toString());
			System.out.println(sb1.toString());
			
			for (Point p:targets){				
				// get corresponding signals at the target sites
				List<Double> target_signals = new ArrayList<Double>();
				for (int i=0;i<TFIDs.length;i++){
					target_signals.add(i, (double)chipseqs.get(TFIDs[i]).countHits(p.expand(TARGET_WIDTH)));
				}
				
				// compute correlation
				double corr = CorrelationSimilarity.computeSimilarity2(signals, target_signals);
				Site_target_corr t = new Site_target_corr();
				t.target = p;
				t.signals = signals;
				t.target_signals = target_signals;
				t.corr = corr;
				if (t.corr>=cutoff)
					list.add(t);
			}
			
			Collections.sort(list);
			for (Site_target_corr t:list){

				System.out.println("chr"+anchor.toString()+"-"+t.target.getLocation()+"\t"+t.target.offset(anchor)+"\t"
						+t.signals.size()+"\t"+String.format("%.2f", t.corr));
				StringBuilder sb = new StringBuilder();
				for (double s: t.signals){
					sb.append(s).append("\t");
				}
				sb.append("\n");
				for (double s: t.target_signals){
					sb.append(s).append("\t");
				}
				System.out.println(sb.toString());
			}
		}
		
		// clean up
		for (DeepSeqExpt e: chipseqs){
			e.closeLoaders();
		}
	}
	
	class Site_target_corr implements Comparable<Site_target_corr>{
		Point target;
		List<Double> signals;
		List<Double> target_signals;
		double corr;
		public int compareTo(Site_target_corr t) {					// descending corr
			return corr>t.corr?-1:corr==t.corr?0:1;
		}
	}
}
