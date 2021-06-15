package org.zrclass.mall.order.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.redisson.RedissonLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.zrclass.common.enume.OrderStatusEnum;
import org.zrclass.common.exception.NotStockException;
import org.zrclass.common.to.mq.OrderTo;
import org.zrclass.common.utils.R;
import org.zrclass.common.vo.MemberRsepVo;
import org.zrclass.mall.order.dao.OrderDao;
import org.zrclass.mall.order.entity.OrderEntity;
import org.zrclass.mall.order.entity.OrderItemEntity;
import org.zrclass.mall.order.feign.WmsFeignService;
import org.zrclass.mall.order.service.OrderItemService;
import org.zrclass.mall.order.service.OrderService;
import org.zrclass.mall.order.to.OrderCreateTo;
import org.zrclass.mall.order.vo.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author zhourui 20114535
 * @version 1.0
 * @date 2021/6/8 10:39
 */
@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private WmsFeignService wmsFeignService;

    @Autowired
    private OrderItemService orderItemService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${myRabbitmq.MQConfig.eventExchange}")
    private String eventExchange;

    @Value("${myRabbitmq.MQConfig.createOrder}")
    private String createOrder;

    @Value("${myRabbitmq.MQConfig.ReleaseOtherKey}")
    private String ReleaseOtherKey;

    @Override
    public void closeOrder(OrderEntity entity) {
        log.info("\n收到过期的订单信息--准关闭订单:" + entity.getOrderSn());
        // 查询这个订单的最新状态
        OrderEntity orderEntity = this.getById(entity.getId());
        if(orderEntity.getStatus() == OrderStatusEnum.CREATE_NEW.getCode()){
            OrderEntity update = new OrderEntity();
            update.setId(entity.getId());
            update.setStatus(OrderStatusEnum.CANCLED.getCode());
            this.updateById(update);
            // 发送给MQ告诉它有一个订单被自动关闭了
            OrderTo orderTo = new OrderTo();
            BeanUtils.copyProperties(orderEntity, orderTo);
            try {
                // 保证消息 100% 发出去 每一个消息在数据库保存详细信息
                // 定期扫描数据库 将失败的消息在发送一遍
                rabbitTemplate.convertAndSend(eventExchange, ReleaseOtherKey , orderTo);
            } catch (AmqpException e) {
                // 将没发送成功的消息进行重试发送.
            }
        }
    }
    @Override
    public OrderEntity getOrderByOrderSn(String orderSn) {
        return this.getOne(new QueryWrapper<OrderEntity>().eq("order_sn", orderSn));
    }

    @Override
