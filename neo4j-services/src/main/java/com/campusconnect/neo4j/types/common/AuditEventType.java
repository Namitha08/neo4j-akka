package com.campusconnect.neo4j.types.common;


public enum AuditEventType {
    FOLLOWING,
    FRIEND,
    BORROWED,
    WISHLIST,
    USER_CREATED,
    USER_UPDATED,
    UPDATED_ADDRESS,
    ADDED_ADDRESS, FOLLOWED, LENT,
    BORROW_REJECTED,
    BORROW_INITIATED,
    BORROW_AGREED,
    REMINDER_SENT, DELETED_ADDRESS,
    BOOK_ADDED_OWNS,BOOK_ADDED_WISHLIST,BOOK_ADDED_READ,BOOK_ADDED_CURRENTLYREADING,RETURN_INITIATED;
}
