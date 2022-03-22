CNCF CKA/CKAD 认证笔记

学习目标

熟悉操作 Kubernetes 的常用命令，提高日常操作效率
熟悉 Kubernetes 常用 Resource 对象的特性以及搭建典型架构
对 Kubernetes 设计思想、底层原理、实现机制有一定的理解，为后续的深入学习打基础
Docker 基础知识
Docker 容器在操作系统层面对应用的运行环境实现了完整的虚拟化，使得每个容器都是一个单独的系统环境。为了实现虚拟化，Docker 容器需要对环境和资源使用进行隔离，隔离的内容主要有三个方面：

对文件系统的隔离
对系统环境的隔离，比如进程、用户、网络、系统分时等
对资源使用的隔离

对于上述内容的隔离是通过 Linux Namespace 和 cGroup 等基技术实现的。
Linux Namespaces

Linux Namespace 是 Linux 提供的一种内核级别的环境隔离技术，我们可以将一个 Namespaces 视为一个黑盒，盒子里面是各种独立的系统资源，比如网络、文件系统、UID/GID 等。

截止到 Linux Kernel 5.6，Linux 一共有 8 种 Namespace，如下：

分类
隔离内容
系统调用参数
内核版本
Mount
隔离文件系统
CLONE_NEWNS
Linux 2.4.19
UTS
隔离主机的 Hostname, Domain name
CLONE_NEWUTS
Linux 2.6.19
IPC
隔离进行间通信的渠道
CLONE_NEWIPC
Linux 2.6.19
PID
隔离进程 PID，进程无法看到命名空间外的进程
CLONE_NEWPID
Linux 2.6.24
Network
隔离网络，比如网卡、网络栈、IP 地址、端口等
CLONE_NEWNET
Linux 2.6.29
User
隔离用户和用户组
CLONE_NEWUSER
Linux 3.8
cGroup
隔离 cGroups 信息
CLONE_NEWCGROUP
Linux 4.6
Time
隔离系统时间
CLONE_NEWTIME
Linux 5.6



Linux Namespace 的使用方式也非常简单：在 Linux 创建新进程时提供一系列可选参数，通过传递上图中对应的参数达到对应资源的隔离。

下面是一个启动 PID 隔离的示例，启动后进程的 PID 为 1。当然，其在主机真实的进程空间中，这个进程的 PID 还是真实的数值，比如 120。

int main()
{
    printf("Parent [%5d] - start a container!\n", getpid());
    /*启用PID namespace - CLONE_NEWPID*/
    int container_pid = clone(container_main, container_stack+STACK_SIZE, 
            CLONE_NEWUTS | CLONE_NEWPID | SIGCHLD, NULL); 
    waitpid(container_pid, NULL, 0);
    printf("Parent - container stopped!\n");
    return 0;
}




Linux CGroup

Namespace 主要解决环境隔离的问题，除此之外我们还需要对计算机资源的使用进行限制，因为在宿主机层次，通过 namespace 隔离的进程和其他进程没有区别，它们是可以随心所欲的使用计算机资源的，但对于容器这样的一个沙盒来说，这显然是不合理的。 Linux 内核提供了 Linux CGroup （Linux Control Group）来对为系统中的进程设置资源限制，比如 CPU 时间、内存、网络带宽等。

Linux CGroup 主要提供如下功能：

Resource limitation: 限制资源使用上限，包括 CPU、内存、磁盘、带宽等
Prioritization: 优先级控制
Accounting: 审计
Control: 挂起与恢复进程

Docker 容器所使用的的主要是其资源限制的能力。

AUFS 与容器镜像

Dockerfile

Dockerfie是用来生成镜像的，描述了如何生成Docker 镜像的具体前置依赖，步骤，和命令。下面是一个 Dockerfile 的示例：

FROM node:12-alpine
RUN apk add --no-cache python g++ make
WORKDIR /app
COPY . .
RUN yarn install --production
CMD ["node", "src/index.js"]


四个重要指令：
FROM
COPY
RUN - 用来运行build时的命令的
CMD - 用来容器启动时运行的。

其它指令
ADD - 解压缩 （注意与COPY的差别）
EXPOSE
ENV
ENTRYPOINT 注意与CMD的不同（CMD通过ENTRYPOINT来运行）
EXPOSE 相关的网络访问端口

Dockerfile Best Practice

在 Docker 17.05 以上版本中，尽可能使用 Multi-stage 技术进行构建。多个 FROM 指令并不是为了生成多根的层关系，最后生成的镜像，仍以最后一条 FROM 为准，之前的 FROM 会被抛弃。虽然最后生成的镜像只能是最后一个阶段的结果，但是，能够将前置阶段中的文件拷贝到后边的阶段中。这样可以减少所构建镜像的大小以及文件层数。


FROM maven:3.6-jdk-8-alpine
WORKDIR /app
COPY ./src
RUN mvn -e -B package

FROM openjdk:8-jre-alpine
COPY --from=builder /app/target/app.jar /
CMD ["java", "-jar", "/app.jar"]

 
避免安装不需要的包。为了减少复杂性，依赖，文件大小，和构建时间，应该避免仅仅因为他们很好用而安装一些额外或者不必要的包。例如，我们不需要在一个数据库镜像中包含一个文本编辑器。如果使用了 apt-get install 后记得删除相关的缓存，如：apt-get update && aptget -y install ... && rm -rf /var/lib/apt/lists/*。
减少Docker镜像中的文件层数。对于Dockerfile中，RUN、COPY和ADD三个命令会创建新的文件层，所以，可以使用 && 来一次运行多个命令（永远将 RUN apt-get update 和 apt-get install 组合成一条 RUN 声明），使用通配符来一次添加多个文件 。
尽可能的使用特定的基础镜像的Tags，如：使用 FROM openjdk:8 而不是 FROM openjdk:lastest
寻找最小尺寸的基础镜像，比如: openjdk:8 有 624MB，而 openjdk:8-jre-alpine 只有83MB。
虽然 ADD 和 COPY 功能类似，但一般优先使用 COPY。因为它比 ADD 更透明。COPY 只支持简单将本地文件拷贝到容器中，而 ADD 有一些并不明显的功能（比如本地 tar 提取和远程 URL 支持）。因此，ADD 的最佳用例是将本地 tar 文件自动提取到镜像中，例如 ADD rootfs.tar.xz。
推荐 ENTRYPOINT 指令使用一个辅助shell脚本，如：ENTRYPOINT ["/entrypoint.sh"]
使用 hadolint 来检查你写的Dockerfile。
不要在 Dockerfile 中写入任何的私密信息，比如：密码或私钥
Local Repository
镜像仓库用来保存和分发Docker 镜像的服务，最常用的镜像仓库就是 Docker 提供的 DockerHub 仓库，除此之外我们还可以自己搭建本地镜像仓库。本地镜像仓库的目的是：
集中控制镜像的存储具体地址。
掌控自己项目的镜像发布的流水线。
将镜像的保存和分发高度整合到个人/企业的现有的发布流程中

常用的镜像仓库有两个：

Docker 官方提供的 registry:2 
Nexus3 仓库：更强大的资源管理工具，可以作为 Docker 镜像仓库、Maven 私服、Node 私服等使用。

本地使用 registry:2 示例：

通过 registry:2 镜像启动一个叫registry的服务，通过host 5000 端口转发到容器5000 端口

docker run -d -p 5000:5000 --name registry registry:2 


pull 或者build生成要存储的目标镜像

docker pull image_name 
docker build  


指向并推入本地镜像仓库
docker image tag imagename localhost:5000/myfirstimage 
docker push localhost:5000/myfirstimage 


使用本地镜像仓库
docker pull localhost:5000/myfirstimage 


Running Command in Container 

可以使用 docker exec 命令在容器中运行新的命令，一般配合 -i（interactive，保持STDIN 打开），-t （tty 分配一个假终端）参数，结合容器的id和具体要执行的指令一起完成。

对运行中的容器启动一个 bash/sh 交互终端来进行一些基础Linux 命令操作： 
docker exec -it my_container -- sh 
docker exec -it my_container -- /bin/bash 

在 k8s 下，可以使用如下的命令
kubectl exec my-pod -- ps aux
kubectl exec my-pod -- ls /
kubectl exec my-pod -- cat /proc/1/mounts

#交互式
kubectl exec -it my-pod -- /bin/bash

#如果一个pod有多个container
kubectl exec -it my-pod -c main-app -- /bin/bash

注意：使用 “--” 来告诉Shell 后面的参数不做任何解析

Docker Network








Docker Volumes
相关重点
Volume 是Docker 把宿主机目录或远程目录mount到容器内的操作
这样就可以在多个容器中进行共享文件Volume
主要是两个参数，-v 或 --mount。--mount 是更明确和冗长的。最大的区别是该 -v 语法将所有选项合并在一个字段中，而 --mount 语法将它们分开。

docker run -d \
  -it \
  --name devtest \
  --mount type=bind,source="$(pwd)"/target,target=/app \
  nginx:latest

docker run -d \
  -it \
  --name devtest \
  -v "$(pwd)"/target:/app \
  nginx:latest

Bind Propagation 有一些配置，如：Shared, slave, private...这些都是Linux内核的配置参数，可以参看 Linux Kernel : Shared Subtrees


--mount 后接的type参数 能取3种值
--mount type=bind 最原始Docker提供的文件系统映射到容器内的方式，会将指定的宿主系统的文件目录结构原封不动映射到容器内，缺乏灵活性。
--mount type=volume 目前比较流行的使用提供给容器使用和持久化数据的方式，卷会直接由docker管理，相对于 type=bind的mount 方式，它具有跨操作系统（Windows/Linux），可以用Docker cli管理，使用一些云存储来实现远端存储数据的功能等等。
--mount type=tmpfs，如果只需要在Linux 环境下运行Docker，tmpfs的方式能让给container 能够在容器外临时存储一些数据，一旦container 停止，tmpfs映射就会移除，数据不会被持久化。比较适合一些零时性存储敏感数据的场景。


Kubernetes 基础知识
 Kubernetes 集群架构

Kuberetes 本身是由一系列组件组成的容器编排系统，每个组件各司其职从而实现容器的调度、部署以及自动伸缩等功能。


Kubernetes 整体的架构图



Master (Control Plane)节点


集群中的控制节点，为单数数量，运行集群中的控制面板逻辑的相关组件。kube-system namespace 下的资源都会在这一个或者多个Master 节点上运行，且默认会有node-rule.kubernetes.io/master:NoSchedule 的 taint，用来告知调度模块不要将用户定义的 Pod 等资源划分到这些节点上运行，以便维持集群控制逻辑的所需资源稳定。Master 节点上主要运行的组件有 4 个：

etcd
api-server
controller manager
scheduler


Worker节点

实际运行用户应用，部署业务的 Pod 的节点。如架构图所示，每个worker节点都会有 kubelet，kube-proxy 和 container runtime。

Master 节点组件

etcd

强一致性，高可用，采用 RAFT一致性协议的 key-value 数据库，Kubernetes 用来保存配置，Kuberntes object 等所有集群相关的数据，是 Kubernetes 唯一存储数据的地方。

etcd 采用乐观锁机制控制对所存储资源的访问，其保存的所有资源都有一个资源版本，每次编辑时都会更新。如果资源版与保存的修订版不同，kube-apiserver 会利用它来拒绝冲突的编辑请求。

api-server

api-server 是提供 Kubernetes 控制面板的 API  的前端，采用 RESTful 协议，支持无状态水平伸缩。api-server 是唯一可以与 etcd 通信的组件，其提供了一系列的 CRUD 接口供外部访问以操作数据，它会校验请求中 Pods，Services，ReplicaSet 等object的数据的合法性，同时还会进行身份认证、鉴权、准入控制等操作。

controller-manager 

运行一系列的控制器进程，每个控制器都是一个单独的进程，执行基于控制循环的状态拟合，保证集群的运行状态与我们的期望状态保持一致。

一些常见的控制器

Deployment Controller
ReplicaSet Controller
DaemonSet
Job Controller

scheduler

隶属于Kubernetes控制面板中的重要成员之一，负责监听集群中还未分配到所属节点的 Pod，其根据一系列调度规则，将 Pod 调度到合适的节点运行。

Worker 节点组件

container-runtime

用于负责运行容器的软件，目前Kubernetes 支持：Docker，containerd，CRI-O 等支持Kubernetes CRI(Container runtime interface) 的所有软件。

CRI（容器运行时接口）于Kubernetes 1.5版引用，是一种插件行的接口，这种接口使得kubelet可以运行各种容器而不需要重新编程，是一种容器标准协议。也就是说，除了Docker外，还可以用其它公司的容器，比如Google自己的rkt容器。

kubelet 会通过 grpc 和 CRI 联系，下面这个图可以清楚看到相关的关系。


目前来说，有两个主流的CRI插件： Redhat的 CRI-O 和 Docker 的 containerd

CRI-O 是Kubernetes CRI 的实现，可以使用兼容OCI（Open Container Initiative）的运行时。它是使用Docker作为kubernetes的运行时的轻量级替代方案。今天，它支持runc和Kata Containers作为容器运行时，但是原则上可以plug in 任何符合OCI的容器。
Docker 家的 CRI 是 containerd，也是默认和最流行的CRI插件。

参考文档：
How to switch container runtime in kubernetes-cluster


Kubelet

Kubelet 是一个运行在每个节点上的 agent，用来负责监控容器符合预期地在 Pod 中运行。kubelet会通过各种方式获取到 Pod 的 Spec，以此为依据来负责监控在Kubernetes中，以PodSpec描述创建的容器的运行和健康状态。


kube-proxy
‘
Kube-proxy 一样运行在每节点上，它的角色是一个网络代理，管理在节点上的网络规则，用来允许集群中的 Pod 进行集群内外的网络通讯。一般情况 kube-proxy 会直接使用操作系统提供的网络包过滤层的能力（比如iptable，ipvs），否则它需要自己实际负责流量的分发。

其他附加组件

CNI plugin

为集群中的 Pod 设置 IP 并实现跨集群的网络通信。

Ingress Controller

该组件基于 Ingress 对外部请求做路由，将 K8s 中的服务暴露给外界访问，最常用的一般是
NGINX Ingress Controlle
Kube DNS

K8s 内部的 DNS 服务，用来对 Service、Pod 做  DNS 解析实现集群内部的服务发现，现在默认使用的是 CoreDNS。

Kubernetes 资源对象

Kubernetes 将一切视为资源，在 Kubernetes 系统中，Kubernetes 对象是持久化的实体，其本质是对分布式系统所需要的各种功能的抽象，其表现形式就是 yaml 文件。Kubernetes 基本上就是由 kubectl 命令操作的 yaml 描述文件，下面是一个 yaml 文件示例：

apiVersion: apps/v1 # for versions before 1.9.0 use apps/v1beta2
kind: Deployment
metadata:
  name: nginx-deployment
spec:
  selector:
    matchLabels:
      app: nginx
  replicas: 2 # tells deployment to run 2 pods matching the template
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:1.14.2
        ports:
        - containerPort: 80


一个完整的资源对象的 yaml 文件由四部分组成：
apiVersion & kind：创建该对象所使用的 Kubernetes API 的版本和对象的类别。
metadata：元信息，帮助唯一性标识对象的一些数据，包括一个 name 字符串、UID 和可选的 namespace。
spec：对象规格，不同的对象有不同的 spec，用于定义其相关属性，创建对象时设置其内容，描述我们希望对象所具有的特征，也就是 「期望状态」。
status:  对象的「当前状态」，由 Kubernetes 系统组件设置并更新，在创建对象时我们只需要定义上述三部分内容即可。在任何时刻，Kubernetes Control Pannel 都一直积极地管理着对象的实际状态，以使之与期望状态相匹配。
使用 kubectl get objectType objectName -o yaml 可以在命令行上查看资源对象完整的 yaml。比如：
$ kubectl get pod nginx -o yaml

 
下图是常用的一些 Kubernetes 对象，所有的对象类型可以用下面命令查看：
$ kubectl api-resources





Pod

为什么需要 Pod

Pods 是 K8s 中最小的可部署和管理单元，是一个逻辑概念。一个 Pod 包含一个或多个 container，它们可以共享网络和存储，可以把 Pod 看作是一个虚拟的逻辑主机，里面包含了一个或多个紧密关联的 container，Pod 同时也告知系统如何去运行它所描述的容器。

Pod 的存在主要是为了解决两个问题：

对“进程组”的抽象，满足容器共享 namespace 的需求。
协同调度

容器本身是单进程模型，其应用本身的 PID 为 1，本身没有管理多个进程的能力。在实际应用中，往往存在着需要「超亲密关系」的进程，它们必须要运行在同一机器上共享存储、网络等，而 Pod 其实就是一组共享了网络、存储、IPC、UTS 以及时间的的容器，它们只有 PID 和文件 namespace 是默认隔离的，可以满足这种超亲密关系的要求。

与此同时，对于这类超亲密关系的进程，在跨机器的集群中，它们必须被部署到同一台机器上。如果以容器为调度单位，对资源的要求就只能在容器上设置，此时当多个超亲密容器需要协同调度时，资源越紧张，容器被调度到不同机器的可能性越大。

如果以 Pod 为原子单位进行调度，则对资源的设置可以定义在 Pod 上，此时只需要以 Pod 为单位统一调度即可，不需要在考虑单个容器的情况。


Pod 可以由用户直接创建，也可以通过 Controller 对象创建。一般来说，Pod 极少直接被单独创建，一般会由用户使用 Deployment，Job，StatefulSet 等实际工作资源，基于其 PodTemplate 创建。另外也可以由 kubelet 直接创建并管理，这类 Pod 称为 StaticPod，像 etcd、api-server 这些 master 节点组件都是 StaticPod。




apiVersion: v1
kind: Pod
metadata:
  creationTimestamp: null
  labels:
    run: nginx
  name: nginx
spec:
  containers:
  - image: nginx
    name: nginx
  restartPolicy: Always
status: {}





当Pod完成它的工作后，会被删除，同时也有可能会因为缺乏资源，Node节点的失败而被驱逐。
给定的Pod（由UID定义）永远不会“重新安排”到其他节点，如果 Pod 失败退出，K8s 会创建一个新的 Pod 来替换。

Pod 状态

PodStatus

Pod的状态是一个 PodStatus 对象，在 Pod 定义中是 status 字段， 其中包含一个pod.status.phase 字段表示当前的状态，可以取如下的值

Pending
表示 Pod 对象已被创建保存在 etcd 中，但有一个或多个容器还不能被顺利创建。当等待调度或者拉取镜像时一般处在该状态。
Running
Pod 已经调度到某一节点并且所有容器都已经创建成功，并且至少有一个容器正在运行或者正在启动/重启。
Succeed
所有容易都正常运行并退出，并且不会重启。一般在运行一次性任务时最为常见。
Failed
所有容器已终止，并且至少有一个以不正常状态退出。此时我们一般需要通过查看 Pod 信息或日志来调试问题。
Unknown
异常状态，Pod 状态无法被 kube-apiserver 获取到。一般是 Pod 节点与 api-server 通信出现异常导致的。



Pod Conditions

除了 status.phase 字段表示状态之外，Pod 还有一组 PodConditions 对象，对应字段是 pod.status.conditions 数组字段来描述 Pod 处于某个 phase 的具体原因，主要有下面几个值：

PodScheduled：Pod 已经被调度到指定节点。
ContainersReady：Pod内所有的容器已经就绪。
Initialized：所有的 init containers 已经成功启动。
Ready：Pod 可以对外提供服务，可以作为 endpoints 给 Service 代理。

PodConditions 对象包含如下的字段

type
类型名称， 上面四个取值之一
status
True， False， Unkonwn 指示这个状况是否就绪
lastProbeTime
最后一个探测状况的时间戳
lastTransitionTime
Pod上一次状况变换的时间戳
reason
Machine-readable，UpperCamelCase
状况最后一次变化的原因
message
Human-readable message


Container States 

Pod 中容器也有状态，主要有下面三个值

Waiting
等待状态。表示容器仍在执行启动所需的操作，比如拉取镜像、注入数据等。
Running
运行状态。表示容器正常运行，如果容器配置了 postStart 命令，该命令已经执行并结束了。
Terminated
终止状态。容器完成任务或者执行失败，如果容器配置了 preStop 命令，必须在该命令执行完成后进行该状态，换句话说，preStop 会阻塞 container 的终止。


Container restart policy

容器的重启策略，有三个取值， 
Always（默认值）：只要不在运行状态就自动重启
OnFailure：在容器异常时自动重启
Never：从不重启


Pod 状态与容器状态的对应关系简要总结如下：

如果 Pod 的 restartPolicy 指定允许容器重启，当有容器异常时， Pod 会一直保持 Running 状态并重启容器，否则Pod 会进入 Faied 状态。
对于多容器的 Pod，只有所有容器都进入异常状态后，Pod 才会进入 Failed，在此之前一直是 Running 状态。查看 Pod 的 Ready 字段会显示正常如果您器个数。

Lifecycle

上面提到了容器可以配置 postStart 和 preStop 命令。这是容器在发生状态变化时可以触发的一系列 hook操作：

postStart：在 Docker 容器启动后立即执行，即 ENTRYPOINT 执行之后执行，但并不保证顺序，postStart 执行时 ENTRYPOINT 命令可能尚未结束。postStart 会阻塞容器状态变化，在 postStart 执行完成前，容器的状态不会设置为 Running。
preStop：在容器被杀死进入 Terminated 状态前执行，会阻塞容器杀死进程。Kubernetes 仅在 Pod 终止时执行 preStop，在完成时不会调用，相关 issues。

命令的操作操作方式有两种：
exec：执行一段命令
http: 执行一个请求

apiVersion: v1
kind: Pod
metadata:
  name: lifecycle-demo
spec:
  containers:
  - name: lifecycle-demo-container
    image: nginx
    lifecycle:
      postStart:
        exec:
          command: ["/bin/sh", "-c", "echo Hello from the postStart handler > /usr/share/message"]
      preStop:
        exec:
          command: ["/usr/sbin/nginx","-s","quit"]





图片来自：Golides


 Pod Probe

探针是由 kubelet 发起的一种容器诊断行为，可以用来检测 Pod 中的容器的健康情况。Kubernetes 中主要有三种探针：

livenessProbe：存活探针，检查容器的健康状态，以决定是否重启容器。
readinessProbe：就绪探针，用来检测容器是否准备好接收外部流量。该探针执行成功后 Pod 的细分状态就会变为 Ready，此时 Pod 可以作为 endpoints 给 Service 进行代理。
startupProbe：启动探针，来判断容器是否启动，该类型探针会屏蔽上述两种探针。可以避免因为容器启动过长导致的死循环问题。如果失败则会重启容器。

上述三类探针，如果没有提供， 默认对应的状态是 Succeess。

kubelet 通过调用 handler 实现对容器的诊断。 有三种类型的Handler


ExecAction
执行一条用户提供的命令（例如“ls” 等）
TCPSocketAction
用TCP 协议探测指定端口是否能通
HttpGetAction
用HTTP GET 请求探测指定URL 是否正常返回200



apiVersion: v1
kind: Pod
metadata:
  name: goproxy
  labels:
    app: goproxy
spec:
  containers:
  - name: goproxy
    image: k8s.gcr.io/goproxy:0.1
    ports:
    - containerPort: 8080
    readinessProbe:
      tcpSocket:
        port: 8080
      initialDelaySeconds: 5
      periodSeconds: 10
    livenessProbe:
      tcpSocket:
        port: 8080
      initialDelaySeconds: 15
      periodSeconds: 20




什么时候启用livenessProbe：

如果容器中的进程在出现问题时可以自行正常崩溃，此时是不需要存活探针的，kubelet 会探测到容器的状态并基于 restartPolicy 执行相应的操作。

但有时候容器进程虽然没有挂掉，但其实已经无法提供服务了，比如遇到死锁，从进程检查的角度它是没问题的。我们可能需要通过访问业务逻辑来判断容器进程是否安全，此时可以使用 livenessProbe。

什么时候启用 readinessProbe

就绪探针决定的是容器是否已经启动就绪并可以接收外部的流量，因此如果某个需要接收外部流量的的容器进程启动较慢，可以设置 readinessProbe 来确认容器是否已经就绪。比如对于 Java/Spring 应用，其启动往往较慢，此时为其设置就绪探针是一个不错的选项。

什么时候启用 startupProbe

针对容器进程启动较慢的情况，可以设置启动指针。避免因为长时间未启动引发 livenessProbe 探测失败导致重启，陷入不断重启的“死循环”中。


Init container

Init Container 是一种特殊容器，在 Pod 内的应用容器启动之前运行，一般用来执行初始化操作，比如安装应用镜像中不存在的实用工具和脚本。每个 Pod 中可以包含多个应用容器，应用运行在这些容器里面，同时 Pod 也可以有一个或多个先于应用容器启动的 Init Container。

initContainer 容器有两个特点：
按顺序执行
它们总是运行到完成，每个 Init Container 必须在下一个容器启动前成功完成。


apiVersion: v1
kind: Pod
metadata:
 name: www
 labels:
   app: www
spec:
 initContainers:
 - name: download
   image: execlb/git
   command:
   - git
   - clone
   - https://github.com/mdn/beginner-html-site-scripted
   - /var/lib/data
   volumeMounts:
   - mountPath: /var/lib/data
     name: source
 containers:
 - name: run
   image: docker.io/centos/httpd
   ports:
   - containerPort: 80
   # Shared volume with main container
   volumeMounts:
   - mountPath: /var/www/html
     name: source
 volumes:
 - emptyDir: {}
   name: source


注意事项

Init Container 不支持 lifecycle、livenessProbe、readinessProbe 和 startupProbe， 因为它们必须在 Pod 就绪之前运行完成。
只有所有 Init Container 都执行成功， Pod才能切换到Ready condition；只要任意一个失败就会触发 Pod 的 restartPolicy。
Pod 重新启动时，所有的 Init Container 会重新执行一遍，因此 Init Container 的执行结果应该是幂等的。
Init Container 执行时 Pod 的condition是 Initialized，status 为true, 但是Phase 是Pending；另外 readniessProbe 不能应用于 Init Container，Init Container 并不能代表Pod 就绪。
在 Container上设置 livenessProbe 和在Pod上 activeDeadlineSeconds可以避免 init container 死循环失败，activeDeadlineSeconds 将会作用于所有的Init 容器。
Init Container 的 image 更新将会重启整个Pod， 应用容器的镜像更新只重启应用容器。

资源计算

Init Container 中可以设置对 CPU、内存资源的 request/limit ，因此也会影响 Pod 的资源计划。Pod 对资源的有效 request/limit 取决于下面两者中的最大值：

所有应用容器的 request/limit 之和
Init containers 中的 request/limit  最大值


尽量使 Init Container 的 request/limit 小于应用容器的 request/limit。因为 Pod 调度是基于有效 request/limit 资源的， Init Container 原因申请过多资源，但在 Pod 生命周中实际用不了这么多资源，从而造成资源浪费。


PodPreset
PodPreset 用来为 Pod 设置预设值，可以自动为对应的 Pod 添加其他必要的信息，比如 labels、annotations、volumes 等。

启动该功能需要两步：
启用 settings.k8s.io/v1alpha1/podpreset API：在 api-server 启动时的 --runtime-config 中添加 ettings.k8s.io/v1alpha1=true。
启用准入控制器：在 --enable-admission-plugins 参数找那个添加 PodPreset。

apiVersion: settings.k8s.io/v1alpha1
kind: PodPreset
metadata:
  name: allow-database
spec:
  selector:
    matchLabels:
      role: frontend
  env:
    - name: DB_PORT
      value: "6379"
  volumeMounts:
    - mountPath: /cache
      name: cache-volume
  volumes:
    - name: cache-volume
      emptyDir: {}



PodPreset  工作流程
当开启 PodPreset 时，Kubernetes 会提供一个名为 PodPreset 的准入控制器（ Admission controller ）, 这个控制器将在Pod 创建请求到来时应用预设功能。当Pod创建发生时， 系统执行如下的步骤：

获取可用的预设值
检查Pod的标签是否和预设值的标签匹配。
在Pod创建的时候，尝试合并在 Pod 预设值中定义的各种资源。
如果合并失败， 则记录Pod的合并资源失败事件，并创建没有注入预设值的 Pod 资源。
被 PodPreset 改动过的 Pod 会带有 annotation：podpreset.admission.kubernetes.io/podpreset-<pod-preset name>: "<resource version> 的形式

每个 Pod 可以匹配 0 或者多条预设值， 每个预设值可以和 0 到多个 Pod 相匹配。当一个 PodPreset 应用到一个或多个Pod时， K8s将会更改Pod的Spec：

对于env， envFrom 和 volumeMounts， K8s会修改Pod内的所有Containe的Container Spec。
对于volume， K8s修改Pod的Spec。

如果希望将某些 Pod 禁用 PodPreset，可以在 Pod 中添加 podpreset.admission.kubernetes.io/exclude: "true" 注解。


Disruption

PDB (Pod disruption budgets) 是指应该应用程序它所期望容忍的最小副本数。 例如，如果一个Deployment 有 .spec.replicas:5 表示在任何时间他将有5个副本。 如果PDB 允许在某个时间为4个服务，那么由Eviction API引发的主动删除Pod副本的将会只会同一时刻删除一个副本（确保总有4个副本在运行）。

PDB 不能阻止非主动宕机（involuntary disruption) 的发生。

Pod “主动”（intended）副本数量是由管理POD的 workload 资源 （deployment，statefulset）的。.spec.replicas 中计算得出。 控制面通过检查 pod的.metadata.ownerReferences 来确定 Pod所属的 workload 资源

在进行滚动更新的Pod将会参考PDB， workload 资源在执行滚动更新时缺不受PDB的限制，而是在应用升级时，由workload资源指定在Spec中的配置的失败处理 （？？？）

使用驱逐API（Evication API）逐出容器后，该容器会被优雅终止，并遵循其PodSpec中的terminationGracePeriodSeconds设置

apiVersion: policy/v1beta1
kind: PodDisruptionBudget
metadata:
  name: zk-pdb
spec:
  selector:
    matchLabels:
      app: zk
  maxUnavailable: 1



临时容器（Ephemeral Containers）



ephemeral containers 主要是用于帮助用户来完成特定目的的比如诊断功能的容器。它临时地存在在一个容器内部。

当由于容器崩溃或容器镜像不包含调试工具而导致 kubectl exec 无用时， 临时容器对于交互式故障排查很有用。

尤其是，distroless 镜像 允许用户部署最小的容器镜像，从而减少攻击面并减少故障和漏洞的暴露。 由于 distroless 镜像不包含 Shell 或任何的调试工具，因此很难单独使用 kubectl exec 命令进行故障排查。

使用临时容器时，启用进程名字空间共享 很有帮助，可以查看其他容器中的进程。

ephemeral container 不会自动重启，所以不适合来做服务。他通过ContainerSpec 进行描述和普通容器描述相同，但是有如下的限制。
临时容器没有端口配置，因此很多字段没有用， 如ports， livenessProbe，readinessProbe
Pod的资源是不变了， 所以 resource 也是不能被指定的。
完整的字段列表，参看  EphemeralContainer reference documentation.

ephemeral container 不能直接被加入到pod.spec中， 需要通过在API中的特定的 ephemeralcontainers handler 进行处理。 所以用户是无法通过 kubectl edit 进行添加的。
和普通container一样，一旦你添加了 ephemeral container 你就不能将它在POD中移除和修改。

通过 kubectl --raw 命令可以创建一个 ephermeral container
{
    "apiVersion": "v1",
    "kind": "EphemeralContainers",
    "metadata": {
            "name": "example-pod"
    },
    "ephemeralContainers": [{
        "command": [
            "sh"
        ],
        "image": "busybox",
        "imagePullPolicy": "IfNotPresent",
        "name": "debugger",
        "stdin": true,
        "tty": true,
        "terminationMessagePolicy": "File"
    }]
}


使用下面的命令，可以将ephermeral container 加到一个已经在运行的 example-pod 中
kubectl replace --raw /api/v1/namespaces/default/pods/example-pod/ephemeralcontainers  -f ec.json

通过 kubectl describe pod  可以查看 pod中的ephemeral container
...
Ephemeral Containers:
  debugger:
    Container ID:  docker://cf81908f149e7e9213d3c3644eda55c72efaff67652a2685c1146f0ce151e80f
    Image:         busybox
    Image ID:      docker-pullable://busybox@sha256:9f1003c480699be56815db0f8146ad2e22efea85129b5b5983d0e0fb52d9ab70
    Port:          <none>
    Host Port:     <none>
    Command:
      sh
    State:          Running
      Started:      Thu, 29 Aug 2019 06:42:21 +0000
    Ready:          False
    Restart Count:  0
    Environment:    <none>
    Mounts:         <none>
...


可以使用以下命令连接到新的临时容器：

kubectl attach -it example-pod -c debugger


如果是网络问题也可以考虑使用  netshoot 工具：

sudo docker run -it  --net container:${containerID} nicolaka/netshoot

Pod Cmd & Args

Pod 的定义中也可以为 Container 指定启动命令和参数，

指定命令

apiVersion: v1
kind: Pod
metadata:
  name: command-demo
  labels:
    purpose: demonstrate-command
spec:
  containers:
  - name: command-demo-container
    image: debian
    command: ["printenv"]
    args: ["HOSTNAME", "KUBERNETES_PORT"]
    # command: ["/bin/sh"]
    # args: ["-c", "while true; do echo hello; sleep 10;done"]
  restartPolicy: OnFailure




Pod 中定义的 command 和 args 会覆盖掉 Dockerfile 中定义的启动命令，其覆盖关系如下：


K8S command/args VS. Docker Entrypoint/Cmd


 

Pod 的启动与终止

Pod 启动流程

当创建一个 Pod 创建时上述组件工作过程如下：
1. 外部发起创建 Pod 请求，api-server 检查无误后将 Pod  存储到 etcd。如果是常见的 Deplpoyment 等控制器组件，则 controller 会执行状态拟合，创建期望数量的 Pod。
2. Scheduler 调度器发现新的未调度 Pod，基于资源需求、节点亲和、污点容忍等规则，将 Pod 调度到合适的节点。其操作就是修改 Pod 的 Spec，将 nodeName 字段设置为对应的节点名，然后存回 etcd。
3. 对应节点上的 kubelet 检测到有新的 Pod 调度到该节点，执行如下操作：
【1】向容器运行时比如 contained 发送请求，创建 infra 容器。
【2】容器运行时调用 CNI 网络插件初始化 Pod 的网络命名空间。
4. 如果存在 initContainer，kubelet 会请求 CRI 按顺序创建 init 容器。
5. kubelet 并发请求 CRI 创建 spec.containers 下定义的容器。
6. kubelet 监控容器，收集容器数据并上报给 api-server。


图片来自:  Goglides

Pod 终止流程


图片来自：Golides

Pod 容器设计模式

很多时候若干个进程是需要紧密运行在同一台主机上的，比如 Linux 进程组下的进程。在容器编排调度中，对于某些超亲密的容器进程，它们也是必须被紧密的调度运行在同一主机环境中。类似于 Kubernetes 的 Pod、Nomad 中的 task-group 都是对这种超亲密关系的抽象。

Google 的两位技术人员对基于容器的设计模式总结了论文和PPT：

论文：Design patterns for container-based distributed systems
课件：https://www.usenix.org/sites/default/files/conference/protected-files/hotcloud16_slides_burns.pdf



SideCar

边车容器用来扩展和增强主容器的功能。在基于容器的分布式系统中，容器作为打包、重用的基本单位，其设计一般是符合单一职责原则的。此时如果我们需要一些额外的功能，可以通过 SideCar 容器实现。比如下载主容器所需的文件，收集主容器进程运行产生的日志等。




apiVersion: v1
kind: Pod
metadata:
  name: web-app
spec:
  containers:
  - name: app
    image: docker.io/centos/httpd    
    ports:
    - containerPort: 80
    volumeMounts:
    - mountPath: /var/www/html       
      name: git
  - name: poll
    image: axeclbr/git               
    volumeMounts:
    - mountPath: /var/lib/data       
      name: git
    env:
    - name: GIT_REPO
      value: https://github.com/mdn/beginner-html-site-scripted
    command:
    - "sh"
    - "-c"
    - "git clone $(GIT_REPO) . && watch -n 600 git pull"
    workingDir: /var/lib/data
  volumes:
  - emptyDir: {}
    name: git


SideCar 容器的使用有如下几个好处：
节约资源：容器作为资源分配的单位，可以将资源优先配置给主容器，而 sidecar 容器可以配置较少的资源，避免其资源占用过多影响主进程。
职责分离：容器作为打包的单位，主容器和 sidecar 容器是可以分开单独开发并打包的。
方便重用：容器也是重用的单位，sidecar 可以用来辅助不同的主容器。
错误隔离：当某个容器出现问题时，可以单独的进行降级、升级、回滚等，尽量不影响其他容器的运行。

Ambassador

大使模式，是一种特殊的 SideCar，用于代理容器的访问请求。比如主容器需要访问数据库获取资源，对于主容器而言，其看到的始终是与本地通信，所有的通信细节都由大使容器实现。



下面是一个简单的示例，主容器产生的日志会经由 localhost:8080 的路径发送给 Ambassador 容器，然后经由 Ambassador 发送给不同的存储媒介。

apiVersion: v1
kind: Pod
metadata:
  name: random-generator
  labels:
    app: random-generator
spec:
  containers:
  - image: k8spatterns/random-generator:1.0            
    name: main
    env:
    - name: LOG_URL                                    
      value: http://localhost:9009
    ports:
    - containerPort: 8080
      protocol: TCP
  - image: k8spatterns/random-generator-log-ambassador 
    name: ambassador


Adapter

适配器模式是另外一种特殊的 SideCar，和大使模式相反。大使模式是屏蔽的外部的变化，对主容器提供一致对外访问体验。而适配器模式则是屏蔽容器内部的变化，对外提供统一的访问模式。最常见的例子就是监控 API，比如 Prometheus，外界不用关心容器内部是怎样实现的，只需要访问固定的 API 获取指标就可以了。



下面是一个 Adapter 的示例，Adapter用到了一个 nginx/nginx-prometheus-exporter 的一个镜像，该适配器会把 Nginx 的 stub staus 页转成 Prometheus 的 metrics，并放了9113端口和默认的 /mterics 的访问路径。

apiVersion: apps/v1
kind: Deployment
metadata:
  creationTimestamp: null
  labels:
    app: adapter-pattern
  name: adapter-pattern
  namespace: adapter
spec:
  replicas: 1
  selector:
    matchLabels:
      app: adapter-pattern
  strategy: {}
  template:
    metadata:
      creationTimestamp: null
      labels:
        app: adapter-pattern
    spec:
      volumes:
      - name: nginx-default-conf-volume
        configMap:
          name: nginx-default-conf
      containers:
      - name: nginx
        image: nginx:1.19.2
        ports:
        - containerPort: 80
        volumeMounts:
        - mountPath: /etc/nginx/conf.d
          name: nginx-default-conf-volume
          readOnly: true
      - name: adapter
        image: nginx/nginx-prometheus-exporter:0.8.0
        args: ["-nginx.scrape-uri", "http://localhost/nginx_status"]
        ports:
        - containerPort: 9113
      - name : debug
        image: nicolaka/netshoot
        command: ["/bin/bash", "-c"]
        args: ["while true; do sleep 60; done"]




控制器

在 Kubernetes 中，Controller 就是那些观察 cluster 状态并进行状态拟合的控制循环。每 Controller 都是一个单独的进程，试图将当前 cluster 的状态调整为其所期望的状态。

for {
  实际状态 := 获取集群中对象 X 的实际状态（Actual State）
  期望状态 := 获取集群中对象 X 的期望状态（Desired State）
  if 实际状态 == 期望状态{
    什么都不做
  } else {
    执行编排动作，将实际状态调整为期望状态
  }
}



K8s中的 controller 有


Deployments - 一个 Deployment 为 Pods和 ReplicaSets提供描述性的更新方式。描述 Deployment 中的 desired state，并且 Deployment 控制器以受控速率更改实际状态，以达到期望状态。可以定义 Deployments 以创建新的 ReplicaSets ，或删除现有 Deployments ，并通过新的 Deployments 使用其所有资源。这是最常用的。

Statefulset - 和 Deployment相同的是，StatefulSet 管理了基于相同容器定义的一组 Pod。但和 Deployment 不同的是，StatefulSet 为它们的每个 Pod 维护了一个固定的 ID。这些 Pod 是基于相同的声明来创建的，但是不能相互替换：无论怎么调度，每个 Pod 都有一个永久不变的 ID。稳定意味着 Pod 调度或重调度的整个过程是有持久性的。如果应用程序不需要任何稳定的标识符或有序的部署、删除或伸缩，则应该使用由一组无状态的副本控制器提供的工作负载来部署应用程序，一般用于有状态的应用。

DaemonSet , 确保全部（或者某些）节点上运行一个 Pod 的副本。 当有节点加入集群时， 也会为他们新增一个 Pod 。 当有节点从集群移除时，这些 Pod 也会被回收。删除 DaemonSet 将会删除它创建的所有 Pod。一般用于“监控进程”、“日志收集”或“节点管理”。

Jobs - 会创建一个或者多个 Pods，并确保指定数量的 Pods 成功终止。 随着 Pods 成功结束，Job 跟踪记录成功完成的 Pods 个数。 当数量达到指定的成功个数阈值时，任务（即 Job）结束。 删除 Job 的操作会清除所创建的全部 Pods。一种简单的使用场景下，你会创建一个 Job 对象以便以一种可靠的方式运行某 Pod 直到完成。 当第一个 Pod 失败或者被删除（比如因为节点硬件失效或者重启）时，Job 对象会启动一个新的 Pod。


CronJob - Cron Job 创建基于时间调度的 Jobs。一个 CronJob 对象就像 crontab (cron table) 文件中的一行。 它用 Cron 格式进行编写， 并周期性地在给定的调度时间执行 Job。

Garbage Collection
TTL Controller for finished Resources
Volume

(CRD应该也符合控制器的定义， 算作一种控制器)


下面介绍的都是针对 Pod 的 Workload 控制器对象，各种 Workload 控制器对象本质上是对各种y应用任务的抽象。比如我们会有长期执行的业务应用，并且业务应用最好始终保持一定数量的节点来对外提供服务，在升级时不会下线并且可以做到自动扩容；也有单次任务、定时任务等各种类型。

副本控制器

副本控制器确保在任何时候都有特定数量的 Pod 处于运行状态。 换句话说，它可以确保一个 Pod 或一组同类的 Pod 总是可用的。当 Pod 数量过多时，副本控制器会终止多余的 Pod。当应用故障导致 Pod 失败退出时，副本控制器会自动基于 PodTemplate 创建新的 Pod 副本。
最开始的的副本控制器是 ReplicationController ，现已被 ReplicaSet 替代，使用示例如下：

apiVersion: apps/v1
kind: ReplicaSet
metadata:
  name: frontend
  labels:
    app: guestbook
    tier: frontend
spec:
  # modify replicas according to your case
  replicas: 3
  selector:
    matchLabels:
      tier: frontend
  template:
    metadata:
      labels:
        tier: frontend
    spec:
      containers:
      - name: php-redis
        image: gcr.io/google_samples/gb-frontend:v3








一般来说我们基本不会使用直接使用 ReplicaSet 来管理 Pod，而是通过 Deployment 对象管理 。它是一个比 ReplicaSet更高级的概念，它管理 ReplicaSet，并向 Pod 提供声明式的更新以及许多其他有用的功能，比如滚动更新、回滚等。

Deployment

在实际的应用部署中，经常会有水平伸缩、应用升级与回滚等操作，Deployment 正是对这类操作的抽象。





下面是一个 Deployment 的例子：

// 控制部分
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
  labels:
    app: nginx
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx
  // 模板
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:1.14.2
        ports:
        - containerPort: 80



.metadata 元信息，指定 Deployment 的名称、标签。
.spec.replicas 副本数量，基于该字段创建对应数量的 Pod。
.spec.selector 标签选择器， 指定哪些 Pod 由这个Deployment 管理。
.spec.template  Pod 模板，基于该模板创建 Pod， Pod 要指定标签，其值务必和 .spec.selector 选择器保持一致。

上面的yaml 文件 可以使用如下的命令生成：

kubectl create deployment nginx --image=:nginx:1.14.2 \
    --replicas=3 --port=80 --dry-run=client -o yaml > nginx.yaml


基本使用

创建并查看 Deployment


$ kubectl apply -f nginx-deployment.yaml
deployment.apps/nginx-deployment created

$ kubectl create deployment redis --image=redis:alpine --replicas=1
deployment.apps/redis created





查看新创建的 Deployment
$ kubectl get deployments.apps -o wide
NAME               READY   UP-TO-DATE   AVAILABLE   AGE     CONTAINERS   IMAGES       SELECTOR
nginx-deployment   3/3     3            3           5m37s   nginx        nginx:1.14.2   app=nginx


查看 Deployment 的展开状态
$ kubectl rollout status deployment nginx-deployment
Waiting for deployment "nginx-deployment" rollout to finish: 3 of 10 updated replicas are available...
Waiting for deployment "nginx-deployment" rollout to finish: 4 of 10 updated replicas are available...
Waiting for deployment "nginx-deployment" rollout to finish: 5 of 10 updated replicas are available...
Waiting for deployment "nginx-deployment" rollout to finish: 6 of 10 updated replicas are available...
Waiting for deployment "nginx-deployment" rollout to finish: 7 of 10 updated replicas are available...
Waiting for deployment "nginx-deployment" rollout to finish: 8 of 10 updated replicas are available...
Waiting for deployment "nginx-deployment" rollout to finish: 9 of 10 updated replicas are available...
deployment "nginx-deployment" successfully rolled out





Deployment 是通过上面提到的 ReplicaSet 来管理 Pod 的，ReplicaSet 本质上和应用的版本相对应。

当执行水平伸缩时，Deployment 会修改当前 ReplicaSet 对象的副本数量，进而实现 Pod 的新建或销毁。
当 Pod 模板发生变化时，Deployment 会创建新的 ReplicaSet 对象，然后遵循通过滚动升级（rolling update ）的方式升级现有的 Pod。

通过 kubectl get rs 查看 由 这个 Deployment 创建的 ReplicatSet，ReplicaSet 将会保证有三个 nginx pod 副本。

$ kubectl get rs
NAME                          DESIRED   CURRENT   READY   AGE
nginx-deployment-7f4fc68488    3        3        3      6m44s



ReplicaSet 的名称满足 [Deployment-Name]-[Random-String] 格式， 其中RANDOM-STRING 是随机生成的，并以 pod-template-hash 为种子。


通过kubectl get pods --show-labels 可以查看生成的 Pod 及其 Label。

$ kubectl get pods --show-labels
NAME                                READY   STATUS    RESTARTS   AGE     LABELS
nginx-deployment-7f4fc68488-2mlzr   1/1     Running   0          7m34s   app=nginx,pod-template-hash=7f4fc68488
nginx-deployment-7f4fc68488-8p47t   1/1     Running   0          102s    app=nginx,pod-template-hash=7f4fc68488
nginx-deployment-7f4fc68488-f6jqw   1/1     Running   0          102s    app=nginx,pod-template-hash=7f4fc68488


必须在 Deployment 中指定合适的 selector 和 Pod 标签，不要和其他 Controller 的 Label 或 selector 重复，Kubernetes 不会检查并阻止 Controller 之间的 label/selector 冲突，如果多个 Controller 有相同的 lable/selector 可能导致冲突，引发预期之外的行为。

pod-Template-hash 是由 Deployment Controller 添加在其创建或领养的ReplicaSet 上的标签，该标签会保证一个 Deployment 下的子ReplicaSet 不会重复。该标签由 ReplicaSet 下的 PodTemplate 通过 hash 处理生成的，并将结果作为 Pod 的标签，并添加到Replicatset的选择器上。不要更改这个标签。

$ kubectl describe rs nginx-deployment-559d658b74
Name:           nginx-deployment-559d658b74
Namespace:      default
Selector:       app=nginx,pod-template-hash=559d658b74
Labels:         app=nginx
                pod-template-hash=559d658b74



水平伸缩与升级

1) 水平伸缩

普通的伸缩命令

kubectl scale deployment.v1.apps/nginx-deployment --replicas=10


如果 HPA 打开的话，可以通过如下命令设置HPA（基于CPU的使用率）

kubectl autoscale deployment.v1.apps/nginx-deployment --min=10 --max=15 --cpu-percent=80


执行后查看 Deployment 的伸缩状态：

$ kubectl rollout status deployment nginx-deployment
Waiting for deployment "nginx-deployment" rollout to finish: 3 of 10 updated replicas are available...
Waiting for deployment "nginx-deployment" rollout to finish: 4 of 10 updated replicas are available...
Waiting for deployment "nginx-deployment" rollout to finish: 5 of 10 updated replicas are available...
Waiting for deployment "nginx-deployment" rollout to finish: 6 of 10 updated replicas are available...
Waiting for deployment "nginx-deployment" rollout to finish: 7 of 10 updated replicas are available...
Waiting for deployment "nginx-deployment" rollout to finish: 8 of 10 updated replicas are available...
Waiting for deployment "nginx-deployment" rollout to finish: 9 of 10 updated replicas are available...
deployment "nginx-deployment" successfully rolled out

$ kubectl get deployments.apps
NAME               READY   UP-TO-DATE   AVAILABLE   AGE
nginx-deployment   10/10   10           10          16m


Deployment 的伸缩本质上是通过修改 ReplicaSet 的副本数实现的，ReplicaSet 副本数修改后，ReplicaSetController 基于控制循环进行状态拟合，创建相应的 Pod。

$ kubectl get rs
NAME                          DESIRED   CURRENT   READY   AGE
nginx-deployment-66b6c48dd5   10        10        10      16m


2) 更新镜像

使用如下命令
kubectl set image deployment/nginx-deployment nginx=nginx:1.16.1 --record

或者通过 kubectl edit 进行修改
kubectl edit deployment.v1.apps/nginx-deployment


查看展开状态结果使用如下命令

$ kubectl rollout status deployment nginx-deployment
Waiting for deployment "nginx-deployment" rollout to finish: 2 out of 3 new replicas have been updated...
Waiting for deployment "nginx-deployment" rollout to finish: 2 out of 3 new replicas have been updated...
Waiting for deployment "nginx-deployment" rollout to finish: 2 out of 3 new replicas have been updated...
Waiting for deployment "nginx-deployment" rollout to finish: 1 old replicas are pending termination...
Waiting for deployment "nginx-deployment" rollout to finish: 1 old replicas are pending termination...
deployment "nginx-deployment" successfully rolled out



通过 kubectl get deployments 可以查看其他细节。

当 PodTemplate 的内容被改变时，Deployment 会创建新的 ReplicaSet 并通过它创建 Pod 副本，同时会将旧的 ReplicaSet 的副本数量改为 0。通过 kubectl get rs 来查看Deployment的更新，可以 Pods 是由新创建的ReplicaSet 扩展出3个副本， 同时旧的副本数量缩小为 0。

kubectl get rs
NAME                          DESIRED   CURRENT   READY   AGE
nginx-deployment-1564180365   3         3         3       6s
nginx-deployment-2035384211   0         0         0       36s





通过 kubectl describe deployments 可以看到 Deployment 的 Events:
    Type    Reason             Age   From                   Message
    ----    ------             ----  ----                   -------
    Normal  ScalingReplicaSet  2m    deployment-controller  Scaled up replica set nginx-deployment-2035384211 to 3
    Normal  ScalingReplicaSet  24s   deployment-controller  Scaled up replica set nginx-deployment-1564180365 to 1
    Normal  ScalingReplicaSet  22s   deployment-controller  Scaled down replica set nginx-deployment-2035384211 to 2
    Normal  ScalingReplicaSet  22s   deployment-controller  Scaled up replica set nginx-deployment-1564180365 to 2
    Normal  ScalingReplicaSet  19s   deployment-controller  Scaled down replica set nginx-deployment-2035384211 to 1
    Normal  ScalingReplicaSet  19s   deployment-controller  Scaled up replica set nginx-deployment-1564180365 to 3
    Normal  ScalingReplicaSet  14s   deployment-controller  Scaled down replica set nginx-deployment-2035384211 to 0



除此之外还可以通过 kubectl edit deployments.apps nginx-deployment 或者 kubectl apply -f nginx-deployment.yml 来更新 PodTemplate。


3) 升级策略

Deployment 升级有两种策略：

ReCreate ：重建，先将旧的 Pod 全部干掉，然后在创建新的。
Rolling Update：滚动更新，默认策略

滚动更新时，Deployment 允许在同一个时刻存在多个不同版本实例。当滚动更新 Deployment 时，在 Deployment 展开过程中（正在展开或暂停），DeploymentController 会平衡新增的副本和当前活跃的副本以降低风险。 这被称为按比例扩展。

Deployment 主要通过 maxSurge 和 maxUnavailable 两个字段控制新旧版本的比例：

maxSurge：允许的最多超出的 Pod 数量。默认值为 25%，计算方式是四舍五入，也可以设置绝对值。比如 Pod 数为 4，那么默认是 4 * 25% = 1，即最多允许超出 1 个，也就是说在滚动升级期间，最多可以有 5 个 Pod 运行。
maxUnavailable：最多允许多少 Pod 不可用。默认值为 25，计算方式是四舍五入，也可以设置绝对值。比如 Pod 数为 4，那么默认是 4 * 25% = 1，即最多允许 1 个 Pod 不可用，也就是说在滚动升级期间，最少有 3 个 Pod 运行。

4) Rollover
当 Deployment 更新时，其会创建新的 的ReplicaSet 并将 Pod 部署为期望的数量。如果在展开的过程找那个又有更新，此时 Deployment 会再次创建新的副本，并终止扩展之前的副本。

例如，我们在创建一个副本为 5 镜像是 nginx:1.14.2 的 Deployment， 目前只有 3 个副本存在，此时更新 Deployment 的镜像为 nginx:1.16.1 。在此场景下，Deployment 会立刻杀掉已经创建的3 个 nginx:1.14.2 的副本， 并开始创建 nginx:1.16.1 Pod 的副本，它并不会等 5 个 nginx:1.14.2 都创建完才开始 nginx:1.16.1 副本的更新。


回滚、暂停与恢复

1) 回滚

查看历史版本

当更新 Deployment 的 PodTemplate 进行升级时，Kubernetes 会保存升级历史，可以通过 kubectl rollout history  命令查看。

kubectl rollout history deployment.v1.apps/nginx-deployment

deployments "nginx-deployment"
REVISION    CHANGE-CAUSE
1           kubectl apply --filename=https://k8s.io/examples/controllers/nginx-deployment.yaml --record=true
2           kubectl set image deployment.v1.apps/nginx-deployment nginx=nginx:1.16.1 --record=true
3           kubectl set image deployment.v1.apps/nginx-deployment nginx=nginx:1.161 --record=true



其中 CHANGE-CAUSE 的这一系列输出来自于 kubernetes.io/change-cause 注解，这是 --record 参数的效果。可以通过 kubectl desc rs/deployment 等命令查看其详情。
Kubernetes 默认保存所有版本的记录，为了节省空间，可以通 spec.revisionHistoryLimit
 	保留版本的个数，默认为 10，如果设置为 0 则表示不保存，也就无法进行回滚操作了。

查看具体的一个版本的细节

$ kubectl rollout history deployment.v1.apps/nginx-deployment --revision=2
deployment.apps/nginx-deployment with revision #2
Pod Template:
  Labels:	app=nginx
	pod-template-hash=7f4fc68488
  Annotations:	kubernetes.io/change-cause: kubectl set image deploy nginx-deployment nginx=nginx:1.17 --record=true
  Containers:
   nginx:
    Image:	nginx:1.17
    Port:	80/TCP
    Host Port:	0/TCP
    Environment:	<none>
    Mounts:	<none>
  Volumes:	<none>


回滚到之前的版本

回滚到最近的一个版本

kubectl rollout undo deployment.v1.apps/nginx-deployment


回滚到指定的版本

kubectl rollout undo deployment.v1.apps/nginx-deployment --to-revision=2

Deployment 会修改对应版本的 ReplicaSet 副本数，并将当前版本的 ReplicaSet 副本数降为 0。


$ kubectl get rs
NAME                                DESIRED   CURRENT   READY   AGE
nginx-deployment-559d658b74   0         0         0       6d21h
nginx-deployment-66b6c48dd5   3         3         3       6d22h
nginx-deployment-7f4fc68488     0         0         0       6d22h

$ kubectl get rs
NAME                                         DESIRED   CURRENT   READY   AGE
nginx-deployment-559d658b74   3         3         3       6d21h
nginx-deployment-66b6c48dd5   0         0         0       6d22h
nginx-deployment-7f4fc68488     0         0         0       6d22h


2) 暂停或继续

有时在升级过程中可能会发现新的问题，此时可以通过 kubectl rollout pause 命令暂停更新，并在暂停期间执行多次变更后通过 kubectl rollout resume  恢复更新。Deployment 会将暂停期间的多次更新视为一次展开，这样可以让你应用多个更新时避免出发多个不必要的展开。

$ kubectl rollout pause deployment.v1.apps/nginx-deployment

$ kubectl set image deployment.v1.apps/nginx-deployment nginx=nginx:1.16.1
deployment.apps/nginx-deployment image updated

$ kubectl rollout history deployment.v1.apps/nginx-deployment
deployments "nginx"
REVISION  CHANGE-CAUSE
1   <none>
$ kubectl get rs
NAME               DESIRED   CURRENT   READY     AGE
nginx-2142116321   3         3         3         2m

$ kubectl set resources deployment.v1.apps/nginx-deployment -c=nginx --limits=cpu=200m,memory=512Mi
deployment.apps/nginx-deployment resource requirements updated

$ kubectl rollout resume deployment.v1.apps/nginx-deployment
deployment.apps/nginx-deployment resumed

$ kubectl get rs -w
NAME               DESIRED   CURRENT   READY     AGE
nginx-2142116321   2         2         2         2m
nginx-3926361531   2         2         0         6s
nginx-3926361531   2         2         1         18s
nginx-2142116321   1         2         2         2m
nginx-2142116321   1         2         2         2m
nginx-3926361531   3         2         1         18s
nginx-3926361531   3         2         1         18s
nginx-2142116321   1         1         1         2m
nginx-3926361531   3         3         1         18s
nginx-3926361531   3         3         2         19s
nginx-2142116321   0         1         1         2m
nginx-2142116321   0         1         1         2m
nginx-2142116321   0         0         0         2m
nginx-3926361531   3         3         3         20s
$ kubectl get rs
NAME               DESIRED   CURRENT   READY     AGE
nginx-2142116321   0         0         0         2m
nginx-3926361531   3         3         3         28s




Canary Deployment



利用 Deployment 可以实现一个粗糙的灰度部署，可以部署两个版本的 Deployment 并设置不同的副本。这样流量可以按比例流入不同的版本中。


apiVersion: apps/v1
kind: Deployment
metadata:
 name: my-app-v1
 labels:
   app: my-app
spec:
 replicas: 10
 selector:
   matchLabels:
     app: my-app
     version: v1.0.0
 template:
   metadata:
     labels:
       app: my-app
       version: v1.0.0
   spec:
     containers:
     - name: my-app
       image: containersol/k8s-deployment-strategies

---

apiVersion: apps/v1
kind: Deployment
metadata:
 name: my-app-v2
 labels:
   app: my-app
spec:
 replicas: 1
 selector:
   matchLabels:
     app: my-app
     version: v2.0.0
 template:
   metadata:
     labels:
       app: my-app
       version: v2.0.0
   spec:
     containers:
     - name: my-app
       image: containersol/k8s-deployment-strategies


虽然使用 Deplpyment 可以实现一个基本的灰度部署，但在实际场景中其功能还是太弱了，无法对灰度流量做更多的控制。

其他灰度方案

Ingress-Nginx#Canary
Istio 等 ServiceMesh 方案
AutoScaling & HPA 

Deployment 可以让我们手动的进行 Pod 的水平扩缩容操作，但在遇到流量压力时，无论是应用层面的 Pod，还是基础设施层面的 Node，人工操作都不容易做到及时和准确的响应，因此 Kuberrnetes 提供了 AutoScaler 来进行快速的自动伸缩。

Kubernetes 中的自动伸缩可以分为三类：

Pod 水平扩缩容（HPA）
Pod 垂直扩缩容（VPA）
集群扩缩容
这里主要看下水平伸缩。HPA 目前支持下面四种资源的自动水平伸缩

Deployment
StatefulSet
ReplicaSet 
ReplicaController

下面是《Kubernetes in Action》中的一个示例，首先创建一个 Deployment 作为水平伸缩的对象

apiVersion: apps/v1
kind: Deployment
metadata:
  name: kubia
spec:
  replicas: 3
  selector:
    matchLabels:
      app: kubia
  template:
    metadata:
      name: kubia
      labels:
        app: kubia
    spec:
      containers:
      - image: luksa/kubia:v1
        name: nodejs
        resources:
          requests:
            cpu: 100m




HPA 可以通过 kubectl 命令自动创建，

$ kubectl autoscale deployment kubia --cpu-percent=30 --min=1 --max=5
horizontalpodautoscaler.autoscaling/kubia autoscaled


也可以通过 yaml 创建：

​​apiVersion: autoscaling/v2beta2
kind: HorizontalPodAutoscaler
metadata:
  name: php-apache
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: kubia
  minReplicas: 1
  maxReplicas: 5
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 30


metrics  伸缩的衡量指标，这里指通过自动扩缩容将 Pod 的 CPU 使用率维持在 30%。也就是说当 Pod 的 CPU 使用率超过 30% 时会进行水平扩容.
scaleTargetRef: 伸缩的对象，这里是指定了名为 kubia 的 Deployment。
minReplicas，maxReplicas：表示扩缩容的最小，最大副本数。这里最大副本数是 5，当扩容到 5 个 Pod 时如果 CPU 使用率依然没有降到 30%，此时也不会创建更多的副本。


常见完 HPA 后可以通过下面的程序来访问以增加 Pod 的负载，如果一条指令不够可以多执行几次。

$ kubectl run -it --rm --restart=Never loadgsnerater --image=busybox  -- sh -c "while true; do wget -O - -q http://kubia.default; done"


现在查看新建的 HPA 信息，可以看到其已经有扩容发生了

$ watch -n 1 kubectl get hpa,deployment
I1204 06:53:22.003902   13443 request.go:665] Waited for 1.136189678s due to client-side throttling, not priority and fairness, request: GET:https://172.19.0.7:6443/apis/rbac.authorization.k8s.io/v1?
timeout=32s
NAME                                        REFERENCE          TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
horizontalpodautoscaler.autoscaling/kubia   Deployment/kubia   38%/30%   1         5         5          5m20s

NAME                    READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/kubia   5/5     5            5           6m50s


$ kubectl describe hpa kubia
… 
Conditions:
  Type            Status  Reason              Message
  ----            ------  ------              -------
  AbleToScale     True    ReadyForNewScale    recommended size matches current size
  ScalingActive   True    ValidMetricFound    the HPA was able to successfully calculate a replica count from cpu resource utilization (percentage of request)
  ScalingLimited  False   DesiredWithinRange  the desired count is within the acceptable range
Events:
  Type    Reason             Age   From                       Message
  ----    ------             ----  ----                       -------
  Normal  SuccessfulRescale  18s   horizontal-pod-autoscaler  New size: 5; reason: cpu resource utilization (percentage of request) above target


Job/CrobJob

像 Deployment 这种是对正常在线业务的抽象，在实际场景中我们有很多离线任务，比如定时脚

任务、离线计算等。Kubernetes 使用 Job 和 CronJob 来执行此类任务。


Job

Job 是对离线任务的抽象，可以用来执行离线计算、批处理任务等。其使用定义如下：

apiVersion: batch/v1
kind: Job
metadata:
  name: pi
spec:
  completions: 10
  parallelism: 5
  backoffLimit: 4
  activeDeadlineSeconds: 100
  template:
    spec:
      containers:
      - name: pi
        image: perl
        command: ["perl",  "-Mbignum=bpi", "-wle", "print bpi(2000)"]
      restartPolicy: Never


Job 的运行也是遵循 Kubernetes 的控制器模式，当 Job 创建时，JobController 监听 Pod 和 Job 的变化，并执行相应的状态拟合。

Job 并不需要指定 selector，在其创建时会被自动加上 controller-uid 标签，该标签会加到 Job 的 selector 和由该 Job 创建出来的 Pod 的标签上。这样做的目的是为了防止不同  Job 之间管理的 Pod 发生重合。

completions：完成次数，会创建对应数量的 Pod 执行任务。默认为 1，上面示例中为 10，则 Job 最终会创建 10 个 Pod。

parallelism：并行数量，表示同时启动的任务数量。默认为 1，表示串行执行，上面示例中为 5，则 Job 会同时运行 5 个 Pod 执行任务。

backoffLimit：重试次数。Job 中 Pod 的重启策只能为设置为 Never 或者 OnFailure。设置为 Never 时，任务失败后 Job 会尝试不断新建 Pod，默认为 6 次，并且重建 Pod 的时间间隔呈指数增加，即下一次重建的间隔是 10s、20s、40s、80s… 如果是 OnFailure 则会不断尝试重启容器。
activeDeadlineSeconds：Job 终止时间，为了防止 Pod 一直运行无法退出，可以设置该值，一旦超出则该 Job 的所有 Pod 都会被终止。



CronJob

CornJob 描述的是定时任务，和 crontab 非常相似。我们可以 Cron 表达式指定任务的调度时间：

apiVersion: batch/v1beta1
kind: CronJob
metadata:
  name: pi
spec:
  schedule: "*/1 * * * *"
  jobTemplate:
    spec:
      completions: 2
      parallelism: 1
      template:
        spec:
          containers:
          - name: pi
            image: perl
            command: ["perl",  "-Mbignum=bpi", "-wle", "print bpi(2000)"]
          restartPolicy: OnFailure



