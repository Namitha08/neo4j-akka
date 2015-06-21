package com.campusconnect.neo4j.akka.goodreads.task;

import com.campusconnect.neo4j.types.neo4j.Book;

/**
 * Created by sn1 on 3/10/15.
 */
public class SaveBookTask {
    final private Book book;

    public SaveBookTask(Book book) {
        this.book = book;
    }

    public Book getBook() {
        return book;
    }
}
