package com.campusconnect.neo4j.types.web;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by sn1 on 3/22/15.
 */
public class WishListBook extends Book implements Serializable {

    private List<UserRecommendation> userRecommendations;

    public WishListBook() {
    }

    public WishListBook(Book book) {
        super(book.getId(), book.getGoodreadsId(), book.getAuthorName(), book.getGoodreadsAuthorId(), book.getName(),
                book.getIsbn(), book.getIsbn13(), book.getPublishedYear(), book.getDescription(), book.getPublisher(),
                book.getNumberOfPages(), book.getImageUrl());
        setBookType(book.getBookType());
    }

    public List<UserRecommendation> getUserRecommendations() {
        if (userRecommendations == null)
            userRecommendations = new ArrayList<>();
        return userRecommendations;
    }
}