jobTemplate：Job 模板，基于该模板创建 Job 对象。CronJob 是通过 Job 控制 Pod的，就和 Deployment 通过 ReplicaSet 控制 Pod 一样。
Schedule： 时间格式和 crontab 是一样的，含义如下：



# ┌───────────── 分钟 (0 - 59)
# │ ┌───────────── 时 (0 - 23)
# │ │ ┌───────────── 一月中的天(1 - 31)
# │ │ │ ┌───────────── 月份 (1 - 12)
# │ │ │ │ ┌───────────── 星期(0-6) (日~六，有些系统中7也表示星期日);
# │ │ │ │ │                                   
# │ │ │ │ │
# │ │ │ │ │
# *  *  *  *  *



CronJob 并不能保证准时执行，可能会有一定的滞后。
concurrencyPolicy: 并发策略。因为是定时任务，存在当前任务要执行时，上一个任务还没有结束的情况。该字段用来设置其处理策略。
Allow: 默认值，允许任务同时存在。
Forbid: 该周期内的任务被跳过，Job 会被标记为 miss。
Replace: 新的 Job 会替换旧的未完成的 Job。
如果在 CronJob 在一段时间内 miss 数据达到 100，则该 CronJob 会被停止。该时长通过 startingDeadlineSeconds 字段设置，但不要小于 10s。

和其他控制器运行方式不同，CronJobController 并不是使用 watch 监听机制从 Informer 接收变更信息的，而是每隔 10s 主动访问 apiserver 获取数据。因为其每隔 10s 访问 apiserver 检查资源，因此如果 startingDeadlineSeconds 设置小于 10s，可能会导致 CronJob 无法被调度。


图片来源：https://draveness.me/kubernetes-job-cronjob/

StatefulSet 

由 Deployment 管理的 Pod 的可以随意的进行上下线、扩缩容，新加的 Pod 可以部署在任意符合条件的节点上。但在实际情况中除了无状态的、可以随意部署灵活伸缩的应用，在分布式系统中，我们还有很多有状态的应用，每个实例有自己的存储数据，实例之间也有主从等特定的关系，如果我们将这样一个 Pod 随意下线上线，那应用重启后就会丢失数据，导致应用执行失败，因此这类应用需要更加特殊的处理，保证其重新部署后数据不会丢失，节点之间的关系不会方发生变化。

Kubernetes 提供了 StatefulSet 来管理有状态应用的资源对象。StatefulSet 早期也叫 PetSet。应用服务可以分为两类，一种是宠物模式，一种是奶牛模式：
宠物模式：宠物有自己单独的名字，我们必须悉心照料每一只宠物，宠物生病时一定要治好救活，主要是对应有状态的服务。
奶牛模式：有些应用像是奶牛，如果奶牛出了问题我们新换一只就行，不用费心太多。
Deployment 管理的无状态应用就像是奶牛，而有状态应用就是在宕机后必须恢复原样的那只“宠物”。
应用中的状态可以分为两类：
拓扑状态：应用中的多个实例并不是完全对等的，它们之前有特殊的关系，这意味着实例的启动需要按照一定的顺序进行。比如 MySQL 的主从，必须先启动主节点，在启动从节点，而终止顺序则应该相反。除此之外，新启动的 Pod 应该和原来网络标识是一样的，以便用户可以使用相同的方式访问。
存储状态：多个实例的数据存储是固定的并且数据不能丢失。Pod A 的数据，在 Pod A 重启后依然可以被访问到。
因此由 StatefulSet 管理的 Pod 有下面一些特性：

Pod 会按顺序创建和销毁：创建后续 Pod 前，必须保证前面的 Pod 已经这个在正常运行进入 Ready 状态。删除时会按照与创建相反的顺序删除，并且只有在后面的 Pod 都删除成功后才会删除当前 Pod。

StatefulSet 的 Pod 具有稳定且唯一的标识，包括顺序标识、稳定的网络标识和稳定的存储。该标识和 Pod 是绑定的，不管它被调度在哪个节点上。对于具有 N 个副本的 StatefulSet，StatefulSet 中的每个 Pod 将被分配一个整数序号，从 0 到 N-1，该序号在 StatefulSet 上是唯一的。

StatefulSet 的 Pod 具有稳定的持久化存储，每个 Pod 都可以拥有自己的独立的 PVC 资源，即使Pod被重新调度到其他节点上，它所拥有的持久化存储也依然会被挂载到该Pod。

StatefulSet 中的每个 Pod 根据 StatefulSet 的名称和 Pod 的序号派生出它的主机名。 组合主机名的格式为 $(StatefulSet name)-$(序号)。 比如，一个叫web的StatefulSets的三个Pod名称分别为 web-0、web-1、web-2 。 

通过固定且唯一的标识，不变的持久化存储，Kubernetes 可以保证在 Pod 重建后依然保留上次的运行状态，从而运行有状态和应用。

下面是 StatefulSet 的一个示例：
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: web
spec:
  selector:
    matchLabels:
      app: nginx # has to match .spec.template.metadata.labels
  serviceName: "nginx"
  replicas: 3 # by default is 1
  template:
    metadata:
      labels:
        app: nginx # has to match .spec.selector.matchLabels
    spec:
      terminationGracePeriodSeconds: 10
      containers:
      - name: nginx
        image: k8s.gcr.io/nginx-slim:0.8
        ports:
        - containerPort: 80
          name: web
        volumeMounts:
        - name: www
          mountPath: /usr/share/nginx/html
  volumeClaimTemplates:
  - metadata:
      name: www
    spec:
      accessModes: [ "ReadWriteOnce" ]
      storageClassName: "my-storage-class"
      resources:
        requests:
          storage: 1Gi



Pod 通信

StatefulSet 创建的 Pod 都有自己的网络标识，并且有状态应用的各个 Pod 之间通常是需要互相通信的。常规的让外部访问 Pod 的方式是使用 Service，通过 svc-name.namespace.svc.cluster.local 域名可以获取到 Service 的 IP，然后随机访问到 某个 Pod。但对于 StatefulSet，每个 Pod 需要知道所有的伙伴节点并进行通信，比如 ElasticSearch 的选主过程，需要所有的选主节点进行投票。

Kubernetes 提供了  Headless Service 来实现 StatefulSet 下 Pod 间的通信，下面是 Headless Service 的示例

apiVersion: v1
kind: Service
metadata:
 name: nginx
 labels:
   app: nginx
spec:
 ports:
 - port: 80
   name: web
 # 设置为 None
 clusterIP: None
 selector:
   app: nginx



和默认 Service 不同，Headless 没有自己的 IP 地址，经由 Headless 代理的 Pod，每个 Pod 创建时都会创建独立的 DNS 记录，格式为 $(pod-ip).$(svc-name).namespace.svc.cluster.local  ，如： web-1.nginx.default.svc.cluster.local。。虽然 Pod 会发生重建，其 IP 也会发生变化，但我们通过上面的 DNS 域名就可以实现对 Pod 的固定访问。

同时对于 Pod 之间的通信，Headless 类型的服务也会创建 svc-name.namespace.svc.cluster.local 域名的 DNS 记录，但是返回的是所有 Pod 的 IP。

数据存储

为了实现数据存储，StatefulSet 需要创建持久卷声明，一个 StatefulSet 可以拥有一个或多个持久卷声明模板，在 Pod 创建之前会先创建这些 PVC 并作为 volume 绑定到 Pod 上。

因为默认声明了 PVC 因此必须要有 PV 来提供存储，给定 Pod 的存储可以由 PVC 模板指定的  StorageClass 自动创建，也可以由集群管理员预先提供。

另外在删除或者伸缩 StatefulSet 时并不会自动删除它关联的存储卷，必须要要手动删除，这样做主要是为了保证数据安全，避免数据误删。

下面是官方文档提到的使用 StatefulSet 时的一些限制和注意事项：


删除 StatefulSets 时，StatefulSet 不提供任何终止 Pod 的保证。为了实现 StatefulSet 中的 Pod 可以有序和优雅的终止，可在删除之前将 StatefulSet 缩放为 0。

在默认 Pod 管理策略(OrderedReady) 时使用 Rolling Update，可能进入需要 人工干预 才能修复的损坏状态。

必须设置 StatefulSet 的 .spec.selector 字段，使之匹配其在 .spec.template.metadata.labels 中设置的标签。在 Kubernetes 1.8 版本之前，被忽略 .spec.selector 字段会获得默认设置值。在 1.8 和以后的版本中，未指定匹配的 Pod 选择器将在创建 StatefulSet 期间导致验证错误。

当 StatefulSet 创建 Pod 时，它会添加一个标签 statefulset.kubernetes.io/pod-name，该标签设置为 Pod 名称。通过该标签可以给 StatefulSet 中的特定 Pod 绑定一个 Service。

