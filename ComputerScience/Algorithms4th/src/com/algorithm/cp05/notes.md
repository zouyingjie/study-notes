
<!-- TOC -->

- [1.前言](#1前言)
- [2.字符串排序](#2字符串排序)
  - [2.1 Key-indexd counting: 索引计数法](#21-key-indexd-counting-索引计数法)

<!-- /TOC -->
## 1.前言

**问题1**

- 基于 Strig 和 StringBuilder 实现的反转字符串的操作
    - 基于 String: O(N^2)
    - 基于 StringBuilder: O(N)

**w问题2**

- 基于 Strig 和 StringBuilder 实现后缀查询
    - 基于 StringBuilder: O(N^2)
    - 基于 String: O(N)

**拓展资料**

- http://java-performance.info/changes-to-string-java-1-7-0_06/
## 2.字符串排序

### 2.1 Key-indexd counting: 索引计数法

核心解法：去掉的快速排序、归并排序中的比较，突破了 NlogN 的性能下限。

### 2.2 LSD & MSD

- LSD: 低位优先排序，也就是从右到左排序，适应于定长字符串的排序
- MSD: 高位优先排序，即从左到右排序，适应于不定长的字符串。

**拓展资料**

- [Youtube: Radix Sort Algorithm Introduction in 5 Minutes](https://www.youtube.com/watch?v=XiuSW_mEn7g)
- [Youtube: Learn Counting Sort Algorithm in LESS THAN 6 MINUTES!](https://www.youtube.com/watch?v=OKd534EWcdk)
- [MIT: Lecture 7: Counting Sort, Radix Sort, Lower Bounds for Sorting](https://www.youtube.com/watch?v=Nz1KZXbghj8)