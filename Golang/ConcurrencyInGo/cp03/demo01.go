package main

import (
	"fmt"
	"sync"
)

func main() {
	// var wg sync.WaitGroup
	// sayHello := func() {
	// 	defer wg.Done()
	// 	fmt.Println("hello")
	// }

	// wg.Add(1)
	// go sayHello()
	// wg.Wait()

	clouser()
}

//  闭包

func clouser() {
	var wg sync.WaitGroup
	for _, salutation := range []string{"hello", "greetings", "good day"} {
		wg.Add(1)

		// 只输出最后一个字符串
		go func() {
			defer wg.Done()
			fmt.Println(salutation)
		}()

		// 正常输出
		go func(salutation string) {
			defer wg.Done()
			fmt.Println(salutation)
		}(salutation)
	}
	wg.Wait()
}
