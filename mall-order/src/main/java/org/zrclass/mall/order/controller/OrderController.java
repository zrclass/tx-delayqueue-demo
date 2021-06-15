package org.zrclass.mall.order.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.zrclass.common.utils.R;
import org.zrclass.mall.order.entity.OrderEntity;
import org.zrclass.mall.order.service.OrderService;
import org.zrclass.mall.order.vo.OrderSubmitVo;
import org.zrclass.mall.order.vo.SubmitOrderResponseVo;

/**
 * @author zhourui 20114535
 * @version 1.0
 * @date 2021/6/8 10:35
 */
@RestController
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/submitOrder")
    public R submitOrder(@RequestBody OrderSubmitVo submitVo){
        SubmitOrderResponseVo responseVo = orderService.submitOrder(submitVo);
        return R.ok().setData(responseVo);
    }


    @GetMapping("/status/{orderSn}")
    public R getOrderStatus(@PathVariable("orderSn") String orderSn){
        OrderEntity orderEntity = orderService.getOrderByOrderSn(orderSn);

        return R.ok().setData(orderEntity);
    }
}
