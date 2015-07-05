package com.campusconnect.neo4j.types.common;

public class Target {

    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    private String displayString;
    private String idType;
    private String url;

    public Target() {

    }

    public Target(String idType, String displayString, String url, String id) {
        super();
        this.id = id;
        this.idType = idType;
        this.displayString = displayString;
        this.url = url;
    }


    public String getDisplayString() {
        return displayString;
    }

    public void setDisplayString(String displayString) {
        this.displayString = displayString;
    }

    public String getIdType() {
        return idType;
    }

    public void setIdType(String idType) {
        this.idType = idType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}
