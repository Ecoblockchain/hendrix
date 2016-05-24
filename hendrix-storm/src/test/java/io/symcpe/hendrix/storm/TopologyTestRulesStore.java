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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;

import io.symcpe.hendrix.storm.bolts.TestAlertingEngineBolt;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplate;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplateSerializer;
import io.symcpe.wraith.rules.Rule;
import io.symcpe.wraith.rules.RuleCommand;
import io.symcpe.wraith.rules.RuleSerializer;
import io.symcpe.wraith.rules.SimpleRule;
import io.symcpe.wraith.store.RulesStore;
import io.symcpe.wraith.store.TemplateStore;

/**
 * @author ambud_sharma
 */
public class TopologyTestRulesStore implements RulesStore, TemplateStore {

	private Map<String, Map<Short, Rule>> rules;
	private Map<Short, AlertTemplate> templates;
	
	public TopologyTestRulesStore() {
		rules = new HashMap<>();
		templates = new HashMap<>();
	}
	
	@Override
	public void initialize(Map<String, String> conf) {
		String rulesJson = conf.get(TestAlertingEngineBolt.RULES_CONTENT);
		Gson gson = new Gson();
		if(rulesJson!=null) {
			RuleCommand[] commands = gson.fromJson(rulesJson, RuleCommand[].class); 
			for (RuleCommand command : commands) {
				if(!rules.containsKey(command.getRuleGroup())) {
					rules.put(command.getRuleGroup(), new HashMap<>());
				}
				SimpleRule[] rulesArray = RuleSerializer.deserializeJSONStringToRules(command.getRuleContent());
				System.err.println("Content:"+command.getRuleContent());
				for(SimpleRule rule:rulesArray) {
					rules.get(command.getRuleGroup()).put(rule.getRuleId(), rule);
				}
			}
		}
		String templateJson = conf.get(TestAlertingEngineBolt.TEMPLATE_CONTENT);
		if (templateJson != null) {
			System.out.println("Templates:"+templateJson);
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
		Map<Short, Rule> temp = new HashMap<>();
		for (Entry<String, Map<Short, Rule>> entry : rules.entrySet()) {
			temp.putAll(entry.getValue());
		}
		return temp;
	}

	@Override
	public Map<String, Map<Short, Rule>> listGroupedRules() throws IOException {
		return rules;
	}

	@Override
	public Map<Short, AlertTemplate> getAllTemplates() throws IOException {
		return templates;
	}

}
