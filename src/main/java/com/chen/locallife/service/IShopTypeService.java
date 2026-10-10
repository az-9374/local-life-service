package com.chen.locallife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chen.locallife.dto.Result;
import com.chen.locallife.entity.ShopType;

/**
 *  服务类
 */
public interface IShopTypeService extends IService<ShopType> {

    /**
     * 获取商品类型列表
     *
     * @return {@link Result}
     */
    Result getTypeList();
}
