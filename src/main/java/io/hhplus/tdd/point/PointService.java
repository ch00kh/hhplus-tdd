package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    public UserPoint findUserPoint(Long id) {

        return userPointTable.selectById(id);
    }

    public List<PointHistory> findPointHistory(Long id) {

        return pointHistoryTable.selectAllByUserId(id);
    }

    public UserPoint charge(Long id, Long amount) throws Exception {

        if (amount <= 0) {
            throw new Exception();
        }

        UserPoint userPoint = userPointTable.selectById(id);
        long chargedPoint = userPoint.point() + amount;

        pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis());

        return userPointTable.insertOrUpdate(id, chargedPoint);
    }

    public UserPoint use(long id, long amount) throws Exception {

        UserPoint userPoint = userPointTable.selectById(id);
        long usedPoint = userPoint.point() - amount;

        if (usedPoint < 0) {
            throw new Exception();
        }

        pointHistoryTable.insert(id, amount, TransactionType.USE, System.currentTimeMillis());

        return userPointTable.insertOrUpdate(id, usedPoint);
    }

}
