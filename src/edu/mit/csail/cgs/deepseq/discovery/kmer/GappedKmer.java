package edu.mit.csail.cgs.deepseq.discovery.kmer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import edu.mit.csail.cgs.deepseq.utilities.CommonUtils;
import edu.mit.csail.cgs.utils.sequence.SequenceUtils;

public class GappedKmer extends Kmer{
	private HashMap<Kmer,Boolean> baseKmers = new HashMap<Kmer,Boolean>();
	
	public GappedKmer(String wkString){
		k = wkString.length();
		kmerString = wkString;
		kmerRC = SequenceUtils.reverseComplement(kmerString);
	}

	/** 
	 * add the kmer to the sub-kmers for the gapped kmer<br>
	 * it is ok to RC() the sub-kmers after adding it because their kmerStrings are not used anymore<br>
	 * we only care about the pos/neg hits, and has stored the orientation
	 * @param kmer
	 */
	public void addBaseKmer (Kmer kmer, boolean isSameOrientation){
		baseKmers.put(kmer, isSameOrientation);
	}
	void linkBaseKmers(){
		for (Kmer km:baseKmers.keySet())
			km.addGappedKmer(this);
	}
	void clearBaseKmers(){
		baseKmers.clear();
	}
	public Set<Kmer> getBaseKmers (){
		return baseKmers.keySet();
	}
	boolean getSubKmerOrientation(Kmer subkmer){
		return baseKmers.get(subkmer);
	}
	public void mergePosHits(){
		posBits.clear();
		for (Kmer km:baseKmers.keySet()){
			posBits.or(km.posBits);
		}
		if (use_weighted_hit_count)
			setWeightedPosHitCount();
	}
	
	public void mergeNegHits(){
//		negHits.clear();
		negBits.clear();
		for (Kmer km:baseKmers.keySet()){
//			negHits.addAll(km.getNegHits());
			negBits.or(km.negBits);
		}
	}
	
	public void update(){
		mergePosHits();
		mergeNegHits();
		linkBaseKmers();
	}
	
	/**
	 * Clone for output only. It does not set up correct GK-SK linkages.
	 */
	public GappedKmer clone(){
		GappedKmer n = new GappedKmer(getKmerString());
		n.clusterId = clusterId;
		n.shift = shift;
		n.setNegBits((BitSet)negBits.clone());
		n.setPosBits((BitSet)posBits.clone());
		n.hgp_lg10 = hgp_lg10;
		n.kmerStartOffset = kmerStartOffset;
		n.isSeedOrientation = isSeedOrientation;
		for (Kmer km:baseKmers.keySet())
			n.addBaseKmer(km, baseKmers.get(km));
		n.update();
		return n;
	}
	
	public void addBasicKmersToSet(HashSet<Kmer> reg){
		for (Kmer km:baseKmers.keySet())
			reg.add(km);
	}
	
	/**
	 * Print a list of k-mers to a KSM file<br>
	 * It can print both gapped and ungapped kmers. For gapped kmers, the sub-kmers will also be printed.
	 * @param kmers
	 * @param k
	 * @param posSeqCount
	 * @param negSeqCount
	 * @param score
	 * @param filePrefix
	 * @param printShortFormat
	 * @param print_kmer_hits
	 * @param printKmersAtK
	 */
	public static void printGappedKmers(ArrayList<Kmer> kmers, int kOriginal, int gap, int posSeqCount, int negSeqCount, double score, 
			String filePrefix, boolean printShortFormat, boolean print_kmer_hits, boolean printKmersAtK){
		if (kmers==null || kmers.isEmpty())
			return;
		
		int k = kOriginal + gap;
		Collections.sort(kmers);
		
		int subKmerId = 0;
		HashMap<Kmer,Integer> allSubKmers = new HashMap<Kmer,Integer>();
		for (Kmer km: kmers){
			if (km instanceof GappedKmer){
				for(Kmer sk: ((GappedKmer) km).getBaseKmers()){
					if (!allSubKmers.containsKey(sk)){
						allSubKmers.put(sk,subKmerId);
						subKmerId++;
					}
				}
			}
	    }
		Kmer[] subkmerList = new Kmer[allSubKmers.size()];
		for (Kmer km: allSubKmers.keySet())
			subkmerList[allSubKmers.get(km)] = km;
		
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("#%d/%d\n", posSeqCount, negSeqCount));
		sb.append(String.format("#%.2f\n", score));
		if (printShortFormat){
			sb.append(GappedKmer.toShortHeader(k)).append("\n");
			for (Kmer kmer:kmers){
				if (kmer instanceof GappedKmer)
					sb.append(kmer.toShortString()).append("\t").append(((GappedKmer) kmer).getBaseKmers().size()).append("\n");
				else
					sb.append(kmer.toShortString()).append("\n");
			}
		}
		else{
			sb.append(GappedKmer.toHeader(k)).append("\n");
			for (Kmer kmer:kmers){
				sb.append(kmer.toString2());				
				if (kmer instanceof GappedKmer){
					sb.append("\t");
					for (Kmer sk: ((GappedKmer) kmer).getBaseKmers())
						sb.append(allSubKmers.get(sk)).append(",");
					sb.deleteCharAt(sb.length()-1);
					if (print_kmer_hits)
						sb.append("\tN.A.\tN.A.");	// the real hits are stored in sub-kmers
				}
				else{
					sb.append("\t").append(-1);
					if (print_kmer_hits)
						sb.append("\t").append(hitBits2string(kmer.posBits)).append("\t").append(hitBits2string(kmer.negBits));
				}
				sb.append("\n");
			}
			sb.append("\n");	// an empty line to signal that the following are the sub-kmers
			for (Kmer kmer:subkmerList){
				sb.append(kmer.toString2()).append("\t").append(allSubKmers.get(kmer));
				if (print_kmer_hits)
					sb.append("\t").append(hitBits2string(kmer.posBits)).append("\t").append(hitBits2string(kmer.negBits));
				sb.append("\n");
			}
		}
		
