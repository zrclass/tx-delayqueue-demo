package org.zrclass.mall.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.zrclass.mall.order.entity.OrderEntity;
import org.zrclass.mall.order.vo.OrderSubmitVo;
import org.zrclass.mall.order.vo.SubmitOrderResponseVo;

/**
 * @author zhourui 20114535
 * @version 1.0
 * @date 2021/6/8 10:39
 */
public interface OrderService extends IService<OrderEntity> {
    /**
     * 下单操作
     */
    SubmitOrderResponseVo submitOrder(OrderSubmitVo submitVo);

    OrderEntity getOrderByOrderSn(String orderSn);


    void closeOrder(OrderEntity entity);
}