在 Kubernetes 1.7 及以后的版本中，StatefulSet 的 .spec.updateStrategy .spec.updateStrategy 字段让您可以配置和禁用掉自动滚动更新 Pod 的容器、标签、资源请求或限制、以及注解。

DaemonSet

DeamonSet 确保全部（或者某些）节点上运行一个 Pod 的副本。 当有节点加入集群时， 也会为他们新增一个 Pod 。 当有节点从集群移除时，这些 Pod 也会被回收。删除 DaemonSet 将会删除它创建的所有 Pod。一般用于“监控进程”、“日志收集”或“节点管控”。

apiVersion: v1
kind: Pod
metadata:
  name: nginx
spec:
  affinity:
    nodeAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
        - matchExpressions:
          # Key Name
          - key: disktype
            operator: In
            # Value
            values:
            - ssd            
  containers:
  - name: nginx
    image: nginx
    imagePullPolicy: IfNotPresent


DaemonSet 默认会在所有节点创建一个 Pod，可以通过 NodeSelector 或者 NodeAffinity 来选择特定的节点部署。

另外设置 Pod 时需要考虑污点和资源设置等情况，比如如果 Pod 想被调度到 master 节点需要设置容忍污点。

kind: DaemonSet
metadata:
  name: fluentd-elasticsearch
  namespace: kube-system
  labels:
    k8s-app: fluentd-logging
spec:
  selector:
    matchLabels:
      name: fluentd-elasticsearch
  template:
    metadata:
      labels:
        name: fluentd-elasticsearch
    spec:
      tolerations:
      # this toleration is to have the daemonset runnable on master nodes
      # remove it if your masters can't run pods
      - key: node-role.kubernetes.io/master
        operator: Exists
        effect: NoSchedule


Kubernetes 网络

网络基础知识

Linux Network Stack

Linux 网络栈示例如下，其符合 TCP/IP 网络模型以及 OSI 模型的理念，网络数据包在链路层、网络层、传输层、应用层之前逐层传递、


图片来自 https://icyfenix.cn/immutable-infrastructure/network/linux-vnet.html
Netfilter 

Netfilter 是 Linux 内核提供的一个框架，它允许以自定义处理程序的形式实现各种与网络相关的操作。 Netfilter 为数据包过滤、网络地址转换和端口转换提供了各种功能和操作，这些功能和操作提供了引导数据包通过网络并禁止数据包到达网络中的敏感位置所需的功能。

简单来说，netfilter 在网络层提供了 5 个钩子（hook），当网络包在网络层传输时，可以通过 hook 注册回调函数来对网络包做相应的处理，hook 如图所示：




图片来自 https://www.teldat.com/blog/en/nftables-and-netfilter-hooks-via-linux-kernel/

Netfilter 提供的 5 个 hook 分别是：

​​Prerouting：路由前触发。设备只要接收到数据包，无论是否真的发往本机，都会触发此hook。一般用于目标网络地址转换（Destination NAT，DNAT）。

Input：收到报文时触发。报文经过 IP 路由后，如果确定是发往本机的，将会触发此hook，一般用于加工发往本地进程的数据包。
Forward 转发时触发。报文经过 IP 路由后，如果确定不是发往本机的，将会触发此 hook，一般用于处理转发到其他机器的数据包。
Output 发送报文时触发。从本机程序发出的数据包，在经过 IP 路由前，将会触发此 hook，一般用于加工本地进程的输出数据包。
Postrouting 路由后触发。从本机网卡出去的数据包，无论是本机的程序所发出的，还是由本机转发给其他机器的，都会触发此hook，一般用于源网络地址转换（Source NAT，SNAT）。
Netfilter 允许在同一个钩子处注册多个回调函数，在注册回调函数时必须提供明确的优先级，多个回调函数就像挂在同一个钩子上的一串链条，触发时能按照优先级从高到低进行激活，因此钩子触发的回调函数集合就被称为“回调链”（Chained Callback)。
Linux 系统提供的许多网络能力，如数据包过滤、封包处理（设置标志位、修改 TTL等）、地址伪装、网络地址转换、透明代理、访问控制、基于协议类型的连接跟踪，带宽限速，等等，都是在 Netfilter 基础之上实现，比如 XTables 系列工具， iptables ，ip6tables 等都是基于 Netfilter 实现的。
Iptables
iptables 是 Linux 自带的防火墙，但更像是一个强大的网络过滤工具，它在 netfilter 的基础上对回调函数的注册做了更进一步的抽象，使我们仅需要通过配置 iptables 规则、无需编码就可以使用其功能。

Iptables 内置了 5 张规则表如下：
raw 表：用于去除数据包上的连接追踪机制（Connection Tracking）。
mangle 表：用于修改数据包的报文头信息，如服务类型（Type Of Service，ToS）、生存周期（Time to Live，TTL）以及为数据包设置 Mark 标记，典型的应用是链路的服务质量管理（Quality Of Service，QoS）。
nat 表：用于修改数据包的源或者目的地址等信息，典型的应用是网络地址转换（Network Address Translation），可以分为 SNAT（修改源地址） 和 DNAT（修改目的地址） 两类。
filter 表：用于对数据包进行过滤，控制到达某条链上的数据包是继续放行、直接丢弃或拒绝（ACCEPT、DROP、REJECT），典型的应用是防火墙。
security 表：用于在数据包上应用SELinux，这张表并不常用。
上面5个表的优先级是 raw→mangle→nat→filter→security。在新增规则时，需要指定要存入到哪张表中，如果没有指定，默认将会存入 filter 表。另外每个表能使用的链也不同，其关系如图所示：
图片来自 https://icyfenix.cn/immutable-infrastructure/network/linux-vnet.html

图片来自 https://www.teldat.com/blog/en/nftables-and-netfilter-hooks-via-linux-kernel/

IPSet

IPSet 是 Linux 内核的网络框架，是 iptables 的配套工具，其允许通过 ipset 命令设置一系列的 IP 集合，并针对该集合设置一条 iptables 规则，从而解决了 iptables 规则过多的问题，带来如下的一些好处：
存储多个 IP 和端口，并且只需要创建一条 iptables 规则就可以实现过滤、转发，维护方便。
IP 变动时可以动态修改 IPSet 集合，无需修改 iptables 规则，提升更新效率。
进行规则匹配时，时间复杂度由 O(N) 变为 O(1)。

Kubernetes CNI 

Kubernetes 本身并没有提供网络相关的功能，鉴于网络通信的复杂性与专业性，为了具有更好的扩展性，在一开始业界的做法就是将网络功能从容器运行时以及容器编排工具中剥离出去，形成容器网络标准，具体实现以插件的形式接入。

在早期 Docker 提出过 CNM规范（Container Network Model，容器网络模型），但被后来 Kubernetes 提出的 CNI（Container Network Interface，容器网络接口）规范代替，两者功能基本一致。

CNI 可以分为两部分：
CNI 规范：定义操作容器网络的各种接口，比如创建、删除网络等。Kubernetes 中容器运行时面向 CNI 进行通信。
CNI 插件：各个厂商基于 CNI 实现各自的网络解决方案，以插件的形式安装在 Kuberetes 中。下图是一些常见的插件提供商:





有了 CNI 规范后，针对容器的网络操作就只需要面向 CNI 即可，Kubernetes 不在关心具体的实现细节。无论实现如何，Kuberetes 对网络提出如下要求：

每个 Pod 都要有自己的 IP
节点上的 pod 可以与所有节点上的所有 pod 通信，无需 NAT
节点上的代理（例如系统守护进程、kubelet）可以与该节点上的所有 Pod 通信

下图是使用 Flannel 网络插件，在创建 Pod 时为 Pod 分配 IP 的过程，可以看到 Pod 的 IP 是由容器运行时访问 CNI 接口，最终由 Flannel 网络插件提供的。


 
图片来自 https://ronaknathani.com/blog/2020/08/how-a-kubernetes-pod-gets-an-ip-address/
Container To Container

首先看 Docker 容器的通信方式，Docker网络分成4种类型

Bridge ：默认的网络模式，使用软件桥接的模式让链接到同一个Bridge上的容器进行通讯。
Host：直接使用本机的网络资源，和普通的原生进程拥有同等的网络能力。
MacVLAN：允许为容器分配mac地址，在有直接使用物理网络的需求下可以使用该网络类型。 
Overlay：在多个Docker daemon host中创建一个分布式的网络，让属于不同的daemon host的容器也能进行通讯。

Bridge, Host, MacVLAN都是本机网络，Overlay是跨宿主机的网络。当不允许 container 使用网络的场景下，比如生成密钥哈希计算等，有安全性需求，可以将网络类型设置成 None，即不允许该容器有网络通讯能力。

这里重点关注 Bridge 网桥模式，Docker 启动时会创建一个名为 docker0 的网桥，同主机上容器之间的通信都是通过该网桥实现的，如图所示：


当启动容器后，docker 会通过 Veth（Virtual Ethernet）对将容器和 docker0 网桥连起来，同时修改容器内的路由规则，当 container1 向 container2 发起请求时，基于路由规则会将请求路由到 docker0 网桥，然后在转发到 container2 中。

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



进入容器看容器内部的网络设备：

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





我们完全可以通过命令行模拟上述操作，实现两个网络 namespace 的互通，步骤如下：

创建 namespace
$ sudo ip netns add test1
$ sudo ip netns add test2


创建 veth 对
$ sudo ip link add veth-test1 type veth peer name veth-test2


将 veth 对加到 namespace 中
$ sudo ip link set veth-test1 netns test1
$ sudo ip link set veth-test2 netns test2


给 veth 添加地址
$ sudo ip netns exec test1 ip addr add 192.168.1.1/24 dev veth-test1
$ sudo ip netns exec test2 ip addr add 192.168.1.2/24 dev veth-test2


启动 veth 设备
$ sudo ip netns exec test1 ip link set dev veth-test1 up
$ sudo ip netns exec test2 ip link set dev veth-test2 up


在 test1 namespace 中访问 test2
$ sudo ip netns exec test1 ping 192.168.1.2
PING 192.168.1.2 (192.168.1.2) 56(84) bytes of data.
64 bytes from 192.168.1.2: icmp_seq=1 ttl=64 time=0.060 ms
64 bytes from 192.168.1.2: icmp_seq=2 ttl=64 time=0.053 ms
64 bytes from 192.168.1.2: icmp_seq=3 ttl=64 time=0.038 ms



Pod To Pod

跨主机的网络通信

跨主机的网络通信方案目前主要有三种方式：

Overlay 模式

所谓 Overylay 网络就是在一个网络之上在覆盖一层网络，对应到我们的云原生环境，为了支持 Pod 的灵活变化，我们可以在基础物理网络之上，在虚拟化一层网络来进行网络通信。位于上层的网络就叫做 Overlay，而底层的基础网络就是 Underlay。Overlay 网络的变化不受底层网络结构的约束，有更高的自由度和灵活性。但缺点在于 Overlay 网络传输是依赖底层网络的，因此在传输时会有额外的封包，解包，从而影响性能。像 Flannel 的 VXLAN 、Calico 的 IPIP 模式、Weave 等 CNI 插件都采用的了该模式。

路由模式

该模式下跨主机的网络通信是直接通过宿主机的路由转发实现的。和 Overlay 相比其优势在于无需额外的封包解包，性能会有所提升，但坏处时路由转发依赖网络底层环境的支持，要么支持二层连通，要么支持 BGP 协议实现三层互通。Flannel 的 HostGateway 模式、Calico 的 BGP 模式都是该模式的代表。


Underlay 模式

容器直接使用宿主机网络通信，直接依赖于虚拟化设备和底层网络设施，上面的路由模式也算是 Underlay 模式的一种。理论上该模式是性能最好的，但因为必须依赖底层，并且可能需要特定的软硬件部署，无法做到 Overlay 那样的自由灵活的使用。

下面以 Flannel 和 Calico 为例看下 Overlay 和路由模式的具体实现。

Flannel UDP

Flannel 是 CoreOS 为Kubernetes设计的配置第三层网络（IP层）的开源解决方案。在 UDP 模式下，Flannel 会在每个节点上运行名为 flanneld 二进制 agent，同时创建名为 flannel0 的虚拟网络设备，在主机网络上创建另外一层扁平的网络，也就是所谓的 Overlay network，所有集群内的Pod会被分配在这个overlay 网络中的一个唯一IP地址，然后Pod 之间的就可以直接使用的这个IP地址和对方通讯了。另外每个节点上还会创建一个 cni0 bridge 来实现本机容器的通讯。



以上图为例， Node1 节点中 IP 为 100.96.1.2 的 container-1 容器要访问节点 Node2 中 IP 为 100.96.2.3 的 container2 容器。转发过程如下：

创建原始 IP 包，源地址为 容器1 的 IP，目的地址为容器 2 的 IP。
经过 cni0 bridge 转发到 flannel0（依赖内核路由表）
发给 flannel0 的包会由 flanneld 程序处理，它会将 IP 包作为数据打包到 UDP 包中，并在存储中（etcd）找到目标地址的实际 IP 地址（即 Node2 的 IP 地址），最终封装为完整的 IP 包进行发送。
Node2 收到 IP 包后进行逆向操作，先将数据报发送给 flanneld 程序，解包后经 flannel0、cni0 bridge 最终发送到容器。
网络包路线定位可以通过下面一些工具实现和基本流程		
brctl show 可以看到容器的网卡绑在了哪个网桥上，当网络包到了 cni0 上就基本上进入路由了。
netstat -nr 可以看到路由表的转发规则，一般会把会把其转到 flannel.1 这个网卡上。
用arp命令看一下 arp -i flannel.1 可以知道各个网段的mac地址。

UDP 模式涉及到 3 次内核态和用户态的转换，因此性能较低，已经被废弃。


Flannel VXLAN

UDP 模式下的打包过程涉及到用户态与内核态的转换，严重影响性能，现在已经被弃用。新的 Overlay 实现方式是通过 VXLAN 在主机之间建立逻辑隧道，并且直接在内核打包，从而提升性能。

VXLAN 隧道网络结构如下：



图片来自https://support.huawei.com/enterprise/en/doc/EDOC1100023542?section=j016&topicName=vxlan


VETP( Virtual Tunnel Endpoints ): VXLAN 网络的虚拟边缘设备，是 VXLAN 隧道的起点与终点，负责 VXLAN 中的封包与解包。和 UDP 模式下 flanneld 程序作用类似。

VNI（VXLAN Network Identifier）：VXLAN 的网络标识，代表一个 VXLAN 网段，只有相同网段下的 VXLAN 虚拟设备才可以互相通信。

VXLAN tunnel：VXLAN 隧道就是在两个 VETP 之间建立的可以用于报文传输的逻辑隧道。业务数据报被 VETP 设备进行封包，然后在第三层透明的传到远端的 VETP 设备在进行数据的解包。

下图是 VXLAN 的数据报格式，VETP 会将发来的原始二层数据包加上 VXLAN Header（主要包含 VNI） 后发送给主机，主机在封装为 UDP 包进行传输，下图的 VXLAN 数据报的的格式：


下图是 Flannel 在 VXLAN 模式下的数据传输过程：


1. 该模式下 Flannel 会创建 flannel.vni 的 VETP 设备
2. node0 的 container1 请求 node1 的 container 1，数据报经由 cni0 bridge 传输到 flanel.vni 设备进行  VXLAN 封包。然后作为 UDP 包发出。
3. UDP 包走正常路由发送到 node1 的 vetp 设备进行解包，最终发送到目标容器
Flannel host-gw

Flannel 现在默认是 VXLAN 模式，可以通过修改其配置设置为 host-gw 模式。

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


在 host-gw 模式下，当 Pod 创建并分配 IP 时，Flannel 会在主机创建路由规则，Pod 之间的通信是通过 IP 路由实现的。

如下图所示，两个节点分别会添加如下 IP 规则：

Node0: ip route add 192.168.1.0/24 via 10.20.0.2 dev eth0
Node1: ip route add 192.168.0.0/24 via 10.20.0.1 dev eth0


图片来自 https://www.digihunch.com/2021/06/kubernetes-networking-solutions-overview/

Flannel-gw 模式有如下几个特点：

跨主机通信是通过路由规则实现的，数据包的 next hop 就是目标机器，因此在 Flannel-gw 模式下节点必须都是二层互通的。
Flannel-gw 模式因为省去了 Overlay 方式下的是数据封包解包因此其性能上会有所提升。

Calico 路由

Calico 是一个纯三层的网络方案，支持以路由规则的方式进行通信，但和 Flannel-gw 必须要求二层互通不同，Calico 使用 BGP(Border Gateway Protocol) 协议进行跨网络的互通。其工作过程本质上和 Flannel-gw 并无不同，也是在宿主机修改路由规则以及 iptables 实现基于路由的数据转发。当然 Calico 也支持 VXLAN 或者 IPIP 模式的 Overlay 方式的网络方案。

下图是在使用 Calico 时的网络架构：

主要包含如下组件：
Felix:  每个节点上的 Calico Agent，负责配置路由、iptables 等信息，确保节点之间的连通。

BIDR: BGP 的客户端，负责在各个节点之间同步路由信息，这是 Calico 能够实现三层互通的关键。

CNI 插件：与容器运行时通信，实现具体的 IP 地址分配等工作。

默认情况下 Calico 是 Node-To-Node-Mesh 模式，就是 BIDR 会与其他各个节点保持通信以同步信息，该模式一般推荐用在节点数量小于 100 的集群中，在更大规模集群中，更推荐使用 Route Reflector 模式，简单来说就是提供若干个专门的节点负责学习和存储全局的路由规则，各个 BIDR 只需要和 Reflector 节点通信即可。



Service To Pod

Service 简介

Kuberetes 通过抽象的 Service 来组织对 Kubernetes 集群内 pod 的访问，因为 pod 是可以被动态创建和销毁的，所以需要一个抽象的资源来维持对其访问的稳定，如同我们用一个 Nginx 对后端服务做做反向代理和负载均衡一样。

可以通过下面的命令创建 Service：

$ kubectl expose deployment nginx-deploy --port=8080 --target-port=80
service/nginx-deploy exposed

$ kubectl create service nodeport nginx --tcp=80
service/nginx exposed


虽然 Service 是对 Pod 的代理和负载均衡，但 Service 和 Pod 并不直接发生关联，而是通过 Endpoints 对象关联。处于 Running 状态，并且通过 readinessProbe 检查的 Pod 会出现在 Service 的 Endpoints 列表里，当某一个 Pod 出现问题时，Kubernetes 会自动把它从 Endpoints  列表里摘除掉。

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


Service 是通过 Label Selector 来选定要代理的 Pod 的，但其可以分为有 Selector 和没有 Selector 的Service， 两者的差别在于， 

有 Selector 的 Service 代理一组 Pod，为 Pod 的服务发现服务的
没有 Selector 的 Service 通常用来代理外部服务，主要应用于如下的场景:
访问的 Service 在生产中是一个在Cluster 外部的Service， 比如 Database 在生产中不在 Cluster里面，但是需要在 Cluster 内部连接测试。
所访问的服务不在同一个命名空间。
正在迁移服务到 Kubernetes，并只有部分backend服务移到K8s中。

因为 Service 是基于通过 Endpoints 进行代理，所以 Endpoints 内的 IP 地址并不一定都是 Pod 的 IP，完全可以是 K8s 集群外的服务。比如我们的 ElasticSearch 集群是部署在机器上，可以通过下面的方式创建 Service，这样在应用内部就可以配置固定域名来对 ElasticSearch 进行访问，如果 ElasticSearch 服务的 IP 发生变化，只需要修改 EndPoints 即可无需修改应用配置。

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




Service 分类

Service 主要有下面 5 种类型（ServiceType），分别是




ExternalName ：纯粹映射一条CNAME 规则的类型，并不会创建任何的代理内容。（例如，创建一个包含 externalName 字段值为foo.bar.example.com 的Service， 访问该Service时候会被替代成访问 foo.bar.example.com的请求）

ClusterIP/ NodePort /LoadBalancer 都是需要通过 kube-proxy 侦听 service 资源的更改来修改 iptables 来建立网络包转发。

ClusterIP

这是 Service 的默认模式，使用集群内部 IP 进行 Pod 的访问，对集群外不可见。ClusterIP 其实是一条 iptables 规则，不是一个真实的 IP ，不可能被路由到。kubernetes通过 iptables 把对 ClusterIP:Port 的访问重定向到 kube-proxy 或后端的pod上。

NodePort

NodePort 与 Cluster IP不一样的是，它需要调用 kube-proxy 在集群中的每个Node节点开一个稳定的端口（范围 30000 - 32767 ）给外部访问。

当 Service 以 NodePort 的方式 expose 的时候，此时该服务会有三个端口：port，targetPort，nodePort，例如


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


nodePort: 30036 是搭配 NodeIP 提供给集群外部访问的端口。
port:8080 是集群内访问该服务的端口。
targetPort:80 是Pod内容器服务监听的端口。。

LoadBalancer

负载均衡器，一般都是在使用云厂商提供的 Kubernetes 服务时，使用各种云厂商提供的负载均衡能力，使我们的服务能在集群外被访问。

如果是自己部署的集群，Kubernetes 本身没有提供类似功能，可以使用 MetalLB 来部署。

ExternalName Service

ExternalName Service 用来映射集群外的服务，意思就是这个服务不在 Kubernetes 里，但是可以由 Kubernnetes 的 Service 进行转发。比如：我们有一个AWS RDS的服务，集群内的 web 应用配置为使用 URL 为 db-service，但是其它数据不在集群内，它真正的URL是  test.database.aws-cn-northeast1b.com，这时就可以创建 ExternalName 服务将集群内的服务名映射到外部的 DNS 地址。这样 ExernalName 服务并且集群内的其它pod访问 db-service时，Kubernetes DNS服务器将返回带有 CNAME 记录的 test.database.aws-cn-norheast1b.com。


kind: Service
apiVersion: v1
metadata:
  name: db-service
spec:
  type: ExternalName
  externalName: test.database.aws-cn-northeast1b.com
  ports:
  - port: 80



Headless Service

有时候我们不需要 load balance 转发 ，而是稳定获取 Endpoints 信息，我们可以把Cluster IP设置成 None，如下面的yaml文件所示：

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


下面是相关信息

$ k get pod -l app=nginx -o wide
NAME                     READY   STATUS    RESTARTS   AGE   IP           NODE           nginx-5848bf568d-npvb7   1/1     Running   0          24h   10.244.2.4   minikube-m03   nginx-5848bf568d-w9dtj   1/1     Running   0          24h   10.244.1.4   minikube-m02  


$ k get endpoints nginx-headless
NAME             ENDPOINTS                     AGE
nginx-headless   10.244.1.4:80,10.244.2.4:80   49s


当访问 headless service 时不需要走 kube-proxy 转发，直接选择其中的 Pod IP 进行访问即可。在部署有状态应用经常会用到 headless service 以减少节点之间的通信时间。


Kube-proxy 工作模式

Service 到 Pod 的转发依赖 iptable/ipvs 的规则，Service 相关的 iptables 规则都在 nat 表里，可以通过检查iptable或者ipvs、nat 查看。


$ sudo ipvsadm -Ln|grep 10.107.140.247 -A3
TCP  10.107.140.247:80 rr
  -> 10.244.2.28:8080             Masq    1      0          0 
$ sudo  iptables-save -t nat



Service 转发请求到 Pod 是基于 kube-proxy 组件实现的，其有三种实现模式

Userspace 模式
Iptables 模式
Ipvs 模式

UserSpace 模式

该模式下，对于 Service 的访问会通过 iptables 转到 kube-proxy，然后转发到对应的 Pod 中。


这种模式的转发是在用户空间的 kube-proxy 程序中进行到，涉及到用户态和内核态的转换，因此性能较低，已经不再使用。

iptables 模式

该模式下， kube-proxy 仅负责设置 iptables 转发规则，对于 Service 的访问通过 iptables 规则做 NAT 地址转换，最终随机访问到某个 Pod。该方式避免了用户态到内核态的转换，提升了性能和可靠性。



kube-proxy 在 iptables 模式下运行时，如果所选的第一个 Pod 没有响应， 则连接失败。这与 userspace 模式不同，在 userspace 模式下，kube-proxy 如果检测到第一个 Pod 连接失败，其会自动选择其他 Pod 重试。可以通过设置 readiness 就绪探针，保证只有正常使用 Pod 可以作为 endpoint 使用，保证 iptables 模式下的 kube-proxy 能访问的都是正常的 Pod，从而避免将流量通过 kube-proxy 发送到已经发生故障的 Pod 中。

下面看一个具体例子，我们创建一个 Service 代理三个 Nginx Pod。

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




创建 Service 后可以通过 sudo iptables -L -t nat 或者  sudo  iptables-save -t nat 命令查看 iptables 规则，刚才我们创建的 Service ，其 iptables 规则如下：

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


从上面的转发规则可以看出，Service 的 ClusterIP 本质上 iptable 规则中的一个 IP ，在 Node 中是没有对应的网络设备的，因此在 Node 上 ping 该 IP 是 ping 不通的。
ipvs 模式

根据 K8s 官方博客 IPVS-Based In-Cluster Load Balancing Deep Dive 介绍，iptables 本质还是为了防火墙目的而设计的，基于内核规则列表工作。这使得随着 K8s 集群的增大，kube-proxy 成为了 K8s 继续扩展的瓶颈，因为节点、Pod、Service 越多，iptables 规则也就越多，在做消息转发时的效率也就越低，因此又出现了 ipvs 模式来解决扩展性的问题。

IPVS（IP Virtual Server）是 Linux 内核中专门用来做负载均衡的工具，其底层也是基于 netfilter 实现的，使用 Hash Table 作为基础数据结构。



kube-proxy 会监视 Kubernetes 服务和端点，调用 netlink 接口相应地创建 IPVS 规则， 并定期将 IPVS 规则与 Kubernetes 服务和端点同步。 该控制循环可确保IPVS 状态与所需状态匹配。访问服务时，IPVS 默认采用轮询的方式将流量定向到后端Pod之一。

与 iptables 模式下的 kube-proxy 相比，IPVS 模式下的 kube-proxy 重定向通信的延迟要短，并且在同步代理规则时具有更好的性能。 与其他代理模式相比，IPVS 模式还支持更高的网络流量吞吐量。



目前 Kubernetes 默认使用的是 iptables 模式，可以通过修改配置使用 ipvs 模式，

修改 kube-proxy 配置
$ kubectl edit configmap kube-proxy -n kube-system
// change mode from "" to ipvs
mode: ipvs


还是以我们上面的 Service 为例，使用 ipvs 模式后，Service IP、Pod IP 以及 ipset 、iptables 如下：
 
Service 与 Pod IP
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

 

IPSet & iptables

IPSet 包含了所有 Service 的 IP 并且只需要一条 iptables 规则即可

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




只有一条 iptables ，表示在 KUBE-CLUSTER-IP 集合中的 IP 包全部接收。

$ sudo iptables -L -t nat
Chain KUBE-SERVICES (2 references)
target     prot opt source               destination
KUBE-MARK-MASQ  all  -- !192.168.0.0/16       anywhere             /* Kubernetes service cluster ip + port for masquerade purpose */ match-set KUBE-CLUSTER-IP dst,dst
KUBE-NODE-PORT  all  --  anywhere             anywhere             ADDRTYPE match dst-type LOCAL
ACCEPT     all  --  anywhere             anywhere             match-set KUBE-CLUSTER-IP dst,dst


IP Virtual Server 与 IPVS 规则

IPVS 会创建一个虚拟网络设备 kube-ipvs0 ，所有 Service 的 IP 会加到该设备上，因此经过 iptables 过滤通过的包会发到该设备，然后基于 IPVS 规则进行转发。
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




性能对比

下图是 iptables 与 ipvs 模式的性能对比，可以看到在 10000 节点的集群上，ipvs 模式比 iptables 模式性能几乎高一倍。因此在大规模集群中一般都会推荐使用 IPVS 模式。



Ingress

在 Kubernetes 中部署的服务如果想被外部访问，主要有三种方式：

NodePort Service：在每个节点上开端口供外部访问。

LoadBanlacer Service：使用云厂商提供的负载均衡对外提供服务。


Ingress：Kubernetes 提供的七层流量转发方案。

图片来自 https://matthewpalmer.net/kubernetes-app-developer/articles/kubernetes-ingress-guide-nginx-example.html


Ingress 就是“服务的服务”，本质上就是对反向代理的抽象。Ingress 的使用需要 Ingress Controller，可以安装标准的 Nginx Ingress Controller，相当于在 Kubernetes 里装一个 Nginx 为业务服务做反向代理负。

Ingress 示例如下：
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


Host: 配置的 host 信息，如果为空表示可以接收任何请求流量。如果设置，比如上面规则设置了 "foo.bar.com" 则 Ingress 规则只作用于请求 "foo.bar.com" 的请求。
pathType: 必填字段，表示路径匹配的类型。
Exact：精确匹配 URL 路径，且区分大小写。
Prefix：基于以 / 分隔的 URL 路径前缀匹配。匹配区分大小写，并且对路径中的元素逐个完成。 路径元素指的是由 / 分隔符分隔的路径中的标签列表。 如果每个 p 都是请求路径 p 的元素前缀，则请求与路径 p 匹配。




Ingress 创建后相当于在 Nginx Ingress Controller 里面创建配置，和我们日常使用 Nginx 添加配置没有区别。

	
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



fanout

所谓 fanout 指的是在同一个域名下，基于 HTTP URL 匹配将请求发送给不同的服务。



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


常用注解

rewrite-target

用来重定向 URL

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

 
在此入口定义中，需要在 path 用正则表达式匹配字符，匹配的值会赋值给 $1、$2…$n 等占位符。在上面的表达式中 (.*) 捕获的任何字符都将分配给占位符$2，然后将其用作 rewrite-target 注释中的参数来修改 URL。

例如，上面的入口定义将导致以下重写：
rewrite.bar.com/something 重写为 rewrite.bar.com/
rewrite.bar.com/something/ 重写为 rewrite.bar.com/
rewrite.bar.com/something/new 重写为 rewrite.bar.com/new

App Root

重定向时指定对于在 “/” 路径下的请求重定向其根路径为注解的值。示例如下，注解值为 /app1
则请求 URL 由原来的的 / 重定向为了 /app1。
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


检查
$ curl -I -k http://approot.bar.com/
HTTP/1.1 302 Moved Temporarily
Server: nginx/1.11.10
Date: Mon, 13 Mar 2017 14:57:15 GMT
Content-Type: text/html
Content-Length: 162
Location: http://stickyingress.example.com/app1
Connection: keep-alive



上传文件限制

外部通过 Nginx 上传文件时会有上传大小限制，在 Nginx Ingress Controller 中该限制默认是 8M，由 proxy-body-size 注解控制：

nginx.ingress.kubernetes.io/proxy-body-size: 8m


可以在创建 Ingress 时设置

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





启用TLS

部署好 NGINX Ingress Controller 后，它会在 Kubernetes 中开启 NodePort 类型的服务，

$ kubectl get svc -n ingress-nginx
NAME                                 TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)                      AGE
ingress-nginx-controller             NodePort    10.107.127.108   <none>        80:32556/TCP,443:30692/TCP   25d
ingress-nginx-controller-admission   ClusterIP   10.111.3.4       <none>        443/TCP                      25d


当我们从外部访问该端口时请求就会根据 Ingress 规则转发到对应的服务中。为了数据安全，外部到服务的请求基本上都需要进行 HTTPS 加密，和我们在没用 Kubernetes 时需要在主机上配置 Nginx 的 HTTPS 一样，我们也需要让我们的 Ingress 支持 HTTPS 。





以自签名证书为例，配置 Ingress 支持 HTTPS 分三步：

生成证书

$ openssl req -x509 -sha256 -nodes -days 365 -newkey rsa:2048 \
      -keyout tls.key -out tls.crt -subj "/CN=foo.bar.com/O=httpsvc"


创建 TLS 类型的 secret 

$ kubectl create secret tls tls-secret --key tls.key --cert tls.crt
secret "tls-secret" created


创建 ingress 并设置 TLS 

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



这样在请求 foo.bar.com 时就可以使用 HTTPS 请求了。


$ kubectl get ing ingress-tls
NAME          CLASS    HOSTS         ADDRESS        PORTS     AGE
ingress-tls   <none>   foo.bar.com   192.168.64.7   80, 443   38s


$ kubectl get ing ingress-tls | grep -v NAME | awk '{print $4, $3}'
192.168.64.7 foo.bar.com

编辑 /etc/hosts ，加入 192.168.64.7 foo.bar.com

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

 
Sticky Session


apiVersion: networking.k8s.io/v1beta1
kind: Ingress
metadata:
  name: nginx-test
  annotations:
    nginx.ingress.kubernetes.io/affinity: "cookie"
    nginx.ingress.kubernetes.io/session-cookie-name: "route"
    nginx.ingress.kubernetes.io/session-cookie-expires: "172800"
    nginx.ingress.kubernetes.io/session-cookie-max-age: "172800"

spec:
  rules:
  - host: stickyingress.example.com
    http:
      paths:
      - backend:
          serviceName: http-svc
          servicePort: 80
        path: /



$ curl -I http://stickyingress.example.com
HTTP/1.1 200 OK
Server: nginx/1.11.9
Date: Fri, 10 Feb 2017 14:11:12 GMT
Content-Type: text/html
Content-Length: 612
Connection: keep-alive
Set-Cookie: INGRESSCOOKIE=a9907b79b248140b56bb13723f72b67697baac3d; Expires=Sun, 12-Feb-17 14:11:12 GMT; Max-Age=172800; Path=/; HttpOnly
Last-Modified: Tue, 24 Jan 2017 14:02:19 GMT
ETag: "58875e6b-264"
Accept-Ranges: bytes


可以看到响应中包含Set-Cookie带有我们定义的设置的标头。该cookie由NGINX创建，它包含一个随机生成的密钥，该密钥与用于该请求的上游相对应（使用一致的哈希选择），并具有一个Expires指令。如果用户更改了该cookie，NGINX将创建一个新的cookie，并将用户重定向到另一个上游。



DNS for Service 



Kubernetes 中 Service 的虚拟 IP、Port 也是会发生变化的，我们不能使用这种变化的 IP 与端口作为访问入口。K8S 内部提供了 DNS 服务，为 Service 提供域名，只要 Service 名不变，其域名就不会变。

Kubernetes 目前使用 CoreDNS 来实现 DNS 功能，其包含一个内存态的 DNS，其本质也是一个 控制器。CoreDNS 监听 Service、Endpoints 的变化并配置更新 DNS 记录，Pod 在解析域名时会从 CoreDNS 中查询到 IP 地址。


普通 Service

对于 ClusterIP / NodePort / LoadBalancer 类型的 Service，Kuberetes 会创建 FQDN 格式为  $svcname.$namespace.svc.$clusterdomain 的 A/AAAA（域名到 IP） 记录和 PRT（IP到域名） 记录。

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





Headless Service

对于无头服务，没有 ClusterIP，Kubernetes 会对 Service 创建 A 记录，但返回的所有 Pod 的 IP。
~  nslookup kubia-headless.default
Server:		10.96.0.10
Address:	10.96.0.10#53

Name:	kubia-headless.default.svc.cluster.local
Address: 10.44.0.6
Name:	kubia-headless.default.svc.cluster.local
Address: 10.44.0.5
Name:	kubia-headless.default.svc.cluster.local
Address: 10.44.0.3


Pod 

对于 Pod 会创建基于地址的 DNS 记录，格式为 pod-ip.svc-name.namespace-name.svc.cluster.local

$ kubectl get pods -o wide
NAME                      READY   STATUS        RESTARTS   AGE     IP           NODE            NOMINATED NODE   READINESS GATES

kubia-5bb46d6998-jtpc9    1/1     Running       0          28h     10.44.0.3


bash-5.1# nslookup 10.44.0.3
3.0.44.10.in-addr.arpa	name = 10-44-0-3.kubia-headless.default.svc.cluster.local.
3.0.44.10.in-addr.arpa	name = 10-44-0-3.kubia.default.svc.cluster.local.


配置管理

ConfigMap


ConfigMap 是一种 API 对象，用来将非机密性的数据保存到健值对中。使用时可以用作环境变量、命令行参数或者存储卷中的配置文件。ConfigMap 可以让配置信息和容器镜像解耦，便于应用配置的修改。每次应用需要修改配置时，只需要修改 ConfigMap 然后按需重启应用 Pod 即可，不用像修改代码那样还需要重新编译打包、制作镜像等操作。

Kubernetes 支持基于字面量、文件、目录等方式创建 ConfigMap，下面是基于字面量是一个示例

kubectl create configmap special-config --from-literal=special.how=very --from-literal=special.type=charm

$ kubectl get configmaps special-config -o yaml
apiVersion: v1
kind: ConfigMap
metadata:
  creationTimestamp: 2016-02-18T19:14:38Z
  name: special-config
  namespace: default
  resourceVersion: "651"
  selfLink: /api/v1/namespaces/default/configmaps/special-config
  uid: dadce046-d673-11e5-8cd0-68f728db1985
data:
  special.how: very
  special.type: charm


ConfigMap 创建后可以在可以作为卷直接挂载到 Pod ，也可以用来声明环境变量：

作为环境变量使用。可以引入指定的键值对作为环境变量，也可以引入所有的键值对作为环境变量。