		if (printKmersAtK)
			CommonUtils.writeFile(String.format("%s_kmers_%d+%d.txt", filePrefix, kOriginal, gap), sb.toString());
		else
			CommonUtils.writeFile(String.format("%s.KSM.txt", filePrefix), sb.toString());
	}

	public void removeBasekmers(Kmer km) {
		baseKmers.remove(km);		
	}
	public static String toHeader(int k){
		int length=2*k+1;
		String firstField = "# k-mer/r.c.";
		if (firstField.length()<length)
			firstField += CommonUtils.padding(length-firstField.length(), ' ');
		return firstField+"\tOffset\tPosCt\twPosCt\tNegCt\tHgpLg10\tCIDs\tPosHits (base85 encoding)\tNegHits (base85 encoding)";
	}

	public static String toShortHeader(int k){
		int length=2*k+1;
		String firstField = "# k-mer/r.c.";
		if (firstField.length()<length)
			firstField += CommonUtils.padding(length-firstField.length(), ' ');
		return firstField+"\tPosCt\twPosCt\tNegCt\tHgpLg10\tNumChildren";
	}
	
	public static ArrayList<Kmer> loadKmers(File file){
		ArrayList<Kmer> kmers = new ArrayList<Kmer>();
		HashMap<String, Kmer> basekmerMap = new HashMap<String, Kmer>();
		try {	
			BufferedReader bin = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			bin.readLine();			// skip header line, to be compatible with old file format
	        String line;
	        while((line = bin.readLine()) != null) { 
	        	if (line.startsWith("#"))
	        		continue;
	            if (line.equals(""))
	            	break;
	            line = line.trim();
	            Kmer kmer = GappedKmer.fromString(line);
	            kmers.add(kmer);
	        }	
	        while((line = bin.readLine()) != null) { 
	            line = line.trim();
	            Kmer kmer = GappedKmer.fromString(line);
	            basekmerMap.put(kmer.CIDs, kmer);
	        }	
	        if (bin != null) {
	            bin.close();
	        }
        } catch (IOException e) {
        	System.err.println("I/O Error when processing "+file.getName());
            e.printStackTrace(System.err);
        }
		for (Kmer km:kmers){
			if (km instanceof GappedKmer){
				GappedKmer gk = (GappedKmer) km;
				String[] f = gk.CIDs.split(",");
				for (String id: f){
					Kmer sk = basekmerMap.get(id);
					gk.addBaseKmer(sk, CommonUtils.strMinDistance(gk.kmerString, sk.kmerString)==1);
				}
				gk.update();
			}
			
		}
        kmers.trimToSize();
		return kmers;
	}

	public static Kmer fromString(String str){
		String[] f = str.trim().split("\t");
		String[] f0f = f[0].split("/");
		String kmerStr = f0f[0];
		Kmer kmer = null;
		if (kmerStr.contains("N")){// Gapped kmer
			kmer = new GappedKmer(kmerStr);
		}
		else{
			BitSet b_pos = BitSet.valueOf(CommonUtils.decodeAscii85StringToBytes(f[7]));
			kmer = new Kmer(kmerStr, b_pos);
			if (f.length>8)
				kmer.negBits = BitSet.valueOf(CommonUtils.decodeAscii85StringToBytes(f[8]));
		}
		kmer.isSeedOrientation = true;
		kmer.CIDs = f[6];
		kmer.kmerStartOffset = Integer.parseInt(f[1]);
		kmer.shift = kmer.kmerStartOffset;
		kmer.hgp_lg10 = Double.parseDouble(f[5]);

		return kmer;
	}
}
