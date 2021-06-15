
### 一.分布式事务概述

分布式情况下，可能出现一些服务事务不一致的情况

* 远程服务假失败
* 远程服务执行完成后，下面其他方法出现异常
![在这里插入图片描述](https://img-blog.csdnimg.cn/20210615224224219.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MjIzMjkzMQ==,size_16,color_FFFFFF,t_70#pic_center)


### 二.分布式事务的解决方案

#### 1.2PC模式

数据库支持的2PC【二阶提交法】，又叫做XA Transactions。Mysql5.5开始支持，SQL Server 2005开始支持，Oracle7开始支持，XA是一个两阶段提交协议，该协议分为一下两阶段

第一阶段：事务协调器要求每个涉及到事务的数据库与提交此操作，并反应是否可以提交。

第二阶段：事务协调器要求每个数据库提交数据

如果有任何一个数据库否决此次提交，那么所有数据库都会被要求回滚它们在在此事务中提交的数据

特点：

- XA协议比较简单。而且一旦商业数据库实现了XA协议。使用分布式事务的成本也比较低
- XA性能不理想，特别是在交易下单链路。往往并发量很高，XA无法满足高并发场景
- XA目前在商业数据库支持的比较理想，在mysql数据库中支持的不太理想，mysql的XA实现没有记录prepare阶段日志，主备切换会导致主库与备库的数据不一致

#### 2.柔性事务-TCC事务补偿型方案

刚性事务，遵循ACID原则，强一致性

柔性事务，遵循BASE理论，最终一致性

与刚性事务不同，柔性事务允许一定时间内，不同节点的数据不一致，单要求最终一致。
![在这里插入图片描述](https://img-blog.csdnimg.cn/20210615224326944.jpg?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MjIzMjkzMQ==,size_16,color_FFFFFF,t_70#pic_center)


第一阶段：prepare行为，调用自定义的prepare逻辑准备数据

第二阶段：commit行为，调用自定义的commit逻辑

第三阶段rollback行为，调用自定义的rollback逻辑

所谓TCC模式，是支持把自定义的分支事务纳入到全局事务管理中

#### 3.柔性事务-最大努力通知方案

最大努力通知型( Best-effort delivery)是最简单的一种柔性事务，适用于一些最终一致性时间敏感度低的业务，且被动方处理结果 不影响主动方的处理结果。典型的使用场景：如银行通知、商户通知等。最大努力通知型的实现方案，一般符合以下特点：

  1、不可靠消息：业务活动主动方，在完成业务处理之后，向业务活动的被动方发送消息，直到通知N次后不再通知，允许消息丢失(不可靠消息)。

  2、定期校对：业务活动的被动方，根据定时策略，向业务活动主动方查询(主动方提供查询接口)，恢复丢失的业务消息。

#### 4.柔性事务-可靠消息+最终一致方案（异步确保型）

业务逻辑在业务事务提交之时，向实时消息服务请求发送消息，实时消息服务只记录消息数据，而不是真正的发送。业务处理服务在业务提交之后，向实时消息服务确认发送。只有在得到确认发送指令后，实时消息服务才会真正发送。

### 三.使用seata解决分布式事务问题（2PC)

seata中文官网：http://seata.io/zh-cn/docs/user/quickstart.html

TC (Transaction Coordinator) - 事务协调者

维护全局和分支事务的状态，驱动全局事务提交或回滚。

TM (Transaction Manager) - 事务管理器

定义全局事务的范围：开始全局事务、提交或回滚全局事务。

RM (Resource Manager) - 资源管理器

管理分支事务处理的资源，与TC交谈以注册分支事务和报告分支事务的状态，并驱动分支事务提交或回滚。

 ![img](https://img-blog.csdnimg.cn/img_convert/7b83b011de7809f4569dd238fb2342ed.png) 

```txt
Seata控制分布式事务
1. 每一个微服务必须创建undo_log
2. 安装事务协调器：https://github.com/seata/seata/releases    1.0.0版本     
3. 解压并启动seata-server        
	registry.conf 注册中心配置,修改registry type=nacos
4. 每个想要使用分布式事务的微服务都要用seata DataSourceProxy代理自己的数据源
5. 每个微服务都不必须导入        
	修改file.conf：vgroup_mapping.{当前应用的名字}-fescar-service-group          
6. 给分布式大事务入口标注 @GlobalTransactional       
7. 每一个远程的小事务用 @Transactional
8. 启动测试分布式事务
```

导入依赖

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
</dependency>
```

环境搭建

下载senta-server-0.7.1并修改`register.conf`,使用nacos作为注册中心

```shell
registry {
  # file 、nacos 、eureka、redis、zk、consul、etcd3、sofa
  type = "nacos"

  nacos {
    serverAddr = "#:8848"
    namespace = "public"
    cluster = "default"
  }
```

将`register.conf`和`file.conf`复制到需要开启分布式事务的根目录，并修改`file.conf`

 `vgroup_mapping.${application.name}-fescar-service-group = "default"`

```shell
service {
  #vgroup->rgroup
  vgroup_mapping.mall-ware-fescar-service-group = "default"
  #only support single node
  default.grouplist = "127.0.0.1:8091"
  #degrade current not support
  enableDegrade = false
  #disable
  disable = false
  #unit ms,s,m,h,d represents milliseconds, seconds, minutes, hours, days, default permanent
  max.commit.retry.timeout = "-1"
  max.rollback.retry.timeout = "-1"
}
```

使用seata包装数据源

```java
@Configuration
public class MySeataConfig {
    @Autowired
    DataSourceProperties dataSourceProperties;

    @Bean
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {

        HikariDataSource dataSource = dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        if (StringUtils.hasText(dataSourceProperties.getName())) {
            dataSource.setPoolName(dataSourceProperties.getName());
        }
        return new DataSourceProxy(dataSource);
    }
}
```

在大事务的入口标记注解`@GlobalTransactional`开启全局事务，并且每个小事务标记注解`@Transactional`

```java
@GlobalTransactional
@Transactional
@Override
public SubmitOrderResponseVo submitOrder(OrderSubmitVo submitVo) {
}
```

### 四. 使用消息队列实现最终一致性

#### 安装rabbitmq

```shell
docker run -d --name rabbitmq -p 5671:5671 -p 5672:5672 -p 4369:4369 -p 25672:25672 -p 15671:15671 -p 15672:15672 rabbitmq:management

##########
# 4369,25672(Erlang发现&集群端口)
# 5672，5671（AMQP端口）
# 15672 （web管理后台端口）
# 61613 61614 （STOMP协议端口）
# 1883 8883 （MQTT协议端口）
# 官网：https:www.rabbitmq.com/networking.html
```



#### (1) 延迟队列的定义与实现

* 定义：

  延迟队列存储的对象肯定是对应的延时消息，所谓"延时消息"是指当消息被发送以后，并不想让消费者立即拿到消息，而是等待指定时间后，消费者才拿到这个消息进行消费。

* 实现：

  rabbitmq可以通过设置队列的`TTL`和死信路由实现延迟队列

  * TTL：

  >RabbitMQ可以针对Queue设置x-expires 或者 针对Message设置 x-message-ttl，来控制消息的生存时间，如果超时(两者同时设置以最先到期的时间为准)，则消息变为dead letter(死信)

  

  * 死信路由DLX

  >RabbitMQ的Queue可以配置x-dead-letter-exchange 和x-dead-letter-routing-key（可选）两个参数，如果队列内出现了dead letter，则按照这两个参数重新路由转发到指定的队列。

  >- x-dead-letter-exchange：出现dead letter之后将dead letter重新发送到指定exchange
  >- x-dead-letter-routing-key：出现dead letter之后将dead letter重新按照指定的routing-key发送

![在这里插入图片描述](https://img-blog.csdnimg.cn/20210615224645485.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MjIzMjkzMQ==,size_16,color_FFFFFF,t_70)


针对订单模块创建以上消息队列，创建订单时消息会被发送至队列`order.delay.queue`，经过`TTL`的时间后消息会变成死信以`order.release.order`的路由键经交换机转发至队列`order.release.order.queue`，再通过监听该队列的消息来实现过期订单的处理

#### (2) 延迟队列使用场景
![在这里插入图片描述](https://img-blog.csdnimg.cn/20210615224753296.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MjIzMjkzMQ==,size_16,color_FFFFFF,t_70)



**为什么不能用定时任务完成？**

如果恰好在一次扫描后完成业务逻辑，那么就会等待两个扫描周期才能扫到过期的订单，不能保证时效性
![在这里插入图片描述](https://img-blog.csdnimg.cn/20210615224903276.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MjIzMjkzMQ==,size_16,color_FFFFFF,t_70#pic_center)


#### (3) 定时关单与库存解锁主体逻辑

* 订单超时未支付触发订单过期状态修改与库存解锁

> 创建订单时消息会被发送至队列`order.delay.queue`，经过`TTL`的时间后消息会变成死信以`order.release.order`的路由键经交换机转发至队列`order.release.order.queue`，再通过监听该队列的消息来实现过期订单的处理
>
> * 如果该订单已支付，则无需处理
> * 否则说明该订单已过期，修改该订单的状态并通过路由键`order.release.other`发送消息至队列`stock.release.stock.queue`进行库存解锁

* 库存锁定后延迟检查是否需要解锁库存

> 在库存锁定后通过`路由键stock.locked`发送至`延迟队列stock.delay.queue`，延迟时间到，死信通过`路由键stock.release`转发至`stock.release.stock.queue`,通过监听该队列进行判断当前订单状态，来确定库存是否需要解锁

* 由于`关闭订单`和`库存解锁`都有可能被执行多次，因此要保证业务逻辑的幂等性，在执行业务是重新查询当前的状态进行判断
* 订单关闭和库存解锁都会进行库存解锁的操作，来确保业务异常或者订单过期时库存会被可靠解锁
![在这里插入图片描述](https://img-blog.csdnimg.cn/20210615225132843.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MjIzMjkzMQ==,size_16,color_FFFFFF,t_70#pic_center)
![在这里插入图片描述](https://img-blog.csdnimg.cn/20210615225159923.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MjIzMjkzMQ==,size_16,color_FFFFFF,t_70#pic_center)
![在这里插入图片描述](https://img-blog.csdnimg.cn/20210615225235804.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MjIzMjkzMQ==,size_16,color_FFFFFF,t_70#pic_center)


#### (4) 创建业务交换机和队列

* 订单模块

```java
@Configuration
public class MyRabbitmqConfig {
    @Bean
    public Exchange orderEventExchange() {
        /**
         *   String name,
         *   boolean durable,
         *   boolean autoDelete,
         *   Map<String, Object> arguments
         */
        return new TopicExchange("order-event-exchange", true, false);
    }

    /**
     * 延迟队列
     * @return
     */
    @Bean
    public Queue orderDelayQueue() {
       /**
            Queue(String name,  队列名字
            boolean durable,  是否持久化
            boolean exclusive,  是否排他
            boolean autoDelete, 是否自动删除
            Map<String, Object> arguments) 属性
         */
        HashMap<String, Object> arguments = new HashMap<>();
        //死信交换机
        arguments.put("x-dead-letter-exchange", "order-event-exchange");
        //死信路由键
        arguments.put("x-dead-letter-routing-key", "order.release.order");
        arguments.put("x-message-ttl", 60000); // 消息过期时间 1分钟
        return new Queue("order.delay.queue",true,false,false,arguments);
    }

    /**
     * 普通队列
     *
     * @return
     */
    @Bean
    public Queue orderReleaseQueue() {

        Queue queue = new Queue("order.release.order.queue", true, false, false);

        return queue;
    }

    /**
     * 创建订单的binding
     * @return
     */
    @Bean
    public Binding orderCreateBinding() {
        /**
         * String destination, 目的地（队列名或者交换机名字）
         * DestinationType destinationType, 目的地类型（Queue、Exhcange）
         * String exchange,
         * String routingKey,
         * Map<String, Object> arguments
         * */
        return new Binding("order.delay.queue", Binding.DestinationType.QUEUE, "order-event-exchange", "order.create.order", null);
    }

    @Bean
    public Binding orderReleaseBinding() {
        return new Binding("order.release.order.queue",
                Binding.DestinationType.QUEUE,
                "order-event-exchange",
                "order.release.order",
                null);
    }

    @Bean
    public Binding orderReleaseOrderBinding() {
        return new Binding("stock.release.stock.queue",
                Binding.DestinationType.QUEUE,
                "order-event-exchange",
                "order.release.other.#",
                null);
    }
}
```

* 库存模块

```java
@Configuration
public class MyRabbitmqConfig {

    @Bean
    public Exchange stockEventExchange() {
        return new TopicExchange("stock-event-exchange", true, false);
    }

    /**
     * 延迟队列
     * @return
     */
    @Bean
    public Queue stockDelayQueue() {
        HashMap<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", "stock-event-exchange");
        arguments.put("x-dead-letter-routing-key", "stock.release");
        // 消息过期时间 2分钟
        arguments.put("x-message-ttl", 120000);
        return new Queue("stock.delay.queue", true, false, false, arguments);
    }

    /**
     * 普通队列，用于解锁库存
     * @return
     */
    @Bean
    public Queue stockReleaseStockQueue() {
        return new Queue("stock.release.stock.queue", true, false, false, null);
    }


    /**
     * 交换机和延迟队列绑定
     * @return
     */
    @Bean
    public Binding stockLockedBinding() {
        return new Binding("stock.delay.queue",
                Binding.DestinationType.QUEUE,
                "stock-event-exchange",
                "stock.locked",
                null);
    }

    /**
     * 交换机和普通队列绑定
     * @return
     */
    @Bean
    public Binding stockReleaseBinding() {
        return new Binding("stock.release.stock.queue",
                Binding.DestinationType.QUEUE,
                "stock-event-exchange",
                "stock.release.#",
                null);
    }
}
```

#### (5) 库存自动解锁

##### 1）库存锁定

在库存锁定是添加以下逻辑

* 由于可能订单回滚的情况，所以为了能够得到库存锁定的信息，在锁定时需要记录库存工作单，其中包括订单信息和锁定库存时的信息(仓库id，商品id，锁了几件...)
* 在锁定成功后，向延迟队列发消息，带上库存锁定的相关信息

```java
@Transactional
@Override
public Boolean orderLockStock(WareSkuLockVo wareSkuLockVo) {
    //因为可能出现订单回滚后，库存锁定不回滚的情况，但订单已经回滚，得不到库存锁定信息，因此要有库存工作单
    WareOrderTaskEntity taskEntity = new WareOrderTaskEntity();
    taskEntity.setOrderSn(wareSkuLockVo.getOrderSn());
    taskEntity.setCreateTime(new Date());
    wareOrderTaskService.save(taskEntity);

    List<OrderItemVo> itemVos = wareSkuLockVo.getLocks();
    List<SkuLockVo> lockVos = itemVos.stream().map((item) -> {
        SkuLockVo skuLockVo = new SkuLockVo();
        skuLockVo.setSkuId(item.getSkuId());
        skuLockVo.setNum(item.getCount());
        List<Long> wareIds = baseMapper.listWareIdsHasStock(item.getSkuId(), item.getCount());
        skuLockVo.setWareIds(wareIds);
        return skuLockVo;
    }).collect(Collectors.toList());

    for (SkuLockVo lockVo : lockVos) {
        boolean lock = true;
        Long skuId = lockVo.getSkuId();
        List<Long> wareIds = lockVo.getWareIds();
        if (wareIds == null || wareIds.size() == 0) {
            throw new NoStockException(skuId);
        }else {
            for (Long wareId : wareIds) {
                Long count=baseMapper.lockWareSku(skuId, lockVo.getNum(), wareId);
                if (count==0){
                    lock=false;
                }else {
                    //锁定成功，保存工作单详情
                    WareOrderTaskDetailEntity detailEntity = WareOrderTaskDetailEntity.builder()
                            .skuId(skuId)
                            .skuName("")
                            .skuNum(lockVo.getNum())
                            .taskId(taskEntity.getId())
                            .wareId(wareId)
                            .lockStatus(1).build();
                    wareOrderTaskDetailService.save(detailEntity);
                    //发送库存锁定消息至延迟队列
                    StockLockedTo lockedTo = new StockLockedTo();
                    lockedTo.setId(taskEntity.getId());
                    StockDetailTo detailTo = new StockDetailTo();
                    BeanUtils.copyProperties(detailEntity,detailTo);
                    lockedTo.setDetailTo(detailTo);
                    rabbitTemplate.convertAndSend("stock-event-exchange","stock.locked",lockedTo);

                    lock = true;
                    break;
                }
            }
        }
        if (!lock) throw new NoStockException(skuId);
    }
    return true;
}
```

##### 2）监听队列

* 延迟队列会将过期的消息路由至`"stock.release.stock.queue"`,通过监听该队列实现库存的解锁
* 为保证消息的可靠到达，我们使用手动确认消息的模式，在解锁成功后确认消息，若出现异常则重新归队

```java
@Component
@RabbitListener(queues = {"stock.release.stock.queue"})
public class StockReleaseListener {

    @Autowired
    private WareSkuService wareSkuService;

    @RabbitHandler
    public void handleStockLockedRelease(StockLockedTo stockLockedTo, Message message, Channel channel) throws IOException {
        log.info("************************收到库存解锁的消息********************************");
        try {
            wareSkuService.unlock(stockLockedTo);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),true);
        }
    }
}
```

##### 3）库存解锁

* 如果工作单详情不为空，说明该库存锁定成功
  * 查询最新的订单状态，如果订单不存在，说明订单提交出现异常回滚，或者订单处于已取消的状态，我们都对已锁定的库存进行解锁
* 如果工作单详情为空，说明库存未锁定，自然无需解锁
* 为保证幂等性，我们分别对订单的状态和工作单的状态都进行了判断，只有当订单过期且工作单显示当前库存处于锁定的状态时，才进行库存的解锁

```java
 @Override
    public void unlock(StockLockedTo stockLockedTo) {
        StockDetailTo detailTo = stockLockedTo.getDetailTo();
        WareOrderTaskDetailEntity detailEntity = wareOrderTaskDetailService.getById(detailTo.getId());
        //1.如果工作单详情不为空，说明该库存锁定成功
        if (detailEntity != null) {
            WareOrderTaskEntity taskEntity = wareOrderTaskService.getById(stockLockedTo.getId());
            R r = orderFeignService.infoByOrderSn(taskEntity.getOrderSn());
            if (r.getCode() == 0) {
                OrderTo order = r.getData("order", new TypeReference<OrderTo>() {
                });
                //没有这个订单||订单状态已经取消 解锁库存
                if (order == null||order.getStatus()== OrderStatusEnum.CANCLED.getCode()) {
                    //为保证幂等性，只有当工作单详情处于被锁定的情况下才进行解锁
                    if (detailEntity.getLockStatus()== WareTaskStatusEnum.Locked.getCode()){
                        unlockStock(detailTo.getSkuId(), detailTo.getSkuNum(), detailTo.getWareId(), detailEntity.getId());
                    }
                }
            }else {
                throw new RuntimeException("远程调用订单服务失败");
            }
        }else {
            //无需解锁
        }
    }
```

#### (6) 定时关单

##### 1) 提交订单

```java
@Transactional
@Override
public SubmitOrderResponseVo submitOrder(OrderSubmitVo submitVo) {

    //提交订单的业务处理。。。
    
    //发送消息到订单延迟队列，判断过期订单
    rabbitTemplate.convertAndSend("order-event-exchange","order.create.order",order.getOrder());

               
}
```

##### 2) 监听队列

创建订单的消息会进入延迟队列，最终发送至队列`order.release.order.queue`，因此我们对该队列进行监听，进行订单的关闭

```java
@Component
@RabbitListener(queues = {"order.release.order.queue"})
public class OrderCloseListener {

    @Autowired
    private OrderService orderService;

    @RabbitHandler
    public void listener(OrderEntity orderEntity, Message message, Channel channel) throws IOException {
        System.out.println("收到过期的订单信息，准备关闭订单" + orderEntity.getOrderSn());
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            orderService.closeOrder(orderEntity);
            channel.basicAck(deliveryTag,false);
        } catch (Exception e){
            channel.basicReject(deliveryTag,true);
        }

    }
}
```

##### 3) 关闭订单

* 由于要保证幂等性，因此要查询最新的订单状态判断是否需要关单
* 关闭订单后也需要解锁库存，因此发送消息进行库存、会员服务对应的解锁

```java
@Override
public void closeOrder(OrderEntity orderEntity) {
    //因为消息发送过来的订单已经是很久前的了，中间可能被改动，因此要查询最新的订单
    OrderEntity newOrderEntity = this.getById(orderEntity.getId());
    //如果订单还处于新创建的状态，说明超时未支付，进行关单
    if (newOrderEntity.getStatus() == OrderStatusEnum.CREATE_NEW.getCode()) {
        OrderEntity updateOrder = new OrderEntity();
        updateOrder.setId(newOrderEntity.getId());
        updateOrder.setStatus(OrderStatusEnum.CANCLED.getCode());
        this.updateById(updateOrder);

        //关单后发送消息通知其他服务进行关单相关的操作，如解锁库存
        OrderTo orderTo = new OrderTo();
        BeanUtils.copyProperties(newOrderEntity,orderTo);
        rabbitTemplate.convertAndSend("order-event-exchange", "order.release.other",orderTo);
    }
}
```

##### 4) 解锁库存

```java
@Slf4j
@Component
@RabbitListener(queues = {"stock.release.stock.queue"})
public class StockReleaseListener {

    @Autowired
    private WareSkuService wareSkuService;

    @RabbitHandler
    public void handleStockLockedRelease(StockLockedTo stockLockedTo, Message message, Channel channel) throws IOException {
        log.info("************************收到库存解锁的消息********************************");
        try {
            wareSkuService.unlock(stockLockedTo);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),true);
        }
    }

    @RabbitHandler
    public void handleStockLockedRelease(OrderTo orderTo, Message message, Channel channel) throws IOException {
        log.info("************************从订单模块收到库存解锁的消息********************************");
        try {
            wareSkuService.unlock(orderTo);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            channel.basicReject(message.getMessageProperties().getDeliveryTag(),true);
        }
    }
}
```

```java
@Override
public void unlock(OrderTo orderTo) {
    //为防止重复解锁，需要重新查询工作单
    String orderSn = orderTo.getOrderSn();
    WareOrderTaskEntity taskEntity = wareOrderTaskService.getBaseMapper().selectOne((new QueryWrapper<WareOrderTaskEntity>().eq("order_sn", orderSn)));
    //查询出当前订单相关的且处于锁定状态的工作单详情
    List<WareOrderTaskDetailEntity> lockDetails = wareOrderTaskDetailService.list(new QueryWrapper<WareOrderTaskDetailEntity>().eq("task_id", taskEntity.getId()).eq("lock_status", WareTaskStatusEnum.Locked.getCode()));
    for (WareOrderTaskDetailEntity lockDetail : lockDetails) {
        unlockStock(lockDetail.getSkuId(),lockDetail.getSkuNum(),lockDetail.getWareId(),lockDetail.getId());
    }
}
```

### 



#### （7）自动关单，和自动解锁库存测试

##### 1）订单的自动关闭（长时间未支付的订单自动关闭）

创建订单

![在这里插入图片描述](https://img-blog.csdnimg.cn/20210615225410399.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MjIzMjkzMQ==,size_16,color_FFFFFF,t_70#pic_center)


会发送订单创建的消息到订单延时队列`order.delay.queue`

![在这里插入图片描述](https://img-blog.csdnimg.cn/20210615225503827.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MjIzMjkzMQ==,size_16,color_FFFFFF,t_70#pic_center)


一分钟后（ttl设置为60s)成为死信，会以`order.release.order`的路由键经交换机转发至队列`order.release.order.queue`

![在这里插入图片描述](https://img-blog.csdnimg.cn/20210615225531260.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MjIzMjkzMQ==,size_16,color_FFFFFF,t_70#pic_center)


同时发送消息给库存服务，告知订单已经关闭，让库存服务解锁库存

库存解锁队列同时绑定了订单的交换机和库存的交换机

![在这里插入图片描述](https://img-blog.csdnimg.cn/20210615225556187.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MjIzMjkzMQ==,size_16,color_FFFFFF,t_70#pic_center)


[外链图片转存失败,源站可能有防盗链机制,建议将图片保存下来直接上传(img-GpEnLSPN-1623768025327)(C:\Users\admin\Documents\分布式事务.assets\image-20210615180210971.png)]

##### 2）库存的自动解锁（订单创建失败）

订单服务调用锁定库存，会发送锁定库存的消息到库存锁定的延时队列



两分钟后（ttl设置为120s)成为死信，会以`stock.release`的路由键经交换机转发至队列`stock.release.stock.queue`

[外链图片转存失败,源站可能有防盗链机制,建议将图片保存下来直接上传(img-9swgm2hT-1623768025328)(C:\Users\admin\Documents\分布式事务.assets\image-20210615181517194.png)]

解锁库存，解锁库存时判断订单是否存在（即订单出错后回滚，订单不存在），如果订单存在，再判断订单是否关闭（只有关闭的订单才能自动解锁库存）

##### 3）库存的自动解锁（订单超时未支付而关闭）

订单服务自动关闭订单时，会通过`order.release.other`的路由键发送到`order-event-exchange`，库存服务的`stock.release.stock.queue`同时通过`order.release.other`路由键绑定了`order-event-exchange`



因此`stock.release.stock.queue`既能收到库存锁定的消息，也能收到订单关闭的消息。
![在这里插入图片描述](https://img-blog.csdnimg.cn/20210615225627349.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MjIzMjkzMQ==,size_16,color_FFFFFF,t_70#pic_center)



监听到订单关闭的消息，使用订单关闭后解锁库存的逻辑；

监听到库存解锁的消息，使用订单失败解锁库存的逻辑