spec:
  containers:
    - name: test-container
      image: k8s.gcr.io/busybox
      command: [ "/bin/sh", "-c", "env" ]
      env:
        - name: SPECIAL_LEVEL_KEY
          valueFrom:
            configMapKeyRef:
              name: special-config
              key: special.how
      envFrom:
      - configMapRef:
          name: special-config
  restartPolicy: Never



直接挂载卷使用

apiVersion: v1
kind: Pod
metadata:
  name: dapi-test-pod
spec:
  containers:
    - name: test-container
      image: k8s.gcr.io/busybox
      command: [ "/bin/sh", "-c", "ls /etc/config/" ]
      volumeMounts:
      - name: config-volume
        mountPath: /etc/config
  volumes:
    - name: config-volume
      configMap:
        name: special-config
  restartPolicy: Never










Secret

ConfigMap 一般用来管理与存储普通配置，而Secret 是用来管理和保存敏感的信息，例如密码，OAuth 令牌，或者是ssh 的密钥等。使用Secret来保存这些信息会比动态地添加到Pod 定义或者是使用ConfigMap更加具备安全性和灵活性。

和 ConfigMap 一样，Secret 也支持基于字面量、文件等方式创建，然后挂载进 Pod 中。
在创建 Secret 时 Kubernetes 提供了不同的类型：

$ kubectl create secret
Create a secret using specified subcommand.

Available Commands:
  docker-registry Create a secret for use with a Docker registry
  generic         Create a secret from a local file, directory, or literal value
  tls             Create a TLS secret


Generic 通用类型，可以基于文件、字面量、目录创建。
tls 用来创建 TLS 加密用的 Secret，需要指定 key 和证书，示例参考我们在 Ingress 启用 TLS
docker-registry: 创建访问私有镜像仓库使用的 Secret，可以将访问镜像仓库所需要的认证信息封装进 Secret。然后当 Pod 中的镜像需要从私有镜像仓库拉取时就可以使用该 Secret 了。 

$ kubectl create secret docker-registry regcred --docker-server=<your-registry-server> --docker-username=<your-name> --docker-password=<your-pword> --docker-email=<your-email>

apiVersion: v1
kind: Pod
metadata:
  name: private-reg
spec:
  containers:
  - name: private-reg-container
    image: <your-private-image>
  imagePullSecrets:
  - name: regcred











apiVersion: v1
kind: Pod
metadata:
  name: mypod
spec:
  containers:
  - name: mypod
    image: redis
    volumeMounts:
    - name: foo
      mountPath: "/etc/foo"
  volumes:
  - name: foo
    secret:
      secretName: mysecret
      defaultMode: 0400


对于普通的 Secret，可以像 ConfigMap 作为环境环境变量或者卷在 Pod 中使用。

apiVersion: v1
kind: Pod
metadata:
  name: mypod
spec:
  containers:
  - name: mypod
    image: redis
    volumeMounts:
    - name: foo
      mountPath: "/etc/foo"
  volumes:
  - name: foo
    secret:
      secretName: mysecret
      defaultMode: 0400

Secret 中存储的值都是经过 base64 编码后的值

$ kubectl create secret generic prod-db-secret \
  --from-literal=username=produser \
  --from-literal=password=Y4nys7f11
secret/prod-db-secret created
username:  8 bytes

$ kubectl get secrets prod-db-secret -o yaml
apiVersion: v1
data:
  password: WTRueXM3ZjEx
  username: cHJvZHVzZXI=
kind: Secret
metadata:
  name: prod-db-secret
  namespace: default
type: Opaque

$ echo "WTRueXM3ZjEx" | base64 -d
Y4nys7f11%

$ echo "cHJvZHVzZXI=" | base64 -d
produser%


因此我们只要拿到 Secret 是可以通过 base64 解码获取到实际敏感数据的值的。因此 Secret 本身提供的安全性是有限的，更多的是围绕 Secret 的安全实践。比如

避免将敏感数据直接写到代码仓库，因此抽取到 Secret。另外只有某节点的 Pod 用到 Secret 时其才会被发送到对应节点，可以设置 Secret 写到内存而不是磁盘，这样 Pod 停止后Secret 数据也会被删除。
Kubernetes 组件与 api-server 之间的通信一般都是受 TLS 保护的，因此 Secret 在组件之间传输时也是安全的。
Pod 之间无法共享 Secret，同时可以在 Pod 级别构建安全分区来保证只有需要的容器才能访问到 Secret。
Kubernetes 存储

操作系统中有 Volume 和 Mount 的概念。

Volume: 表示物理存储的逻辑抽象
Mount：将外部存储挂载到系统、容器中。

为了解决容器的数据存储，Kubernetes 也引入 Volume 的概念。Kubernetes 将 Volume 存储分为了普通的非持久化 Volume 和持久化的 PersistentVolume 两类。
Volumes

普通非持久化 Volume 的目的主要是为了在 Pod 的容器之间共享存储资源，防止 Kubelet 重启容器时造成数据丢失，因此它们的生命周期与 Pod 相同。Pod 中容器的重启不影响 Volume 中的数据存储，只有在 Pod 销毁后 Volume 中的数据才会被删除。

Kubernetes 默认支持许多种类型的卷，比如 emptyDir，hostPath， configMap、secret downwardAPI 等作为 Volume 挂载到容器中。

下面看两个常用的类型：

emptyDir类型： 临时的卷类型，当一个Pod被分配到一个 Node 时候被创建，生命周期只维持在该 Pod 依然运行在该节点上的这段时间。在两个容器共享一些临时文件的场景下，可以使用这种类型。

apiVersion: v1
kind: Pod
metadata:
  name: test-pd
spec:
  containers:
  - image: k8s.gcr.io/test-webserver
    name: test-container
    volumeMounts:
    - mountPath: /cache
      name: cache-volume
  volumes:
  - name: cache-volume
    emptyDir: {}



hostPath类型：该卷类型会将宿主节点文件系统上的文件或者目录映射到Pod中。当Pod 需要访问一些宿主节点的数据的场景下可以使用。 Pod 被删除后 Host 上的文件依然存在，当新的 Pod 被调度到节点上挂载 Volume 后会看到对应 path 中的文件。

apiVersion: v1
kind: Pod
metadata:
  name: test-webserver
spec:
  containers:
  - name: test-webserver
    image: k8s.gcr.io/test-webserver:latest
    volumeMounts:
    - mountPath: /var/local/aaa
      name: mydir
    - mountPath: /var/local/aaa/1.txt
      name: myfile
  volumes:
  - name: mydir
    hostPath:
      # Ensure the file directory is created.
      path: /var/local/aaa
      type: DirectoryOrCreate
  - name: myfile
    hostPath:
      path: /var/local/aaa/1.txt
      type: FileOrCreate



图片来自 《Kubernetes In Action》


PV & PVC

除了临时性的存储以及共享文件外，我们还需要持久化的存储，无论 Pod 如何被调度，数据始终是一致的。比如我们在 Kubernetes 部署 MySQL 、MongoDB 等服务，任何时候看到的数据都应该是一样的。另外存储媒介是一个底层的实现，Pod 不应该关心具体的底层存储类型。因此为了实现持久化存储并使得底层存储与 Pod 解耦，Kuberetes 提出了 Persistent Volume（持久卷） 的概念，而 Pod 对存储资源的需求则被抽象为了 PersistentVolumeClaim（持久卷声明）。
Persistent Volume：由集群管理员提供的集群存储声明，和普通的 Volume 没有本质的不同，只是其生命周期独立于任何使用 PV 的 Pod。
PersistentVolumeClaim：由用户（开发人员）提供的存储请求声明。

这样在实际使用时，集群可以预先创建一批 PV 提供存储，而用户则只需要创建 PVC 提出自己的存储需求就行了。Kubernetes 会自动将 PVC 与 PV 进行绑定，在将 PVC 挂载到 Pod 中去，就可以进行数据的读写了。





图片来自《Kubernetes in Action》

下面是 PV 和 PVC 是使用示例：

创建 PV

下面是分别使用本地磁盘和 NFS 作为底层存储的 PV 示例：

apiVersion: v1
kind: PersistentVolume
metadata:
  name: task-pv-volume
  labels:
    type: local
spec:
  storageClassName: manual
  volumeMode: Filesystem
  persistentVolumeReclaimPolicy: Recycle
  capacity:
    storage: 10Gi
  accessModes:
    - ReadWriteOnce
  hostPath:
    path: "/mnt/data"


apiVersion: v1
kind: PersistentVolume
metadata:
 name: nfs
spec:
 capacity:
   storage: 1Mi
 accessModes:
   - ReadWriteMany
 nfs:
   server: nfs-server.default.svc.cluster.local
   path: "/"
 mountOptions:
   - nfsvers=4.2



accessModes: 访问权限
ReadWriteOnce 允许单个节点对 PV 进行读写操作
ReadOnlyMany 允许多个节点执行只读操作
ReadWriteMany 允许多个节点进行读写操作
capacity: 持久卷的容量大小
volumeMode 持久卷moshi ，默认为 Filesystem，表示在挂载进 Pod 时如果卷存储设备为空会创建完整的文件系统目录。从 1.18 开始支持 Block 选项，表示卷作为原始的块设备，不会创建文件系统目录。此时 Pod 应用需要知道如何处理原始块设备。
persistentVolumeReclaimPolicy：回收策略
Retain：PVC 删除后 PV 保留数据，PV 对其他 PVC 不可用。
Delete： PVC 删除后 PV 删除 PV 以及 PV 存储的数据。
Recycle：删除数据并对其他 PVC 可用，已弃用。



创建 PVC
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: task-pv-claim
spec:
  storageClassName: manual
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 3Gi


PVC 可以通过 storageClassName、accessModes、volumeMode 以及存储容量大小来选择符合条件的 PV，同时还可以通过标签和 Key 来选择执行 PV 的选择范围。

apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: myclaim
spec:
  accessModes:
    - ReadWriteOnce
  volumeMode: Filesystem
  resources:
    requests:
      storage: 8Gi
  storageClassName: slow
  selector:
    matchLabels:
      release: "stable"
    matchExpressions:
      - {key: environment, operator: In, values: [dev]}




Pod 挂载 PVC 作为存储
apiVersion: v1
kind: Pod
metadata:
  name: task-pv-pod
spec:
  volumes:
    - name: task-pv-storage
      persistentVolumeClaim:
        claimName: task-pv-claim
  containers:
    - name: task-pv-container
      image: nginx
      ports:
        - containerPort: 80
          name: "http-server"
      volumeMounts:
        - mountPath: "/usr/share/nginx/html"
          name: task-pv-storage



下面是 PV 是使用过程中的生命周期：

阶段


Provisioning
卷资源在供给阶段，有两种方式，管理员手动生成或者系统根据PVC需求动态分配
Binding
通过手动分配或者动态分配的方式，系统为一个PCV找到和合适的实际PV，则将两者双向绑定起来。
Using
用户实际使用资源时的阶段。也就是在 Pod中像 volume一样使用pvc。
Protecting
1. 有活跃的 Pod 正在使用卷时，如果删除PVC会进入这个阶段，此时会延迟删除 PVC 只到没有使用了。
2. 如果删除了有绑定 PVC 的PV，也会进入该阶段，会等到 PV 和PVC 解开绑定后，才会删除PVC。
Reclaiming
当用户不再需要 PV 资源了，删除PVC后，卷会进入这个阶段，根据定义PV会有两种处理方式：
保留，用来维持卷，提供时间给到手动清理上面的数据
删除， PV会被直接删除，包括里面的数据
回收，（回收模式已经不再推荐使用）


Storage Class

上面提到 PV 是由集群管理员预先创建的，属于静态处理。这里会有两个问题：

创建不及时：集群管理员无法及时预知所有的存储需求，一旦 PVC 创建 Pod 部署后没有 PV 可用，会导致 Pod 运行出错。
需求不匹配：比如管理员预先创建了 5G 大小的 PV，但此时 Pod 的 PVC 只需要 3G，此时如果绑定会造成 2G 的空间浪费。

于此同时，对于 PV 中数据的处理方式也不够灵活，除了保留、删除外其实还可以有更细化的处理，比如删除到回收站，保留一段时间后在删除，这些可能都需要管理员对 PV 的底层存储进行手动操作。

因此为了及时、灵活的分配与管理存储资源，Kubernetes 引入了动态存储分配的方式，其提供了 StorageClass 的资源对象，相当于 PV 的模板，该对象基于一个存储的提供者（Provisioner），比如 NFS，AWS S3 等，当用户需要存储资源时，只需要创建对应的 PVC 并指定 StorageClass，Pod 在创建完成需要使用存储资源时，StorageClass 就会根据需要自动的创建 PV 并绑定 PVC。


图片来自《Kubernetes in Action》

下面是一个使用 aws-ebs 作为 provisioner 的示例：

apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: standard
provisioner: kubernetes.io/aws-ebs
parameters:
  type: gp2
reclaimPolicy: Retain
allowVolumeExpansion: true
mountOptions:
  - debug
volumeBindingMode: Immediate



这样在创建 PVC 只需要指定该 StorageClass 就可以了

​apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: task-pv-claim
spec:
  storageClassName: standard
  accessModes:
    - ReadWriteOnce
  resources:
    requests: 
storage: 3Gi​


另外可以根据需要实现创建不同的 StorageClass，比如下面使用 gce-pd 的 provisioner  创建的两个 StorageClass：使用标准存储的 slow 和使用 SSD 存储的 fast。在创建 PVC 时就可以根据性能需要选择合适的 StorageClass。这样对存储资源的分配和使用就实现了按需灵活分配的目的，除此之外，还可以在 provisioner 里添加处理代码来管理数据的保存、删除，实现更加灵活的管理。Kubernetes官方已经明确建议废弃Recycle策略，如有这类需求，应由Dynamic Provisioning去实现。

apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: slow
provisioner: kubernetes.io/gce-pd
parameters:
  type: pd-standard
---
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: fast
provisioner: kubernetes.io/gce-pd
parameters:
  type: pd-ssd
--
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: claim1
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: fast
  resources:
    requests:
      storage: 30Gi



PV 挂载过程

PV 持久化存储大多数时都是依赖一个远程存储服务，比如 NFS、AWS S3 等远程存储。类比操作系统中的引入存储设备的流程，首先我们需要准备好存储设备，然后将其作为块设备附加到系统中，最后挂载到文件系统后，就可以进行读写操作了。Kubernetes 中的操作也是类似的流程：

准备操作（Provision）：由各个存储插件准备好自己的存储空间，相当于给 Kuberetes 准备一块磁盘。
附加操作（Attach）: Pod 调度完成后，在对应宿主机的添加存储块，将远程存储添加到本地存储，相当于将磁盘添加到 Kubernetes。。比如使用 GCE Disk 服务，此时需要 kubelet 调用 GCE Disk 的 API 将远程存储添加到宿主机。
挂载操作（Mount）：存储块添加完成后，Kuberetes 会格式化目录并将块挂载到宿主机的挂载点，这样在 Pod 中挂载卷时，写入到对应目录的数据就会写到远程存储中去。卷目录一般是 ​ /var/lib/kubelet/pods/<Pod的ID>/volumes/kubernetes.io~<Volume类型>/<Volume名字>​。

比如我们 EaseMesh 中 Control Plane 的 Pod 有三个卷，其中一个为持久卷，则在 Pod 的宿主机上述目录下就能找到对应卷的目录和数据。

node4:➜  ~  |>kubectl get pods -n easemesh -o wide                                                                                                                                                                                        [~]
NAME                                                 READY   STATUS    RESTARTS   AGE     IP              NODE    NOMINATED NODE   READINESS GATES
easemesh-control-plane-1                             1/1     Running   0          2d19h   10.233.67.90    node4   <none>           <none>


node4:➜  ~  |>kubectl describe pod easemesh-control-plane-1 -n easemesh                                                                                                                                                                   [~]
Name:         easemesh-control-plane-1
...
Volumes:
  easegress-control-plane-pv:
    Type:       PersistentVolumeClaim (a reference to a PersistentVolumeClaim in the same namespace)
    ClaimName:  easegress-control-plane-pv-easemesh-control-plane-1
    ReadOnly:   false
  easemesh-cluster-cm:
    Type:      ConfigMap (a volume populated by a ConfigMap)
    Name:      easemesh-cluster-cm
    Optional:  false
  default-token-tcdjd:
    Type:        Secret (a volume populated by a Secret)
    SecretName:  default-token-tcdjd
    Optional:    false


现在基于 PodId 和 VolumeName 查看下宿主机的挂载目录，就会有对应的文件

node4:➜  ~  |>sudo ls -l  /var/lib/kubelet/pods/1e2cfc60-493e-4bd3-804b-695e7c0af24f/volumes/                                                                                                                                             [~]
total 12
drwxr-xr-x 3 root root 4096 Dec  8 12:45 kubernetes.io~configmap
drwxr-x--- 3 root root 4096 Dec  8 12:45 kubernetes.io~local-volume
drwxr-xr-x 3 root root 4096 Dec  8 12:45 kubernetes.io~secret



node4:➜  ~  |>sudo ls -l  /var/lib/kubelet/pods/1e2cfc60-493e-4bd3-804b-695e7c0af24f/volumes/kubernetes.io~configmap                                                                                                                      [~]
total 4
drwxrwxrwx 3 root root 4096 Dec  8 12:45 easemesh-cluster-cm


node4:➜  ~  |>sudo ls -l  /var/lib/kubelet/pods/1e2cfc60-493e-4bd3-804b-695e7c0af24f/volumes/kubernetes.io~local-volume                                                                                                                   [~]
total 4
drwxr-xr-x 5 ubuntu ubuntu 4096 Jun 23 06:59 easemesh-storage-pv-3

node4:➜  ~  |>sudo ls -l  /var/lib/kubelet/pods/1e2cfc60-493e-4bd3-804b-695e7c0af24f/volumes/kubernetes.io~local-volume/easemesh-storage-pv-3                                                                                             [~]
total 12
drwx------ 3 root root 4096 Dec  8 12:45 data
drwx------ 2 root root 4096 Jun 23 07:00 log
drwx------ 2 root root 4096 Dec  8 12:45 member



可以看到在持久卷对应的目录下有数据、日志等目录，而这些就是我们持久卷存储中的数据。我们用的宿主机磁盘的 /volumes/easemesh-storage 作为存储，因此在该目录下也会看到相同的文件：

node4:➜  /volumes  |>kubectl describe  pv easemesh-storage-pv-3                                                                                                                                                                    [/volumes]
Name:              easemesh-storage-pv-3
Labels:            <none>
Annotations:       pv.kubernetes.io/bound-by-controller: yes
Finalizers:        [kubernetes.io/pv-protection]
StorageClass:      easemesh-storage
Status:            Bound
Claim:             easemesh/easegress-control-plane-pv-easemesh-control-plane-1
Reclaim Policy:    Delete
Access Modes:      RWO
VolumeMode:        Filesystem
Capacity:          4Gi
Node Affinity:
  Required Terms:
    Term 0:        kubernetes.io/hostname in [node4]
Message:
Source:
    Type:  LocalVolume (a persistent volume backed by local storage on a node)
    Path:  /volumes/easemesh-storage
Events:    <none>

node4:➜  /volumes  |>ls -l /volumes/easemesh-storage                                                                                                                                                                               [/volumes]
total 12
drwx------ 3 root root 4096 Dec  8 12:45 data
drwx------ 2 root root 4096 Jun 23 07:00 log
drwx------ 2 root root 4096 Dec  8 12:45 member



以上步骤是由 Kubernetes 的 PersistentVolume Controller、Attach/Detach Controller 控制器以及 Kubelet 中的 Volume Manager 来实现。

PersistentVolume Controller：执行控制循环，保证所有绑定的 PV 都是可用的以及所有的 PVC 都能与合适的 PV 绑定。在执行拟合的过程中，Controller 会根据需要调用存储驱动插件的 Provision/Delete 接口进行操作。
Attach/Detach Controller：在 PV 与 PVC 绑定， Pod 被调度到某个节点后，执行 Attach 操作，将远程存储添加到宿主机 Attact 都对应的节点。
Volume Manager: 这是一个单独的 gorouting，独立于 kubelet 的主循环，执行具体的 Mount 操作，格式化磁盘并将设备挂载到宿主机目录。

而上述操作完成后，在 Pod 部署创建容器时，Kubelet 就会在启动容器时将我们已挂载的宿主机目录在挂载进容器中去，相当于执行如下命令：

$ docker run -v /var/lib/kubelet/pods/<Pod的ID>/volumes/kubernetes.io~<Volume类型>/<Volume名字>:/<容器内的目标目录> ......


以上就是 PV 挂载过程的简要概述，当然有挂载就有卸载的操作，以上三步分别对应有 Delete、Detach、Unmount 的相反操作，这里就不做赘述了。

CSI 插件

除了内置的插件，Kubernetes 还提供了扩展机制来实现自己的存储插件，早期的扩展方式是通过 FlexVolume 实现，在 1.23 版本已经被废弃，目前主要是通过 CSI（Container Storage Interface，容器存储接口）来实现，当前 Kubernetes 中内置的存储插件也在进行 CSI 改造，最终会将内置的插件从 Kubernetes 核心中移植出去。

上面提到的 PV 挂载过程，在没有 CSI 之前其 Provision、Attach、Mount 等操作最终都是通过 Kubernetes 内置的 Volume 插件完成的。


图片来自
​https://medium.com/google-cloud/understanding-the-container-storage-interface-csi-ddbeb966a3b​

Kubernetes 早期内置了非常多的 Volume 插件，为其广泛推广发挥了重要的作用，但也随之引入了如下问题：

更新不及时：插件内置在 Kubernetes 中，导致第三方存储厂商的发布节奏必须和 Kubernetes 保持一致。。
代码不可控：很多插件都是由第三方存储厂商提供的可执行命令，将其和 Kubernetes 核心代码放一起会引发可靠性和安全性问题。

因此 Kubernetes 从 1.14 版本开始了内置插件的外迁工作。和之前看到 CNI 网络接口旨在提供移一致的容器网络操作一样，CSI 则是对所有的容器编排系统，比如 Kubernetes、Docker swarm 提供一致的存储访问接口，第三方存储插件只需要按照 CSI 实现对应的接口实现就可以满足所有系统的需要。

​​图片来自
​https://medium.com/google-cloud/understanding-the-container-storage-interface-csi-ddbeb966a3b​

使用了 CSI 插件后 Kuberetes 存储架构如下： 
图片来自​https://medium.com/google-cloud/understanding-the-container-storage-interface-csi-ddbeb966a3b​

CSI 的内容主要包括两部分：

容器系统的相关操作

比如存储插件的注册移除操作以及与存储插件通信实现各种对 Volume 的管理，比如调用第三方存储插件进行 Volume 的创建、删除、扩缩容、查询监控信息等。主要抱包含下面一些组件：
 Driver Register：将会被Node Driver Register所取代，负责注册第三方插件。插件注册进 kubelet 后就可以通过 gRPC 与插件进行通信了。
 External Provisioner：调用第三方插件的接口来完成数据卷的创建与删除操作。
 External Attacher：调用第三方插件的接口来完成数据卷的挂载和操作。
 External Resizer：调用第三方插件来完成数据卷的扩容操作。
 External Snapshotter：调用第三方插件来完成快照的创建和删除操作。
 External Health Monitor：调用第三方插件来提供度量监控数据功能。
这部分功能虽然从 Kubernetes 核心代码剥离了出来，但依然由 Kubernetes 官方维护。

第三方插件的具体操作

需要由云存储厂商实现来执行具体的操作。比如我们上面提到的 Provision、Attach、Mount 操作。这里主要包含三个服务：
CSI Identity：用于对外暴露插件的基本信息，比如插件版本号、插件所支持的CSI规范版本、是否支持存储卷创建及删除功能、是否支持存储卷挂载功能，等等。另外还可以用于检查插件的健康状态，开发者可以通过实现 Probe 接口对外提供存储的健康度量信息。
// IdentityServer is the server API for Identity service.
type IdentityServer interface {
  // return the version and name of the plugin
  GetPluginInfo(context.Context, *GetPluginInfoRequest) (*GetPluginInfoResponse, error)
  // reports whether the plugin has the ability of serving the Controller interface
  GetPluginCapabilities(context.Context, *GetPluginCapabilitiesRequest) (*GetPluginCapabilitiesResponse, error)
  // called by the CO just to check whether the plugin is running or not
  Probe(context.Context, *ProbeRequest) (*ProbeResponse, error)
}


CSI Controller：提供各种对存储系统也就是 Volume 的管理控制接口，其实就是上面提到的准备和 Attach 阶段。比如譬如准备和移除存储（Provision、Delete操作）、附加与分离存储（Attach、Detach操作）、对存储进行快照等。存储插件并不一定要实现这个接口的所有方法，对于存储本身就不支持的功能，可以在CSI Identity 服务中声明为不提供。



// ControllerServer is the server API for Controller service.
type ControllerServer interface {
  // // provisions a volume
  CreateVolume(context.Context, *CreateVolumeRequest) (*CreateVolumeResponse, error)
  ​​ 
  DeleteVolume(context.Context, *DeleteVolumeRequest) (*DeleteVolumeResponse, error)

  ControllerPublishVolume(context.Context, *ControllerPublishVolumeRequest) (*ControllerPublishVolumeResponse, error)

  ControllerUnpublishVolume(context.Context, *ControllerUnpublishVolumeRequest) (*ControllerUnpublishVolumeResponse, error)

  ValidateVolumeCapabilities(context.Context, *ValidateVolumeCapabilitiesRequest) (*ValidateVolumeCapabilitiesResponse, error)

  ​​
  ListVolumes(context.Context, *ListVolumesRequest) (*ListVolumesResponse, error)
  GetCapacity(context.Context, *GetCapacityRequest) (*GetCapacityResponse, error)
  ControllerGetCapabilities(context.Context, *ControllerGetCapabilitiesRequest) (*ControllerGetCapabilitiesResponse, error)
  CreateSnapshot(context.Context, *CreateSnapshotRequest) (*CreateSnapshotResponse, error)

  DeleteSnapshot(context.Context, *DeleteSnapshotRequest) (*DeleteSnapshotResponse, error)

  ListSnapshots(context.Context, *ListSnapshotsRequest) (*ListSnapshotsResponse, error)

  ControllerExpandVolume(context.Context, *ControllerExpandVolumeRequest) (*ControllerExpandVolumeResponse, error)
}


CSI Node 服务：Controller 中定义的功能都不是在宿主机层面操作的，这部分操作由 CSI Node 服务实现，也就是上面提到的 mount 阶段。其在集群节点层面执行具体操作，比如将 Volume 分区和格式化、将存储卷挂载到指定目录上或者将存储卷从指定目录上卸载等。



// NodeServer is the server API for Node service.
type NodeServer interface {
  // temporarily mount the volume to a staging path
  NodeStageVolume(context.Context, *NodeStageVolumeRequest) (*NodeStageVolumeResponse, error)
  
  // unmount the volume from staging path
  NodeUnstageVolume(context.Context, *NodeUnstageVolumeRequest) (*NodeUnstageVolumeResponse, error)

  // mount the volume from staging to target path
  NodePublishVolume(context.Context, *NodePublishVolumeRequest) (*NodePublishVolumeResponse, error)

  // unmount the volume from staging path
  NodeUnpublishVolume(context.Context, *NodeUnpublishVolumeRequest) (*NodeUnpublishVolumeResponse, error)

… … 
  // returns the capabilities of the Node plugin
  NodeGetCapabilities(context.Context, *NodeGetCapabilitiesRequest) (*NodeGetCapabilitiesResponse, error)

  // return the info of the node
  NodeGetInfo(context.Context, *NodeGetInfoRequest) (*NodeGetInfoResponse, error)
}

  
而对于 CSI 插件的编写，其实就是实现上面的三个接口，下面看一个比较简单的 csi-s3   插件的例子，项目目录结构如下：

csi-s3
├── cmd
│   └── s3driver
│       ├── Dockerfile
│       ├── Dockerfile.full
│       └── main.go

├── pkg
│   ├── driver
│   │   ├── controllerserver.go
│   │   ├── driver.go
│   │   ├── driver_suite_test.go
│   │   ├── driver_test.go
│   │   ├── identityserver.go
│   │   └── nodeserver.go
│   ├── mounter
│   │   ├── goofys.go
│   │   ├── mounter.go
│   │   ├── rclone.go
│   │   ├── s3backer.go
│   │   └── s3fs.go
│   └── s3
│       └── client.go


在 pkg/driver 目录下有三个文件：
 identityserver.go 代表 CSI Identity 服务，提供了 CSI 插件的基本信息，Probe 探针接口等。
controllerserver.go 代表 CSI Controller 服务，定义了操作 Volume 的相关操作。下面是 CSI  中完整的接口列表，cis-s3  只实现了其中一部分：
// s3 插件的 controller 实现
type controllerServer struct {
  *csicommon.DefaultControllerServer
}

func (cs *controllerServer) CreateVolume(ctx context.Context, req *csi.CreateVolumeRequest) (*csi.CreateVolumeResponse, error) {
// 调用 S3 api 创建存储卷
params := req.GetParameters()
capacityBytes := int64(req.GetCapacityRange().GetRequiredBytes())
mounterType := params[mounter.TypeKey]
volumeID := sanitizeVolumeID(req.GetName())
bucketName := volumeID
prefix := ""

client, err := s3.NewClientFromSecret(req.GetSecrets())

if err = client.CreateBucket(bucketName); err != nil {
  return nil, fmt.Errorf("failed to create bucket %s: %v", bucketName, err)
}

return &csi.CreateVolumeResponse{
  Volume: &csi.Volume{
     VolumeId:      volumeID,
     CapacityBytes: capacityBytes,
     VolumeContext: req.GetParameters(),
  },
}, nil


}
func (cs *controllerServer) DeleteVolume(ctx context.Context, req *csi.DeleteVolumeRequest) (*csi.DeleteVolumeResponse, error) {
...
}
...

nodeserver.go  代表CSI Node 服务，定义了在宿主机挂载 Volume 的操作，其实现了 CSI 中 NodeServer 的接口。下面是接口列表，我们提到在 Mount 阶段需要将远程存储作为块设备添加都节点并挂载到对应目录，这两步就是通过 NodeStageVolume 和 NodePublishVolume 实现的。



三个服务都是通过 gRPC 与 Kubernetes 进行通信的，下面是服务的启动代码，这里定义了 Driver 的版本信息和插件名，CSI 插件要求名字遵守 反向 DNS 格式。

var (
  vendorVersion = "v1.2.0-rc.1"
  driverName    = "ch.ctrox.csi.s3-driver"
)

// New initializes the driver
func New(nodeID string, endpoint string) (*driver, error) {
  d := csicommon.NewCSIDriver(driverName, vendorVersion, nodeID)
  if d == nil {
     glog.Fatalln("Failed to initialize CSI Driver.")
  }

  s3Driver := &driver{
     endpoint: endpoint,
     driver:   d,
  }
  return s3Driver, nil
}

func (s3 *driver) newIdentityServer(d *csicommon.CSIDriver) *identityServer {
  return &identityServer{
     DefaultIdentityServer: csicommon.NewDefaultIdentityServer(d),
  }
}

func (s3 *driver) newControllerServer(d *csicommon.CSIDriver) *controllerServer {
  return &controllerServer{
     DefaultControllerServer: csicommon.NewDefaultControllerServer(d),
  }
}

func (s3 *driver) newNodeServer(d *csicommon.CSIDriver) *nodeServer {
  return &nodeServer{
     DefaultNodeServer: csicommon.NewDefaultNodeServer(d),
  }
}

func (s3 *driver) Run() {
  ......

  // Create GRPC servers
  s3.ids = s3.newIdentityServer(s3.driver)
  s3.ns = s3.newNodeServer(s3.driver)
  s3.cs = s3.newControllerServer(s3.driver)

  s := csicommon.NewNonBlockingGRPCServer()
  s.Start(s3.endpoint, s3.ids, s3.cs, s3.ns)
  s.Wait()
}


以上是 csi-c3 的简单示例，在将该插件部署到我们的 Kuberetes 后就可以用该插件作为提供商来动态创建 PV 了。对于 CSI 的部署有一些部署原则：

CSI Node 服务需要与宿主机节点进行交互，因此需要通过 DaemonSet 在每个节点都启动插件来与 Kubelet 通信。
通过 StatefulSet 在任意节点启动一个 CSI 插件已提供 CSI Controller 服务。这样可以保证 CSI 服务的稳定性和正确性。

将 CSI 插件部署后就可以使用 StorageClass 和 PVC 了，示例如下：

部署 CSI

$ kubectl create -f provisioner.yaml
kubectl create -f attacher.yaml
kubectl create -f csi-s3.yaml
serviceaccount/csi-provisioner-sa created
clusterrole.rbac.authorization.k8s.io/external-provisioner-runner created
clusterrolebinding.rbac.authorization.k8s.io/csi-provisioner-role created
service/csi-provisioner-s3 created
statefulset.apps/csi-provisioner-s3 created
serviceaccount/csi-attacher-sa created
clusterrole.rbac.authorization.k8s.io/external-attacher-runner created
clusterrolebinding.rbac.authorization.k8s.io/csi-attacher-role created
service/csi-attacher-s3 created
statefulset.apps/csi-attacher-s3 created
serviceaccount/csi-s3 created
clusterrole.rbac.authorization.k8s.io/csi-s3 created
clusterrolebinding.rbac.authorization.k8s.io/csi-s3 created
daemonset.apps/csi-s3 created

使用 CSI 
---
kind: StorageClass
apiVersion: storage.k8s.io/v1
metadata:
  name: csi-s3
provisioner: ch.ctrox.csi.s3-driver
parameters:
  # specify which mounter to use
  # can be set to rclone, s3fs, goofys or s3backer
  mounter: rclone
  # to use an existing bucket, specify it here:
  # bucket: some-existing-bucket
  csi.storage.k8s.io/provisioner-secret-name: csi-s3-secret
  csi.storage.k8s.io/provisioner-secret-namespace: kube-system
  csi.storage.k8s.io/controller-publish-secret-name: csi-s3-secret
  csi.storage.k8s.io/controller-publish-secret-namespace: kube-system
  csi.storage.k8s.io/node-stage-secret-name: csi-s3-secret
  csi.storage.k8s.io/node-stage-secret-namespace: kube-system
  csi.storage.k8s.io/node-publish-secret-name: csi-s3-secret
  csi.storage.k8s.io/node-publish-secret-namespace: kube-system
--
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
 name: csi-s3-pvc
 namespace: default
spec:
 accessModes:
 - ReadWriteOnce
 resources:
   requests:
     storage: 5Gi
 storageClassName: csi-s3
--

apiVersion: v1
kind: Pod
metadata:
 name: csi-s3-test-nginx
 namespace: default
spec:
 containers:
  - name: csi-s3-test-nginx
    image: nginx
    volumeMounts:
      - mountPath: /var/lib/www/html
        name: webroot
 volumes:
  - name: webroot
    persistentVolumeClaim:
      claimName: csi-s3-pvc
      readOnly: false





Kubernetes 调度

所谓调度就是按照一系列的需求、规则，将 Pod 调度到合适的 Node 上。下面是 Kubernetes 提供的一些调度方式：

手动调度

Pod 的定义中有 nodeName 属性，调度器就是在选择出最合适的节点后修改 Pod 的 nodeName 来指定 Pod 的运行节点，我们可以在定义 Pod 时直接指定，示例如下，这样该 Pod 就会被调度到 node02 节点。
apiVersion: v1
kind: Pod
metadata:
  name: nginx
spec:
  containers:
  - name: nginx
    image: nginx
  # 指定节点名称
  nodeName: node02


NodeSlector

可以通过在 Node 打标签，然后使用标签选择器将 Pod 调度到对应节点。比如我们希望某些执行 IO 任务的 Pod 调度到磁盘类型为 ssd 的节点上，可以先在节点上打标签 disktype: ssd 然后使用 NodeSelector 将 Pod 调度到对应节点，示例如下：

apiVersion: v1
kind: Pod
metadata:
  name: nginx
  labels:
    env: test
spec:
  containers:
  - name: nginx
    image: nginx
    imagePullPolicy: IfNotPresent
  # 配置 nodeSelector
  nodeSelector:
    disktype: ssd


Node & Pod Affinity

nodeSelector 只能简单的根据标签是否相等来进行调度，会被逐渐弃用。现在更推荐使用拥有更强大的节点关联规则，调度更加灵活的  Node/Pod Affinty （亲和性）。
亲和性规则分为 Node Affinity  和 Pod Affinity 两种，下面是 Node Affinity （节点亲和度）的示例：
apiVersion: v1
kind: Pod
metadata:
  name: with-node-affinity
Spec:
  # 设置亲和度
  affinity:
    # 设置节点亲和度
    nodeAffinity:
      # 指定 affinity 类型
      requiredDuringSchedulingIgnoredDuringExecution:
        # 指定若干个规则
        nodeSelectorTerms:
        - matchExpressions:
          - key: kubernetes.io/e2e-az-name
            operator: In
            values:
            - e2e-az1
            - e2e-az2
      preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 1
        preference:
          matchExpressions:
          - key: another-node-label-key
            operator: In
            values:
            - another-node-label-value
  containers:
  - name: with-node-affinity
    image: k8s.gcr.io/pause:2.0

 
