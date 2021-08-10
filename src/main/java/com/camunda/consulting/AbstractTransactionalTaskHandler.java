package com.camunda.consulting;

import static java.util.stream.Collectors.*;
import static org.apache.commons.lang3.StringUtils.*;

import java.util.Map;
import java.util.UUID;

import org.camunda.bpm.client.spring.boot.starter.ClientProperties;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.rest.dto.PatchVariablesDto;
import org.camunda.bpm.engine.rest.dto.VariableValueDto;
import org.camunda.bpm.engine.rest.dto.runtime.PriorityDto;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

public abstract class AbstractTransactionalTaskHandler implements ExternalTaskHandler
{
	private final Logger log = LoggerFactory.getLogger(this.getClass());

	private final RestTemplate restTemplate = new RestTemplate();
	private final String baseUrl;
	private final ProcessEngine processEngine;

	public AbstractTransactionalTaskHandler(
		ClientProperties properties,
		ProcessEngine processEngine)
	{
		this.baseUrl = properties.getBaseUrl();
		this.processEngine = processEngine;
	}

	@Override
	public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService)
	{
		String transactionId = externalTask.getVariable("transaction_id");
		if (isEmpty(transactionId) && this.transactionCanBeOpened())
		{
			transactionId = this.createTransactionId(externalTask.getExecutionId());
			this.log(transactionId, "Created new transaction");
		}
		if (isNotEmpty(transactionId)
			&& (this.isActive(transactionId) == false)
			&& (this.isCompleted(transactionId) == false))
		{
			this
				.startTransaction(
					transactionId,
					Map.of("external_task_id", externalTask.getId(), "priority", externalTask.getPriority()));
			this.decreasePriority(externalTask);
			this.log(transactionId, "Started transaction handling");
		}
		if (isNotEmpty(transactionId)
			&& (this.isCompleted(transactionId)))
		{
			externalTaskService
				.complete(
					externalTask,
					Map.of(),
					this.processEngine
						.getHistoryService()
						.createHistoricVariableInstanceQuery()
						.processInstanceId(this.getHistoricProcessInstance(transactionId).getId())
						.list()
						.stream()
						.collect(toMap(HistoricVariableInstance::getName, HistoricVariableInstance::getValue)));
			this.log(transactionId, "Completed transaction");
		}
	}

	private String createTransactionId(String executionId)
	{
		String transactionId = UUID.randomUUID().toString();
		while (this.alreadyInUse(transactionId))
		{
			transactionId = UUID.randomUUID().toString();
		}
		String url = String
			.join("/", this.baseUrl, "execution", executionId, "localVariables");
		VariableValueDto transactionValue = new VariableValueDto();
		transactionValue.setValue(transactionId);
		transactionValue.setType("String");
		PatchVariablesDto body = new PatchVariablesDto();
		body.setModifications(Map.of("transaction_id", transactionValue));
		this.restTemplate.execute(url, HttpMethod.POST, this.restTemplate.httpEntityCallback(body), null);
		return transactionId;
	}

	private boolean alreadyInUse(String transactionId)
	{
		return (this.processEngine
			.getRuntimeService()
			.createProcessInstanceQuery()
			.processInstanceBusinessKey(transactionId)
			.count() != 0)
			||
			(this.processEngine
				.getHistoryService()
				.createHistoricProcessInstanceQuery()
				.processInstanceBusinessKey(transactionId)
				.count() != 0);
	}

	private boolean isActive(String transactionId)
	{
		return this.processEngine
			.getRuntimeService()
			.createProcessInstanceQuery()
			.processInstanceBusinessKey(transactionId)
			.processDefinitionKey(this.getProcessDefinitionKey())
			.singleResult() != null;
	}

	private boolean isCompleted(String transactionId)
	{
		return this.getHistoricProcessInstance(transactionId) != null;
	}

	private HistoricProcessInstance getHistoricProcessInstance(String transactionId)
	{
		return this.processEngine
			.getHistoryService()
			.createHistoricProcessInstanceQuery()
			.processInstanceBusinessKey(transactionId)
			.processDefinitionKey(this.getProcessDefinitionKey())
			.completed()
			.singleResult();
	}

	private ProcessInstance startTransaction(String transactionId, Map<String, Object> variables)
	{
		return this.processEngine
			.getRuntimeService()
			.startProcessInstanceByKey(this.getProcessDefinitionKey(), transactionId, variables);
	}

	private boolean transactionCanBeOpened()
	{

		long count = this.processEngine
			.getRuntimeService()
			.createProcessInstanceQuery()
			.processDefinitionKey(this.getProcessDefinitionKey())
			.count();
		return count < this.getMaxTasks();
	}

	private void decreasePriority(ExternalTask externalTask)
	{
		String url = String
			.join("/", this.baseUrl, "external-task", externalTask.getId(), "priority");
		PriorityDto body = new PriorityDto();
		body.setPriority(Math.max(1, externalTask.getPriority() - 1));
		this.restTemplate.execute(url, HttpMethod.PUT, this.restTemplate.httpEntityCallback(body), null);
	}

	private final void log(String transactionId, String message)
	{
		this.log.info("{}: {}", transactionId, message);
	}

	protected abstract long getMaxTasks();

	protected abstract String getProcessDefinitionKey();
}
