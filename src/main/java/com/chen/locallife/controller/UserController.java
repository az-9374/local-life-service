package com.chen.locallife.controller;


import cn.hutool.core.bean.BeanUtil;
import com.chen.locallife.dto.LoginFormDTO;
import com.chen.locallife.dto.Result;
import com.chen.locallife.dto.UserDTO;
import com.chen.locallife.entity.User;
import com.chen.locallife.entity.UserInfo;
import com.chen.locallife.service.IUserInfoService;
import com.chen.locallife.service.IUserService;
import com.chen.locallife.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * 前端控制器
 */
@Slf4j
@RestController//？
@RequestMapping("/user")
public class UserController {

    @Resource//？这啥
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {//？HttpSession session啥
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        //我有点记不清楚@RequestParam("phone")和@RequestBody了这两都是用来接收前端的参数的吧
        // 实现登录功能
        return userService.login(loginForm,session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){//service里没做这个
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me(){
        //  获取当前登录的用户并返回
        return Result.ok(UserHolder.getUser());//这啥写法？
    }

    @GetMapping("/info/{id}")//查询用户详情
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);//为啥
        info.setUpdateTime(null);//为啥
        // 返回
        return Result.ok(info);
    }
    @GetMapping("/{id}")//查询用户返回DTO，这是哪个功能需要的
    public Result queryUserById(@PathVariable("id")Long userId){
        User user = userService.getById(userId);
        if (user==null){
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }
    @PostMapping("/sign")//签到
    public Result sign(){
        return userService.sign();
    }
    @GetMapping("/sign/count")//签到天数统计
    public Result signCount(){
        return userService.signCount();
    }
}
