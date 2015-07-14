package com.campusconnect.neo4j.akka.goodreads.api;

import com.campusconnect.neo4j.akka.goodreads.client.GoodReadsClient;
import com.campusconnect.neo4j.akka.goodreads.types.GetBookResponse;
import com.campusconnect.neo4j.akka.goodreads.util.ResponseUtils;
import com.campusconnect.neo4j.exceptions.NotFoundException;
import com.campusconnect.neo4j.util.ErrorCodes;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.campusconnect.neo4j.akka.goodreads.client.GoodReadsClient.getDefaultHeaders;

/**
 * Created by sn1 on 3/10/15.
 */
public class GetBook {

    private static Logger logger = LoggerFactory.getLogger(GetBook.class);

    private GoodReadsClient goodReadsClient;

    public GetBook(GoodReadsClient goodReadsClient) {
        this.goodReadsClient = goodReadsClient;
    }

    public static void main(String[] args) throws IOException {
        GetBook getBook = new GetBook(new GoodReadsClient("https://www.goodreads.com", "QLM3lL2nqXe4LujHQt12A"));
        getBook.getBookById("1768603");
        getBook.getBookByISBN("1416562591");
    }

    public GetBookResponse getBookById(String goodreadsId) throws IOException {
        ClientResponse clientResponse = goodReadsClient.path("book/show").path(goodreadsId).addAppKeyQueryParam().queryParam("format", "xml").header(getDefaultHeaders()).get(ClientResponse.class);
        if (clientResponse.getStatus() == 404) {
            logger.warn("Book with grId:" + goodreadsId + " not found");
            throw new NotFoundException(ErrorCodes.BOOK_NOT_FOUND, "Book with grId:" + goodreadsId + " not found");
        }
        String theString = IOUtils.toString(clientResponse.getEntityInputStream());
        return ResponseUtils.getEntity(theString, GetBookResponse.class);
    }

    public GetBookResponse getBookByISBN(String isbn) throws IOException {
        ClientResponse clientResponse = goodReadsClient.path("book/isbn").addAppKeyQueryParam().queryParam("isbn", isbn).header(getDefaultHeaders()).get(ClientResponse.class);
        if (clientResponse.getStatus() == 404) {
            logger.warn("Book with isbn:" + isbn + " not found");
            throw new NotFoundException(ErrorCodes.BOOK_NOT_FOUND, "Book with isbn:" + isbn + " not found");
        }
        String theString = IOUtils.toString(clientResponse.getEntityInputStream());
        return ResponseUtils.getEntity(theString, GetBookResponse.class);
    }

}
