
<!-- TOC -->

- [一. IP 协议 Overview](#一-ip-协议-overview)
  - [IP 协议发展历史](#ip-协议发展历史)
  - [IP 协议的功能与特性](#ip-协议的功能与特性)
- [二. IP 寻址](#二-ip-寻址)
  - [2.1 IP 分类寻址(Classful Addressing)](#21-ip-分类寻址classful-addressing)
    - [分类寻址网络/主机号划分](#分类寻址网络主机号划分)
    - [A/B/C 类地址空间](#abc-类地址空间)
    - [特殊地址含义（多播、保留地址等）](#特殊地址含义多播保留地址等)
    - [分类寻址的问题](#分类寻址的问题)
  - [2.2 IP 子网寻址(Subnet addressing)](#22-ip-子网寻址subnet-addressing)
    - [可变子网寻址（VLSM，Variable Length Subnet Masking ）](#可变子网寻址vlsmvariable-length-subnet-masking-)
  - [2.3 无类寻址](#23-无类寻址)
    - [CIDR 简介](#cidr-简介)
    - [CIDR 网络分层与表示](#cidr-网络分层与表示)
- [三. IP 数据包的封装与格式](#三-ip-数据包的封装与格式)
- [四. IP 数据包的分片与重组](#四-ip-数据包的分片与重组)
  - [4.1 IP 分片处理](#41-ip-分片处理)
  - [4.2 IP 数据报重组](#42-ip-数据报重组)

<!-- /TOC -->


文中图片均来自 [The TCP/IP Guide](http://www.tcpipguide.com/free/t_toc.htm) 网站。

### 一. IP 协议 Overview

#### IP 协议发展历史

IP 协议作为 TCP/IP 协议栈中最重要的协议，其最开始是属于 TCP 协议的一部分的，后来单独独立出来。1981 年，[RFC 791](https://tools.ietf.org/html/rfc791) 发布，这标志着 IP 协议正式诞生，其定义了 IP 版本的核心功能和特性，并一直沿用至今。


**为什么独立出来待补充**

[RFC 791](https://tools.ietf.org/html/rfc791)  定义的 IP 版本为 4，其原因是早期的 TCP 经历了三个较早的版本，并在版本 4 分为了 TCP 和 IP。为了保持一致性，该版本号同时应用于 TCP 和 IP。

当前主流版本就是 4，也就是我们常说的 IPv4，一般情况下我们说的 IP 协议默认就是 IPv4。但因为 IPv4 存在地址不足的等原因，新版本的 IP 协议始终是需要的，最新版本的 IP 协议是 IPv6(Internet Protocol version 6 )，也被称为 **IPng（IP Next Generation）**。

#### IP 协议的功能与特性

简单来说，IP 协议主要实现了一个功能：

> **跨网络的数据报传输**，其将上层（4层协议或者其他协议）协议发过来的数据（TCP、UDP等包）封装打包并进行必要的处理，然后将其发送到目标网络。

IP 协议主要有下面一些特性：

- 通用地址：其使用统一的 IP 地址标识网络接口
- 底层协议无关：IP 协议不关心底层协议
- 无连接：IP 协议是无连接的，因此其传输和数据时无需同步状态，建立连接
- 不可靠
- 无确认

基于以上特性，IP 协议并不保证数据传输一定会成功，其目标只有一个：**尽可能的传输数据**。

IP 协议主要有如下四个功能：

- **数据封装与打包**
- **IP 寻址**
- **IP 路由 & 间接传输**
- **数据分片与重组**

### 二. IP 寻址

为了能够在设备之间传递数据，IP 协议必须知道设备在哪，这是由 **IP 地址** 实现的，IP 协议最重要的功能之一就是**IP 寻址**，其主要包含两方面：

- Network Interface Identification（网络接口标识）：通过 IP 地址标识网络接口
- Routing（路由）： 基于 IP 地址在互联网传输 IP 数据报

注意 IP 地址标识的是网络接口，而不是主机，因为主机可以有多个网络接口，即一个主机可以有多个网络地址。

**IP 地址表示**

对于 IPv4，IP 地址是一串 32 位二进制数，当然也可以用十六进制或十进制形式表示。为了方便人们阅读，最常见的表示方式是 **点分十进制表示法（dotted decimal notation ）**。 

> 将32位划分为四个字节，然后将每个字节的二进制串转换为十进制，然后用点分隔这些数字以创建点分十进制表示法，比如 IP 地址 11100011010100101001101110110001 分为四组是 11100011 - 01010010 - 10011101 - 10110001，然后转为十进制就是 227.82.157.177。

如下图所示：

![](/images/ipv4division-networkid-and-hostid.png)


32位的 IPv4 地址主要分为两部分：网络号和主机号

- 网络号（Network ID）: 从 IP 地址最左边开始的若干位为网络号，用来标识网络接口所在的网络，实现路由功能。
- 主机号（Host ID）: 用于标识位于网络中的某台主机。

拿我们的手机号类比，比如中国大陆手机号格式是 +86 12233335555，其中 +86 表示是中国的国际区号，后面的号码就是某个人的具体手机号。当我们在国外打电话时，+86 告诉运营商我这是中国大陆的手机号，麻烦转到中国大陆，然后在通过手机号将信号发送到我们的手机。对应到 IP 地址，网络号就相当于 +86，手机号相当于主机号。当数据在网络上传输，路由器通过网络号将 IP 数据报发送到目标网络，到达目标网络后在通过主机号找到具体接收数据的那一台主机，然后将数据发送到对应的网络接口，这样就完成了 IP 数据的传输。

和我们的手机号其国际区号固定是 +86 三位不同，网络号和主机号的划分并不是固定的，因此我们需要知道 IPv4 地址中哪些是网络号，哪些是主机号。网络号与主机号的区分主要取决于其采用的**寻址方式**，IPv4 发展过程中先后有下面三种分类方式：

- 分类寻址（ Classful Addressing）
- 子网分类寻址（Subnetted Classful Addressing）
- 无类寻址（Classless Addressing）

#### 2.1 IP 分类寻址(Classful Addressing)

所谓分类寻址就是，基于不同的功能将 IP 地址分为 A、B、C、D、E 5 类。如下表

分类| IP 地址数量占比|网络号位数|主机号位数|功能
---|--------------|------- | ------- |--
 A 类  | 1/2| 8 | 24 |单播地址，用于大型组织
 B 类  | 1/4| 16 | 16 |单播地址，用于中型组织
 C 类  | 1/8| 24 | 8 |单播地址，用于小型组织
 D 类  | 1/16| n/a | n/a |IP 多播地址
 E 类  | 1/16| n/a | n/a |保留供实验使用

各个类别地址占比如图所示：

![](/images/IPv4-Address-Space-Into-Classes.png)

A/B/C 三类地址是最重要的，其用于常规的单播地址，其地址数量占了 IPv4 地址总数量的 7/8，D 类地址保留给 IP 多播使用，而 E 类则保留给实验使用。

**分类寻址的设计原理**

虽然现在分类寻址已经被弃用，现在讨论更多的是分类寻址的缺点。但在互联网发展初期，分类寻址的实现是有其道理的。以当时的视角来看，分类寻址具有下面一些优点：

- **简洁明了**：仅有 5 类地址，很容易理解，并且基于分类可以方便的知道网络号与主机号的划分。
- **灵活性较好**：在上个世纪网络远没有现在发达， A、B、C 的三类地址可以满足大、中、小型组织的不同需求。
- **路由简单**：地址类别是直接编码到地址中的，路由器可以方便的知道地址的网络号，而不需要子网掩码等其他手段。


##### 分类寻址网络/主机号划分

分类寻址中，地址类别是直接编码到地址中的，本质是 IP 地址的类别是有其前几位决定的，判断逻辑如下：

![](/images/classful-ip-addresses.png)


- 如果第 1 位是 0，则该地址是 A 类
- 如果前 2 位是 10，则该地址是 B 类
- 如果前 3 位是 110，则该地址是 C 类
- 如果前 4 位是 1110，则该地址是 D 类
- 如果前 4 位是 1111，则该地址是 E 类

下面是五类地址的地址范围：

分类| 第一个 8 位字节表示|第一个 8 位字节最小值|第一个 8 位字节最大值|第一个八位位组值的范围（十进制）|网络号/主机号比例|IP 地址范围
--|--|-- | -- |-- | --|--
 A 类  | 0xxx xxxx | 0000 0001 | 0111 1110 | 1 to 126   | 1/3 | 1.0.0.0 to 126.255.255.255
 B 类  | 10xx xxxx | 1000 0000 | 1011 1111 | 128 to 191 | 2/2 | 128.0.0.0 to 191.255.255.255
 C 类  | 110x xxxx | 1100 0000 | 1101 1111 | 192 to 223 | 3/1 | 192.0.0.0 to 223.255.255.255
 D 类  | 1110 xxxx | 1110 0000 | 1110 1111 | 224 to 239 |  -  | 224.0.0.0 to 239.255.255.255
 E 类  | 1111 xxxx | 1111 0000 | 1111 1111 | 240 to 255 |  -  | 240.0.0.0 to 255.255.255.255

 可以看到五类地址的第一个八位字节开头是固定的，因此我们很容易通过前几位判断出地址属于哪一类。另外需要注意的一点是 A 类地址的最大值是 126.x.x.x，B 类地址的最小值
 是 128.x.x.x，中间少了 127 开头的地址，是因为 127 开头的地址已经保留作为本地回环地址。


##### A/B/C 类地址空间

对于 A、B、C 类地址，其网络号和主机号的划分是固定的，如图所示

![](/images/IP-Address-Network-HostID-Sizes.png)

A 类地址主机号为后 24 位，B 类地址主机号为后 16 位，而 C 类地址主机号为后 8 位，其拥有的 IP 地址数量如下表所示：

类别|网络号&主机号位数占比|第一个 8 位格式|识别网络 ID 的位数|可用网络位数|可用网络 ID 数量|每个网络 ID 下可用主机数量
--- |---   |-- | -- |-- | --|--
A 类| 8/24  | 0xxx xxxx | 1 | 8-1 = 7 | 2^7-2 = 126 | 2^24-2 = 16,277,214
B 类| 16/16 | 10xx xxxx | 2 | 16-2 = 14 | 2^14 = 16,384 | 2^16-2 = 65,534
C 类| 24/8  | 110x xxxx | 3 | 24-3 = 21 | 2^21 = 2,097,152 | 2^8-2 = 254

以 B 类地址为例，其前 2 位固定，因此其可用的网络号为 14 位，则其可用的网络 ID 就是 2 的 14 次方，对于主机号则是 2 的 16 次方，需要减 2 的原因是主机号全部为 0 或者 1 的两个地址被保留另做他用。由此我们可以知道，在基于分类寻址的网络结构中，一个 A 类网络大约有 1600 万个网络地址，B 类网络大约有地址为 65000 个，而 C 类网络地址只有 254 个。

##### 特殊地址含义（多播、保留地址等）

虽然理论上 IPv4 地址可以有 2^32 个，但实际可分配的地址数量是远远少于这个数的，有很多特殊地址是不用被分配的，包括保留地址、私有不可路由的地址、回环地址以及某些有特殊意义的地址。下面是一些地址示例。

**特殊地址**

对于某个 IPv4 地址，其网络号或主机号全部为 0 或者全部为 1 时有其特殊含义，如下：

网络号|主机号|A类地址|B 类示例|C类示例| 描述
----- | --- |----- | ----- |----- | ---
NetworkID | HostID  | 77.91.215.5 | 154.3.99.6 |227.82.157.160 | 正常 IP 地址
NetworkID | 全部为 0 | 77.0.0.0    | 154.3.0.0  | 227.82.157.0   | 表示某个网络
全部为 0   | HostID  | 0.91.215.5 | 0.0.99.6| 0.0.0.160 | 表示当前网络的特定主机
全部为 0   | 全部为 0 | 0.0.0.0     | 0.0.0.0    | 0.0.0.0       |一般用来表示当前主机
Network ID| 全部为 1 | 77.255.255.255 | 154.3.255.255 | 227.82.157.255 | 指定网络上的所有地址，一般用于广播到本地网络的所有主机
全部为 1   | 全部为 1 | 77.0.0.0    | 154.3.0.0  | 227.82.157.0   |所有网络的所有主机，会向所有主机发送全局广播

**私有地址**

[RFC1918](https://tools.ietf.org/html/rfc1918) 定义一系列私有地址，其不能被用于公网 IP，路由器无法路由这些 IP 发出的请求。

**回环地址**

IP 协议传输数据时，会将 IP 数据报发送到底层的链路层协议进行传输。
IP 规定 127.0.0.0 ~ 127.255.255.255 范围内的地址为回环地址，向该类地址发送的数据报不会发送到底层链路协议，而是直接发送到本机的 IP 层。

下面是各类特殊地址的汇总“

![](/images/ip_private_loopback_address.png)


##### 分类寻址的问题

随着互联网的快速发展，分类寻址开始出现许多问题。主要有下面三个：

- 内部地址缺乏灵活性

假设一家公司有 5000 台电脑，其需要 5000 个网络地址，基于 A、B、C 三类的分类寻址，每个网络号下的主机都是分在一个网络内的，但是我们不一定希望这 5000 台电脑都在同一网络内，基于分类寻址是无法做到的。

- 地址空间利用率低

当初将地址分为 A、B、C 类，对应于现实中的大、中、小型组织，但是类之间的网络地址的数量跨度太大了。

B 类地址空间太大了，而 C 类地址空间只有 254 个又太小了，而 A 类地址更是很少会用到。如果一家公司有 5000 台电脑需要 5000 个网络地址，此时如果申请 B 类地址，会有近 6 万个地址是浪费的，现实世界中一定会有大量电脑超过 254 但又远远少于 65534 台的公司、组织，如果都采用 B 类地址，将会造成大量的地址空间浪费。

![](/images/ipv4-classful-address-02.png)

- 路由性能较差

还是以 5000 台电脑需求为例，分配 B 类地址会造成大量地址空间浪费，但如果分配 C 类，此时需要近 20 个 C 类地址空间，虽然提高了网络空间的利用率，这会造成路由表的增大，而路由表越大，其路由所耗费的时间越多，从而会降低网络性能。


**解决方案**

基于分类寻址的若干问题，目前主要有三种解决方案：

- （1）子网寻址: 将主机号继续划为子网号和主机号，主要解决了第一个内部地址不灵活的问题。
- （2）无类寻址：比较好的解决了上述问题，帮 IPv4 续了不知道多少 s 的命，但依然受制于 IPv4 地址的数量问题。
- （3）IPv6：彻底解决上述问题。

#### 2.2 IP 子网寻址(Subnet addressing)

上面提到的分类寻址很快暴露出了问题，[RFC 950](https://tools.ietf.org/html/rfc950)提出了新的寻址方式：子网寻址。

子网寻址是将原来分类寻址中的 hostID 主机号进一步划分为子网ID（subnet Id）和主机 ID，如图所示：

![](/images/ipsubnet.png)


使用子网寻址后一个很重要的问题是如何区分子网号与主机号。这是通过 **子网掩码（subnet mask）** 实现的。子网掩码的格式也 IPv4 地址一样，也是 32 位的二进制数。对于网络号和子网号，子网掩码的数值是 1，对于主机号则是 0。如图所示，对于 B 类地址，我们将 16 位主机号中的前 5 位拿来做子网号，其子网掩码就是 ``11111111 11111111 11111000 00000000``。

![](/images/ipsubnetmask.png)

简单来说，子网掩码只做了一件事:

> 传达子网 ID 与主机 ID 的分割线

因为子网寻址依然是基于分类寻址的，因此通过类别我们可以知道网络号是多少，然后在通过子网掩码找到子网号，路由器就可以将 IP 路由到正确的子网了。具体使用是通过 AND 操作，因为子网掩码中主机号对应的都是 0，则执行 AND 操作之后也都是 0。而主机号和子网号都是 1 ，执行 AND 后保持不变，这样我们就拿到了子网地址，如图所示：

![](/images/ipsubnetmasking.png)

##### 可变子网寻址（VLSM，Variable Length Subnet Masking ）

子网寻址只提供了二级的网络层次结构，依然不够灵活。为了解决该问题，后面又提出了 VLSM 可变子网寻址的方案。

具体方案就是可以对子网进行更多次的划分。如图所示，我们有 C 类网络地址  201.45.222.0/24，

- 第一级划分：两个子网分别有 126 个主机
- 二级划分：将某个子网又分为两个子网，每个含有 62 个主机
- 三级划分：将某个 62 个主机的子网再次划分，分为了包含 14 个主机的 4 个子网。

![](/images/ipvlsm.png)


VLSM 极大提高了网络配置的灵活性。其和下面的 CIDR 已经非常相似了，唯一不同就是 VLSM 是作用于某个子网的，而 CIDR 则是针对整个互联网的。


#### 2.3 无类寻址

**子网寻址的问题**

子网寻址虽然实现了网络内部的二级划分，但是其网络依然是基于 A/B/C 分类寻址来设计，其最根本的问题依然没有解决：A、B(65534个地址) 类型的网络包含的地址数量太大了，而 C 类（254）地址数量又太少了。世界上存在众多组织其所需要的地址大于 254 而又远远小于 65534。

比如一个公司只有 2000 台机器，如果分配 B 类地址，那么会有超过 62,000 个地址被浪费，这让本就数量不足的 IPv4 地址雪上加霜。而如果分配 C 类地址，我们需要申请 10 个 C 类网络，而这会造成路由表条目变为 10 条，极大降低路由效率，同时不足以支持内部对网络进行更加灵活的划分。

由于子网寻址并不能彻底解决分类寻址带来的问题，人们意识到在 IPv6 普及之前还需要一种新的方案来延长 IPv4协议的使用寿命。这一方案就是 **无类域间路由(CIDR:Classless Inter-Domain Routing )**。

##### CIDR 简介

CIDR 在 1993 年提出，其相关的 RFC 为 [RFC1517](https://tools.ietf.org/html/rfc1517)，[RFC1518](https://tools.ietf.org/html/rfc1518)，[RFC1519](https://tools.ietf.org/html/rfc1519)，[RFC1520](https://tools.ietf.org/html/rfc1520)。

CIDR 的核心思想是将子网划分的概念拓展至整个网络，并且除了一级子网划分之外可以对网络进行任意划分，形成多层次结构的网络。

**CIDR的好处**

- 有效的地址分配：不同于分类寻址只能按类和子网分配地址，CIDR 可以以任何二的倍数的大小分配地址。如果一个组织需要 5000 个地址，那么我可以分配 8190 个地址(2^13) 即可，而无需分配 65534 个 B 类地址。
- 高效路由：CIDR 的多层次结构网络可以通过少量的路由条目
- 通用的的子网划分方案

当然 CIDR 在解决分类寻址带来的问题的同时也存在一些新的问题，主要是两个：
- 复杂性：分类寻址可以很容易知道是网络号和主机号，而 CIDR 没有那么简单

##### CIDR 网络分层与表示

CIDR 消除了类型的概念，因此无法像之前那样明确的知晓一个 IP 地址中哪些是网络号，哪些是主机号。其采用新的 **CIDR 斜杠表示法(CIDR notation 或者 Slash notation.)**，通过在 IP 地址后跟 **斜杠/数字** 的形式，数字代表了网络号所占位数。比如对于地址 184.13.152.0/22，其后面跟了 22，表示该 IP 地址中有 22位是网络号，10 位是主机号。

通过 CIDR 我们可以自由的对网络进行划分。

下面是 CIDR 的一个示例，首先将网络分成了两个包含 65534 个主机的子网，网络地址分别是

- 子网1：71.94.0.0/16
- 子网2：71.95.0.0/16

然后又将第二个子网继续划分，如图所示：

![](/images/cidr-hierarchical-address-division.png)


### 三. IP 数据包的封装与格式 


对于由多层协议组成的 TCP/IP 协议栈，底层的协议会将上层协议发送过来的数据完整打包，将其封装为自己的消息格式，并添加包含重要的控制信息的包头(Header)和页脚（footer）。

![](/images/IP-datagram-encapsulation.png)


IP 协议会将传输的数据封装为 IP 数据报（IP datagram），其内部包含了 IP 传输数据所需的若干属性字段。IP 数据包格式如图：

![](/images/ipv4-datagram-format.png)

各个字段含义如下表：

  字段名  |大小 (字节 Bytes)|描述
---------|----------------|--
 Version | 1/2 (4 bit)    | IP 协议版本号，对于 IPV4 就是 4，对于 IPV6 就是 6。
 HL      | 4 bit          | Header Length: IP 包头部长度，以 32 bit（4字节）为单位，在没有额外可选字段的情况下为 5，即 IP 头默认为 20 字节。
 TOS     | 1              | Type Of Service: 服务类型，包含 3 bit 的优先权子字段，4 bit 的 TOS 子字段和 1 bit 必须设置为 0 的未用位。
 TL      | 2              | Total Length: IP 数据报总字节长度，该字段长度为 16 位，因此 IP 数据报的最大长度为 2^16 - 1= 65,535 字节，当然实际的 IP 数据报一般会小得多。
 Identification | 2       | 标识符，主要用于 IP datagram 的分片中，拥有相同标识符的分片属于同一个 IP datagram。
 Flags   | 3/8(3 bit)     | 标志位：有三个标志位，两个用于数据分片管理，一种作为保留。具体和含义参考下面的 Flag 字段表。
 Fragment Offset  | 1 + 5/8(13 bits)  | 分片偏移量，当 IP 数据报发生分片时，其以 8 字节(64 bit) 为单位标识数据包的偏移量，便于数据重组。第一个数据包的 offset 为 0。
 TTL     | 1              | Time To Live: IP 数据报在网络上的存活时间，这里的数值指的是路由跳数，即数据报可以经过的最多路由器数。路由过程中，每个路由器发送数据报前将 TTL 减 1，如果 TTL 降为 0 说明路由时间过长，会将其丢弃，并通过 ICMP 协议通知源主机。
Protocol | 1              | 上层协议，用于表示 IP 传输的数据是由哪些协议发过来的，比如 TCP、UDP、ICMP 等。
Header Checksum | 2       | 首部校验和，用于验证数据，防止数据被损坏，其仅计算 IP 首部的内容，不对首部后面的数据做计算。具体计算方式是：首先将校验和字段设置为 0，然后每两个字节（16bit）做一次计算得到补码，然后求和。
Source Address  | 4       | 源地址，即最初发送数据报的设备的地址
Destination Address | 4   | 目的地址，即 IP 数据报最终接收者的地址| 
Options | Variable| 数据报可选项
Padding | Variable| 填充字段，如果 IP 数据报包含可选项字段，其长度必须为 32bit 的倍数，如果不足的话需要填充补齐。
Data    | Variable| IP 数据报要传递的数据，一般来自上层协议，比如 TCP 段

**Protocol 字段值**

对于 Protocol 字段，其可选值如下：

值（10进制）|值（16进制）|代表协议
--|--|--
0 | 00 | 保留字段
1 | 01 | ICMP
2 | 02 | IGMP
3 | 03 | GGP
4 | 04 | IP-in-IP Encapsulation
6 | 06 | TCP
8 | 08 | EGP
17 | 11 | UDP
50 | 32 | Encapsulating Security Payload (ESP) Extension Header
51 | 33 | Authentication Header (AH) Extension Header

**Flag 字段值**

对于 Flag 字段，其可选值如下：

子字段名 |大小(字节)|描述
--|--|--
 Reserved               | 1/8 (1 bit)| 保留字段，不使用
 DF（Don't Fragment）   |1/8 (1 bit)| 当设置为 1 时表示不允许分片，在测试传输路径 MTU 时会设置为 1，其他大多数情况下都不会用到。
 MF (More Fragments)   |1/8 (1 bit)| 表示某个消息的分片是否已经结束。当某条消息没有被分片时，设置为 0 ；如果某条消息被分片了，除了最后一条设置为 0 外，其他的数据报均设置为 1 表示还会有更多的分片没有到达。
 
 **TOS 字段值**
 
对于 TOS 字段，其可选值如下：

子字段名 |大小|描述
--|--|-- 
Precedence(优先权) | 3bit | 标识数据报的优先级，有 8 个值 |
D        | 1bit |设置为 1 标识号最小时延
T        | 1bit |设置为 1 标识最大吞吐量
R        | 1bit |设置为 1 标识最高可靠性
M        | 1bit |设置为 1 标识最小费用
Reserved| 1bit | 保留位，必须为 0

如果 DTRM 4 位均为 0 则意味着一般服务。

**Node**

《The TCP/IP Guide》中的表格只给出了 3bit 的 TOS 字段，而保留字段为 2bit，而《TCP/IP 详解》和 [RFC1349](https://tools.ietf.org/html/rfc1349#page-4) 中都是 4bit 的 TOS 字段和 1bit 保留字段，暂时没找到其他和 《The TCP/IP Guide》相同表示的资料，因此以 RFC 为准。

下面是 RFC1349 中对 TOS 字段的部分描述：
![](/images/ip-tos-field-format.png)

![](/images/ip-tos-subfield-values.png)


**IP Options**

除了固定的 20 字节的 IP 头之外，IP 数据包还提供了 Options 字段来保证 IP 数据包的扩展性。IP Option 没有明确定义，不同类型的字段其包含的数据内容也不同，一般来说，IP Option 都是下面这种格式。

![](/images/ipoptionformat.png)

字段名|大小|描述|
--|--|--
Type | 8bit  | 可选字段类型，一般来说就是 1 个数字，但有的还可以细化为三个字段，如上图所示
Len  | 0 或者 8bit | 表示可选字段的长度
Data | 可变长度 |可选项字段的数据，比如路由信息、时间戳等，不同类型的可选项字段包含的数据不同

可用的 IP Option 字段如下

类别 | 编号 | 长度（bytes） | 名称 | 描述
--|--|--|--|--
 0| 0| 1 | 选项列表结尾 | 表示 IP Option 字段的结尾，如果此时 IP 头长度不是 32bit 的倍数，后面会跟填充位。
 0| 1| 1 | 无操作 | 虚拟选项，需要时被用作“内部填充”，以在32位边界上对齐某些选项
 0| 2| 11 | 安全性 | 用于指示 IP 数据报的安全性分类
 0| 3| 可变 | 松散的源路由 | 携带一系列路由地址，要求 IP 报必须经过这些地址进行路由，但允许中间有跳跃节点。
 0| 7| 可变 | 记录路由 | 用来记录路由过程中的 IP 地址，在 ping 命令中可以用到
 0| 9| 可变 | 严格的源路由 | 其会携带一系列路由地址，要求 IP 报的路由路径必须经过这些地址
 2| 4| 可变 | 时间戳 | 用于指示记录时间戳，ping 命令中会用到
 2| 18| 12 | traceroute | 追踪路由，在 traceroute 命令中会用到


### 四. IP 数据包的分片与重组

IP 数据报最终是经链路层传输的，链路层有 MTU（maximum transmission unit，最大传输单元）的限制，表示链路层能传输的最大数据值，也就是能传输的 IP 数据报的最大值。比如 Ethernet 的默认 MTU 是 1500 字节，如果一个 IP 数据报的大小超过 1500 字节，那么就会被分片传输。

#### 4.1 IP 分片处理

IP 的分片可以发生在路由过程的任一节点，因此最终的分片结果一定是基于路由过程中最小的 MTU 决定的。

下图是一个 IP 分片的示例：

![](/images/IPv4-datagram-fragmentation-process.png)

我们有一个 12000 字节的 IP 数据报，包括 IP header 20 个字节和 11980 字节的数据。而链路层的 MTU 只有 3300 字节，因此 IP 数据报会发生分片，分层结果如上图所示，IP 数据报被分为了四个：

- 01：MF 为 1 表示有更多的数据报，offset 为 0，数据大小为 3280 字节，这样加上 IP header 就是 3300 字节等于 MTU 大小。
- 02、03 MF 也都为 1，数据大小也一样，唯一不同的是 Offset
- 04 的 MF 为 0 表示没有更多的分片了，其数据大小只有 2140 字节。

分片后四个分片的 IP header 与原始数据报基本相同。有几个不同的地方如下：

- **Total Length**：分片后的 TL 字段等于各个分片的长度，而不是原始 IP 数据报的长度。
- **Identification**：如果有众多的 IP 数据报需要分配，我们需要知道哪些分片是属于同一个 datagram 的。Identification 字段起到了这个作用，同一个数据报的分片有相同的 Identification。
- **More Fragments**: 01、02、03 的 More Fragments 标志位都为 1 表示还有分片，04 也就是最后一个分片为 0 表示这是该 IP 数据报的最后一个分片。
- **Fragment Offset**：表示分片的偏移量，是基于数据偏移量计算的，其单位为 8字节。比如 01 号分片的数据大小为 3280，那么 ``3280 / 8 = 410`` 因此 02 号分片的 offset 为 410。该字段为 13 位，则其表示的最大偏移量为 ``2^13 - 1 = 8191``，也就是 ``8191 * 8 = 65528`` 字节，已经接近 IP 数据报的最大值 65535 字节了。
  
上图还演示了二次分片的过程，在路由过程中可能会遇到 MTU 更小的路由设备，因此难免发生二次分片，其过程与我们上述是一样的。

对于 IP 分片有下面几个问题:

- **影响性能**：可以看到分片后每个分片都多了个 IP header，传输的数据更多了，同时 IP 数据报的分片与重组也会消耗性能。
- **分片丢失**：**IP 分片一旦丢失，整个数据报都要重传。** 因为整个路由过程都有可能发生分片，源主机并不知道哪个分片丢失了，因此只能重传整个数据报。当然我们知道 IP 协议是不支持重传的，因此这一般是由上层协议实现。因为重传会影响性能，因此 TCP 协议会尽量避免重传。

#### 4.2 IP 数据报重组

分片可以发生在源主机或者路由器上，但是 IP 数据包的重组只会在最终目的机器上进行。这样设计的原因有下面几个：

- IP 包的传输路径不一致，某个路由器可能无法获取所有的 fragment
- 增加路由器的复杂度
- 重组耗费时间，影响路由性能

所以路由器并不关心 IP 数据包的重组，只管路由即可。但其也会有下面几个问题：

- 与在中间进行重组相比，fragment 在网络中传输的路径越长，其丢失的风险就越高
- 无法充分利用链路层的传输效率



