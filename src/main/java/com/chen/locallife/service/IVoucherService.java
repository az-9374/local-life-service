package com.chen.locallife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chen.locallife.dto.Result;
import com.chen.locallife.entity.Voucher;

/**
 *  服务类
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
