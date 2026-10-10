package com.chen.locallife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen.locallife.dto.Result;
import com.chen.locallife.entity.SeckillVoucher;
import com.chen.locallife.entity.Voucher;
import com.chen.locallife.mapper.VoucherMapper;
import com.chen.locallife.service.ISeckillVoucherService;
import com.chen.locallife.service.IVoucherService;
import com.chen.locallife.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static com.chen.locallife.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 *  服务实现类
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;//秒杀卷相关
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {//这个商铺有哪些优惠卷
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);//和baseMapper的区别是，这个的sql是手动写的 ，为啥不用Mapper注入呢
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);//
        //保存秒杀库存的到redis（voucherId,库存）
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY+voucher.getId(),voucher.getStock().toString());
        //key："seckill:stock:voucherId" value：stock(库存)
    }
}
