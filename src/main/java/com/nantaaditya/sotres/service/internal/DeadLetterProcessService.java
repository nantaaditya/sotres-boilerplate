package com.nantaaditya.sotres.service.internal;

import com.nantaaditya.sotres.model.request.RetryDeadLetterProcessRequest;

public interface DeadLetterProcessService {
    void remove(int days);

    void retry(RetryDeadLetterProcessRequest request);
}