首先要指定 Affinity 的规则类型，主要有下面三类
已有类型
requiredDuringSchedulingIgnoredDuringExecution：表示节点只能在满足匹配规则的情况下，Pod 才会被调度上去。
preferredDuringSchedulingIgnoredDuringExecution：表示会优先将 Pod 调度到那些满足匹配规则的节点上，实在没有的话也可以调度到其他节点。
计划类型
requiredDuringSchedulingRequiredDuringExecution：这是在计划中的特性，目前还没有实际使用，和下面要讲到的 Taint 的 NoExecute 效果很像，其会影响到已经运行的 Pod。
虽然 affinity 规则类型的名字看着很长，但其语义还是很清晰的，由 类型 和 作用时期 组成。如图：

DuringScheduling 和 DuringExecution 分别表示对调度期和运行期的要求，调度期只会在部署调度新的 Pod 时生效，而运行期则会影响正在运行的 Pod。
required 表示必须满足条件；preferred 表示尽量满足，也就是说会优先将 Pod 调度到满足亲和性规则的节点上，如果找不到也可以调度到其他节点。
NodeAffinity 的 Operator 支持 In, NotIn, Exists, DoesNotExist, Gt, Lt 这几种操作，我们可以通过 NotIn、DoesNotExist 支持反亲和操作。
另外有下面几条亲和规则需要注意
如果同时指定 nodeSelector 和 nodeAffinity，则必须同时满足两个条件，才能将Pod调度到候选节点上。
如果 nodeAffinity 的某个类型关联了多个 nodeSelectorTerms，只需要满足其中之一，就可以将 Pod 调度到节点上。
如果 nodeSelectorTerms 下有多个 matchExpressions，则只有在满足所有matchExpressions的情况下，才可以将 Pod 调度到一个节点上。
最后对于 preferredDuringSchedulingIgnoredDuringExecution 还会有一个 weight 权重字段，用来在调度时结合其他条件计算 Node 的优先级，Pod 最终会调度到优先级最高的 Node 上。
除了节点亲和度，还有 Pod 亲和度、反亲和度（anti-affinity）来指定使 Pod 优先与某些 Pod 部署到一起或者不与某些 Pod 部署到一起，下面是一个官网的例子：
 
apiVersion: v1
kind: Pod
metadata:
  name: with-pod-affinity
spec:
  affinity:
    # Pod 亲和度，与标签匹配的 Pod 部署在一起
    podAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
      - labelSelector:
          matchExpressions:
          - key: security
            operator: In
            values:
            - S1
        topologyKey: topology.kubernetes.io/zone
    # 反亲和，优先部署在没有对应标签的 Pod 运行的节点上
    podAntiAffinity:
      preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchExpressions:
            - key: security
              operator: In
              values:
              - S2
          topologyKey: topology.kubernetes.io/zone
  containers:
  - name: with-pod-affinity
    image: k8s.gcr.io/pause:2.0

 
 不过在官网的建议中，Pod 亲和、反亲和的调度规则会降低集群的调度速度，因此不建议在超过数百个节点中的集群中使用。
Resource Request

在定义 Pod 时可以选择性地为每个容器设定所需要的资源数量。 最常见的可设定资源是 CPU 和内存（RAM）大小，从而使得 Pod 调度到符合资源需求的节点上，示例如下：

apiVersion: v1
kind: Pod
metadata:
  name: frontend
spec:
  containers:
  - name: app
    image: images.my-company.example/app:v4
    env:
    - name: MYSQL_ROOT_PASSWORD
      value: "password"
    resources:
      requests:
        memory: "64Mi"
        cpu: "250m"
      limits:
        memory: "128Mi"
        cpu: "500m"


这里有 requests 和 limits 两个设置项：

requests：给调度器使用，scheduler 根据该值进行调度决策，在执行调度时，会以 Pod 中所有容器的 request 值总和作为判断。
limits：资源使用配额，给 cGroups 使用，用来限制容器资源的使用。

Pod 对特定资源类型的请求/约束值是 Pod 中各容器对该类型资源的请求/约束值的总和。

下面的例子中， Pod 有两个 Container，每个 Container 的请求为 0.25 cpu 和 64MiB 内存， 每个容器的资源约束为 0.5 cpu 和 128MiB 内存。 可以认为该 Pod 的资源请求为 0.5 cpu 和 128 MiB 内存，资源限制为 1 cpu 和 256MiB 内存。当执行调度时，资源是否充足的依据也是基于节点上各个 Pod 的 request 之和来计算的，而不是依据实际使用的情况。

apiVersion: v1
kind: Pod
metadata:
  name: frontend
spec:
  containers:
  - name: app
    image: images.my-company.example/app:v4
    env:
    - name: MYSQL_ROOT_PASSWORD
      value: "password"
    resources:
      requests:
        memory: "64Mi"
        cpu: "250m"
      limits:
        memory: "128Mi"
        cpu: "500m"
  - name: log-aggregator
    image: images.my-company.example/log-aggregator:v6
    resources:
      requests:
        memory: "64Mi"
        cpu: "250m"
      limits:
        memory: "128Mi"
        cpu: "500m"





根据谷歌的 brog 论文，在实际操作中人们往往会过度请求资源，大多数实际运行的应用真正用到的资源往往远小于其所请求的配额。

Kubernetes 使用上述两个配置项来设定资源的使用，并且基于该配置项确定 Pod 的服务质量等级（Quality of Service Level，QoS Level）：

QoS 等级有三类，基于 limits 和 requests 确定：

Guaranteed：最高服务等级，当 Pod 中所有容器都设置了limits 和 requests 并且值相等时，此时 Pod 的 Qos 是 Guaranteed，资源不足时优先保证该类 Pod 的运行。

Burstable：Pod 中有容器只设置了 requests 没有设置 limits，或者 requests 的值小于 limits 值，此时 QoS 为 Burstable。

BestEffort: Pod 中容器都没有设置 limits 和 requests，资源不足时优先杀死这类 Pod。

不难看出，Kuberetes 鼓励我们按实际需要分配资源，如果我们随意设置甚至不设置资源，则 Kubernetes 会做出“惩罚”，优先将这类 Pod 驱逐。

下图是 容器 的 QoS 等级与 Pod 的关系





Taints & Tolerations

上面提到的规则基本都是表示将 Pod 调度到哪个节点，而对于某些节点，我们希望 Pod 不要调度到该节点上去。此时可以通过给 Node 打 Taint(污点) 的方式实现。

给 Node 打污点的命令格式如下：

$ kubectl taint nodes node name key=value:taint effect


key 代表污点的键
value 代表污点的值，可以省略
taint effect 代表污点的效果，有下面三个可选值

NoSchedule：如果 Pod 没有容忍该污点，则不会被调度到打上该污点的 Node 上，但已运行的 Pod 不受影响。
PreferNoSchedule：如果 Pod 没有容忍该污点，则尽量不让其调度到打上该污点的节点上。 实在没有其他 Node 可用了才会调度。
NoExecute：前两种效果影响的只是调度期，而该效果会影响运行期，如果向节点添加了该作用的污点，则已运行在该 Node 上的没有忍受该污点的 Pod 会被驱逐。

下面看几个示例。

1. 添加、查看与移除污点

给 node2 节点添加两个污点

# key 为 node-type，value 为 production，效果为 NoSchedule
$ kubectl taint node node2 node-type=production:NoSchedule
node/node2 tainted

# key 为 isProduct，value 省略，效果为 NoSchedule
$ kubectl taint node node2 isProduct=:NoSchedule
node/node2 tainted


新建两个 Pod 并且没有容忍上述的污点，可以看到新的 Pod 都调度到了 vm-0-4-ubuntu 节点上。

$ kubectl run redis --image=redis --labels="app=redis"
pod/redis created

$ kubectl run nginx --image=nginx
pod/nginx created

$ kubectl get pods -o wide
NAME    READY   STATUS    RESTARTS   AGE   IP          NODE            NOMINATED NODE   READINESS GATES
nginx   1/1     Running   0          55s   10.32.0.7   vm-0-4-ubuntu   <none>           <none>
redis   1/1     Running   0          58s   10.32.0.8   vm-0-4-ubuntu   <none>           <none>


现在去掉 node2 上的两个污点，然后将 vm-0-4-ubuntu 节点打上 NoExecute 效果的污点，看上面的 Pod 是否被驱逐。

移除污点的方式很简单，在污点最后面加 - 即可，如下：

$ kubectl taint node node2 isProduct=:NoSchedule-
node/node2 untainted


现在将 vm-0-4-ubuntu 打上新的效果为 NoExecute 的污点

$ kubectl taint node vm-0-4-ubuntu node-type=production:NoExecute
node/vm-0-4-ubuntu tainted


可以看到 Pod 已经被驱逐了，如果 Pod 是由 Deployment 等控制的，那应该会重新调度到 node2 上。

2. 设置 Pod 容忍污点
如果我们不想移除污点但是依然想让 Pod 调度到该节点的话，就需要给 Pod 添加 Tolerations（容忍度） 了。示例如下：

apiVersion: v1
kind: Pod
metadata:
  name: nginx
  labels:
    env: test
spec:
  containers:
  - name: nginx
    image: nginx
    imagePullPolicy: IfNotPresent
  tolerations:
  - key: "key1"
    operator: "Equal"
    value: "value1"
    effect: "NoSchedule"
  - key: "example-key"
    operator: "Exists"
    effect: "NoSchedule"


operator 有两种：

Equal：这是默认值，表示容忍某个 key 等于 value，并且 effect 为对应效果的污点。
Exists：用于判断没有 value 的污点，表示容忍如果某个 key 存在且 effect 为对应效果的污点。

另外这里还有两种特殊情况:

key 为空并且 operator 为 Exists，表示容忍所有污点
effect 为空，表示容忍所有与 key 匹配的污点。
  tolerations:
  - key: "key1"
    operator: "Equal"
    value: "value1"
    effect: ""
  - key: ""
    operator: "Exists"
    effect: "NoSchedule"


针对 NoExecute 类型的污点，还有一个 tolerationSeconds 的配置，表示可以容忍某个污点多长时间。

tolerations:
- key: "key1"
  operator: "Equal"
  value: "value1"
  effect: "NoExecute"
  tolerationSeconds: 3600



上面我们提到如果打上 NoExecute 效果的污点，会将正在运行的没有容忍该污点的 Pod 驱逐出去，如果加上 tolerationSeconds 配置，则 Pod 会继续运行，如果超出 tolerationSeconds 时间后还没有结束的话则会被驱逐。

比如，一个使用了很多本地状态的应用程序在网络断开时，仍然希望停留在当前节点上运行一段较长的时间， 愿意等待网络恢复以避免被驱逐。在这种情况下，Pod 的容忍度可能是下面这样的：
tolerations:
- key: "node.kubernetes.io/unreachable"
  operator: "Exists"
  effect: "NoExecute"
  tolerationSeconds: 6000




Pod 驱逐

在基于资源进行调度一节中提到，当资源不足时 Kubernetes 会将 QoS 等级较低的 Pod 杀死，该过程在 Kubernetes 中称为驱逐（Eviction）。

计算机资源可以分为两类：
可压缩资源：像 CPU 这类资源，当资源不足时，Pod 会运行变慢，但不会被杀死。
不可压缩资源：像磁盘、内存等资源，当资源不足时 Pod 会被杀死，比如发生内存溢出时 Pod 被直接终止。

Kubernetes 默认设置了一系列阈值，当不可压缩资源达到阈值时，kubelet 就会执行驱逐机制。
主要的阈值有下面几个：

memory.available < 100Mi # 可用内存
nodefs.available < 10%   # 可用磁盘空间
nodefs.inodesFree < 5%   # 文件系统可用 inode 是数量
imagefs.available < 15%  # 可用的容器运行时镜像存储空间


另外驱逐机制中还有软驱逐（soft eviction）、硬驱逐（hard eviction）以及优雅退出期（grace period）的概念:

软驱逐：一个较低的警戒线，资源持续超过该警戒线一段时间后，会触发 Pod 的优雅退出，系统通知 Pod 做必要的善后清理，然后自行结束。超出优雅退出期后，系统会强行杀死未自动退出的 Pod。

硬驱逐：配置一个较高的警戒线，一旦触及此红线，则立即强行杀死 Pod，不会优雅退出。

之所以出现这样更加细化的概念，是因为驱逐 Pod 是一种严重的破坏行为，可能导致服务中断，因此需要兼顾系统短时间的资源波动和资源剧烈消耗影响到高服务质量的 Pod 甚至集群节点的情况。

Kubelet 启动时默认配置文件是 /etc/kubernetes/kubelet-config.yaml，可以通过修改该文件 然后重启 Kubelet 来修改上述阈值配置，示例如下：

apiVersion: kubelet.config.k8s.io/v1beta1
kind: KubeletConfiguration
nodeStatusUpdateFrequency: "10s"
failSwapOn: True
...
...
# 配置硬驱逐阈值
eventRecordQPS: 5
evictionHard:
  nodefs.available:  "5%"
  imagefs.available:  "5%"

调度过程 

Kubernetes 调度过程图所示：

图片来自：https://icyfenix.cn/immutable-infrastructure/schedule/hardware-schedule.html

主要有下面几个步骤：
Informer Loop: 持续监听 etcd 中的资源信息，当 Pod、Node 信息发生变化时触发监听，更新调度缓存和调度队列。

Scheduler Loop: 该步骤主要是从优先级调度队列中获取要调度的 Pod，并基于调度缓存中的信息进行调度决策，主要有如下过程：

Predicates: 过滤阶段，本质上是一组节点过滤器，基于一系列的过滤策略，包括我们上面提到的这些调度规则的设定，比如亲和度都是在这里起作用。只有满足条件的节点才会被筛选出来。

Priorities: 打分阶段，所有可用节点被过滤筛选出来后会进入打分阶段，基于各种打分规则给 Node 打分以选出最合适的节点后进行调度。具体的过滤、打分策略可以参考文档  Scheduling Policies。

Bind：经过过滤打分最终选出合适的 Node 后，会更新本地调度缓存闭关通过异步请求的方式更新 Etcd 中 Pod 的 nodeName 属性。这样如果调度成功则本地缓存与 Etcd 中的信息向保持一致，如果调度失败，则会通过 Informer 循环更新本地缓存，重新调度。

另外为了提升调度性能：

调度过程全程只和本地缓存通信，只有在最后的 bind 阶段才会向 api-server 发起异步通信。
调度器不会处理所有的节点，而是选择一部分节点进行过滤、打分操作。

自定义调度器

除了默认的 Kubernetes 默认提供的调度器，我们还可以自定义调度器并在集群中部署多个调度器，然后在创建  Pod 选择使用的调度器。

下面是一个基于官方的 scheduler 的例子，在集群中部署另一个调度器。

apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    component: scheduler
    tier: control-plane
  name: my-scheduler
  namespace: kube-system
spec:
  selector:
    matchLabels:
      component: scheduler
      tier: control-plane
  replicas: 1
  template:
    metadata:
      labels:
        component: scheduler
        tier: control-plane
        version: second
    spec:
      serviceAccountName: my-scheduler
      containers:
      - command:
        - /usr/local/bin/kube-scheduler
        - --config=/etc/kubernetes/my-scheduler/my-scheduler-config.yaml
        image: gcr.io/my-gcp-project/my-kube-scheduler:1.0
        livenessProbe:
          httpGet:
            path: /healthz
            port: 10251
          initialDelaySeconds: 15
        name: kube-second-scheduler
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    component: scheduler
    tier: control-plane
  name: my-scheduler
  namespace: kube-system
spec:
  selector:
    matchLabels:
      component: scheduler
      tier: control-plane
  replicas: 1
  template:
    metadata:
      labels:
        component: scheduler
        tier: control-plane
        version: second
    spec:
      serviceAccountName: my-scheduler
      containers:
      - command:
        - /usr/local/bin/kube-scheduler
        - --config=/etc/kubernetes/my-scheduler/my-scheduler-config.yaml
        image: gcr.io/my-gcp-project/my-kube-scheduler:1.0
        livenessProbe:
          httpGet:
            path: /healthz
            port: 10251
          initialDelaySeconds: 15
        name: kube-second-scheduler



上面是如何配置的一个新的调度器。对于如何按需实现自己的服务器，Kubernetes 还提供了 Kubernetes Scheduling Framework 来帮我们进行开发。

上面提到 Kubernetes 调度过程分为过滤、打分等一系列阶段，对于这些阶段 Scheduling Framework 提供了一系列的接口使得我们可以自己实现对应的处理，从而实现自定义调度。
下面是主要的扩展点。


图片来自 https://kubernetes.io/docs/concepts/scheduling-eviction/scheduling-framework/

具体到代码中就是实现相应的接口，因为是内部扩展机制，因此在修改后需要重新编译部署。具体代码可以参考 scheduler-plugins 的代码。
Kubernetes 安全
mTLS 

所谓 mTLS（ Mutual TLS）是指在通信时除了客户端会验证服务端证书确认其是否合法之外，服务端也会验证客户端的证书是否合法以确认客户端的合法性。

在常见的浏览器访问网站的场景下，通过 HTTPS 协议，浏览器会对服务器进行身份验证。但在微服务架构中，服务之间的访问通常需要各自进行身份认证，比如常见的 HTTP Basic、JWT 方式认证，这些基本都是应用级别的处理，需要在程序内部做处理。 

使用 mTLS 有如下优点：

可以同时满足加密传输和身份认证
独立于应用之外，与具体语言无关

当然 mTLS 也有不足：

证书管理过于复杂。假设有一对客户端与服务端通信，如果使用自签名证书我们需要 ca 私钥证书、服务端私钥证书、客户端私钥证书共 6 个文件需要进行管理。如果服务变多证书数量也会随之增多，从而增加管理成本
证书更新时需要重启应用

Kubernetes 各个组件之间的通信采用的是 mTLS 认证方式，服务端与客户端都需要各自进行身份认证。Kubernetes 中各个组件通信情况如图：





Server 端包括

kube-apiserver: 外部和 Kubernetes 中的各个组件都是与 api-server 通信。
ETCD Server  : apiserver 与 etcd 进行通信。 
kubelet server : api-server 会与 kubelet 通信。

Client 端证书包括：

admin：外部命令行与 apiserver 通信
scheduler: 调度器作为客户端与 apiserver 通信
controller manager：控制器作为客户端与 apiserver 通信
kube-proxy: kube-proxy 作为客户端与 api-server 通信
kubelet： kubelet 与 apiserver 通信

Kubernetes 使用的是自签名证书，以我们使用 kubeadm 为例，在部署时 kubeadm 会自动帮我们生成证书，Kubernetes 用到的证书大致如下：

# ubuntu @ VM-0-7-ubuntu in /etc/kubernetes/pki 
$ ll
total 60K
-rw-r--r-- 1 root root 1.2K Nov 12 18:30 apiserver-etcd-client.crt
-rw------- 1 root root 1.7K Nov 12 18:30 apiserver-etcd-client.key
-rw-r--r-- 1 root root 1.2K Nov 12 18:30 apiserver-kubelet-client.crt
-rw------- 1 root root 1.7K Nov 12 18:30 apiserver-kubelet-client.key
-rw-r--r-- 1 root root 1.3K Nov 12 18:30 apiserver.crt
-rw------- 1 root root 1.7K Nov 12 18:30 apiserver.key
-rw-r--r-- 1 root root 1.1K Nov 12 18:30 ca.crt
-rw------- 1 root root 1.7K Nov 12 18:30 ca.key
drwxr-xr-x 2 root root 4.0K Nov 12 18:30 etcd
-rw-r--r-- 1 root root 1.1K Nov 12 18:30 front-proxy-ca.crt
-rw------- 1 root root 1.7K Nov 12 18:30 front-proxy-ca.key
-rw-r--r-- 1 root root 1.1K Nov 12 18:30 front-proxy-client.crt
-rw------- 1 root root 1.7K Nov 12 18:30 front-proxy-client.key
-rw------- 1 root root 1.7K Nov 12 18:30 sa.key
-rw------- 1 root root  451 Nov 12 18:30 sa.pub


# ubuntu @ VM-0-7-ubuntu in /etc/kubernetes/pki/etcd 
$ ll
total 32K
-rw-r--r-- 1 root root 1.1K Nov 12 18:30 ca.crt
-rw------- 1 root root 1.7K Nov 12 18:30 ca.key
-rw-r--r-- 1 root root 1.2K Nov 12 18:30 healthcheck-client.crt
-rw------- 1 root root 1.7K Nov 12 18:30 healthcheck-client.key
-rw-r--r-- 1 root root 1.2K Nov 12 18:30 peer.crt
-rw------- 1 root root 1.7K Nov 12 18:30 peer.key
-rw-r--r-- 1 root root 1.2K Nov 12 18:30 server.crt
-rw------- 1 root root 1.7K Nov 12 18:30 server.key

$ sudo ls -l /var/lib/kubelet/pki
total 12
kubelet-client-2021-11-12-18-30-55.pem
-rw-r--r-- 1 root root 2287 Nov 12 18:30 kubelet.crt
-rw------- 1 root root 1679 Nov 12 18:30 kubelet.key





Kubeadm 生成的 ca 证书有效期为 10 年，其他证书为 1 年，可以通过 sudo kubeadm  certs check-expiration 命令查看证书的过期情况。可以通过自动脚本定时更新或者修改 kubeadm 源码将证书有效期延长。

$ sudo kubeadm  certs check-expiration
[check-expiration] Reading configuration from the cluster...
[check-expiration] FYI: You can look at this config file with 'kubectl -n kube-system get cm kubeadm-config -o yaml'

CERTIFICATE                EXPIRES                  RESIDUAL TIME   CERTIFICATE AUTHORITY   EXTERNALLY MANAGED
admin.conf                 Nov 12, 2022 10:30 UTC   354d                                    
apiserver                  Nov 12, 2022 10:30 UTC   354d            ca                      noapiserver-etcd-client    Nov 12, 2022 10:30 UTC   354d            etcd-ca                 
apiserver-kubelet-client   Nov 12, 2022 10:30 UTC   354d            ca                      
controller-manager.conf    Nov 12, 2022 10:30 UTC   354d                                    
etcd-healthcheck-client    Nov 12, 2022 10:30 UTC   354d            etcd-ca                 
etcd-peer                  Nov 12, 2022 10:30 UTC   354d            etcd-ca                 
etcd-server                Nov 12, 2022 10:30 UTC   354d            etcd-ca                 
front-proxy-client         Nov 12, 2022 10:30 UTC   354d               
scheduler.conf             Nov 12, 2022 10:30 UTC   354d                                    

CERTIFICATE AUTHORITY   EXPIRES                  RESIDUAL TIME   EXTERNALLY MANAGED
ca                      Nov 10, 2031 10:30 UTC   9y              no
etcd-ca                 Nov 10, 2031 10:30 UTC   9y              no
front-proxy-ca          Nov 10, 2031 10:30 UTC   9y              no


认证 

apiserver 收到外部请求时，首先要需要经过一系列的验证后才能确认是否允许请求继续执行。主要有三步：

身份认证：Who you are？
权限验证：What can you do？
准入控制


对于身份验证，Kubernetes 中身份信息可以分为两类：

Service Account：集群内部进行身份认证和授权的服务账户。
普通用户：外部访问集群的用户。
ServiceAccount

ServiceAccount 是 Kubernetes 内部通信使用的账户信息，每个 namesapce 下都会有一个名为 default 的默认 Service Account，在没有单独设置时 Pod 默认使用该 ServiceAccout 作为服务账户。Service Account 会生成对应的 Secret 存储其 token。

$ kubectl get serviceaccounts
NAME      SECRETS   AGE
default   1         10d

$ kubectl get secrets
NAME                  TYPE                                  DATA   AGE
default-token-c7bv9   kubernetes.io/service-account-token   3      10d


$ kubectl describe secrets default-token-c7bv9
Name:         default-token-c7bv9
Namespace:    default
Labels:       <none>
Annotations:  kubernetes.io/service-account.name: default
              kubernetes.io/service-account.uid: 517fd6b1-6441-4817-bf16-14ef37175da2

Type:  kubernetes.io/service-account-token
Data
====
ca.crt:     1099 bytes
namespace:  7 bytes
token:      eyJhbGciOiJSUzI1NiIsImtpZCI6IjFLMkVMNm5mMkFhYmQyMUdCVXp3OGdiZEs1dkdRQ3NNR0JWT0RZblIzYkkifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tYzdidjkiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjUxN2ZkNmIxLTY0NDEtNDgxNy1iZjE2LTE0ZWYzNzE3NWRhMiIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.C2gGaqrF1effQy_9e48VGh06Ks1ihwR3Q6gHezBBZ51WmD2Sg4Pt0WASZEpJ8swPLXUCo13UaL_y2b3dXOwcjWDOApFsPttDZQtfjiIDn_Wt0RMCKTUNr9ft8_GcM2Xjt8Bnz_mev-NZFwBBJC1vhJn2u-XQLfsp0XiHVTsls0JlPdtZjBOAvlxTQtM9LbMb2o5flEXLCHEGiKNkrYczS7SDNFfrOUNcdDbJHUhifAynOm0bSFIWTG9R0CYHvM3oTJyLSLHuSjqZjpMfNev_4V27AWfSTWg1rwC3Bhj3FNzQSEriQCg1rt9t-Bq58AbJR4vrj2dQa6vT5FP6xQVULA



CA_CERT=/var/run/secrets/kubernetes.io/serviceaccount/ca.crt 
TOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token) 
NAMESPACE=$(cat /var/run/secrets/kubernetes.io/serviceaccount/namespace)



Pod 会将 Secret 作为卷挂在进来，Service Account 的 Secrets 的 token 文件会被mount 到 pod 里的下面这个位置。 在 pod 里可以使他们来访问 API，为便于操作可以先在 Pod 里设置几个环境变量。


$ ls -l /var/run/secrets/kubernetes.io/serviceaccount/

lrwxrwxrwx    1 root  root 13 Aug 31 03:24 ca.crt -> ..data/ca.crt
lrwxrwxrwx    1 root  root 16 Aug 31 03:24 namespace -> ..data/namespace
lrwxrwxrwx    1 root  root 12 Aug 31 03:24 token -> ..data/token




我们也可以直接使用该 token 与 apiserver 通信。


$ curl -k https://172.19.0.7:6443/api
{
  "kind": "Status",
  "apiVersion": "v1",
  "metadata": {

  },
  "status": "Failure",
  "message": "forbidden: User \"system:anonymous\" cannot get path \"/api\"",
  "reason": "Forbidden",
  "details": {

  },
  "code": 403
}

$ curl -k https://172.19.0.7:6443/api -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IjFLMkVMNm5mMkFhYmQyMUdCVXp3OGdiZEs1dkdRQ3NNR0JWT0RZblIzYkkifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tYzdidjkiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjUxN2ZkNmIxLTY0NDEtNDgxNy1iZjE2LTE0ZWYzNzE3NWRhMiIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.C2gGaqrF1effQy_9e48VGh06Ks1ihwR3Q6gHezBBZ51WmD2Sg4Pt0WASZEpJ8swPLXUCo13UaL_y2b3dXOwcjWDOApFsPttDZQtfjiIDn_Wt0RMCKTUNr9ft8_GcM2Xjt8Bnz_mev-NZFwBBJC1vhJn2u-XQLfsp0XiHVTsls0JlPdtZjBOAvlxTQtM9LbMb2o5flEXLCHEGiKNkrYczS7SDNFfrOUNcdDbJHUhifAynOm0bSFIWTG9R0CYHvM3oTJyLSLHuSjqZjpMfNev_4V27AWfSTWg1rwC3Bhj3FNzQSEriQCg1rt9t-Bq58AbJR4vrj2dQa6vT5FP6xQVULA"
{
  "kind": "APIVersions",
  "versions": [
    "v1"
  ],
  "serverAddressByClientCIDRs": [
    {
      "clientCIDR": "0.0.0.0/0",
      "serverAddress": "172.19.0.7:6443"
    }
  ]
}


除了每个 namespace 默认的服务账户外，我们可以自己创建 Service Account。

​​$ kubectl create serviceaccount build-robot
serviceaccount/build-robot created

# ubuntu @ VM-0-7-ubuntu in ~ [9:45:08]
$ kubectl get serviceaccounts/build-robot -o yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  creationTimestamp: "2021-11-23T01:45:08Z"
  name: build-robot
  namespace: default
  resourceVersion: "881093"
  uid: 0160e851-cae1-4927-a524-58c3a379ee05
secrets:
- name: build-robot-token-d9p58


创建完成后我们可以在创建 Pod 时通过修改 spec.serviceAccountName 属性来指定 ServiceAccount。通过自己创建 ServiceAccount 并结合 RBAC 授权可以针对特殊的 Pod 进行单独的权限控制。

apiVersion: v1
kind: Pod
metadata:
  name: nginx
spec:
  containers:
  - image: nginx
    name: nginx
    volumeMounts:
    - mountPath: /var/run/secrets/tokens
      name: vault-token
  serviceAccountName: build-robot



用户

Kubernetes 虽然有用户的概念，但它认为用户是由集群无关的服务进行管理的，因此并没有提供类似  kubectl create user 的 API 来创建用户。

当外部访问 apiserver 时，Kubernetes 可以使用的认证方式有：

客户端证书认证
静态的用户密码或 token 文件（Departed）
Bootstrap tokens
OpenID Connect (OIDC)
Authenticating proxy
Webhook token authentication

这里比较常用的方式是客户端证书，其他方式使用可以参考这份索引 文档。

Kubernetes 内置了 Certificate Signing Request 对象来执行证书签名。当外部用户想请求 Kubernetes 时，可以使用私钥来生成 CSR 证书签名请求，然后交给 Kuberetes 签发。流程如下

生成私钥与 CSR
# 创建私钥
$ openssl genrsa -out Jane.key 2048
Generating RSA private key, 2048 bit long modulus (2 primes)
.............................+++++
.........................................................+++++
e is 65537 (0x010001)


# 生成 CSR
$ openssl req -new -key Jane.key -out Jane.csr
...
Common Name (e.g. server FQDN or YOUR name) []:Jane
Email Address []:

Please enter the following 'extra' attributes
to be sent with your certificate request
A challenge password []:
An optional company name []:


$ cat Jane.csr | base64 | tr -d "\n"

LS0tLS1CRUdJTiBDRVJUSUZJQ0FURSBSRVFVRVNULS0tLS0KTUlJQ21UQ0NBWUVDQVFBd1ZERUxNQWtHQTFVRUJoTUNRVlV4RXpBUkJnTlZCQWdNQ2xOdmJXVXRVM1JoZEdVeApJVEFmQmdOVkJBb01HRWx1ZEdWeWJtVjBJRmRwWkdkcGRITWdVSFI1SUV4MFpERU5NQXNHQTFVRUF3d0VTbUZ1ClpUQ0NBU0l3RFFZSktvWklodmNOQVFFQkJRQURnZ0VQQURDQ0FRb0NnZ0VCQU5tb3dZUkdpbHlWSkVIbkxaUU0KSFEvQWRveG9CNmJUN2YvSjFuc2xBYXZEYm9Sc3BKdjBBcGh6a05RYXJDU1E1SDRYVjR2OGZDdDVmeGFyL294agpmUXVyWDNrbXk1SHpJTGFod0svWXUvWU01djhacG53S3J3RmpmTzVpVC9rRmhyOUF0VkhWL0ZMajBhZURzUHRaCjlaemduUXUwbUUxcmc5WWZBUVFxOHo5UjB5bGFxQ0V2SU9HVU5FRzBrNGN2K0lDNE96KzZjQmIyUGhLLzFKc3kKcUg3V3RONnIraDI0S0FveXExZDFSY1NIU0ppbVgwbkExNlFCYjRuRVFGc0xJaUtXSXRxQ1JXUm9WT2dqSDhMUQpOV1ZlQmRKRVh6MWxIYXhzVE56OEo0QVhUZGFTLzcwSDhMRXhCT3ppMXNXMFB1aldPRC8xRkVhc2dqY1NNUG9uCnVua0NBd0VBQWFBQU1BMEdDU3FHU0liM0RRRUJDd1VBQTRJQkFRQ2Z3ck5menl1blFtaUVBaXpxdzN6VGh0UkIKdjZtSmZVL2tNZTN1eHVDZm1MR3Y4OXpvZ3k3SWQxM25pdTE5Zzgzdy82UktkRFI1QVhXODk0L3daQi9CQjQwcgoyU3pQcmk5L3hMUkFManZnbWY0d1NhaDIyUnRJUjJYZGNVelJZL2V4V2w4ajJVV0w5Mkwxci82bWNjSVdrT1BUCnNmUWVYWWYxZThnNVk5Q1VSbThTNlloeURQOXFCQzk3QjEwenovNU1SN0YxdmtxMTI2OEtEek9GbWtWSnJLMkEKaXFNQk4xdkRSVkJURFVISFJ4V1lwTUpTSHJrOFRlVHhLVE1RNG12WGxCMzNUbElIZlU4L1ZOMEtQNFU5d0k5egpLNEI1NWFFc3QxUW5TWkw3cDlxMisvb20rdmtZUC9RblU4M3I4RlBFVTJ4R2ZzSTR0WFpzNkdseGZyVzMKLS0tLS1FTkQgQ0VSVElGSUNBVEUgUkVRVUVTVC0tLS0tCg==%

 

创建 Certificate Signing Request

CSR 生成后我们通过 base64  编码拿到其内容，并创建 Kubernetes 的 CSR 对象：

apiVersion: certificates.k8s.io/v1
kind: CertificateSigningRequest
metadata:
  name: Jane
spec:
  request: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURSBSRVFVRVNULS0tLS0KTUlJQ21UQ0NBWUVDQVFBd1ZERUxNQWtHQTFVRUJoTUNRVlV4RXpBUkJnTlZCQWdNQ2xOdmJXVXRVM1JoZEdVeApJVEFmQmdOVkJBb01HRWx1ZEdWeWJtVjBJRmRwWkdkcGRITWdVSFI1SUV4MFpERU5NQXNHQTFVRUF3d0VTbUZ1ClpUQ0NBU0l3RFFZSktvWklodmNOQVFFQkJRQURnZ0VQQURDQ0FRb0NnZ0VCQU5tb3dZUkdpbHlWSkVIbkxaUU0KSFEvQWRveG9CNmJUN2YvSjFuc2xBYXZEYm9Sc3BKdjBBcGh6a05RYXJDU1E1SDRYVjR2OGZDdDVmeGFyL294agpmUXVyWDNrbXk1SHpJTGFod0svWXUvWU01djhacG53S3J3RmpmTzVpVC9rRmhyOUF0VkhWL0ZMajBhZURzUHRaCjlaemduUXUwbUUxcmc5WWZBUVFxOHo5UjB5bGFxQ0V2SU9HVU5FRzBrNGN2K0lDNE96KzZjQmIyUGhLLzFKc3kKcUg3V3RONnIraDI0S0FveXExZDFSY1NIU0ppbVgwbkExNlFCYjRuRVFGc0xJaUtXSXRxQ1JXUm9WT2dqSDhMUQpOV1ZlQmRKRVh6MWxIYXhzVE56OEo0QVhUZGFTLzcwSDhMRXhCT3ppMXNXMFB1aldPRC8xRkVhc2dqY1NNUG9uCnVua0NBd0VBQWFBQU1BMEdDU3FHU0liM0RRRUJDd1VBQTRJQkFRQ2Z3ck5menl1blFtaUVBaXpxdzN6VGh0UkIKdjZtSmZVL2tNZTN1eHVDZm1MR3Y4OXpvZ3k3SWQxM25pdTE5Zzgzdy82UktkRFI1QVhXODk0L3daQi9CQjQwcgoyU3pQcmk5L3hMUkFManZnbWY0d1NhaDIyUnRJUjJYZGNVelJZL2V4V2w4ajJVV0w5Mkwxci82bWNjSVdrT1BUCnNmUWVYWWYxZThnNVk5Q1VSbThTNlloeURQOXFCQzk3QjEwenovNU1SN0YxdmtxMTI2OEtEek9GbWtWSnJLMkEKaXFNQk4xdkRSVkJURFVISFJ4V1lwTUpTSHJrOFRlVHhLVE1RNG12WGxCMzNUbElIZlU4L1ZOMEtQNFU5d0k5egpLNEI1NWFFc3QxUW5TWkw3cDlxMisvb20rdmtZUC9RblU4M3I4RlBFVTJ4R2ZzSTR0WFpzNkdseGZyVzMKLS0tLS1FTkQgQ0VSVElGSUNBVEUgUkVRVUVTVC0tLS0tCg==
  signerName: kubernetes.io/kube-apiserver-client
  expirationSeconds: 86400  # one day
  usages:
  - client auth



$ kubectl apply -f k8s-csr-jane.yaml
certificatesigningrequest.certificates.k8s.io/Jane created







批准 Certificate Signing Request

新创建的 CSR 处于 Pending 状态，需要批准后才能使用。

$ kubectl get csr
NAME     AGE     SIGNERNAME                            REQUESTOR          REQUESTEDDURATION   CONDITION
Jane     6s      kubernetes.io/kube-apiserver-client   kubernetes-admin   24h                 Pending


$ kubectl certificate approve Jane
certificatesigningrequest.certificates.k8s.io/Jane approved

