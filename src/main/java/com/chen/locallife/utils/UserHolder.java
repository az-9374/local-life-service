package com.chen.locallife.utils;

import com.chen.locallife.dto.UserDTO;


public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();//可以拿到当前线程的相关信息？

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
