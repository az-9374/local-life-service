package com.chen.locallife.dto;

import lombok.Data;

@Data
public class LoginFormDTO {//具体啥用，登录的手机+验证码+密码
    private String phone;
    private String code;
    private String password;
}