$ kubectl get csr 
NAME   AGE     SIGNERNAME                            REQUESTOR          REQUESTEDDURATION   CONDITION
Jane   4m18s   kubernetes.io/kube-apiserver-client   kubernetes-admin   24h                 Approved,Issued


批准后 CSR 中就有了证书信息：


$ kubectl get csr Jane -o yaml
apiVersion: certificates.k8s.io/v1
kind: CertificateSigningRequest
metadata:
  annotations:
    kubectl.kubernetes.io/last-applied-configuration: |
      {"apiVersion":"certificates.k8s.io/v1","kind":"CertificateSigningRequest","metadata":{"annotations":{},"name":"Jane"},"spec":{"expirationSeconds":86400,"request":"LS0tLS1CRUdJTiBDRVJUSUZJQ0FURSBSRVFVRVNULS0tLS0KTUlJQ21UQ0NBWUVDQVFBd1ZERUxNQWtHQTFVRUJoTUNRVlV4RXpBUkJnTlZCQWdNQ2xOdmJXVXRVM1JoZEdVeApJVEFmQmdOVkJBb01HRWx1ZEdWeWJtVjBJRmRwWkdkcGRITWdVSFI1SUV4MFpERU5NQXNHQTFVRUF3d0VTbUZ1ClpUQ0NBU0l3RFFZSktvWklodmNOQVFFQkJRQURnZ0VQQURDQ0FRb0NnZ0VCQU5tb3dZUkdpbHlWSkVIbkxaUU0KSFEvQWRveG9CNmJUN2YvSjFuc2xBYXZEYm9Sc3BKdjBBcGh6a05RYXJDU1E1SDRYVjR2OGZDdDVmeGFyL294agpmUXVyWDNrbXk1SHpJTGFod0svWXUvWU01djhacG53S3J3RmpmTzVpVC9rRmhyOUF0VkhWL0ZMajBhZURzUHRaCjlaemduUXUwbUUxcmc5WWZBUVFxOHo5UjB5bGFxQ0V2SU9HVU5FRzBrNGN2K0lDNE96KzZjQmIyUGhLLzFKc3kKcUg3V3RONnIraDI0S0FveXExZDFSY1NIU0ppbVgwbkExNlFCYjRuRVFGc0xJaUtXSXRxQ1JXUm9WT2dqSDhMUQpOV1ZlQmRKRVh6MWxIYXhzVE56OEo0QVhUZGFTLzcwSDhMRXhCT3ppMXNXMFB1aldPRC8xRkVhc2dqY1NNUG9uCnVua0NBd0VBQWFBQU1BMEdDU3FHU0liM0RRRUJDd1VBQTRJQkFRQ2Z3ck5menl1blFtaUVBaXpxdzN6VGh0UkIKdjZtSmZVL2tNZTN1eHVDZm1MR3Y4OXpvZ3k3SWQxM25pdTE5Zzgzdy82UktkRFI1QVhXODk0L3daQi9CQjQwcgoyU3pQcmk5L3hMUkFManZnbWY0d1NhaDIyUnRJUjJYZGNVelJZL2V4V2w4ajJVV0w5Mkwxci82bWNjSVdrT1BUCnNmUWVYWWYxZThnNVk5Q1VSbThTNlloeURQOXFCQzk3QjEwenovNU1SN0YxdmtxMTI2OEtEek9GbWtWSnJLMkEKaXFNQk4xdkRSVkJURFVISFJ4V1lwTUpTSHJrOFRlVHhLVE1RNG12WGxCMzNUbElIZlU4L1ZOMEtQNFU5d0k5egpLNEI1NWFFc3QxUW5TWkw3cDlxMisvb20rdmtZUC9RblU4M3I4RlBFVTJ4R2ZzSTR0WFpzNkdseGZyVzMKLS0tLS1FTkQgQ0VSVElGSUNBVEUgUkVRVUVTVC0tLS0tCg==","signerName":"kubernetes.io/kube-apiserver-client","usages":["client auth"]}}
  creationTimestamp: "2021-11-24T22:47:57Z"
  name: Jane
  resourceVersion: "1127112"
  uid: 6f0b5433-e1d0-4f89-bbf1-a14fc1d0ad55
spec:
  expirationSeconds: 86400
  groups:
  - system:masters
  - system:authenticated
  request: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURSBSRVFVRVNULS0tLS0KTUlJQ21UQ0NBWUVDQVFBd1ZERUxNQWtHQTFVRUJoTUNRVlV4RXpBUkJnTlZCQWdNQ2xOdmJXVXRVM1JoZEdVeApJVEFmQmdOVkJBb01HRWx1ZEdWeWJtVjBJRmRwWkdkcGRITWdVSFI1SUV4MFpERU5NQXNHQTFVRUF3d0VTbUZ1ClpUQ0NBU0l3RFFZSktvWklodmNOQVFFQkJRQURnZ0VQQURDQ0FRb0NnZ0VCQU5tb3dZUkdpbHlWSkVIbkxaUU0KSFEvQWRveG9CNmJUN2YvSjFuc2xBYXZEYm9Sc3BKdjBBcGh6a05RYXJDU1E1SDRYVjR2OGZDdDVmeGFyL294agpmUXVyWDNrbXk1SHpJTGFod0svWXUvWU01djhacG53S3J3RmpmTzVpVC9rRmhyOUF0VkhWL0ZMajBhZURzUHRaCjlaemduUXUwbUUxcmc5WWZBUVFxOHo5UjB5bGFxQ0V2SU9HVU5FRzBrNGN2K0lDNE96KzZjQmIyUGhLLzFKc3kKcUg3V3RONnIraDI0S0FveXExZDFSY1NIU0ppbVgwbkExNlFCYjRuRVFGc0xJaUtXSXRxQ1JXUm9WT2dqSDhMUQpOV1ZlQmRKRVh6MWxIYXhzVE56OEo0QVhUZGFTLzcwSDhMRXhCT3ppMXNXMFB1aldPRC8xRkVhc2dqY1NNUG9uCnVua0NBd0VBQWFBQU1BMEdDU3FHU0liM0RRRUJDd1VBQTRJQkFRQ2Z3ck5menl1blFtaUVBaXpxdzN6VGh0UkIKdjZtSmZVL2tNZTN1eHVDZm1MR3Y4OXpvZ3k3SWQxM25pdTE5Zzgzdy82UktkRFI1QVhXODk0L3daQi9CQjQwcgoyU3pQcmk5L3hMUkFManZnbWY0d1NhaDIyUnRJUjJYZGNVelJZL2V4V2w4ajJVV0w5Mkwxci82bWNjSVdrT1BUCnNmUWVYWWYxZThnNVk5Q1VSbThTNlloeURQOXFCQzk3QjEwenovNU1SN0YxdmtxMTI2OEtEek9GbWtWSnJLMkEKaXFNQk4xdkRSVkJURFVISFJ4V1lwTUpTSHJrOFRlVHhLVE1RNG12WGxCMzNUbElIZlU4L1ZOMEtQNFU5d0k5egpLNEI1NWFFc3QxUW5TWkw3cDlxMisvb20rdmtZUC9RblU4M3I4RlBFVTJ4R2ZzSTR0WFpzNkdseGZyVzMKLS0tLS1FTkQgQ0VSVElGSUNBVEUgUkVRVUVTVC0tLS0tCg==
  signerName: kubernetes.io/kube-apiserver-client
  usages:
  - client auth
  username: kubernetes-admin
status:
  certificate: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURPakNDQWlLZ0F3SUJBZ0lSQU9MQzB0MXpQVUdIdkU0Z3ZJRUova0l3RFFZSktvWklodmNOQVFFTEJRQXcKRlRFVE1CRUdBMVVFQXhNS2EzVmlaWEp1WlhSbGN6QWVGdzB5TVRFeE1qUXlNalEzTVRKYUZ3MHlNVEV4TWpVeQpNalEzTVRKYU1GUXhDekFKQmdOVkJBWVRBa0ZWTVJNd0VRWURWUVFJRXdwVGIyMWxMVk4wWVhSbE1TRXdId1lEClZRUUtFeGhKYm5SbGNtNWxkQ0JYYVdSbmFYUnpJRkIwZVNCTWRHUXhEVEFMQmdOVkJBTVRCRXBoYm1Vd2dnRWkKTUEwR0NTcUdTSWIzRFFFQkFRVUFBNElCRHdBd2dnRUtBb0lCQVFEWnFNR0VSb3BjbFNSQjV5MlVEQjBQd0hhTQphQWVtMCszL3lkWjdKUUdydzI2RWJLU2I5QUtZYzVEVUdxd2trT1IrRjFlTC9Id3JlWDhXcS82TVkzMExxMTk1CkpzdVI4eUMyb2NDdjJMdjJET2IvR2FaOENxOEJZM3p1WWsvNUJZYS9RTFZSMWZ4UzQ5R25nN0Q3V2ZXYzRKMEwKdEpoTmE0UFdId0VFS3ZNL1VkTXBXcWdoTHlEaGxEUkJ0Sk9ITC9pQXVEcy91bkFXOWo0U3Y5U2JNcWgrMXJUZQpxL29kdUNnS01xdFhkVVhFaDBpWXBsOUp3TmVrQVcrSnhFQmJDeUlpbGlMYWdrVmthRlRvSXgvQzBEVmxYZ1hTClJGODlaUjJzYkV6Yy9DZUFGMDNXa3YrOUIvQ3hNUVRzNHRiRnREN28xamcvOVJSR3JJSTNFakQ2SjdwNUFnTUIKQUFHalJqQkVNQk1HQTFVZEpRUU1NQW9HQ0NzR0FRVUZCd01DTUF3R0ExVWRFd0VCL3dRQ01BQXdId1lEVlIwagpCQmd3Rm9BVTdMQXF5b3RONUltOFBQZnFlTEgwVmMvcjdNb3dEUVlKS29aSWh2Y05BUUVMQlFBRGdnRUJBRjljCnIyQWVuYVl1UDBvcmlLZTU5QjgwaWk2WUErbGdBelowU2lwdnhTYzlQbDBsZ3NuN01ibGtQdkc3MGM4S3UyRVIKRXl5WE9WcjFjcVhjSE1DNDk3b0hHQUM5L2ZDcitUc3lLT2x4L1A5TWxCOTRZdC9ZMStvd2drUndzajFnSnVHTQorbENlbUcyKy9yZCtWbTlHeEh0c3pxODZHa0tDNHVzV0dKMmhkZGVDYVV2OEdjZk9KMCtUT1orM3ZwNExIWmZ2CmFuakNEK2R2RzIxVW5SUXM2eUQyZUNDVG0ydVhVQkdNSnlkajFEUlNERis5b0ZRS2hvZVVPMnhUaWFpdFZEeTgKNy9RODZVb2hMS1lGQUdWTmFSazRDdDh0N2hMeFFVaU9USUcxajNUbi96T0lZd256aURCV0dhZ1RKOVNMbGk3RQovcGRlTWFReEFiMHdBMnkvbnlzPQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCg==
  conditions:
  - lastTransitionTime: "2021-11-24T22:52:12Z"
    lastUpdateTime: "2021-11-24T22:52:12Z"
    message: This CSR was approved by kubectl certificate approve.
    reason: KubectlApprove
    status: "True"
    type: Approved



我们可以通过 base64 将 CSR 中的证书解码导出然后在 作为登陆凭证配合在 kubeconfig 或者 RBAC 授权中。

​​$ kubectl get csr Jane -o jsonpath='{.status.certificate}' | base64 -d > Jane.crt


Kubeconfig

有了用户信息后，我们可以通过 curl 或者 kubectl 访问集群了，我们可以在发起请求时配置证书或者在请求头中设置 token 进行验证。

curl -k https://172.19.0.7:6443/api -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IjFLMkVMNm5mMkFhYmQyMUdCVXp3OGdiZEs1dkdRQ3NNR0JWT0RZblIzYkkifQ..."

$ kubectl get pods --server https://172.19.0.7:6443 \
> --client-key admin.key \
> --client-certificate admin.crt \
> --certificate-authority ca.crt




但每次都这样访问的话非常不方便，尤其是需要访问用不同身份访问不同集群时，Kubernetes 提供了 kubeconfig 配置文件让我们可以更方便的配置对集群的访问。

$ kubectl get pods --kubeconfig /etc/kubernetes/admin.conf
NAME                         READY   STATUS    RESTARTS      AGE
php-apache-d4cf67d68-xsbbt   1/1     Running   2 (44h ago)   7d15h


默认的 kubeconfig 配置文件位于 ~/.kube/config ，一般安装完成后我们会将 /etc/kubernetes/admin.conf 文件复制过来，这样我们就可以通过 kubectl 直接访问集群了。

可以通过查看文件或者 kubectl config view 命令查看

$ kubectl config view
apiVersion: v1
kind: Config
clusters:
- cluster:
    certificate-authority-data: DATA+OMITTED
    server: https://172.19.0.7:6443
  name: kubernetes
contexts:
- context:
    cluster: kubernetes
    user: kubernetes-admin
  name: kubernetes-admin@kubernetes
current-context: kubernetes-admin@kubernetes
preferences: {}
users:
- name: kubernetes-admin
  user:
    client-certificate-data: REDACTED
    client-key-data: REDACTED



Kubeconfig 文件可以分为三部分：

Clusters：要访问的集群，指定集群名称、访问地址以及 CA 证书。
Users：   用户信息，指定用户名以及私钥、证书作为访问凭证。
Contexts：访问上下文，指定用哪个用户访问哪个集群。



通过添加不同的集群和用户，并设置不同的上下文，我们就可以在同一个终端对不同的集群进行访问。

除了直接修改文件外我们可以通过 kubectl config 命令来动态操作 kubeconfig 配置。


$ kubectl config
Modify kubeconfig files using subcommands like "kubectl config set current-context my-context"

 The loading order follows these rules:

  1.  If the --kubeconfig flag is set, then only that file is loaded. The flag may only be set once and no merging takes
place.
  2.  If $KUBECONFIG environment variable is set, then it is used as a list of paths (normal path delimiting rules for
your system). These paths are merged. When a value is modified, it is modified in the file that defines the stanza. When
a value is created, it is created in the first file that exists. If no files in the chain exist, then it creates the
last file in the list.
  3.  Otherwise, ${HOME}/.kube/config is used and no merging takes place.

Available Commands:
  current-context Display the current-context
  delete-cluster  Delete the specified cluster from the kubeconfig
  delete-context  Delete the specified context from the kubeconfig
  delete-user     Delete the specified user from the kubeconfig
  get-clusters    Display clusters defined in the kubeconfig
  get-contexts    Describe one or many contexts
  get-users       Display users defined in the kubeconfig
  rename-context  Rename a context from the kubeconfig file
  set             Set an individual value in a kubeconfig file
  set-cluster     Set a cluster entry in kubeconfig
  set-context     Set a context entry in kubeconfig
  set-credentials Set a user entry in kubeconfig
  unset           Unset an individual value in a kubeconfig file
  use-context     Set the current-context in a kubeconfig file
  view            Display merged kubeconfig settings or a specified kubeconfig file

Usage:
  kubectl config SUBCOMMAND [options]


比如我每次查看 easemesh 命名空间下的资源对象都需要加 -n 指定 namespace 比较麻烦，我可以新加一个 context 使得每次默认访问 easemesh 命名空间下的对象。

新建 context
$ kubectl config set-context mesh-checker --cluster=kubernetes --user=kubernetes-admin --namespace=easemesh
Context "mesh-checker" created.

$ kubectl config current-context
kubernetes-admin@kubernetes


   2. 切换 context 
$ kubectl config use-context mesh-checker
Switched to context "mesh-checker".


$ kubectl get pods
NAME                                                READY   STATUS    RESTARTS      AGE
easemesh-control-plane-0                            1/1     Running   0             24h
easemesh-ingress-easegress-659556f6dd-xwtcb         1/1     Running   2 (44h ago)   7d15h
easemesh-operator-6754847bb7-fcglb                  2/2     Running   4 (44h ago)   7d15h
easemesh-shadowservice-controller-cb64cb5f5-z9tbz   1/1     Running   1 (44h ago)   5d16h

$ kubectl config current-context
mesh-checker


在 CKA/CKAD 考试中一般会提供若干个集群供我们操作，会频繁的用到  kubectl config use-context 来切换上下文。
授权



有了身份认证后还需要权限认证来确认请求者是否有权限执行操作：


认证方式
实现方式
使用方式
Node 授权
apiserver 内置
内部使用（kubelet）
ABAC
静态文件
已弃用
RBAC
Kuberetes 对象
用户/管理员授权
WebHook
外部服务


Always Dency/Always Allow
apiserver 内置
测试时使用


RBAC


基于角色的访问控制（Role-Based Access Control, 即”RBAC”）使用”rbac.authorization.k8s.io” API Group 实现授权决策，允许管理员通过 Kubernetes API 动态配置策略。要启用 RBAC，需使用 --authorization-mode=RBAC  启动 API Server。

Kubernetes 中 RBAC 的核心是通过 Role/ClusterRole、RoleBinding/ClusterRoleBind 来完成授权策略的定义：

Role/ClusterRole: 在 namespace 或者集群层面定义针对资源的权限集合
RoleBinding/ClusterRoleBinding: 将 Role/ClusterRole 绑定到目标对象完成授权，目前 Kubernetes 支持给 User、Group、ServiceAccount 三种对象授权。

Roles & ClusterRoles

Role 代表对某个单一命名空间下的访问权限，而如果相对整个集群内的某些资源拥有权限，则需要通过 ClusterRole 实现。


以下是在 ”default” 命名空间中一个 Role 对象的定义，用于授予对 pod 的读访问权限：

kind: Role
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  namespace: default
  name: pod-reader
rules:
- apiGroups: [""] # 空字符串"" 表明使用 core API group
  resources: ["pods"]
  verbs: ["get", "watch", "list", “”]


下面例子是 ClusterRole 的定义示例，用于授予用户对某一特定命名空间，或者所有命名空间中的 secret（取决于其绑定方式）的读访问权限：

kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  # 鉴于 ClusterRole 是集群范围对象，所以这里不需要定义 "namespace" 字段
  name: secret-reader
rules:
- apiGroups: [""]
  resources: ["secrets"]
  verbs: ["get", "watch", "list"]



权限集合的定义在 rules 中，包含三部分：

apiGroups

API 组，资源所在的组，比如 Job 对象在 batch 组，Deploymet 在 app 组。可以通过 kubectl api-resources 命令查看其 apiversion 中的组。如果是空字符串代表 core 组。

resources

具体的资源列表，比如 pods，cronjobs 等。大多数资源由代表其名字的字符串表示，例如”pods”，但有一些 Kubernetes API 还 包含了“子资源”，比如 pod 的 logs。在 Kubernetes 中，pod logs endpoint 的 URL 格式为：

GET /api/v1/namespaces/{namespace}/pods/{name}/log


在这种情况下，”pods” 是命名空间资源，而 “log” 是 pods 的子资源。为了在 RBAC 的角色中表示出这一点，我们需要使用斜线来划分资源 与子资源。如果需要角色绑定主体读取 pods 以及 pod log，您需要定义以下角色：

kind: Role
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  namespace: default
  name: pod-and-pod-logs-reader
rules:
- apiGroups: [""]
  resources: ["pods", "pods/log"]
  verbs: ["get", "list"]


另外可以通过设置 resourceNames 表示针对某个特定的对象的操作权限，此时请求所使用的动词不能是 list、watch、create 或者 deletecollection，因为资源名不会出现在 create、list、watch 和 deletecollection 等的 API 请求中。

除了针对 API Object 这些资源外，我们还需要对其他非资源类的 API 进行授权，此时需要通过 nonResourceURLs 资源来指定 URL 进行授权。下面是一个示例，表示对 “/healthz” 及其所有子路径有”GET” 和”POST” 的请求权限。

rules:
- nonResourceURLs: ["/healthz", "/healthz/*"] # 在非资源 URL 中，'*' 代表后缀通配符
  verbs: ["get", "post"]



verbs


一系列动词集合，代表允许对资源执行的操作，本质上可以发起的请求类型。动词选项和 HTTP 请求对应如下：

verb
含义
HTTP 请求
get
获取单个资源
GET，HEAD
list
获取一组资源
GET，HEAD
watch
监听单个或一组资源
GET，HEAD
create
创建资源
POST
update
更新资源
PUT
patch
局部更新资源
PATCH
delete
删除单个资源
DELETE
deletecollection
删除一组资源
DELETE


通过 kubectl get clusterrole  或 kubectl get role --all-namespace 可以查出k8s的所有的 ClusterRole 和 Role。通过 kubectl describe clusterrole <role> 可以看到这些role在哪些资源上有什么样的权限。

下面是一些示例：

允许读取 core API Group 中定义的资源”pods”：
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list", "watch"]


允许读写在”extensions” 和”apps” API Group 中定义的”deployments”：
rules:
- apiGroups: ["extensions", "apps"]
  resources: ["deployments"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]


允许读取”pods” 以及读写”jobs”：
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["batch", "extensions"]
  resources: ["jobs"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]


允许读取一个名为 ”my-config” 的ConfigMap实例（需要将其通过RoleBinding绑定从而限制针对某一个命名空间中定义的一个ConfigMap实例的访问）：
rules:
- apiGroups: [""]
  resources: ["configmaps"]
  resourceNames: ["my-config"]
  verbs: ["get"]


允许读取 core API Group 中的”nodes” 资源，由于Node是集群级别资源，所以此ClusterRole 定义需要与一个 ClusterRoleBinding绑定才能有效。
rules:
- apiGroups: [""]
  resources: ["nodes"]
  verbs: ["get", "list", "watch"]


RoleBinding & ClusterRoleBinding

角色绑定将一个角色中定义的各种权限授予一个或者一组用户。 角色绑定包含了一组相关 subject 主体, subject 包括用户 User、用户组 Group、或者服务账户 Service Account 以及对被授予角色的引用。 

在命名空间中可以通过 RoleBinding 对象授予权限，而集群范围的权限授予则通过 ClusterRoleBinding 对象完成。

ClusterRole 可以通过 RoleBinding 进行角色绑定，但仅对  RoleBinding 所在命名空间有效。

下面示例是一些示例：

在 default 命名空间中将 pod-reader  角色授予用户 jane， 允许用户 jane 从 default 命名空间中读取 pod。

kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: read-pods
  namespace: default
subjects:
- kind: User
  name: jane
  apiGroup: rbac.authorization.k8s.io
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io


ClusterRoleBinding 对象允许在用户组 "manager" 中的任何用户都可以读取集群中任何命名空间中的 secret。

kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: read-secrets-global
subjects:
- kind: Group
  name: manager
  apiGroup: rbac.authorization.k8s.io
roleRef:
  kind: ClusterRole
  name: secret-reader
  apiGroup: rbac.authorization.k8s.io


命令行工具

除了定义 yaml 文武兼外，对于  rolebinding 的操作可以通过命令行方便的完成，下面是一些示例：

在某一特定命名空间内授予 Role 或者 ClusterRole。示例如下：

# 在名为"acme" 的命名空间中将 admin ClusterRole 授予用户"bob"：
kubectl create rolebinding bob-admin-binding --clusterrole=admin --user=bob --namespace=acme

# 在名为"acme" 的命名空间中将 view ClusterRole 授予服务账户"myapp"：
kubectl create rolebinding myapp-view-binding --clusterrole=view --serviceaccount=acme:myapp --namespace=acme


在整个集群中授予 ClusterRole，包括所有命名空间。示例如下：

# 在整个集群范围内将 cluster-admin ClusterRole 授予用户"root"：
kubectl create clusterrolebinding root-cluster-admin-binding --clusterrole=cluster-admin --user=root

# 在整个集群范围内将 system:node ClusterRole 授予用户"kubelet"：
kubectl create clusterrolebinding kubelet-node-binding --clusterrole=system:node --user=kubelet

# 在整个集群范围内将 view ClusterRole 授予命名空间"acme" 内的服务账户"myapp"：
kubectl create clusterrolebinding myapp-view-binding --clusterrole=view --serviceaccount=acme:myapp




准入控制

请求在完成认证和授权之后，对象在被持久化到 etcd 之前，还需要通过一系列的准入控制器进行验证。如同我们业务系统一样，除了需要验证登陆用户的身份、操作权限外，还需要验证用户的操作对不对，比如提交一个表单，要看下必填项是否都填了，手机号码的格式是否正确等，甚至还可能要拦截请求做额外的处理，比如注入请求头做流量着色。

Kubernetes 也一样，需要对外部提交的请求做校验、拦截修改等操作。所谓准入控制器就是一系列的插件，每个插件都有其特定的功能，比如允许哪些请求进入，限定对资源的使用，设定 Pod 的安全策略等。它们作为看门人（gatekeeper）来对发送到 Kubernetes 做拦截验证，从而实现对集群使用方式的管理，


图片来自 https://sysdig.com/blog/kubernetes-admission-controllers/

准入控制器的操作有两种：

mutating ：拦截并修改请求
validating：验证请求的合法性

一个准入控制器可以是只执行 mutating 或者 validating ，也可以两个都执行，先执行 mutating 在执行  validating。

 Kubernetes 本身已经提供了很多的准入控制器插件，可以通过以下命令查看：

$ kube-apiserver -h | grep enable-admission-plugins
CertificateApproval, CertificateSigning, CertificateSubjectRestriction, DefaultIngressClass, DefaultStorageClass, DefaultTolerationSeconds, LimitRanger, MutatingAdmissionWebhook, NamespaceLifecycle, PersistentVolumeClaimResize, Priority, ResourceQuota, RuntimeClass, ServiceAccount, StorageObjectInUseProtection, TaintNodesByCondition, ValidatingAdmissionWebhook


每个准入控制器插件基本都是实现了某一特定的功能，在启动 kube-apiserver 时可以通过设置 --enable-admission-plugins，--disable-admission-plugins 参数来启动或者禁用某些注入控制器。已有的控制器类型可以参考 文档。

动态准入控制

在 Kubernetes 默认的准入控制器中，有两个特殊的控制器：

MutatingAdmissionWebhook
ValidatingAdmissionWebhook

它们以 WebHook 的方式提供扩展能力，我们可以在集群中创建相关的 WebHook 配置并在配置中选择想要关注的资源对象，这样对应的资源对象在执行操作时就可以触发 WebHook，然后我们可以编写具体的响应代码实现准入控制。

MutatingAdmissionWebhook 用来修改用户的请求，执行 mutating 操作，比如修改镜像、添加注解、注入 SideCar 等。我们的 EaseMesh Operator 就是通过 MutatingAdmissionWebhook 实现了 SideCar 和 JavaAgent 的注入。

ValidatingAdmissionWebhook 则只能用来做校验，比如检查命名规范，检查镜像的使用。 MutatingAdmissionWebhook 会在  ValidatingAdmissionWebhook 前执行。

想要自定义准入控制策略，集群需要满足以下条件：

确保 Kubernetes 集群版本至少为 v1.16（以便使用 admissionregistration.k8s.io/v1 API） 或者 v1.9 （以便使用 admissionregistration.k8s.io/v1beta1 API）。
确保启用 MutatingAdmissionWebhook 和 ValidatingAdmissionWebhook 控制器。 
确保启用了 admissionregistration.k8s.io/v1 或者 admissionregistration.k8s.io/v1beta1 API。
为了实现自定义的 WebHook，我们主要需要两步操作：
创建 webhook server
创建 webhook 配置
WebHook Server

所谓 WebHook Server 就是 在  webhook 触发时的响应服务：

本质上是一个 HTTP 服务，接收 POST + JSON 请求
请求和响应都是一个 AdmissionReview 对象，其内部包含 request 和 response 两个对象。
每个请求都有 uid 字段；而响应则必须含有如下字段：
uid 字段：从请求中拷贝的 uid。
allowed: true 或者 false，表示是否允许请求执行

{
  "apiVersion": "admission.k8s.io/v1",
  "kind": "AdmissionReview",
  "response": {
    "uid": "<value from request.uid>",
    "allowed": true
  }
}


因此，我们的任务就是编写一个 HTTP 服务，来接收 AdmissionReview 的请求并返回 AdmissionReview 响应。
WebHook 配置

有了 WebHook server 后，我们就可以创建配置来指定要选择的资源以及响应服务了。
Kubernetes 提供了  MutatingWebhookConfiguration 和 ValidatingWebhookConfiguration  两种 API 对象来让我们动态的创建准入控制的配置。顾名思义，前者用来拦截并修改请求，后者用来验证请求是否正确。下面是一个 ValidatingWebhookConfiguration 配置示例：

apiVersion: admissionregistration.k8s.io/v1
kind: ValidatingWebhookConfiguration
metadata:
  name: "pod-policy.example.com"
webhooks:
- name: "pod-policy.example.com"
  rules:
  - apiGroups:   [""]
    apiVersions: ["v1"]
    operations:  ["CREATE"]
    resources:   ["pods"]
    scope:       "Namespaced"
  clientConfig:
    service:
      namespace: "example-namespace"
      name: "example-service"
    caBundle: "Ci0tLS0tQk...<`caButLS0K"
  admissionReviewVersions: ["v1", "v1beta1"]
  sideEffects: None
  timeoutSeconds: 5



可以看到 API 对象就是用来定义一系列的 webhook 的，每个 webhook 的配置包含以下主要字段：

rules
每个webhook 需要设置一系列规则来确认某个请求是否需要发送给 webhook。每个规则可以指定一个或多个 operations、apiGroups、apiVersions 和 resources 以及资源的 scope：

operations 列出一个或多个要匹配的操作。 可以是 CREATE、UPDATE、DELETE、CONNECT 或 * 以匹配所有内容。
apiGroups 列出了一个或多个要匹配的 API 组。"" 是核心 API 组。"*" 匹配所有 API 组。
apiVersions 列出了一个或多个要匹配的 API 版本。"*" 匹配所有 API 版本。
resources 列出了一个或多个要匹配的资源。
"*" 匹配所有资源，但不包括子资源。
"*/*" 匹配所有资源，包括子资源。
"pods/*" 匹配 pod 的所有子资源。
"*/status" 匹配所有 status 子资源。
scope 指定要匹配的范围。有效值为 "Cluster"、"Namespaced" 和 "*"。 子资源匹配其父资源的范围。在 Kubernetes v1.14+ 版本中才被支持。 默认值为 "*"，对应 1.14 版本之前的行为。
"Cluster" 表示只有集群作用域的资源才能匹配此规则（API 对象 Namespace 是集群作用域的）。
"Namespaced" 意味着仅具有名字空间的资源才符合此规则。
"*" 表示没有范围限制。

下面示例表示匹配针对 apps/v1 和 apps/v1beta1 组中 deployments 和 replicasets 资源的 CREATE 或 UPDATE 请求
apiVersion: admissionregistration.k8s.io/v1
kind: ValidatingWebhookConfiguration
...
webhooks:
- name: my-webhook.example.com
  rules:
  - operations: ["CREATE", "UPDATE"]
    apiGroups: ["apps"]
    apiVersions: ["v1", "v1beta1"]
    resources: ["deployments", "replicasets"]
    scope: "Namespaced"



 
objectSelector
根据发送对象的标签来判断是否拦截，如下面示例，任何带有 foo=bar 标签的对象创建请求都会被拦截触发 webhook。
apiVersion: admissionregistration.k8s.io/v1
kind: MutatingWebhookConfiguration
...
webhooks:
- name: my-webhook.example.com
  objectSelector:
    matchLabels:
      foo: bar
  rules:
  - operations: ["CREATE"]
    apiGroups: ["*"]
    apiVersions: ["*"]
    resources: ["*"]
    scope: "*"
  ...

namespaceSelector
匹配命名空间，根据资源对象所在的命名空间作拦截，下面是我们 EaseMesh 的示例，
针对带有 mesh.megaease.com/mesh-service 标签的，除 easemesh、kube-system、kube-public 之外的命名空间中的对象，如果对象符合 rules 中的定义，则会将请求发送到 webhook server。
apiVersion: admissionregistration.k8s.io/v1
kind: MutatingWebhookConfiguration
...
webhooks:
- name: mesh-injector.megaease.com
  namespaceSelector:
    matchExpressions:
    - key: kubernetes.io/metadata.name
      operator: NotIn
      values:
      - easemesh
      - kube-system
      - kube-public
    - key: mesh.megaease.com/mesh-service
      operator: Exists

 
 
clientConfig
这里配置的就是我们的 Webhook Server 访问地址以及验证信息。当一个请求经由上述选择规则确定要发送到 webhook 后，就会根据 clientConfig 中配置的信息向我们的 WebHook Server 发送请求。
WebHook Server 可以分为集群内和集群外部的服务，如果是集群外部的服务需要配置访问 URL，示例如下：
apiVersion: admissionregistration.k8s.io/v1
kind: MutatingWebhookConfiguration
...
webhooks:
- name: my-webhook.example.com
  clientConfig:
    url: "https://my-webhook.example.com:9443/my-webhook-path"
  ...

 
如果是集群内部的服务，则可以通过配置服务名后通过 Kubernetes 的 DNS 访问到，下面是我们 EaseMesh 中的示例：
➜  ~  |>kubectl get svc -n easemesh                                                            
NAME                                               TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)                                        AGE
easemesh-operator-service                          ClusterIP   10.233.53.203   <none>        8443/TCP,9090/TCP

 
clientConfig:
    caBundle:  LS0...tCg==
    service:
      Name:        easemesh-operator-service
      Namespace:   easemesh
      Path:        /mutate
      Port:        9090


 
下面是官方文档中提供的一个例子 admission-controller-webhook-demo 。
该应用的目的是要限制 Pod 的权限，尽量避免以 root 用户的身份运行：
如果 Pod 没有明确设置 runAsNonRoot，则默认添加 runAsNonRoot: true ；如果没有设置 runAsUser 则默认添加 runAsUser: 1234 配置。
如果设置 runAsNonRoot 为 true，则校验 runAsUser 是否等于 0（root)，不等于的话 Pod 会创建失败。

var runAsNonRoot *bool
var runAsUser *int64
if pod.Spec.SecurityContext != nil {
  runAsNonRoot = pod.Spec.SecurityContext.RunAsNonRoot
  runAsUser = pod.Spec.SecurityContext.RunAsUser
}

// Create patch operations to apply sensible defaults, if those options are not set explicitly.
var patches []patchOperation
if runAsNonRoot == nil {
  patches = append(patches, patchOperation{
     Op:   "add",
     Path: "/spec/securityContext/runAsNonRoot",
     // The value must not be true if runAsUser is set to 0, as otherwise we would create a conflicting
     // configuration ourselves.
     Value: runAsUser == nil || *runAsUser != 0,
  })

  if runAsUser == nil {
     patches = append(patches, patchOperation{
        Op:    "add",
        Path:  "/spec/securityContext/runAsUser",
        Value: 1234,
     })
  }
} else if *runAsNonRoot == true && (runAsUser != nil && *runAsUser == 0) {
  // Make sure that the settings are not contradictory, and fail the object creation if they are.
  return nil, errors.New("runAsNonRoot specified, but runAsUser set to 0 (the root user)")
}

 
上面是主要的 mutating 逻辑代码，下面是启动一个 http server 来处理请求，将上面的方法传进去作为 handler 。
func main() {
  certPath := filepath.Join(tlsDir, tlsCertFile)
  keyPath := filepath.Join(tlsDir, tlsKeyFile)

  mux := http.NewServeMux()
  mux.Handle("/mutate", admitFuncHandler(applySecurityDefaults))
  server := &http.Server{
     // We listen on port 8443 such that we do not need root privileges or extra capabilities for this server.
     // The Service object will take care of mapping this port to the HTTPS port 443.
     Addr:    ":8443",
     Handler: mux,
  }
  log.Fatal(server.ListenAndServeTLS(certPath, keyPath))
}
func main() {
  certPath := filepath.Join(tlsDir, tlsCertFile)
  keyPath := filepath.Join(tlsDir, tlsKeyFile)

  mux := http.NewServeMux()
  mux.Handle("/mutate", admitFuncHandler(applySecurityDefaults))
  server := &http.Server{
     // We listen on port 8443 such that we do not need root privileges or extra capabilities for this server.
     // The Service object will take care of mapping this port to the HTTPS port 443.
     Addr:    ":8443",
     Handler: mux,
  }
  log.Fatal(server.ListenAndServeTLS(certPath, keyPath))
}

 
将上面的程序部署到集群
$ kubectl get service -n webhook-demo
NAME             TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)   AGE
webhook-server   ClusterIP   10.108.206.71   <none>        443/TCP   7s

$ kubectl get pods -n webhook-demo
NAME                              READY   STATUS    RESTARTS   AGE
webhook-server-69c78cb569-s9dw6   1/1     Running   0          10s

 
Webhook Server 部署好就可以配置准入控制的配置了，因为要修改请求，因此要创建 MutatingWebhookConfiguration，下面是创建好的配置内容：
$ kubectl describe mutatingwebhookconfigurations demo-webhook
Name:         demo-webhook
Namespace:
Labels:       <none>
Annotations:  <none>
API Version:  admissionregistration.k8s.io/v1
Kind:         MutatingWebhookConfiguration
Metadata:
Webhooks:
  Admission Review Versions:
    v1
    v1beta1
  Client Config:
    Ca Bundle:  LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURQekNDQWllZ0F3SUJBZ0lVZUNs
    Service:
      Name:        webhook-server
      Namespace:   webhook-demo
      Path:        /mutate
      Port:        443
  Failure Policy:  Fail
  Match Policy:    Equivalent
  Name:            webhook-server.webhook-demo.svc
  Namespace Selector:
  Object Selector:
  Reinvocation Policy:  Never
  Rules:
    API Groups:
    API Versions:
      v1
    Operations:
      CREATE
    Resources:
      pods
    Scope:          *
  Side Effects:     None
  Timeout Seconds:  10
