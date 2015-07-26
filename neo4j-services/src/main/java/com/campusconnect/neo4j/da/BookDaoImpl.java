package com.campusconnect.neo4j.da;

import com.campusconnect.neo4j.akka.goodreads.GoodreadsAsynchHandler;
import com.campusconnect.neo4j.da.iface.AuditEventDao;
import com.campusconnect.neo4j.da.iface.BookDao;
import com.campusconnect.neo4j.da.iface.EmailDao;
import com.campusconnect.neo4j.da.iface.NotificationDao;
import com.campusconnect.neo4j.da.iface.UserDao;
import com.campusconnect.neo4j.da.mapper.DBMapper;
import com.campusconnect.neo4j.da.utils.EventHelper;
import com.campusconnect.neo4j.da.utils.HistoryEventHelper;
import com.campusconnect.neo4j.da.utils.Queries;
import com.campusconnect.neo4j.da.utils.TargetHelper;
import com.campusconnect.neo4j.exceptions.Neo4jException;
import com.campusconnect.neo4j.exceptions.NotFoundException;
import com.campusconnect.neo4j.mappers.Neo4jToWebMapper;
import com.campusconnect.neo4j.repositories.BookRepository;
import com.campusconnect.neo4j.repositories.UserRecRepository;
import com.campusconnect.neo4j.types.common.AuditEventType;
import com.campusconnect.neo4j.types.common.BookDetails;
import com.campusconnect.neo4j.types.common.RelationTypes;
import com.campusconnect.neo4j.types.common.Target;
import com.campusconnect.neo4j.types.neo4j.Book;
import com.campusconnect.neo4j.types.neo4j.*;
import com.campusconnect.neo4j.types.neo4j.Group;
import com.campusconnect.neo4j.types.neo4j.User;
import com.campusconnect.neo4j.types.web.*;
import com.campusconnect.neo4j.util.Constants;
import com.campusconnect.neo4j.util.ErrorCodes;
import com.campusconnect.neo4j.util.TimeUtils;
import com.campusconnect.neo4j.util.Validator;
import com.googlecode.ehcache.annotations.PartialCacheKey;

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

import static com.campusconnect.neo4j.da.mapper.DBMapper.*;
import static com.campusconnect.neo4j.da.utils.Queries.*;
import static com.campusconnect.neo4j.mappers.Neo4jToWebMapper.mapGroupNeo4jToWeb;
import static com.campusconnect.neo4j.mappers.Neo4jToWebMapper.mapUserNeo4jToWeb;
import static com.campusconnect.neo4j.types.common.AuditEventType.BORROWED;
import static com.campusconnect.neo4j.types.common.AuditEventType.BORROW_AGREED;
import static com.campusconnect.neo4j.types.common.AuditEventType.LENT;
import static com.campusconnect.neo4j.util.Constants.*;

/**
 * Created by sn1 on 2/16/15.
 */
public class BookDaoImpl implements BookDao {
    private static final Logger logger = LoggerFactory.getLogger(BookDaoImpl.class);

    public BookDaoImpl() {
    }

    @Autowired
    BookRepository bookRepository;

    @Autowired
    UserRecRepository userRecRepository;

    @Autowired
    AuditEventDao auditEventDao;

    @Autowired
    NotificationDao notificationDao;

    private Neo4jTemplate neo4jTemplate;
    private GoodreadsDao goodreadsDao;
    private GoodreadsAsynchHandler goodreadsAsynchHandler;
    private EmailDao emailDao;
    private UserDao userDao;

    public BookDaoImpl(Neo4jTemplate neo4jTemplate, GoodreadsDao goodreadsDao, GoodreadsAsynchHandler goodreadsAsynchHandler, EmailDao emailDao, UserDao userDao) {
        this.neo4jTemplate = neo4jTemplate;
        this.goodreadsDao = goodreadsDao;
        this.goodreadsAsynchHandler = goodreadsAsynchHandler;
        this.emailDao = emailDao;
        this.userDao = userDao;
    }

    @Override
    public Book createBook(Book book) {
        return neo4jTemplate.save(book);
    }

    @Override
    public Book getBook(String bookId) {
        Book book = bookRepository.findBySchemaPropertyValue("id", bookId);
        if (book == null) {
            throw new NotFoundException(ErrorCodes.BOOK_NOT_FOUND, "Book with id:" + bookId + " not found.");
        }
        return book;
    }

    @Override
    public void listBookAsOwns(OwnsRelationship ownsRelationship) {

    	OwnsRelationship existingOwnsRelationship = bookRepository.getOwnsRelationship(ownsRelationship.getUser().getId(), ownsRelationship.getBook().getId());

    	if(null==existingOwnsRelationship)
    	{
    		neo4jTemplate.save(ownsRelationship);
    		Target target = TargetHelper.createBookTarget(ownsRelationship.getBook());
    		Event event = EventHelper.createPublicEvent(AuditEventType.BOOK_ADDED_OWNS, target);
    		auditEventDao.addEvent(ownsRelationship.getUser().getId(), event);

    	}else
    	{
    		existingOwnsRelationship.setStatus(ownsRelationship.getStatus());
    		existingOwnsRelationship.setLastModifiedDate(ownsRelationship.getLastModifiedDate());
    		neo4jTemplate.save(existingOwnsRelationship);
    	}

    }

