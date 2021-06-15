package org.zrclass.mall.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.zrclass.mall.order.dao.OrderItemDao;
import org.zrclass.mall.order.entity.OrderItemEntity;
import org.zrclass.mall.order.service.OrderItemService;

/**
 * @author zhourui 20114535
 * @version 1.0
 * @date 2021/6/8 11:39
 */
@Service("orderItemService")
public class OrderItemServiceImpl extends ServiceImpl<OrderItemDao, OrderItemEntity> implements OrderItemService {
}
