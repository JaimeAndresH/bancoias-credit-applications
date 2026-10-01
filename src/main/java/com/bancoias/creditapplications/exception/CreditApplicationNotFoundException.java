package com.bancoias.creditapplications.exception;

public class CreditApplicationNotFoundException extends RuntimeException{
    public CreditApplicationNotFoundException(String message) {
        super(message);
    }
}
