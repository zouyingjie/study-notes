### 一. 测试分类

- 单元测试
- 测试

### 二. 测试框架

- Junit5

#### 1. 常用注解

**常用注解**
- @Test
- @Before
- @After

**常用断言**

- assertEquals
- assertNotEquals
- assertTrue
- assertFalse
- assertThorws

```java

```

#### 2. 对象模拟

- Stub：模拟数据对象
- Mock：模拟 Service 对象

**Mockito 框架常用方式**

```java
when(accountDao.findAccount(TEST_ACCOUNT_NO)).thenReturn(account);
verify(accountDao, times(1)).findAccount(any(String.class));
```