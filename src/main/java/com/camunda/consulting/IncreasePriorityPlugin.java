package com.camunda.consulting;

import org.camunda.bpm.client.spring.boot.starter.ClientProperties;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.camunda.bpm.engine.rest.dto.runtime.PriorityDto;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class IncreasePriorityPlugin extends AbstractProcessEnginePlugin
{
	private final ClientProperties props;

	public IncreasePriorityPlugin(ClientProperties props)
	{
		this.props = props;
	}

	@Override
	public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration)
	{
		processEngineConfiguration.getCustomPreBPMNParseListeners().add(new AbstractBpmnParseListener()
		{
			@Override
			public void parseEndEvent(Element endEventElement, ScopeImpl scope, ActivityImpl activity)
			{
				scope.addListener(ExecutionListener.EVENTNAME_END, new ExecutionListener()
				{
					private final RestTemplate restTemplate = new RestTemplate();

					@Override
					public void notify(DelegateExecution execution) throws Exception
					{
						String url = String
							.join(
								"/",
								IncreasePriorityPlugin.this.props.getBaseUrl(),
								"external-task",
								(String) execution.getVariable("external_task_id"),
								"priority");
						PriorityDto body = new PriorityDto();
						body.setPriority(Math.max(1, (long) execution.getVariable("priority") + 2));
						this.restTemplate
							.execute(url, HttpMethod.PUT, this.restTemplate.httpEntityCallback(body), null);

					}

				});
			}
		});
	}
}
