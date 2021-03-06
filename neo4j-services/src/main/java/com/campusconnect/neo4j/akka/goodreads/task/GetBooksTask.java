package com.campusconnect.neo4j.akka.goodreads.task;

/**
 * Created by sn1 on 3/12/15.
 */
public class GetBooksTask {
    private String userId;
    private String goodreadsUserId;
    private Integer page;
    private String accessToken;
    private String accessTokenSecret;

    public GetBooksTask(String userId, String goodreadsUserId, Integer page, String accessToken, String accessTokenSecret) {
        this.userId = userId;
        this.goodreadsUserId = goodreadsUserId;
        this.page = page;
        this.accessToken = accessToken;
        this.accessTokenSecret = accessTokenSecret;
    }

    public String getUserId() {
        return userId;
    }

    public String getGoodreadsUserId() {
        return goodreadsUserId;
    }

    public Integer getPage() {
        return page;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getAccessTokenSecret() {
        return accessTokenSecret;
    }

}
