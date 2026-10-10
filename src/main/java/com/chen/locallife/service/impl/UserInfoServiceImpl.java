package com.chen.locallife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen.locallife.entity.UserInfo;
import com.chen.locallife.mapper.UserInfoMapper;
import com.chen.locallife.service.IUserInfoService;
import org.springframework.stereotype.Service;

/**
 *  服务实现类
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
