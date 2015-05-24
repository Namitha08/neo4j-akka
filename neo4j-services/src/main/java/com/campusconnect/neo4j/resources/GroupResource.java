package com.campusconnect.neo4j.resources;

import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.springframework.context.annotation.ComponentScan.Filter;

import com.campusconnect.neo4j.da.GroupDao;
import com.campusconnect.neo4j.da.UserDaoImpl;
import com.campusconnect.neo4j.da.iface.UserDao;
import com.campusconnect.neo4j.exceptions.InvalidInputDataException;
import com.campusconnect.neo4j.types.AccessRoles;
import com.campusconnect.neo4j.types.Book;
import com.campusconnect.neo4j.types.BooksPage;
import com.campusconnect.neo4j.types.Group;
import com.campusconnect.neo4j.types.User;
import com.campusconnect.neo4j.types.UserGroupRelationship;
import com.campusconnect.neo4j.types.UserIdsPage;
import com.campusconnect.neo4j.types.UsersPage;

@Path("/groups")
@Consumes("application/json")
@Produces("application/json")
public class GroupResource {

	GroupDao groupDao;
	UserDao userDao;

	public GroupResource(GroupDao groupDao, UserDao userDao) {
		this.groupDao = groupDao;
		this.userDao = userDao;
	}

	@POST
	public Response createGroup(Group group, @QueryParam("userId") final String userId) {
		group.setId(UUID.randomUUID().toString());
		setGroupCreateProperties(group, userId);
		Group createdGroup = groupDao.createGroup(group);
		
		// Todo Add user as admin
		groupDao.addUser(createdGroup.getId(),userId,AccessRoles.ADMIN.toString(),userId);
		return Response.created(null).entity(createdGroup).build();
	}

	// @DELETE
	// public Response deleteGroup(Group group)
	// {
	// groupDao.deleteGroup(group);
	// return Response.ok().build();
	// }

	@GET
	@Path("{groupId}")
	public Response getGroup(@PathParam("groupId") final String groupId) {
		Group group = groupDao.getGroup(groupId);
		return Response.ok().entity(group).build();
	}

	@PUT
	@Path("{groupID}")
	public Response updateGroup(@PathParam("groupId") final String groupId,
			Group group) {
		//Todo Add the user who updated Group (last updated by)
		Group groupToBeUpdated = groupDao.updateGroup(groupId, group);
		group.setLastModifiedTime(System.currentTimeMillis());
		return Response.created(null).entity(groupToBeUpdated).build();
	}

	private Group setGroupCreateProperties(Group group, String userId) {
		long createdDate = System.currentTimeMillis();
		group.setCreatedDate(createdDate);
		group.setLastModifiedTime(createdDate);
		group.setLastModifiedBy(userId);
		return group;
	}

	@POST
	@Path("{groupId}/users/{userId}")
	public Response addUser(@PathParam("groupId") final String groupId,
			@PathParam("userId") final String userId,@QueryParam("role") final String role,
			@QueryParam("createdBy") final String createdBy) {
		
		groupDao.addUser(groupId,userId,role,createdBy);
		return Response.created(null).build();
	}

	@POST
	@Path("{groupId}/users/bulk")
	public Response addUsers(@PathParam("groupId") final String groupId,
			@QueryParam("createdBy") final String createdBy,
			final UserIdsPage userIdsPage) {
		// Todo validate user exist
		// Todo CreatedBy is of Admin or Write USER_ACCESS
		Long currentTime = System.currentTimeMillis();
		for (String userId : userIdsPage.getUserIds()) {
			User user = userDao.getUser(userId);
			groupDao.addUser(groupId,userId,AccessRoles.READ.toString(),createdBy);
		}
		return Response.ok().build();
	}

	@GET
	@Path("{groupId}/users")
	public Response getUsers(@PathParam("groupId") final String groupId) {
		List<User> users = groupDao.getUsers(groupId);
		UsersPage usersListPage = new UsersPage(0, users.size(), users);
		return Response.ok().entity(usersListPage).build();
	}
	
	@GET
	@Path("{groupId}/books")
	public Response getBooks(@PathParam("groupId")final String groupId,@QueryParam("filter")String filterParam)
	{
		if(null==filterParam || filterParam.equals("") || filterParam.toLowerCase().equals("available"))
		{		 
			List<Book> books = groupDao.getavailableBooks(groupId);
			BooksPage booksPage = new BooksPage(0,books.size(),books);
			return Response.ok().entity(booksPage).build();
		}
		else if(filterParam.toLowerCase().equals("lookingfor"))
		{
			List<Book> books = groupDao.getWishListBooks(groupId);
			BooksPage booksPage = new BooksPage(0,books.size(),books);
			return Response.ok().entity(booksPage).build();
		}
		return Response.ok().entity(new BooksPage()).build();
	}
}
