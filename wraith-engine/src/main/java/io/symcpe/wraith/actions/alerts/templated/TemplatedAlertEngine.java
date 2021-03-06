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
package io.symcpe.wraith.actions.alerts.templated;

import io.symcpe.wraith.Event;
import io.symcpe.wraith.actions.alerts.Alert;

/**
 * @author ambud_sharma
 */
public interface TemplatedAlertEngine {

	/**
	 * To support template based alerts
	 * 
	 * @param event
	 * @param ruleGroup
	 * @param ruleId
	 * @param actionId
	 * @param templateId
	 * @return
	 */
	public Alert materialize(Event event, String ruleGroup, short ruleId, short actionId, String ruleName, short templateId,
			long timestamp);

	/**
	 * Update it's internal data structures when the rule gets updated
	 * 
	 * @param ruleGroup
	 * @param templateJson
	 * @param delete
	 */
	public void updateTemplate(String ruleGroup, String templateJson, boolean delete);

	/**
	 * To support template based alerts
	 * 
	 * @param event
	 * @param ruleId
	 * @param actionId
	 * @param templateId
	 * @return
	 */
	public Alert materialize(Event event, short ruleId, short actionId, String ruleName, short templateId, long timestamp);

}
