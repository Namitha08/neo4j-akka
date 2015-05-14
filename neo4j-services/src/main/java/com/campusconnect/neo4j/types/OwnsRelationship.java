package com.campusconnect.neo4j.types;

import org.springframework.data.neo4j.annotation.EndNode;
import org.springframework.data.neo4j.annotation.GraphId;
import org.springframework.data.neo4j.annotation.RelationshipEntity;
import org.springframework.data.neo4j.annotation.StartNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by sn1 on 2/25/15.
 */
@RelationshipEntity(type = "OWNS")
public class OwnsRelationship extends BookRelation {

	private String borrowerId;

	private Integer contractPeriodInDays;

	private String dueDate;

	private String goodreadsStatus;
    
    private String userComment;

    public String getUserComment() {
        return userComment;
    }

    public void setUserComment(String userComment) {
        this.userComment = userComment;
    }

    public void setContractPeriodInDays(Integer contractPeriodInDays) {

        this.contractPeriodInDays = contractPeriodInDays;
    }

    public OwnsRelationship() {
		super();
	}

	public OwnsRelationship(User user, Book book, long createdDate,
			String status, long lastModifiedDate) {
		super(user, book, status, createdDate, lastModifiedDate);
	}

	public OwnsRelationship(User user, Book book, long createdDate,
			String status, long lastModifiedDate, String goodreadsStatus) {
		super(user, book, status, createdDate, lastModifiedDate);
		this.goodreadsStatus = goodreadsStatus;
	}

	public String getBorrowerId() {
		return borrowerId;
	}

	public int getContractPeriodInDays() {
		return contractPeriodInDays;
	}

	public String getDueDate() {
		return dueDate;
	}

	public String getGoodreadsStatus() {
		return goodreadsStatus;
	}

	public void setBorrowerId(String borrowerId) {

		this.borrowerId = borrowerId;
	}

	public void setContractPeriodInDays(int contractPeriodInDays) {
		this.contractPeriodInDays = contractPeriodInDays;
	}

	public void setDueDate(String dueDate) {
		this.dueDate = dueDate;
	}

	public void setGoodreadsStatus(String goodreadsStatus) {
		this.goodreadsStatus = goodreadsStatus;
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OwnsRelationship)) return false;

        OwnsRelationship that = (OwnsRelationship) o;

        if (contractPeriodInDays != that.contractPeriodInDays) return false;
        if (borrowerId != null ? !borrowerId.equals(that.borrowerId) : that.borrowerId != null) return false;
        if (dueDate != null ? !dueDate.equals(that.dueDate) : that.dueDate != null) return false;
        if (goodreadsStatus != null ? !goodreadsStatus.equals(that.goodreadsStatus) : that.goodreadsStatus != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = borrowerId != null ? borrowerId.hashCode() : 0;
        result = 31 * result + contractPeriodInDays;
        result = 31 * result + (dueDate != null ? dueDate.hashCode() : 0);
        result = 31 * result + (goodreadsStatus != null ? goodreadsStatus.hashCode() : 0);
        return result;
    }

    public Map<String, String> getFieldsAsMap() {
        Map<String, String> fields = new HashMap<>();
        if(contractPeriodInDays != null)
            fields.put("contractPeriodInDays", String.valueOf(contractPeriodInDays));
        if (borrowerId != null )
            fields.put("borrowerId", borrowerId);
        if (dueDate != null)
            fields.put("dueDate", dueDate);
        if (goodreadsStatus != null)
            fields.put("goodreadsStatus", goodreadsStatus);
        return fields;
    }
}
