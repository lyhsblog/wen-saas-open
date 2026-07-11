# 数据目录：无需预建。compose 各服务直接 bind 挂载 ./data/ 下各自子目录
#（postgresql / redis/data / rocketmq/...），目录缺失时 Docker 会自动创建（已实测）。
# 首次启动请确保 ./data 不存在或为空，避免残留旧数据。

# 启动在后台运行
docker compose up -d

# 停止并删除容器 (数据在宿主机 ./data/，不会被删除；要清空数据就删 ./data 目录)
docker compose down

## RocketMQ：brokerIP1 为什么是 host.docker.internal（原理）

理解前先分清 RocketMQ 的**两条独立通道**——它们用的地址来源不同，`brokerIP1` 只影响其中一条：

| 通道 | 谁连谁 | 用什么地址 | 受 brokerIP1 影响 |
|---|---|---|---|
| **A. 出方向找 NameServer** | broker→namesrv、proxy→namesrv | `NAMESRV_ADDR=rocketmq-namesrv:9876`（**容器名**，走 Docker 内网 DNS，如 `rocketmq-namesrv→172.18.0.x`） | 否 |
| **B. 入方向连 Broker** | proxy→broker、dashboard→broker、宿主机 client→broker | broker **注册到 namesrv 的地址 = `brokerIP1`** | 是 |

- **通道 A 与 brokerIP1 无关**：broker / proxy 主动拨出去找 namesrv 用的是容器名，不管 brokerIP1 设成啥都通，所以 namesrv 永远知道 broker 存在。
- **通道 B 全靠 brokerIP1**：别人问 namesrv「broker 在哪」，拿到的就是 `brokerIP1`，再各自去连。

**为什么不能用 `127.0.0.1`**：容器内的 `127.0.0.1` 指的是**容器自己**（proxy 自身 IP 如 172.18.0.4）。若 `brokerIP1=127.0.0.1`，proxy / dashboard 拿到这地址会连自己的 loopback → 那儿没有 broker → 连不上（dashboard 加载不出 Broker 列表就是这个病根）。`127.0.0.1` 只伺候了「恰好住在宿主机上、且 10911 端口已映射」的那一个消费者（宿主机 app），容器一侧全废。

**为什么不用容器名 `rocketmq-broker`**：容器之间本可走网桥直连（`rocketmq-broker:10911→172.18.0.x`，最短路径），但宿主机 app 解析不了这个容器名 → 宿主机又废了。

**`host.docker.internal` 是唯一两全值**：它语义是「宿主机」，宿主机和所有容器都能解析到宿主机，于是大家统一走**宿主机映射出来的 10911**：

```
宿主机 app   →(/etc/hosts)→ 127.0.0.1   ─┐
proxy 容器   →(host-gateway)→ ::254/网关 ─┼─→ 宿主机映射的 10911 → broker
dashboard    →(host-gateway)→ ::254/网关 ─┘
```

代价是容器要「发卡弯」（出到宿主机再绕回映射端口，非内网直连，效率略低），但这是 Docker Desktop 上宿主机 + 全部容器都能用的单一地址——所以选它不是迁就 dashboard，而是这套「宿主机 app + 容器组件」混合拓扑下本就更正确的值。

## RocketMQ：宿主机 hosts 约定（必读）

承上：`brokerIP1=host.docker.internal` 后，宿主机 app 和容器内 rocketmq-dashboard 都拿这同一个值在各自网络栈解析。要让两边都通，**需在宿主机 `/etc/hosts` 加一行**：

```
127.0.0.1 host.docker.internal
```

- app（宿主机 Java）解析成 `127.0.0.1` → 走 loopback 直连映射出来的 broker 端口（10911 等）。
- dashboard（容器内）用容器自己的 `/etc/hosts`（Docker 注入的 host-gateway），不受这行影响，Broker 列表正常加载。

> ⚠️ **若用 Clash / Surge 等代理且开了 fake-ip 模式**：它会劫持 DNS，把 `host.docker.internal` 解析成 `198.18.x.x` 假 IP，连接被吞进代理隧道，导致 app 的 MQ 发送**静默失败**（异步任务永久 `pending`、audit/站内信/邮件 publish 全挂，日志 `send request to <host.docker.internal/198.18.0.x:10911> failed`）。macOS 的 `/etc/hosts` 命中即返回、不发 DNS 查询，正好能绕开 fake-ip——所以上面那行是**必须**的，不是可选兜底。
>
> 验证（不要用 `nc`/`telnet`，对 fake-ip 会返回假 OK）：`lsof -nP -p <app-pid> | grep :10911` 应是 `ESTABLISHED` 到 `127.0.0.1`（不是 198.18.x）；dashboard `curl -s http://localhost:18082/cluster/list.query` 看 `brokerActive=true`。
>
> 改 `broker.conf` 后须 `docker restart rocketmq-broker` **再重启 app**（运行中的 producer 缓存了旧路由）。

## RocketMQ：v5 gRPC 客户端的两个本机必读（否则 app 起不来）

