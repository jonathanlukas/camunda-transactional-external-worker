package com.camunda.consulting;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.spring.boot.starter.ClientProperties;
import org.camunda.bpm.engine.ProcessEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ExternalTaskSubscription(topicName = "transaction1", lockDuration = 1000l)
public class Transaction1TaskHandler extends AbstractTransactionalTaskHandler
{
	@Autowired
	public Transaction1TaskHandler(ClientProperties config, ProcessEngine processEngine)
	{
		super(config, processEngine);
	}

	@Override
	protected long getMaxTasks()
	{
		return 1000l;
	}

	@Override
	protected String getProcessDefinitionKey()
	{
		return "test_process";
	}
}
