package edu.mit.csail.cgs.projects.readdb;

import java.io.*;
import java.util.*;
import org.apache.commons.cli.*;
import net.sf.samtools.*;
import net.sf.samtools.util.CloseableIterator;


/**
 * Reads two files of SAM or BAM data and produces output on stdout in the
 * format expected by ImportHits.  Both files must be sorted in the same order.
 * Only reads present in both files will be included in the output (on stdout).
 * 
 * The matching of reads between files is done by stripping "/\d" from the end of the 
 * read name, as reads usually end in /1 or /2.
 *
 * Usage:
 * java PairedSAMToReadDB --left leftreads.bam --right rightreads.bam
 *
 *
 * Options:	--nosuboptimal (flag to only take the hits with the minimum number of mismatches)
 * 			--uniquehits (flag to only print 1:1 read to hit mappings)
 * 
 * nosuboptimal is applied before uniquehits
 *
 * Output columns are
 * 1) left chromname
 * 2) left position
 * 3) left strand
 * 4) left readlen
 * 5) right chromname
 * 6) right position
 * 7) right strand
 * 8) right length
 * 9) weight
 */


public class PairedSAMToReadDB {

    public static boolean uniqueOnly, filterSubOpt, debug;
    public static ArrayList<SAMRecord> leftbuffer, rightbuffer;
    public static CloseableIterator<SAMRecord> leftiter, rightiter;

    public static void main(String args[]) throws IOException, ParseException {
        Options options = new Options();
        options.addOption("l","left",true,"filename of left side of read");
        options.addOption("r","right",true,"filename of right side of read");
        options.addOption("u","uniquehits",false,"only output hits with a single mapping");
        options.addOption("s","nosuboptimal",false,"do not include hits whose score is not equal to the best score for the read");
        options.addOption("D","debug",false,"enable debugging spew?");
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args, false );            
    	uniqueOnly = cl.hasOption("uniquehits");
    	filterSubOpt = cl.hasOption("nosuboptimal");
        debug = cl.hasOption("debug");
        String leftfile = cl.getOptionValue("left");
        String rightfile = cl.getOptionValue("right");

        SAMFileReader leftreader = new SAMFileReader(new FileInputStream(leftfile));
        SAMFileReader rightreader = new SAMFileReader(new FileInputStream(rightfile));

        leftbuffer = new ArrayList<SAMRecord>();
        rightbuffer = new ArrayList<SAMRecord>();

        leftiter = leftreader.iterator();
        rightiter = rightreader.iterator();

        Collection<SAMRecord> leftrecords = new ArrayList<SAMRecord>();
        Collection<SAMRecord> rightrecords = new ArrayList<SAMRecord>();

        boolean keepgoing = true;
        while (keepgoing) {
            SAMRecord left = nextLeft();
            SAMRecord right = nextRight();
            if (left == null || right == null) {
                break;
            }
            if (debug) {
                System.err.println("LEFT " + left);
                System.err.println("RIGHT " + right);            
            }
            if (left.getReadName().equals(right.getReadName())) {
                leftrecords.add(left);
                rightrecords.add(right);
                if (debug) {
                    System.err.println("MATCH.  Storing.");
                }
            } else {                
                dumpRecords(leftrecords, rightrecords);
                leftrecords.clear();
                rightrecords.clear();                
                leftbuffer.add(left);
                rightbuffer.add(right);
                if (debug) {
                    System.err.println("mismatch.  dumped and cleared\n");
                }
            }
            for (int i = 0; i < leftbuffer.size(); i++) {
                int j = 0;
                while (j < rightbuffer.size() && !leftbuffer.get(i).getReadName().equals(rightbuffer.get(j).getReadName())) {
                    j++;
                }
                if (j == rightbuffer.size()) {
                    continue;
                }
                if (debug) {
                    System.err.println(String.format("Found match of %s at %d and %d",leftbuffer.get(i).getReadName(),i,j));
                }

                int k = i;
                int l = j;
                do {
                    leftrecords.add(leftbuffer.get(k++));
                } while (k < leftbuffer.size() && leftbuffer.get(i).getReadName().equals(leftbuffer.get(k).getReadName()));
                do {
                    rightrecords.add(rightbuffer.get(l++));
                } while (l < rightbuffer.size() && leftbuffer.get(i).getReadName().equals(rightbuffer.get(l).getReadName()));
                dumpRecords(leftrecords, rightrecords);
                leftrecords.clear();
                rightrecords.clear();                
                
                while (k-- > 0) {
                    leftbuffer.remove(0);
                }
                while (l-- > 0) {
                    rightbuffer.remove(0);
                }

                i = -1;                
            }
            if (!leftiter.hasNext()) {
                rightbuffer.clear();
            }
            if (!rightiter.hasNext()) {
                leftbuffer.clear();
            }
            if (debug) {
                System.err.println("li.hn " + leftiter.hasNext() + " lb.size " + leftbuffer.size() + 
                                   "ri.hn " + rightiter.hasNext() + " rb.size " + rightbuffer.size());                
            }
            keepgoing = (leftiter.hasNext() || leftbuffer.size() > 0) &&
                (rightiter.hasNext() || rightbuffer.size() > 0);
        }
        dumpRecords(leftrecords, rightrecords);
    }
    public static SAMRecord nextLeft() {
        SAMRecord result = null;
        while (result == null) {
            if (leftiter.hasNext()) {
                result = leftiter.next();
            } else if (leftbuffer.size() > 0) {
                result = leftbuffer.remove(leftbuffer.size() -1);
            } else {
                return null;
            }
            result.setReadName(result.getReadName().replaceAll("/\\d$",""));
            if (result.getReferenceName().equals("*")) {
                result = null;
            }
        }
        return result;
    }
    public static SAMRecord nextRight() {
        SAMRecord result = null;
        while (result == null) {
            if (rightiter.hasNext()) {
                result = rightiter.next();
            } else if (rightbuffer.size() > 0) {
                result = rightbuffer.remove(rightbuffer.size() -1);
            } else {
                return null;
            }
            result.setReadName(result.getReadName().replaceAll("/\\d$",""));
            if (result.getReferenceName().equals("*")) {
                result = null;
            }
        }
        return result;
    }    

    public static void dumpRecords(Collection<SAMRecord> lefts,
                                   Collection<SAMRecord> rights) {
        int mapcount = lefts.size() * rights.size();
        if (mapcount == 0) {
            return;
        }
        if (uniqueOnly && mapcount > 1) {
            return;
        }
        float weight = 1 / ((float)mapcount);
        for (SAMRecord left : lefts) {
            for (SAMRecord right : rights) {
                System.out.println(String.format("%s\t%d\t%s\t%d\t%s\t%d\t%s\t%d\t%f",
                                                 left.getReferenceName(),
                                                 left.getReadNegativeStrandFlag() ? 
                                                 left.getAlignmentEnd() : 
                                                 left.getAlignmentStart(),
                                                 left.getReadNegativeStrandFlag() ? "-" : "+",
                                                 left.getReadLength(),
                                                 
                                                 right.getReferenceName(),
                                                 right.getReadNegativeStrandFlag() ? 
                                                 right.getAlignmentEnd() : 
                                                 right.getAlignmentStart(),
                                                 right.getReadNegativeStrandFlag() ? "-" : "+",
                                                 right.getReadLength(),

                                                 weight));

                
            }
        }



    }

    
        
        



}