@[toc]
### 1. 为什么需要 Pod

Pods 是 K8s 中最小的可部署和管理单元，是一个逻辑概念。一个 Pod 包含一个或多个 container，它们可以**共享网络和存储**，可以把 Pod 看作是一个虚拟的逻辑主机，里面包含了一个或多个紧密关联的 container，Pod 同时也告知系统如何去运行它所描述的容器。

Pod 的存在主要是为了解决两个问题：

- 对“进程组”的抽象，满足容器共享 namespace 的需求。
- 协同调度

容器本身是单进程模型，其应用本身的 PID 为 1，本身没有管理多个进程的能力。在实际应用中，往往存在着需要「超亲密关系」的进程，它们必须要运行在同一机器上共享存储、网络等，而 Pod 其实就是一组共享了网络、存储、IPC、UTS 以及时间的的容器，它们只有 PID 和文件 namespace 是默认隔离的，可以满足这种超亲密关系的要求。

与此同时，对于这类超亲密关系的进程，在跨机器的集群中，它们必须被部署到同一台机器上。如果以容器为调度单位，对资源的要求就只能在容器上设置，此时当多个超亲密容器需要协同调度时，资源越紧张，容器被调度到不同机器的可能性越大。

如果以 Pod 为原子单位进行调度，则对资源的设置可以定义在 Pod 上，此时只需要以 Pod 为单位统一调度即可，不需要在考虑单个容器的情况。

