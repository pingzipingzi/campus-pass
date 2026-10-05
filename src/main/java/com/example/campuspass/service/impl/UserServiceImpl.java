package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.campuspass.dto.Result;
import com.example.campuspass.dto.UserDTO;
import com.example.campuspass.entity.User;
import com.example.campuspass.mapper.UserMapper;
import com.example.campuspass.service.IUserService;
import com.example.campuspass.utils.RegexUtils;
import com.example.campuspass.utils.lua_execute.ExecuteSaveHashMapLua;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.UserVO;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.example.campuspass.exception.BusinessException.error;
import static com.example.campuspass.utils.RedisConstants.USER_CACHE_KEY_PREFIX;
import static com.example.campuspass.utils.RedisConstants.USER_CACHE_TTL_SECONDS;
import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final Integer USER_PAGE_SIZE = 10;

    @Autowired
    private ExecuteSaveHashMapLua executeSaveHashMapLua;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result<PageResultVO<UserVO>> PageAllUsersForAdmin(Integer pages) {
        checkIfAdmin();
        Page<User> page = new Page<>(pages, USER_PAGE_SIZE);
        Page<User> result = lambdaQuery().orderByDesc(User::getUserId).page(page);
        List<UserVO> list = page.getRecords().stream()
                .map(elem -> BeanUtil.copyProperties(elem, UserVO.class)).toList();
        return Result.ok(new PageResultVO<>
                (result.getTotal(), result.getCurrent(), result.getSize(), result.getPages(), list));
    }

    @Override
    public Result<Void> disableUserForAdmin(Long userId) {
        checkIfAdmin();
        User user = lambdaQuery().eq(User::getUserId, userId).one();
        if (user == null) {
            error("用户不存在");
        }
        if (Integer.valueOf(1).equals(user.getRole())) {
            error("不能管理管理员权限用户");
        }
        boolean success = lambdaUpdate().eq(User::getUserId, userId).set(User::getStatus, 1).update();
        if (!success) {
            error("数据更新失败");
        }
        refreshCacheUser( user,1);

        return Result.ok();

    }



    @Override
    public Result<Void> enableUserForAdmin(Long userId) {
        checkIfAdmin();
        User user = lambdaQuery().eq(User::getUserId, userId).one();
        if (user == null) {
            error("用户不存在");
        }
        if (Integer.valueOf(1).equals(user.getRole())) {
            error("不能管理管理员权限用户");
        }
        boolean success = lambdaUpdate().eq(User::getUserId, userId).set(User::getStatus, 0).update();
        if (!success) {
            error("数据更新失败");
        }
        refreshCacheUser(user, 0);
        return Result.ok();
    }

    @Override
    public Result<UserVO> getMyProfile() {
        Long userId = getUserId();
        String key=USER_CACHE_KEY_PREFIX+userId;

        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        if (!map.isEmpty()) {
            UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
            UserVO userVO = BeanUtil.copyProperties(userDTO, UserVO.class);
            return Result.ok(userVO);
        }

        User user = lambdaQuery().eq(User::getUserId, userId).one();
        if (user == null) {
            error("用户不存在");
        }
        UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
        return Result.ok(userVO);
    }



    @Override
    public Result<Void> updateMyNickName(String name) {
        RegexUtils.validateNickName(name);
        Long userId = getUserId();
        try {
            boolean update = lambdaUpdate().eq(User::getUserId, userId).set(User::getUserName, name).update();
            if(!update){
                error("更新数据失败");
            }
        } catch (DuplicateKeyException e) {
            error("用户名重复,请换一个试试呢");
        }
        stringRedisTemplate.delete(USER_CACHE_KEY_PREFIX + userId);
        return Result.ok();
    }

    public static @NonNull Long getUserId() {
        UserDTO userDTO = USER_THREAD_LOCAL.get();
        if (userDTO == null) {
            error("用户未登录");
        }
        return userDTO.getUserId();
    }
    public void refreshCacheUser( User user,Integer status) {
        user.setStatus(status);
        String key = USER_CACHE_KEY_PREFIX + user.getUserId();
        Map<String, Object> map = BeanUtil.beanToMap(BeanUtil.copyProperties(user, UserDTO.class));

        executeSaveHashMapLua.executeScript(map,key,USER_CACHE_TTL_SECONDS);
    }

    public static void checkIfAdmin() {
        User user = BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if (user == null ||! Integer.valueOf(1).equals(user.getRole())) {
            error("用户不具备管理员权限");
        }
    }
}
