package com.campusconnect.neo4j.da;

import com.campusconnect.neo4j.akka.goodreads.GoodreadsAsynchHandler;
import com.campusconnect.neo4j.da.iface.BookDao;
import com.campusconnect.neo4j.repositories.BookRepository;
import com.campusconnect.neo4j.repositories.OwnsRelationshipRepository;
import com.campusconnect.neo4j.repositories.UserRecRepository;
import com.campusconnect.neo4j.types.*;
import com.googlecode.ehcache.annotations.Cacheable;
import com.googlecode.ehcache.annotations.KeyGenerator;
import com.googlecode.ehcache.annotations.PartialCacheKey;
import com.googlecode.ehcache.annotations.Property;
import org.neo4j.rest.graphdb.entity.RestNode;
import org.neo4j.rest.graphdb.entity.RestRelationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.conversion.Result;
import org.springframework.data.neo4j.support.Neo4jTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;

/**
 * Created by sn1 on 2/16/15.
 */
public class BookDaoImpl implements BookDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(BookDaoImpl.class);

    private Neo4jTemplate neo4jTemplate;
    private GoodreadsDao goodreadsDao;
    private GoodreadsAsynchHandler goodreadsAsynchHandler;

    @Autowired
    BookRepository bookRepository;

    @Autowired
    OwnsRelationshipRepository ownsRelationshipRepository;
    
    @Autowired
    UserRecRepository userRecRepository;

    public BookDaoImpl(Neo4jTemplate neo4jTemplate, GoodreadsDao goodreadsDao, GoodreadsAsynchHandler goodreadsAsynchHandler) {
        this.neo4jTemplate = neo4jTemplate;
        this.goodreadsDao = goodreadsDao;
        this.goodreadsAsynchHandler = goodreadsAsynchHandler;
    }

    @Override
    public Book createBook(Book book) {
        return neo4jTemplate.save(book);
    }

    @Override
    public Book getBook(String bookId) {
        return bookRepository.findBySchemaPropertyValue("id", bookId);
    }

    @Override
    public void listBookAsOwns(OwnsRelationship ownsRelationship) {
        neo4jTemplate.save(ownsRelationship);
    }
    
    @Override
    public void listBookAsRead(ReadRelation readRelation) {
        neo4jTemplate.save(readRelation);
    }

    @Override
    @Transactional
    public void updateOwnedBookStatus(User user, Book book, String status) {
        OwnsRelationship relationship = neo4jTemplate.getRelationshipBetween(user, book, OwnsRelationship.class, RelationTypes.OWNS.toString());
        if (relationship == null) //todo: throw an exception
            return;
        relationship.setStatus(status);
        relationship.setLastModifiedDate(System.currentTimeMillis());
        neo4jTemplate.save(relationship);
    }

    @Override
    public void addBookToBorrower(User borrower, Book book, BorrowRequest borrowRequest) {
        BorrowRelation borrowRelation = new BorrowRelation(borrower, book, "pending");
        borrowRelation.setBorrowDate(borrowRequest.getBorrowDate());
        borrowRelation.setContractPeriodInDays(borrowRequest.getContractPeriodInDays());
        borrowRelation.setAdditionalComments(borrowRequest.getAdditionalMessage());
        borrowRelation.setOwnerUserId(borrowRequest.getOwnerUserId());
        neo4jTemplate.save(borrowRelation);
    }

    @Override
    public void updateBookStatusOnAgreement(User user, Book book, User borrower) {
        updateOwnedBookStatus(user, book, "locked");
        updateBorrowedBookStatus(borrower, book, "agreed");
    }

    @Override
    public void updateBookStatusOnSuccess(User user, Book book, User borrower) {
        updateOwnedBookStatus(user, book, "lent");
        updateBorrowedBookStatus(borrower, book, "borrowed");
    }

    @Override
    @Transactional
    public void updateBorrowedBookStatus(User user, Book book, String status) {
        BorrowRelation relationship = neo4jTemplate.getRelationshipBetween(user, book, BorrowRelation.class, RelationTypes.BORROWED.toString());
        if (relationship == null) //todo: throw an exception
            return;
        relationship.setStatus(status);
        relationship.setLastModifiedDate(System.currentTimeMillis());
        neo4jTemplate.save(relationship);
    }

    @Override
    public SearchResult search(String queryString) {
        return goodreadsDao.search(queryString);
    }

    @Override
    @Cacheable(cacheName = "bookByGRIdCache", keyGenerator = @KeyGenerator(name="HashCodeCacheKeyGenerator", properties = @Property( name="includeMethod", value="false")))
    public Book getBookByGoodreadsId(String goodreadsId) throws IOException {
        try {
            Book book = bookRepository.findBySchemaPropertyValue("goodreadsId", goodreadsId);
            if(book == null) {
                LOGGER.info("Goodreads book is not in there datsbase fetching from goodreads. Id: " + goodreadsId);
                final Book bookByGoodreadId = goodreadsDao.getBookById(goodreadsId);
                bookByGoodreadId.setId(UUID.randomUUID().toString());
                goodreadsAsynchHandler.saveBook(bookByGoodreadId);
                return bookByGoodreadId;     
            }
        } catch (Exception e) {
           //Todo : if exception is not found search in goodreads
            return goodreadsDao.getBookById(goodreadsId);
        }
        return null;
    }

    @Override
    @Cacheable(cacheName = "bookByGRIdCache", keyGenerator = @KeyGenerator(name="HashCodeCacheKeyGenerator", properties = @Property( name="includeMethod", value="false")))
    public Book getBookByGoodreadsIdAndSaveIfNotExists(@PartialCacheKey String goodreadsId, Book book) {
        Book bookByGoodreadsId = bookRepository.findBySchemaPropertyValue("goodreadsId", goodreadsId);
        if(bookByGoodreadsId == null) {
            book.setId(UUID.randomUUID().toString());
            return createBook(book);
        }
        return bookByGoodreadsId;
    }
    
    

    @Override
    public void addWishBookToUser(WishListRelationship wishListRelationship) {
        neo4jTemplate.save(wishListRelationship);
    }
    
    @Override
    public void createGoodreadsFriendBookRec(GoodreadsFriendBookRecRelation goodreadsFriendBookRecRelation) {
        neo4jTemplate.save(goodreadsFriendBookRecRelation);
    }

    @Override
    public Book getBookByIsbn(String isbn) throws IOException {
        Book book = goodreadsDao.getBookByISBN(isbn);
        Book goodreadsBook = getBookByGoodreadsIdAndSaveIfNotExists(book.getGoodreadsAuthorId(), book);
        return goodreadsBook;
    }
    
    @Override
    public List<UserRecommendation> getRecommendationsForUserAndBook(String bookId, String userId) {
        List<GoodreadsFriendBookRecRelation> friendBookRecRelations = userRecRepository.getGoodreadsFriendBookRecRelations(userId, bookId);
        List<UserRecommendation> userRecommendations = convertToUserRec(friendBookRecRelations);
        return userRecommendations;
    }

    private List<UserRecommendation> convertToUserRec(List<GoodreadsFriendBookRecRelation> friendBookRecRelations) {
        List<UserRecommendation> userRecommendations = new ArrayList<>();
        for(GoodreadsFriendBookRecRelation friendBookRecRelation : friendBookRecRelations) {
            UserRecommendation userRecommendation = new UserRecommendation();
            userRecommendation.setFriendGoodreadsId(friendBookRecRelation.getFriendGoodreadsId());
            userRecommendation.setFriendImageUrl(friendBookRecRelation.getFriendImageUrl());
            userRecommendation.setFriendName(friendBookRecRelation.getFriendName());
            userRecommendation.setFriendId(friendBookRecRelation.getFriendId());
            
            userRecommendations.add(userRecommendation);
        }
        return userRecommendations;
    }

    @Override
    public Book getBook(String bookId, String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("bookId", bookId);
        Result<Map<String, Object>> mapResult = neo4jTemplate.query("match(book:Book {id: {bookId}}) - [relation] - (user:User {id: {userId}}) return relation, book", params);
        //todo throw not found
        return getBookDetails(mapResult, userId);
    }

    private Book getBookDetails(Result<Map<String, Object>> mapResult, String userId) {
        Book book = null;
        for (Map<String, Object> objectMap : mapResult) {
            RestNode bookNode = (RestNode) objectMap.get("book");
            RestRelationship rawWishRelationship = (RestRelationship) objectMap.get("relation");

            book = neo4jTemplate.convert(bookNode, Book.class);
            if(rawWishRelationship.getType().name().equals("OWNS")){
                book.setBookType("OWNS");
                OwnsRelationship ownsRelationship = neo4jTemplate.convert(rawWishRelationship, OwnsRelationship.class);
                book.getAdditionalProperties().putAll(ownsRelationship.getFieldsAsMap());
                return book;
            }
            if(rawWishRelationship.getType().name().equals("BORROWED")) {
                book.setBookType("BORROWED");
                BorrowRelation borrowRelation = neo4jTemplate.convert(rawWishRelationship, BorrowRelation.class);
                book.getAdditionalProperties().putAll(borrowRelation.getFieldsAsMap());
                return book;
            }
            if(rawWishRelationship.getType().name().equals("WISH")) {
                book.setBookType("WISH");
                //find if there are any recommendations
                List<UserRecommendation> userRecommendations = getRecommendationsForUserAndBook(book.getId(), userId);
                book.getAdditionalProperties().put("recommendations", userRecommendations);
                return book;
            }
        }
        return book;
    }
}