![在这里插入图片描述](https://img-blog.csdnimg.cn/88f7dfb771a9464bb149c3951811e95a.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

Pod 可以由用户直接创建，也可以通过 Controller 对象创建。一般来说，Pod 极少直接被单独创建，一般会由用户使用 Deployment，Job，StatefulSet 等实际工作资源，基于其 PodTemplate 创建。另外也可以由 kubelet 直接创建并管理，这类 Pod 称为 StaticPod，像 etcd、api-server 这些 master 节点组件都是 StaticPod。




```yaml
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
```





当Pod完成它的工作后，会被删除，同时也有可能会因为缺乏资源，Node节点的失败而被驱逐。
给定的Pod（由UID定义）永远不会“重新安排”到其他节点，如果 Pod 失败退出，K8s 会创建一个新的 Pod 来替换。

### 2. Pod 状态

#### 2.1 PodStatus

Pod的状态是一个 PodStatus 对象，在 Pod 定义中是 status 字段， 其中包含一个pod.status.phase 字段表示当前的状态，可以取如下的值
|  |  |
|--|--|
| Pending |  表示 Pod 对象已被创建保存在 etcd 中，但有一个或多个容器还不能被顺利创建。当等待调度或者拉取镜像时一般处在该状态。
|Running | Pod 已经调度到某一节点并且所有容器都已经创建成功，并且至少有一个容器正在运行或者正在启动/重启。|
|Succeed | 所有容易都正常运行并退出，并且不会重启。一般在运行一次性任务时最为常见。|
|Failed|所有容器已终止，并且至少有一个以不正常状态退出。此时我们一般需要通过查看 Pod 信息或日志来调试问题。|
|Unknown|异常状态，Pod 状态无法被 kube-apiserver 获取到。一般是 Pod 节点与 api-server 通信出现异常导致的。|


#### 2.2 Pod Conditions

除了 status.phase 字段表示状态之外，Pod 还有一组 PodConditions 对象，对应字段是 pod.status.conditions 数组字段来描述 Pod 处于某个 phase 的具体原因，主要有下面几个值：

- **PodScheduled**：Pod 已经被调度到指定节点。
- **ContainersReady**：Pod内所有的容器已经就绪。
- **Initialized**：所有的 init containers 已经成功启动。
- **Ready**：Pod 可以对外提供服务，可以作为 endpoints 给 Service 代理。

PodConditions 对象包含如下的字段

| type |  类型名称， 上面四个取值之一|
|--|--|
| status |  True， False， Unkonwn 指示这个状况是否就绪|
|lastProbeTime|最后一个探测状况的时间戳|
|lastTransitionTime|Pod上一次状况变换的时间戳|
reason | 状况最后一次变化的原因， Machine-readable|
message| 状况最后一次变化的原因，Human-readable message|




#### 2.3 Container States 

Pod 中容器也有状态，主要有下面三个值

|  |  |
|--|--|
| Waiting |  等待状态。表示容器仍在执行启动所需的操作，比如拉取镜像、注入数据等。|
|Running|运行状态。表示容器正常运行，如果容器配置了 postStart 命令，该命令已经执行并结束了|
|Terminated|终止状态。容器完成任务或者执行失败，如果容器配置了 preStop 命令，必须在该命令执行完成后进行该状态，换句话说，preStop 会阻塞 container 的终止。|




#### 2。4 Container restart policy

容器的重启策略，有三个取值， 
- **Always（默认值）**：只要不在运行状态就自动重启
- **OnFailure**：在容器异常时自动重启
- **Never**：从不重启


Pod 状态与容器状态的对应关系简要总结如下：

- 如果 Pod 的 restartPolicy 指定允许容器重启，当有容器异常时， Pod 会一直保持 Running 状态并重启容器，否则Pod 会进入 Faied 状态。
- 对于多容器的 Pod，只有所有容器都进入异常状态后，Pod 才会进入 Failed，在此之前一直是 Running 状态。查看 Pod 的 Ready 字段会显示正常如果您器个数。

#### 2.5 Lifecycle
上面提到了容器可以配置 postStart 和 preStop 命令。这是容器在发生状态变化时可以触发的一系列 hook操作：

-  **postStart**：在 Docker 容器启动后立即执行，即 ENTRYPOINT 执行之后执行，但并不保证顺序，postStart 执行时 ENTRYPOINT 命令可能尚未结束。postStart 会阻塞容器状态变化，在 postStart 执行完成前，容器的状态不会设置为 Running。
- **preStop**：在容器被杀死进入 Terminated 状态前执行，会阻塞容器杀死进程。Kubernetes 仅在 Pod 终止时执行 preStop，在完成时不会调用，相关 issues。

命令的操作操作方式有两种：
- **exec**：执行一段命令
- **http**: 执行一个请求

```yaml
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
```






### 3.  Pod Probe

探针是由 kubelet 发起的一种容器诊断行为，可以用来检测 Pod 中的容器的健康情况。Kubernetes 中主要有三种探针：

- **livenessProbe**：存活探针，检查容器的健康状态，以决定是否重启容器。
- **readinessProbe**：就绪探针，用来检测容器是否准备好接收外部流量。该探针执行成功后 Pod 的细分状态就会变为 Ready，此时 Pod 可以作为 endpoints 给 Service 进行代理。
- **startupProbe**：启动探针，来判断容器是否启动，该类型探针会屏蔽上述两种探针。可以避免因为容器启动过长导致的死循环问题。如果失败则会重启容器。

上述三类探针，如果没有提供， 默认对应的状态是 Succeess。

kubelet 通过调用 handler 实现对容器的诊断。 有三种类型的Handler


- **ExecAction**: 执行一条用户提供的命令（例如“ls” 等）
- **TCPSocketAction**: 用TCP 协议探测指定端口是否能通
- **HttpGetAction**: 用HTTP GET 请求探测指定URL 是否正常返回200



```yaml
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
```


**什么时候启用livenessProbe**

如果容器中的进程在出现问题时可以自行正常崩溃，此时是不需要存活探针的，kubelet 会探测到容器的状态并基于 restartPolicy 执行相应的操作。

但有时候容器进程虽然没有挂掉，但其实已经无法提供服务了，比如遇到死锁，从进程检查的角度它是没问题的。我们可能需要通过访问业务逻辑来判断容器进程是否安全，此时可以使用 livenessProbe。

**什么时候启用 readinessProbe**

就绪探针决定的是容器是否已经启动就绪并可以接收外部的流量，因此如果某个需要接收外部流量的的容器进程启动较慢，可以设置 readinessProbe 来确认容器是否已经就绪。比如对于 Java/Spring 应用，其启动往往较慢，此时为其设置就绪探针是一个不错的选项。

**什么时候启用 startupProbe**

针对容器进程启动较慢的情况，可以设置启动指针。避免因为长时间未启动引发 livenessProbe 探测失败导致重启，陷入不断重启的“死循环”中。


### 4. Init container

Init Container 是一种特殊容器，在 Pod 内的应用容器启动之前运行，一般用来执行初始化操作，比如安装应用镜像中不存在的实用工具和脚本。每个 Pod 中可以包含多个应用容器，应用运行在这些容器里面，同时 Pod 也可以有一个或多个先于应用容器启动的 Init Container。

initContainer 容器有两个特点：
- 按顺序执行
- 它们总是运行到完成，每个 Init Container 必须在下一个容器启动前成功完成。

```yaml
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
```


**注意事项**

1. Init Container 不支持 lifecycle、livenessProbe、readinessProbe 和 startupProbe， 因为它们必须在 Pod 就绪之前运行完成。
2. 只有所有 Init Container 都执行成功， Pod才能切换到Ready condition；只要任意一个失败就会触发 Pod 的 restartPolicy。
3. Pod 重新启动时，所有的 Init Container 会重新执行一遍，因此 Init Container 的执行结果应该是幂等的。
4. Init Container 执行时 Pod 的condition是 Initialized，status 为true, 但是Phase 是Pending；另外 readniessProbe 不能应用于 Init Container，Init Container 并不能代表Pod 就绪。
5. 在 Container上设置 livenessProbe 和在Pod上 activeDeadlineSeconds可以避免 init container 死循环失败，activeDeadlineSeconds 将会作用于所有的Init 容器。
6. Init Container 的 image 更新将会重启整个Pod， 应用容器的镜像更新只重启应用容器。

**资源计算**

Init Container 中可以设置对 CPU、内存资源的 request/limit ，因此也会影响 Pod 的资源计划。Pod 对资源的有效 request/limit 取决于下面两者中的最大值：

- 所有应用容器的 request/limit 之和
- Init containers 中的 request/limit  最大值


尽量使 Init Container 的 request/limit 小于应用容器的 request/limit。因为 Pod 调度是基于有效 request/limit 资源的， Init Container 原因申请过多资源，但在 Pod 生命周中实际用不了这么多资源，从而造成资源浪费。


### 5. PodPreset
PodPreset 用来为 Pod 设置预设值，可以自动为对应的 Pod 添加其他必要的信息，比如 labels、annotations、volumes 等。

启动该功能需要两步：
- 启用 `settings.k8s.io/v1alpha1/podpreset` API：在 api-server 启动时的 --runtime-config 中添加 ettings.k8s.io/v1alpha1=true。
- 启用准入控制器：在 `--enable-admission-plugins` 参数找那个添加 PodPreset。

```yaml
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
```



**PodPreset  工作流程**

当开启 PodPreset 时，Kubernetes 会提供一个名为 PodPreset 的准入控制器（ Admission controller ）, 这个控制器将在Pod 创建请求到来时应用预设功能。当Pod创建发生时， 系统执行如下的步骤：

- 获取可用的预设值
- 检查Pod的标签是否和预设值的标签匹配。
- 在Pod创建的时候，尝试合并在 Pod 预设值中定义的各种资源。
- 如果合并失败， 则记录Pod的合并资源失败事件，并创建没有注入预设值的 Pod 资源。
- 被 PodPreset 改动过的 Pod 会带有 annotation：`podpreset.admission.kubernetes.io/podpreset-<pod-preset name>: "<resource version>` 的形式

每个 Pod 可以匹配 0 或者多条预设值， 每个预设值可以和 0 到多个 Pod 相匹配。当一个 PodPreset 应用到一个或多个Pod时， K8s将会更改Pod的Spec：

- 对于env， envFrom 和 volumeMounts， K8s会修改Pod内的所有Containe的Container Spec。
- 对于volume， K8s 修改 Pod 的 Spec。

如果希望将某些 Pod 禁用 PodPreset，可以在 Pod 中添加 `podpreset.admission.kubernetes.io/exclude: "true"` 注解。


### 6. Disruption

PDB (Pod disruption budgets) 是指应该应用程序它所期望容忍的最小副本数。 例如，如果一个Deployment 有 .spec.replicas:5 表示在任何时间他将有5个副本。 如果PDB 允许在某个时间为4个服务，那么由Eviction API引发的主动删除Pod副本的将会只会同一时刻删除一个副本（确保总有4个副本在运行）。

PDB 不能阻止非主动宕机（involuntary disruption) 的发生。

