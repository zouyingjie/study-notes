@[toc]
操作系统中有 Volume 和 Mount 的概念。

- **Volume**: 表示物理存储的逻辑抽象
- **Mount**：将外部存储挂载到系统、容器中。

为了解决容器的数据存储，Kubernetes 也引入 Volume 的概念。Kubernetes 将 Volume 存储分为了普通的非持久化 Volume 和持久化的 PersistentVolume 两类。

### 1. Volumes

普通非持久化 Volume 的目的主要是为了在 Pod 的容器之间共享存储资源，防止 Kubelet 重启容器时造成数据丢失，因此它们的生命周期与 Pod 相同。Pod 中容器的重启不影响 Volume 中的数据存储，只有在 Pod 销毁后 Volume 中的数据才会被删除。

Kubernetes 默认支持许多种类型的卷，比如 emptyDir，hostPath， configMap、secret downwardAPI 等作为 Volume 挂载到容器中。

下面看两个常用的类型：

- emptyDir类型： 临时的卷类型，当一个Pod被分配到一个 Node 时候被创建，生命周期只维持在该 Pod 依然运行在该节点上的这段时间。在两个容器共享一些临时文件的场景下，可以使用这种类型。

```yaml
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
```

- hostPath类型：该卷类型会将宿主节点文件系统上的文件或者目录映射到Pod中。当Pod 需要访问一些宿主节点的数据的场景下可以使用。 Pod 被删除后 Host 上的文件依然存在，当新的 Pod 被调度到节点上挂载 Volume 后会看到对应 path 中的文件。

```yaml
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
```

