package com.jaconis.finance_api.exception;

public record ValidationFieldError(String field, String message) {}
