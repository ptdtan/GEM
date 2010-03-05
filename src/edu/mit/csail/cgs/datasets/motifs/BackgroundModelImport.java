/**
 * 
 */
package edu.mit.csail.cgs.datasets.motifs;

import java.io.*;
import java.sql.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import edu.mit.csail.cgs.tools.utils.Args;
import edu.mit.csail.cgs.utils.database.DatabaseException;
import edu.mit.csail.cgs.utils.database.DatabaseFactory;
import edu.mit.csail.cgs.utils.io.motifs.BackgroundModelIO;
import edu.mit.csail.cgs.utils.*;
import edu.mit.csail.cgs.datasets.species.Genome;
import edu.mit.csail.cgs.datasets.species.Organism;

/**
 * @author rca
 * Code for database interaction involving background models
 */
public class BackgroundModelImport {

  private static Logger logger = Logger.getLogger(BackgroundModelImport.class);
  
  /** 
   * Imports a weightmatrix from a file into the DB 
   *
   * Usage:
   * java edu.mit.csail.cgs.datasets.motifs.BackgroundModelImport --genome "Mus musculus;mm8" --bgname "whole genome" --bgtype MARKOV --bgfile foo.back
   */
  public static void main(String args[]) {
  	try {
  		Genome gen = null;
  		String bgName = null;
  		String bgType = null;
  		String bgFilename = null;
  		
  		args = new String[] {"--species", "Mus musculus;mm8", "--bgname", "whole genome", "--bgtype", "MARKOV", "--bgfile", "mm8.back"};

  		gen = Args.parseGenome(args).cdr();
  		bgName = Args.parseString(args, "bgname", null);
  		bgType = Args.parseString(args, "bgtype", null);
  		bgFilename = Args.parseString(args, "bgfile", null);

  		if (gen == null) {
  			logger.fatal("Must specify a genome in --species"); System.exit(1);
  		}
  		if (bgFilename == null) {
  			logger.fatal("Must supply a --bgfile"); System.exit(1);
  		} 
  		if (bgName == null) {
  			logger.fatal("Must supply a --bgname"); System.exit(1);
  		}
  		if (bgType == null) {
  			logger.fatal("Must supply a --bgtype"); System.exit(1);
  		}

  		if (bgType.toUpperCase().equals("MARKOV")) {
  			MarkovBackgroundModel mbg = BackgroundModelIO.parseMarkovBackgroundModel(bgName, bgFilename, gen);
  			BackgroundModelImport.insertMarkovModel(mbg);
  		}
  		else if (bgType.toUpperCase().equals("FREQUENCY")) {
  		  FrequencyBackgroundModel fbg = BackgroundModelIO.parseFreqBackgroundModel(bgName, bgFilename, gen);
  		  BackgroundModelImport.insertFrequencyModel(fbg);
  		}
      else if (bgType.toUpperCase().equals("COUNTS;MARKOV")) {
        CountsBackgroundModel cbg = BackgroundModelIO.parseCountsBackgroundModel(bgName, bgFilename, gen);
        BackgroundModelImport.insertCountsModel(cbg, true);
      }
      else if (bgType.toUpperCase().equals("COUNTS;FREQUENCY")) {
        CountsBackgroundModel cbg = BackgroundModelIO.parseCountsBackgroundModel(bgName, bgFilename, gen);
        BackgroundModelImport.insertCountsModel(cbg, false);
      }
      else {
        logger.fatal("Background type must be one of: Markov, Frequency, Counts;Markov, Counts;Frequency.");
        System.exit(1);
  		}
  	}
  	catch (NotFoundException nfex) {
  		logger.fatal(nfex);
  		System.exit(1);
  	}
  	catch (IOException ioex) {
  		logger.fatal(ioex);
      System.exit(1);
  	}
  	catch (ParseException pex) {
  		logger.fatal(pex);  		
      System.exit(1);
  	}
  	catch (SQLException sqlex) {
  		logger.fatal(sqlex);
      System.exit(1);
  	}
  	catch (CGSException cgsex) {
  		logger.fatal(cgsex);
  		System.exit(1);
  	}
  	System.exit(0);
  }
  
    
  /**
   * @see getBackgroundModelID(String, int, String, Connection)
   * Creates a database connection for the query
   */
  public static Integer getBackgroundModelID(String name, int kmerLen, String modelType) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelImport.getBackgroundModelID(name, kmerLen, modelType, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Look for a model with this name, kmerlen, and type and return its ID. 
   * 
   * @param name the name of the model
   * @param kmerLen the length of the longest kmer in the model
   * @param modelType the type of the model (FREQUENCY or MARKOV)
   * @param cxn an open database connection
   * @return the ID of the model, or null if there's no match
   * @throws SQLException
   */
  public static Integer getBackgroundModelID(String name, int kmerLen, String modelType, Connection cxn) throws SQLException {
    PreparedStatement getModelID = null;
    ResultSet rs = null;    
    try {
      getModelID = cxn.prepareStatement("select id from background_model where name = ? and max_kmer_len = ? and model_type = ?");
      getModelID.setString(1, name);
      getModelID.setInt(2, kmerLen);
      getModelID.setString(3, modelType);
      rs = getModelID.executeQuery();

      if (rs.next()) {
        Integer modelID = rs.getInt(1);
        rs.close();
        return modelID;
      }
      else {
        rs.close();
        return null;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getModelID != null) {
        getModelID.close();
      }
    }
  }
  
  
  /**
   * @see getBackgroundGenomeMapID(int, int, Connection)
   * Creates a database connection for the query
   */
  public static Integer getBackgroundGenomeMapID(int bgModelID, int genomeID) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelImport.getBackgroundGenomeMapID(bgModelID, genomeID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  /**
   * Look for an entry in the background model genome map for the specified
   * background model and genome, and return its ID
   * @param bgModelID the id of the model to look up
   * @param genomeID a genome for which there may be an instance of the model
   * @param cxn an open database connection
   * @return the background genome map ID of the model, or null if there's no match
   * @throws SQLException
   */
  public static Integer getBackgroundGenomeMapID(int bgModelID, int genomeID, Connection cxn) throws SQLException {
    PreparedStatement getBGGenomeMapID = null;
    ResultSet rs = null;
    try {
      getBGGenomeMapID = cxn.prepareStatement("select id from background_genome_map where bg_model_id = ? and genome_id = ?");
      getBGGenomeMapID.setInt(1, bgModelID);
      getBGGenomeMapID.setInt(2, genomeID);
      rs = getBGGenomeMapID.executeQuery();
      if (rs.next()) {
        return rs.getInt(1);
      }
      else {
        return null;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getBGGenomeMapID != null) {
        getBGGenomeMapID.close();
      }      
    }
  }
  
  
  /**************************************************************************
   * Methods for looking up which background models are in the database
   **************************************************************************/
  
  /**
   * 
   * @param modelID
   * @return
   * @throws SQLException
   */  
  public static BackgroundModelMetadata getBackgroundModel(int modelID) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelImport.getBackgroundModel(modelID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  public static BackgroundModelMetadata getBackgroundModel(int modelID, Connection cxn) throws SQLException {
    PreparedStatement getModel = null;
    ResultSet rs = null;
    try {
      getModel = cxn.prepareStatement("select name, kmerlen, model_type from background_model where id = ?");
      getModel.setInt(1, modelID);
      rs = getModel.executeQuery();
      if (rs.next()) {
        return new BackgroundModelMetadata(modelID, rs.getString(1), rs.getInt(2), rs.getString(3));
      }
      else {
        return null;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getModel != null) {
        getModel.close();
      }      
    }
  }
  
  
  public static BackgroundModelMetadata getBackgroundModelByMapID(int bggmID) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelImport.getBackgroundModelByMapID(bggmID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  public static BackgroundModelMetadata getBackgroundModelByMapID(int bggmID, Connection cxn) throws SQLException {
    PreparedStatement getModel = null;
    ResultSet rs = null;
    try {
      getModel = 
        cxn.prepareStatement("select bm.id, bm.name, bm.kmerlen, bm.model_type, bgm.genome_id"
            + " from background_model bm, background_genome_map bgm" 
            + " where bm.id = bgm.bg_model_id and bgm.id = ?");
      getModel.setInt(1, bggmID);
      rs = getModel.executeQuery();
      if (rs.next()) {
        return new BackgroundModelMetadata(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4), bggmID, rs.getInt(5));
      }
      else {
        return null;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getModel != null) {
        getModel.close();
      }      
    }
  }
  
  
  public static List<BackgroundModelMetadata> getAllBackgroundModels() throws SQLException {
    return BackgroundModelImport.getAllBackgroundModels(false);
  }
  
  
  public static List<BackgroundModelMetadata> getAllBackgroundModels(boolean ignoreGenome) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelImport.getAllBackgroundModels(ignoreGenome, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  

  public static List<BackgroundModelMetadata> getAllBackgroundModels(Connection cxn) throws SQLException {
    return BackgroundModelImport.getAllBackgroundModels(false, cxn);
  }

  
  public static List<BackgroundModelMetadata> getAllBackgroundModels(boolean ignoreGenome, Connection cxn) throws SQLException {
    PreparedStatement getAllModels = null;
    ResultSet rs = null;
    try {
      if (ignoreGenome) {
        getAllModels = cxn.prepareStatement("select id, name, kmerlen, model_type from background_model");
        rs = getAllModels.executeQuery();
        List<BackgroundModelMetadata> results = new ArrayList<BackgroundModelMetadata>();
        while (rs.next()) {        
          results.add(new BackgroundModelMetadata(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4)));
        }
        return results;
      }
      else {
        getAllModels = cxn.prepareStatement("select bm.id, bm.name, bm.kmerlen, bm.model_type, bggm.id, bggm.genome_id"
            + " from background_model bm, background_genome_map bggm"
            + " where bm.id = bggm.bg_model_id");
        rs = getAllModels.executeQuery();
        List<BackgroundModelMetadata> results = new ArrayList<BackgroundModelMetadata>();
        while (rs.next()) {        
          results.add(new BackgroundModelMetadata(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getInt(5), rs.getInt(6)));
        }
        return results;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getAllModels != null) {
        getAllModels.close();
      }      
    }
  }
  
  
  public static List<BackgroundModelMetadata> getBackgroundModelsForGenome(int genomeID) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelImport.getBackgroundModelsForGenome(genomeID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  public static List<BackgroundModelMetadata> getBackgroundModelsForGenome(int genomeID, Connection cxn) throws SQLException {
    PreparedStatement getGenomeModels = null;
    ResultSet rs = null;    
    try {
      getGenomeModels = 
        cxn.prepareStatement("select bm.id, bm.name, bm.kmerlen, bm.model_type, bgm.id"
            + " from background_model bm, background_genome_map bgm" 
            + " where bm.id = bgm.bg_model_id and bgm.genome_id = ?");
      getGenomeModels.setInt(1, genomeID);
      rs = getGenomeModels.executeQuery();
      List<BackgroundModelMetadata> results = new ArrayList<BackgroundModelMetadata>();
      while (rs.next()) {
        results.add(new BackgroundModelMetadata(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getInt(5), genomeID));
      }
      return results;
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getGenomeModels != null) {
        getGenomeModels.close();
      }      
    }
  }
  
  
  public static List<Integer> getGenomesForBackgroundModel(int modelID) throws SQLException{
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelImport.getGenomesForBackgroundModel(modelID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  public static List<Integer> getGenomesForBackgroundModel(int modelID, Connection cxn) throws SQLException {
    PreparedStatement getModels = null;
    ResultSet rs = null;
    try {
      getModels = cxn.prepareStatement("select genome_id from background_genome_map where bg_model_id = ?");
      getModels.setInt(1, modelID);
      List<Integer> results = new ArrayList<Integer>();
      rs = getModels.executeQuery();
      while (rs.next()) {
        results.add(rs.getInt(1));
      }
      return results;
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getModels != null) {
        getModels.close();
      }
    }
  }
  
  
  /**************************************************************************
   * 
   **************************************************************************/
  
  /**
   * 
   * @param bggmID
   * @return
   * @throws SQLException
   */

  public static boolean hasCounts(int bggmID) throws SQLException{
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelImport.hasCounts(bggmID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  public static boolean hasCounts(int bggmID, Connection cxn) throws SQLException {
    PreparedStatement checkCounts = null;
    ResultSet rs = null;
    try {
      //check if the model has any kmers with a null count. 
      checkCounts = cxn.prepareStatement("select kmer from background_model_cols bmc where bggm_id = ? and count is null");
      checkCounts.setInt(1, bggmID);
      rs = checkCounts.executeQuery();      
      if (rs.next()) {
        return false;
      }
      else {
        return true;
      }
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (checkCounts != null) {
        checkCounts.close();
      }
    }
  }
  
  
//  public static CountsBackgroundModel getCountsModel(String name, int kmerlen, String type, int genomeID) {
//    java.sql.Connection cxn = null;
//    try {
//      cxn = DatabaseFactory.getConnection("annotations");
//      return BackgroundModelImport.getCountsModel(bggmID, cxn);
//    }
//    finally {
//      DatabaseFactory.freeConnection(cxn);
//    }
//
//    BackgroundModelImport.getBackgroundGenomeMapID(bgModelID, genomeID)
//  }

  
  public static CountsBackgroundModel getCountsModel(int bggmID) throws SQLException {
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      return BackgroundModelImport.getCountsModel(bggmID, cxn);
    }
    finally {
      DatabaseFactory.freeConnection(cxn);
    }
  }
  
  
  public static CountsBackgroundModel getCountsModel(int bggmID, Connection cxn) throws SQLException {
    PreparedStatement getCounts = null;
    ResultSet rs = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      if (BackgroundModelImport.hasCounts(bggmID, cxn)) {
        BackgroundModelMetadata md = BackgroundModelImport.getBackgroundModelByMapID(bggmID, cxn);
        CountsBackgroundModel cbm = new CountsBackgroundModel(md.name, Organism.findGenome(md.genomeID), md.kmerlen);
        cbm.setDBID(bggmID);
        
        getCounts = cxn.prepareStatement("select kmer, count from background_model_cols where bggm_id = ?");
        getCounts.setInt(1, bggmID);
        rs = getCounts.executeQuery();        
        while (rs.next()) {
          cbm.setKmerCount(rs.getString(1), rs.getLong(2));
        }
        return cbm;
      }
      else {
        return null;
      }
    }
    catch (NotFoundException nfex) {
      throw new DatabaseException("Error loading genome for model", nfex);
    }
    finally {
      if (rs != null) {
        rs.close();
      }
      if (getCounts != null) {
        getCounts.close();
      }
    }
  }
  
  
  private static CountsBackgroundModel createCountsModel(ResultSet rs) throws SQLException {
    try {
      int bggmID = rs.getInt(1);
      int genomeID = rs.getInt(2);
      String name = rs.getString(3);
      int kmerlen = rs.getInt(4);
      CountsBackgroundModel cbm;
      cbm = new CountsBackgroundModel(name, Organism.findGenome(genomeID), kmerlen);
      cbm.setDBID(bggmID);
      cbm.setKmerCount(rs.getString(5), rs.getLong(6));
      while (rs.next() && (rs.getInt(1) == cbm.getDBID())) {
        cbm.setKmerCount(rs.getString(2), rs.getLong(3));
      }
      return cbm;    
    }
    catch (NotFoundException nfex) {
      throw new DatabaseException("Error loading genome for model", nfex);
    }
  }
  
  
  
  /**************************************************************************
   * Inserting and Updating code
   **************************************************************************/
  
  /**
   * Insert a markov background model into the database
   * @param model the model to insert
   * @return the DBID of the model
   * @throws SQLException
   */
  public static int insertMarkovModel(MarkovBackgroundModel model) throws SQLException, CGSException {
    //make sure the model has a name and genome
    if ((model.getName() == null) || (model.getName().isEmpty()) || (model.getGenome() == null)) {
      throw new IllegalArgumentException("Model must have a name and genome specified to be imported to database.");
    }
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      cxn.setAutoCommit(false);

      //insert into the background model and background
      int bggmID = BackgroundModelImport.insertBackgroundModelAndMap(model.getName(), model.getMaxKmerLen(), "FREQUENCY", model.getGenome().getDBID(), cxn);

      //Finally, insert all the "columns" of the background model
      BackgroundModelImport.insertMarkovModelColumns(model, bggmID, cxn);
      
      //If everything has worked then commit
      cxn.commit();

      //update the model with its new ID
      model.setDBID(bggmID);

      return bggmID;
    }
    catch (RuntimeException ex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw ex;
    }
    finally {
      if (cxn != null) {
        DatabaseFactory.freeConnection(cxn);
      }
    }
  }


  /**
   * insert a frequency background model into the database
   * @param model the model to insert
   * @return the DBID of the model
   * @throws SQLException
   */
  public static int insertFrequencyModel(FrequencyBackgroundModel model) throws SQLException, CGSException {
    //make sure the model has a name and genome
    if ((model.getName() == null) || (model.getName().isEmpty()) || (model.getGenome() == null)) {
      throw new IllegalArgumentException("Model must have a name and genome specified to be imported to database.");
    }
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      cxn.setAutoCommit(false);

      //insert into the background model and background
      int bggmID = BackgroundModelImport.insertBackgroundModelAndMap(model.getName(), model.getMaxKmerLen(), "FREQUENCY", model.getGenome().getDBID(), cxn);

      //insert all the "columns" of the background model
      BackgroundModelImport.insertFrequencyModelColumns(model, bggmID, cxn);

      //If everything has worked then commit
      cxn.commit();

      //update the model with its new ID
      model.setDBID(bggmID);

      return bggmID;
    }
    catch (RuntimeException ex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw ex;
    }
    finally {
      if (cxn != null) {
        DatabaseFactory.freeConnection(cxn);
      }
    }
  }
  

  /**
   * Insert a counts background model into the database
   * @param model the model to insert
   * @param insertAsMarkov indicates whether to insert the markov probabilities
   * or the frequencies corresponding to the counts 
   * @return the DBID of the model
   * @throws SQLException
   */
  public static int insertCountsModel(CountsBackgroundModel model, boolean insertAsMarkov) throws SQLException, CGSException {
    //make sure the model has a name and genome
    if ((model.getName() == null) || (model.getName().isEmpty()) || (model.getGenome() == null)) {
      throw new IllegalArgumentException("Model must have a name and genome specified to be imported to database.");
    }
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      cxn.setAutoCommit(false);

      String modelType;
      if (insertAsMarkov) {
        modelType = "MARKOV";
      }
      else {
        modelType = "FREQUENCY";
      }
      
      //insert into the background model and background
      int bggmID = BackgroundModelImport.insertBackgroundModelAndMap(model.getName(), model.getMaxKmerLen(), modelType, model.getGenome().getDBID(), cxn);

      //insert all the "columns" of the background model
      BackgroundModelImport.insertCountsModelColumns(model, bggmID, insertAsMarkov, cxn);

      //If everything has worked then commit
      cxn.commit();

      //update the model with its new ID
      model.setDBID(bggmID);

      return bggmID;
    }
    catch (RuntimeException ex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw ex;
    }
    finally {
      if (cxn != null) {
        DatabaseFactory.freeConnection(cxn);
      }
    }
  }
  
  
  /**
   * Update a markov background model already in the database
   * @param model the model to insert
   * @throws SQLException
   */
  public static void updateMarkovModel(MarkovBackgroundModel model) throws SQLException, CGSException {
    //make sure the model has a name and genome
    if (!model.hasDBID()) {
      throw new IllegalArgumentException("Model must already have a database ID to be updated in the database.");
    }
    
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      cxn.setAutoCommit(false);

      int bggmID = model.getDBID();
      //remove from the database all the existing entries for the model columns
      BackgroundModelImport.removeModelColumns(bggmID, cxn);
      
      //Insert all the "columns" of the background model
      BackgroundModelImport.insertMarkovModelColumns(model, bggmID, cxn);
      
      //If everything has worked then commit
      cxn.commit();
    }
    catch (RuntimeException ex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw ex;
    }
    finally {
      if (cxn != null) {
        DatabaseFactory.freeConnection(cxn);
      }
    }
  }

  
  /**
   * Update a frequency background model already in the database
   * @param model the model to insert
   * @throws SQLException
   */
  public static void updateFrequencyModel(FrequencyBackgroundModel model) throws SQLException, CGSException {
    //make sure the model has a name and genome
    if (!model.hasDBID()) {
      throw new IllegalArgumentException("Model must already have a database ID to be updated in the database.");
    }
    
    java.sql.Connection cxn = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      cxn.setAutoCommit(false);

      int bggmID = model.getDBID();
      //remove from the database all the existing entries for the model columns
      BackgroundModelImport.removeModelColumns(bggmID, cxn);
      
      //Insert all the "columns" of the background model
      BackgroundModelImport.insertFrequencyModelColumns(model, bggmID, cxn);
      
      //If everything has worked then commit
      cxn.commit();
    }
    catch (RuntimeException ex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw ex;
    }
    finally {
      if (cxn != null) {
        DatabaseFactory.freeConnection(cxn);
      }
    }
  }

  
  /**
   * Update a counts background model already in the database
   * @param model the model to insert
   * @throws SQLException
   */
  public static void updateCountsModel(CountsBackgroundModel model) throws SQLException, CGSException {
    //make sure the model has a name and genome
    if (!model.hasDBID()) {
      throw new IllegalArgumentException("Model must already have a database ID to be updated in the database.");
    }
    
    java.sql.Connection cxn = null;
    PreparedStatement getModelType = null;
    ResultSet rs = null;
    try {
      cxn = DatabaseFactory.getConnection("annotations");
      cxn.setAutoCommit(false);      
      
      int bggmID = model.getDBID();
      
      //determine whether this model exists in the database as a markov model or
      //a frequency model, so that it can be updated in the same format
      boolean isMarkov;
      getModelType = 
      	cxn.prepareStatement("select model_type from background_model bm, background_genome_map bgm where bggm.id = ? and bggm.bg_model_id = bm.id");
      getModelType.setInt(1, bggmID);
      rs = getModelType.executeQuery();
      if (rs.next()) {
      	isMarkov = rs.getString(1).equals("MARKOV");
      }
      else {
      	throw new DatabaseException("Unable to find Background Model in database.");
      }
      
      //remove from the database all the existing entries for the model columns
      BackgroundModelImport.removeModelColumns(bggmID, cxn);
      
      //Insert all the "columns" of the background model
      BackgroundModelImport.insertCountsModelColumns(model, bggmID, isMarkov, cxn);
      
      //If everything has worked then commit
      cxn.commit();
    }
    catch (RuntimeException ex) {
      //If any runtime exceptions come up rollback the transaction and then
      //rethrow the exception
      cxn.rollback();
      throw ex;
    }
    finally {
    	if (rs != null) {
    		rs.close();
    	}
    	if (getModelType != null) {
    		getModelType.close();
    	}
      if (cxn != null) {
        DatabaseFactory.freeConnection(cxn);
      }
    }
  }

  
  /**
   * insert entries for the model in the background model table and the
   * background model genome map table
   * @param name the name of the model
   * @param kmerLen the length of the longest kmer in the model
   * @param modelType the type of the model ("MARKOV" or "FREQUENCY")
   * @param genomeID the DBID of the genome the model is for
   * @param cxn an open database connection
   * @return the background genome map ID of the model
   * @throws SQLException
   */
  private static Integer insertBackgroundModelAndMap(String name, int kmerLen, String modelType, int genomeID, Connection cxn) throws SQLException, CGSException {
    /**
     * Check whether there is already an entry for a model with this name, kmerlen, and type. If so, reuse the model ID, 
     * otherwise create one.
     */
    Integer modelID = BackgroundModelImport.getBackgroundModelID(name, kmerLen, modelType, cxn);
    if (modelID == null) {
      modelID = BackgroundModelImport.insertBackgroundModel(name, kmerLen, modelType, cxn);
    }

    /**
     * Check whether there is already an entry for this model's genome in the
     * background model genome map. If not then create one, otherwise throw
     * an exception indicating that the update method should be called to
     * update an existing model.
     */
    Integer bggmID = BackgroundModelImport.getBackgroundGenomeMapID(modelID, genomeID, cxn);
    if (bggmID != null) {
      throw new CGSException("Model already exists. Select a different name or use updateMarkovModel() to update the existing model.");
    }
    else {
      bggmID = BackgroundModelImport.insertBackgroundGenomeMap(modelID, genomeID, cxn);
    }

    return bggmID;
  }


  /**
   * insert the model in the the background model table
   * @param name the name of the model
   * @param kmerLen the length of the longest kmer in the model
   * @param modelType the type of the model ("MARKOV" or "FREQUENCY")
   * @param cxn an open database connection
   * @return the background model ID 
   * @throws SQLException
   */
  private static Integer insertBackgroundModel(String name, int kmerLen, String modelType, Connection cxn) throws SQLException {
    PreparedStatement insertBG = cxn.prepareStatement("insert into background_model(id,name,max_kmer_len,model_type) values (weightmatrix_id.nextval,?,?,'MARKOV')");
    insertBG.setString(1, name);
    insertBG.setInt(2, kmerLen);
    insertBG.setString(3, modelType);
    insertBG.execute();
    insertBG.close();
    PreparedStatement getModelID = cxn.prepareStatement("select weightmatrix_id.currval from dual");
    ResultSet rs = getModelID.executeQuery();
    try {
      if (rs.next()) {
        return rs.getInt(1);
      }
      else {
        throw new SQLException("Failed to get Model ID following insert into Background_Model table.");
      }    
    }
    finally {
      rs.close();
      getModelID.close();
    }
  }
  
  
  /**
   * insert the model in the background model genome map table
   * @param modelID the background model table ID of the model
   * @param genomeID the DBID of the genome the model is for
   * @param cxn an open database connection
   * @return the
   * @throws SQLException
   */
  private static Integer insertBackgroundGenomeMap(int modelID, int genomeID, Connection cxn) throws SQLException {
    PreparedStatement insertBGGM = 
      cxn.prepareStatement("insert into background_genome_map(id, genome_id, bg_model_id) values (bggm_id.nextval, ?, ?)");
    insertBGGM.setInt(1, modelID);
    insertBGGM.setInt(2, genomeID);
    insertBGGM.execute();
    insertBGGM.close();
    PreparedStatement getBGGMID = cxn.prepareStatement("select bggm_id.currval from dual");
    ResultSet rs = getBGGMID.executeQuery();
    try {
      if (rs.next()) {
        return rs.getInt(1);
      }
      else {
        throw new SQLException("Failed to get Background Model Genome Map ID following insert into Background_Genome_Map table.");
      }
    }
    finally {
      rs.close();
      getBGGMID.close();
    }
  }
  
  
  /**
   * remove the model's kmer probabilities 
   * @param bggmID the model's ID in the background genome map table
   * @param cxn an open database connection
   * @throws SQLException
   */
  private static void removeModelColumns(int bggmID, Connection cxn) throws SQLException {
  	PreparedStatement deleteOld = cxn.prepareStatement("delete from background_model_cols where bggm_id = ?");
    try {
      deleteOld.setInt(1,bggmID);
      deleteOld.execute();
    }
    finally {
      deleteOld.close();
    }
  }
  
  
  /**
   * insert the model's kmer probabilities 
   * @param model the background model
   * @param bggmID the model's ID in the background genome map table
   * @param cxn an open database connection
   * @throws SQLException
   */
  private static void insertMarkovModelColumns(MarkovBackgroundModel model, int bggmID, Connection cxn) throws SQLException {
    PreparedStatement insertCol = cxn.prepareStatement("insert into background_model_cols(bggm_id,kmer,probability) values(?,?,?)");
    try {
      for (int i = 1; i <= model.getMaxKmerLen(); i++) {
        for (String kmer : model.getKmers(i)) {
          double prob = model.getMarkovProb(kmer);

          insertCol.setInt(1, bggmID);
          insertCol.setString(2, kmer);
          insertCol.setDouble(3, prob);
          insertCol.execute();
        }
      }
    }
    finally {
      insertCol.close();
    }
  }
  
  
  /**
   * insert the model's kmer probabilities 
   * @param model the background model
   * @param bggmID the model's ID in the background genome map table
   * @param cxn an open database connection
   * @throws SQLException
   */
  private static void insertFrequencyModelColumns(FrequencyBackgroundModel model, int bggmID, Connection cxn) throws SQLException {
    PreparedStatement insertCol = cxn.prepareStatement("insert into background_model_cols(bggm_id,kmer,probability) values(?,?,?)");
    try {
      for (int i = 1; i <= model.getMaxKmerLen(); i++) {
        for (String kmer : model.getKmers(i)) {
          double prob = model.getFrequency(kmer);

          insertCol.setInt(1, bggmID);
          insertCol.setString(2, kmer);
          insertCol.setDouble(3, prob);
          insertCol.execute();
        }
      }
    }
    finally {
      insertCol.close();
    }
  }
  

  /**
   * insert the model's kmer probabilities 
   * @param model the background model
   * @param bggmID the model's ID in the background genome map table
   * @param insertAsMarkov if true then insert the markov probabilities, if
   * false then insert the kmer frequencies
   * @param cxn an open database connection
   * @throws SQLException
   */
  private static void insertCountsModelColumns(CountsBackgroundModel model, int bggmID, boolean insertAsMarkov, Connection cxn) throws SQLException {
    PreparedStatement insertCol = cxn.prepareStatement("insert into background_model_cols(bggm_id,kmer,probability,count) values(?,?,?,?)");
    try {
      for (int i = 1; i <= model.getMaxKmerLen(); i++) {
        for (String kmer : model.getKmers(i)) {
          double prob;
          if (insertAsMarkov) {
            prob = model.getMarkovProb(kmer);
          }
          else {
            prob = model.getFrequency(kmer);
          }          

          insertCol.setInt(1, bggmID);
          insertCol.setString(2, kmer);
          insertCol.setDouble(3, prob);
          insertCol.setLong(4, model.getKmerCount(kmer));
          insertCol.execute();
        }
      }
    }
    finally {
      insertCol.close();
    }
  }
}
