package main

import (
	"fmt"
	"sync"
)

func main() {

}

// 1. 创建 channel
func demo01() {

	var dataStream1 chan interface{}
	dataStream1 = make(chan interface{})
	var dataStream2 chan<- interface{}
	dataStream2 = make(chan<- interface{})

	fmt.Println(dataStream1)
	fmt.Println(dataStream2)
}

// 2. channel 死锁
// channnel 无法写入，导致 main goroutine 一直等待
// 另外一个 goroutine 执行完毕后， Go runtime 察觉到没有 goroutine 在运行，会报错 deadlock
func demo02() {
	stringStream := make(chan string)
	go func() {
		if 0 != 1 {
			return
		}
		stringStream <- "Hello channels!"
	}()
	fmt.Println(<-stringStream)
}

// for range
// channel 会返回
// 因此可以在 for 循环中自动终止
func demo03() {
	intStream := make(chan int)
	go func() {
		defer close(intStream)
		for i := 1; i <= 5; i++ {
			intStream <- i
		}
	}()

	for integer := range intStream {
		fmt.Printf("%v ", integer)
	}
}

// 多个 goroutine 可以等待一个 channel，从而达到 sync.Cond 的效果

func demo04() {
	begin := make(chan interface{})
	var wg sync.WaitGroup
	for i := 0; i < 5; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			<-begin
			fmt.Printf("%v has begun\n", i)
		}(i)
	}

	fmt.Println("Unblocking goroutines...")
	close(begin)
	wg.Wait()
}