    @Override
    public void listBookAsRead(ReadRelationship readRelation) {

    	ReadRelationship existingReadRelationship = bookRepository.getReadRelationShip(readRelation.getUser().getId(),readRelation.getBook().getId());
    	if(null==existingReadRelationship)
    	{
            neo4jTemplate.save(readRelation);
            Target target = TargetHelper.createBookTarget(readRelation.getBook());
    		Event event = EventHelper.createPublicEvent(AuditEventType.BOOK_ADDED_READ.toString(), target);
    		auditEventDao.addEvent(readRelation.getUser().getId(), event);
    	}

    }

    @Override
    public List<Book> getWishlistBooksWithDetails(String userId) {
        List<Book> wishlistRecOnFriends = getWishlistRecOnFriends(userId);
        List<Book> wishlistRecOnGroups = getWishlistRecOnGroups(userId);

        //todo: merge the result to single id
//        wishlistRecOnFriends.addAll(wishlistRecOnGroups);
        List<Book> mergeWishList = new ArrayList<>();
        if(wishlistRecOnGroups.isEmpty()){
            mergeWishList.addAll(wishlistRecOnFriends);
        } else {
            for (Book wishListFriend : wishlistRecOnFriends){
                for (Iterator<Book> iterator = wishlistRecOnGroups.iterator(); iterator.hasNext(); ) {
                    Book wishListGroup = iterator.next();
                    if(wishListFriend.getId().equals(wishListGroup.getId())){
                        WishlistBookDetails wishlistBookDetailsFriendRec = (WishlistBookDetails) wishListFriend.getBookDetails();
                        WishlistBookDetails wishlistBookDetailsGroupRec = (WishlistBookDetails) wishListGroup.getBookDetails();
                        wishlistBookDetailsFriendRec.getGroupsWithMembers().addAll(wishlistBookDetailsGroupRec.getGroupsWithMembers());
                        iterator.remove();
                    }
                    mergeWishList.add(wishListFriend);
                }
            }
        }
        //add all remaining books
        mergeWishList.addAll(wishlistRecOnGroups);
        return mergeWishList;
    }

    @Override
    public void initiateBookReturn(String bookId, String status, ReturnRequest returnRequest) {
        //TODO: get a relation b/w users with borrowed, if not exists throw not found error

    	BorrowRelationship borrowRelationship = bookRepository.getBorrowRelationship(returnRequest.getBorrowerUserId(), bookId, returnRequest.getOwnerUserId());
    	OwnsRelationship ownsRelationship = bookRepository.getOwnsRelationship(returnRequest.getOwnerUserId(), bookId);



    	if(borrowRelationship.getStatus().toUpperCase().equals(Constants.BORROW_SUCCESS))
    	Validator.checkBookReturnPreConditions(borrowRelationship,ownsRelationship);

    		//TODO : OPtimize updateBorrowedBookStatus and updateOwnedBookStatus

	        User borrower = userDao.getUser(returnRequest.getBorrowerUserId());
	        User owner = userDao.getUser(returnRequest.getOwnerUserId());
	        Book book = getBook(bookId);
	        updateBorrowedBookStatus(borrower, book, RETURN_INIT, returnRequest.getAdditionalMessage());
	        updateOwnedBookStatus(owner, book, RETURN_INIT, returnRequest.getAdditionalMessage());

    }

    @Override
    public void updateBookReturnToAgreed(String bookId, String status, String ownerId, String borrowerId, String comment) {
        //todo: get a relation b/w users with borrowed, if not exists throw not found error
        User borrower = userDao.getUser(borrowerId);
        User owner = userDao.getUser(ownerId);
        Book book = getBook(bookId);
        updateBorrowedBookStatus(borrower, book, RETURN_AGREED, comment);
        updateOwnedBookStatus(owner, book, RETURN_INIT, comment);
        //todo: add notification to the user
    }

    @Override
    public void updateBookReturnToSuccess(String bookId, String status, String ownerId, String borrowerId, String comment) {
        //todo: get a relation b/w users with borrowed, if not exists throw not found error
        User borrower = userDao.getUser(borrowerId);
        User owner = userDao.getUser(ownerId);
        Book book = getBook(bookId);
        updateBorrowedBookStatus(borrower, book, RETURN_SUCCESS, comment);
        updateOwnedBookStatus(owner, book, RETURN_SUCCESS, comment);
        //todo: add notification to the user
    }

    @Override
    public List<Book> getWishListBooksWithRec(String userId) {
        List<Book> books = getWishlistBooksWithDetails(userId);
        List<Book> resultBooks = new ArrayList<>();
        if(books != null && !books.isEmpty()) {
            for(Book book: books) {
                if(book.getBookDetails() != null){
                    resultBooks.add(book);
                }
            }
        }
        return resultBooks;
    }

    private List<Book> getWishlistRecOnFriends(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        Result<Map<String, Object>> mapResult = neo4jTemplate.query(Queries.GET_WISHLIST_REC_FRIENDS, params);
        return getWishListBookAndUserFromResultMap(mapResult);
    }

