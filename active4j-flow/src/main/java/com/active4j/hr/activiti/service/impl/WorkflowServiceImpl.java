package com.active4j.hr.activiti.service.impl;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.ExclusiveGateway;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricActivityInstanceQuery;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.identity.Authentication;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.repository.ProcessDefinitionQuery;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Comment;
import org.activiti.engine.task.IdentityLink;
import org.activiti.engine.task.IdentityLinkType;
import org.activiti.engine.task.Task;
import org.activiti.image.ProcessDiagramGenerator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.active4j.hr.activiti.dao.WorkflowDao;
import com.active4j.hr.activiti.entity.WorkflowBaseEntity;
import com.active4j.hr.activiti.entity.WorkflowMngEntity;
import com.active4j.hr.activiti.service.WorkflowBaseService;
import com.active4j.hr.activiti.service.WorkflowMngService;
import com.active4j.hr.activiti.service.WorkflowService;
import com.active4j.hr.activiti.util.ActivitiUtils;
import com.active4j.hr.core.shiro.ShiroUtils;
import com.active4j.hr.core.web.tag.PagerUtil;
import com.active4j.hr.core.web.tag.model.DataGrid;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.extern.slf4j.Slf4j;

/**
 * @title WorkflowServiceImpl.java
 * @description ????????? ??????
 * @time 2020???4???17??? ??????3:08:58
 * @author ?????????
 * @version 1.0
 */
@Service("workflowService")
@Transactional
@Slf4j
public class WorkflowServiceImpl implements WorkflowService {

	@Autowired
	private RepositoryService repositoryService;

	@Autowired
	private WorkflowMngService workflowMngService;

	@Autowired
	private RuntimeService runtimeService;

	@Autowired
	private TaskService taskService;

	@Autowired
	private HistoryService historyService;

	@Autowired
	private WorkflowBaseService workflowBaseService;

	/** ?????????????????? */
	@Autowired
	private ProcessDiagramGenerator processDiagramGenerator;
	
	@Autowired
	private WorkflowDao workflowDao;

	/**
	 * ????????????????????????
	 * 
	 * @param dataGrid
	 * @return
	 */
	public IPage<Deployment> findDeployList(DataGrid dataGrid) {
		Long allCounts = repositoryService.createDeploymentQuery()// ????????????????????????
				.count();

		int counts = allCounts.intValue();

		int pageSize = dataGrid.getRows();// ???????????????

		List<Deployment> list = repositoryService.createDeploymentQuery()// ????????????????????????
				.orderByDeploymenTime().desc()// ?????????????????? ??????
				// .list(); //????????????
				.listPage(PagerUtil.getFirstResult(dataGrid.getPage(), pageSize), pageSize); // ????????????

		Page<Deployment> lstResult = new Page<Deployment>(dataGrid.getPage(), pageSize, counts);
		lstResult.setRecords(list);

		return lstResult;
	}

	/**
	 * ????????????ID ????????????
	 * 
	 * @param id
	 * @throws Exception
	 */
	public void deleteDeploy(String id) throws Exception {
		repositoryService.deleteDeployment(id);
	}

	/**
	 * ????????????
	 * 
	 * @param name     ????????????
	 * @param category ????????????
	 * @param file     ???????????????
	 * @throws FileNotFoundException
	 */
	public void saveNewDeploy(String name, InputStream file) throws Exception {
		ZipInputStream zipInputStream = new ZipInputStream(file);
		Deployment deployment = repositoryService.createDeployment()// ??????????????????
				.name(name)// ??????????????????
				.addZipInputStream(zipInputStream)// ????????????
				.deploy();// ????????????
		/**
		 * ??????????????????????????????????????????????????????key???????????????????????????
		 */
		ProcessDefinition pd = repositoryService.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();

		QueryWrapper<WorkflowMngEntity> queryWrapper = new QueryWrapper<WorkflowMngEntity>();
		queryWrapper.eq("PROCESS_KEY", pd.getKey());
		List<WorkflowMngEntity> lstResult = workflowMngService.list(queryWrapper);
		if (null != lstResult && lstResult.size() > 0) {
			for (WorkflowMngEntity mng : lstResult) {
				mng.setStatus("2");
				workflowMngService.saveOrUpdate(mng);
			}
		}
	}

