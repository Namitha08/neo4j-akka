package com.campusconnect.neo4j.mappers;

import com.campusconnect.neo4j.types.web.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: sn1
 * Date: 6/4/15
 * Time: 10:29 PM
 * To change this template use File | Settings | File Templates.
 */
public class Neo4jToWebMapper {
    public static User mapUserNeo4jToWeb(com.campusconnect.neo4j.types.neo4j.User user) {
        if (user == null)
            return null;
        Set<Address> addresses = new HashSet<>();
        if (user.getAddresses() != null && user.getAddresses().size() > 0) {
            for (com.campusconnect.neo4j.types.neo4j.Address address : user.getAddresses()) {
                addresses.add(Neo4jToWebMapper.mapAddressNeo4jToWeb(address));
            }
        }
        return new User(addresses, user.getCreatedDate(), user.getEmail(), user.getFavorites(), user.getFbId(), user.getGoodreadsAccessToken(), user.getGoodreadsAccessTokenSecret(), user.getGoodreadsAuthStatus(),
                user.getGoodReadsSynchStatus(), user.getLastGoodreadsSychDate(), user.getGoodreadsId(), user.getGoogleId(), user.getId(), user.getLastModifiedDate(), user.getName(), user.getPhone(), user.getGender(),
                user.getProfileImageUrl(), user.getWorkDesignation(), user.getWorkLocation(), user.getUserRelation());
    }

    public static Book mapBookNeo4jToWeb(com.campusconnect.neo4j.types.neo4j.Book book) {
        if (book == null)
            return null;
        return new Book(book.getId(), book.getGoodreadsId(), book.getAuthorName(), book.getGoodreadsAuthorId(), book.getName(), book.getIsbn(), book.getIsbn13(),
                book.getPublishedYear(), book.getDescription(), book.getPublisher(), book.getNumberOfPages(), book.getImageUrl());
    }

    public static Favourite mapFavouriteNeo4jToWeb(com.campusconnect.neo4j.types.neo4j.Favourite favourite) {
        if (favourite == null)
            return null;
        return new Favourite(favourite.getGenre(), favourite.getNodeId());
    }

    public static Group mapGroupNeo4jToWeb(com.campusconnect.neo4j.types.neo4j.Group group) {
        if (group == null)
            return null;
        return new Group(group.getId(), group.getName(), group.getCreatedDate(), group.getLastModifiedTime(), group.getLastModifiedBy());
    }

    public static Reminder mapReminderNeo4jToWeb(com.campusconnect.neo4j.types.neo4j.Reminder reminder) {
        if (reminder == null)
            return null;
        return new Reminder(reminder.getCreatedDate(), reminder.getDescription(), reminder.getLastModifiedTime(), reminder.getNodeId(), reminder.getReminderMessage(), reminder.getReminderTime(), reminder.getSubject());
    }

    public static Address mapAddressNeo4jToWeb(com.campusconnect.neo4j.types.neo4j.Address address) {
        if (address == null)
            return null;
        return new Address(address.getCity(), address.getCountry(), address.getCreatedDate(), address.getId(), address.getLandmark(), address.getLastModifiedTime(), address.getLatitude(),
                address.getLine1(), address.getLine2(), address.getLongitude(), address.getState(), address.getType(), address.getZipCode());
    }

}