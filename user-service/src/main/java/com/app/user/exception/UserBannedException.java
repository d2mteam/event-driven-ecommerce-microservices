package com.app.user.exception;

public class UserBannedException extends RuntimeException {

    public UserBannedException() {
        super("Tài khoản đã bị khóa");
    }
}
