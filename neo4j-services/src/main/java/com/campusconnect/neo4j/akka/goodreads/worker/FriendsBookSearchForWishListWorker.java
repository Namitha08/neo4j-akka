package com.campusconnect.neo4j.akka.goodreads.worker;

import akka.actor.UntypedActor;
import com.campusconnect.neo4j.akka.goodreads.GoodreadsAsynchHandler;
import com.campusconnect.neo4j.akka.goodreads.client.GoodreadsOauthClient;
import com.campusconnect.neo4j.akka.goodreads.task.AddFriendsFromGoodReadsTask;
import com.campusconnect.neo4j.akka.goodreads.task.FriendsBookSearchForWishListTask;
import com.campusconnect.neo4j.akka.goodreads.task.UserRecForWishListTask;
import com.campusconnect.neo4j.akka.goodreads.types.Friends;
import com.campusconnect.neo4j.akka.goodreads.types.GetFriendsResponse;
import com.campusconnect.neo4j.akka.goodreads.types.User;
import com.campusconnect.neo4j.akka.goodreads.util.ResponseUtils;
import com.campusconnect.neo4j.da.iface.BookDao;
import com.campusconnect.neo4j.da.iface.UserDao;
import com.campusconnect.neo4j.types.web.GoodreadsUserRecommendation;
import com.sun.jersey.api.uri.UriBuilderImpl;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.UriBuilder;
import java.util.List;

/**
 * Created by sn1 on 3/22/15.
 */
public class FriendsBookSearchForWishListWorker extends UntypedActor {

    private static Logger logger = LoggerFactory.getLogger(FriendsBookSearchForWishListWorker.class);

    @Autowired
    private GoodreadsAsynchHandler goodreadsAsynchHandler;

    @Autowired
    private GoodreadsOauthClient goodreadsOauthClient;

    @Autowired
    private UserDao userDao;

    @Autowired
    private BookDao bookDao;

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof FriendsBookSearchForWishListTask) {
            FriendsBookSearchForWishListTask getFriendsTask = (FriendsBookSearchForWishListTask) message;
            UriBuilder uriBuilder = new UriBuilderImpl();
            uriBuilder.path("https://www.goodreads.com");
            uriBuilder.path("friend/user");
            uriBuilder.path(getFriendsTask.getGoodreadsId());
            uriBuilder.queryParam("format", "xml");
            uriBuilder.queryParam("page", getFriendsTask.getPage());
            Token sAccessToken = new Token(getFriendsTask.getAccessToken(), getFriendsTask.getAccessSecret());
            OAuthRequest getBooksRequest = new OAuthRequest(Verb.GET, uriBuilder.build().toString());
            goodreadsOauthClient.getsService().signRequest(sAccessToken, getBooksRequest);
            Response response = getBooksRequest.send();
            GetFriendsResponse getFriendsResponse = ResponseUtils.getEntity(response.getBody(), GetFriendsResponse.class);
            assert getFriendsResponse != null;
            final Friends friends = getFriendsResponse.getFriends();


            if (friends != null && Integer.parseInt(friends.getTotal()) != 0) {
                List<GoodreadsUserRecommendation> existingRecommendations = bookDao.getGoodreadsUserRecommendations(getFriendsTask.getUserId());

                //if page is not the end and put the message back incrementing one page
                if (Integer.parseInt(friends.getEnd()) != Integer.parseInt(friends.getTotal())) {
                    logger.info("fired friends request again for page:" + (getFriendsTask.getPage() + 1));
                    getSelf().tell(new FriendsBookSearchForWishListTask(getFriendsTask.getAccessToken(), getFriendsTask.getAccessSecret(), getFriendsTask.getUserId(),
                            getFriendsTask.getGoodreadsId(), getFriendsTask.getPage() + 1, getFriendsTask.getWishListBooks()), getSelf());
                }
                logger.info("acquiring data from friends of number: " + friends.getUser().size() + " for user :" + getFriendsTask.getUserId() + " page : " + getFriendsTask.getPage());


                com.campusconnect.neo4j.types.neo4j.User currentUser = userDao.getUser(getFriendsTask.getUserId());
                goodreadsAsynchHandler.getAddGoodReadsFriendsRouter().tell(new AddFriendsFromGoodReadsTask(currentUser, friends), getSender());

                for (User user : friends.getUser()) {
                    goodreadsAsynchHandler.getUserRecForWishListRouter().tell(new UserRecForWishListTask(getFriendsTask.getAccessToken(), getFriendsTask.getAccessSecret(), getFriendsTask.getUserId(),
                            getFriendsTask.getGoodreadsId(), user, getFriendsTask.getWishListBooks(), 1, existingRecommendations), getSelf());
                }
            }
        }
    }
}
