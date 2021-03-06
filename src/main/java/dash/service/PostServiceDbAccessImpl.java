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

import dash.dao.GroupEntity;
import dash.dao.PostDao;
import dash.dao.PostEntity;
import dash.dao.TaskEntity;
import dash.errorhandling.AppException;
import dash.filters.AppConstants;
import dash.helpers.NullAwareBeanUtilsBean;
import dash.pojo.Group;
import dash.pojo.Post;
import dash.pojo.User;
import dash.security.CustomPermission;
import dash.security.GenericAclController;


public class PostServiceDbAccessImpl extends ApplicationObjectSupport implements
PostService {

	@Autowired
	PostDao postDao;

	@Autowired
	private MutableAclService mutableAclService;
	
	@Autowired
	private GroupService groupService;

	@Autowired
	private GenericAclController<Post> aclController;

	private static final String SORT_ORDER=null;
	private static final Integer NUM_DAYS_LOOKBACK=null;


	/********************* Create related methods implementation ***********************/
	@Override
	@Transactional
	public Long createPost(Post post,  Group group) throws AppException {

		long postId = postDao.createPost(new PostEntity(post));
		post.setId(postId);
		aclController.createACL(post);
		aclController.createAce(post, CustomPermission.READ);
		aclController.createAce(post, CustomPermission.WRITE);
		aclController.createAce(post, CustomPermission.DELETE);
		return postId;
	}

	

	//Inactive
	@Override
	@Transactional
	public void createPosts(List<Post> posts) throws AppException {
		for (Post post : posts) {
			//createPost(post);
		}
	}


	// ******************** Read related methods implementation **********************
	@Override
	public List<Post> getPosts(int numberOfPosts, Long startIndex) throws AppException{
		
		List<PostEntity> posts = postDao.getPosts(numberOfPosts, startIndex);
		return getPostsFromEntities(posts);
	}
	
	@Override
	public List<Post> getPostsByGroup(int numberOfPosts, Long startIndex, Group group) throws AppException {

		//verify optional parameter numberDaysToLookBack first
		
		List<PostEntity> groupPosts = postDao.getPosts(numberOfPosts, startIndex, group);
		return getPostsFromEntities(groupPosts);

		
		
	}
	
	//TODO: rework so that it can paginate.
	@Override
	public List<Post> getPostsByMyGroups(int numberOfPosts, Long startIndex) throws AppException {
		
		
		List<Group>myGroups= groupService.getGroupsByMembership(SORT_ORDER, NUM_DAYS_LOOKBACK);
		myGroups.addAll(groupService.getGroupsByManager(SORT_ORDER, NUM_DAYS_LOOKBACK));
		
		List<Post> postsByMyGroups=new ArrayList<Post>();
		for(int i=0; i<myGroups.size(); i++){
			postsByMyGroups.addAll(getPostsByGroup(numberOfPosts, startIndex,
					myGroups.get(i)));
		}
		
		return postsByMyGroups;
	}
	

	@Override
	public Post getPostById(Long id) throws AppException {
		PostEntity postById = postDao.getPostById(id);
		if (postById == null) {
			throw new AppException(Response.Status.NOT_FOUND.getStatusCode(),
					404,
					"The post you requested with id " + id
					+ " was not found in the database",
					"Verify the existence of the post with the id " + id
					+ " in the database", AppConstants.DASH_POST_URL);
		}

		return new Post(postDao.getPostById(id));
	}

	private List<Post> getPostsFromEntities(List<PostEntity> postEntities) {
		List<Post> response = new ArrayList<Post>();
		for (PostEntity postEntity : postEntities) {
			response.add(new Post(postEntity));
		}

		return response;
	}
	

	
	

//	public List<Post> getRecentPosts(int numberOfDaysToLookBack) {
//		List<PostEntity> recentPosts = postDao
//				.getRecentPosts(numberOfDaysToLookBack);
//
//		return getPostsFromEntities(recentPosts);
//	}

	@Override
	public int getNumberOfPosts() {
		int totalNumber = postDao.getNumberOfPosts();

		return totalNumber;

	}



	/********************* UPDATE-related methods implementation ***********************/
	
	@Override
	@Transactional
	public void updateFullyPost(Post post) throws AppException {
		
		
		
		Post verifyPostExistenceById = verifyPostExistenceById(post
				.getId());
		if (verifyPostExistenceById == null) {
			throw new AppException(Response.Status.NOT_FOUND.getStatusCode(),
					404,
					"The resource you are trying to update does not exist in the database",
					"Please verify existence of data in the database for the id - "
							+ post.getId(),
							AppConstants.DASH_POST_URL);
		}
		copyAllProperties(verifyPostExistenceById, post);

		postDao.updatePost(new PostEntity(verifyPostExistenceById));

	}

	private void copyAllProperties(Post verifyPostExistenceById, Post post) {

		BeanUtilsBean withNull=new BeanUtilsBean();
		try {
			withNull.copyProperty(verifyPostExistenceById, "content", post.getContent());
			withNull.copyProperty(verifyPostExistenceById, "image", post.getImage());
			withNull.copyProperty(verifyPostExistenceById, "task_link_id", post.getTask_link_id());
			
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
	public void deletePost(Post post) {

		postDao.deletePostById(post);
		aclController.deleteACL(post);

	}

	@Override
	@Transactional
	// TODO: This shouldn't exist? If it must, then it needs to accept a list of
	// Posts to delete
	public void deletePosts() {
		postDao.deletePosts();
	}

	@Override
	
	public Post verifyPostExistenceById(Long id) {
		PostEntity postById = postDao.getPostById(id);
		if (postById == null) {
			return null;
		} else {
			return new Post(postById);
		}
	}

	@Override
	@Transactional
	public void updatePartiallyPost(Post post) throws AppException {
		//do a validation to verify existence of the resource
		Post verifyPostExistenceById = verifyPostExistenceById(post.getId());
		if (verifyPostExistenceById == null) {
			throw new AppException(Response.Status.NOT_FOUND.getStatusCode(),
					404,
					"The resource you are trying to update does not exist in the database",
					"Please verify existence of data in the database for the id - "
							+ post.getId(), AppConstants.DASH_POST_URL);
		}
		copyPartialProperties(verifyPostExistenceById, post);
		postDao.updatePost(new PostEntity(verifyPostExistenceById));

	}

	private void copyPartialProperties(Post verifyPostExistenceById, Post post) {

		BeanUtilsBean notNull=new NullAwareBeanUtilsBean();
		try {
			notNull.copyProperties(verifyPostExistenceById, post);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}


}
