package org.zrclass.mall.ware.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.zrclass.mall.ware.entity.WareOrderTaskEntity;

/**
 * 库存工作单
 * 
 * @author zhourui
 * @email 1312311306@qq.com
 * @date 2021-05-15 18:59:55
 */
@Mapper
public interface WareOrderTaskDao extends BaseMapper<WareOrderTaskEntity> {
	
}
