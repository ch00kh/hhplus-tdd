# 동시성 제어 방식 및 각 적용의 장/단점

동시성은 멀티쓰레드 환경에서 작업이 동시에 실행되는 것 처럼 보이는 개념이다.
여러 스레드가 동시에 하나의 자원을 공유하고 있기 때문에 같은 자원에 대한 경쟁 상태, 데드락 같은 문제가 발생한다.
이러한 문제를 방지하고 해결하기 위해 thread-safe한 코드를 작성해야한다.

---

## 동시성 제어 방식

> ### synchronized
> - Java 1.0 처음부터 동기화 방법을 포함해서 나왔기에 문법으로 제공된다.
> - 메서드나 코드 블록에 적용하여 한 번에 하나의 스레드만 접근할 수 있도록 보장한다.
> - 메서드 레벨에 적용할 경우 해당 메서드 전체가 임계 영역이 되어 다른 스레드의 접근을 방지한다.
- 편리하고 직관적이며, JVM에 의해 자동으로 락의 획득과 해제가 자동으로 관리된다.
- BLOCKED 된 상태의 스레드는 락이 해제될 때까지 무한 대기한다.
- 락 획득 순서가 스레드마다 다르다면 데드락이 발생할 수 있다.
- timeout, interrupt 를 지원하지 않아 데드락이 발생했을때 해결하기 어렵다.
- 락이 돌아왔을때 BLOCKED 상태의 스레드 중 어떤 스레드가 락을 획득했는지 추적이 어렵다.

> ### ReentrantLock
> - Java 1.5 부터 동시성 문제 해결을 위해 `java.util.concurrent.locks` 제공하고 있다.
> - Lock 인터페이스를 구현체이며, `synchronized`와 달리 락의 획득과 해제를 명시적으로 제어할 수 있다.
- 락의 획득과 해제를 코드에서 명시하여 제어할 수 있다.
- timeout, interrupt를 지원하며, 공정성을 제어하여 기아현상을 방지할 수 있다.
- 세밀한 스레드 제어와, 락의 상태를확인하는 등 `synchronized`보다 다양하고 유연한 기능을 제공한다.

---

## 동시성 테스트 방법

> ### CountDownLatch
> - 멀티쓰레드 환경에서 작업이 완료될 때까지 기다릴 수 있게 해주는 동기화 도구이다.

### Thread
- Java의 가장 기본적인 스레드 구현 방식으로, 각각 독립적으로 실행된다.
- 직접 스레드를 생성하고 관리하는 방식으로, 세밀한 제어를 하기 어렵다.
<details>
<summary>code</summary>

```java
@Test
void concurrentChargeTest() throws InterruptedException {

    int threadCount = 3;
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < 3; i++) {
        new Thread(() -> {
            try {
                pointService.charge(USER_ID, 45000L);

            } catch (PointException e) {
                assertThat(e)
                        .isInstanceOf(PointException.class)
                        .hasMessageContaining(PointErrorCode.NOT_ENOUGH_POINT.getMessage());
            } finally {
                latch.countDown();
            }
        }).start();
    }
    latch.await();

    UserPoint actualUserPoint = pointService.findUserPoint(USER_ID);
    List<PointHistory> pointHistoryList = pointService.findPointHistory(USER_ID);

    assertThat(actualUserPoint.point()).isEqualTo(90000L);
    assertThat(pointHistoryList.size()).isEqualTo(2);
}
```
</details>

### ExecutorService
- 스레드 풀을 이용한 작업 실행을 관리한다.
- 작업과 실행을 분리할 수 있어 리소스를 효율적으로 관리할 수 있다.
- 재사용이 가능하다.

<details>
<summary>code</summary>

```java
@Test
void concurrentChargeTest() throws InterruptedException {

    int threadCount = 3;
    CountDownLatch latch = new CountDownLatch(threadCount);
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    
    executorService.execute(() -> {
        pointService.charge(USER_ID, 50000L);
        latch.countDown();
    });
    
    executorService.execute(() -> {
        pointService.charge(USER_ID, 40000L);
        latch.countDown();
    });
    
    executorService.execute(() -> {
        try {
            pointService.charge(USER_ID, 45000L);
    
        } catch (PointException e) {
            assertThat(e)
                    .isInstanceOf(PointException.class)
                    .hasMessageContaining(PointErrorCode.NOT_ENOUGH_POINT.getMessage());
        } finally {
            latch.countDown();
        }
    });
    
    latch.await();
    executorService.shutdown();
    
    UserPoint actualUserPoint = pointService.findUserPoint(USER_ID);
    List<PointHistory> pointHistoryList = pointService.findPointHistory(USER_ID);
    
    assertThat(actualUserPoint.point()).isEqualTo(90000L);
    assertThat(pointHistoryList.size()).isEqualTo(2);
    }
```
</details>


> ### CompletableFuture
> - Java 1.8에서 도입된 비동기 작업을 위한 클래스이다.
> - `Future`를 확장하여 함수형프로그래밍 방식(람다)식으로 작성할 수 있다.
> - 복잡한 비동기 작업을 간결하게 구현할 수 있다.

<details>
    <summary>code</summary>

```java
@Test
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
```
</details>