Pod “主动”（intended）副本数量是由管理POD的 workload 资源 （deployment，statefulset）的。.spec.replicas 中计算得出。 控制面通过检查 pod的.metadata.ownerReferences 来确定 Pod所属的 workload 资源

在进行滚动更新的Pod将会参考PDB， workload 资源在执行滚动更新时缺不受PDB的限制，而是在应用升级时，由workload资源指定在Spec中的配置的失败处理 （？？？）

使用驱逐API（Evication API）逐出容器后，该容器会被优雅终止，并遵循其PodSpec中的`terminationGracePeriodSeconds`设置

```yaml
apiVersion: policy/v1beta1
kind: PodDisruptionBudget
metadata:
  name: zk-pdb
spec:
  selector:
    matchLabels:
      app: zk
  maxUnavailable: 1
```


### 7. 临时容器（Ephemeral Containers）

ephemeral containers 主要是用于帮助用户来完成特定目的的比如诊断功能的容器。它临时地存在在一个容器内部。

当由于容器崩溃或容器镜像不包含调试工具而导致 kubectl exec 无用时， 临时容器对于交互式故障排查很有用。

尤其是，[distroless 镜像](https://github.com/GoogleContainerTools/distroless) 允许用户部署最小的容器镜像，从而减少攻击面并减少故障和漏洞的暴露。 由于 distroless 镜像不包含 Shell 或任何的调试工具，因此很难单独使用 kubectl exec 命令进行故障排查。

使用临时容器时，启用[进程名字空间共享](https://kubernetes.io/docs/tasks/configure-pod-container/share-process-namespace/) 很有帮助，可以查看其他容器中的进程。

ephemeral container 不会自动重启，所以不适合来做服务。他通过ContainerSpec 进行描述和普通容器描述相同，但是有如下的限制。
- 临时容器没有端口配置，因此很多字段没有用， 如ports， livenessProbe，readinessProbe
- Pod的资源是不变了， 所以 resource 也是不能被指定的。
- 完整的字段列表，参看  [EphemeralContainer reference documentation.](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.19/#ephemeralcontainer-v1-core)

ephemeral container 不能直接被加入到pod.spec中， 需要通过在API中的特定的 ephemeralcontainers handler 进行处理。 所以用户是无法通过 kubectl edit 进行添加的。
和普通container一样，一旦你添加了 ephemeral container 你就不能将它在POD中移除和修改。

通过 kubectl --raw 命令可以创建一个 ephermeral container

```yaml
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
```


使用下面的命令，可以将ephermeral container 加到一个已经在运行的 example-pod 中
kubectl replace --raw /api/v1/namespaces/default/pods/example-pod/ephemeralcontainers  -f ec.json

通过 kubectl describe pod  可以查看 pod中的ephemeral container

```bash
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
```

可以使用以下命令连接到新的临时容器：

```bash
kubectl attach -it example-pod -c debugger
```


如果是网络问题也可以考虑使用  netshoot 工具：

```bash
sudo docker run -it  --net container:${containerID} nicolaka/netshoot
```

### 8. Pod Cmd & Args

Pod 的定义中也可以为 Container 指定启动命令和参数，

- 指定命令

```yaml
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
```



Pod 中定义的 command 和 args 会覆盖掉 Dockerfile 中定义的启动命令，其覆盖关系如下：


**K8S command/args VS. Docker Entrypoint/Cmd**
![在这里插入图片描述](https://img-blog.csdnimg.cn/aa0218c36eec47eba4e8058427f710d4.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)



### 9. Pod 的启动与终止

#### 9.1 Pod 启动流程

当创建一个 Pod 创建时上述组件工作过程如下：
1. 外部发起创建 Pod 请求，api-server 检查无误后将 Pod  存储到 etcd。如果是常见的 Deplpoyment 等控制器组件，则 controller 会执行状态拟合，创建期望数量的 Pod。
2. Scheduler 调度器发现新的未调度 Pod，基于资源需求、节点亲和、污点容忍等规则，将 Pod 调度到合适的节点。其操作就是修改 Pod 的 Spec，将 nodeName 字段设置为对应的节点名，然后存回 etcd。
3. 对应节点上的 kubelet 检测到有新的 Pod 调度到该节点，执行如下操作：
【1】向容器运行时比如 contained 发送请求，创建 infra 容器。
【2】容器运行时调用 CNI 网络插件初始化 Pod 的网络命名空间。
4. 如果存在 initContainer，kubelet 会请求 CRI 按顺序创建 init 容器。
5. kubelet 并发请求 CRI 创建 spec.containers 下定义的容器。
6. kubelet 监控容器，收集容器数据并上报给 api-server。

![在这里插入图片描述](https://img-blog.csdnimg.cn/36ca9f47bf4b4e498b29d87defebbf5d.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

图片来自:  [Goglides](https://goglides.io/understanding-kubernetes-pods/713/)

#### 9.2 Pod 终止流程

![在这里插入图片描述](https://img-blog.csdnimg.cn/b6c41821818e4622b7ed743585c783c6.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)



图片来自：[Golides](https://goglides.io/understanding-kubernetes-pods/713/)

### 10. Pod 容器设计模式

很多时候若干个进程是需要紧密运行在同一台主机上的，比如 Linux 进程组下的进程。在容器编排调度中，对于某些超亲密的容器进程，它们也是必须被紧密的调度运行在同一主机环境中。类似于 Kubernetes 的 Pod、Nomad 中的 task-group 都是对这种超亲密关系的抽象。

Google 的两位技术人员对基于容器的设计模式总结了论文和PPT：

- 论文：[Design patterns for container-based distributed systems](https://www.usenix.org/system/files/conference/hotcloud16/hotcloud16_burns.pdf)
- 课件：[https://www.usenix.org/sites/default/files/conference/protected-files/hotcloud16_slides_burns.pdf](https://www.usenix.org/sites/default/files/conference/protected-files/hotcloud16_slides_burns.pdf)


#### 10.1 SideCar

边车容器用来扩展和增强主容器的功能。在基于容器的分布式系统中，容器作为打包、重用的基本单位，其设计一般是符合单一职责原则的。此时如果我们需要一些额外的功能，可以通过 SideCar 容器实现。比如下载主容器所需的文件，收集主容器进程运行产生的日志等。

![在这里插入图片描述](https://img-blog.csdnimg.cn/fdddb5f457b64afb86b46a7fdd29fd41.png)

![在这里插入图片描述](https://img-blog.csdnimg.cn/21fa6aa24c0e48d29b2d1eaf93f40257.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

```yaml
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
```

SideCar 容器的使用有如下几个好处：
- **节约资源**：容器作为资源分配的单位，可以将资源优先配置给主容器，而 sidecar 容器可以配置较少的资源，避免其资源占用过多影响主进程。
- **职责分离**：容器作为打包的单位，主容器和 sidecar 容器是可以分开单独开发并打包的。
- **方便重用**：容器也是重用的单位，sidecar 可以用来辅助不同的主容器。
- **错误隔离**：当某个容器出现问题时，可以单独的进行降级、升级、回滚等，尽量不影响其他容器的运行。

#### 10.2 Ambassador

大使模式，是一种特殊的 SideCar，用于代理容器的访问请求。比如主容器需要访问数据库获取资源，对于主容器而言，其看到的始终是与本地通信，所有的通信细节都由大使容器实现。

![在这里插入图片描述](https://img-blog.csdnimg.cn/0d468f3cd6fe43f0a75af48acebdec3c.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)


下面是一个简单的示例，主容器产生的日志会经由 localhost:8080 的路径发送给 Ambassador 容器，然后经由 Ambassador 发送给不同的存储媒介。

```yaml
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
```


Adapter

适配器模式是另外一种特殊的 SideCar，和大使模式相反。大使模式是屏蔽的外部的变化，对主容器提供一致对外访问体验。而适配器模式则是屏蔽容器内部的变化，对外提供统一的访问模式。最常见的例子就是监控 API，比如 Prometheus，外界不用关心容器内部是怎样实现的，只需要访问固定的 API 获取指标就可以了。

![在这里插入图片描述](https://img-blog.csdnimg.cn/f0c00c778eac439196dd9cf7ebd3538f.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)


下面是一个 Adapter 的示例，Adapter用到了一个 nginx/nginx-prometheus-exporter 的一个镜像，该适配器会把 Nginx 的 stub staus 页转成 Prometheus 的 metrics，并放了9113端口和默认的 /mterics 的访问路径。

```yaml
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
```




