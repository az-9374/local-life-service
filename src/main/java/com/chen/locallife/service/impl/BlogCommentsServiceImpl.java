package com.chen.locallife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen.locallife.entity.BlogComments;
import com.chen.locallife.mapper.BlogCommentsMapper;
import com.chen.locallife.service.IBlogCommentsService;
import org.springframework.stereotype.Service;

/**
 *  服务实现类
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