Events:             <none>

 
可以看到其 Rule 是当收到 Pod 的创建请求时，会将发送请求到我们的 Webhook Server。
下面测试 Pod 的创建，首先是默认情况下，如果不设置会自动配置 runAsNoneRoot 和 runAsUser
 
apiVersion: v1
kind: Pod
metadata:
  name: pod-with-defaults
  labels:
    app: pod-with-defaults
spec:
  restartPolicy: OnFailure
  containers:
  - name: busybox
    image: busybox
    command: ["sh", "-c", "echo I am running as user $(id -u)"]

 
$ kubectl get pods pod-with-defaults -o yaml
apiVersion: v1
kind: Pod
metadata:
   labels:
    app: pod-with-defaults
  name: pod-with-defaults
  namespace: default

spec:
  containers:
  - command:
    - sh
    - -c
    - echo I am running as user $(id -u)
    image: busybox
    imagePullPolicy: Always
    name: busybox
  securityContext:
    runAsNonRoot: true
    runAsUser: 1234

 
如果是 runAsNonRoot 如果设置为 true，但是 runAsUser 设置 设置为 0，请求会被拦截并报错：
apiVersion: v1
kind: Pod
metadata:
  name: pod-with-conflict
  labels:
    app: pod-with-conflict
spec:
  restartPolicy: OnFailure
  securityContext:
    runAsNonRoot: true
    runAsUser: 0
  containers:
    - name: busybox
      image: busybox
      command: ["sh", "-c", "echo I am running as user $(id -u)"]

 
$ kubectl apply -f examples/pod-with-conflict.yaml
Error from server: error when creating "examples/pod-with-conflict.yaml": admission webhook "webhook-server.webhook-demo.svc" denied the request: runAsNonRoot specified, but runAsUser set to 0 (the root user)

 
可以看到请求被正确拦截了。以上是动态准入控制的简单介绍，更多的细节可以参考官方文档 和博客。
 
Security Context

上述 RBAC、准入控制等策略都是针对 api-server 的安全访问控制，如果外部攻击者攻破了 API Server 的访问控制成功部署了 Pod，并在容器中运行攻击代码，依然是可以对我们的系统造成损害。因此我们还需要设置 Pod 的操作权限，不能让 Pod “为所欲为”。

宿主机命名空间

 Pod 有自己的网络、PID、IPC 命名空间，因此同一 Pod 中的容器可以共享网络，可以进行进程间通信以及只看到自己的进程树。如果某些 Pod 需要使用宿主机默认的命令空间，则需要额外进行设置。
网络命名空间

可以通过 hostNetwork: true 配置来使 Pod 直接使用网络的命名空间。
apiVersion: v1
kind: Pod
metadata:
  name: pod-with-host-network
spec:
  hostNetwork: true                    
  containers:
  - name: main
    image: alpine
    command: ["/bin/sleep", "999999"]


这样 Pod 创建后其网络用的就是宿主机的网络，在 Pod 中执行 ifconfig 命令查看网络设备会看到其所在宿主机的网络设备列表。

$ kubectl exec -it pod-with-host-network -- ifconfig
eth0      Link encap:Ethernet  HWaddr 52:54:00:22:84:B5
          inet addr:172.19.0.3  Bcast:172.19.15.255  Mask:255.255.240.0
          inet6 addr: fe80::5054:ff:fe22:84b5/64 Scope:Link
          UP BROADCAST RUNNING MULTICAST  MTU:1500  Metric:1
          RX packets:5089655 errors:0 dropped:0 overruns:0 frame:0
          TX packets:5061521 errors:0 dropped:0 overruns:0 carrier:0
          collisions:0 txqueuelen:1000
          RX bytes:1952900135 (1.8 GiB)  TX bytes:1062809752 (1013.5 MiB)

lo        Link encap:Local Loopback
          inet addr:127.0.0.1  Mask:255.0.0.0
          inet6 addr: ::1/128 Scope:Host
          UP LOOPBACK RUNNING  MTU:65536  Metric:1
          RX packets:1334609 errors:0 dropped:0 overruns:0 frame:0
          TX packets:1334609 errors:0 dropped:0 overruns:0 carrier:0
          collisions:0 txqueuelen:1000
          RX bytes:159996559 (152.5 MiB)  TX bytes:159996559 (152.5 MiB)
...






像 Kubernetes 的控制平面组件 kube-apiserver 等都是设置了该选项，从而使得它们的行为与不在 Pod 中运行时相同。

$ kubectl get pods -n kube-system kube-apiserver-vm-0-7-ubuntu -o yaml
apiVersion: v1
kind: Pod
metadata:
  name: kube-apiserver-vm-0-7-ubuntu
  namespace: kube-system
spec:
  containers:
  - command:
    - kube-apiserver
  
    image: k8s.gcr.io/kube-apiserver:v1.22.3
    imagePullPolicy: IfNotPresent
  ...
  hostNetwork: true



另外还可以通过设置 hostPort 使容器使用所在节点的主机端口而不是直接共享命名空间。

apiVersion: v1
kind: Pod
metadata:
  name: kubia-hostport
spec:
  containers:
  - image: luksa/kubia
    name: kubia
    ports:
    - containerPort: 8080     
      hostPort: 9000          
      protocol: TCP


这样当访问 Pod 所在节点上的 9000 端口时会访问到 Pod 中容器，因为要占用主机端口，因此如果有多个副本的话这些副本不能被调度到同一个节点。

该功能最初主要是用来暴露通过 DaemonSet 在每个节点上运行的服务，后来也用来做 Pod 的调度，保证相同的 Pod 不能被部署到同一个节点，现在已经被 Pod 非亲和的调度方式所取代。

PID & IPC 命名空间

除了网络命名空间，Pod 还可以直接使用宿主机的 IPC 和 PID 命名空间，从而看到宿主机所有的进程，以及与宿主机的进程进行通信。

apiVersion: v1
kind: Pod
metadata:
  name: pod-with-host-pid-and-ipc
spec:
  hostPID: true                      
  hostIPC: true                      
  containers:
  - name: main
    image: alpine
    command: ["/bin/sleep", "999999"]


Pod Security Context

除了使用宿主机的命名空间，还是设置安全上下文来定义 Pod 或者容器的特权和访问控制设置。包含但不限于一下配置：
指定容器中运行进程的用户和用户组，从而简介判定对对象、文件的操作权限。
设置 SELinux 选项，加强对容器的限制
以特权模式或者非特权模式运行，特权模式下容器对宿主机节点内核具有完整的访问权限
配置内核功能，以细粒度的方式配置内核访问权限
AppArmor：使用程序框架来限制个别程序的权能。
Seccomp：过滤进程的系统调用。
readOnlyRootFilesystem：以只读方式加载容器的根文件系统。
下面是一些使用示例：
 
设置容器的安全上下文

设置非 root 用户执行

容器运行的用户可以在构建镜像时指定，如果攻击者获取到 Dockerfile 并设置的 root 用户，如果 Pod 挂载了宿主机目录，此时就会对宿主机的目录有完整的访问权限。如果是非 root 用户则不会有完整的权限。

apiVersion: v1
kind: Pod
metadata:
  name: pod-run-as-non-root
spec:
  containers:
  - name: main
    image: alpine
    command: ["/bin/sleep", "999999"]
    securityContext:                   
      runAsNonRoot: true 

设置特权模式运行

如果容器获取内核的完整权限，需要在宿主机能做任何事，就可以设置为特权模式。

apiVersion: v1
kind: Pod
metadata:
  name: pod-privileged
spec:
  containers:
  - name: main
    image: alpine
    command: ["/bin/sleep", "999999"]
    securityContext:
      privileged: true



像 kube-proxy 需要修改 iptables 规则，因此就开启了特权模式。

$ kubectl get pods kube-proxy-fschm -n kube-system -o yaml
apiVersion: v1
kind: Pod
metadata:
  name: kube-proxy-fschm
  namespace: kube-system
spec:
    image: k8s.gcr.io/kube-proxy:v1.22.3
    imagePullPolicy: IfNotPresent
    name: kube-proxy
    securityContext:
      privileged: true


为容器单独添加或者禁用内核功能

除了赋予完整权限的特权模式，我们还可以细粒度的添加或者删除内核操作权限，下面是一个例子，允许修改系统时间，但不允许容器修改文件的所有者。
apiVersion: v1
kind: Pod
metadata:
  name: pod-add-settime-capability
spec:
  containers:
  - name: main
    image: alpine
    command: ["/bin/sleep", "999999"]
    securityContext:                     
      capabilities:                      
        add:                             
        - SYS_TIME
        drop:                   
        - CHOWN


除了为容器单独设置上下文，一部分配置可以在 Pod 层面设置，表示对 Pod 中所有的容器生效，如果容器也设置了则会覆盖掉 Pod 的设置，另外 Pod 也要独有的上下文配置，可以参考 官方文档，这里就不做赘述了。

Pod Security

安全上下文是创建 Pod 的用户指定的，除此之外我们还需要在集群层面来保证用户不能滥用相关的权限。因此之前 Kubernetes 提供了集群层面 PSP（PodSecurityPolicy）对象来让集群员来定义用户的 Pod 能否使用各种安全相关的特性。

可以通过 PSP 来统一批量设置相关的安全设置，然后通过 RBAC 为不同的用户赋予不同 PSP，然后在创建 Pod 时指定用户，就可以实现针对不用的 Pod 应用不同的安全策略了。下面是一个例子：

通过创建一个 PSP 同时指定用户、是否使用宿主机命名空间、启用和禁用内核权限等。

apiVersion: extensions/v1beta1
kind: PodSecurityPolicy
metadata:
  name: default
spec:
  hostIPC: false                 
  hostPID: false                 
  hostNetwork: false             
  hostPorts:                     
  - min: 10000                   
    max: 11000                   
  - min: 13000                   
    max: 14000                   
  privileged: false              
  readOnlyRootFilesystem: true   
  runAsUser:                     
    rule: RunAsAny               
  fsGroup:                       
    rule: RunAsAny   
  allowedCapabilities:            
  - SYS_TIME                      
  defaultAddCapabilities:         
  - CHOWN                         
  requiredDropCapabilities:       
  - SYS_ADMIN                     
  - SYS_MODULE                    
然后可以通过 RBAC 进行设置，鉴于 PSP 已经被弃用，并将在 1.25 版本移除，这里就不多做讲解了。取而代之的是使用新的 PodSecurity 进行安全相关的设置，截止到 1.23 该特性处于 beta 阶段。下面简单看一下

PodSecurity 是一个准入控制器，其由松到紧定义了三种安全级别的策略：

privileged: 特权策略，表示几乎没有限制。提供最大可能范围的权限许可

baseline：基线策略，允许使用默认的（规定最少）Pod 配置。

Restricted：限制性最强的策略，遵循保护 Pod 针对最佳实践。

策略具体关联都的权限控制可以查看 pod-security-standards 文档。有了策略后，我们可以在命名空间上声明针对各个安全策略的处理方式。具体处理方式也有三种：

enforce：强制执行，如果违反策略，则 od 创建请求会被拒绝。
audit：执行监听，如果违反策略，则会触发记录监听日志，但 Pod 可以被创建成功。
warn: 执行警告，如果违反策略，Pod 创建时会提示用户，但依然可以被创建成成功。

针对每种方式，Kubernetes 提供了两个标签来指定处理的安全级别和 Kubernetes minor 版本：

pod-security.kubernetes.io/<MODE>: <LEVEL>，model 必须是 enforce、audit、warn 之一，level 必须是 privileged、baseline、restricted 之一。

pod-security.kubernetes.io/<MODE>-version: <VERSION>：表示策略执行的版本，必须是 Kubernetes minor 版本或者 latest。

然后就可以在 namespace 上添加标签来进行安全限制了，下面的例子表示，在 my-baseline-namespace 来命名空间创建的 Pod:

如果不满足 baseline 策略，会被拒绝创建。
如果不满足 restricted 策略，则会记录监听日志以及向用户发出经过。其策略版本是 1.23 版本。
apiVersion: v1
kind: Namespace
metadata:
  name: my-baseline-namespace
  labels:
    pod-security.kubernetes.io/enforce: baseline
    pod-security.kubernetes.io/enforce-version: v1.23

    # We are setting these to our _desired_ `enforce` level.
    pod-security.kubernetes.io/audit: restricted
    pod-security.kubernetes.io/audit-version: v1.23
    pod-security.kubernetes.io/warn: restricted
    pod-security.kubernetes.io/warn-version: v1.23



Network Policy

Network Policy 类似于 AWS 的安全组，是一组 Pod 间及与其他网络端点间所允许的通信规则。NetworkPolicy 资源使用 Label 和 Selector 选择 Pod，并定义选定 Pod 所允许的通信规则。

下面是一个 NetworkPolicy 的示例:

apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: test-network-policy
  namespace: default
spec:
  podSelector:
    matchLabels:
      role: db
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - ipBlock:
        cidr: 172.17.0.0/16
        except:
        - 172.17.1.0/24
    - namespaceSelector:
        matchLabels:
          project: myproject
    - podSelector:
        matchLabels:
          role: frontend
    ports:
    - protocol: TCP
      port: 6379
  egress:
  - to:
    - ipBlock:
        cidr: 10.0.0.0/24
    ports:
    - protocol: TCP
      port: 5978


上述的示例意思是：

对  default 命名空间下带有标签 role=db 的 Pod 进行如下配置

（Ingress 规则）允许以下客户端连接到被选中 Pod 的 6379 TCP 端口：
default 命名空间下任意带有 role=frontend 标签的 Pod
带有 project=myproject 标签的任意命名空间中的 Pod
IP 地址范围为 172.17.0.0–172.17.0.255 和 172.17.2.0–172.17.255.255（即，除了 172.17.1.0/24 之外的所有 172.17.0.0/16）的外部节点

（Egress 规则）允许被选中 Pod 可以访问以下节点
地址为 10.0.0.0/24 下 的 5978  端口

podSelector

每个 NetworkPolicy 都包括一个 podSelector ，它根据标签选安定一组 Pod 以应用其所定义的规则。示例中表示选择带有 "role=db" 标签的 Pod。如果 podSelector 为空，表示选择namespace下的所有 Pod。

policyTypes

表示定义的规则类型，包含 Ingress 或 Egress 或两者兼具。

Ingress 表示所选 Pod 的入网规则，即哪些端点可以访问该 Pod。
Egress 表示 Pod 的出网规则，即该 Pod 可以访问那些端点。


Ingress / Egress 下用来限定规则的方式有四种：

podSelector：选择相同命名空间下的特定 Pod 。
namespaceSelector：选择特定命名空间下的所有 Pod 。
podSelector 与 namespaceSelector：选择特定命名空间下的特定 Pod。定义时注意和之上两者的区别

同时指定 namespaceSelector 和 podSelector，需要同时满足任意两个条件。

ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          user: alice
      podSelector:
        matchLabels:
          role: client



各自指定，只满足其中一个条件即可：

 ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          user: alice
    - podSelector:
        matchLabels:
          role: client


ipBlock：IP CIDR 范围，一般都是外部地址，因为 Pod 地址是临时性、经常变化的。
ports

ports 可以被访问的端口或者可以访问的外部端口。在定义时指明协议和端口即可。一般都是 TCP 协议，

apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: test-network-policy
  namespace: default
spec:
....
  ingress:
  - from:
    - ipBlock:
        cidr: 172.17.0.0/16
    ports:
    - protocol: TCP
      port: 6379
  egress:
  - to:
    - ipBlock:
        cidr: 10.0.0.0/24
    ports:
    - protocol: TCP
      port: 5978




从 1.20 版本开始默认也支持 SCTP 协议，如果想关掉需要修改 apiserver 的启动配置 --feature-gates=SCTPSupport=false。

另外现在可以指定一组端口，该特性在 1.22 版本处于 beta 状态，使用示例如下：

apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: multi-port-egress
  namespace: default
spec:
  podSelector:
    matchLabels:
      role: db
  policyTypes:
  - Egress
  egress:
  - to:
    - ipBlock:
        cidr: 10.0.0.0/24
    ports:
    - protocol: TCP
      port: 32000
      endPort: 32768


这里表示选中的 Pod 可以访问 10.0.0.0/24 网段的 32000 ~ 32768 端口。这里有几个要求：
endPort 必须大于等于 port
endPort 不能被单独定义，必须先指定 port
端口都是数字

下面是一些特殊的规则示例：

拒绝所有入站流量

---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-ingress
  namespace: default
spec:
  podSelector: {}
  policyTypes:
  - Ingress


允许所有入站流量

apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-all-ingress
  namespace: default
spec:
  podSelector: {}
  ingress:
  - {}
  policyTypes:
  - Ingress


拒绝所有出站流量

apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-egress
  namespace: default
spec:
  podSelector: {}
  policyTypes:
  - Egress


允许所有出站流量

apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-all-egress
spec:
  podSelector: {}
  egress:
  - {}
  policyTypes:
  - Egress




拒绝所有入站和出站流量

apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress



最后需要注意的是 Network Policy 的功能是由网络插件实现的，因此是否可以使用该特性取决于我们使用的网络插件。像 Calico、Weave 都对该功能做了支持，但 Flannel 本身不支持，需要结合 Calico 使用才性能，参考文档  Installing Calico for policy and flannel (aka Canal) for networking。


以上是对 Kubernetes 安全相关的简单概述，在实际云原生环境里，其安全性按层分需要从所谓的 4C（Cloud, Clusters, Containers, and Code.）四个层面来保证。Kubernetes 只是其中的一层。


Kubernetes 应用封装与扩展

Kustomize

当我们需要在 Kubernetes 部署应用时，往往是编写许多 yaml 文件来部署各种资源对象，并且同一个应用针对不同的环境可能需要编写不同的 yaml 文件，这个过程往往非常繁琐。

为了解决这个问题 Kubernetes 推出了  Kustomize 工具，官方称为 Kubernetes 原生配置管理工具。Kustomize 将我们应用部署所需要的信息分为不变的 base 配置和容易变化 overlay 配置，最终将文件合起来成为一个完整的定义文件。类似于 Docker 镜像分层的概念，最终所有的层次合并起来成为一个完整的应用镜像。

Kustomize 最常见的用途就是根据不同的环境生成不同的部署文件，在基准 base 文件的基础上，定义不同的 Overlay 文件，在通过 Kustomization 文件的定义进行整合。
base 文件，基准 yaml 文件
overlay 文件，针对不同需求设置的 yaml 文件
Kustomization.yaml：整合 base 和 overlay 以生成完整的部署配置。

kubectl 命令已经内置了 kustomize 命令，当我们定义好上述文件后，可以通过 kubectl kustomize overlays/dev 查看生成的部署内容，通过  kubectl apply -k overlays/dev
直接执行部署。


下面是一个简单的示例，我们部署一个应用，在测试环境部署一个 1 个副本并且使用 test 镜像，在生产环境部署 5 个副本并使用最新的镜像。正常情况下我们需要定义两个完整的 yaml 文件分别去生产和测试环境部署，如果使用 Kustomize 可以想下面这样定义：

$ tree app
app
|-- base
|   |-- deployment.yml
|   |-- kustomization.yaml
|   |-- service.yaml
`-- overlays
    |-- dev
    |   |-- deployment.yml
    |   `-- kustomization.yaml
    `-- prod
        |-- deployment.yml
        `-- kustomization.yaml



首先我们定义 base 文件以及 kustomization 文件：

Deployment 文件

apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend-deployment
spec:
  replicas: 3
  selector:
    matchLabels:
      app: frontend-deployment
  template:
    metadata:
      labels:
        app: frontend-deployment
    spec:
      containers:
      - name: app
        image: foo/bar:latest
        ports:
        - name: http
          containerPort: 8080
          protocol: TCP


Service 文件

apiVersion: v1
kind: Service
metadata:
  name: frontend-service
spec:
  ports:
  - name: http
    port: 8080
  selector:
    app: frontend-deployment


Kustomization 文件

通过该文件将所有 base 文件整合后统一部署。

apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
- deployment.yml
- service.yaml


通过 kubectl kustomize <path> 命令查看最终导出的部署文件内容。

$ kubectl kustomize kustomize/base
apiVersion: v1
kind: Service
metadata:
  name: frontend-service
spec:
  ports:
  - name: http
    port: 8080
  selector:
    app: frontend-deployment
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend-deployment
spec:
  replicas: 3
  selector:
    matchLabels:
      app: frontend-deployment
  template:
    metadata:
      labels:
        app: frontend-deployment
    spec:
      containers:
      - image: foo/bar:latest
        name: app
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP



有了 base 后我们针对 dev 和 prod 环境做不同的设置，

test 环境，设置副本为 1，镜像为 test

apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend-deployment
spec:
  replicas: 1
  template:
    spec:
      containers:
      - image: foo/bar:test
        name: app


apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
bases:
- ../../base
patchesStrategicMerge:
- deployment.yml


完成后通过 kubectl kustomize overlays/dev 查看

$ kubectl kustomize overlays/dev
apiVersion: v1
kind: Service
metadata:
  name: frontend-service
spec:
  ports:
  - name: http
    port: 8080
  selector:
    app: frontend-deployment
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: frontend-deployment
  template:
    metadata:
      labels:
        app: frontend-deployment
    spec:
      containers:
      - image: foo/bar:test
        name: app
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP




可以看到副本是 1，镜像 tag 是 test，此时执行  kubectl apply -k overlays/dev 命令就可以直接部署上述配置了。

通过 kustomize 我们可以将部署所需的文件在 base 里面一次写好，后续不同的开发、运维人员如果不用不同的配置，只需要增加 overlay 来打补丁就行了。使用 overlay 打补丁的好处时既不会像 Ansible 那样需要通过字符替换对元文件造成入侵，也不需要学习额外的 DSL 语法。只需要定义好 yaml 通过一条命令就可以将方服务一次性部署好。

Kustomize 还可以生成 ConfigMap、Secret 等对象，具体细节可以参考 文档，另外通过 https://kustomize.io/tutorial 可以通过上传自己的 yaml 文件然后在线编辑生成 kustomize 文件。

Helm

Kustomize 本身可以使我们以相对便捷的方式分离开发和运维工作，优点是轻量便捷，但其功能也相对不足。虽然能简化对不同场景下的重复配置，但其实我们该写的配置还是要写，只是不用重复写而已，并且除了安装部署外，应用还有更新、回滚、多版本、依赖项维护等操作，Kustomize 无法完善的提供。

为了解决 Kubernetes 中应用部署维护的问题，后续出现了 Helm ，其定位很简单：
操作系统中都有包管理器，比如 ubuntu 有 apt-get 和 dpkg 格式的包，RHEL 系的有 yum 和 RPM 格式的包，而 Kubernetes 作为云原生时代的操作系统，Helm 就是要成为这个操作系统的应用包管理工具。

Helm 提供格式为 chart 的包管理，通过 Helm 包管理器，我们可以很方便的从应用仓库下载、安装部署、升级、回滚、卸载程序，并且仓库中有完整的依赖管理和版本变更信息

关于具体操作可以参考 官方文档 和动物园的书籍  《Learning Helm》，中文翻译版叫《Helm学习指南：Kubernetes上的应用程序管理》，这里仅做一个入门性质的介绍。

应用安装

首先看一下如何使用 helm 来安装、部署应用，我们可以用 apt-get 的作对比，在 Ubuntu 安装某应用时，我们需要添加某个仓库地址，然后执行 sudo apt-get update，完成后才会执行 sudo apt-get install <name> 安装应用。Helm 的使用和 apt-get 命令的步骤基本一致，如下：

添加仓库并更新

$ helm repo add bitnami https://charts.bitnami.com/bitnami
"bitnami" has been added to your repositories

$ helm repo update
Hang tight while we grab the latest from your chart repositories...
...Successfully got an update from the "bitnami" chart repository
Update Complete. ⎈Happy Helming!⎈




安装应用

$ helm install bitnami/mysql --generate-name
NAME: mysql-1638139621
LAST DEPLOYED: Mon Nov 29 06:47:04 2021
NAMESPACE: default
STATUS: deployed
REVISION: 1
TEST SUITE: None
NOTES:
CHART NAME: mysql
CHART VERSION: 8.8.13
APP VERSION: 8.0.27

** Please be patient while the chart is being deployed **

Tip:
    ....


查看应用状态

$ helm list
NAME            	NAMESPACE	REVISION	UPDATED                                	STATUS  	CHART            	APP VERSION
mysql-1638139621	default  	1       	2021-11-29 06:47:04.581748474 +0800 CST	deployed	mysql-8.8.13     	8.0.27

$ helm status mysql-1638139621
NAME: mysql-1638139621
LAST DEPLOYED: Mon Nov 29 06:47:04 2021
NAMESPACE: default
STATUS: deployed
REVISION: 1
TEST SUITE: None
NOTES:
CHART NAME: mysql
CHART VERSION: 8.8.13
APP VERSION: 8.0.27

** Please be patient while the chart is being deployed **

Tip:

  Watch the deployment status using the command: kubectl get pods -w --namespace default

Services:

  echo Primary: mysql-1638139621.default.svc.cluster.local:3306
...


当然我们也可以直接查看 Kubernetes 的对象

$ kubectl get statefulsets.apps
NAME               READY   AGE
mysql-1638139621   0/1     13s


卸载应用

$ helm uninstall mysql-1638139621
release "mysql-1638139621" uninstalled

$ kubectl get statefulsets.apps
No resources found in default namespace.


应用创建

Helm 有三个主要概念：

Chart：Helm 提出的包封装格式，就是 Ubuntu 中的 dpkg 包一样，用来封装我们的应用，包含所有的依赖信息。比如我们上面安装的 MySQL，就是一个完整的 Chart。
Release: 相当于版本，Kubernetes 经常针对一个应用部署多个版本，每个版本就是一个 Release。
Repository：应用仓库，用来存储 Chart。

下面简单看下如何编写一个 Chart 

创建 chart

Helm 提供了 create 命令供我们快速创建 chart。

$ helm create  my-app
Creating my-app

