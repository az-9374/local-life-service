package com.chen.locallife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chen.locallife.dto.Result;
import com.chen.locallife.entity.VoucherOrder;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

/**
 * ivoucher订单服务
 * 服务类
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠券
     *
     * @param voucherId 券id
     * @return {@link Result}
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 得到结果
     *
     * @param voucherId 券id
     * @return {@link Result}
     */
    Result getResult(Long voucherId);

    /**
     * 创建优惠券订单
     *
     * @param voucherOrder 券订单
     */
    @NotNull
    @Transactional(rollbackFor = Exception.class)
    void createVoucherOrder(VoucherOrder voucherOrder);


    Result createOrder(Long voucherId);
}
