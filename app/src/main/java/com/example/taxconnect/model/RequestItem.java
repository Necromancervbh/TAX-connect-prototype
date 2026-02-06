package com.example.taxconnect.model;

public class RequestItem {
    public static final int TYPE_CONVERSATION = 0;
    public static final int TYPE_BOOKING = 1;

    private int type;
    private Object data;

    public RequestItem(int type, Object data) {
        this.type = type;
        this.data = data;
    }

    public int getType() { return type; }
    public Object getData() { return data; }
}
