package main

import (
	"fmt"
	"sync"
	"time"
)

func main() {
	// demo01()
	// demo02()
	demo03()
}

// Cond 对象，类似 Java 中的 Condition
// 拓展：生产者消费者模式
func demo01() {
	c := sync.NewCond(&sync.Mutex{})
	queue := make([]interface{}, 0, 10)
	remoteFromQueue := func(delay time.Duration) {
		time.Sleep(delay)
		c.L.Lock()
		queue = queue[1:]
		fmt.Println("Remoted from queue")
		c.L.Unlock()
		c.Signal()
	}

	for i := 0; i < 10; i++ {
		c.L.Lock()
		for len(queue) == 2 {
			c.Wait()
		}
		fmt.Println("Adding to queue")
		queue = append(queue, struct{}{})
		go remoteFromQueue(1 * time.Second)
		c.L.Unlock()
	}

}

// Once: 表示执行一次
// 拓展：实现单例模式
// 每个 Once 的 Do 只会执行一次
func demo02() {
	var count int

	increment := func() {
		count++
	}

	var once sync.Once

	var increments sync.WaitGroup
	increments.Add(100)
	for i := 0; i < 100; i++ {
		go func() {
			defer increments.Done()
			once.Do(increment)
		}()
	}

	increments.Wait()
	fmt.Printf("Count is %d\n", count)
}

// 使用 sync.Once 实现单例模式示例
type PrometheusExporter struct {
}
type Spec struct{}

var once sync.Once
var exporter *PrometheusExporter

func GetPrometheusExporter(spec *Spec) *PrometheusExporter {
	once.Do(initPrometheusExporter)
	return exporter
}

func initPrometheusExporter() {
	exporter = &PrometheusExporter{}
}

// sync.Pool 使用示例，线程池
// https://medium.com/swlh/go-the-idea-behind-sync-pool-32da5089df72
func demo03() {
	var numCalcsCreated int
	var tmp int
	calcPool := &sync.Pool{
		New: func() interface{} {
			numCalcsCreated += 1
			mem := make([]byte, 1024)
			return &mem
		},
	}

	// Seed the pool with 4KB
	calcPool.Put(calcPool.New())
	calcPool.Put(calcPool.New())
	calcPool.Put(calcPool.New())
	calcPool.Put(calcPool.New())

	const numWorkers = 1024 * 1024
	var wg sync.WaitGroup
	wg.Add(numWorkers)
	for i := numWorkers; i > 0; i-- {
		go func() {
			defer wg.Done()

			mem := calcPool.Get().(*[]byte)
			defer calcPool.Put(mem)

			// Assume something interesting, but quick is being done with
			// this memory.
			tmp++
			time.Sleep(1 * time.Second)
		}()
	}

	wg.Wait()
	fmt.Printf("%d calculators were created.", numCalcsCreated)
}

// 模拟服务端
// 每次新建连接和使用 sync.Pool 性能相差三个数量级。
func connectToService() interface{} {
	time.Sleep(1 * time.Second)
	return struct{}{}
}
