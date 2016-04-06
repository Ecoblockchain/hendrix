/**
 * Copyright 2016 Symantec Corporation.
 * 
 * Licensed under the Apache License, Version 2.0 (the “License”); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.symcpe.hendrix.storm;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.symcpe.hendrix.storm.SQLRulesStore;
import io.symcpe.wraith.actions.Action;
import io.symcpe.wraith.actions.alerts.AlertAction;
import io.symcpe.wraith.conditions.Condition;
import io.symcpe.wraith.conditions.relational.JavaRegexCondition;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.rules.SimpleRule;
import io.symcpe.wraith.store.RulesStore;

/**
 * Load rules into an Embedded Derby DB and validate whether or not rules can be
 * fetched back in their original form from the DB.
 * 
 * @author ambud_sharma
 */
public class TestSQLRulesStore {

	private static final String SCHEMA = "APP";
	private static final String TEST_RULE_TABLE = "testRules";
	private static final String CONNECTION_STRING = "jdbc:derby:target/rules.db;create=true";
	private static final String CONNECTION_NC_STRING = "jdbc:derby:target/rules.db;";
	private static final String TARGET_RULES_DB = "target/rules.db";
	private RulesStore store = new SQLRulesStore();

	public TestSQLRulesStore() {
		Map<String, String> conf = new HashMap<>();
		conf.put(SQLRulesStore.RSTORE_SQL_URL, CONNECTION_NC_STRING);
		conf.put(SQLRulesStore.RSTORE_SQL_DB, SCHEMA);
		conf.put(SQLRulesStore.RSTORE_SQL_TABLE, TEST_RULE_TABLE);
		store.initialize(conf);
	}

	@Before
	public void before() throws SQLException, IOException {
		System.setProperty("derby.stream.error.field", DerbyUtil.class.getCanonicalName() + ".DEV_NULL");
		File db = new File(TARGET_RULES_DB);
		if (db.exists()) {
			System.out.println("Deleting database");
			FileUtils.deleteDirectory(db);
		}
		String createTable = "create table testRules(" + SQLRulesStore.COLUMN_RULE_ID + " smallint primary key,"
				+ SQLRulesStore.COLUMN_RULE_CONTENT + " varchar(3000))";
		runSQL(CONNECTION_STRING, createTable);

		Condition condition = new JavaRegexCondition("tst", "\\d+");
		Action action = new AlertAction((short) 2, "test", "test", "test");
		Rule testRule = new SimpleRule((short) 1233, "testRule", true, condition, action);

		Connection conn = DriverManager.getConnection(CONNECTION_STRING);
		PreparedStatement insert = conn.prepareStatement("insert into testRules values(?, ?)");
		insert.setShort(1, testRule.getRuleId());
		insert.setString(2, RuleSerializer.serializeRuleToJSONString(testRule, false));
		insert.execute();
		conn.close();
	}

	@After
	public void after() throws IOException, SQLException {
		runSQL(CONNECTION_NC_STRING, "drop table testRules");
	}

	@Test
	public void testRuleLoading() throws IOException {
		store.connect();
		Map<Short, Rule> result = store.listRules();
		assertEquals(1, result.size());
		assertEquals((short) 1233, result.get((short) 1233).getRuleId());
		store.disconnect();
	}

	/**
	 * Test if SQL update of rule is loaded by RulesStore
	 * 
	 * @throws IOException
	 * @throws SQLException
	 */
	@Test
	public void testRuleUpdateLoading() throws IOException, SQLException {
		Condition condition = new JavaRegexCondition("tst", "\\d+");
		Action action = new AlertAction((short) 2, "test", "test", "test");
		Rule testRule = new SimpleRule((short) 1234, "testRule", true, condition, action);
		String ruleUpdate = RuleSerializer.serializeRuleToJSONString(testRule, false);
		String updateRule = "update " + TEST_RULE_TABLE + " set " + SQLRulesStore.COLUMN_RULE_ID + "=1234, "
				+ SQLRulesStore.COLUMN_RULE_CONTENT + "='" + ruleUpdate + "' where " + SQLRulesStore.COLUMN_RULE_ID
				+ "=1233";
		runSQL(CONNECTION_NC_STRING, updateRule);
		store.connect();
		Map<Short, Rule> result = store.listRules();
		assertEquals(1, result.size());
		System.out.println(result);
		assertEquals((short) 1234, result.get((short) 1234).getRuleId());
		store.disconnect();
	}

	/**
	 * Execute an SQL Query on a connection
	 * 
	 * @param connectionString
	 * @param createTable
	 * @throws SQLException
	 */
	public void runSQL(String connectionString, String createTable) throws SQLException {
		Connection conn = DriverManager.getConnection(connectionString);
		Statement st = conn.createStatement();
		System.err.println(createTable);
		st.execute(createTable);
		st.close();
		conn.close();
	}

}
