package io.hhplus.tdd.point;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public class PointException extends RuntimeException {

    private final PointErrorCode errorCode;

    @Override
    public String getMessage() {
        return this.errorCode.getMessage();
    }

    public String getErrorCode() {
        return this.errorCode.getCode();
    }

    public HttpStatus getHttpStatus() {
        return this.errorCode.getStatus();
    }
}
