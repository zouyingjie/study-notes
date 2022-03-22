<!-- TOC -->

- [1. 网络基础知识](#1-网络基础知识)
  - [1.1 Linux Network Stack](#11-linux-network-stack)
  - [1.2 Netfilter](#12-netfilter)
  - [1.3 Iptables](#13-iptables)
  - [1.4 IPSet](#14-ipset)
- [2. Kubernetes CNI](#2-kubernetes-cni)
- [3. Container To Container](#3-container-to-container)
- [4. Pod To Pod](#4-pod-to-pod)
  - [4.1 跨主机的网络通信](#41-跨主机的网络通信)
  - [4.2 Flannel UDP](#42-flannel-udp)
  - [4.3 Flannel VXLAN](#43-flannel-vxlan)
  - [4.4 Flannel host-gw](#44-flannel-host-gw)
  - [4.5 Calico 路由](#45-calico-路由)
- [5. Service To Pod](#5-service-to-pod)
  - [5.1 Service 简介](#51-service-简介)
  - [5.2 Service 分类](#52-service-分类)
    - [5.2.1 ClusterIP](#521-clusterip)
    - [5.2.2 NodePort](#522-nodeport)
    - [5.2.3 LoadBalancer](#523-loadbalancer)
    - [5.2.4 ExternalName Service](#524-externalname-service)
    - [5.2.5 Headless Service](#525-headless-service)
  - [5.4 Kube-proxy 工作模式](#54-kube-proxy-工作模式)
    - [5.4.1 UserSpace 模式](#541-userspace-模式)
    - [5.4.2 iptables 模式](#542-iptables-模式)
    - [5.4.3 ipvs 模式](#543-ipvs-模式)
    - [5.4.4 性能对比](#544-性能对比)
- [6. Ingress](#6-ingress)
  - [6.1 fanout](#61-fanout)
  - [6.2 常用注解](#62-常用注解)
  - [6.4 启用TLS](#64-启用tls)
- [7. DNS for Service](#7-dns-for-service)
  - [7.1 普通 Service](#71-普通-service)
  - [7.2 Headless Service](#72-headless-service)
  - [7.3 Pod](#73-pod)

<!-- /TOC -->

### 1. 网络基础知识

#### 1.1 Linux Network Stack

Linux 网络栈示例如下，其符合 TCP/IP 网络模型以及 OSI 模型的理念，网络数据包在链路层、网络层、传输层、应用层之前逐层传递、

![在这里插入图片描述](https://img-blog.csdnimg.cn/f128681ee2b847d9a13859eed562161c.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

图片来自 https://icyfenix.cn/immutable-infrastructure/network/linux-vnet.html

#### 1.2 Netfilter 

Netfilter 是 Linux 内核提供的一个框架，它允许以自定义处理程序的形式实现各种与网络相关的操作。 Netfilter 为数据包过滤、网络地址转换和端口转换提供了各种功能和操作，这些功能和操作提供了引导数据包通过网络并禁止数据包到达网络中的敏感位置所需的功能。

简单来说，netfilter 在网络层提供了 5 个钩子（hook），当网络包在网络层传输时，可以通过 hook 注册回调函数来对网络包做相应的处理，hook 如图所示：

![在这里插入图片描述](https://img-blog.csdnimg.cn/e027a5e12f0e40dcbadf218ea4218a94.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)



图片来自 https://www.teldat.com/blog/en/nftables-and-netfilter-hooks-via-linux-kernel/

Netfilter 提供的 5 个 hook 分别是：

- **​​Prerouting：路由前触发**。设备只要接收到数据包，无论是否真的发往本机，都会触发此hook。一般用于目标网络地址转换（Destination NAT，DNAT）。

- **Input：收到报文时触发**。报文经过 IP 路由后，如果确定是发往本机的，将会触发此hook，一般用于加工发往本地进程的数据包。
- **Forward 转发时触发**。报文经过 IP 路由后，如果确定不是发往本机的，将会触发此 hook，一般用于处理转发到其他机器的数据包。
- **Output 发送报文时触发**。从本机程序发出的数据包，在经过 IP 路由前，将会触发此 hook，一般用于加工本地进程的输出数据包。
- **Postrouting 路由后触发**。从本机网卡出去的数据包，无论是本机的程序所发出的，还是由本机转发给其他机器的，都会触发此hook，一般用于源网络地址转换（Source NAT，SNAT）。

Netfilter 允许在同一个钩子处注册多个回调函数，在注册回调函数时必须提供明确的优先级，多个回调函数就像挂在同一个钩子上的一串链条，触发时能按照优先级从高到低进行激活，因此钩子触发的回调函数集合就被称为“回调链”（Chained Callback)。

Linux 系统提供的许多网络能力，如数据包过滤、封包处理（设置标志位、修改 TTL等）、地址伪装、网络地址转换、透明代理、访问控制、基于协议类型的连接跟踪，带宽限速，等等，都是在 Netfilter 基础之上实现，比如 XTables 系列工具， iptables ，ip6tables 等都是基于 Netfilter 实现的。

#### 1.3 Iptables

iptables 是 Linux 自带的防火墙，但更像是一个强大的网络过滤工具，它在 netfilter 的基础上对回调函数的注册做了更进一步的抽象，使我们仅需要通过配置 iptables 规则、无需编码就可以使用其功能。

Iptables 内置了 5 张规则表如下：
- **raw 表**：用于去除数据包上的[连接追踪机制](https://en.wikipedia.org/wiki/Netfilter#Connection_tracking)（Connection Tracking）。
- **mangle 表**：用于修改数据包的报文头信息，如服务类型（Type Of Service，ToS）、生存周期（Time to Live，TTL）以及为数据包设置 Mark 标记，典型的应用是链路的服务质量管理（Quality Of Service，QoS）。
- **nat 表**：用于修改数据包的源或者目的地址等信息，典型的应用是网络地址转换（Network Address Translation），可以分为 SNAT（修改源地址） 和 DNAT（修改目的地址） 两类。
- **filter 表**：用于对数据包进行过滤，控制到达某条链上的数据包是继续放行、直接丢弃或拒绝（ACCEPT、DROP、REJECT），典型的应用是防火墙。
- **security 表**：用于在数据包上应用SELinux，这张表并不常用。

上面5个表的优先级是 raw→mangle→nat→filter→security。在新增规则时，需要指定要存入到哪张表中，如果没有指定，默认将会存入 filter 表。另外每个表能使用的链也不同，其关系如图所示：


![在这里插入图片描述](https://img-blog.csdnimg.cn/73b9c33745de45b0b1490ce330ccc4d1.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

图片来自 https://icyfenix.cn/immutable-infrastructure/network/linux-vnet.html

![在这里插入图片描述](https://img-blog.csdnimg.cn/0a6b5345a4234dbc8b35c52d2ec79ff5.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

图片来自 https://www.teldat.com/blog/en/nftables-and-netfilter-hooks-via-linux-kernel/

#### 1.4 IPSet

IPSet 是 Linux 内核的网络框架，是 iptables 的配套工具，其允许通过 ipset 命令设置一系列的 IP 集合，并针对该集合设置一条 iptables 规则，从而解决了 iptables 规则过多的问题，带来如下的一些好处：
- 存储多个 IP 和端口，并且只需要创建一条 iptables 规则就可以实现过滤、转发，维护方便。
- IP 变动时可以动态修改 IPSet 集合，无需修改 iptables 规则，提升更新效率。
- 进行规则匹配时，时间复杂度由 O(N) 变为 O(1)。

### 2. Kubernetes CNI 

Kubernetes 本身并没有提供网络相关的功能，鉴于网络通信的复杂性与专业性，为了具有更好的扩展性，在一开始业界的做法就是将网络功能从容器运行时以及容器编排工具中剥离出去，形成容器网络标准，具体实现以插件的形式接入。

在早期 Docker 提出过 CNM规范（Container Network Model，容器网络模型），但被后来 Kubernetes 提出的 CNI（Container Network Interface，容器网络接口）规范代替，两者功能基本一致。

CNI 可以分为两部分：
- CNI 规范：定义操作容器网络的各种接口，比如创建、删除网络等。Kubernetes 中容器运行时面向 CNI 进行通信。
- CNI 插件：各个厂商基于 CNI 实现各自的网络解决方案，以插件的形式安装在 Kuberetes 中。下图是一些常见的插件提供商:
![在这里插入图片描述](https://img-blog.csdnimg.cn/8f7f2b12d4a84d159ee4977c306bca82.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)




有了 CNI 规范后，针对容器的网络操作就只需要面向 CNI 即可，Kubernetes 不在关心具体的实现细节。无论实现如何，Kuberetes 对网络提出如下要求：

- 每个 Pod 都要有自己的 IP
- 节点上的 pod 可以与所有节点上的所有 pod 通信，无需 NAT
- 节点上的代理（例如系统守护进程、kubelet）可以与该节点上的所有 Pod 通信

下图是使用 Flannel 网络插件，在创建 Pod 时为 Pod 分配 IP 的过程，可以看到 Pod 的 IP 是由容器运行时访问 CNI 接口，最终由 Flannel 网络插件提供的。

![在这里插入图片描述](https://img-blog.csdnimg.cn/c1e33dd1301e496d8e7d14f081422ef8.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

 
图片来自 https://ronaknathani.com/blog/2020/08/how-a-kubernetes-pod-gets-an-ip-address/
### 3. Container To Container

首先看 Docker 容器的通信方式，Docker网络分成4种类型

- **Bridge** ：默认的网络模式，使用软件桥接的模式让链接到同一个Bridge上的容器进行通讯。
- **Host**：直接使用本机的网络资源，和普通的原生进程拥有同等的网络能力。
- **MacVLAN**：允许为容器分配mac地址，在有直接使用物理网络的需求下可以使用该网络类型。 
- **Overlay**：在多个Docker daemon host中创建一个分布式的网络，让属于不同的daemon host的容器也能进行通讯。

Bridge, Host, MacVLAN都是本机网络，Overlay是跨宿主机的网络。当不允许 container 使用网络的场景下，比如生成密钥哈希计算等，有安全性需求，可以将网络类型设置成 None，即不允许该容器有网络通讯能力。

这里重点关注 Bridge 网桥模式，Docker 启动时会创建一个名为 docker0 的网桥，同主机上容器之间的通信都是通过该网桥实现的，如图所示：

![在这里插入图片描述](https://img-blog.csdnimg.cn/e96df05218744cb1bef4dbf009268232.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)


当启动容器后，docker 会通过 Veth（Virtual Ethernet）对将容器和 docker0 网桥连起来，同时修改容器内的路由规则，当 container1 向 container2 发起请求时，基于路由规则会将请求路由到 docker0 网桥，然后在转发到 container2 中。

```bash
$ docker ps
CONTAINER ID        IMAGE               COMMAND                  CREATED             STATUS              PORTS               NAMES
c89802c02b43        nginx               "/docker-entrypoint...."   13 seconds ago      Up 11 seconds       80/tcp              quirky_goodall
bf6ff6504dfa        nginx               "/docker-entrypoint...."   36 seconds ago      Up 33 seconds       80/tcp              festive_bardeen

$ ip link
3: docker0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP mode DEFAULT group default
    link/ether 02:42:f1:6c:7e:36 brd ff:ff:ff:ff:ff:ff
5: vethbc789f2@if4: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue master docker0 state UP mode DEFAULT group default
    link/ether 8a:22:a5:d4:28:8e brd ff:ff:ff:ff:ff:ff link-netnsid 0
7: veth5bed0bd@if6: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue master docker0 state UP mode DEFAULT group default
    link/ether 22:bc:9a:cc:2f:d3 brd ff:ff:ff:ff:ff:ff link-netnsid 1

$ brctl show
bridge name	bridge id		STP enabled	interfaces
docker0		8000.0242f16c7e36	no	veth5bed0bd
							vethbc789f2
```



进入容器看容器内部的网络设备：

```bash
$ docker run -it --net container:c89802c02b43  nicolaka/netshoot

 c89802c02b43  ~  ifconfig
eth0      Link encap:Ethernet  HWaddr 02:42:AC:11:00:03
          inet addr:172.17.0.3  Bcast:172.17.255.255  Mask:255.255.0.0
          UP BROADCAST RUNNING MULTICAST  MTU:1500  Metric:1
          RX packets:66 errors:0 dropped:0 overruns:0 frame:0
          TX packets:0 errors:0 dropped:0 overruns:0 carrier:0
          collisions:0 txqueuelen:0
          RX bytes:4716 (4.6 KiB)  TX bytes:0 (0.0 B)

lo        Link encap:Local Loopback
          inet addr:127.0.0.1  Mask:255.0.0.0
          UP LOOPBACK RUNNING  MTU:65536  Metric:1
          RX packets:0 errors:0 dropped:0 overruns:0 frame:0
          TX packets:0 errors:0 dropped:0 overruns:0 carrier:0
          collisions:0 txqueuelen:1000
          RX bytes:0 (0.0 B)  TX bytes:0 (0.0 B)


 c89802c02b43  ~  cat /sys/class/net/eth0/iflink
7

 c89802c02b43  ~  route
Kernel IP routing table
Destination     Gateway         Genmask         Flags Metric Ref    Use Iface
default         172.17.0.1      0.0.0.0         UG    0      0        0 eth0
172.17.0.0      *               255.255.0.0     U     0      0        0 eth0
```




我们完全可以通过命令行模拟上述操作，实现两个网络 namespace 的互通，步骤如下：

**1. 创建 namespace**

```bash
$ sudo ip netns add test1
$ sudo ip netns add test2
```


**2. 创建 veth 对**

```bash
$ sudo ip link add veth-test1 type veth peer name veth-test2
```


**3. 将 veth 对加到 namespace 中**

```bash
$ sudo ip link set veth-test1 netns test1
$ sudo ip link set veth-test2 netns test2
```


**4. 给 veth 添加地址**

```bash
$ sudo ip netns exec test1 ip addr add 192.168.1.1/24 dev veth-test1
$ sudo ip netns exec test2 ip addr add 192.168.1.2/24 dev veth-test2
```


**5. 启动 veth 设备**

```bash
$ sudo ip netns exec test1 ip link set dev veth-test1 up
$ sudo ip netns exec test2 ip link set dev veth-test2 up
```


**6. 在 test1 namespace 中访问 test2**

```bash
$ sudo ip netns exec test1 ping 192.168.1.2
PING 192.168.1.2 (192.168.1.2) 56(84) bytes of data.
64 bytes from 192.168.1.2: icmp_seq=1 ttl=64 time=0.060 ms
64 bytes from 192.168.1.2: icmp_seq=2 ttl=64 time=0.053 ms
64 bytes from 192.168.1.2: icmp_seq=3 ttl=64 time=0.038 ms
```


### 4. Pod To Pod

#### 4.1 跨主机的网络通信

跨主机的网络通信方案目前主要有三种方式：

**Overlay 模式**

所谓 Overylay 网络就是在一个网络之上在覆盖一层网络，对应到我们的云原生环境，为了支持 Pod 的灵活变化，我们可以在基础物理网络之上，在虚拟化一层网络来进行网络通信。位于上层的网络就叫做 Overlay，而底层的基础网络就是 Underlay。Overlay 网络的变化不受底层网络结构的约束，有更高的自由度和灵活性。但缺点在于 Overlay 网络传输是依赖底层网络的，因此在传输时会有额外的封包，解包，从而影响性能。像 Flannel 的 VXLAN 、Calico 的 IPIP 模式、Weave 等 CNI 插件都采用的了该模式。

**路由模式**

该模式下跨主机的网络通信是直接通过宿主机的路由转发实现的。和 Overlay 相比其优势在于无需额外的封包解包，性能会有所提升，但坏处时路由转发依赖网络底层环境的支持，要么支持二层连通，要么支持 BGP 协议实现三层互通。Flannel 的 HostGateway 模式、Calico 的 BGP 模式都是该模式的代表。


**Underlay 模式**

容器直接使用宿主机网络通信，直接依赖于虚拟化设备和底层网络设施，上面的路由模式也算是 Underlay 模式的一种。理论上该模式是性能最好的，但因为必须依赖底层，并且可能需要特定的软硬件部署，无法做到 Overlay 那样的自由灵活的使用。

下面以 Flannel 和 Calico 为例看下 Overlay 和路由模式的具体实现。

#### 4.2 Flannel UDP

Flannel 是 CoreOS 为Kubernetes设计的配置第三层网络（IP层）的开源解决方案。在 UDP 模式下，Flannel 会在每个节点上运行名为 flanneld 二进制 agent，同时创建名为 flannel0 的虚拟网络设备，在主机网络上创建另外一层扁平的网络，也就是所谓的 Overlay network，所有集群内的Pod会被分配在这个overlay 网络中的一个唯一IP地址，然后Pod 之间的就可以直接使用的这个IP地址和对方通讯了。另外每个节点上还会创建一个 cni0 bridge 来实现本机容器的通讯。

![在这里插入图片描述](https://img-blog.csdnimg.cn/51b7fc7ec6ef436abeec9661776e2d9b.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)


以上图为例， Node1 节点中 IP 为 100.96.1.2 的 container-1 容器要访问节点 Node2 中 IP 为 100.96.2.3 的 container2 容器。转发过程如下：

- 创建原始 IP 包，源地址为 容器1 的 IP，目的地址为容器 2 的 IP。
- 经过 cni0 bridge 转发到 flannel0（依赖内核路由表）
- 发给 flannel0 的包会由 flanneld 程序处理，它会将 IP 包作为数据打包到 UDP 包中，并在存储中（etcd）找到目标地址的实际 IP 地址（即 Node2 的 IP 地址），最终封装为完整的 IP 包进行发送。
- Node2 收到 IP 包后进行逆向操作，先将数据报发送给 flanneld 程序，解包后经 flannel0、cni0 bridge 最终发送到容器。

网络包路线定位可以通过下面一些工具实现和基本流程		:

- `brctl show` 可以看到容器的网卡绑在了哪个网桥上，当网络包到了 cni0 上就基本上进入路由了。
netstat -nr 可以看到路由表的转发规则，一般会把会把其转到 flannel.1 这个网卡上。
- 用arp命令看一下 `arp -i flannel.1` 可以知道各个网段的mac地址。

UDP 模式涉及到 3 次内核态和用户态的转换，因此性能较低，已经被废弃。

![在这里插入图片描述](https://img-blog.csdnimg.cn/a221e5a32c66414995640a95d4fb954c.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

#### 4.3 Flannel VXLAN

UDP 模式下的打包过程涉及到用户态与内核态的转换，严重影响性能，现在已经被弃用。新的 Overlay 实现方式是通过 VXLAN 在主机之间建立逻辑隧道，并且直接在内核打包，从而提升性能。

VXLAN 隧道网络结构如下：

![在这里插入图片描述](https://img-blog.csdnimg.cn/474c6d3774b84f52af848dc685b3a098.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_15,color_FFFFFF,t_70,g_se,x_16)

图片来自https://support.huawei.com/enterprise/en/doc/EDOC1100023542?section=j016&topicName=vxlan


- **VETP( Virtual Tunnel Endpoints )**: VXLAN 网络的虚拟边缘设备，是 VXLAN 隧道的起点与终点，负责 VXLAN 中的封包与解包。和 UDP 模式下 flanneld 程序作用类似。

- **VNI（VXLAN Network Identifier）**：VXLAN 的网络标识，代表一个 VXLAN 网段，只有相同网段下的 VXLAN 虚拟设备才可以互相通信。

- **VXLAN tunnel**：VXLAN 隧道就是在两个 VETP 之间建立的可以用于报文传输的逻辑隧道。业务数据报被 VETP 设备进行封包，然后在第三层透明的传到远端的 VETP 设备在进行数据的解包。

下图是 VXLAN 的数据报格式，VETP 会将发来的原始二层数据包加上 VXLAN Header（主要包含 VNI） 后发送给主机，主机在封装为 UDP 包进行传输：

![在这里插入图片描述](https://img-blog.csdnimg.cn/5c793b241c0e46fea164d0179c9ccac2.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)


下图是 Flannel 在 VXLAN 模式下的数据传输过程：

![在这里插入图片描述](https://img-blog.csdnimg.cn/e5503a95b7524031941dba1ca1725264.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)


1. 该模式下 Flannel 会创建 flannel.vni 的 VETP 设备
2. node0 的 container1 请求 node1 的 container 1，数据报经由 cni0 bridge 传输到 flanel.vni 设备进行  VXLAN 封包。然后作为 UDP 包发出。
3. UDP 包走正常路由发送到 node1 的 vetp 设备进行解包，最终发送到目标容器

#### 4.4 Flannel host-gw

Flannel 现在默认是 VXLAN 模式，可以通过修改其配置设置为 host-gw 模式。

```yaml
kind: ConfigMap
apiVersion: v1
metadata:
  name: kube-flannel-cfg
  namespace: kube-system
  labels:
    tier: node
    app: flannel
data:
  cni-conf.json: |
   ...
  net-conf.json: |
    {
      "Network": "10.244.0.0/16",
      "Backend": {
        "Type": "vxlan" // change to host-gw
      }
    }
```

在 host-gw 模式下，当 Pod 创建并分配 IP 时，Flannel 会在主机创建路由规则，Pod 之间的通信是通过 IP 路由实现的。

如下图所示，两个节点分别会添加如下 IP 规则：

```bash
Node0: ip route add 192.168.1.0/24 via 10.20.0.2 dev eth0
Node1: ip route add 192.168.0.0/24 via 10.20.0.1 dev eth0
```

![在这里插入图片描述](https://img-blog.csdnimg.cn/b654ce1670fd406983f8d97f8b5556fe.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

图片来自 https://www.digihunch.com/2021/06/kubernetes-networking-solutions-overview/

Flannel-gw 模式有如下几个特点：

- 跨主机通信是通过路由规则实现的，数据包的 next hop 就是目标机器，因此在 Flannel-gw 模式下节点必须都是二层互通的。
- Flannel-gw 模式因为省去了 Overlay 方式下的是数据封包解包因此其性能上会有所提升。

#### 4.5 Calico 路由

Calico 是一个纯三层的网络方案，支持以路由规则的方式进行通信，但和 Flannel-gw 必须要求二层互通不同，Calico 使用 BGP(Border Gateway Protocol) 协议进行跨网络的互通。其工作过程本质上和 Flannel-gw 并无不同，也是在宿主机修改路由规则以及 iptables 实现基于路由的数据转发。当然 Calico 也支持 VXLAN 或者 IPIP 模式的 Overlay 方式的网络方案。

下图是在使用 Calico 时的网络架构：

![在这里插入图片描述](https://img-blog.csdnimg.cn/0c033763323f4428b07ac9688bc58e91.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

主要包含如下组件：

- **Felix**:  每个节点上的 Calico Agent，负责配置路由、iptables 等信息，确保节点之间的连通。

- **BIDR**: BGP 的客户端，负责在各个节点之间同步路由信息，这是 Calico 能够实现三层互通的关键。

- **CNI 插件**：与容器运行时通信，实现具体的 IP 地址分配等工作。

默认情况下 Calico 是 `Node-To-Node-Mesh` 模式，就是 BIDR 会与其他各个节点保持通信以同步信息，该模式一般推荐用在节点数量小于 100 的集群中，在更大规模集群中，更推荐使用 Route Reflector 模式，简单来说就是提供若干个专门的节点负责学习和存储全局的路由规则，各个 BIDR 只需要和 Reflector 节点通信即可。

![在这里插入图片描述](https://img-blog.csdnimg.cn/719e99bca7dd491a922b599b29290068.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)



###  5. Service To Pod

#### 5.1 Service 简介

Kuberetes 通过抽象的 Service 来组织对 Kubernetes 集群内 pod 的访问，因为 pod 是可以被动态创建和销毁的，所以需要一个抽象的资源来维持对其访问的稳定，如同我们用一个 Nginx 对后端服务做做反向代理和负载均衡一样。

可以通过下面的命令创建 Service：

```bash
$ kubectl expose deployment nginx-deploy --port=8080 --target-port=80
service/nginx-deploy exposed

$ kubectl create service nodeport nginx --tcp=80
service/nginx exposed
```

虽然 Service 是对 Pod 的代理和负载均衡，但 Service 和 Pod 并不直接发生关联，而是通过 Endpoints 对象关联。处于 Running 状态，并且通过 readinessProbe 检查的 Pod 会出现在 Service 的 Endpoints 列表里，当某一个 Pod 出现问题时，Kubernetes 会自动把它从 Endpoints  列表里摘除掉。

```bash
$ kubectl get endpoints
NAME           ENDPOINTS                                               AGE
kubernetes     172.19.0.6:6443                                         9h
nginx-deploy   192.168.135.16:80,192.168.135.17:80,192.168.135.18:80   17m

$ kubectl describe svc nginx-deploy
Name:              nginx-deploy
Namespace:         default
Labels:            app=nginx-deploy
Annotations:       <none>
Selector:          app=nginx-deploy
Type:              ClusterIP
IP Families:       <none>
IP:                10.107.138.117
IPs:               10.107.138.117
Port:              <unset>  8080/TCP
TargetPort:        80/TCP
Endpoints:         192.168.135.16:80,192.168.135.17:80,192.168.135.18:80
Session Affinity:  None
Events:            <none>
```


Service 是通过 Label Selector 来选定要代理的 Pod 的，但其可以分为有 Selector 和没有 Selector 的Service， 两者的差别在于， 

- 有 Selector 的 Service 代理一组 Pod，为 Pod 的服务发现服务的
- 没有 Selector 的 Service 通常用来代理外部服务，主要应用于如下的场景:
	- 访问的 Service 在生产中是一个在Cluster 外部的Service， 比如 Database 在生产中不在 Cluster里面，但是需要在 Cluster 内部连接测试。
	- 所访问的服务不在同一个命名空间。
	- 正在迁移服务到 Kubernetes，并只有部分backend服务移到K8s中。

因为 Service 是基于通过 Endpoints 进行代理，所以 Endpoints 内的 IP 地址并不一定都是 Pod 的 IP，完全可以是 K8s 集群外的服务。比如我们的 ElasticSearch 集群是部署在机器上，可以通过下面的方式创建 Service，这样在应用内部就可以配置固定域名来对 ElasticSearch 进行访问，如果 ElasticSearch 服务的 IP 发生变化，只需要修改 EndPoints 即可无需修改应用配置。

```yaml
apiVersion: v1
kind: Service
metadata:
  name: elasticsearch
  namespace: default
spec:
  ports:
  - port: 9200
    protocol: TCP
    targetPort: 9200

---

apiVersion: v1
kind: Endpoints
metadata:
  name: elasticsearch
  namespace: default
subsets:
- addresses:
  - ip: 192.168.24.14
  - ip: 192.168.24.15
  - ip: 192.168.24.16
  ports:
    - port: 9200
```



#### 5.2 Service 分类

Service 主要有下面 5 种类型（ServiceType），分别是



##### 5.2.1 ClusterIP

这是 Service 的默认模式，使用集群内部 IP 进行 Pod 的访问，对集群外不可见。ClusterIP 其实是一条 iptables 规则，不是一个真实的 IP ，不可能被路由到。kubernetes通过 iptables 把对 ClusterIP:Port 的访问重定向到 kube-proxy 或后端的pod上。

##### 5.2.2 NodePort

NodePort 与 Cluster IP不一样的是，它需要调用 kube-proxy 在集群中的每个Node节点开一个稳定的端口（范围 30000 - 32767 ）给外部访问。

当 Service 以 NodePort 的方式 expose 的时候，此时该服务会有三个端口：port，targetPort，nodePort，例如


```yaml
apiVersion: v1
kind: Service
metadata:
  name: hello-world
spec:
  type: NodePort
  selector:
    app: hello-world
  ports:
    - protocol: TCP
      port: 8080
      targetPort: 80
      nodePort: 30036
```


- nodePort: 30036 是搭配 NodeIP 提供给集群外部访问的端口。
- port:8080 是集群内访问该服务的端口。
- targetPort:80 是Pod内容器服务监听的端口。。

##### 5.2.3 LoadBalancer

负载均衡器，一般都是在使用云厂商提供的 Kubernetes 服务时，使用各种云厂商提供的负载均衡能力，使我们的服务能在集群外被访问。

如果是自己部署的集群，Kubernetes 本身没有提供类似功能，可以使用 [MetalLB](https://metallb.universe.tf/) 来部署。

##### 5.2.4 ExternalName Service

ExternalName Service 用来映射集群外的服务，意思就是这个服务不在 Kubernetes 里，但是可以由 Kubernnetes 的 Service 进行转发。比如：我们有一个AWS RDS的服务，集群内的 web 应用配置为使用 URL 为 db-service，但是其它数据不在集群内，它真正的URL是  test.database.aws-cn-northeast1b.com，这时就可以创建 ExternalName 服务将集群内的服务名映射到外部的 DNS 地址。这样 ExernalName 服务并且集群内的其它pod访问 db-service时，Kubernetes DNS服务器将返回带有 CNAME 记录的 test.database.aws-cn-norheast1b.com。


```yaml
kind: Service
apiVersion: v1
metadata:
  name: db-service
spec:
  type: ExternalName
  externalName: test.database.aws-cn-northeast1b.com
  ports:
  - port: 80
```


##### 5.2.5 Headless Service

有时候我们不需要 load balance 转发 ，而是稳定获取 Endpoints 信息，我们可以把Cluster IP设置成 None，如下面的yaml文件所示：

```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginx-headless
spec:
  clusterIP: None
  ports:
  - port: 80
  selector:
    app: nginx
```


下面是相关信息

```bash
$ k get pod -l app=nginx -o wide
NAME                     READY   STATUS    RESTARTS   AGE   IP           NODE           nginx-5848bf568d-npvb7   1/1     Running   0          24h   10.244.2.4   minikube-m03   nginx-5848bf568d-w9dtj   1/1     Running   0          24h   10.244.1.4   minikube-m02  


$ k get endpoints nginx-headless
NAME             ENDPOINTS                     AGE
nginx-headless   10.244.1.4:80,10.244.2.4:80   49s
```


当访问 headless service 时不需要走 kube-proxy 转发，直接选择其中的 Pod IP 进行访问即可。在部署有状态应用经常会用到 headless service 以减少节点之间的通信时间。
#### 5.4 Kube-proxy 工作模式

Service 到 Pod 的转发依赖 iptable/ipvs 的规则，Service 相关的 iptables 规则都在 nat 表里，可以通过检查iptable或者ipvs、nat 查看。


```bash
$ sudo ipvsadm -Ln|grep 10.107.140.247 -A3
TCP  10.107.140.247:80 rr
  -> 10.244.2.28:8080             Masq    1      0          0 
$ sudo  iptables-save -t nat
```


Service 转发请求到 Pod 是基于 kube-proxy 组件实现的，其有三种实现模式

- Userspace 模式
- Iptables 模式
- Ipvs 模式

##### 5.4.1 UserSpace 模式

该模式下，对于 Service 的访问会通过 iptables 转到 kube-proxy，然后转发到对应的 Pod 中。

![在这里插入图片描述](https://img-blog.csdnimg.cn/70886e9cede34461a7cb1b959e2d3fd2.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

这种模式的转发是在用户空间的 kube-proxy 程序中进行到，涉及到用户态和内核态的转换，因此性能较低，已经不再使用。


##### 5.4.2 iptables 模式

该模式下， kube-proxy 仅负责设置 iptables 转发规则，对于 Service 的访问通过 iptables 规则做 NAT 地址转换，最终随机访问到某个 Pod。该方式避免了用户态到内核态的转换，提升了性能和可靠性。

![在这里插入图片描述](https://img-blog.csdnimg.cn/41422ae32f6e48a7b9d947b16243a1a0.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

kube-proxy 在 iptables 模式下运行时，如果所选的第一个 Pod 没有响应， 则连接失败。这与 userspace 模式不同，在 userspace 模式下，kube-proxy 如果检测到第一个 Pod 连接失败，其会自动选择其他 Pod 重试。可以通过设置 readiness 就绪探针，保证只有正常使用 Pod 可以作为 endpoint 使用，保证 iptables 模式下的 kube-proxy 能访问的都是正常的 Pod，从而避免将流量通过 kube-proxy 发送到已经发生故障的 Pod 中。

下面看一个具体例子，我们创建一个 Service 代理三个 Nginx Pod。

```bash
$ kubectl get svc
NAME           TYPE        CLUSTER-IP     EXTERNAL-IP   PORT(S)    AGE
nginx-deploy   ClusterIP   10.98.254.54   <none>        8080/TCP   10m

$ kubectl get endpoints
NAME           ENDPOINTS                                 AGE
nginx-deploy   10.32.0.10:80,10.32.0.9:80,10.40.0.6:80   10m

$ kubectl get pods -o wide
NAME                            READY   STATUS    RESTARTS   AGE     IP           NODE     NOMINATED NODE   READINESS GATES
nginx-deploy-7c8d8c76bf-2wr4t   1/1     Running   0          2d17h   10.40.0.6    node02   <none>           <none>
nginx-deploy-7c8d8c76bf-rbx7d   1/1     Running   0          2d17h   10.32.0.10   node03   <none>           <none>
nginx-deploy-7c8d8c76bf-wwdgq   1/1     Running   0          2d17h   10.32.0.9    node03   <none>           <none>
```



创建 Service 后可以通过 `sudo iptables -L -t nat` 或者  `sudo  iptables-save -t nat` 命令查看 iptables 规则，刚才我们创建的 Service ，其 iptables 规则如下：

```bash
$ sudo iptables -L -t nat
// 将发给 ClusterIP 的包转到 KUBE-SVC-I3MZKEX26BFPQC5G 链
Chain KUBE-SERVICES (2 references)
target                     prot  opt  source               destination
KUBE-SVC-I3MZKEX26BFPQC5G  tcp   --   anywhere             10.98.254.54         /* default/nginx-deploy cluster IP */ tcp dpt:http-alt



// 将发到该链的包随机转发到三个链中，iptables 规则是从上到下逐条匹配，因此要设置匹配概率保证每条的匹配规则相同，下面一次是 1/3，1/2 , 1。
Chain KUBE-SVC-I3MZKEX26BFPQC5G (1 references)
target     prot opt source               destination
KUBE-SEP-JJ4EMQL3SIA7AKAP  all  --  anywhere             anywhere             /* default/nginx-deploy */ statistic mode random probability 0.33333333349
KUBE-SEP-Y6QONWGPY6X4R5GG  all  --  anywhere             anywhere             /* default/nginx-deploy */ statistic mode random probability 0.50000000000
KUBE-SEP-3RXQWVMJJL4LJKWS  all  --  anywhere             anywhere             /* default/nginx-deploy */


// 下面是三条 Pod 的链，其实是三条 DNAT 规则，会在 prerouting 前修改包的目标 IP 和端口，修改为 tcp to 对应的地址与端口，也就是三个 Pod 对应的 IP 地址和端口，从而完成数据报的转发。
Chain KUBE-SEP-JJ4EMQL3SIA7AKAP (1 references)
target          prot opt source               destination
KUBE-MARK-MASQ  all  --  10.32.0.10           anywhere             /* default/nginx-deploy */
DNAT       tcp  --  anywhere             anywhere             /* default/nginx-deploy */ tcp to:10.32.0.10:80

Chain KUBE-SEP-Y6QONWGPY6X4R5GG (1 references)
target     prot opt source               destination
KUBE-MARK-MASQ  all  --  10.32.0.9            anywhere             /* default/nginx-deploy */
DNAT       tcp  --  anywhere             anywhere             /* default/nginx-deploy */ tcp to:10.32.0.9:80

Chain KUBE-SEP-3RXQWVMJJL4LJKWS (1 references)
target     prot opt source               destination
KUBE-MARK-MASQ  all  --  10.40.0.6            anywhere             /* default/nginx-deploy */
DNAT       tcp  --  anywhere             anywhere             /* default/nginx-deploy */ tcp to:10.40.0.6:80
```

从上面的转发规则可以看出，Service 的 ClusterIP 本质上 iptable 规则中的一个 IP ，在 Node 中是没有对应的网络设备的，因此在 Node 上 ping 该 IP 是 ping 不通的。
##### 5.4.3 ipvs 模式

根据 K8s 官方博客 [IPVS-Based In-Cluster Load Balancing Deep Dive](https://kubernetes.io/blog/2018/07/09/ipvs-based-in-cluster-load-balancing-deep-dive/) 介绍，iptables 本质还是为了防火墙目的而设计的，基于内核规则列表工作。这使得随着 K8s 集群的增大，kube-proxy 成为了 K8s 继续扩展的瓶颈，因为节点、Pod、Service 越多，iptables 规则也就越多，在做消息转发时的效率也就越低，因此又出现了 ipvs 模式来解决扩展性的问题。

IPVS（IP Virtual Server）是 Linux 内核中专门用来做负载均衡的工具，其底层也是基于 netfilter 实现的，使用 Hash Table 作为基础数据结构。

![在这里插入图片描述](https://img-blog.csdnimg.cn/8ee2560d86ea455b8e842ffdb359bb04.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)


kube-proxy 会监视 Kubernetes 服务和端点，调用 netlink 接口相应地创建 IPVS 规则， 并定期将 IPVS 规则与 Kubernetes 服务和端点同步。 该控制循环可确保IPVS 状态与所需状态匹配。访问服务时，IPVS 默认采用轮询的方式将流量定向到后端Pod之一。

与 iptables 模式下的 kube-proxy 相比，IPVS 模式下的 kube-proxy 重定向通信的延迟要短，并且在同步代理规则时具有更好的性能。 与其他代理模式相比，IPVS 模式还支持更高的网络流量吞吐量。

![在这里插入图片描述](https://img-blog.csdnimg.cn/52cf37521e45437a9b7dc89d89c8b9de.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)


目前 Kubernetes 默认使用的是 iptables 模式，可以通过修改配置使用 ipvs 模式，

修改 kube-proxy 配置

```bash
$ kubectl edit configmap kube-proxy -n kube-system
// change mode from "" to ipvs
mode: ipvs
```

还是以我们上面的 Service 为例，使用 ipvs 模式后，Service IP、Pod IP 以及 ipset 、iptables 如下：
 
**Service 与 Pod IP**

```bash
$ kubectl describe svc nginx-deploy
Name:              nginx-deploy
Namespace:         default
Labels:            app=nginx-deploy
Annotations:       <none>
Selector:          app=nginx-deploy
Type:              ClusterIP
IP Families:       <none>
IP:                10.107.138.117
IPs:               10.107.138.117
Port:              <unset>  8080/TCP
TargetPort:        80/TCP
Endpoints:         192.168.135.16:80,192.168.135.17:80,192.168.135.18:80
Session Affinity:  None
Events:            <none>
```

 

**IPSet & iptables**

IPSet 包含了所有 Service 的 IP 并且只需要一条 iptables 规则即可

```bash
$ sudo ipset list
Name: KUBE-CLUSTER-IP
Type: hash:ip,port
Revision: 5
Header: family inet hashsize 1024 maxelem 65536
Size in memory: 1176
References: 2
Number of entries: 17
Members:
10.107.138.117,tcp:8080
...
```



只有一条 iptables ，表示在 KUBE-CLUSTER-IP 集合中的 IP 包全部接收。

```bash
$ sudo iptables -L -t nat
Chain KUBE-SERVICES (2 references)
target     prot opt source               destination
KUBE-MARK-MASQ  all  -- !192.168.0.0/16       anywhere             /* Kubernetes service cluster ip + port for masquerade purpose */ match-set KUBE-CLUSTER-IP dst,dst
KUBE-NODE-PORT  all  --  anywhere             anywhere             ADDRTYPE match dst-type LOCAL
ACCEPT     all  --  anywhere             anywhere             match-set KUBE-CLUSTER-IP dst,dst
```


**IP Virtual Server 与 IPVS 规则**

IPVS 会创建一个虚拟网络设备 kube-ipvs0 ，所有 Service 的 IP 会加到该设备上，因此经过 iptables 过滤通过的包会发到该设备，然后基于 IPVS 规则进行转发。

```bash
$ ip addr
56: kube-ipvs0: <BROADCAST,NOARP> mtu 1500 qdisc noop state DOWN group default
    link/ether 0e:a5:64:c4:d6:42 brd ff:ff:ff:ff:ff:ff
    inet 10.107.138.117/32 scope global kube-ipvs0
       valid_lft forever preferred_lft forever




$ sudo ipvsadm -Ln
IP Virtual Server version 1.2.1 (size=4096)
Prot LocalAddress:Port Scheduler Flags
  -> RemoteAddress:Port           Forward Weight ActiveConn InActConn
TCP  127.0.0.1:31029 rr
  -> 192.168.135.5:2381           Masq    1      0          0

TCP  10.107.138.117:8080 rr
  -> 192.168.135.16:80            Masq    1      0          0
  -> 192.168.135.17:80            Masq    1      0          0
  -> 192.168.135.18:80            Masq    1      0          0
```



##### 5.4.4 性能对比

下图是 iptables 与 ipvs 模式的性能对比，可以看到在 10000 节点的集群上，ipvs 模式比 iptables 模式性能几乎高一倍。因此在大规模集群中一般都会推荐使用 IPVS 模式。
![在这里插入图片描述](https://img-blog.csdnimg.cn/4db66544269342768b8846edaa00a07f.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)



### 6. Ingress

在 Kubernetes 中部署的服务如果想被外部访问，主要有三种方式：

- NodePort Service：在每个节点上开端口供外部访问。

![在这里插入图片描述](https://img-blog.csdnimg.cn/51e11a6320ab4a5f8816200cb61922a7.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

- LoadBanlacer Service：使用云厂商提供的负载均衡对外提供服务。
![在这里插入图片描述](https://img-blog.csdnimg.cn/9c12e87d3adc47d3bee0db6c46a90b3d.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)


- Ingress：Kubernetes 提供的七层流量转发方案。

![在这里插入图片描述](https://img-blog.csdnimg.cn/0489f8e4db9a4383a17af5dcb279a9c1.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)


图片来自 https://matthewpalmer.net/kubernetes-app-developer/articles/kubernetes-ingress-guide-nginx-example.html


Ingress 就是“服务的服务”，本质上就是对反向代理的抽象。Ingress 的使用需要 Ingress Controller，可以安装标准的 [Nginx Ingress Controller](https://kubernetes.github.io/ingress-nginx/deploy/)，相当于在 Kubernetes 里装一个 Nginx 为业务服务做反向代理负。

Ingress 示例如下：

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ingress-wildcard-host
spec:
  rules:
  - host: "foo.bar.com"
    http:
      paths:
      - pathType: Prefix
        path: "/bar"
        backend:
          service:
            name: service1
            port:
              number: 80
  - host: "*.foo.com"
    http:
      paths:
      - pathType: Prefix
        path: "/foo"
        backend:
          service:
            name: service2
            port:
              number: 80
```

- Host: 配置的 host 信息，如果为空表示可以接收任何请求流量。如果设置，比如上面规则设置了 "foo.bar.com" 则 Ingress 规则只作用于请求 "foo.bar.com" 的请求。
- pathType: 必填字段，表示路径匹配的类型。
	- Exact：精确匹配 URL 路径，且区分大小写。
	- Prefix：基于以 / 分隔的 URL 路径前缀匹配。匹配区分大小写，并且对路径中的元素逐个完成。 路径元素指的是由 / 分隔符分隔的路径中的标签列表。 如果每个 p 都是请求路径 p 的元素前缀，则请求与路径 p 匹配。

![在这里插入图片描述](https://img-blog.csdnimg.cn/60db0e92ecae4b8e924d4f231813444a.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)



Ingress 创建后相当于在 Nginx Ingress Controller 里面创建配置，和我们日常使用 Nginx 添加配置没有区别。

	
```bash
## start server *.foo.com
	server {
		server_name *.foo.com;

		listen 80;

		set $proxy_upstream_name "-";

		listen 443  ssl http2;

		# PEM sha: 2d165d45c7f24c8a4df64a740666f02378fc8828
		ssl_certificate                         /etc/ingress-controller/ssl/default-fake-certificate.pem;
		ssl_certificate_key                     /etc/ingress-controller/ssl/default-fake-certificate.pem;
                      location ~* "^/foo" {
```


#### 6.1 fanout

所谓 fanout 指的是在同一个域名下，基于 HTTP URL 匹配将请求发送给不同的服务。

![在这里插入图片描述](https://img-blog.csdnimg.cn/2d6592da4a2e4598abf085995f957552.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)


```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: simple-fanout-example
spec:
  rules:
  - host: foo.bar.com
    http:
      paths:
      - path: /foo
        pathType: Prefix
        backend:
          service:
            name: service1
            port:
              number: 4200
      - path: /bar
        pathType: Prefix
        backend:
          service:
            name: service2
            port:
              number: 8080
```


#### 6.2 常用注解

**rewrite-target**

用来重定向 URL

```yaml
apiVersion: networking.k8s.io/v1beta1
kind: Ingress
metadata:
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /$2
  name: rewrite
  namespace: default
spec:
  rules:
  - host: rewrite.bar.com
    http:
      paths:
      - backend:
          serviceName: http-svc
          servicePort: 80
        path: /something(/|$)(.*)
```

 
在此入口定义中，需要在 path 用正则表达式匹配字符，匹配的值会赋值给 $1、$2…$n 等占位符。在上面的表达式中 (.*) 捕获的任何字符都将分配给占位符$2，然后将其用作 rewrite-target 注释中的参数来修改 URL。

例如，上面的入口定义将导致以下重写：
- rewrite.bar.com/something 重写为 rewrite.bar.com/
- rewrite.bar.com/something/ 重写为 rewrite.bar.com/
- rewrite.bar.com/something/new 重写为 rewrite.bar.com/new

**App Root**

重定向时指定对于在 “/” 路径下的请求重定向其根路径为注解的值。示例如下，注解值为 /app1
则请求 URL 由原来的的 / 重定向为了 /app1。

```yaml
apiVersion: networking.k8s.io/v1beta1
kind: Ingress
metadata:
  annotations:
    nginx.ingress.kubernetes.io/app-root: /app1
  name: approot
  namespace: default
spec:
  rules:
  - host: approot.bar.com
    http:
      paths:
      - backend:
          serviceName: http-svc
          servicePort: 80
        path: /
```

检查

```yaml
$ curl -I -k http://approot.bar.com/
HTTP/1.1 302 Moved Temporarily
Server: nginx/1.11.10
Date: Mon, 13 Mar 2017 14:57:15 GMT
Content-Type: text/html
Content-Length: 162
Location: http://stickyingress.example.com/app1
Connection: keep-alive
```


**上传文件限制**

外部通过 Nginx 上传文件时会有上传大小限制，在 Nginx Ingress Controller 中该限制默认是 8M，由 proxy-body-size 注解控制：

```bash
nginx.ingress.kubernetes.io/proxy-body-size: 8m
```


可以在创建 Ingress 时设置

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: 80m
  name: gateway
  namespace: default

spec:
  rules:
...
```



#### 6.4 启用TLS

部署好 NGINX Ingress Controller 后，它会在 Kubernetes 中开启 NodePort 类型的服务，

```bash
$ kubectl get svc -n ingress-nginx
NAME                                 TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)                      AGE
ingress-nginx-controller             NodePort    10.107.127.108   <none>        80:32556/TCP,443:30692/TCP   25d
ingress-nginx-controller-admission   ClusterIP   10.111.3.4       <none>        443/TCP                      25d
```

当我们从外部访问该端口时请求就会根据 Ingress 规则转发到对应的服务中。为了数据安全，外部到服务的请求基本上都需要进行 HTTPS 加密，和我们在没用 Kubernetes 时需要在主机上配置 Nginx 的 HTTPS 一样，我们也需要让我们的 Ingress 支持 HTTPS 。


以自签名证书为例，配置 Ingress 支持 HTTPS 分三步：

**生成证书**

```bash
$ openssl req -x509 -sha256 -nodes -days 365 -newkey rsa:2048 \
      -keyout tls.key -out tls.crt -subj "/CN=foo.bar.com/O=httpsvc"
```


**创建 TLS 类型的 secret** 

```bash
$ kubectl create secret tls tls-secret --key tls.key --cert tls.crt
secret "tls-secret" created
```


**创建 ingress 并设置 TLS** 

```yaml
apiVersion: networking.k8s.io/v1beta1
kind: Ingress
metadata:
  name: nginx-test
spec:
  tls:
    - hosts:
      - foo.bar.com
      # This assumes tls-secret exists and the SSL
      # certificate contains a CN for foo.bar.com
      secretName: tls-secret
  rules:
    - host: foo.bar.com
      http:
        paths:
        - path: /
          backend:
            # This assumes http-svc exists and routes to healthy endpoints
            serviceName: httpsvc
            servicePort: 80
```



这样在请求 foo.bar.com 时就可以使用 HTTPS 请求了。


```bash
$ kubectl get ing ingress-tls
NAME          CLASS    HOSTS         ADDRESS        PORTS     AGE
ingress-tls   <none>   foo.bar.com   192.168.64.7   80, 443   38s


$ kubectl get ing ingress-tls | grep -v NAME | awk '{print $4, $3}'
192.168.64.7 foo.bar.com
```

编辑 /etc/hosts ，加入 192.168.64.7 foo.bar.com

```bash
$ curl -k https://foo.bar.com 
<!DOCTYPE html>
<html>
<head>
<title>Welcome to nginx!</title>
<style>
    body {
        width: 35em;
        margin: 0 auto;
        font-family: Tahoma, Verdana, Arial, sans-serif;
    }
</style>
</head>
<body>
<h1>Welcome to nginx!</h1>
<p>If you see this page, the nginx web server is successfully installed and
working. Further configuration is required.</p>

<p>For online documentation and support please refer to
<a href="http://nginx.org/">nginx.org</a>.<br/>
Commercial support is available at
<a href="http://nginx.com/">nginx.com</a>.</p>

<p><em>Thank you for using nginx.</em></p>
</body>
</html>
```



### 7. DNS for Service 



Kubernetes 中 Service 的虚拟 IP、Port 也是会发生变化的，我们不能使用这种变化的 IP 与端口作为访问入口。K8S 内部提供了 DNS 服务，为 Service 提供域名，只要 Service 名不变，其域名就不会变。

Kubernetes 目前使用 CoreDNS 来实现 DNS 功能，其包含一个内存态的 DNS，其本质也是一个 控制器。CoreDNS 监听 Service、Endpoints 的变化并配置更新 DNS 记录，Pod 在解析域名时会从 CoreDNS 中查询到 IP 地址。
![在这里插入图片描述](https://img-blog.csdnimg.cn/367b4bdcd05947f3bd90535744efb7c9.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)



#### 7.1 普通 Service

对于 ClusterIP / NodePort / LoadBalancer 类型的 Service，Kuberetes 会创建 FQDN 格式为  `$svcname.$namespace.svc.$clusterdomain` 的 A/AAAA（域名到 IP） 记录和 PRT（IP到域名） 记录。

```bash
$ kubectl get svc kubia
NAME    TYPE        CLUSTER-IP     EXTERNAL-IP   PORT(S)   AGE
kubia   ClusterIP   10.99.166.60   <none>        80/TCP    47h


$ kubectl get pods
NAME                      READY   STATUS    RESTARTS   AGE
kubia-5bb46d6998-jjlgn    1/1     Running   0          2m25s
kubia-5bb46d6998-jtpc9    1/1     Running   0          14h
kubia-5bb46d6998-mnlvj    1/1     Running   0          2m25s

~  nslookup kubia.default
Server:		10.96.0.10
Address:	10.96.0.10#53

Name:	kubia.default.svc.cluster.local
Address: 10.99.166.60

 ~  nslookup 10.99.166.60
60.166.99.10.in-addr.arpa	name = kubia.default.svc.cluster.local.
```





#### 7.2 Headless Service

对于无头服务，没有 ClusterIP，Kubernetes 会对 Service 创建 A 记录，但返回的所有 Pod 的 IP。

```bash
~  nslookup kubia-headless.default
Server:		10.96.0.10
Address:	10.96.0.10#53

Name:	kubia-headless.default.svc.cluster.local
Address: 10.44.0.6
Name:	kubia-headless.default.svc.cluster.local
Address: 10.44.0.5
Name:	kubia-headless.default.svc.cluster.local
Address: 10.44.0.3
```


#### 7.3 Pod 

对于 Pod 会创建基于地址的 DNS 记录，格式为 `pod-ip.svc-name.namespace-name.svc.cluster.local`

```bash
$ kubectl get pods -o wide
NAME                      READY   STATUS        RESTARTS   AGE     IP           NODE            NOMINATED NODE   READINESS GATES

kubia-5bb46d6998-jtpc9    1/1     Running       0          28h     10.44.0.3


bash-5.1# nslookup 10.44.0.3
3.0.44.10.in-addr.arpa	name = 10-44-0-3.kubia-headless.default.svc.cluster.local.
3.0.44.10.in-addr.arpa	name = 10-44-0-3.kubia.default.svc.cluster.local.
```




















