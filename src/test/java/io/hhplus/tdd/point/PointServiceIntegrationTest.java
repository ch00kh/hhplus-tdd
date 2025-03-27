package io.hhplus.tdd.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PointServiceIntegrationTest {

    private final long USER_ID = 1L;

    @Autowired
    PointService pointService;

    @Test
    @DisplayName("포인트 충전 - 동시에 포인트 충전 후 최대 잔고값 넘어가면 예외 발생")
    void concurrentChargeTest_CompletableFuture() {

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> pointService.charge(USER_ID, 50000L)),
                CompletableFuture.runAsync(() -> pointService.charge(USER_ID, 40000L)),
                CompletableFuture.runAsync(() -> {
                    try {
                        pointService.charge(USER_ID, 30000L);
                    } catch (PointException e) {
                        assertThat(e).isInstanceOf(PointException.class)
                                .hasMessageContaining(PointErrorCode.MAX_POINT_EXCEED.getMessage());
                    }
                })
        ).join();

        UserPoint actualUserPoint = pointService.findUserPoint(USER_ID);
        List<PointHistory> pointHistoryList = pointService.findPointHistory(USER_ID);

        assertThat(actualUserPoint.point()).isEqualTo(90000L);
        assertThat(pointHistoryList.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("포인트 충전 & 사용 - 10000 포인트 충전 후 포인트 사용 시 포인트가 부족하면 예외 발생")
    void concurrentUseTest_CompletableFuture() {

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> pointService.charge(USER_ID, 10000)),
                CompletableFuture.runAsync(() -> pointService.use(USER_ID, 4000)),
                CompletableFuture.runAsync(() -> pointService.use(USER_ID, 3000)),
                CompletableFuture.runAsync(() -> pointService.use(USER_ID, 2000)),
                CompletableFuture.runAsync(() -> {
                    try {
                        pointService.use(USER_ID, 3000L);
                    } catch (PointException e) {
                        assertThat(e).isInstanceOf(PointException.class)
                                .hasMessageContaining(PointErrorCode.NOT_ENOUGH_POINT.getMessage());
                    }
                })
        ).join();

        UserPoint actualUserPoint = pointService.findUserPoint(USER_ID);
        List<PointHistory> pointHistoryList = pointService.findPointHistory(USER_ID);

        assertThat(actualUserPoint.point()).isEqualTo(1000L);
        assertThat(pointHistoryList.size()).isEqualTo(4);
    }

    @Test
    @DisplayName("동시 충전 혹은 동시 요청 테스트")
    void concurrentVariousTasksTest() {

        CompletableFuture.allOf(
                // 0 + 10000 = 10000
                CompletableFuture.runAsync(() -> pointService.charge(USER_ID, 10000)),
                // 10000 - 12000 = -2000 => 예외 (잔고: 10000)
                CompletableFuture.runAsync(() -> {
                    try {
                        pointService.use(USER_ID, 11000);
                    } catch (PointException e) {
                        assertThat(e).isInstanceOf(PointException.class)
                                .hasMessageContaining(PointErrorCode.NOT_ENOUGH_POINT.getMessage());
                    }
                }),
                // 10000 + 10000 = 20000
                CompletableFuture.runAsync(() -> pointService.charge(USER_ID, 10000)),
                // 20000 - 12000 = 8000
                CompletableFuture.runAsync(() -> pointService.use(USER_ID, 12000)),
                // 8000 + 500 = 8500
                CompletableFuture.runAsync(() -> pointService.charge(USER_ID, 500)),
                // 8500 - 8500 = 0
                CompletableFuture.runAsync(() -> pointService.use(USER_ID, 8500))
        ).join();

        UserPoint userPoint = pointService.findUserPoint(USER_ID);
        List<PointHistory> pointHistoryList = pointService.findPointHistory(userPoint.id());
        List<PointHistory> pointChargingList = new ArrayList<>();
        List<PointHistory> pointUsingList = new ArrayList<>();

        pointHistoryList.forEach(p -> {
            if (p.type() == TransactionType.CHARGE) {
                pointChargingList.add(p);
            }
            if (p.type() == TransactionType.USE) {
                pointUsingList.add(p);
            }
        });

        assertThat(userPoint).isNotNull();
        assertThat(userPoint.point()).isEqualTo(0);
        assertThat(pointHistoryList.size()).isEqualTo(5);
        assertThat(pointChargingList.size()).isEqualTo(3);  // 충전 3회
        assertThat(pointUsingList.size()).isEqualTo(2);     // 사용 2회
    }

    @Test
    @DisplayName("여러명이 각자 충전 혹은 사용")
    void concurrentChargeOrUseSelfTest() {

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> pointService.charge(1L, 1000)),
                CompletableFuture.runAsync(() -> pointService.charge(2L, 1900)),
                CompletableFuture.runAsync(() -> pointService.charge(3L, 1500)),

                CompletableFuture.runAsync(() -> pointService.charge(1L, 2000)),
                CompletableFuture.runAsync(() -> pointService.use(2L, 1800)),
                CompletableFuture.runAsync(() -> pointService.use(3L, 1500)),

                CompletableFuture.runAsync(() -> pointService.use(1L, 1500)),
                CompletableFuture.runAsync(() -> pointService.charge(2L, 2000)),
                CompletableFuture.runAsync(() -> pointService.charge(3L, 100))
        ).join();

        UserPoint userPoint1 = pointService.findUserPoint(1L);
        List<PointHistory> pointHistoryList1 = pointService.findPointHistory(1L);

        assertThat(userPoint1.point()).isEqualTo(1500);
        assertThat(pointHistoryList1.size()).isEqualTo(3);

        UserPoint userPoint2 = pointService.findUserPoint(2L);
        List<PointHistory> pointHistoryList2 = pointService.findPointHistory(2L);

        assertThat(userPoint2.point()).isEqualTo(2100);
        assertThat(pointHistoryList2.size()).isEqualTo(3);

        UserPoint userPoint3 = pointService.findUserPoint(3L);
        List<PointHistory> pointHistoryList3 = pointService.findPointHistory(3L);

        assertThat(userPoint3.point()).isEqualTo(100);
        assertThat(pointHistoryList3.size()).isEqualTo(3);
    }
}
