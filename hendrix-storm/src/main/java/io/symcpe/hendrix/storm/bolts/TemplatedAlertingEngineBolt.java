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
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.RuntimeSingleton;
import org.apache.velocity.runtime.parser.ParseException;
import org.apache.velocity.runtime.parser.node.SimpleNode;

import com.google.gson.Gson;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import io.symcpe.hendrix.storm.Constants;
import io.symcpe.hendrix.storm.StormContextUtil;
import io.symcpe.hendrix.storm.UnifiedFactory;
import io.symcpe.hendrix.storm.Utils;
import io.symcpe.wraith.Event;
import io.symcpe.wraith.actions.alerts.Alert;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplate;
import io.symcpe.wraith.actions.alerts.templated.AlertTemplateSerializer;
import io.symcpe.wraith.actions.alerts.templated.TemplateCommand;
import io.symcpe.wraith.actions.alerts.templated.TemplatedAlertEngine;
import io.symcpe.wraith.store.StoreFactory;
import io.symcpe.wraith.store.TemplateStore;

/**
 * {@link TemplatedAlertEngine} implementation in form of a Storm
 * {@link BaseRichBolt} . This bolt is responsible for converting an alert
 * generated by RulesEngineBolt to an actual user facing alert by materializing
 * the Velocity template body of the actual alert configuration.
 * 
 * @author ambud_sharma
 */