    private List<Book> getAllFriendsBooks(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        Result<Map<String, Object>> mapResult = neo4jTemplate.query(Queries.GET_ALL_FRIENDS_BOOKS, params);
        return getWishListBookAndUserFromResultMap(mapResult);
    }

    private List<Book> getWishlistRecOnGroups(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        Result<Map<String, Object>> mapResult = neo4jTemplate.query(Queries.GET_WISHLIST_GROUP, params);
        return getWishListBookGroupAndFriendFromResultMap(mapResult);
    }

    private List<Book> getWishListBookGroupAndFriendFromResultMap(Result<Map<String, Object>> mapResult) {
        List<Book> books = new ArrayList<>();
        for (Map<String, Object> objectMap : mapResult) {
            RestNode bookNode = (RestNode) objectMap.get("book");
            RestNode userNode = (RestNode) objectMap.get("friend");
            RestNode groupNode = (RestNode) objectMap.get("group");

            User user = getUserFromRestNode(userNode);
            Book book = getBookFromRestNode(bookNode);
            Group group = getGroupFromRestNode(groupNode);
            if(group == null || user == null || book == null)
                throw new Neo4jException(ErrorCodes.INTERNAL_SERVER_ERROR, "Data is corrupted. Needs action");
            GroupMember groupMember = Neo4jToWebMapper.mapUserNeo4jToWebGroupMember(user, group.getId(), group.getName(), group.getCreatedDate(), null);
            boolean appended = false;
            for (Book listedBook : books) {
                if (listedBook.getId().equals(book.getId())) {
                    WishlistBookDetails wishlistBookDetails = (WishlistBookDetails) listedBook.getBookDetails();
                    boolean groupAppended = false;
                    for (GroupWithMembers groupWithMembers : wishlistBookDetails.getGroupsWithMembers()){
                        if(groupWithMembers.getGroup().getId().equals(group.getId())){
                            groupWithMembers.getGroupMembers().add(groupMember);
                            groupAppended = true;
                            break;
                        }
                    }
                    if(!groupAppended){
                        GroupWithMembers groupWithMembers = new GroupWithMembers();
                        groupWithMembers.setGroup(mapGroupNeo4jToWeb(group));
                        groupWithMembers.getGroupMembers().add(groupMember);
                        wishlistBookDetails.getGroupsWithMembers().add(groupWithMembers);
                    }
                    appended = true;
                    break;
                }
            }
            if (!appended) {
                WishlistBookDetails wishlistBookDetails = new WishlistBookDetails();
                GroupWithMembers groupWithMembers = new GroupWithMembers();
                groupWithMembers.setGroup(mapGroupNeo4jToWeb(group));
                groupWithMembers.getGroupMembers().add(groupMember);
                wishlistBookDetails.getGroupsWithMembers().add(groupWithMembers);
                book.setBookDetails(wishlistBookDetails);
                book.setBookType(WISHLIST);
                books.add(book);
            }
        }
        return books;
    }

    private List<Book> getWishListBookAndUserFromResultMap(Result<Map<String, Object>> mapResult) {
        List<Book> books = new ArrayList<>();
        for (Map<String, Object> objectMap : mapResult) {
            RestNode bookNode = (RestNode) objectMap.get("book");
            RestNode userNode = (RestNode) objectMap.get("friend");
            User user = getUserFromRestNode(userNode);
            Book book = getBookFromRestNode(bookNode);

            boolean appended = false;
            for (Book listedBook : books) {
                if (listedBook.getId().equals(book.getId())) {
                    WishlistBookDetails wishlistBookDetails = (WishlistBookDetails) listedBook.getBookDetails();
                    wishlistBookDetails.getUsers().add(mapUserNeo4jToWeb(user));
                    appended = true;
                }
            }
            if (!appended) {
                WishlistBookDetails wishlistBookDetails = new WishlistBookDetails();
                wishlistBookDetails.getUsers().add(mapUserNeo4jToWeb(user));
                book.setBookDetails(wishlistBookDetails);
                book.setBookType(WISHLIST);
                books.add(book);
            }

        }
        return books;
    }


    @Override
    @Transactional
    public void updateOwnedBookStatus(User user, Book book, String status, String userComment) {
        OwnsRelationship relationship = neo4jTemplate.getRelationshipBetween(user, book, OwnsRelationship.class, RelationTypes.OWNS.toString());
        if (relationship == null) //todo: throw an exception
            return;
        relationship.setStatus(status);
        relationship.setLastModifiedDate(System.currentTimeMillis());
        relationship.setUserComment(userComment);
        neo4jTemplate.save(relationship);

        Target target = TargetHelper.createBookTarget(book);
        Notification notification = new Notification(target, System.currentTimeMillis(), Constants.RETURN_INIT_NOTIFICATION_TYPE);
        notificationDao.addNotification(user.getId(), notification);

       //TODO : Book History Event

    }