	/**
	 * ????????????????????????????????????????????? ?????????
	 * 
	 * @param name             ????????????
	 * @param isLastestVersion ????????????????????????
	 * @param dataGrid         ??????
	 * @return
	 */
	public IPage<ProcessDefinition> findProcessListByName(String name, Boolean isLastestVersion, DataGrid dataGrid) {
		ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
		if (StringUtils.isNotEmpty(name)) {
			query.processDefinitionNameLike(name);
		}
		if (null != isLastestVersion && isLastestVersion) {
			query.latestVersion();
		}
		// ??????????????????
		Long allCounts = query.count();

		int counts = allCounts.intValue();

		int pageSize = dataGrid.getRows();// ???????????????

		// ????????????
		List<ProcessDefinition> lst = query.orderByProcessDefinitionVersion().desc().listPage(PagerUtil.getFirstResult(dataGrid.getPage(), pageSize), pageSize); // ????????????

		IPage<ProcessDefinition> lstResult = new Page<ProcessDefinition>(dataGrid.getPage(), pageSize, counts);
		lstResult.setRecords(lst);

		return lstResult;
	}

	/**
	 * ??????????????????ID??????????????????
	 * 
	 * @param id
	 * @return
	 */
	public InputStream findWorkflowImage(String id) {
		ProcessDefinition pd = repositoryService.createProcessDefinitionQuery().processDefinitionId(id).singleResult();
		return repositoryService.getResourceAsStream(pd.getDeploymentId(), pd.getDiagramResourceName());
	}

	/**
	 * ????????????ID ????????????
	 * 
	 * @param id
	 * @throws Exception
	 */
	public void deleteDefine(String id) throws Exception {
		ProcessDefinition pd = repositoryService.createProcessDefinitionQuery().processDefinitionId(id).singleResult();

		repositoryService.deleteDeployment(pd.getDeploymentId());

		QueryWrapper<WorkflowMngEntity> queryWrapper = new QueryWrapper<WorkflowMngEntity>();
		queryWrapper.eq("PROCESS_DEFINE_ID", id);
		List<WorkflowMngEntity> lstResult = workflowMngService.list(queryWrapper);
		if (null != lstResult && lstResult.size() > 0) {
			for (WorkflowMngEntity mng : lstResult) {
				mng.setStatus("2");
				workflowMngService.saveOrUpdate(mng);
			}
		}

	}

	/**
	 * ??????????????????ID ??????
	 * 
	 * @param id
	 * @throws Exception
	 */
	public void deleteAll(String id) {
		ProcessDefinition pd = repositoryService.createProcessDefinitionQuery().processDefinitionId(id).singleResult();

		repositoryService.deleteDeployment(pd.getDeploymentId(), true);

		QueryWrapper<WorkflowMngEntity> queryWrapper = new QueryWrapper<WorkflowMngEntity>();
		queryWrapper.eq("PROCESS_DEFINE_ID", id);
		List<WorkflowMngEntity> lstResult = workflowMngService.list(queryWrapper);
		if (null != lstResult && lstResult.size() > 0) {
			for (WorkflowMngEntity mng : lstResult) {
				mng.setStatus("2");
				workflowMngService.saveOrUpdate(mng);
			}
		}
	}

	/**
	 * ????????????????????????????????????????????????
	 */
	public List<ProcessDefinition> findProcessDefineList() {
		List<ProcessDefinition> lst = repositoryService.createProcessDefinitionQuery().latestVersion().orderByDeploymentId().desc().list();

		return lst;
	}

	/**
	 * ??????????????????key???????????????????????????
	 * 
	 * @param key
	 * @return
	 */
	public ProcessDefinition findNewestProcessDefine(String key) {
		ProcessDefinition pd = repositoryService.createProcessDefinitionQuery().processDefinitionKey(key).latestVersion().singleResult();

		return pd;
	}

