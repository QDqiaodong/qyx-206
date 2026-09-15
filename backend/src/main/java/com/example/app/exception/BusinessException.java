package com.example.app.exception;

/**
 * 业务规则异常（参数或前置条件不满足），统一返回 400。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
