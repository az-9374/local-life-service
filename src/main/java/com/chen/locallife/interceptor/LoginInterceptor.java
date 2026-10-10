package com.chen.locallife.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chen.locallife.dto.UserDTO;
import com.chen.locallife.entity.User;
import com.chen.locallife.utils.RedisConstants;
import com.chen.locallife.utils.UserHolder;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取用户
        if (UserHolder.getUser() == null) {
            //不存在用户 拦截
            response.setStatus(401);
            return false;
        }
        //存在用户放行
        return true;
    }


}