public class TemplatedAlertingEngineBolt extends BaseRichBolt implements TemplatedAlertEngine {

	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger(TemplatedAlertingEngineBolt.class.getName());
	private transient Map<Short, VelocityAlertTemplate> templateMap;
	private transient OutputCollector collector;
	private transient StoreFactory storeFactory;
	private transient RuntimeServices runtimeServices;
	private transient Gson gson;
	private transient boolean multiTenancyActive;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.multiTenancyActive = Boolean
				.parseBoolean(stormConf.getOrDefault(Constants.RULE_GROUP_ACTIVE, Constants.FALSE).toString());
		this.collector = collector;
		this.gson = new Gson();
		this.templateMap = new HashMap<>();
		this.storeFactory = new UnifiedFactory();
		this.runtimeServices = RuntimeSingleton.getRuntimeServices();
		try {
			initializeTemplates(runtimeServices, templateMap, storeFactory, stormConf);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to initialize templates for alerts", e);
			throw new RuntimeException(e);
		}
		logger.info("Templated alerting Engine Bolt initialized");
	}

	@Override
	public void execute(Tuple tuple) {
		if (Utils.isTemplateSyncTuple(tuple)) {
			logger.info(
					"Attempting to apply template update:" + tuple.getValueByField(Constants.FIELD_TEMPLATE_CONTENT));
			TemplateCommand templateCommand = (TemplateCommand) tuple.getValueByField(Constants.FIELD_TEMPLATE_CONTENT);
			try {
				logger.info("Received template tuple with template content:" + templateCommand.getTemplate());
				updateTemplate(templateCommand.getRuleGroup(), templateCommand.getTemplate(),
						templateCommand.isDelete());
				logger.info("Applied template update with template content:" + templateCommand.getTemplate());
			} catch (Exception e) {
				// failed to update template
				System.err.println("Failed to apply template update:" + e.getMessage() + "\t"
						+ tuple.getValueByField(Constants.FIELD_TEMPLATE_CONTENT));
				StormContextUtil.emitErrorTuple(collector, tuple, TemplatedAlertingEngineBolt.class, tuple.toString(),
						"Failed to apply template update", e);
			}
		} else {
			Alert alertResult = null;
			if (multiTenancyActive) {
				alertResult = materialize((Event) tuple.getValueByField(Constants.FIELD_EVENT),
						tuple.getStringByField(Constants.FIELD_RULE_GROUP),
						tuple.getShortByField(Constants.FIELD_RULE_ID),
						tuple.getShortByField(Constants.FIELD_ACTION_ID),
						tuple.getStringByField(Constants.FIELD_RULE_NAME),
						tuple.getShortByField(Constants.FIELD_ALERT_TEMPLATE_ID),
						tuple.getLongByField(Constants.FIELD_TIMESTAMP));
			} else {
				alertResult = materialize((Event) tuple.getValueByField(Constants.FIELD_EVENT),
						tuple.getShortByField(Constants.FIELD_RULE_ID),
						tuple.getShortByField(Constants.FIELD_ACTION_ID),
						tuple.getStringByField(Constants.FIELD_RULE_NAME),
						tuple.getShortByField(Constants.FIELD_ALERT_TEMPLATE_ID),
						tuple.getLongByField(Constants.FIELD_TIMESTAMP));
			}
			if (alertResult == null) {
				String eventJson = gson.toJson(((Event) tuple.getValueByField(Constants.FIELD_EVENT)).getHeaders());
				StormContextUtil.emitErrorTuple(collector, tuple, TemplatedAlertingEngineBolt.class,
						"Failed to materialize alert into template due to missing templates", eventJson, null);
			} else {
				collector.emit(Constants.ALERT_STREAM_ID, tuple, new Values(gson.toJson(alertResult)));
			}
		}
		collector.ack(tuple);
	}

	@Override
	public Alert materialize(Event event, String ruleGroup, short ruleId, short actionId, String ruleName,
			short templateId, long timestamp) {
		Alert alert = materialize(event, ruleId, actionId, ruleName, templateId, timestamp);
		if (alert != null) {
			alert.setRuleGroup(ruleGroup);
		}
		return alert;
	}

	@Override
	public Alert materialize(Event event, short ruleId, short actionId, String ruleName, short templateId,
			long timestamp) {
		Alert alert = new Alert();
		VelocityAlertTemplate template = templateMap.get(templateId);
		if (template != null) {
			VelocityContext ctx = new VelocityContext();
			for (Entry<String, Object> entry : event.getHeaders().entrySet()) {
				ctx.put(entry.getKey(), entry.getValue());
			}
			StringWriter writer = new StringWriter(1000);
			template.getVelocityBodyTemplate().merge(ctx, writer);
			alert.setBody(writer.toString());
			if (template.getSubject() == null) {
				alert.setSubject("");
			} else {
				writer = new StringWriter(1000);
				template.getVelocitySubjectTemplate().merge(ctx, writer);
				alert.setSubject(writer.toString());
			}
			alert.setTarget(template.getDestination());
			alert.setMedia(template.getMedia());
			alert.setId(template.getTemplateId());
			alert.setTimestamp(timestamp);
			return alert;
		} else {
			return null;
		}
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declareStream(Constants.ALERT_STREAM_ID, new Fields(Constants.FIELD_ALERT));
		StormContextUtil.declareErrorStream(declarer);
	}

	@Override
	public void updateTemplate(String ruleGroup, String templateJson, boolean delete) {
		try {
			AlertTemplate template = AlertTemplateSerializer.deserialize(templateJson);
			if (delete) {
				templateMap.remove(template.getTemplateId());
			} else {
				buildTemplateMap(runtimeServices, templateMap, template);
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Alert template error", e);
		}
	}

	/**
	 * Connects to {@link TemplateStore} and fetches all the templates and loads
	 * them into the memory {@link Map} for quick lookups
	 * 
	 * @param conf
	 * @throws Exception
	 */
	public static void initializeTemplates(RuntimeServices runtimeServices,
			Map<Short, VelocityAlertTemplate> templateMap, StoreFactory storeFactory, Map<String, String> conf)
			throws Exception {
		Properties props = new Properties();
		props.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogChute");

		Velocity.init(props);
		TemplateStore store = null;
		try {
			System.out.println(conf.get(Constants.TSTORE_TYPE));
			store = storeFactory.getTemplateStoreStore(conf.get(Constants.TSTORE_TYPE), conf);
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			throw e;
		}
		try {
			store.connect();
			Map<Short, AlertTemplate> templates = store.getAllTemplates();
			logger.info("Fetched " + templates.size() + " alert templates from the store");
			for (AlertTemplate template : templates.values()) {
				try {
					buildTemplateMap(runtimeServices, templateMap, template);
				} catch (ParseException e) {
					// TODO log ignore template
				}
			}
			store.disconnect();
		} catch (IOException e) {
			throw e;
		}
	}

	/**
	 * Builds {@link AlertTemplate} {@link Map} used for lookups.
	 * 
	 * @param template
	 * @throws ParseException
	 */
	public static void buildTemplateMap(RuntimeServices runtimeServices, Map<Short, VelocityAlertTemplate> templateMap,
			AlertTemplate template) throws ParseException {
		StringReader reader = new StringReader(template.getBody());
		SimpleNode node = runtimeServices.parse(reader, String.valueOf(template.getTemplateId()) + "_body");
		Template velocityTemplate = new Template();
		velocityTemplate.setRuntimeServices(runtimeServices);
		velocityTemplate.setData(node);
		velocityTemplate.initDocument();
		VelocityAlertTemplate alertTemplate = new VelocityAlertTemplate(template);
		alertTemplate.setVelocityBodyTemplate(velocityTemplate);

		reader = new StringReader(template.getSubject());
		node = runtimeServices.parse(reader, String.valueOf(template.getTemplateId()) + "_subject");
		velocityTemplate = new Template();
		velocityTemplate.setRuntimeServices(runtimeServices);
		velocityTemplate.setData(node);
		velocityTemplate.initDocument();
		alertTemplate.setVelocitySubjectTemplate(velocityTemplate);
		templateMap.put(template.getTemplateId(), alertTemplate);
	}

	/**
	 * @return the templateMap
	 */
	protected Map<Short, VelocityAlertTemplate> getTemplateMap() {
		return templateMap;
	}

	/**
	 * @return the storeFactory
	 */
	protected StoreFactory getStoreFactory() {
		return storeFactory;
	}

}