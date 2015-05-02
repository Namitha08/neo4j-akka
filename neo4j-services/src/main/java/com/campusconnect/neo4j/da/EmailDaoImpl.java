package com.campusconnect.neo4j.da;

import com.campusconnect.neo4j.akka.util.Neo4jAsynchHandler;
import com.campusconnect.neo4j.da.iface.EmailDao;
import com.campusconnect.neo4j.types.Book;
import com.campusconnect.neo4j.types.User;

/**
 * Created by sn1 on 5/1/15.
 */
public class EmailDaoImpl implements EmailDao {
    
    private Neo4jAsynchHandler neo4jAsynchHandler;

    public EmailDaoImpl(Neo4jAsynchHandler neo4jAsynchHandler) {
        this.neo4jAsynchHandler = neo4jAsynchHandler;
    }

    @Override
    public void sendBorrowBookInitEmail(User fromUser, User toUser, Book book) {
        neo4jAsynchHandler.sendBorrowInitEmail(fromUser, toUser, book);
    }
}