package io.hhplus.tdd.point;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum PointErrorCode {

    NON_POSITIVE_AMOUNT("E101", HttpStatus.BAD_REQUEST, "0이하의 값은 사용할 수 없습니다."),
    MAX_POINT_EXCEED("E102", HttpStatus.BAD_REQUEST, "최대 포인트가 초과되었습니다."),
    NOT_ENOUGH_POINT("E103", HttpStatus.BAD_REQUEST, "포인트가 부족합니다."),
    ;

    private final String code;
    private final HttpStatus status;
    private final String message;
}
