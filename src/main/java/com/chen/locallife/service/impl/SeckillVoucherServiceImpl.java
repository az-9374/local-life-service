package com.chen.locallife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen.locallife.entity.SeckillVoucher;
import com.chen.locallife.mapper.SeckillVoucherMapper;
import com.chen.locallife.service.ISeckillVoucherService;
import org.springframework.stereotype.Service;

/**
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 */
//这个类 这么空何意味
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

}
