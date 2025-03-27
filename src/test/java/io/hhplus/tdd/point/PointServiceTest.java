package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    private final long USER_ID = 1L;
    private final Long USER_POINT = 1000L;
    private final Lock lock = new ReentrantLock();
    private UserPoint userPoint;
    private UserPoint emptyUserPoint;

    @Mock
    PointHistoryTable pointHistoryTable;

    @Mock
    UserPointTable userPointTable;

    @Mock
    LockManager lockManager;

    @InjectMocks
    PointService pointService;

    @BeforeEach
    void setUp() {
        userPoint = new UserPoint(USER_ID, USER_POINT, System.currentTimeMillis());
        emptyUserPoint = new UserPoint(USER_ID, 0, System.currentTimeMillis());
    }

    @Nested
    @DisplayName("UserPoint 조회")
    class findUserPoint {

        @Test
        @DisplayName("신규 User Point 조회")
        void findNewUserPointTest() {
            // given
            given(userPointTable.selectById(USER_ID)).
                    willReturn(emptyUserPoint); // 잔여: 0L

            // when
            UserPoint actualUserPoint = pointService.findUserPoint(USER_ID);

            // then
            assertThat(actualUserPoint.id()).isEqualTo(USER_ID);
            assertThat(actualUserPoint.point()).isEqualTo(0L);
        }

        @Test
        @DisplayName("기존 User Point 조회")
        void findExistingUserPointTest() {
            // given
            given(userPointTable.selectById(USER_ID))
                    .willReturn(userPoint); // 잔여: 1000L

            // when
            UserPoint actualUserPoint = pointService.findUserPoint(USER_ID);

            // then
            assertThat(actualUserPoint.id()).isEqualTo(USER_ID);
            assertThat(actualUserPoint.point()).isEqualTo(USER_POINT);
        }
    }

    @Nested
    @DisplayName("Point History 조회")
    class findPointHistory {

        @Test
        @DisplayName("신규 Point History 조회")
        void findEmptyPointHistoryTest(){
            // given
            given(pointHistoryTable.selectAllByUserId(USER_ID))
                    .willReturn(Collections.emptyList());

            // when
            List<PointHistory> actualPointHistories = pointService.findPointHistory(USER_ID);

            // then
            assertThat(actualPointHistories).isEmpty();
        }


        @Test
        @DisplayName("기존 Point History 조회")
        void findUserPointHistoryTest() {
            // given
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

            // when
            List<PointHistory> actualPointHistories = pointService.findPointHistory(USER_ID);

            // then
            assertThat(actualPointHistories.size()).isEqualTo(pointHistories.size());
        }
    }

    @Nested
    @DisplayName("포인트 충전")
    class charge {

        @ParameterizedTest
        @DisplayName("[FAIL] 0이하의 포인트를 충전 시 PointException 발생")
        @ValueSource(longs = {-1000L, 0L}) // given
        void nonPositiveAmountChargeTest(long nonPositiveAmount) {

            // when + then
            assertThatThrownBy(() -> pointService.charge(USER_ID, nonPositiveAmount))
                    .isInstanceOf(PointException.class)
                    .hasMessageContaining(PointErrorCode.NON_POSITIVE_AMOUNT.getMessage());
        }

        @Test
        @DisplayName("[FAIL] 충전된 포인트가 100,000 초과 시 PointException 발생")
        void maxPointExceedChargeTest() {
            // given
            given(lockManager.getLock(USER_ID))
                    .willReturn(lock);

            given(userPointTable.selectById(USER_ID))
                    .willReturn(emptyUserPoint); // 잔여: 0L

            // when + then
            assertThatThrownBy(() -> pointService.charge(USER_ID, 100001L))
                    .isInstanceOf(PointException.class)
                    .hasMessageContaining(PointErrorCode.MAX_POINT_EXCEED.getMessage());
        }

        @Test
        @DisplayName("[SUCCESS] 정상적인 포인트 충전")
        void chargeTest() {
            // given
            given(lockManager.getLock(USER_ID))
                    .willReturn(lock);

            given(userPointTable.selectById(USER_ID))
                    .willReturn(userPoint); // 잔여: 1000L

            long chargingPoint = 99000L;
            long remainingPoint = userPoint.point() + chargingPoint; // 99000 + 1000  = 100000

            given(userPointTable.insertOrUpdate(USER_ID, remainingPoint))
                    .willReturn(new UserPoint(USER_ID, remainingPoint, System.currentTimeMillis()));

            // when
            UserPoint actualUserPoint = pointService.charge(USER_ID, chargingPoint);

            //then
            assertThat(actualUserPoint.id()).isEqualTo(USER_ID);
            assertThat(actualUserPoint.point()).isEqualTo(remainingPoint);
        }

    }

    @Nested
    @DisplayName("포인트 사용")
    class use {

        @Test
        @DisplayName("[FAIL] 포인트 사용 시 잔여 포인트가 0이하가 되는 경우 PointException 발생")
        void notEnoughPointUseTest() {
            // given
            given(lockManager.getLock(USER_ID))
                    .willReturn(lock);

            given(userPointTable.selectById(USER_ID))
                    .willReturn(emptyUserPoint); // 잔여: 0L

            // when + then
            assertThatThrownBy(() -> pointService.use(USER_ID, 10000L))
                    .isInstanceOf(PointException.class)
                    .hasMessageContaining(PointErrorCode.NOT_ENOUGH_POINT.getMessage());
        }

        @ParameterizedTest
        @DisplayName("[FAIL] 0이하의 포인트를 사용하는 경우 PointException 발생")
        @ValueSource(longs = {-10000L, 0L}) // given
        void nonPositiveAmountPointUseTest(long amount) {

            // when + then
            assertThatThrownBy(() -> pointService.use(USER_ID, amount))
                    .isInstanceOf(PointException.class)
                    .hasMessageContaining(PointErrorCode.NON_POSITIVE_AMOUNT.getMessage());
        }

        @Test
        @DisplayName("[SUCCESS] 정상적인 포인트 사용")
        void useTest() {
            // given
            given(lockManager.getLock(USER_ID))
                    .willReturn(lock);

            given(userPointTable.selectById(USER_ID))
                    .willReturn(userPoint); // 기존: 1000L

            long usingPoint = 1000L;
            long remainingPoint = userPoint.point() - usingPoint; // 1000 - 1000 = 0

            given(userPointTable.insertOrUpdate(USER_ID, remainingPoint))
                    .willReturn(new UserPoint(USER_ID, remainingPoint, System.currentTimeMillis()));

            // when
            UserPoint actualUserPoint = pointService.use(USER_ID, usingPoint);

            // then
            assertThat(actualUserPoint.id()).isEqualTo(USER_ID);
            assertThat(actualUserPoint.point()).isEqualTo(remainingPoint);
        }
    }

}
