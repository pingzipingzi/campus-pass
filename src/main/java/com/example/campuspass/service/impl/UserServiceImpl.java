package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.campuspass.dto.Result;
import com.example.campuspass.dto.UserDTO;
import com.example.campuspass.entity.User;
import com.example.campuspass.exception.BusinessException;
import com.example.campuspass.mapper.UserMapper;
import com.example.campuspass.service.IUserService;
import com.example.campuspass.utils.lua_execute.ExecuteSaveHashMapLua;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public static void error(String message) {
        throw new BusinessException(40000, message, HttpStatus.BAD_REQUEST);
    }

    public void refreshCacheUser( User user,Integer status) {
        user.setStatus(status);
        String key = "user:user-id:" + user.getUserId();
        Map<String, Object> map = BeanUtil.beanToMap(BeanUtil.copyProperties(user, UserDTO.class));

        executeSaveHashMapLua.executeScript(map,key,2*60L);
    }

    public static void checkIfAdmin() {
        User user = BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if (user == null ||! Integer.valueOf(1).equals(user.getRole())) {
            error("用户不具备管理员权限");
        }
    }
}
