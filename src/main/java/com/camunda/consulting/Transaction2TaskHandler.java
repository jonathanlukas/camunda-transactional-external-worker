package com.camunda.consulting;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.spring.boot.starter.ClientProperties;
import org.camunda.bpm.engine.ProcessEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ExternalTaskSubscription(topicName = "transaction2", lockDuration = 1000l)
public class Transaction2TaskHandler extends AbstractTransactionalTaskHandler
{
	@Autowired
	public Transaction2TaskHandler(ClientProperties config, ProcessEngine processEngine)
	{
		super(config, processEngine);
	}

	@Override
	protected long getMaxTasks()
	{
		return 10l;
	}

	@Override
	protected String getProcessDefinitionKey()
	{
		return "test_process_2";
	}
}
