package com.nantaaditya.sotres.model.dto;

public record RetryHistoryContext(
    int counter,
    String response,
    String lastError
) {

}
