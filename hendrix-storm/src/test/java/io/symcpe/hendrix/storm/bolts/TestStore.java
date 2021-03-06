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
package io.symcpe.hendrix.storm.bolts;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.symcpe.wraith.actions.alerts.templated.AlertTemplate;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplateSerializer;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.store.RulesStore;
import io.symcpe.wraith.store.TemplateStore;

/**
 * Rules store for unit tests
 * 
 * @author ambud_sharma
 */
public class TestStore implements RulesStore, TemplateStore {

	private Map<Short, Rule> rules;
	private Map<Short, AlertTemplate> templates;

	public TestStore() {
		rules = new HashMap<>();
		templates = new HashMap<>();
	}

	@Override
	public void initialize(Map<String, String> conf) {
		String rulesJson = conf.get(TestAlertingEngineBolt.RULES_CONTENT);
		if (rulesJson != null) {
			Rule[] ruleList = RuleSerializer.deserializeJSONStringToRules(rulesJson);
			System.out.println("Rule content:" + rulesJson + "\t" + ruleList.length);
			for (Rule rule : ruleList) {
				rules.put(rule.getRuleId(), rule);
			}
		}
		String templateJson = conf.get(TestAlertingEngineBolt.TEMPLATE_CONTENT);
		if (templateJson != null) {
			AlertTemplate[] alertTemplates = AlertTemplateSerializer.deserializeArray(templateJson);
			for (AlertTemplate template : alertTemplates) {
				System.out.println("Template content:" + template + "\t" + alertTemplates.length);
				templates.put(template.getTemplateId(), template);
			}
		}
	}

	@Override
	public void connect() throws IOException {
	}

	@Override
	public void disconnect() throws IOException {
	}

	@Override
	public Map<Short, Rule> listRules() throws IOException {
		return rules;
	}

	@Override
	public Map<String, Map<Short, Rule>> listGroupedRules() throws IOException {
		return null;
	}

	@Override
	public Map<Short, AlertTemplate> getAllTemplates() throws IOException {
		return templates;
	}

}