    @Override
    public void addBookToBorrower(Book book, BorrowRequest borrowRequest) {
        //check both owner and borrower exists
        User borrower = userDao.getUser(borrowRequest.getBorrowerUserId());
        User ownerUser = userDao.getUser(borrowRequest.getOwnerUserId());

        BorrowRelationship borrowRelation = new BorrowRelationship(borrower, book, Constants.BORROW_IN_PROGRESS);
        long now = TimeUtils.getCurrentTime();
        borrowRelation.setCreatedDate(now);
        borrowRelation.setLastModifiedDate(now);
        borrowRelation.setContractPeriodInDays(borrowRequest.getContractPeriodInDays());
        borrowRelation.setAdditionalComments(borrowRequest.getAdditionalMessage());
        borrowRelation.setOwnerUserId(borrowRequest.getOwnerUserId());
        neo4jTemplate.save(borrowRelation);
        //TODO : create notification to owner

        HistoryEvent historyEvent = HistoryEventHelper.createPublicEvent(AuditEventType.BORROW_INITIATED, TargetHelper.createUserTarget(borrower));
        setBookHistory(book.getId(), ownerUser.getId(), historyEvent);
        emailDao.sendBorrowBookInitEmail(borrower, ownerUser, book);

        Target target = TargetHelper.createBookTarget(book);
        Notification notification = new Notification(target, System.currentTimeMillis(), Constants.BORROW_IN_PROGRESS);
        notificationDao.addNotification(ownerUser.getId(), notification);


    }

    @Override
    public void updateBookStatusOnAgreement(User user, Book book, User borrower, String userComment) {
        updateOwnedBookStatus(user, book, BORROW_LOCK, userComment);
        updateBorrowedBookStatus(borrower, book, BORROW_LOCK, userComment);

        HistoryEvent historyEvent = HistoryEventHelper.createPublicEvent(BORROW_AGREED.toString(), TargetHelper.createUserTarget(borrower));
        setBookHistory(book.getId(), user.getId(), historyEvent);
        emailDao.sendAcceptedToLendBookEmail(user, borrower, book);

        Target target = TargetHelper.createBookTarget(book);
		Event event = EventHelper.createPrivateEvent(BORROW_AGREED, target);
		auditEventDao.addEvent(user.getId(), event);
		//auditEventDao.addEvent(borrower.getId(), event);
		//TODO : Revisit


        Notification notification = new Notification(target, System.currentTimeMillis(), Constants.BORROW_AGREED);
        notificationDao.addNotification(borrower.getId(), notification);
    }

    @Override
    public void updateBookStatusOnSuccess(User user, Book book, User borrower, String userComment) {

    	//TODO : Change update methods to have borrower and owner variables
        updateOwnedBookStatus(user, book, Constants.LENT, userComment);
        updateBorrowedBookStatus(borrower, book, Constants.BORROWED, userComment);
        HistoryEvent historyEvent = HistoryEventHelper.createPublicEvent(BORROWED, TargetHelper.createUserTarget(borrower));
        setBookHistory(book.getId(), user.getId(), historyEvent);
        emailDao.sendSuccessfulBookTransactionEmail(user, borrower, book);

        Target target = TargetHelper.createBookTarget(book);
		Event eventBorrower = EventHelper.createPublicEvent(BORROWED, target);
		Event eventOwner = EventHelper.createPublicEvent(LENT, target);
		auditEventDao.addEvent(user.getId(), eventOwner);
		auditEventDao.addEvent(borrower.getId(), eventBorrower);

        Notification notification = new Notification(target, System.currentTimeMillis(), Constants.BORROW_SUCCESS);
        notificationDao.addNotification(borrower.getId(), notification);
    }

    @Override
    @Transactional
    public void updateBorrowedBookStatus(User user, Book book, String status, String userComment) {
        BorrowRelationship relationship = neo4jTemplate.getRelationshipBetween(user, book, BorrowRelationship.class, RelationTypes.BORROWED.toString());
        if (relationship == null) //TODO: throw an exception
            return;
        relationship.setStatus(status);
        relationship.setLastModifiedDate(System.currentTimeMillis());
        relationship.setAdditionalComments(userComment);
        neo4jTemplate.save(relationship);
        //TODO : is notification/Event required
    }

    @Override
    public List<Book> search(String queryString) {
        List<Book> search = goodreadsDao.search(queryString);
        logger.debug("got results back");
        return search;
    }

    @Override
    public List<Book> searchWithRespectToUser(String userId, String searchString) {
        List<Book> searchBooks = goodreadsDao.search(searchString);
        List<Book> existingBooks = getAllUserBooks(userId);

        //todo: make sure already read books comes first

        return replaceBooksWithExistingBooks(searchBooks, existingBooks);
    }

    private List<Book> replaceBooksWithExistingBooks(List<Book> books, List<Book> existingBooks) {

        List<Book> identifiedBooks = new ArrayList<>();
        for (Iterator<Book> iterator = books.iterator(); iterator.hasNext(); ) {
            Book book = iterator.next();
            for (Book existingBook : existingBooks) {
                if(existingBook.getId() != null && book.getId() != null && book.getId().equals(existingBook.getId())){
                    iterator.remove();
                    identifiedBooks.add(existingBook);
                }
                else if (existingBook.getGoodreadsId() != null && book.getGoodreadsId() != null && book.getGoodreadsId().equals(existingBook.getGoodreadsId())) {
                    iterator.remove();
                    identifiedBooks.add(existingBook);
                }
            }
        }
        logger.debug("Identified books count:" + identifiedBooks.size());
        List<Book> resultBooks = new ArrayList<>();
        resultBooks.addAll(identifiedBooks);
        resultBooks.addAll(books);
        return resultBooks;
    }

