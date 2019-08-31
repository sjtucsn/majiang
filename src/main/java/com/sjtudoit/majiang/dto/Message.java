package com.sjtudoit.majiang.dto;

public class Message<T> {
    private Integer type;
    private T message;

    public Message() {
    }

    public Message(Integer type) {
        this.type = type;
    }

    public Message(Integer type, T message) {
        this.type = type;
        this.message = message;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public T getMessage() {
        return message;
    }

    public void setMessage(T message) {
        this.message = message;
    }
}
