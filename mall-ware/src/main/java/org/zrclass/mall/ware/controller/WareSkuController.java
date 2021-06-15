package org.zrclass.mall.ware.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.zrclass.common.exception.BizCodeEnum;
import org.zrclass.common.exception.NotStockException;
import org.zrclass.common.to.es.SkuHasStockVo;
import org.zrclass.common.utils.PageUtils;
import org.zrclass.common.utils.R;
import org.zrclass.mall.ware.entity.WareSkuEntity;
import org.zrclass.mall.ware.service.WareSkuService;
import org.zrclass.mall.ware.vo.WareSkuLockVo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


/**
 * 商品库存
 *
 * @author zhourui
 * @email 1312311306@qq.com
 * @date 2021-05-15 18:59:56
 */
@RestController
@RequestMapping("ware/waresku")
@Slf4j
public class WareSkuController {

    @Autowired
    private WareSkuService wareSkuService;

    @PostMapping("/lock/order")
    public R orderLockStock(@RequestBody WareSkuLockVo vo){
        try {
            wareSkuService.orderLockStock(vo);
            return R.ok();
        } catch (NotStockException e) {
            log.warn("\n" + e.getMessage());
        }
        return R.error(BizCodeEnum.NOT_STOCK_EXCEPTION.getCode(), BizCodeEnum.NOT_STOCK_EXCEPTION.getMsg());
    }




}