    @Override
//    @Cacheable(cacheName = "bookByGRIdCache", keyGenerator = @KeyGenerator(name="HashCodeCacheKeyGenerator", properties = @Property( name="includeMethod", value="false")))
    public Book getBookByGoodreadsId(Integer goodreadsId) {
        Book book = bookRepository.findBySchemaPropertyValue("goodreadsId", goodreadsId);
        if (book == null) {
            logger.info("Goodreads book is not in there datsbase fetching from goodreads. Id: " + goodreadsId);
            return getBookFromGRIdAndSave(goodreadsId);
        } else {
            return book;
        }
    }

    private Book getBookFromGRIdAndSave(Integer goodreadsId) {
        final Book bookByGoodreadId = goodreadsDao.getBookById(goodreadsId.toString());
        if(bookByGoodreadId == null){
            throw new NotFoundException("NOT_FOUND", "Book with goodreads id:" + goodreadsId + " not found");
        }
        bookByGoodreadId.setId(UUID.randomUUID().toString());
        createBook(bookByGoodreadId);
        return bookByGoodreadId;
    }

    @Override
    public Book getBookByGoodreadsIdAndSaveIfNotExists(@PartialCacheKey Integer goodreadsId, Book book) {
        Book bookByGoodreadsId = bookRepository.findBySchemaPropertyValue("goodreadsId", goodreadsId);
        if (bookByGoodreadsId == null) {
            book.setId(UUID.randomUUID().toString());
            return createBook(book);
        }
        return bookByGoodreadsId;
    }

    @Override
    public void addWishBookToUser(WishListRelationship wishListRelationship) {
    	WishListRelationship existingWishListRelationship = bookRepository.getWishListRelationship(wishListRelationship.getUser().getId(), wishListRelationship.getBook().getId());
    	if(null ==existingWishListRelationship)
    	{
    		neo4jTemplate.save(wishListRelationship);
    		Target target = TargetHelper.createBookTarget(wishListRelationship.getBook());
    		Event event = EventHelper.createPublicEvent(AuditEventType.BOOK_ADDED_WISHLIST, target);
    		auditEventDao.addEvent(wishListRelationship.getUser().getId(), event);
    	}
    }

    @Override
    public void createGoodreadsFriendBookRec(GoodreadsRecRelationship goodreadsFriendBookRecRelation) {
        neo4jTemplate.save(goodreadsFriendBookRecRelation);
    }

    @Override
    public Book getBookByIsbn(String isbn) throws IOException {
        Book book;
        if (isbn.length() == 13)
            book = bookRepository.findBySchemaPropertyValue("isbn13", isbn);
        else
            book = bookRepository.findBySchemaPropertyValue("isbn", isbn);
        if (book == null) {
            book = goodreadsDao.getBookByISBN(isbn);
            book = getBookByGoodreadsIdAndSaveIfNotExists(book.getGoodreadsId(), book);
        }
        return book;
    }

    @Override
    //cached
    public Book getBookRelatedUser(String bookId, String userId) {
        List<Book> allUserBooks = getAllUserBooks(userId);
        for(Book book: allUserBooks){
            if(book.getId().equals(bookId)){
                return book;
            }
        }
        return getBook(bookId);
    }

    @Override
    public List<Book> getAllUserBooks(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        Result<Map<String, Object>> mapResult = neo4jTemplate.query(GET_ALL_BOOKS_QUERY, params);
        //todo throw not found
        List<Book> books = getWishlistBooksWithDetails(userId);
        List<Book> bookFromMapResult = getBookFromMapResult(mapResult, userId);
        return replaceBooksWithExistingBooks(bookFromMapResult, books);
    }

    @Override
    public Book getBookByGoodreadsIdWithUser(Integer goodreadsId, String userId) {
        //Make sure book already exists in DB:
        Book bookByGoodreadsId = getBookByGoodreadsId(goodreadsId);
        List<Book> allUserBooks = getAllUserBooks(userId);
        for(Book book: allUserBooks){
            if(book.getGoodreadsId() != null && book.getGoodreadsId().equals(goodreadsId)){
                return book;
            }
        }

        return bookByGoodreadsId;
    }

    private List<Book> getBookFromMapResult(Result<Map<String, Object>> mapResult, String loggedInUser) {
        List<Book> books = new ArrayList<>();
        for (Map<String, Object> objectMap : mapResult) {
            RestNode bookNode = (RestNode) objectMap.get("book");
            RestRelationship rawWishRelationship = (RestRelationship) objectMap.get("relation");
            Book book = getBookFromRestNode(bookNode);
            book = setRelationDetailsToBook(rawWishRelationship, book, loggedInUser);
            books.add(book);
        }
        return books;
    }


