package edu.mit.csail.cgs.projects.readdb;

import java.util.*;
import java.io.*;
import org.junit.*;
import static org.junit.Assert.*;

public class TestPairedHits {

    private static int chrom;
    private static String prefix;
    private static boolean isLeft;
    private PairedHit[] hits;
    private Header header;
    private PairedHits hitsfile;

    public TestPairedHits() throws IOException {
        int len = 1000;
        hits = new PairedHit[len];
        for (int i = 0; i < len; i++) {
            hits[i] = new PairedHit(isLeft ? chrom : (int)(Math.random() * 1000),
                                    (int)(Math.random() * 100000000),
                                    i % 3 == 1,
                                    (short)(Math.random()*100+1),
                                    !isLeft ? chrom : (int)(Math.random() * 1000),
                                    (int)(Math.random() * 100000000),
                                    i % 3 == 1,
                                    (short)(Math.random()*100+1),
                                    (float)Math.random());
        }        
        PairedHits.writePairedHits(hits, prefix, chrom, isLeft);
        


        hitsfile = new PairedHits(prefix,chrom,isLeft);
        header = new Header(hitsfile.getPositionsBuffer().ib);
        header.writeIndexFile(prefix + chrom + ".index");        
        header = Header.readIndexFile(prefix + chrom + ".index");
    }

    @Test public void testCount() {
        assertEquals(hits.length, header.getNumHits());
    }

    @Test public void testArraySort() {
        List<PairedHit> ph = new ArrayList<PairedHit>();
        for (int i = 0; i < 1000; i++) {
            ph.add(new PairedHit(chrom,
                                 (int)(Math.random() * 100000000),
                                 i % 3 == 1,
                                 (short)(Math.random()*100+1),
                                 chrom,
                                 (int)(Math.random() * 100000000),
                                 i % 3 == 1,
                                 (short)(Math.random()*100+1),
                                 (float)Math.random()));
        }        
        Collections.sort(ph, new PairedHitLeftComparator());
        for (int i = 1; i < hits.length; i++) {
            assertTrue(ph.get(i-1).leftPos < ph.get(i).leftPos);
        }
        Collections.sort(ph, new PairedHitRightComparator());
        for (int i = 1; i < hits.length; i++) {
            assertTrue(ph.get(i-1).rightPos < ph.get(i).rightPos);
        }
        
    }

    @Test public void testFileSort() {
        IntBP positions = hitsfile.getPositionsBuffer();
        for (int i = 1; i < hits.length; i++) {
            assertTrue(positions.get(i-1) < positions.get(i));
        }        
    }

    @Test public void testChroms() {
        IntBP chroms = hitsfile.getChromsBuffer();
        for (int i = 0; i < hits.length; i++) {
            assertEquals(hitsfile.isLeft() ? hits[i].rightChrom : hits[i].leftChrom, chroms.get(i));
        }
    }

    @Test public void testPositions() {
        IntBP positions = hitsfile.getOtherPosBuffer();
        for (int i = 0; i < hits.length; i++) {
            assertEquals(hitsfile.isLeft() ? hits[i].rightPos : hits[i].leftPos, positions.get(i));
        }
    }

    @Test public void testStrand() {
        IntBP las = hitsfile.getLASBuffer();
        for (int i = 0; i < hits.length; i++) {
            assertEquals(!hitsfile.isLeft() ? hits[i].rightStrand : hits[i].leftStrand, Hits.getStrandOne(las.get(i)));
            assertEquals(hitsfile.isLeft() ? hits[i].rightStrand : hits[i].leftStrand, Hits.getStrandTwo(las.get(i)));
        }
    }
    @Test public void testLength() {
        IntBP las = hitsfile.getLASBuffer();
        for (int i = 0; i < hits.length; i++) {
            assertEquals(!hitsfile.isLeft() ? hits[i].rightLength : hits[i].leftLength, Hits.getLengthOne(las.get(i)));
            assertEquals(hitsfile.isLeft() ? hits[i].rightLength : hits[i].leftLength, Hits.getLengthTwo(las.get(i)));
        }
    }

   public static void main(String args[]) {
        prefix = args[0];
        if (!prefix.endsWith(System.getProperty("file.separator"))) {
            prefix = prefix + System.getProperty("file.separator");
        }
        chrom = Integer.parseInt(args[1]);
        isLeft = Boolean.parseBoolean(args[2]);

        org.junit.runner.JUnitCore.main("edu.mit.csail.cgs.projects.readdb.TestPairedHits");
    }
}