package com.campusconnect.neo4j.tests.functional;

import com.campusconnect.neo4j.tests.TestBase;
import com.campusconnect.neo4j.types.web.UserIdsPage;
import com.campusconnect.neo4j.types.web.UsersPage;
import com.sun.jersey.api.client.ClientResponse;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class UserGroupRelationTest extends TestBase {

    @Test
    public void TestGroupCreationAndUserAdditionToGroup() {
        String userIdAdmin = UserResourceFuncTest.createUser();
        String groupId = GroupResourceTest.createGroup(userIdAdmin);
        String userId = UserResourceFuncTest.createUser();

        ClientResponse clientResponseForCreationOfGroupAndAddingUsers = resource
                .path("groups").path(groupId).path("users").path(userId)
                .queryParam("createdBy", userId).type("application/json")
                .post(ClientResponse.class);
        assert clientResponseForCreationOfGroupAndAddingUsers.getStatus() == 201;

        ClientResponse clientResponseToReturnAddedUsers = resource
                .path("groups").path(groupId).path("users")
                .type("application/json").get(ClientResponse.class);
        UsersPage usersPage = clientResponseToReturnAddedUsers
                .getEntity(UsersPage.class);

        for (com.campusconnect.neo4j.types.web.User user : usersPage.getUsers()) {
            assert user.getId().equals(userIdAdmin)
                    || user.getId().equals(userId);
        }

        List<String> bulkUserIds = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            String userIdbulk = UserResourceFuncTest.createUser();
            bulkUserIds.add(userIdbulk);
        }

        UserIdsPage userIdsPage = new UserIdsPage(0, bulkUserIds.size(), bulkUserIds);

        ClientResponse clientResponseForBulkAdditionOfUsers = resource.path("groups").path(groupId).path("users").path("bulk").queryParam("createdBy", userIdAdmin).type("application/json").entity(userIdsPage).post(ClientResponse.class);
        assert clientResponseForBulkAdditionOfUsers.getStatus() == 200;

        ClientResponse clientResponseForBulkAdditionOfUsersResp = resource.path("groups").path(groupId).path("users").type("application/json").get(ClientResponse.class);
        assert clientResponseForBulkAdditionOfUsersResp.getStatus() == 200;

        UsersPage page = clientResponseForBulkAdditionOfUsersResp.getEntity(UsersPage.class);
        assert page.getSize() > 5;


    }

}