    private Book setRelationDetailsToBook(RestRelationship rawRelationship, Book book, String loggedInUser) {
        switch (rawRelationship.getType().name()) {
            case OWNS_RELATION:
                book.setBookType(OWNS_RELATION);
                OwnsRelationship ownsRelationship = getOwnsRelationship(rawRelationship);
                OwnedBookDetails ownedBookDetailsFromRelation = getOwnedBookDetailsFromRelation(ownsRelationship);
                //todo: set history events
                //todo: set those who are looking for
                book.setBookDetails(ownedBookDetailsFromRelation);
                break;
            case BORROWED_RELATION:
                book.setBookType(BORROWED_RELATION);
                BorrowRelationship borrowBookRelationship = getBorrowBookRelationship(rawRelationship);
                BorrowedBookDetails borrowBookDetailsFromRelation = getBorrowBookDetailsFromRelation(borrowBookRelationship);
                book.setBookDetails(borrowBookDetailsFromRelation);
                //todo: who already read the book
                break;
            case READ_RELATION:
                book.setBookType(READ_RELATION);
                break;

            case WISHLIST_RELATION:
                book.setBookType(WISHLIST_RELATION);
                break;
            case CURRENTLY_READING_RELATION:
                book.setBookType(CURRENTLY_READING_RELATION);
                break;
            default:
                book.setBookType("NONE");
                break;
        }
        return book;
    }


    private Book setRelationDetailsToBook(RestRelationship rawRelationship, Book book) {
        switch (rawRelationship.getType().name()) {
            case OWNS_RELATION:
                book.setBookType(OWNS_RELATION);
                OwnsRelationship ownsRelationship = getOwnsRelationship(rawRelationship);
                OwnedBookDetails ownedBookDetailsFromRelation = getOwnedBookDetailsFromRelation(ownsRelationship);
                //todo: set history events
                //todo: set those who are looking for
                book.setBookDetails(ownedBookDetailsFromRelation);
                break;
            case BORROWED_RELATION:
                book.setBookType(BORROWED_RELATION);
                BorrowRelationship borrowBookRelationship = getBorrowBookRelationship(rawRelationship);
                BorrowedBookDetails borrowBookDetailsFromRelation = getBorrowBookDetailsFromRelation(borrowBookRelationship);
                book.setBookDetails(borrowBookDetailsFromRelation);
                //todo: who already read the book
                break;
            case READ_RELATION:
                book.setBookType(READ_RELATION);
                break;

            case WISHLIST_RELATION:
                book.setBookType(WISHLIST_RELATION);
                break;
            case CURRENTLY_READING_RELATION:
                book.setBookType(CURRENTLY_READING_RELATION);
                break;
            default:
                book.setBookType("NONE");
                break;
        }
        return book;
    }

    private BorrowedBookDetails getBorrowBookDetailsFromRelation(BorrowRelationship borrowBookRelationship) {
        BorrowedBookDetails borrowedBookDetails = new BorrowedBookDetails();
        borrowedBookDetails.setAdditionalComments(borrowBookRelationship.getAdditionalComments());
        borrowedBookDetails.setBorrowDate(borrowBookRelationship.getBorrowDate());
        borrowedBookDetails.setOwnerUserId(mapUserNeo4jToWeb(userDao.getUser(borrowBookRelationship.getOwnerUserId())));
        borrowedBookDetails.setContractPeriodInDays(borrowBookRelationship.getContractPeriodInDays());
        borrowedBookDetails.setStatus(borrowBookRelationship.getStatus());
        return borrowedBookDetails;
    }

    private OwnedBookDetails getOwnedBookDetailsFromRelation(OwnsRelationship ownsRelationship) {
        OwnedBookDetails ownedBookDetails = new OwnedBookDetails();
        BorrowedBookDetails borrowedBookDetails = new BorrowedBookDetails();
        borrowedBookDetails.setAdditionalComments(ownsRelationship.getUserComment());
        borrowedBookDetails.setBorrowDate(ownsRelationship.getLentDate());
        borrowedBookDetails.setBorrower(mapUserNeo4jToWeb(userDao.getUser(ownsRelationship.getBorrowerId())));
        borrowedBookDetails.setContractPeriodInDays(ownsRelationship.getContractPeriodInDays());
        borrowedBookDetails.setStatus(ownsRelationship.getStatus());
        ownedBookDetails.setBorrowedBookDetails(borrowedBookDetails);
        return ownedBookDetails;
    }


    @Override
    public List<GoodreadsUserRecommendation> getGoodreadsUserRecommendations(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        Result<Map<String, Object>> mapResult = neo4jTemplate.query(GOODREADS_USER_REC_QUERY, params);
        return getWishUserRecFromResultMap(mapResult);
    }


    @Override
//    @Cacheable(cacheName = "userWishBooks", keyGenerator = @KeyGenerator(name = "HashCodeCacheKeyGenerator", properties = @Property(name = "includeMethod", value = "false")))
    public List<WishListBook> getWishListBooks(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        Result<Map<String, Object>> mapResult = neo4jTemplate.query(WISHLIST_BOOKS_QUERY, params);
        return getWishListBooksFromResultMap(mapResult);
    }