	/**
	 * ?????????????????????ID?????????????????????????????????, ???????????????ID
	 * 
	 * @param processDefinitionKey ???????????????key key?????????????????????????????????
	 * @param map                  ????????????
	 * @param businessKey          ??????ID
	 * @param flag                 ?????????????????????
	 * @param applyName            ?????????
	 */
	public void startProcessInstanceByKey(String processDefinitionKey, String businessKey, boolean flag, String applyName, Map<String, Object> map) {
		if (flag) {
			// ?????????????????????????????????????????????????????????????????????
			startProcessInstanceByKey(processDefinitionKey, map, businessKey);

			// ????????????
			Task task = taskService.createTaskQuery()//
					.taskAssignee(applyName)// ????????????????????????
					.processInstanceBusinessKey(businessKey).singleResult();

			taskService.complete(task.getId());

		} else {
			startProcessInstanceByKey(processDefinitionKey, map, businessKey);
		}
	}

	/**
	 * ?????????????????????ID?????????????????????????????????, ???????????????ID
	 * 
	 * @param processDefinitionKey ???????????????key key?????????????????????????????????
	 * @param variables            ????????????
	 * @param businessKey          ??????ID
	 */
	@Override
	public void startProcessInstanceByKey(String processDefinitionKey, Map<String, Object> variables, String businessKey) {
		variables.put("applyName", ShiroUtils.getSessionUserName());
		runtimeService.startProcessInstanceByKey(processDefinitionKey, businessKey, variables);
	}

	/**
	 * ????????????ID?????????????????????????????????
	 * 
	 * @param businesskey
	 * @return
	 */
	public List<Task> findTaskListByBusinessKey(String businesskey, String userName) {
		List<Task> lstTasks = new ArrayList<Task>();

		// ??????????????????
		List<ProcessInstance> lstPis = runtimeService.createProcessInstanceQuery().processInstanceBusinessKey(businesskey).list();
		// ?????????????????????ID???????????????????????????????????????????????????????????????Set????????????
		Set<String> st = new HashSet<String>();
		for (ProcessInstance pi : lstPis) {
			String piId = pi.getProcessInstanceId();
			st.add(piId);
		}

		for (String piId : st) {
			List<Task> lst = taskService.createTaskQuery().processInstanceId(piId).taskAssignee(userName).list();
			lstTasks.addAll(lst);
		}

		return lstTasks;
	}
	
	/**
	 * ????????????????????????????????????ID
	 * @param userName
	 * @param category ????????????????????????
	 * @return
	 */
	public IPage<WorkflowBaseEntity> findFinishedTaskByUserName(IPage<WorkflowBaseEntity> page, WorkflowBaseEntity base, String startTime, String endTime, String userName, String category){
		/*List<HistoricTaskInstance> lstHisTasks = historyService.createHistoricTaskInstanceQuery()
				.taskAssignee(userName) //???????????????
				.taskCategory(category) // ????????????????????????
				.finished()  //????????????
				.orderByTaskCreateTime().desc()//??????
				.list();
		
		//?????????????????????????????????????????????
		if(null != lstHisTasks && lstHisTasks.size() > 0) {
			Set<String> st = new HashSet<String>();
			for(HistoricTaskInstance task : lstHisTasks) {
				HistoricProcessInstance hisPi = historyService.createHistoricProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();
				//????????????key
				if(null != hisPi && StringUtils.isNotEmpty(hisPi.getBusinessKey())) {
					st.add(hisPi.getBusinessKey());
				}
			}
			return st.toArray();
		}*/
		
		//??????????????????sql????????????
		
		return workflowDao.findFinishedTaskByUserName(page, userName, base.getCategoryId(), base.getProjectNo(), base.getName(), base.getApplyName(), startTime, endTime);
	}

	/**
	 * ????????????ID?????????????????????????????????????????????????????????
	 * 
	 * @param businesskey
	 * @return
	 */
	public List<Comment> findCommentsListByBusinessKey(String businesskey) {
		List<Comment> lstComments = new ArrayList<Comment>();
		// ??????????????????
		List<HistoricProcessInstance> lstPis = historyService.createHistoricProcessInstanceQuery().processInstanceBusinessKey(businesskey).list();

		// ?????????????????????ID???????????????????????????????????????????????????????????????Set????????????
		Set<String> st = new HashSet<String>();
		for (HistoricProcessInstance pi : lstPis) {
			String piId = pi.getId();
			st.add(piId);
		}

		for (String piId : st) {
			List<Comment> lst = taskService.getProcessInstanceComments(piId);
			if (null != lst) {
				lstComments.addAll(lst);
			}
		}

		return lstComments;
	}

