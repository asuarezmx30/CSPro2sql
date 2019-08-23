package cspro2sql;

import cspro2sql.bean.Answer;
import cspro2sql.bean.Concepts;
import cspro2sql.bean.ConnectionParams;
import cspro2sql.bean.Dictionary;
import cspro2sql.bean.DictionaryInfo;
import cspro2sql.bean.Item;
import cspro2sql.bean.Questionnaire;
import cspro2sql.bean.Record;
import cspro2sql.reader.DictionaryReader;
import cspro2sql.reader.QuestionnaireReader;
import cspro2sql.sql.DictionaryQuery;
import cspro2sql.sql.PreparedStatementManager;
import cspro2sql.utils.Utility;
import cspro2sql.writer.DeleteWriter;
import cspro2sql.writer.InsertWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Copyright 2017 ISTAT
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * Licence for the specific language governing permissions and limitations under
 * the Licence.
 *
 * @author Guido Drovandi <drovandi @ istat.it>
 * @author Mauro Bruno <mbruno @ istat.it>
 * @version 0.9.18.2
 */
public class LoaderEngine {

    private static final Logger LOGGER = Logger.getLogger(LoaderEngine.class.getName());
    private static final int MAX_COMMIT_SIZE = 1000;

    public static void main(String[] args) {
        Properties prop = new Properties();

        try (InputStream in = LoaderEngine.class.getResourceAsStream("/database.properties")) {
            prop.load(in);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Cannot read properties file", ex);
            return;
        }
        try {
            List<Dictionary> dictionaries = DictionaryReader.parseDictionaries(
                    prop.getProperty("db.dest.schema").trim(),
                    prop.getProperty("dictionary").trim(),
                    prop.getProperty("dictionary.prefix").trim());

            execute(dictionaries, prop, true, false, false, true, false, null);
        } catch (Exception ex) {
            System.exit(1);
        }
    }

