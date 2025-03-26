package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.Lock;

@Service
@RequiredArgsConstructor
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final LockManager lockManager;

    public UserPoint findUserPoint(Long id) {

        return userPointTable.selectById(id);
    }

    public List<PointHistory> findPointHistory(Long id) {

        return pointHistoryTable.selectAllByUserId(id);
    }

    public UserPoint charge(long id, long amount) {

        if (amount <= 0) {
            throw new PointException(PointErrorCode.NON_POSITIVE_AMOUNT);
        }

        Lock lock = lockManager.getLock(id);
        lock.lock();
        try {
            UserPoint userPoint = userPointTable.selectById(id);

            long chargedPoint = userPoint.point() + amount;

            if (chargedPoint > 100000L) {
                throw new PointException(PointErrorCode.MAX_POINT_EXCEED);
            }

            pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis());

            return userPointTable.insertOrUpdate(id, chargedPoint);

        } finally {
            lock.unlock();
        }
    }

    public UserPoint use(long id, long amount) {

        if (amount <= 0) {
            throw new PointException(PointErrorCode.NON_POSITIVE_AMOUNT);
        }

        Lock lock = lockManager.getLock(id);
        lock.lock();
        try {
            UserPoint userPoint = userPointTable.selectById(id);
            long usedPoint = userPoint.point() - amount;

            if (usedPoint < 0) {
                throw new PointException(PointErrorCode.NOT_ENOUGH_POINT);
            }

            pointHistoryTable.insert(id, amount, TransactionType.USE, System.currentTimeMillis());

            return userPointTable.insertOrUpdate(id, usedPoint);

        } finally {
            lock.unlock();
        }
    }

}