	/**
	 * ??????????????????ID????????????????????????
	 * 
	 * @param businessKey
	 * @return
	 */
	public InputStream findImageProcess(String businessKey) {
		// ????????????ID???????????????????????????
		/*
		 * ??????????????????
		 */
		HistoricProcessInstance processInstance = historyService.createHistoricProcessInstanceQuery().processInstanceBusinessKey(businessKey).singleResult();
		if (processInstance == null) {
			return null;
		}

		// ??????????????????????????????????????????
		BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
		

		/*
		 * ?????????????????????????????? ??????????????????????????????????????????????????????????????????????????????????????????
		 */
		// ????????????????????????
		HistoricActivityInstanceQuery historyInstanceQuery = historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstance.getId());
		// ??????????????????
		List<HistoricActivityInstance> historicActivityInstanceList = historyInstanceQuery.orderByHistoricActivityInstanceStartTime().asc().list();

		if (historicActivityInstanceList == null || historicActivityInstanceList.size() == 0) {
			return outputImg(bpmnModel, null, null);
		}

		// ??????????????????ID??????(???historicActivityInstanceList????????????activityId?????????????????????executedActivityIdList)
		List<String> executedActivityIdList = historicActivityInstanceList.stream().map(item -> item.getActivityId()).collect(Collectors.toList());

		/*
		 * ????????????????????????
		 */
		// ??????????????????
		ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(processInstance.getProcessDefinitionId());
		List<String> flowIds = ActivitiUtils.getHighLightedFlows(bpmnModel, processDefinition, historicActivityInstanceList);