![在这里插入图片描述](https://img-blog.csdnimg.cn/3ae7010167ce49cf9b94306c15275eb3.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)


图片来自 《Kubernetes In Action》


### 2. PV & PVC

除了临时性的存储以及共享文件外，我们还需要持久化的存储，无论 Pod 如何被调度，数据始终是一致的。比如我们在 Kubernetes 部署 MySQL 、MongoDB 等服务，任何时候看到的数据都应该是一样的。另外存储媒介是一个底层的实现，Pod 不应该关心具体的底层存储类型。因此为了实现持久化存储并使得底层存储与 Pod 解耦，Kuberetes 提出了 Persistent Volume（持久卷） 的概念，而 Pod 对存储资源的需求则被抽象为了 PersistentVolumeClaim（持久卷声明）。
- **Persistent Volume**：由集群管理员提供的集群存储声明，和普通的 Volume 没有本质的不同，只是其生命周期独立于任何使用 PV 的 Pod。
- **PersistentVolumeClaim**：由用户（开发人员）提供的存储请求声明。

这样在实际使用时，集群可以预先创建一批 PV 提供存储，而用户则只需要创建 PVC 提出自己的存储需求就行了。Kubernetes 会自动将 PVC 与 PV 进行绑定，在将 PVC 挂载到 Pod 中去，就可以进行数据的读写了。



![在这里插入图片描述](https://img-blog.csdnimg.cn/02751e90578f4f348e8776e42f5673ba.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)


图片来自《Kubernetes in Action》

下面是 PV 和 PVC 是使用示例：

**创建 PV**

下面是分别使用本地磁盘和 NFS 作为底层存储的 PV 示例：

```yaml
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
---

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
```



- accessModes: 访问权限
	- **ReadWriteOnce** 允许单个节点对 PV 进行读写操作
	- **ReadOnlyMany** 允许多个节点执行只读操作
	- **ReadWriteMany** 允许多个节点进行读写操作
- **capacity**: 持久卷的容量大小
- **volumeMode**： 持久卷模式 ，默认为 Filesystem，表示在挂载进 Pod 时如果卷存储设备为空会创建完整的文件系统目录。从 1.18 开始支持 Block 选项，表示卷作为原始的块设备，不会创建文件系统目录。此时 Pod 应用需要知道如何处理原始块设备。
- **persistentVolumeReclaimPolicy**：回收策略
	- **Retain：PVC** 删除后 PV 保留数据，PV 对其他 PVC 不可用。
	- **Delete**： PVC 删除后 PV 删除 PV 以及 PV 存储的数据。
	- **Recycle**：删除数据并对其他 PVC 可用，已弃用。



**创建 PVC**

```yaml
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
```

PVC 可以通过 storageClassName、accessModes、volumeMode 以及存储容量大小来选择符合条件的 PV，同时还可以通过标签和 Key 来选择执行 PV 的选择范围。

```yaml
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
```




**Pod 挂载 PVC 作为存储**

```yaml
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
```


下面是 PV 是使用过程中的生命周期：

| 阶段 |  |
|--|--|
| Provisioning |  卷资源在供给阶段，有两种方式，管理员手动生成或者系统根据PVC需求动态分配|
|Binding | 通过手动分配或者动态分配的方式，系统为一个PCV找到和合适的实际PV，则将两者双向绑定起来。|
|Using | 用户实际使用资源时的阶段。也就是在 Pod中像 volume一样使用pvc。|
| Protecting| 1. 有活跃的 Pod 正在使用卷时，如果删除PVC会进入这个阶段，此时会延迟删除 PVC 只到没有使用了。2. 如果删除了有绑定 PVC 的PV，也会进入该阶段，会等到 PV 和PVC 解开绑定后，才会删除PVC。|
| Reclaiming| 当用户不再需要 PV 资源了，删除PVC后，卷会进入这个阶段，根据定义PV会有两种处理方式：保留，用来维持卷，提供时间给到手动清理上面的数据删除， PV会被直接删除，包括里面的数据回收，（回收模式已经不再推荐使用）|





### 3. Storage Class

上面提到 PV 是由集群管理员预先创建的，属于静态处理。这里会有两个问题：

- **创建不及时**：集群管理员无法及时预知所有的存储需求，一旦 PVC 创建 Pod 部署后没有 PV 可用，会导致 Pod 运行出错。
- **需求不匹配**：比如管理员预先创建了 5G 大小的 PV，但此时 Pod 的 PVC 只需要 3G，此时如果绑定会造成 2G 的空间浪费。

于此同时，对于 PV 中数据的处理方式也不够灵活，除了保留、删除外其实还可以有更细化的处理，比如删除到回收站，保留一段时间后在删除，这些可能都需要管理员对 PV 的底层存储进行手动操作。

因此为了及时、灵活的分配与管理存储资源，Kubernetes 引入了动态存储分配的方式，其提供了 StorageClass 的资源对象，相当于 PV 的模板，该对象基于一个存储的提供者（Provisioner），比如 NFS，AWS S3 等，当用户需要存储资源时，只需要创建对应的 PVC 并指定 StorageClass，Pod 在创建完成需要使用存储资源时，StorageClass 就会根据需要自动的创建 PV 并绑定 PVC。


![在这里插入图片描述](https://img-blog.csdnimg.cn/1a29258f31e84a22bffd8278d29eea66.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

图片来自《Kubernetes in Action》

下面是一个使用 aws-ebs 作为 provisioner 的示例：

```yaml
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
```


这样在创建 PVC 只需要指定该 StorageClass 就可以了

​

```yaml
apiVersion: v1
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
```


另外可以根据需要实现创建不同的 StorageClass，比如下面使用 gce-pd 的 provisioner  创建的两个 StorageClass：使用标准存储的 slow 和使用 SSD 存储的 fast。在创建 PVC 时就可以根据性能需要选择合适的 StorageClass。这样对存储资源的分配和使用就实现了按需灵活分配的目的，除此之外，还可以在 provisioner 里添加处理代码来管理数据的保存、删除，实现更加灵活的管理。Kubernetes官方已经明确建议废弃Recycle策略，如有这类需求，应由Dynamic Provisioning去实现。

```yaml
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
```


### 4. PV 挂载过程

PV 持久化存储大多数时都是依赖一个远程存储服务，比如 NFS、AWS S3 等远程存储。类比操作系统中的引入存储设备的流程，首先我们需要准备好存储设备，然后将其作为块设备附加到系统中，最后挂载到文件系统后，就可以进行读写操作了。Kubernetes 中的操作也是类似的流程：

- **准备操作（Provision）**：由各个存储插件准备好自己的存储空间，相当于给 Kuberetes 准备一块磁盘。
- **附加操作（Attach）**: Pod 调度完成后，在对应宿主机的添加存储块，将远程存储添加到本地存储，相当于将磁盘添加到 Kubernetes。。比如使用 GCE Disk 服务，此时需要 kubelet 调用 GCE Disk 的 API 将远程存储添加到宿主机。
- **挂载操作（Mount）**：存储块添加完成后，Kuberetes 会格式化目录并将块挂载到宿主机的挂载点，这样在 Pod 中挂载卷时，写入到对应目录的数据就会写到远程存储中去。卷目录一般是 ​ `/var/lib/kubelet/pods/<Pod的ID>/volumes/kubernetes.io~<Volume类型>/<Volume名字>​`。

比如使用 EaseMesh 时 Control Plane 的 Pod 有三个卷，其中一个为持久卷，则在 Pod 的宿主机上述目录下就能找到对应卷的目录和数据。

```bash
node:➜  ~  |>kubectl get pods -n easemesh -o wide                                                                                                                                                                                        [~]
NAME                                                 READY   STATUS    RESTARTS   AGE     IP              NODE    NOMINATED NODE   READINESS GATES
easemesh-control-plane-1                             1/1     Running   0          2d19h   10.233.67.90    node   <none>           <none>


node:➜  ~  |>kubectl describe pod easemesh-control-plane-1 -n easemesh                                                                                                                                                                   [~]
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
```


现在基于 PodId 和 VolumeName 查看下宿主机的挂载目录，就会有对应的文件

```bash
node:➜  ~  |>sudo ls -l  /var/lib/kubelet/pods/1e2cfc60-493e-4bd3-804b-695e7c0af24f/volumes/                                                                                                                                             [~]
total 12
drwxr-xr-x 3 root root 4096 Dec  8 12:45 kubernetes.io~configmap
drwxr-x--- 3 root root 4096 Dec  8 12:45 kubernetes.io~local-volume
drwxr-xr-x 3 root root 4096 Dec  8 12:45 kubernetes.io~secret



node:➜  ~  |>sudo ls -l  /var/lib/kubelet/pods/1e2cfc60-493e-4bd3-804b-695e7c0af24f/volumes/kubernetes.io~configmap                                                                                                                      [~]
total 4
drwxrwxrwx 3 root root 4096 Dec  8 12:45 easemesh-cluster-cm


node:➜  ~  |>sudo ls -l  /var/lib/kubelet/pods/1e2cfc60-493e-4bd3-804b-695e7c0af24f/volumes/kubernetes.io~local-volume                                                                                                                   [~]
total 4
drwxr-xr-x 5 ubuntu ubuntu 4096 Jun 23 06:59 easemesh-storage-pv-3

node:➜  ~  |>sudo ls -l  /var/lib/kubelet/pods/1e2cfc60-493e-4bd3-804b-695e7c0af24f/volumes/kubernetes.io~local-volume/easemesh-storage-pv-3                                                                                             [~]
total 12
drwx------ 3 root root 4096 Dec  8 12:45 data
drwx------ 2 root root 4096 Jun 23 07:00 log
drwx------ 2 root root 4096 Dec  8 12:45 member
```


可以看到在持久卷对应的目录下有数据、日志等目录，而这些就是我们持久卷存储中的数据。我们用的宿主机磁盘的 /volumes/easemesh-storage 作为存储，因此在该目录下也会看到相同的文件：

```bash
node:➜  /volumes  |>kubectl describe  pv easemesh-storage-pv-3                                                                                                                                                                    [/volumes]
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

node:➜  /volumes  |>ls -l /volumes/easemesh-storage                                                                                                                                                                               [/volumes]
total 12
drwx------ 3 root root 4096 Dec  8 12:45 data
drwx------ 2 root root 4096 Jun 23 07:00 log
drwx------ 2 root root 4096 Dec  8 12:45 member
```


以上步骤是由 Kubernetes 的 PersistentVolume Controller、Attach/Detach Controller 控制器以及 Kubelet 中的 Volume Manager 来实现。

- **PersistentVolume Controller**：执行控制循环，保证所有绑定的 PV 都是可用的以及所有的 PVC 都能与合适的 PV 绑定。在执行拟合的过程中，Controller 会根据需要调用存储驱动插件的 Provision/Delete 接口进行操作。
- **Attach/Detach Controller**：在 PV 与 PVC 绑定， Pod 被调度到某个节点后，执行 Attach 操作，将远程存储添加到宿主机 Attact 都对应的节点。
- **Volume Manager**: 这是一个单独的 gorouting，独立于 kubelet 的主循环，执行具体的 Mount 操作，格式化磁盘并将设备挂载到宿主机目录。

而上述操作完成后，在 Pod 部署创建容器时，Kubelet 就会在启动容器时将我们已挂载的宿主机目录在挂载进容器中去，相当于执行如下命令：

```bash
$ docker run -v /var/lib/kubelet/pods/<Pod的ID>/volumes/kubernetes.io~<Volume类型>/<Volume名字>:/<容器内的目标目录> ......
```

以上就是 PV 挂载过程的简要概述，当然有挂载就有卸载的操作，以上三步分别对应有 Delete、Detach、Unmount 的相反操作，这里就不做赘述了。

### 5. CSI 插件

除了内置的插件，Kubernetes 还提供了扩展机制来实现自己的存储插件，早期的扩展方式是通过 FlexVolume 实现，在 1.23 版本已经被废弃，目前主要是通过 CSI（Container Storage Interface，容器存储接口）来实现，当前 Kubernetes 中内置的存储插件也在进行 CSI 改造，最终会将内置的插件从 Kubernetes 核心中移植出去。

上面提到的 PV 挂载过程，在没有 CSI 之前其 Provision、Attach、Mount 等操作最终都是通过 Kubernetes 内置的 Volume 插件完成的。

![在这里插入图片描述](https://img-blog.csdnimg.cn/5c3ba69d3f974307b04d552e5d563b33.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

图片来自
​https://medium.com/google-cloud/understanding-the-container-storage-interface-csi-ddbeb966a3b​

Kubernetes 早期内置了非常多的 Volume 插件，为其广泛推广发挥了重要的作用，但也随之引入了如下问题：

- **更新不及时**：插件内置在 Kubernetes 中，导致第三方存储厂商的发布节奏必须和 Kubernetes 保持一致。。
- **代码不可控**：很多插件都是由第三方存储厂商提供的可执行命令，将其和 Kubernetes 核心代码放一起会引发可靠性和安全性问题。

因此 Kubernetes 从 1.14 版本开始了内置插件的外迁工作。和之前看到 CNI 网络接口旨在提供移一致的容器网络操作一样，CSI 则是对所有的容器编排系统，比如 Kubernetes、Docker swarm 提供一致的存储访问接口，第三方存储插件只需要按照 CSI 实现对应的接口实现就可以满足所有系统的需要。


![在这里插入图片描述](https://img-blog.csdnimg.cn/9fcd91ba977845c7aaae635e64dc655e.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)

​​图片来自
​https://medium.com/google-cloud/understanding-the-container-storage-interface-csi-ddbeb966a3b​

使用了 CSI 插件后 Kuberetes 存储架构如下：

![在这里插入图片描述](https://img-blog.csdnimg.cn/458504ba1c684429ae386b380e02723e.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Im-5biM5bCE5pel,size_20,color_FFFFFF,t_70,g_se,x_16)
 
图片来自​https://medium.com/google-cloud/understanding-the-container-storage-interface-csi-ddbeb966a3b​

CSI 的内容主要包括两部分：

**容器系统的相关操作**

比如存储插件的注册移除操作以及与存储插件通信实现各种对 Volume 的管理，比如调用第三方存储插件进行 Volume 的创建、删除、扩缩容、查询监控信息等。主要抱包含下面一些组件：
-  Driver Register：将会被Node Driver Register所取代，负责注册第三方插件。插件注册进 kubelet 后就可以通过 gRPC 与插件进行通信了。
- External Provisioner：调用第三方插件的接口来完成数据卷的创建与删除操作。
- External Attacher：调用第三方插件的接口来完成数据卷的挂载和操作。
- External Resizer：调用第三方插件来完成数据卷的扩容操作。
- External Snapshotter：调用第三方插件来完成快照的创建和删除操作。
- External Health Monitor：调用第三方插件来提供度量监控数据功能。

这部分功能虽然从 Kubernetes 核心代码剥离了出来，但依然由 Kubernetes 官方维护。

**第三方插件的具体操作**

需要由云存储厂商实现来执行具体的操作。比如我们上面提到的 Provision、Attach、Mount 操作。这里主要包含三个服务：
- **CSI Identity**：用于对外暴露插件的基本信息，比如插件版本号、插件所支持的CSI规范版本、是否支持存储卷创建及删除功能、是否支持存储卷挂载功能，等等。另外还可以用于检查插件的健康状态，开发者可以通过实现 Probe 接口对外提供存储的健康度量信息。

```go
// IdentityServer is the server API for Identity service.
type IdentityServer interface {
  // return the version and name of the plugin
  GetPluginInfo(context.Context, *GetPluginInfoRequest) (*GetPluginInfoResponse, error)
  // reports whether the plugin has the ability of serving the Controller interface
  GetPluginCapabilities(context.Context, *GetPluginCapabilitiesRequest) (*GetPluginCapabilitiesResponse, error)
  // called by the CO just to check whether the plugin is running or not
  Probe(context.Context, *ProbeRequest) (*ProbeResponse, error)
}
```


- **CSI Controller**：提供各种对存储系统也就是 Volume 的管理控制接口，其实就是上面提到的准备和 Attach 阶段。比如譬如准备和移除存储（Provision、Delete操作）、附加与分离存储（Attach、Detach操作）、对存储进行快照等。存储插件并不一定要实现这个接口的所有方法，对于存储本身就不支持的功能，可以在CSI Identity 服务中声明为不提供。



```go
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
```


- **CSI Node 服务**：Controller 中定义的功能都不是在宿主机层面操作的，这部分操作由 CSI Node 服务实现，也就是上面提到的 mount 阶段。其在集群节点层面执行具体操作，比如将 Volume 分区和格式化、将存储卷挂载到指定目录上或者将存储卷从指定目录上卸载等。



```go
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
```

  
而对于 CSI 插件的编写，其实就是实现上面的三个接口，下面看一个比较简单的 csi-s3   插件的例子，项目目录结构如下：

```bash
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
```

在 pkg/driver 目录下有三个文件：
-  **identityserver.go** 代表 CSI Identity 服务，提供了 CSI 插件的基本信息，Probe 探针接口等。
- **controllerserver.go** 代表 CSI Controller 服务，定义了操作 Volume 的相关操作。下面是 CSI  中完整的接口列表，cis-s3  只实现了其中一部分：

```go
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
```

- **nodeserver.go**  代表CSI Node 服务，定义了在宿主机挂载 Volume 的操作，其实现了 CSI 中 NodeServer 的接口。下面是接口列表，我们提到在 Mount 阶段需要将远程存储作为块设备添加都节点并挂载到对应目录，这两步就是通过 NodeStageVolume 和 NodePublishVolume 实现的。



三个服务都是通过 gRPC 与 Kubernetes 进行通信的，下面是服务的启动代码，这里定义了 Driver 的版本信息和插件名，CSI 插件要求名字遵守 反向 DNS 格式。

```go
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
```

以上是 csi-c3 的简单示例，在将该插件部署到我们的 Kuberetes 后就可以用该插件作为提供商来动态创建 PV 了。对于 CSI 的部署有一些部署原则：

- CSI Node 服务需要与宿主机节点进行交互，因此需要通过 DaemonSet 在每个节点都启动插件来与 Kubelet 通信。
- 通过 StatefulSet 在任意节点启动一个 CSI 插件已提供 CSI Controller 服务。这样可以保证 CSI 服务的稳定性和正确性。

将 CSI 插件部署后就可以使用 StorageClass 和 PVC 了，示例如下：

**部署 CSI**

```bash
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
```
**使用 CSI** 
```yaml

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
```
