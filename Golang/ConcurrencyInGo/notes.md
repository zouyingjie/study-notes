### 第三章

goroutine 的特性：

- 非抢占式
- 由 Go runtime 确定何时中断与恢复
- 算是一种特殊的协程
  
  **fork-join model**

  ![](./images/fork-join.png)


**WaitGroup**

- 类似于 Java 中的 CountDownLatch

```Java

// 1. 一等多
// main thread
CountDownLatch latch = new CountDownLatch(5);
// other threads
latch.countDown();

// main thread
lath.await();



// 2. 多等一
// main thread
CountDownLatch latch = new CountDownLatch(1);
// other threads
latch.await();
// main thread
lath.countDown();
```

**RWMutex 读写锁**

**Cond**

类似于 Java 的 Condition 对象，当不满于条件时 wait() 等待，其他线程发送 signal 后被唤醒。