//    @GlobalTransactional
    @Transactional
    public SubmitOrderResponseVo submitOrder(OrderSubmitVo vo) {
        SubmitOrderResponseVo submitVo = new SubmitOrderResponseVo();
        submitVo.setCode(0);
        //获取当前登录会员，本应该是从redis或者session中获取登录保存的用户信息，这里直接写死
        MemberRsepVo memberRsepVo = getLoginMember();

        //保证幂等性，防止用户多次点击
        RLock lock = redissonClient.getLock("order-submit-lock-" + memberRsepVo.getId());
        try {
            if (lock.tryLock()) {
                //获取锁成功
                // 1 .创建订单等信息
                OrderCreateTo order = createOrder(vo);
                //2. 验价,这里测试直接是true
                BigDecimal payAmount = order.getOrder().getPayAmount();
                vo.setPayPrice(new BigDecimal("5011"));
                BigDecimal voPayPrice = vo.getPayPrice();
                if (Math.abs(payAmount.subtract(voPayPrice).doubleValue()) < 0.01 || true) {
                    // 金额对比成功
                    // 3.保存订单
                    saveOrder(order);
                    // 4.库存锁定
                    WareSkuLockVo lockVo = getWareSkuLockVo(order);
                    // 远程锁库存
                    R r = wmsFeignService.orderLockStock(lockVo);
                    if (r.getCode() == 0) {
                        // 库存足够 锁定成功 给MQ发送消息
                        submitVo.setOrderEntity(order.getOrder());
                        rabbitTemplate.convertAndSend(this.eventExchange, this.createOrder, order.getOrder());
//                        int i = 10/0;
                    } else {
                        // 锁定失败
                        String msg = (String) r.get("msg");
                        throw new NotStockException(msg);
                    }
                } else {
                    // 价格验证失败
                    submitVo.setCode(2);
                }
            } else {
                //获取锁失败
                submitVo.setCode(1);
            }
        }finally {
            lock.unlock();
        }

        return submitVo;
    }

    private WareSkuLockVo getWareSkuLockVo(OrderCreateTo order) {
        WareSkuLockVo lockVo = new WareSkuLockVo();
        lockVo.setOrderSn(order.getOrder().getOrderSn());
        List<OrderItemVo> locks = order.getOrderItems().stream().map(item -> {
            OrderItemVo itemVo = new OrderItemVo();
            // 锁定的skuId 这个skuId要锁定的数量
            itemVo.setSkuId(item.getSkuId());
            itemVo.setCount(item.getSkuQuantity());
            itemVo.setTitle(item.getSkuName());
            return itemVo;
        }).collect(Collectors.toList());

        lockVo.setLocks(locks);
        return lockVo;
    }

    /**
     * 保存订单所有数据
     */
    public void saveOrder(OrderCreateTo order) {
        OrderEntity orderEntity = order.getOrder();
        orderEntity.setModifyTime(new Date());
        this.save(orderEntity);

        List<OrderItemEntity> orderItems = order.getOrderItems();
        orderItems = orderItems.stream().map(item -> {
            item.setOrderId(orderEntity.getId());
            item.setSpuName(item.getSpuName());
            item.setOrderSn(order.getOrder().getOrderSn());
            item.setSpuPic("http://xx.jpg");
            return item;
        }).collect(Collectors.toList());
        orderItemService.saveBatch(orderItems);
    }

    private OrderCreateTo createOrder(OrderSubmitVo vo) {
        OrderCreateTo orderCreateTo = new OrderCreateTo();
        // 1. 生成一个订单号
        String orderSn = IdWorker.getTimeId();
        OrderEntity orderEntity = buildOrderSn(orderSn, vo);

        // 2. 获取所有订单项
        List<OrderItemEntity> items = buildOrderItems(orderSn);

        // 3.验价	传入订单 、订单项 计算价格、积分、成长值等相关信息
        computerPrice(orderEntity,items);
        orderCreateTo.setOrder(orderEntity);
        orderCreateTo.setOrderItems(items);
        return orderCreateTo;
    }

    private void computerPrice(OrderEntity orderEntity, List<OrderItemEntity> items) {
        BigDecimal totalPrice = new BigDecimal("0.0");
        // 叠加每一个订单项的金额
        BigDecimal coupon = new BigDecimal("0.0");
        BigDecimal integration = new BigDecimal("0.0");
        BigDecimal promotion = new BigDecimal("0.0");
        BigDecimal gift = new BigDecimal("0.0");
        BigDecimal growth = new BigDecimal("0.0");
        for (OrderItemEntity item : items) {
            // 优惠券的金额
            coupon = coupon.add(item.getCouponAmount());
            // 积分优惠的金额
            integration = integration.add(item.getIntegrationAmount());
            // 打折的金额
            promotion = promotion.add(item.getPromotionAmount());
            BigDecimal realAmount = item.getRealAmount();
            totalPrice = totalPrice.add(realAmount);

            // 购物获取的积分、成长值
            gift.add(new BigDecimal(item.getGiftIntegration().toString()));
            growth.add(new BigDecimal(item.getGiftGrowth().toString()));
        }
        // 1.订单价格相关 总额、应付总额
        orderEntity.setTotalAmount(totalPrice);
        orderEntity.setPayAmount(totalPrice.add(orderEntity.getFreightAmount()));

        orderEntity.setPromotionAmount(promotion);
        orderEntity.setIntegrationAmount(integration);
        orderEntity.setCouponAmount(coupon);

        // 设置积分、成长值
        orderEntity.setIntegration(gift.intValue());
        orderEntity.setGrowth(growth.intValue());

        // 设置订单的删除状态
        orderEntity.setDeleteStatus(OrderStatusEnum.CREATE_NEW.getCode());
    }

    private List<OrderItemEntity> buildOrderItems(String orderSn) {
        // 这里应该远程调用购物车服务，demo测试写死
        List<OrderItemVo> cartItems = getCartItems();
        List<OrderItemEntity> itemEntities = null;
        if (cartItems != null && cartItems.size() > 0) {
            itemEntities = cartItems.stream().map(cartItem -> {
                OrderItemEntity itemEntity = buildOrderItem(cartItem);
                itemEntity.setOrderSn(orderSn);
                return itemEntity;
            }).collect(Collectors.toList());
        }
        return itemEntities;
    }

    private List<OrderItemVo> getCartItems() {
        List<OrderItemVo> cartItems = new ArrayList<>();
        OrderItemVo cartItem = new OrderItemVo();
        cartItem.setPrice(new BigDecimal(4999));
        cartItem.setSkuId(1l);
        cartItem.setTitle("华为mate40 8+256 麒麟9000");
        cartItem.setImage("http:xxxx/xxx.jpg");
        cartItem.setSkuAttr(Arrays.asList("亮黑色","8+256G","充电套装"));
        cartItem.setCount(1);
        cartItems.add(cartItem);
        return cartItems;
    }


    /**
     * 构建某一个订单项
     */
    private OrderItemEntity buildOrderItem(OrderItemVo cartItem) {
        OrderItemEntity itemEntity = new OrderItemEntity();
        // 1.订单信息： 订单号

        // 2.商品spu信息
        Long skuId = cartItem.getSkuId();
        //R r = productFeignService.getSkuInfoBySkuId(skuId);
        //SpuInfoVo spuInfo = r.getData(new TypeReference<SpuInfoVo>() {});
        //这里本来要调用商品服务，获取spu信息，直接写死
        SpuInfoVo spuInfo = getSpuInfo(skuId);
        itemEntity.setSpuId(spuInfo.getId());
        itemEntity.setSpuBrand(spuInfo.getBrandId().toString());
        itemEntity.setSpuName(spuInfo.getSpuName());
        itemEntity.setCategoryId(spuInfo.getCatalogId());
        // 3.商品的sku信息
        itemEntity.setSkuId(cartItem.getSkuId());
        itemEntity.setSkuName(cartItem.getTitle());
        itemEntity.setSkuPic(cartItem.getImage());
        itemEntity.setSkuPrice(cartItem.getPrice());
        // 把一个集合按照指定的字符串进行分割得到一个字符串
        String skuAttr = StringUtils.collectionToDelimitedString(cartItem.getSkuAttr(), ";");
        itemEntity.setSkuAttrsVals(skuAttr);
        itemEntity.setSkuQuantity(cartItem.getCount());
        // 4.积分信息 买的数量越多积分越多 成长值越多
        itemEntity.setGiftGrowth(cartItem.getPrice().multiply(new BigDecimal(cartItem.getCount())).intValue());
        itemEntity.setGiftIntegration(cartItem.getPrice().multiply(new BigDecimal(cartItem.getCount())).intValue());
        // 5.订单项的价格信息 优惠金额
        itemEntity.setPromotionAmount(new BigDecimal("0.0"));
        itemEntity.setCouponAmount(new BigDecimal("0.0"));
        itemEntity.setIntegrationAmount(new BigDecimal("0.0"));
        // 当前订单项的实际金额
        BigDecimal orign = itemEntity.getSkuPrice().multiply(new BigDecimal(itemEntity.getSkuQuantity().toString()));
        // 减去各种优惠的价格
        BigDecimal subtract = orign.subtract(itemEntity.getCouponAmount()).subtract(itemEntity.getPromotionAmount()).subtract(itemEntity.getIntegrationAmount());
        itemEntity.setRealAmount(subtract);
        return itemEntity;
    }

    private SpuInfoVo getSpuInfo(Long skuId) {
        SpuInfoVo spuInfoVo = new SpuInfoVo();
        spuInfoVo.setId(1l);
        spuInfoVo.setBrandId(1l);
        spuInfoVo.setSpuName("华为手机");
        spuInfoVo.setCatalogId(3l);

        return spuInfoVo;
    }


    private OrderEntity buildOrderSn(String orderSn, OrderSubmitVo submitVo) {
        OrderEntity entity = new OrderEntity();
        entity.setOrderSn(orderSn);
        entity.setCreateTime(new Date());
        entity.setCommentTime(new Date());
        entity.setReceiveTime(new Date());
        entity.setDeliveryTime(new Date());
        MemberRsepVo rsepVo = getLoginMember();
        entity.setMemberId(rsepVo.getId());
        entity.setMemberUsername(rsepVo.getUsername());
        entity.setBillReceiverEmail(rsepVo.getEmail());
        // 2. 获取收获地址信息
        //R fare = wmsFeignService.getFare(submitVo.getAddrId());
        //FareVo resp = fare.getData(new TypeReference<FareVo>() {});
        FareVo resp = getFare();
        entity.setFreightAmount(resp.getFare());
        entity.setReceiverCity(resp.getMemberAddressVo().getCity());
        entity.setReceiverDetailAddress(resp.getMemberAddressVo().getDetailAddress());
        entity.setDeleteStatus(OrderStatusEnum.CREATE_NEW.getCode());
        entity.setReceiverPhone(resp.getMemberAddressVo().getPhone());
        entity.setReceiverName(resp.getMemberAddressVo().getName());
        entity.setReceiverPostCode(resp.getMemberAddressVo().getPostCode());
        entity.setReceiverProvince(resp.getMemberAddressVo().getProvince());
        entity.setReceiverRegion(resp.getMemberAddressVo().getRegion());
        // 设置订单状态
        entity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        entity.setAutoConfirmDay(7);
        return entity;
    }

    private FareVo getFare() {
        FareVo fareVo = new FareVo();
        MemberAddressVo addressVo = new MemberAddressVo();
        addressVo.setName("张三");
        addressVo.setPhone("15988888888");
        addressVo.setAreacode("332600");
        addressVo.setDetailAddress("浙江省杭州市萧山区北干街道");
        addressVo.setCity("杭州市");
        addressVo.setProvince("浙江省");
        addressVo.setRegion("萧山区");
        addressVo.setPostCode("332600");
        addressVo.setId(1l);
        fareVo.setMemberAddressVo(addressVo);
        fareVo.setFare(new BigDecimal(12));
        return fareVo;
    }

    private MemberRsepVo getLoginMember() {
        MemberRsepVo memberRsepVo = new MemberRsepVo();
        memberRsepVo.setId(1l);
        memberRsepVo.setUsername("zrclass");
        memberRsepVo.setEmail("1312311306@qq.com");

        return memberRsepVo;
    }
}