    static boolean execute(List<Dictionary> dictionaries, Properties prop, boolean allRecords, boolean checkConstraints, boolean checkOnly, boolean force, boolean recovery, PrintStream out) {
        boolean errors = false;
        SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

        for (Dictionary dictionary : dictionaries) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
                String srcSchema = prop.getProperty("db.source.schema").trim();
                String srcDataTable = dictionary.getName();

                //Connect to CSPro database
                ConnectionParams sourceConnection = ConnectionParams.getSourceParams(prop);
                try (Connection connSrc = DriverManager.getConnection(sourceConnection.getUri(), sourceConnection.getUsername(), sourceConnection.getPassword())) {
                    connSrc.setReadOnly(true);
                    Long start, stop, chunkStart, chunkStop;
                    //Connect to Dashboard database
                    if ("sqlserver".equals(prop.getProperty("db.dest.type"))) {
                        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver").newInstance();
                    }
                    ConnectionParams destConnParams = ConnectionParams.getDestParams(prop);
                    try (Connection connDst = DriverManager.getConnection(destConnParams.getUri(), destConnParams.getUsername(), destConnParams.getPassword())) {
                        connDst.setAutoCommit(false);

                        DictionaryQuery dictionaryQuery = new DictionaryQuery(connDst);

                        start = System.currentTimeMillis();

                        DictionaryInfo dictionaryInfo = dictionaryQuery.getDictionaryInfo(srcDataTable);
                        int lastRevision = dictionaryInfo.getRevision();

                        int nextRevision;
                        byte[] firstGuid;
                        if (recovery && !force) {
                            firstGuid = dictionaryInfo.getLastGuid();
                            nextRevision = dictionaryInfo.getNextRevision();
                        } else {
                            firstGuid = null;
                            try (Statement stmt = connSrc.createStatement()) {
                                try (ResultSet r = stmt.executeQuery("select max(revision) from `" + srcSchema + "`." + srcDataTable)) {
                                    r.next();
                                    nextRevision = r.getInt(1);
                                }
                            }
                        }

                        dictionaryInfo.setNextRevision(nextRevision);
                        if ((dictionaryInfo = dictionaryQuery.run(dictionaryInfo, force, recovery)) == null) {
                            System.out.println("An instance of the LOADER is still runnning!");
                            return false;
                        }

                        //Load data from CSPro database
                        ResultSet result;
                        PreparedStatement selectQuestionnaire;
                        if (allRecords) {
                            selectQuestionnaire = connSrc.prepareStatement("select questionnaire, guid, deleted from `" + srcSchema + "`." + srcDataTable + " order by guid limit " + MAX_COMMIT_SIZE);
                            result = selectQuestionnaire.executeQuery();
                            selectQuestionnaire = connSrc.prepareStatement("select questionnaire, guid, deleted from `" + srcSchema + "`." + srcDataTable + " where guid > ? order by guid limit " + MAX_COMMIT_SIZE);
                            System.out.println(SDF.format(new Date(System.currentTimeMillis())) + " Starting data transfer from CsPro/" + dictionary.getName() + " to MySql... [all records]");
                        } else {
                            if (firstGuid == null) {
                                selectQuestionnaire = connSrc.prepareStatement("select questionnaire, guid, deleted from `" + srcSchema + "`." + srcDataTable + " where revision > ? AND revision <= ? order by guid limit " + MAX_COMMIT_SIZE);
                                selectQuestionnaire.setInt(1, lastRevision);
                                selectQuestionnaire.setInt(2, nextRevision);
                            } else {
                                selectQuestionnaire = connSrc.prepareStatement("select questionnaire, guid, deleted from `" + srcSchema + "`." + srcDataTable + " where guid > ? AND revision > ? AND revision <= ? order by guid limit " + MAX_COMMIT_SIZE);
                                selectQuestionnaire.setBytes(1, firstGuid);
                                selectQuestionnaire.setInt(2, lastRevision);
                                selectQuestionnaire.setInt(3, nextRevision);
                            }
                            result = selectQuestionnaire.executeQuery();
                            selectQuestionnaire = connSrc.prepareStatement("select questionnaire, guid, deleted from `" + srcSchema + "`." + srcDataTable + " where guid > ? AND revision > ? AND revision <= ? order by guid limit " + MAX_COMMIT_SIZE);
                            selectQuestionnaire.setInt(2, lastRevision);
                            selectQuestionnaire.setInt(3, nextRevision);
                            System.out.println(SDF.format(new Date(System.currentTimeMillis())) + " Starting data transfer from CsPro/" + dictionary.getName() + " to MySql... [" + lastRevision + " -> " + nextRevision + "]");
                        }

                        try (Statement stmtDst = connDst.createStatement()) {
                            stmtDst.executeQuery("SET unique_checks=0");
                            stmtDst.executeQuery("SET foreign_key_checks=0");

                            //Get list of tables to be updated and their max id
                            Map<String, Integer> tablesLastId = new LinkedHashMap<>();

                            setDictionaryTablesLastId(tablesLastId, dictionary, stmtDst);

                            //Clear prepared statements
                            PreparedStatementManager.clearRecordListMap();

                            int chunkCounter = 0;
                            chunkStart = start;
                            boolean chunkError = false;
                            List<Questionnaire> quests = new LinkedList<>();

                            while (result.next()) { //Cicle on CSPro questionnaires
                                String questionnaire = result.getString(1);
                                byte[] guid = result.getBytes(2);
                                boolean deleted = result.getInt(3) == 1;

                                //Get the microdata parsing CSPro plain text files according to its dictionary
                                Questionnaire microdata = QuestionnaireReader.parse(dictionary, questionnaire, dictionary.getSchema(), guid, deleted);
                                dictionaryInfo.incTotal();
                                dictionaryInfo.setLastGuid(guid);

                                if ((checkConstraints || checkOnly) && !microdata.isDeleted() && !microdata.checkValueSets()) {
                                    errors = true;
                                    chunkError = true;
                                    String msg = "Validation failed\n" + microdata.getCheckErrors();
                                    dictionaryQuery.writeError(dictionaryInfo, msg, microdata, "");
                                    dictionaryInfo.incErrors();
                                } else if (!checkOnly) {
                                    quests.add(microdata);
                                }

                                if (result.isLast()) {
                                    if (checkOnly) {
                                        System.out.print((chunkError) ? '-' : 'x');
                                    } else {
                                        chunkError = newCommitList(dictionary, quests, stmtDst, dictionaryQuery, dictionaryInfo, tablesLastId, out);
                                        dictionaryQuery.updateLoaded(dictionaryInfo);
                                        chunkCounter++;
                                        if (chunkError) {
                                            System.out.print('-');
                                            errors = true;
                                        } else {
                                            System.out.print('+');
                                        }
                                        if (chunkCounter % 20 == 0) {
                                            chunkStop = System.currentTimeMillis();
                                            System.out.print(" Loaded: " + dictionaryInfo.getLoaded() + " quests;");
                                            if (dictionaryInfo.getErrors() > 0) {
                                                System.out.print(" Detected errors in " + dictionaryInfo.getErrors() + " quests;");
                                            } else {
                                                System.out.print(" No errors detected;");
                                            }
                                            System.out.print(" Parsed " + dictionaryInfo.getTotal() + " CSPro quests;");
                                            System.out.print(" Time: " + Utility.convertMillis(chunkStop - chunkStart));
                                            System.out.println();
                                            chunkStart = chunkStop;
                                        }
                                    }
                                    quests.clear();
                                    result.close();
                                    chunkError = false;
                                    selectQuestionnaire.setBytes(1, guid);
                                    result = selectQuestionnaire.executeQuery();
                                }
                            }
                            System.out.println();

                            if (!checkOnly) {
                                dictionaryQuery.updateRevision(dictionaryInfo);
                            }

                            stmtDst.executeQuery("SET foreign_key_checks=1");
                            stmtDst.executeQuery("SET unique_checks=1");
                        }

                        stop = System.currentTimeMillis();

                        if (errors) {
                            System.out.println(SDF.format(new Date(System.currentTimeMillis())) + " Data transfer completed with ERRORS (check error table)!");
                        } else {
                            System.out.println(SDF.format(new Date(System.currentTimeMillis())) + " Data transfer completed!");
                        }
                        dictionaryInfo.printShort(System.out, stop - start);

                        if (!dictionaryQuery.stop(dictionaryInfo)) {
                            System.out.println("Impossible to set LOADER status to stop!");
                            errors = false;
                        }
                    }
                }
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException ex) {
                LOGGER.log(Level.SEVERE, "Database exception", ex);
            }
        }
        return !errors;
    }

    private static boolean commitList(Dictionary dictionary, List<Questionnaire> quests, Statement stmtDst,
            DictionaryQuery dictionaryQuery, DictionaryInfo dictionaryInfo, PrintStream out) throws SQLException {
        boolean error = false;
        int deleted = 0;
        int loaded = 0;
        try {
            StringBuilder script = out == null ? null : new StringBuilder();
            for (Questionnaire q : quests) {
                if (q.isDeleted()) {
                    DeleteWriter.create(q.getSchema(), dictionary, q, stmtDst, script);
                    deleted++;
                } else {
                    InsertWriter.create(q.getSchema(), dictionary, q, stmtDst, script);
                    loaded++;
                }
            }
            if (out != null) {
                out.println();
                out.println(script);
                out.println();
            }
            stmtDst.getConnection().commit();
        } catch (Exception e1) {
            stmtDst.getConnection().rollback();
            deleted = 0;
            loaded = 0;
            for (Questionnaire q : quests) {
                StringBuilder script = new StringBuilder();
                try {
                    if (q.isDeleted()) {
                        DeleteWriter.create(q.getSchema(), dictionary, q, stmtDst, script);
                    } else {
                        InsertWriter.create(q.getSchema(), dictionary, q, stmtDst, script);
                    }
                    stmtDst.getConnection().commit();
                    if (q.isDeleted()) {
                        deleted++;
                    } else {
                        loaded++;
                    }
                } catch (Exception e2) {
                    stmtDst.getConnection().rollback();
                    error = true;
                    String msg = "Impossible to load questionnaire - " + e2.getMessage();
                    dictionaryQuery.writeError(dictionaryInfo, msg, q, script.toString());
                    dictionaryInfo.incErrors();
                }
            }
        }
        dictionaryInfo.incLoaded(loaded);
        dictionaryInfo.incDeleted(deleted);
        return error;
    }

    private static boolean newCommitList(Dictionary dictionary, List<Questionnaire> quests, Statement stmtDst,
            DictionaryQuery dictionaryQuery, DictionaryInfo dictionaryInfo, Map<String, Integer> tablesLastId, PrintStream out) throws SQLException {

        boolean error = false;

        try {
            StringBuilder script = out == null ? null : new StringBuilder();
            //Delete 'deleted & updated questionnaires'
            DeleteWriter.create(dictionary.getSchema(), dictionary, quests, stmtDst, tablesLastId, false);
            //Insert all questionnaires
            InsertWriter.create(dictionary.getSchema(), dictionary, quests, stmtDst, tablesLastId, false, script, dictionaryQuery, dictionaryInfo);
            //Commit
            stmtDst.getConnection().commit();

        } catch (Exception e) {
            stmtDst.getConnection().rollback();
            error = true;
            StringBuilder script = new StringBuilder();
            //Delete 'deleted & updated questionnaires'
            DeleteWriter.create(dictionary.getSchema(), dictionary, quests, stmtDst, tablesLastId, true);
            //Clear record statements
            PreparedStatementManager.clearRecordListMap();
            //Get last stored ids
            setDictionaryTablesLastId(tablesLastId, dictionary, stmtDst);
            //Insert questionnaires one by one 
            InsertWriter.create(dictionary.getSchema(), dictionary, quests, stmtDst, tablesLastId, true, script, dictionaryQuery, dictionaryInfo);
        }
        return error;
    }

    private static void setDictionaryTablesLastId(Map<String, Integer> tableLastId, Dictionary dictionary, Statement stmt) throws SQLException {

        Integer conceptId = -1;
        String tableName;
        if (dictionary.hasTag(Dictionary.TAG_HOUSEHOLD)) {
            conceptId = Concepts.HOUSEHOLD_ID;
        } else if (dictionary.hasTag(Dictionary.TAG_LISTING)) {
            conceptId = Concepts.LISTING_ID;
        } else if (dictionary.hasTag(Dictionary.TAG_EXPECTED)) {
            conceptId = Concepts.EXPECTED_ID;
        }
        String selectSql = "SELECT table_name FROM " + dictionary.getSchema() + ".dashboard_meta_unit where concept_id = " + conceptId
                + " UNION SELECT unit.table_name FROM " + dictionary.getSchema() + ".dashboard_meta_unit as unit "
                + " JOIN " + dictionary.getSchema() + ".dashboard_meta_unit as parent on unit.parent_id = parent.id"
                + " WHERE parent.concept_id = " + conceptId;

        //System.out.println(selectSql);
        if (tableLastId.isEmpty()) {
            try (ResultSet executeQuery = stmt.executeQuery(selectSql)) {
                while (executeQuery.next()) {
                    tableName = executeQuery.getString(1);
                    if (tableName != null) {
                        tableLastId.put(tableName, null);
                    }
                }
            }
        }

        String selectMax;
        for (Map.Entry<String, Integer> entry : tableLastId.entrySet()) {
            selectMax = "SELECT MAX(ID) FROM " + dictionary.getSchema() + "." + entry.getKey();
            //System.out.println(selectMax);
            try (ResultSet executeQuery = stmt.executeQuery(selectMax)) {
                while (executeQuery.next()) {
                    entry.setValue(executeQuery.getInt(1));
                }
            }

        }
    }
}
