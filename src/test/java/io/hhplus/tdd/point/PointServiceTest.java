package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    private final long USER_ID = 1L;
    private final Long USER_POINT = 1000L;
    private UserPoint userPoint;

    @Mock
    PointHistoryTable pointHistoryTable;

    @Mock
    UserPointTable userPointTable;

    @InjectMocks
    PointService pointService;

    @BeforeEach
    void setUp() {
        userPoint = new UserPoint(USER_ID, USER_POINT, System.currentTimeMillis());
    }

    @Test
    @DisplayName("User Point 조회 (신규)")
    void findNewUserPointTest() {
        given(userPointTable.selectById(USER_ID)).
                willReturn(new UserPoint(USER_ID, 0L, System.currentTimeMillis()));

        UserPoint actualUserPoint = pointService.findUserPoint(USER_ID);

        assertThat(actualUserPoint.id()).isEqualTo(USER_ID);
        assertThat(actualUserPoint.point()).isEqualTo(0L);
    }

    @Test
    @DisplayName("User Point 조회")
    void findExistingUserPointTest() {
        given(userPointTable.selectById(USER_ID))
                .willReturn(userPoint);

        UserPoint actualUserPoint = pointService.findUserPoint(USER_ID);

        assertThat(actualUserPoint.id()).isEqualTo(USER_ID);
        assertThat(actualUserPoint.point()).isEqualTo(USER_POINT);
    }

    @Test
    @DisplayName("Point History 조회 (신규)")
    void findEmptyPointHistoryTest(){
        given(pointHistoryTable.selectAllByUserId(USER_ID))
                .willReturn(Collections.emptyList());

        List<PointHistory> actualPointHistories = pointService.findPointHistory(USER_ID);

        assertThat(actualPointHistories).isEmpty();
    }


    @Test
    @DisplayName("Point History 조회")
    void findUserPointHistoryTest() {
        ArrayList<PointHistory> pointHistories = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                PointHistory chargeHistory = new PointHistory(i, USER_ID, 1000L, TransactionType.CHARGE, System.currentTimeMillis());
                pointHistories.add(chargeHistory);
            } else {
                PointHistory useHistory = new PointHistory(i, USER_ID, 500L, TransactionType.USE, System.currentTimeMillis());
                pointHistories.add(useHistory);
            }
        }

        given(pointHistoryTable.selectAllByUserId(USER_ID))
                .willReturn(pointHistories);

        List<PointHistory> actualPointHistories = pointService.findPointHistory(USER_ID);

        assertThat(actualPointHistories.size()).isEqualTo(pointHistories.size());
    }

    @Test
    @DisplayName("0이하 포인트 충전")
    void nonPositiveAmountChargeTest() {
        List<Long> nonPositiveAmounts = List.of(-1000L, 0L);

        for (Long nonPositiveAmount : nonPositiveAmounts) {

            PointException pointException = assertThrows(PointException.class,
                    () -> pointService.charge(USER_ID, nonPositiveAmount));

            assertEquals(PointErrorCode.NON_POSITIVE_AMOUNT.getCode(), pointException.getErrorCode());
            assertEquals(PointErrorCode.NON_POSITIVE_AMOUNT.getStatus(), pointException.getHttpStatus());
            assertEquals(PointErrorCode.NON_POSITIVE_AMOUNT.getMessage(), pointException.getMessage());
        }
    }

    @Test
    @DisplayName("100,000 초과 포인트 충전")
    void maxPointExceedChargeTest() {

        given(userPointTable.selectById(USER_ID))
                .willReturn(userPoint);

        List<Long> exceedAmounts = List.of(99001L, 1000000L);

        for (Long exceedAmount : exceedAmounts) {

            PointException pointException = assertThrows(PointException.class,
                    () -> pointService.charge(USER_ID, exceedAmount));

            assertEquals(PointErrorCode.MAX_POINT_EXCEED.getCode(), pointException.getErrorCode());
            assertEquals(PointErrorCode.MAX_POINT_EXCEED.getStatus(), pointException.getHttpStatus());
            assertEquals(PointErrorCode.MAX_POINT_EXCEED.getMessage(), pointException.getMessage());
        }
    }

    @Test
    @DisplayName("충전")
    void chargeTest() {

        given(userPointTable.selectById(USER_ID))
                .willReturn(userPoint);

        given(userPointTable.insertOrUpdate(USER_ID, 11000L))
                .willReturn(new UserPoint(USER_ID, 11000L, System.currentTimeMillis()));


        UserPoint actualUserPoint = pointService.charge(USER_ID, 10000L);

        assertThat(actualUserPoint.id()).isEqualTo(USER_ID);
        assertThat(actualUserPoint.point()).isEqualTo(11000L);
    }

    @Test
    @DisplayName("포인트 사용시 잔여 포인트가 0이하인 경우")
    void notEnoughPointUseTest() {

        given(userPointTable.selectById(USER_ID))
                .willReturn(userPoint); // 기존 1000L

        List<Long> notEnoughPoints = List.of(10000L, 1001L);

        for (Long notEnoughPoint : notEnoughPoints) {

            PointException pointException = assertThrows(PointException.class,
                    () -> pointService.use(USER_ID, notEnoughPoint));

            assertEquals(pointException.getErrorCode(), PointErrorCode.NOT_ENOUGH_POINT.getCode());
            assertEquals(pointException.getHttpStatus(), PointErrorCode.NOT_ENOUGH_POINT.getStatus());
            assertEquals(pointException.getMessage(), PointErrorCode.NOT_ENOUGH_POINT.getMessage());
        }
    }

    @Test
    @DisplayName("포인트 사용")
    void useTest() {
        given(userPointTable.selectById(USER_ID))
                .willReturn(userPoint);

        long usingPoint = 1000L;
        long remainingPoint = userPoint.point() - usingPoint;

        given(userPointTable.insertOrUpdate(USER_ID, remainingPoint))
                .willReturn(new UserPoint(USER_ID, 0L, System.currentTimeMillis()));

        UserPoint actualUserPoint = pointService.use(USER_ID, usingPoint);

        assertThat(actualUserPoint.id()).isEqualTo(USER_ID);
        assertThat(actualUserPoint.point()).isEqualTo(remainingPoint);
    }

}