    @Override
    public List<Book> getReadBooks(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        Result<Map<String, Object>> mapResult = neo4jTemplate.query(READ_BOOKS_QUERY, params);
        return getBooksFromResultMap(mapResult);

    }

    @Override
//    @Cacheable(cacheName = "userOwnedBooks", keyGenerator = @KeyGenerator(name = "HashCodeCacheKeyGenerator", properties = @Property(name = "includeMethod", value = "false")))
    public List<OwnedBook> getOwnedBooks(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        Result<Map<String, Object>> mapResult = neo4jTemplate.query(OWNED_BOOKS_BOOKS, params);
        return getOwnedBooksFromResultMap(mapResult);
    }

    @Override
    public List<OwnedBook> getAvailableBooks(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        Result<Map<String, Object>> mapResult = neo4jTemplate.query(AVAILABLE_BOOKS_QUERY, params);
        return getOwnedBooksFromResultMap(mapResult);
    }

    @Override
    public List<OwnedBook> getLentBooks(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        Result<Map<String, Object>> mapResult = neo4jTemplate.query(LENT_BOOKS_QUERY, params);
        return getOwnedBooksFromResultMap(mapResult);
    }

    private List<OwnedBook> getOwnedBooksFromResultMap(Result<Map<String, Object>> mapResult) {
        List<OwnedBook> ownedBooks = new ArrayList<>();
        for (Map<String, Object> objectMap : mapResult) {
            RestNode bookNode = (RestNode) objectMap.get("books");
            RestRelationship rawOwnsRelationship = (RestRelationship) objectMap.get("relation");

            Book book = getBookFromRestNode(bookNode);
            setRelationDetailsToBook(rawOwnsRelationship, book);

            com.campusconnect.neo4j.types.web.Book webBook = Neo4jToWebMapper.mapBookNeo4jToWeb(book);
            OwnedBook ownedBook = new OwnedBook(webBook);
            ownedBooks.add(ownedBook);
        }
        return ownedBooks;
    }

    @Override
//    @Cacheable(cacheName = "userBorrowedBooks", keyGenerator = @KeyGenerator(name = "HashCodeCacheKeyGenerator", properties = @Property(name = "includeMethod", value = "false")))
    public List<BorrowedBook> getBorrowedBooks(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        Result<Map<String, Object>> mapResult = neo4jTemplate.query(BORROWED_BOOKS_QUERY, params);
        return getBorrowedBooksFromResultMap(mapResult);
    }

    private List<Book> getBooksFromResultMap(Result<Map<String, Object>> mapResult) {
        List<Book> books = new ArrayList<>();
        for (Map<String, Object> objectMap : mapResult) {
            RestNode bookNode = (RestNode) objectMap.get("books");
            Book book = getBookFromRestNode(bookNode);
            books.add(book);
        }
        return books;
    }

    private List<WishListBook> getWishListBooksFromResultMap(Result<Map<String, Object>> mapResult) {
        List<WishListBook> wishListBooks = new ArrayList<>();
        for (Map<String, Object> objectMap : mapResult) {
            RestNode bookNode = (RestNode) objectMap.get("books");
            RestRelationship rawWishRelationship = (RestRelationship) objectMap.get("relation");

            Book book = getBookFromRestNode(bookNode);
            setRelationDetailsToBook(rawWishRelationship, book);

            com.campusconnect.neo4j.types.web.Book webBook = Neo4jToWebMapper.mapBookNeo4jToWeb(book);
            WishListBook wishListBook = new WishListBook(webBook);

            wishListBooks.add(wishListBook);
        }
        return wishListBooks;
    }

    private List<CurrentlyReadingBook> getCurrentlyReadingBookFromResultMap(Result<Map<String, Object>> mapResult) {
        List<CurrentlyReadingBook> currentlyReadingBooks = new ArrayList<>();
        for (Map<String, Object> objectMap : mapResult) {
            RestNode bookNode = (RestNode) objectMap.get("books");
            RestRelationship rawWishRelationship = (RestRelationship) objectMap.get("relation");

            Book book = getBookFromRestNode(bookNode);
            setRelationDetailsToBook(rawWishRelationship, book);

            com.campusconnect.neo4j.types.web.Book webBook = Neo4jToWebMapper.mapBookNeo4jToWeb(book);
            CurrentlyReadingBook currentlyReadingBook = new CurrentlyReadingBook(webBook);

            currentlyReadingBooks.add(currentlyReadingBook);
        }
        return currentlyReadingBooks;
    }

