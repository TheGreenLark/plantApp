package dash.service;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ApplicationObjectSupport;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.MutableAclService;
import org.springframework.transaction.annotation.Transactional;

import dash.dao.TaskDao;
import dash.dao.TaskEntity;
import dash.errorhandling.AppException;
import dash.filters.AppConstants;
import dash.helpers.NullAwareBeanUtilsBean;
import dash.pojo.Group;
import dash.pojo.Task;
import dash.pojo.User;
import dash.security.CustomPermission;
import dash.security.GenericAclController;


public class TaskServiceDbAccessImpl extends ApplicationObjectSupport implements
TaskService {

	@Autowired
	TaskDao taskDao;

	@Autowired
	private MutableAclService mutableAclService;

	@Autowired
	private GenericAclController<Task> aclController;
	
	@Autowired
	private GenericAclController<Group> groupAclController;

	


	/********************* Create related methods implementation ***********************/
	@Override
	@Transactional
	public Long createTask(Task task, Group group) throws AppException {

		validateInputForCreation(task);

		//verify existence of resource in the db (feed must be unique)
		TaskEntity taskByName = taskDao.getTaskByName(task.getName());
		if (taskByName != null) {
			throw new AppException(
					Response.Status.CONFLICT.getStatusCode(),
					409,
					"Task with taskname already existing in the database with the id "
							+ taskByName.getId(),
							"Please verify that the taskname and password are properly generated",
							AppConstants.DASH_POST_URL);
		}

		long taskId = taskDao.createTask(new TaskEntity(task));
		task.setId(taskId);
		aclController.createACL(task);
		aclController.createAce(task, CustomPermission.MANAGER);
		return taskId;
	}

	private void validateInputForCreation(Task task) throws AppException {
		if (task.getName() == null) {
			throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), 400, "Provided data not sufficient for insertion",
					"Please verify that the taskname is properly generated/set",
					AppConstants.DASH_POST_URL);
		}
		
		//etc...
	}

	//Inactive
	@Override
	@Transactional
	public void createTasks(List<Task> tasks) throws AppException {
		for (Task task : tasks) {
			//createTask(task);
		}
	}


	// ******************** Read related methods implementation **********************
	@Override
	public List<Task> getTasks(String orderByInsertionDate,
			Integer numberDaysToLookBack) throws AppException {

		//verify optional parameter numberDaysToLookBack first
		if(numberDaysToLookBack!=null){
			List<TaskEntity> recentTasks = taskDao
					.getRecentTasks(numberDaysToLookBack);
			return getTasksFromEntities(recentTasks);
		}

		if(isOrderByInsertionDateParameterValid(orderByInsertionDate)){
			throw new AppException(
					Response.Status.BAD_REQUEST.getStatusCode(),
					400,
					"Please set either ASC or DESC for the orderByInsertionDate parameter",
					null, AppConstants.DASH_POST_URL);
		}
		List<TaskEntity> tasks = taskDao.getTasks(orderByInsertionDate);

		return getTasksFromEntities(tasks);
	}
	
	@Override
	public List<Task> getTasksByGroup(Group group){
		
		List<TaskEntity> tasks = taskDao.getTasksByGroup(group);
		return getTasksFromEntities(tasks);
		
	}
	
	
	
	@Override
	public List<Task> getTasksByMembership(String orderByInsertionDate,
	Integer numberDaysToLookBack) throws AppException {
	
		return getTasks(orderByInsertionDate, numberDaysToLookBack);
	}
	
	@Override
	public List<Task> getTasksByManager(String orderByInsertionDate,
	Integer numberDaysToLookBack) throws AppException {
	
		return getTasks(orderByInsertionDate, numberDaysToLookBack);
	}
	
	

	private boolean isOrderByInsertionDateParameterValid(
			String orderByInsertionDate) {
		return orderByInsertionDate!=null
				&& !("ASC".equalsIgnoreCase(orderByInsertionDate) || "DESC".equalsIgnoreCase(orderByInsertionDate));
	}
	
	@Override
	// TODO: This doesnt need to exist. It is the exact same thing as
	// getTaskById(Long)
	public Task verifyTaskExistenceById(Long id) {
		TaskEntity taskById = taskDao.getTaskById(id);
		if (taskById == null) {
			return null;
		} else {
			return new Task(taskById);
		}
	}

	@Override
	public Task getTaskById(Long id) throws AppException {
		TaskEntity taskById = taskDao.getTaskById(id);
		if (taskById == null) {
			throw new AppException(Response.Status.NOT_FOUND.getStatusCode(),
					404,
					"The task you requested with id " + id
					+ " was not found in the database",
					"Verify the existence of the task with the id " + id
					+ " in the database", AppConstants.DASH_POST_URL);
		}

		return new Task(taskDao.getTaskById(id));
	}

	private List<Task> getTasksFromEntities(List<TaskEntity> taskEntities) {
		List<Task> response = new ArrayList<Task>();
		for (TaskEntity taskEntity : taskEntities) {
			response.add(new Task(taskEntity));
		}

		return response;
	}

	public List<Task> getRecentTasks(int numberOfDaysToLookBack) {
		List<TaskEntity> recentTasks = taskDao
				.getRecentTasks(numberOfDaysToLookBack);

		return getTasksFromEntities(recentTasks);
	}

	@Override
	public int getNumberOfTasks() {
		int totalNumber = taskDao.getNumberOfTasks();

		return totalNumber;

	}



	/********************* UPDATE-related methods implementation ***********************/
	@Override
	@Transactional
	public void updateFullyTask(Task task, Group group) throws AppException {
		//do a validation to verify FULL update with PUT
		

		Task verifyTaskExistenceById = verifyTaskExistenceById(task
				.getId());
		if (verifyTaskExistenceById == null) {
			throw new AppException(Response.Status.NOT_FOUND.getStatusCode(),
					404,
					"The resource you are trying to update does not exist in the database",
					"Please verify existence of data in the database for the id - "
							+ task.getId(),
							AppConstants.DASH_POST_URL);
		}
		copyAllProperties(verifyTaskExistenceById, task);
		taskDao.updateTask(new TaskEntity(verifyTaskExistenceById));

	}

	private void copyAllProperties(Task verifyTaskExistenceById, Task task) {

		BeanUtilsBean withNull=new BeanUtilsBean();
		try {
			withNull.copyProperty(verifyTaskExistenceById, "description", task.getDescription());
			withNull.copyProperty(verifyTaskExistenceById, "name", task.getName());
			withNull.copyProperty(verifyTaskExistenceById, "time", task.getTime());
			withNull.copyProperty(verifyTaskExistenceById, "duration", task.getDuration());
			withNull.copyProperty(verifyTaskExistenceById, "location",  task.getLocation());
			withNull.copyProperty(verifyTaskExistenceById, "badge_id",  task.getBadge_id());
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/********************* DELETE-related methods implementation ***********************/

	@Override
	@Transactional
	public void deleteTask(Task task, Group group)  throws AppException{

		taskDao.deleteTaskById(task);
		aclController.deleteACL(task);

	}

	@Override
	@Transactional
	// TODO: This shouldn't exist? If it must, then it needs to accept a list of
	// Tasks to delete
	public void deleteTasks() {
		taskDao.deleteTasks();
	}
	
	/****************** Update Related Methods ***********************/

	

	@Override
	@Transactional
	public void updatePartiallyTask(Task task, Group group) throws AppException {
		//do a validation to verify existence of the resource
		Task verifyTaskExistenceById = verifyTaskExistenceById(task.getId());
		if (verifyTaskExistenceById == null) {
			throw new AppException(Response.Status.NOT_FOUND.getStatusCode(),
					404,
					"The resource you are trying to update does not exist in the database",
					"Please verify existence of data in the database for the id - "
							+ task.getId(), AppConstants.DASH_POST_URL);
		}
		copyPartialProperties(verifyTaskExistenceById, task);
		taskDao.updateTask(new TaskEntity(verifyTaskExistenceById));

	}

	private void copyPartialProperties(Task verifyTaskExistenceById, Task task) {

		BeanUtilsBean notNull=new NullAwareBeanUtilsBean();
		try {
			notNull.copyProperties(verifyTaskExistenceById, task);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * ACL related methods
	 */
	// Adds an additional manager to the task
	@Override
	@Transactional
	public void addManager(User user, Task task, Group group) throws AppException{
		if(isGroupManager(user, group) || isGroupMember(user, group)){
			aclController.createAce(task, CustomPermission.MANAGER, new PrincipalSid(user.getUsername()));
			if(aclController.hasPermission(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername())))	
				aclController.deleteACE(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername()));
		}else{
			throw new AppException(Response.Status.CONFLICT.getStatusCode(),
					409,
					"Cannot add user as manager because user is already manager of the group"
					+ "or they are not a member of the group to which this task belongs.",
					"Users with group manager status may not have task specific permissions for that groups tasks"
							+ task.getId(), AppConstants.DASH_POST_URL);
		}
	}
	
	//Removes all managers and sets new manager to user
	@Override
	@Transactional
	public void resetManager(User user, Task task) throws AppException{
		Group group= new Group();
		group.setId(task.getGroup_id());
		if(isGroupManager(user, group) || isGroupMember(user, group)){
			aclController.clearPermission(task, CustomPermission.MANAGER);
			aclController.createAce(task, CustomPermission.MANAGER, new PrincipalSid(user.getUsername()));
			if(aclController.hasPermission(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername())))	
				aclController.deleteACE(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername()));
		}else{
			throw new AppException(Response.Status.CONFLICT.getStatusCode(),
					409,
					"Cannot add user as manager because user is already manager of the group"
					+ "or they are not a member of the group to which this task belongs.",
					"Users with group manager status may not have task specific permissions for that groups tasks"
							+ task.getId(), AppConstants.DASH_POST_URL);
		}
	}
	
	//Removes a single manager from a task
	@Override
	@Transactional
	public void deleteManager(User user, Task task, Group group) throws AppException{
		aclController.deleteACE(task, CustomPermission.MANAGER, new PrincipalSid(user.getUsername()));
		aclController.createAce(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername()));
	}
	
	//Adds a member to the task
	@Override
	@Transactional
	public void addMember(User user, Task task) throws AppException{
		Group group= new Group();
		group.setId(task.getGroup_id());
		if(isGroupManager(user, group) || isGroupMember(user, group)){
			aclController.createAce(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername()));
			if(aclController.hasPermission(task, CustomPermission.MANAGER, new PrincipalSid(user.getUsername())))	
				aclController.deleteACE(task, CustomPermission.MANAGER, new PrincipalSid(user.getUsername()));
		}else{
			throw new AppException(Response.Status.CONFLICT.getStatusCode(),
					409,
					"Cannot add user as member because user is already manager of the group"
					+ " or they are not a member of the group to which this task belongs.",
					" Users with group manager status may not have task specific permissions for that groups tasks"
							+ task.getId(), AppConstants.DASH_POST_URL);
		}
		
	}
	
	//Removes single member
	@Override
	@Transactional
	public void deleteMember(User user, Task task, Group group) throws AppException{
		aclController.deleteACE(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername()));
	}
	
	
	
	/***********************  Helper Methods  **************************************/

	//Verifies that an user is not already a manager of the group
	//This avoids having a group manager also have task level permissions since they are redundant
	private boolean isGroupManager(User user, Group group){
		return groupAclController.hasPermission(group, CustomPermission.MANAGER, new PrincipalSid(user.getUsername()));
	}
	private boolean isGroupMember(User user, Group group){
		return groupAclController.hasPermission(group, CustomPermission.MEMBER, new PrincipalSid(user.getUsername()));
	}
	
	

}