		return outputImg(bpmnModel, flowIds, executedActivityIdList);
	}

	/**
	 * <p>
	 * ????????????
	 * </p>
	 * 
	 * @param response               ????????????
	 * @param bpmnModel              ????????????
	 * @param flowIds                ?????????????????????
	 * @param executedActivityIdList void ??????????????????ID??????
	 * @author FRH
	 * @time 2018???12???10?????????11:23:01
	 * @version 1.0
	 */
	private InputStream outputImg(BpmnModel bpmnModel, List<String> flowIds, List<String> executedActivityIdList) {
		InputStream imageStream = null;
		try {
			imageStream = processDiagramGenerator.generateDiagram(bpmnModel, executedActivityIdList, flowIds, "??????", "????????????", "??????", true, "png");
		} catch (Exception e) {
			log.error("???????????????????????????, ????????????:{}", e);
		}

		return imageStream;
	}

	/**
	 * ???????????????????????????????????? --??????
	 * 
	 * @param userName
	 * @param category
	 * @return
	 */
	public IPage<WorkflowBaseEntity> findTaskStrsByUserName(IPage<WorkflowBaseEntity> page, WorkflowBaseEntity base, String startTime, String endTime, String userName, String category) {
		// ??????????????????
		/*List<Task> lst = taskService.createTaskQuery().taskAssignee(userName) .taskCategory(category) .list();
		if (null != lst && lst.size() > 0) {
			Set<String> lstKeys = new HashSet<String>();
			for (Task task : lst) {
				ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();
				lstKeys.add(pi.getBusinessKey());
			}

			return (Object[]) lstKeys.toArray();
		}*/
		
		//???????????????????????????sql????????????
		return workflowDao.findTaskStrsByUserName(page, userName, base.getCategoryId(), base.getProjectNo(), base.getName(), base.getApplyName(), startTime, endTime);
	}

	/**
	 * ??????????????????
	 * 
	 * @param taskId      ??????ID
	 * @param businessKey ????????????ID ????????????BaseActivitiEntity
	 * @param comments    ????????????
	 */
	public void saveSubmitTask(String taskId, String businessKey, String comments) {
		// ????????????ID????????????????????????????????????????????????ID
		Task task = taskService.createTaskQuery()//
				.taskId(taskId)// ????????????ID??????
				.singleResult();

		// ??????????????????ID
		String processInstanceId = task.getProcessInstanceId();

		/**
		 * ???????????????????????????????????????Activiti???????????????????????? String userId =
		 * Authentication.getAuthenticatedUserId(); CommentEntity comment = new
		 * CommentEntity(); comment.setUserId(userId);
		 * ???????????????Session??????????????????????????????????????????????????????????????????????????????act_hi_comment?????????User_ID???????????????????????????????????????????????????null
		 * ???????????????????????????????????????Authentication.setAuthenticatedUserId();??????????????????????????????
		 */
		Authentication.setAuthenticatedUserId(ShiroUtils.getSessionUserName());
		taskService.addComment(taskId, processInstanceId, comments);
		// ????????????ID??????????????????????????????????????????????????????
		taskService.complete(taskId);

		/**
		 * ???????????????????????????????????????????????? ??????????????????????????????????????????????????????1??????2????????????-->???????????????
		 */
		WorkflowBaseEntity workflowBaseEntity = workflowBaseService.getById(businessKey);
		if (null != workflowBaseEntity) {

			ProcessInstance pi = runtimeService.createProcessInstanceQuery()//
					.processInstanceId(processInstanceId)// ??????????????????ID??????
					.singleResult();
			// ???????????????
			if (pi == null) {
				// ??????????????????????????????2??????3????????????-->???????????????
				workflowBaseEntity.setStatus("3");
			} else {
				workflowBaseEntity.setStatus("2");
			}
			workflowBaseService.saveOrUpdate(workflowBaseEntity);
			log.info("??????:" + workflowBaseEntity.getName() + "???????????????????????????ID:" + taskId + "??? ????????????:" + workflowBaseEntity.getStatus());
		}

	}

	/**
	 * ??????????????????
	 * 
	 * @param taskId      ??????ID
	 * @param businessKey ????????????ID ????????????BaseActivitiEntity
	 * @param comments    ????????????
	 * @param variables   ????????????
	 */
	public void saveSubmitTask(String taskId, String businessKey, String comments, Map<String, Object> variables) {
		// ????????????ID????????????????????????????????????????????????ID
		Task task = taskService.createTaskQuery()//
				.taskId(taskId)// ????????????ID??????
				.singleResult();

		// ??????????????????ID
		String processInstanceId = task.getProcessInstanceId();

		/**
		 * ???????????????????????????????????????Activiti???????????????????????? String userId =
		 * Authentication.getAuthenticatedUserId(); CommentEntity comment = new
		 * CommentEntity(); comment.setUserId(userId);
		 * ???????????????Session??????????????????????????????????????????????????????????????????????????????act_hi_comment?????????User_ID???????????????????????????????????????????????????null
		 * ???????????????????????????????????????Authentication.setAuthenticatedUserId();??????????????????????????????
		 */
		Authentication.setAuthenticatedUserId(ShiroUtils.getSessionUserName());
		taskService.addComment(taskId, processInstanceId, comments);

		// ????????????ID??????????????????????????????????????????????????????
		taskService.complete(taskId, variables, true);

		/**
		 * ???????????????????????????????????????????????? ??????????????????????????????????????????????????????1??????2????????????-->???????????????
		 */
		WorkflowBaseEntity workflowBaseEntity = workflowBaseService.getById(businessKey);
		if (null != workflowBaseEntity) {

			ProcessInstance pi = runtimeService.createProcessInstanceQuery()//
					.processInstanceId(processInstanceId)// ??????????????????ID??????
					.singleResult();
			// ???????????????
			if (pi == null) {
				// ??????????????????????????????2??????3????????????-->???????????????
				workflowBaseEntity.setStatus("3");
			} else {
				workflowBaseEntity.setStatus("2");
			}
			workflowBaseService.saveOrUpdate(workflowBaseEntity);
			log.info("??????:" + workflowBaseEntity.getName() + "???????????????????????????ID:" + taskId + "??? ????????????:" + workflowBaseEntity.getStatus());
		}
	}
	
	/**
	 * ??????????????????
	 * @param taskId   ??????ID
	 * @param businessKey  ????????????ID
	 * @param comments  ????????????
	 * @param variables   ????????????
	 */
	public void saveBackTask(String taskId, String businessKey, String comments, Map<String, Object> variables){
		//????????????ID????????????????????????????????????????????????ID
		Task task = taskService.createTaskQuery()//
						.taskId(taskId)//????????????ID??????
						.singleResult();
		
		//??????????????????ID
		String processInstanceId = task.getProcessInstanceId();
		
		/**
		 * ???????????????????????????????????????Activiti????????????????????????
		 * 		String userId = Authentication.getAuthenticatedUserId();
			    CommentEntity comment = new CommentEntity();
			    comment.setUserId(userId);
			  ???????????????Session??????????????????????????????????????????????????????????????????????????????act_hi_comment?????????User_ID???????????????????????????????????????????????????null
			 ???????????????????????????????????????Authentication.setAuthenticatedUserId();??????????????????????????????
		 * */
		Authentication.setAuthenticatedUserId(ShiroUtils.getSessionUserName());
		taskService.addComment(taskId, processInstanceId, comments);
		
		//????????????ID??????????????????????????????????????????????????????
		taskService.complete(taskId, variables, true);
		
		/**
		 * ????????????????????????????????????????????????
   			??????????????????????????????????????????????????????1??????2????????????-->???????????????
		 */
		WorkflowBaseEntity baseActivitiEntity = workflowBaseService.getById(businessKey);
		baseActivitiEntity.setStatus("5");
		workflowBaseService.saveOrUpdate(baseActivitiEntity);
		log.info("??????:" + baseActivitiEntity.getName() + "???????????????????????????ID:" + taskId + "??? ????????????:" + baseActivitiEntity.getStatus());
	}

	/**
	 * ????????????ID????????????????????? ??????????????????????????????????????????
	 * 
	 * @param taskId
	 * @return
	 */
	public int findTaskOutgoByTaskId(String taskId) {
		int i = 1;
		// ????????????
		Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
		// ??????????????????????????????????????????
		BpmnModel bpmnModel = repositoryService.getBpmnModel(task.getProcessDefinitionId());
		
		List<org.activiti.bpmn.model.Process> lstProcess = bpmnModel.getProcesses();
		if(null != lstProcess && lstProcess.size() > 0) {
			for(org.activiti.bpmn.model.Process process : lstProcess) {
				//??????????????????????????????
		        List<UserTask> UserTaskList = process.findFlowElementsOfType(UserTask.class);
		        for(UserTask userTask:UserTaskList){
		        	if(StringUtils.equals(task.getTaskDefinitionKey(), userTask.getId())) {
		        		List<SequenceFlow> pvmTransitions = userTask.getOutgoingFlows();
		        		for (SequenceFlow pvmTransition : pvmTransitions) {
		        			String nextTarget = pvmTransition.getTargetRef();
		    				FlowElement flowElement = process.getFlowElement(nextTarget);
		    				if(flowElement instanceof ExclusiveGateway) {
		    					ExclusiveGateway gateway = (ExclusiveGateway)flowElement;
		    					List<SequenceFlow> flows = gateway.getOutgoingFlows();
		    					if(null != flows && flows.size() > 0) {
		    						for(SequenceFlow flow : flows) {
		    							/**
		    							 * ???????????????????????????flag??????????????????????????????????????????????????????
		    							 */
		    							if(StringUtils.contains(flow.getConditionExpression(), "flag")) {
		    								i = 2;
		    							}
		    						}
		    					}
		    				}
		        		}
		        	}
		        }
			}
		}
		
		return i;
	}

	/**
	 * ????????????key???????????????????????????
	 * @param businessKey
	 */
	public void deleteProcessInstranceByBusinessKey(String businessKey, String reason){
		//????????????ID???????????????????????????
		ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceBusinessKey(businessKey).singleResult();
		if(null != pi) {
			runtimeService.deleteProcessInstance(pi.getId(), reason);
		}
	}
	
	/**
	 * ????????????????????????????????????ID  ?????????  activiti7????????????springsecurity??????taskCandidateUser????????????
	 * @param userName
	 * @return
	 */
	public Object[] findGroupTaskStrsByUserName(String userName){
		//?????????????????????
		/*List<Task> lst = taskService.createTaskQuery().taskCandidateUser(userName).list();
		if(null != lst && lst.size() > 0) {
			Set<String> lstKeys = new HashSet<String>();
			for(Task task : lst) {
				ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();
				lstKeys.add(pi.getBusinessKey());
			}
			
			return (Object[])lstKeys.toArray();
		}*/
		
		return null;
	}
	
	
	/**
	 * ????????????????????????????????????ID  ?????????
	 * 			activiti7????????????springsecurity??????taskCandidateUser????????????
	 * @param userName
	 * @param category
	 * @return
	 */
	public Object[] findGroupTaskStrsByUserName(String userName, String category){
		//?????????????????????
		/*List<Task> lst = taskService.createTaskQuery().taskCandidateUser(userName).taskCategory(category).list();
		if(null != lst && lst.size() > 0) {
			Set<String> lstKeys = new HashSet<String>();
			for(Task task : lst) {
				ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();
				lstKeys.add(pi.getBusinessKey());
			}
			
			return (Object[])lstKeys.toArray();
		}*/
		
		return null;
	}
	
	/**
	 * ????????????????????????????????????ID  ????????????
	 * @param userName
	 * @return
	 */
	public void saveClaimGroupTaskByBusinessKey(String businessKey, String userName){
		/*//??????????????????
		List<ProcessInstance> lstPis = runtimeService.createProcessInstanceQuery().processInstanceBusinessKey(businessKey).list();
		//?????????????????????ID???????????????????????????????????????????????????????????????Set????????????
		Set<String> st = new HashSet<String>();
		for(ProcessInstance pi : lstPis) {
			String piId = pi.getProcessInstanceId();
			st.add(piId);
		}
		
		for(String piId : st) {
			List<Task> lst = taskService.createTaskQuery().processInstanceId(piId).taskCandidateUser(userName).list();
			for(Task task : lst) {
				taskService.claim(task.getId(), userName);
			}
		}*/
		
		List<String> lstTaskIds = workflowDao.findGroupTaskIdByBusinessKey(businessKey, userName);
		
		for(String taskId : lstTaskIds) {
			taskService.claim(taskId, userName);
		}
		
	}
	
	/**
	 * 
	 * @description
	 *  	???????????? ?????????????????????
	 * @return IPage<WorkflowBaseEntity>
	 * @author ?????????
	 * @time 2020???4???28??? ??????10:45:43
	 */
	public IPage<WorkflowBaseEntity> findGroupTaskStrsByUserName(IPage<WorkflowBaseEntity> page, WorkflowBaseEntity base, String startTime, String endTime, String userName){
		return workflowDao.findGroupTaskStrsByUserName(page, userName, base.getCategoryId(), base.getProjectNo(), base.getName(), base.getApplyName(), startTime, endTime);
	}
	   
	/**
	 *    
	 * @description
	 *  	??????????????????????????????????????????
	 * @return List<String>
	 * @author ?????????
	 * @time 2020???4???28??? ??????10:47:20
	 */
	public List<String> findGroupTaskIdByBusinessKey(String businessKey, String userName){
		return workflowDao.findGroupTaskIdByBusinessKey(businessKey, userName);
	}
	
	
	/**
	 * ????????????????????????????????????ID  ???????????????
	 * @param userName
	 * @return
	 */
	public boolean saveBackClaimGroupTaskByBusinessKey(String businessKey, String userName){
		//??????????????????
		List<ProcessInstance> lstPis = runtimeService.createProcessInstanceQuery().processInstanceBusinessKey(businessKey).list();
		//?????????????????????ID???????????????????????????????????????????????????????????????Set????????????
		Set<String> st = new HashSet<String>();
		for(ProcessInstance pi : lstPis) {
			String piId = pi.getProcessInstanceId();
			st.add(piId);
		}
		
		boolean isCandiate = false;
		for(String piId : st) {
			List<Task> lst = taskService.createTaskQuery().processInstanceId(piId).taskAssignee(userName).list();
			for(Task task : lst) {
				List<IdentityLink> lstLinks = taskService.getIdentityLinksForTask(task.getId());
				for(IdentityLink link : lstLinks) {
					if(StringUtils.equals(link.getType(), IdentityLinkType.CANDIDATE)) {
						isCandiate = true;
						if(isCandiate) {
							break;
						}
					}
				}
			}
			if(isCandiate) {
				for(Task task : lst) {
					taskService.setAssignee(task.getId(), null);
				}
			}
			
		}
		return isCandiate;
	}
}