    private List<GoodreadsUserRecommendation> getWishUserRecFromResultMap(Result<Map<String, Object>> mapResult) {
        List<GoodreadsUserRecommendation> goodreadsUserRecommendations = new ArrayList<>();
        for (Map<String, Object> objectMap : mapResult) {
            RestNode bookNode = (RestNode) objectMap.get("books");
            RestRelationship rawWishRelationship = (RestRelationship) objectMap.get("relation");

            Book book = getBookFromRestNode(bookNode);
            book.setBookType("WISH");
            GoodreadsRecRelationship goodreadsRecRelation = DBMapper.getGoodreadsRecRelationship(rawWishRelationship);

            GoodreadsUserRecommendation goodreadsUserRecommendation = new GoodreadsUserRecommendation(Neo4jToWebMapper.mapBookNeo4jToWeb(book));
            goodreadsUserRecommendation.setFriendGoodreadsId(goodreadsRecRelation.getFriendGoodreadsId());
            goodreadsUserRecommendation.setFriendImageUrl(goodreadsRecRelation.getFriendImageUrl());
            goodreadsUserRecommendation.setFriendName(goodreadsRecRelation.getFriendName());
            goodreadsUserRecommendation.setCreateDate(goodreadsRecRelation.getCreatedDate());
            goodreadsUserRecommendation.setFriendId(goodreadsRecRelation.getFriendId());
            goodreadsUserRecommendations.add(goodreadsUserRecommendation);
        }
        return goodreadsUserRecommendations;
    }

    private List<BorrowedBook> getBorrowedBooksFromResultMap(Result<Map<String, Object>> mapResult) {
        List<BorrowedBook> borrowedBooks = new ArrayList<>();
        for (Map<String, Object> objectMap : mapResult) {
            RestNode bookNode = (RestNode) objectMap.get("books");
            RestRelationship rawBorrowRelationship = (RestRelationship) objectMap.get("relation");
            Book book = getBookFromRestNode(bookNode);
            setRelationDetailsToBook(rawBorrowRelationship, book);

            com.campusconnect.neo4j.types.web.Book webBook = Neo4jToWebMapper.mapBookNeo4jToWeb(book);
            BorrowedBook borrowedBook = new BorrowedBook(webBook);
            borrowedBooks.add(borrowedBook);
        }
        return borrowedBooks;
    }

    @Override
    public List<HistoryEvent> getBookHistory(String bookId, String userId) {

        OwnsRelationship ownsRelationship = bookRepository.getOwnsRelationship(userId, bookId);
        Set<String> historyEvents = ownsRelationship.getHistoryEvents();
        List<HistoryEvent> historyEventList = new ArrayList<HistoryEvent>();

        for (String historyEvent : historyEvents) {
            historyEventList.add(HistoryEventHelper.deserializeEventString(historyEvent));
        }

        return historyEventList;
    }

    @Override
    public void deleteBorrowRequest(String borrowerId,
                                    String bookId, String ownerId, String message) {
        BorrowRelationship borrowRelationship = bookRepository.getBorrowRelationship(borrowerId, bookId, ownerId);
        if (null != borrowRelationship) {
            neo4jTemplate.delete(borrowRelationship);
            emailDao.sendRejectedToLendBookEmail(userDao.getUser(ownerId), userDao.getUser(borrowerId), getBook(bookId), message);
            //TODO: put notification to borrower
            HistoryEvent historyEventRejectBorrow = HistoryEventHelper.createPublicEvent(AuditEventType.BORROW_REJECTED.toString(), TargetHelper.createUserTarget(userDao.getUser(borrowerId)));
            setBookHistory(bookId, ownerId, historyEventRejectBorrow);

            Target target = TargetHelper.createBookTarget(getBook(bookId));
    		Event event = EventHelper.createPrivateEvent(AuditEventType.BORROW_REJECTED.toString(), target);
    		auditEventDao.addEvent(ownerId, event);

            Notification notification = new Notification(target, System.currentTimeMillis(), Constants.BORROW_REJECT);
            notificationDao.addNotification(borrowerId, notification);

        }
    }

    private void setBookHistory(String bookId, String ownerId, HistoryEvent historyEvent) {
        OwnsRelationship ownsRelationship = bookRepository.getOwnsRelationship(ownerId, bookId);
        if(ownsRelationship != null){
            Set<String> historyEvents = ownsRelationship.getHistoryEvents();
            historyEvents.add(HistoryEventHelper.serializeEvent(historyEvent));
            neo4jTemplate.save(ownsRelationship);
        }
    }

    @Override
    public void listBookAsCurrentlyReading(
            CurrentlyReadingRelationShip currentlyReadingRelationship) {

    		CurrentlyReadingRelationShip existingCurrentlyReadingRelationShip = bookRepository.getCurrentlyReadingRelationShip(currentlyReadingRelationship.getUser().getId(),currentlyReadingRelationship.getBook().getId());
    		if(null == existingCurrentlyReadingRelationShip)
    		{
    			neo4jTemplate.save(currentlyReadingRelationship);
    			Target target = TargetHelper.createBookTarget(currentlyReadingRelationship.getBook());
        		Event event = EventHelper.createPublicEvent(AuditEventType.BOOK_ADDED_CURRENTLY_READING.toString(), target);
        		auditEventDao.addEvent(currentlyReadingRelationship.getUser().getId(), event);
    		}
    }

    @Override
    public List<CurrentlyReadingBook> getCurrentlyReadingBook(String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        Result<Map<String, Object>> mapResult = neo4jTemplate.query(CURRENTLY_READING_BOOKS_QUERY, params);
        return getCurrentlyReadingBookFromResultMap(mapResult);
    }
}
