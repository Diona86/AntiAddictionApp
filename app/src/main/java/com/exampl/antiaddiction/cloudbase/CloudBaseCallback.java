package com.exampl.antiaddiction.cloudbase;

// CloudBaseCallback.java
public interface CloudBaseCallback<T> {
    void onSuccess(T data);
    void onError(int code, String message);
}