后端已迁到 RocketMQ 5.x gRPC 客户端（`rocketmq-v5-client-spring-boot-starter`，连 **Proxy** 的 gRPC 端口，不再直连 NameServer）。本机 docker 有两个坑会让 app 启动时 10 个消费者报 `Failed to start RocketMQ push consumer`。

> **拓扑：broker 容器 + 独立 proxy 容器（`rocketmq-broker` 跑 `sh mqbroker`、`rocketmq-proxy` 跑 `sh mqproxy`，CLUSTER 模式）**。宿主机 app 走 `rocketmq-proxy` 的 gRPC 8081。
> **不要改成合并模式 `mqbroker --enable-proxy`**：合并(LOCAL proxy)下 proxy 把自报给客户端的 gRPC 端点绑死成 `broker.conf` 的 `brokerIP1=host.docker.internal`，而宿主机上的 app 对它建 gRPC telemetry 长流连不通（`UNAVAILABLE: Network closed`／改回 127.0.0.1 又因端点不一致报 `Stream is already completed`）。独立 proxy 才能把「容器内客户端用的 broker 10911 地址(host.docker.internal)」与「宿主 app 用的 proxy 8081 gRPC 地址(127.0.0.1)」解耦——这是本机 host+容器混合拓扑唯一稳的方式（2026-06-24 实测合并模式起不来，已回退）。

### 1. Proxy gRPC 端口必须 `8081:8081`，**不能重映射**

`docker-compose.yml` 里 `rocketmq-proxy` 的 gRPC 端口映射保持 `8081:8081`（宿主=容器一致）。原理：RocketMQ 5.x 客户端连上 Proxy 后，Proxy 在 topic 路由里**通告自己的容器内端口 8081** 给客户端建 telemetry 流。若宿主重映射成别的端口（如曾经的 `18081:8081`），客户端初始连 18081 能拉到路由，但随后用 Proxy 自报的 `127.0.0.1:8081` 建 telemetry 流时连不上（宿主 8081 没通到 Proxy）→ 流被关 → 报：

```
Stream is already completed, no further calls are allowed
  → Failed to fetch topics after 3 attempts → Failed to start RocketMQ push consumer
```

对应 `application-dev.yml` 的 `rocketmq.producer/push-consumer.endpoints` 也写 `127.0.0.1:8081`。

### 2. 消费者订阅的 topic 必须**预先存在** —— 已由 `rocketmq-topic-init` 自动建好

v5 gRPC consumer 启动时严格 `fetchTopicRoute`，topic 不存在就直接启动失败（v4 remoting 客户端容忍、订阅时空转）。app 既是这些 topic 的生产者又是消费者，但**消费者在启动期先于任何消息生产**，`autoCreateTopicEnable` 只在「发消息到不存在的 topic」时建、不在消费订阅时建，于是从未有过流量的 topic 会让对应消费者起不来。

`docker-compose.yml` 已加 **一次性 init 容器 `rocketmq-topic-init`**：`docker compose up -d` 时它在 broker 注册到 namesrv 后，自动预建 10 个消费者订阅的 topic 再退出（`docker compose ps` 里看不到它属正常，它是跑完即停）。**首次搭建 / 删除 `./data` 重来后都不用再手动建**。

这 10 个 = app 里 10 个 `@RocketMQMessageListener` push consumer 一一对应（`wen_setting_invalidate`/`wen_tenant_invalidate` 不在内：前者无启动期 push consumer，后者已被 `TenantCacheEvictor` 取代为死 topic）。需手动补建时（init 列表在 compose 里同步维护）：

```bash
for t in wen_audit_operation wen_audit_login wen_notify_station wen_notify_email \
         wen_notify_sms wen_notify_im wen_task_execute wen_api_call_log \
         wen_event wen_webhook_send; do
  docker exec rocketmq-broker sh mqadmin updateTopic -n rocketmq-namesrv:9876 -c DefaultCluster -t $t
done
```

> 验证 init 是否成功：`docker logs rocketmq-topic-init` 末行应是 `all topics ready`；`docker exec rocketmq-broker sh mqadmin topicList -n rocketmq-namesrv:9876 | grep -c '^wen_'` ≥ 10。

## RocketMQ：死信兜底（DLQ）—— 本机无需额外配置

后端的死信兜底（两套重试语义 + 死信落库）已**内聚到消费链路**：v5 `MessageView.getDeliveryAttempt()` 让消费者在 `consume(...)` 里直接判定末次失败，毒丸即 ack 落库、瞬时异常达 `max-delivery-attempts` 落库并 ack。**不再有独立的 `DeadLetterMonitor`、不订阅任何 `%DLQ%`**，死信表 `sys_mq_dead_letter` 是唯一真相源。

> 本机无需任何 DLQ 相关配置。验证：制造一条解析不了的毒丸消息 → 查 `sys_mq_dead_letter`（source=poison_fast_fail）；或构造连续瞬时失败达上限 → 查 source=retry_exhausted。
