package com.sjtudoit.majiang.dto;

public class Message {
    private Integer type;
    private String userName;
    private String message;

    public Message() {
    }

    public Message(Integer type) {
        this.type = type;
    }

    public Message(Integer type, String message) {
        this.type = type;
        this.message = message;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