$ tree my-app
my-app
|-- charts
|-- Chart.yaml
|-- templates
|   |-- deployment.yaml
|   |-- _helpers.tpl
|   |-- hpa.yaml
|   |-- ingress.yaml
|   |-- NOTES.txt
|   |-- serviceaccount.yaml
|   |-- service.yaml
|   `-- tests
|       `-- test-connection.yaml
`-- values.yaml


指定名称执行命令后会自动生成同名目录以及相关文件：

charts 目录: 存储依赖的 chart
Chart.yaml：存放 chart 的元信息以及一些 chart 空间。
template 目录：存放最终生成 Kubernetes 清单 yaml 的模板文件
template.test: 测试文件，不会被按安装到集群中，可以由 helm test 执行测试。
values.yaml：定义值，在 helm 渲染模板时传递给模板覆盖默认值。

默认生成的 chart 是一个 Nginx 的应用，我们可以直接安装运行

$ helm install nginx-1-16  .
NAME: nginx-1-16
LAST DEPLOYED: Tue Nov 30 06:17:38 2021
NAMESPACE: default
STATUS: deployed
REVISION: 1
NOTES:
1. Get the application URL by running these commands:
  export POD_NAME=$(kubectl get pods --namespace default -l "app.kubernetes.io/name=my-app,app.kubernetes.io/instance=nginx-1-16" -o jsonpath="{.items[0].metadata.name}")
  export CONTAINER_PORT=$(kubectl get pod --namespace default $POD_NAME -o jsonpath="{.spec.containers[0].ports[0].containerPort}")
  echo "Visit http://127.0.0.1:8080 to use your application"
  kubectl --namespace default port-forward $POD_NAME 8080:$CONTAINER_PORT

$ kubectl get deployments.apps
NAME                  READY   UP-TO-DATE   AVAILABLE   AGE
nginx-1-16-my-app     1/1     1            1           56s




可以看到 Nginx 的 Deployment 已经创建好了。下面是它的 Deployment 的模板和 values.yaml 部分内容：

templates/deployment.yaml

apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "my-app.fullname" . }}
  labels:
    {{- include "my-app.labels" . | nindent 4 }}
spec:
  {{- if not .Values.autoscaling.enabled }}
  replicas: {{ .Values.replicaCount }}
  {{- end }}
  selector:
    matchLabels:
      {{- include "my-app.selectorLabels" . | nindent 6 }}
  template:
    metadata:


values.yaml

# Default values for my-app.
# This is a YAML-formatted file.
# Declare variables to be passed into your templates.

replicaCount: 1

image:
  repository: nginx
  pullPolicy: IfNotPresent
  # Overrides the image tag whose default is the chart appVersion.
  tag: ""

imagePullSecrets: []
nameOverride: ""
fullnameOverride: ""


可以看到最终部署的对象是基于 template 和 values 渲染出来的。现在我们需要部署一个 3 节点的 Nginx ，将 values.yaml 中的 replicaCount 改成 3 然后执行部署。

$ cd ~/helm-chart/my-app$ kubectl get deployments.apps
$ helm install nginx-1-16-3 .
NAME: nginx-1-16-33
LAST DEPLOYED: Tue Nov 30 22:08:47 2021
NAMESPACE: default
STATUS: deployed
REVISION: 1
NOTES:
1. Get the application URL by running these commands:
  export POD_NAME=$(kubectl get pods --namespace default -l "app.kubernetes.io/name=my-app,app.kubernetes.io/instance=nginx-1-16-3" -o jsonpath="{.items[0].metadata.name}")
  export CONTAINER_PORT=$(kubectl get pod --namespace default $POD_NAME -o jsonpath="{.spec.containers[0].ports[0].containerPort}")
  echo "Visit http://127.0.0.1:8080 to use your application"
  kubectl --namespace default port-forward $POD_NAME 8080:$CONTAINER_PORT



$ cd ~/helm-chart/my-app$ kubectl get deployments.apps
NAME                   READY   UP-TO-DATE   AVAILABLE   AGE

nginx-1-16-3-my-app   3/3     3            3           7s


可以看到我们修改之后副本为 3 个的新的 chart 已经部署好了。
应用发布

Chart 写好后我们就可以发布到存储库了，所有 chart 的存储库都含有一个 index.yaml 索引文件，记录了所有可用的 chart 及其版本以及各自的下载位置。下面是一个 index.yaml 的实例：

apiVersion: v1
entries:
  first-chart:
  - apiVersion: v2
    appVersion: 1.16.0
    created: "2021-11-30T06:46:36.769746109+08:00"
    description: A Helm chart for Kubernetes
    digest: 5dff0cfeafa00d9a87e9989586de3deda436a05fca118df03aa3469221866a8d
    name: first-chart
    type: application
    urls:
    - src/first-chart-0.1.0.tgz
    version: 0.1.0
  my-app:
  - apiVersion: v2
    appVersion: 1.16.0
    created: "2021-11-30T06:46:36.770983392+08:00"
    description: A Helm chart for Kubernetes
    digest: 8256153f37ed0071e81fa2fe914e7bcf82e914bec951dadc5f2645faa38c4021
    name: my-app
    type: application
    urls:
    - src/my-app-0.2.0.tgz
    version: 0.2.0
  - apiVersion: v2
    appVersion: 1.16.0
    created: "2021-11-30T06:46:36.770313312+08:00"
    description: A Helm chart for Kubernetes
    digest: c2865e2c9d0a74044b7d8ff5471df7fd552bc402b5240e01cf02e116ee5f800e
    name: my-app
    type: application
    urls:
    - src/my-app-0.1.0.tgz
    version: 0.1.0
generated: "2021-11-30T06:46:36.768952982+08:00"


可以看到该库包含了 first-chart 和 my-app 两个 chart，my-app 有两个版本分别是 0.1.0 和 0.2.0，并且包含了各个版本的 url 下载路径。

我们可以使用 ChartMuseum、Google 云端存储或者自己搭建静态 web 服务器来实现存储库，如果 Chart 可以公开的话 Github Pages 也是一个非常好的选择。下面以 Github Pages 为例看下如何使用存储库：


创建 Github repo 并设置 Pages

首先我们在 Github 创建一个 public 仓库


一个非常好的仓库创建完成后设置 Pages，选择 main 分支，表示每次 main 分支更新时都会重新部署 Github Pages 站点，如果有自定义的域名也可以设置域名。这里设置的域名是 https://zouyingjie.cn/naive-charts-repo/。




添加 chart 到存储库

GIthub 仓库创建完成后，我们可以 clone 到本地然后添加 index.yaml 文件以及 Chart，将其转为真正的 chart 存储库。



Clone 项目 并创建 Chart

$ git clone https://github.com/zouyingjie/naive-charts-repo.git
Cloning into 'naive-charts-repo'...
remote: Enumerating objects: 3, done.
remote: Counting objects: 100% (3/3), done.
remote: Total 3 (delta 0), reused 0 (delta 0), pack-reused 0
Unpacking objects: 100% (3/3), done.

$ cd naive-charts-repo
$ mkdir src

$ helm create src/naive-nginx
Creating src/naive-nginx

$ ll
total 8.0K
-rw-rw-r-- 1 ubuntu ubuntu   19 Nov 30 22:30 README.md
drwxrwxr-x 3 ubuntu ubuntu 4.0K Nov 30 22:32 src

$ ll src
total 4.0K
drwxr-xr-x 4 ubuntu ubuntu 4.0K Nov 30 22:32 naive-nginx


打包 Chart 并创建 index.yaml

创建chart 后可以通过 helm package 命令打包并通过 helm repo index 命令自动生成 index.yaml 文件。

$ helm package src/naive-nginx
Successfully packaged chart and saved it to: /home/ubuntu/naive-charts-repo/naive-nginx-0.1.0.tgz

$ ll
total 12K
-rw-rw-r-- 1 ubuntu ubuntu   19 Nov 30 22:30 README.md
-rw-rw-r-- 1 ubuntu ubuntu 3.7K Nov 30 22:33 naive-nginx-0.1.0.tgz
drwxrwxr-x 3 ubuntu ubuntu 4.0K Nov 30 22:32 src


# 自动生成 index.yaml 文件

$ helm repo index .
$ ll
total 16K
-rw-rw-r-- 1 ubuntu ubuntu   19 Nov 30 22:30 README.md
-rw-r--r-- 1 ubuntu ubuntu  404 Nov 30 22:35 index.yaml
-rw-rw-r-- 1 ubuntu ubuntu 3.7K Nov 30 22:33 naive-nginx-0.1.0.tgz
drwxrwxr-x 3 ubuntu ubuntu 4.0K Nov 30 22:32 src


新生成的 index.yaml 文件内容如下：

apiVersion: v1
entries:
  naive-nginx:
  - apiVersion: v2
    appVersion: 1.16.0
    created: "2021-11-30T22:35:53.796835302+08:00"
    description: A Helm chart for Kubernetes
    digest: 5900f92fc1c6e453b48b36f74d04a475d4694e16a9e60e1b04a449558337b525
    name: naive-nginx
    type: application
    urls:
    - naive-nginx-0.1.0.tgz
    version: 0.1.0
generated: "2021-11-30T22:35:53.796044446+08:00"



完成后可以将新创建的所有文件 commit 并 push 到 Github  仓库，提交完成后我们就可以使用 Github Pages 做 chart 存储库了。

现在可以将 Github Page 的存储库添加到本地存储库了：

$ helm repo add naive-gh-repo https://zouyingjie.cn/naive-charts-repo/
"naive-gh-repo" has been added to your repositories

$ helm repo list
NAME         	URL
bitnami      	https://charts.bitnami.com/bitnami
naive-gh-repo	https://zouyingjie.cn/naive-charts-repo/


$ helm repo update
Hang tight while we grab the latest from your chart repositories...
...Successfully got an update from the "naive-gh-repo" chart repository
...Successfully got an update from the "bitnami" chart repository
Update Complete. ⎈Happy Helming!⎈


添加完仓库就可以安装 Chart ，执行 helm install <name>  <chart-name> 安装

$ helm install naive-nginx-v01  naive-gh-repo/naive-nginx
NAME: naive-nginx-v01
LAST DEPLOYED: Tue Nov 30 22:54:49 2021
NAMESPACE: default
STATUS: deployed
REVISION: 1
NOTES:
1. Get the application URL by running these commands:
  export POD_NAME=$(kubectl get pods --namespace default -l "app.kubernetes.io/name=naive-nginx,app.kubernetes.io/instance=naive-nginx-v01" -o jsonpath="{.items[0].metadata.name}")
  export CONTAINER_PORT=$(kubectl get pod --namespace default $POD_NAME -o jsonpath="{.spec.containers[0].ports[0].containerPort}")
  echo "Visit http://127.0.0.1:8080 to use your application"
  kubectl --namespace default port-forward $POD_NAME 8080:$CONTAINER_PORT

$ kubectl get pods
NAME                                    READY   STATUS             RESTARTS   AGE
naive-nginx-v01-696948788d-cjq5z        1/1     Running            0          7s


现在将 naive-nginx 中的 replicaCount 改为 3，并将 Chart.yaml 中的版本改为 0.2.0 我们在发布一个 0.2.0 的包并重新生成 index.yaml：

$ helm package src/naive-nginx
Successfully packaged chart and saved it to: /home/ubuntu/CKAD-note/naive-charts-repo/naive-nginx-0.2.0.tgz

$ helm repo index .

$ cat index.yaml
apiVersion: v1
entries:
  naive-nginx:
  - apiVersion: v2
    appVersion: 1.16.0
    created: "2021-11-30T23:02:48.05121892+08:00"
    description: A Helm chart for Kubernetes
    digest: 466755358c9c3e7ba36497c57485ba98754302d483b0c73a9a79565a3465d739
    name: naive-nginx
    type: application
    urls:
    - naive-nginx-0.2.0.tgz
    version: 0.2.0
  - apiVersion: v2
    appVersion: 1.16.0
    created: "2021-11-30T23:02:48.049967356+08:00"
    description: A Helm chart for Kubernetes
    digest: 5900f92fc1c6e453b48b36f74d04a475d4694e16a9e60e1b04a449558337b525
    name: naive-nginx
    type: application
    urls:
    - naive-nginx-0.1.0.tgz
    version: 0.1.0
generated: "2021-11-30T23:02:48.049169158+08:00"


现在将新的包和 index.yaml 文件 push 到仓库中，在执行 helm repo update 更新本地存储，现在在执行安装默认就会安装最新版本的有 3 个副本的  Chart 了。

$ helm install naive-nginx  naive-gh-repo/naive-nginx
NAME: naive-nginx
LAST DEPLOYED: Tue Nov 30 23:06:14 2021
NAMESPACE: default
STATUS: deployed
REVISION: 1
NOTES:
1. Get the application URL by running these commands:
  export POD_NAME=$(kubectl get pods --namespace default -l "app.kubernetes.io/name=naive-nginx,app.kubernetes.io/instance=naive-nginx" -o jsonpath="{.items[0].metadata.name}")
  export CONTAINER_PORT=$(kubectl get pod --namespace default $POD_NAME -o jsonpath="{.spec.containers[0].ports[0].containerPort}")
  echo "Visit http://127.0.0.1:8080 to use your application"
  kubectl --namespace default port-forward $POD_NAME 8080:$CONTAINER_PORT


$ kubectl get pods
NAME                                    READY   STATUS             RESTARTS   AGE
naive-nginx-784f55b8d4-bbxpq            1/1     Running            0          5s
naive-nginx-784f55b8d4-mkv5m            1/1     Running            0          5s
naive-nginx-784f55b8d4-xpcpz            1/1     Running            0          5s


此时如果我们想安装 0.1.0 版本的，需要在安装时通过 --version 参数指明版本：

$ helm install naive-nginx-v01  naive-gh-repo/naive-nginx --version=0.1.0
NAME: naive-nginx-v01
LAST DEPLOYED: Tue Nov 30 23:06:43 2021
NAMESPACE: default
STATUS: deployed
REVISION: 1
NOTES:
1. Get the application URL by running these commands:
  export POD_NAME=$(kubectl get pods --namespace default -l "app.kubernetes.io/name=naive-nginx,app.kubernetes.io/instance=naive-nginx-v01" -o jsonpath="{.items[0].metadata.name}")
  export CONTAINER_PORT=$(kubectl get pod --namespace default $POD_NAME -o jsonpath="{.spec.containers[0].ports[0].containerPort}")
  echo "Visit http://127.0.0.1:8080 to use your application"
  kubectl --namespace default port-forward $POD_NAME 8080:$CONTAINER_PORT

$ kubectl get deployments.apps
NAME                   READY   UP-TO-DATE   AVAILABLE   AGE
naive-nginx            3/3     3            3           3m30s
naive-nginx-v01        1/1     1            1           3m1s




最后如果我们不在需要存储库了可以将其删除

$ helm repo remove naive-gh-repo
"naive-gh-repo" has been removed from your repositories


CRD & Operator

之前看的 Pod、Service、Deployment 都是 Kubernetes 自己提供的资源对象。除了自身提供的资源对象，Kubernetes 还提供的 CustomResourceDefinition 对象使我们自定义资源对象，从而实现对 Kubernetes 的扩展。

CustomResourceDefinition

为了创建自定义对象，我们需要先来定义其对象的格式，也就是创建 CRD，示例如下：

apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  # name must match the spec fields below, and be in the form: <plural>.<group>
  name: crontabs.stable.example.com
spec:
  # group name to use for REST API: /apis/<group>/<version>
  group: stable.example.com
  # list of versions supported by this CustomResourceDefinition
  versions:
    - name: v1
      # Each version can be enabled/disabled by Served flag.
      served: true
      # One and only one version must be marked as the storage version.
      storage: true
      schema:
        openAPIV3Schema:
          type: object
          properties:
            spec:
              type: object
              properties:
                cronSpec:
                  type: string
                image:
                  type: string
                replicas:
                  type: integer
  # either Namespaced or Cluster
  scope: Namespaced
  names:
    # plural name to be used in the URL: /apis/<group>/<version>/<plural>
    plural: crontabs
    # singular name to be used as an alias on the CLI and for display
    singular: crontab
    # kind is normally the CamelCased singular type. Your resource manifests use this.
    kind: CronTab
    # shortNames allow shorter string to match your resource on the CLI
    shortNames:
    - ct


versions 们 自定义对象的版本，可以定义多个，这里最重要的是 schema 字段，用来定义我们的自定义对象的结构，采用 OpenAPI 3.0 规范进行校验，写过 swagger API 的同学应该会比较熟悉其结构。像上面的例子，我们的自定义对象有 cronSpec、image、replica 三个字段，前两个是 string 类型，replicas 是整数类型。
names ： 就是我们自定义对象的名称，如同内置资源对象的名称、缩写一样。
$ kubectl apply -f crontab.yaml
customresourcedefinition.apiextensions.k8s.io/crontabs.stable.example.com created

$ kubectl api-resources
                          
NAME            SHORTNAMES   APIVERSION                  NAMESPACED   KIND
pods            po           v1                          true         Pod
deployments     deploy       apps/v1                     true    Deployment
crontabs        ct           stable.example.com/v1       true       CronTab


CRD 创建完成后，可以看到 api-resources 里就多了  CronTab 的资源。现在我们就可以创建该
对象了：
apiVersion: "stable.example.com/v1"
kind: CronTab
metadata:
  name: my-new-cron-object
spec:
  cronSpec: "* * * * */5"
  image: my-awesome-cron-image
  replicas: 5


$ kubectl apply -f new-crontab.yaml
crontab.stable.example.com/my-new-cron-object created

$ kubectl get crontabs
NAME                 AGE
my-new-cron-object   64s


$ kubectl get ct
NAME                 AGE
my-new-cron-object   67s




最后我们还可以删除 CRD 对象，CRD 删除后对应的所有自定义对象也会被一起删除。

$ kubectl delete customresourcedefinition crontabs.stable.example.com
customresourcedefinition.apiextensions.k8s.io "crontabs.stable.example.com" deleted


仅仅定义了 CRD 以及自定义对象，还无法做到非常好的扩展。Kubernetes 的内置对象，比如 Deployment 之所以能执行滚动升级，是因为有对应的控制器在执行状态拟合。对于 CRD 也一样，我们的自定义对象也需要一个控制器来执行操作，这是由 Operator 提供的。

创建部署 Operator

Operator是使用自定义资源（CR，Custom Resource，是CRD的实例），管理应用及其组件的
自定义Kubernetes控制器。高级配置和设置由用户在CR中提供。Kubernetes Operator基于嵌
入在Operator逻辑中的最佳实践将高级指令转换为低级操作。Kubernetes Operator监视CR类型并采取特定于应用的操作，确保当前状态与该资源的理想状态相符。--- Red Hat

简单来说：Operator = CRD + Controller。

Kubernetes 本身提供的资源对象和控制器只能对最通用的操作做抽象，比如 CronJob 执行定时任务，Deployment 执行滚动升级等。但对于应用特定操作 Kubernetes 是做不到的，比如在 Kuberetes 部署一个 3 节点ElasticSearch 集群，需要将 StatefulSet、ConfigMap、Service 文件等悉数配置好才可以成功。之所以要详细配置是因为 Kubernetes 并不知道如何将 ElasticSearch 部署为一个集群，但如果 Kuberetes 本身有一个 ElasticSearch 的资源对象，并且有控制器基于该资源对象进行状态拟合，那我们可以很方便将部署 ElasticSearch 集群这些“高级指令”转化为 Kubernetes 可以执行的的 “低级操作”。

apiVersion: elasticsearch.k8s.elastic.co/v1
kind: Elasticsearch
metadata:
  name: quickstart
spec:
  version: 7.15.2
  nodeSets:
  - name: default
    count: 3
    config:
      node.store.allow_mmap: false



目前 operator 是一种非常流行的 Kubernetes 方式，大量复杂的分布式系统都提供了 Operator，可以参考 awesome-operators。

Operator 目前最大的问题在于编写起来比较麻烦，因为要封装大量的应用，以 etcd 为例，虽然其功能并不算复杂，但光是实现集群的创建删除、扩缩容、滚动更新等功能代码就已经超过了一万行，编写起来还是有一定的门槛。

为了方便开发 Operator，社区也有了不少工具来简化我们的工作，目前最常用的工具是两个：

kubebuilder: kubernetes-sigs 发布的脚手架工具，帮助我们快速搭建 operator 项目。
Operator framework: Red Hat 发布的 operator 开发工具，目前已经加入了 CNCF landscape。

关于两者的比较，可以参考 这篇文章。这里我们简单看下如何通过 operator-framework 来开发一个 operator。


初始化项目

首先是初始化项目，我们先创建一个monkey-operator 创 项目目录，然后执行 operator-sdk init行 命令初始化项目。operator-framework 会将项目所需的基本骨架给创建好。
·
➜  monkey-operator operator-sdk init --domain example.com --repo github.com/example/monkey-operator
Writing kustomize manifests for you to edit...
Writing scaffold for you to edit...
Get controller runtime:
$ go get sigs.k8s.io/controller-runtime@v0.9.2
go: downloading sigs.k8s.io/controller-runtime v0.9.2
go: downloading k8s.io/apimachinery v0.21.2
go: downloading k8s.io/client-go v0.21.2
go: downloading k8s.io/component-base v0.21.2
go: downloading golang.org/x/time v0.0.0-20210611083556-38a9dc6acbc6
go: downloading sigs.k8s.io/structured-merge-diff/v4 v4.1.0
go: downloading k8s.io/api v0.21.2
go: downloading k8s.io/apiextensions-apiserver v0.21.2
go: downloading golang.org/x/sys v0.0.0-20210603081109-ebe580a85c40
Update dependencies:
$ go mod tidy
Next: define a resource with:
$ operator-sdk create api


创建 CRD
项目创建完成后就可以创建 CRD 了，执行 operator-sdk create api 执行命令指定 group、version以及 kind。这里我们创建一个 MonkeyPod 的 CRD。

➜  monkey-operator operator-sdk create api --group monkey --version v1alpha1  --kind 

MonkeyPod --resource --controller
Writing kustomize manifests for you to edit...
Writing scaffold for you to edit...
api/v1alpha1/monkeypod_types.go
controllers/monkeypod_controller.go
Update dependencies:
$ go mod tidy
Running make:
$ make generate
go: creating new go.mod: module tmp
Downloading sigs.k8s.io/controller-tools/cmd/controller-gen@v0.6.1
go: downloading sigs.k8s.io/controller-tools v0.6.1
go: downloading golang.org/x/tools v0.1.3
go get: added sigs.k8s.io/controller-tools v0.6.1
/home/ubuntu/monkey-operator/bin/controller-gen object:headerFile="hack/boilerplate.go.txt" paths="./..."



完成后项目的目录结构如下

➜  monkey-operator tree
.
├── Dockerfile
├── Makefile
├── PROJECT
├── api
│   └── v1alpha1
│       ├── groupversion_info.go
│       ├── monkeypod_types.go
│       └── zz_generated.deepcopy.go
├── bin
│   └── controller-gen
├── config
│   ├── crd
│   │   ├── kustomization.yaml
│   │   ├── kustomizeconfig.yaml
│   │   └── patches
│   │       ├── cainjection_in_monkeypods.yaml
│   │       └── webhook_in_monkeypods.yaml
│   ├── default
│   │   ├── kustomization.yaml
│   │   ├── manager_auth_proxy_patch.yaml
│   │   └── manager_config_patch.yaml
│   ├── manager
│   │   ├── controller_manager_config.yaml
│   │   ├── kustomization.yaml
│   │   └── manager.yaml
│   ├── manifests
│   │   └── kustomization.yaml
│   ├── prometheus
│   │   ├── kustomization.yaml
│   │   └── monitor.yaml
│   ├── rbac
│   │   ├── auth_proxy_client_clusterrole.yaml
│   │   ├── auth_proxy_role.yaml
│   │   ├── auth_proxy_role_binding.yaml
│   │   ├── auth_proxy_service.yaml
│   │   ├── kustomization.yaml
│   │   ├── leader_election_role.yaml
│   │   ├── leader_election_role_binding.yaml
│   │   ├── monkeypod_editor_role.yaml
│   │   ├── monkeypod_viewer_role.yaml
│   │   ├── role_binding.yaml
│   │   └── service_account.yaml
│   ├── samples
│   │   ├── kustomization.yaml
│   │   └── monkey_v1alpha1_monkeypod.yaml
│   └── scorecard
│       ├── bases
│       │   └── config.yaml
│       ├── kustomization.yaml
│       └── patches
│           ├── basic.config.yaml
│           └── olm.config.yaml
├── controllers
│   ├── monkeypod_controller.go
│   └── suite_test.go
├── go.mod
├── go.sum
├── hack
│   └── boilerplate.go.txt
└── main.go


这里最主要的是两个目录：

apis 目录：CRD 对象的定义目录，我们创建的 MonkeyPod 会在这里自动生成对象定义：

// MonkeyPod is the Schema for the monkeypods API
type MonkeyPod struct {
  metav1.TypeMeta   `json:",inline"`
  metav1.ObjectMeta `json:"metadata,omitempty"`

  Spec   MonkeyPodSpec   `json:"spec,omitempty"`
  Status MonkeyPodStatus `json:"status,omitempty"`
}


我们可以根据需要在这里定义好 CRD，然后自动生成 yaml 文件，这样就不用手动编写复杂的 yaml 文件了。这里我将 MonkeyPod 的 Spec 和 Status 替换为 Pod 的对象：




import (
  corev1 "k8s.io/api/core/v1"

)

// EDIT THIS FILE!  THIS IS SCAFFOLDING FOR YOU TO OWN!
// NOTE: json tags are required.  Any new fields you add must have json tags for the fields to be serialized.

// +kubebuilder:object:root=true
// +kubebuilder:subresource:status

// MonkeyPod is the Schema for the monkeypods API
type MonkeyPod struct {
  metav1.TypeMeta   `json:",inline"`
  metav1.ObjectMeta `json:"metadata,omitempty"`

  Spec   corev1.PodSpec  `json:"spec,omitempty"`
  Status corev1.PodStatus `json:"status,omitempty"`
}


修改完成后，需要执行如下命令：

# 重新生成 CRD yaml 文件
➜  monkey-operator make manifests
/home/ubuntu/monkey-operator/bin/controller-gen "crd:trivialVersions=true,preserveUnknownFields=false" rbac:roleName=manager-role webhook paths="./..." output:crd:artifacts:config=config/crd/bases

# 重新生成 go 相关文件
➜  monkey-operator make generate 
/home/ubuntu//operator/monkey-operator/bin/controller-gen object:headerFile="hack/boilerplate.go.txt" paths="./..."



controller 目录：这里写的就是具体的控制逻辑，我们编写 operator 时主要的工作量基本都是编写控制逻辑。这里的逻辑非常简单，每当有 MonkeyPod 创建，我们的 controller 会创建同名的 Pod 并打上 "monkey": "stupid-monkey" 标签:

func (r *MonkeyPodReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
  _ = log.FromContext(ctx)

  // your logic here
  monkeyPod := &monkeyv1alpha1.MonkeyPod{}
  err := r.Get(ctx, req.NamespacedName, monkeyPod)
  pod := &corev1.Pod{
     TypeMeta: metav1.TypeMeta{
        Kind:       "Pod",
        APIVersion: "v1",
     },
     ObjectMeta: metav1.ObjectMeta{
        Name:      monkeyPod.Name,
        Namespace: monkeyPod.Namespace,
     },
  }
  pod.Spec = monkeyPod.Spec
  labels := map[string]string{
     "monkey": "stupid-monkey",
  }
  pod.Labels = labels
  createPod, err := CreatePod(pod)
  fmt.Println(createPod)
  if err != nil {
     return ctrl.Result{}, err
  }
  return ctrl.Result{}, err
}


完成后项目有 make docker-build 和 make deploy 命令可以创建镜像以及部署。

$ kubectl get deployments.apps -n monkey-operator-system
NAME                                 READY   UP-TO-DATE   AVAILABLE   AGE
monkey-operator-controller-manager   1/1     1            1           8h


$ kubectl get pods -n monkey-operator-system
NAME                                                  READY   STATUS    RESTARTS   AGE
monkey-operator-controller-manager-674cd8bc69-dgcbj   2/2     Running   0          20m



部署完成后创建 一个 MonkeyPod 对象：

apiVersion: monkey.example.com/v1alpha1
kind: MonkeyPod
metadata:
  labels:
    run: nginx
  name: nginx
spec:
  containers:
  - image: nginx
    name: nginx
    resources: {}
  dnsPolicy: ClusterFirst
  restartPolicy: Always
status: {}


创建完成后我们的 operator 就会自动创建一个同名的 Pod 并打上标签了：

$ kubectl describe pod nginx
Name:         nginx
Namespace:    default
Priority:     0
Node:         vm-0-3-ubuntu/172.19.0.3
Start Time:   Sun, 05 Dec 2021 06:26:43 +0800
Labels:       monkey=stupid-monkey


以上是 Operator Framework的简单使用，具体操作时还要考虑权限设置等问题，比如我们的例子就需要给 operator 添加 Pod 创建权限，具体操作可以参考 RBAC 部分，这里就不做赘述。

一般来说对于 Operator 的编写更推荐使用 Go 语言编写，可以使用 client-go 库很好的与 Kubernetes 交互。其他语言的话 JavaClient有两个，一个是Jasery，另一个是Fabric8，后者对 Pod、Deployment 等做了 DSL 定义而且可以用 Builder 模式，写起来也相对方便一些。

Kubernetes 监控 & 调试
集群监控

Metrics Server

Metrics Server 是 Kubernetes 提供的监控工具，主要用来收集 Node 和 Pod 的 CPU、内存使用情况。其本质就是通过 kube-aggregator 实现的一个 server。


图片来自 https://www.jetstack.io/blog/resource-and-custom-metrics-hpa-v2/

Kubelet 内置了 cAdvisor 服务运行在每个节点上收集容器的各种资源信息，并对外提供了 API 来查询这些信息。Metric Server 正是访问 Kubelet 提供的 /stats/summary API 来获取监控数据，我们的 kubernetes-core-metrics-collector 项目也是基于该 API 来实现的。

可以通过下面命令安装 MetricServer

$ kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml


安装完成后就可以通过 kubectl top 命令查看 Pod 和 Node 的资源使用信息了。

$ kubectl top nodes
NAME            CPU(cores)   CPU%   MEMORY(bytes)   MEMORY%
tk01            217m         10%    5296Mi          68%
vm-0-2-ubuntu   84m          4%     1189Mi          32%

$ kubectl top pods --all-namespaces
NAMESPACE     NAME                              CPU(cores)   MEMORY(bytes)
kube-system   coredns-f9fd979d6-jzv8q           4m           10Mi
kube-system   coredns-f9fd979d6-tx9m4           4m           10Mi
kube-system   etcd-tk01                         14m          50Mi
kube-system   kube-apiserver-tk01               31m          293Mi


Prometheus

Prometheus 是 CNCF 的第二个毕业项目，目前已经是 Kubernetes 监控方面的事实标准。其架构如图：


其提供了若干组件来完成数据的收集、存储、展示与告警等：

数据收集：Prometheus 采用 pull 的模式定期从各个目标收集数据。对于应用指标收集，应用只需要提供一个类似 /metrics  接口供 Prometheus 访问即可，对于中间件、系统的监控，由官方和社区维护了一系列的 Exporter 来实现数据的收集。对于某些短时任务可以通过 pushGateway 来实现，先将任务的指标收集到 gateway，在被 pull 到 Prometheus 。

Prometheus Server: 收集并存储数据，Prometheus 内置了时序数据库，也可以使用外部的 InfluxDB 等其他存储。关于数据的存储原理可以看之前皓哥的分享 技术分享：Prometheus是怎么存储数据的（陈皓）。

AlertManager: 告警组件，可以根据一系列规则实现及时的告警。

数据展示组件：Prometheus 本身提供了 API 供外部查询各种指标，同时也内置了 UI 界面实现可视化查询与展示，另外比较常用的是结合 Grafana 实现数据的可视化。



这里只对 Prometheus 监控 Kubernetes 做一个简单的 demo，其监控架构如图，从 Kubernetes 组件、节点以及各种中间件中收集数据并存储，然后经由 Grafana 展示并提供给 AlertManager 展示。






就 Kuberetes 而言，其监控数据分为三种:

主机指标：Kubernetes 各个宿主机节点的指标，由 Node Exporter 提供。
组件指标：Kuberetes 各个组件的指标，比如 api-server、kubelet 等组件的指标，这个由各个组件的 /metrics API 提供。
核心指标： Kubernetes 中各种资源对象的数据，比如 Pod 、Node、容器的各种指标，NameSpace、Deployment 、Service 等各种资源的信息。


下面是部署 Prometheus 并查看监控的一个示例，目前在 Kuberetes 中有三种方式安装 Prometheus:


Prometheus-operator
社区提供的 Helm Chart
Kube-prometheus
这里我使用 prometheus-operator 作为部署方式：

$ git clone https://github.com/prometheus-operator/kube-prometheus.git
kubectl create -f manifests/setup
until kubectl get servicemonitors --all-namespaces ; do date; sleep 1; echo ""; done
kubectl create -f manifests/


完成后就可以在 monitoring namespace 下看到 Prometheus 相关的组件了：

$ kubectl get pods -n monitoring
NAME                                   READY   STATUS    RESTARTS   AGE
alertmanager-main-0                    2/2     Running   0          9h
alertmanager-main-1                    2/2     Running   0          9h
alertmanager-main-2                    2/2     Running   0          9h
blackbox-exporter-6798fb5bb4-88bhj     3/3     Running   0          9h
grafana-698f6895f4-8gwt7               1/1     Running   0          9h
kube-state-metrics-5fcb7d6fcb-hpsn6    3/3     Running   0          9h
node-exporter-2z8sq                    2/2     Running   0          9h
node-exporter-bcfcr                    2/2     Running   0          9h
node-exporter-jg2w4                    2/2     Running   0          9h
prometheus-adapter-7dc46dd46d-6tw7k    1/1     Running   0          9h
prometheus-adapter-7dc46dd46d-ss7h8    1/1     Running   0          9h
prometheus-k8s-0                       2/2     Running   0          9h
prometheus-k8s-1                       2/2     Running   0          9h
prometheus-operator-66cf6bd9c6-w9m5k   2/2     Running   0          9h

$ kubectl get svc -n monitoring
NAME                    TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)                         AGE
alertmanager-main       ClusterIP   10.104.201.190   <none>        9093/TCP,8080/TCP               9h
alertmanager-operated   ClusterIP   None             <none>        9093/TCP,9094/TCP,9094/UDP      9h
blackbox-exporter       ClusterIP   10.105.110.192   <none>        9115/TCP,19115/TCP              9h
grafana                 ClusterIP   10.103.196.221   <none>        3000/TCP                        9h
grafana-pub             NodePort    10.109.122.46    <none>        3000:32130/TCP                  9h
kube-state-metrics      ClusterIP   None             <none>        8443/TCP,9443/TCP               9h
node-exporter           ClusterIP   None             <none>        9100/TCP                        9h
prometheus-adapter      ClusterIP   10.106.96.212    <none>        443/TCP                         9h
prometheus-k8s          NodePort    10.99.87.46      <none>        9090:32142/TCP,8080:32161/TCP   9h
prometheus-operated     ClusterIP   None             <none>        9090/TCP                        9h
prometheus-operator     ClusterIP   None             <none>        8443/TCP                        9h





可以看到 Prometheus Server、node-exporter、grafana 等组件都已经部署好了，除了 Operator 自己创建的 Service 上面还额外加了两个 NodePort 的 service 方便从外部访问。Prometheus 默认监听 9090 端口，下面是 Prometheus 的UI 示例，我们可以查询 Prometheus 的监听对象，设置报警规则，查询各种指标等操作：

目标 target，表示收集目标的对象，这里是在 Kubernetes 部署后自动配置的，我们也可以在 Prometheus 文件中设置。



查询节点信息



- 查询 deployment 信息

除了 Prometheus 本身的 UI，Operator 还部署了 Grafana 并自动创建了众多 Dashboard，默认用户名密码是 admin:admin，登陆进后就可以查看相关的监控指标了，下面是几个示例：



Dashboard 列表



集群整体监控





kubelet 监控



宿主机节点监控




Debug/Logging/TroubleShooting

当运行的应用出现问题时，我们需要找出问题，恢复正常运行，一般包含一些操作：

查看 Pod 状态以及 Spec，看是否被正确调度，Volume 挂载是否准确等。
查看应用本身是否正确，比如数据库配置是否正确，代码是否报错等，一般可以通过查看日志来解决，另外如果 Pod 内容器支持 debug 可以运行命令进入容器执行 debug。
查看 Service、Ingress 等配置是否正确，保证外部请求能正确访问到应用。

另外集群的控制组件、worker node 都有可能出现问题，导致集群不可用，此时需要检查 Kubernetes 的各个组件是否正常运行。
Debug Pod/Service

首先可以通过 kubectl describe 命令和 kubectl get pod $<POD_NAME> -o yaml 命令 查看 Pod 状态或者完整的定义。

$ kubectl describe pod -n ingress-nginx ingress-nginx-controller-5fd866c9b6-qc824
Name:         ingress-nginx-controller-5fd866c9b6-qc824
Namespace:    ingress-nginx
Priority:     0
Node:         vm-0-7-ubuntu/172.19.0.7
...
Events:
  Type    Reason   Age                      From                      Message
  ----    ------   ----                     ----                      -------
  Normal  Pulling  28m (x73 over 7h8m)      kubelet                   Pulling image "k8s.gcr.io/ingress-nginx/controller:v1.1.0@sha256:f766669fdcf3dc26347ed273a55e754b427eb4411ee075a53f30718b4499076a"
  Normal  BackOff  8m42s (x1613 over 7h7m)  kubelet                   Back-off pulling image "k8s.gcr.io/ingress-nginx/controller:v1.1.0@sha256:f766669fdcf3dc26347ed273a55e754b427eb4411ee075a53f30718b4499076a"
  Normal  RELOAD   5m33s                    nginx-ingress-controller  NGINX reload triggered due to a change in configuration


这样通过查看 Pod 的状态、Event信息可以初步了解 Pod 启动失败的原因。比如

如果Pod一直处于 Pending的状态，那说明Kubernetes 无法将其分配到一个节点上。一般会有以下几种情况：
CPU/Memory 资源不足，首先确认除了master 节点外的机器资源，可以通过命令kubectl get nodes -o yaml | egrep '\sname:|cpu:|memory:'， Pod 的资源申请不能大于节点容量。或者添加一个Node，或者删除一些再需要的Pod 来释放一些资源
如果Pod 有使用 hostPort资源（即Node上实际的端口资源），这样会限制Pod能被调度到的Node节点，除非必要，请用service资源替代。
如果Pod一直处于waiting 的状态，那说明Pod已经被调度都某一个节点，但是无法执行成功，一般比较大的概率是镜像问题，可以检查：
镜像名称是否有误？版本号码是否正确
是否已经push到镜像仓库，可以使用docker pull <image>来进行验证
如果Pod已经执行起来，但是一直crashing 或者处于不健康状态，此时可能需要通过日志或者 debug 命令来检查 Pod 中容器的运行情况。

首先可以通过 kubectl log 命令查看 Pod 某个容器的 log 

kubectl logs ${POD_NAME} ${CONTAINER_NAME}


如果容器之前有crash 过，可以通过以下命令查看crash 的容器的log
kubectl logs --previous ${POD_NAME} ${CONTAINER_NAME}


如果容器镜像已经包含 debug 功能的命令，可以使用 kube exec 命令来执行：

kubectl exec ${POD_NAME} -c ${CONTAINER_NAME} -- ${CMD} ${ARG1} ${ARG2} ... ${ARGN}，例如：

kubectl exec -it cassandra -- sh


如果容器本身没有开启 debug ，可以使用SideCar 容器或者 Ephemeral 容器来定位那些运行没有包含debugging功能镜像的容器。

$ kubectl run ephemeral-demo --image=k8s.gcr.io/pause:3.1 --restart=Never
pod/ephemeral-demo created

$ kubectl exec -it ephemeral-demo -- sh
OCI runtime exec failed: exec failed: container_linux.go:380: starting container process caused: exec: "sh": executable file not found in $PATH: unknown
command terminated with exit code 126


此时执行 debug 命令会报错，因此可以使用 

$ kubectl debug -it ephemeral-demo --image=busybox --target=ephemeral-demo

Defaulting debug container name to debugger-8xzrl.
If you don't see a command prompt, try pressing enter.
/ #

会报除了 Pod 一般还会有 Service 的调试以保证 Pod 会被访问到，对于 Service 主要就是查看 Service 资源创建成功以及 Endpoints 是否是对应的 Pod。其次可以通过 <service-name>.<namesapce-name> 来检查 Service  的 DNS 是否正确。
网络调试

除了应用本身的问题 ，Kuberetes 中网络问题算是占比较大的问题类型，但 Pod 中的容器往往都只安装了应用所需的依赖和命令，操作系统中的很多程序和命令都是没有的，比如 tcdump 、ifconfig、vim 等程序。为了方便调试网络问题社区提供了 nicolaka/netshoot 工具，其包含众多常用的网络以及相关调试命令。



下面是使用 netshoot 的一个示例，在使用我们的 EaseMesh 做灰度时，需要通过抓包检查下请求是否到了灰度应用中。

首先查看下 Pod 所在节点并找到对应的容器：

$ kubectl get pods -o wide
NAME                                        READY   STATUS    RESTARTS   AGE   IP              NODE    NOMINATED NODE   READINESS GATES
cloud-ease-coupon-975986b55-r66kg           2/2     Running   0          13h   10.233.68.108   node5   <none>           <none>
cloud-ease-coupon-shadow-7647db59f5-vcdzn   2/2     Running   0          13h   10.233.67.129   node4   <none>           <none>

node4:➜  ~  |>docker ps                                                                                                                 [~]
CONTAINER ID        IMAGE                                                  COMMAND                  CREATED             STATUS              PORTS               NAMES
                       k8s_easemesh-sidecar_cloud-ease-coupon-shadow-7647db59f5-vcdzn_mesh-service_1f563154-8a25-431e-8d44-3b1e2b0aab02_0
1879f34bc3df        172.20.2.189:5001/megaease/cloud-ease-coupon           "/bin/bash -c '/boot..."   14 hours ago        Up 14 hours                             k8s_cloud-ease-coupon_cloud-ease-coupon-shadow-7647db59f5-vcdzn_mesh-service_1f563154-8a25-431e-8d44-3b1e2b0aab02_0
a2ba0b7db5a5        k8s.gcr.io/pause:3.3                                   "/pause"                 14 hours ago        Up 14 hours                             k8s_POD_cloud-ease-coupon-shadow-7647db59f5-vcdzn_mesh-service_1f563154-8a25-431e-8d44-3b1e2b0aab02_0
20a9e3ec64e4        dd9001359df9                                           "/bin/sh -c '/opt/ea..."   2 days ago          Up 2 days


在对应的节点上找到已经创建的容器，因为 Kubernetes 是通过 pause 容器来创建的网络 namespaace，因此我们在 pause 容器中进行抓包操作，netshoot 提供了命令 docker run -it --net container:<container_name> nicolaka/netshoot 使我们进入目标容器内部，进入容器后就可以使用相关的命令了。下面我们通过 ifconfig 查看容器内网络设备以及通过 tcpdump 命令一抓包查看是否有请求进入容器的操作示例：

node4:➜  ~  |>docker run -it --net container:a2ba0b7db5a5  nicolaka/netshoot                                                            [~]
                    dP            dP                           dP
                    88            88                           88
88d888b. .d8888b. d8888P .d8888b. 88d888b. .d8888b. .d8888b. d8888P
88'  `88 88ooood8   88   Y8ooooo. 88'  `88 88'  `88 88'  `88   88
88    88 88.  ...   88         88 88    88 88.  .88 88.  .88   88
dP    dP `88888P'   dP   `88888P' dP    dP `88888P' `88888P'   dP

Welcome to Netshoot! (github.com/nicolaka/netshoot)



 cloud-ease-coupon-shadow-7647db59f5-vcdzn  ~  ifconfig
eth0      Link encap:Ethernet  HWaddr 6A:A1:BD:16:29:85
          inet addr:10.233.67.129  Bcast:0.0.0.0  Mask:255.255.255.255
          UP BROADCAST RUNNING MULTICAST  MTU:9001  Metric:1
          RX packets:599624 errors:0 dropped:0 overruns:0 frame:0
          TX packets:737437 errors:0 dropped:0 overruns:0 carrier:0
          collisions:0 txqueuelen:0
          RX bytes:161261874 (153.7 MiB)  TX bytes:295757144 (282.0 MiB)

lo        Link encap:Local Loopback
          inet addr:127.0.0.1  Mask:255.0.0.0
          UP LOOPBACK RUNNING  MTU:65536  Metric:1
          RX packets:228767 errors:0 dropped:0 overruns:0 frame:0
          TX packets:228767 errors:0 dropped:0 overruns:0 carrier:0
          collisions:0 txqueuelen:1000
          RX bytes:22301724 (21.2 MiB)  TX bytes:22301724 (21.2 MiB)



// 抓包
 cloud-ease-coupon-shadow-7647db59f5-vcdzn  ~  tcpdump -s0 -Xvn -i eth0 tcp port 13001
tcpdump: listening on eth0, link-type EN10MB (Ethernet), snapshot length 262144 bytes
22:56:43.071099 IP (tos 0x0, ttl 63, id 58997, offset 0, flags [DF], proto TCP (6), length 60)
    10.233.65.136.56160 > 10.233.67.129.13001: Flags [S], cksum 0xfeca (correct), seq 938442090, win 62377, options [mss 8911,sackOK,TS val 4022397520 ecr 0,nop,wscale 7], length 0
	0x0000:  4500 003c e675 4000 3f06 ba6b 0ae9 4188  E..<.u@.?..k..A.
	0x0010:  0ae9 4381 db60 32c9 37ef 7d6a 0000 0000  ..C..`2.7.}j....
	0x0020:  a002 f3a9 feca 0000 0204 22cf 0402 080a  ..........".....
	0x0030:  efc0 ea50 0000 0000 0103 0307            ...P........
22:56:43.071114 IP (tos 0x0, ttl 64, id 0, offset 0, flags [DF], proto TCP (6), length 60)
    10.233.67.129.13001 > 10.233.65.136.56160: Flags [S.], cksum 0x9b09 (incorrect -> 0x4b72), seq 2068664002, ack 938442091, win 62293, options [mss 8911,sackOK,TS val 3323601777 ecr 4022397520,nop,wscale 7], length 0
	0x0000:  4500 003c 0000 4000 4006 9fe1 0ae9 4381  E..<..@.@.....C.
	0x0010:  0ae9 4188 32c9 db60 7b4d 4ec2 37ef 7d6b  ..A.2..`{MN.7.}k
	0x0020:  a012 f355 9b09 0000 0204 22cf 0402 080a  ...U......".....
	0x0030:  c61a 2371 efc0 ea50 0103 0307            ..#q...P....
22:56:43.071221 IP (tos 0x0, ttl 63, id 58998, offset 0, flags [DF], proto TCP (6), length 52)
    10.233.65.136.56160 > 10.233.67.129.13001: Flags [.], cksum 0x88c7 (correct), ack 1, win 488, options [nop,nop,TS val 4022397520 ecr 3323601777], length 0



集群组件排错

如果是集群出错，我们需要查看控制节点和 worker 节点的各个组件是否正确。下面是一些基本的步骤供参考：

检查控制组件 api-server、etcd、scheduler、controller 是否启动成功，可以通过上面提到的 debug Pod 的方式以及检查  /etc/kubernetes/manifests/ 下的 yaml 文件是否有问题。
检查网络插件是否安装正确以及确保网络插件支持所需的特性。
检查 kube-proxy 是否部署配置正确。
检查 DNS 是否配置正确。
检查 kubelet 是否正常启动，可以通过 systemd status kubelet 命令查看 kubelet 的状态以及  journalctl -u  kubelet | tail 命令查看 kubelet 的日志。

另外关于集群的基本信息，在 kube-public 命名空间下有 ConfigMap 记录，这里记录了基本的 Kubernetes server 信息，如果在节点变化时 server 信息没有及时同步，可以手动该这里的配置进行排错。

$ kubectl get cm -n kube-public
NAME               DATA   AGE
cluster-info       1      28d























