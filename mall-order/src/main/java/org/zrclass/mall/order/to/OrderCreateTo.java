package org.zrclass.mall.order.to;

import lombok.Data;
import org.zrclass.mall.order.entity.OrderEntity;
import org.zrclass.mall.order.entity.OrderItemEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>Title: OrderCreateTo</p>
 * Description：
 * date：2020/7/1 23:51
 */
@Data
public class OrderCreateTo {

	private OrderEntity order;

	private List<OrderItemEntity> orderItems;

	/**
	 * 订单计算的应付价格
	 */
	private BigDecimal payPrice;

	/**
	 * 运费
	 */
	private BigDecimal fare;
}
