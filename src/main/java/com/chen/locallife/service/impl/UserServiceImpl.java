package com.chen.locallife.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen.locallife.dto.LoginFormDTO;
import com.chen.locallife.dto.Result;
import com.chen.locallife.dto.UserDTO;
import com.chen.locallife.entity.User;
import com.chen.locallife.mapper.UserMapper;
import com.chen.locallife.service.IUserService;
import com.chen.locallife.utils.RedisConstants;
import com.chen.locallife.utils.RegexUtils;
import com.chen.locallife.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.chen.locallife.utils.RedisConstants.*;
import static com.chen.locallife.utils.SystemConstants.*;


/**
 * 服务实现类
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;//redis客户端
    //验证码发送
    @Override
    public Result sendCode(String phone, HttpSession session) {//发送手机验证码，会话相关我不懂
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //手机号不符合
            return Result.fail("手机号格式错误");
        }
        //手机号符合,生成验证码
        String code = RandomUtil.randomNumbers(6);//随机6位数字
        /*//保存验证码到session
        session.setAttribute("code", code);*///为什么会考虑到保存验证码到会话呢
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //这是key--code外加时效+还有什么？
        //发送验证码
        log.debug("发送验证码成功，验证码：{}", code);
        //返回ok
        return Result.ok();//那么这个功能的下一步呢，在redis保存了验证码？哪里发送了?而且对应的登录校验功能在哪里呢
    }
    //登录校验
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //手机号不符合
            return Result.fail("手机号格式错误");
        }
        //从redis中获取验证码 校验验证码
        /*  Object cacheCode = session.getAttribute("code");*/
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {//比较验证码是否和缓存里的一致
            //不一致 报错
            return Result.fail("验证码错误");
        }
        //一致 根据手机号查询用户
        User user = baseMapper
                .selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, phone));
        //判断用户是否存在
        if (user == null) {
            //不存在 创建新用户
            user = createUserWithPhone(phone);//最下面有
        }
        /*//保存用户信息到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));*/
        //生成token
        String token = UUID.randomUUID().toString(true);//知道意思但不理解写法
        //userDTO转map
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);//？BeanUtil啥东西
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>()
                , CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor(
                                (name, value) -> value.toString()
                        ));
        //保存用户信息到redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, map);
        //设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    //签到功能
    @Override
    public Result sign() {
        //获取当前登陆用户
        Long id = UserHolder.getUser().getId();//怎么就当前了，多用户时怎么区分的
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String yyyyMM = now.format(DateTimeFormatter.ofPattern("yyyy:MM:"));//不完全记得
        String key = USER_SIGN_KEY +yyyyMM+ id;//统一前缀+日期+用户id
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();//我倒不知道这个用法
        //写了redis
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);

        return Result.ok();
    }

    //
    @Override
    public Result signCount() {
        //获取当前登陆用户
        Long id = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String yyyyMM = now.format(DateTimeFormatter.ofPattern("yyyy:MM:"));
        String key = USER_SIGN_KEY +yyyyMM+ id;
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //获取截至本月今天的所有签到记录
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key
                , BitFieldSubCommands
                        .create()
                        .get(BitFieldSubCommands.BitFieldType
                                .unsigned(dayOfMonth))//从第 0 位开始取
                        .valueAt(0)//从第 0 位开始取
        );//bitField 是 Redis 的 BitField 命令，可以一次取多个 bit，并转成整数，这里只取了一个所以get(0)【而且是十进制的】
        if (result==null||result.isEmpty()){
            return Result.ok(0);
        }
        Long num = result.get(0);//
        if (num==null||num==0){
            return Result.ok(0);
        }
        //转二进制字符串
        String binaryString = Long.toBinaryString(num);//转二进制字符串
        //计算连续签到天数
        int count=0;
        for (int i = binaryString.length()-1; i >=0; i--) {
            if (binaryString.charAt(i)=='1'){
                count++;
            }
            else {
                break;
            }
        }
        //返回
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        //生成随机昵称
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));//默认前缀+随机字符是吧
        baseMapper.insert(user);//存入数据库，这个baseMapper不用初始化吗
        return user;
    }
}
