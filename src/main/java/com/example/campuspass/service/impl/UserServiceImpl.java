package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.User;
import com.example.campuspass.exception.BusinessException;
import com.example.campuspass.mapper.UserMapper;
import com.example.campuspass.service.IUserService;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.UserVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final Integer USER_PAGE_SIZE = 10;

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
        User user = lambdaQuery().eq(User::getUserId, userId).select(User::getRole).one();
        if (Integer.valueOf(1).equals(user.getRole())) {
            throw new BusinessException(40000, "不能禁用管理员权限用户", HttpStatus.BAD_REQUEST);
        }
        boolean success = lambdaUpdate().eq(User::getUserId, userId).set(User::getStatus, 1).update();
        if (!success) {
            throw new BusinessException(40000, "数据更新失败", HttpStatus.BAD_REQUEST);
        }
        return Result.ok();

    }

    @Override
    public Result<Void> enableUserForAdmin(Long userId) {
        checkIfAdmin();
        boolean success = lambdaUpdate().eq(User::getUserId, userId).set(User::getStatus, 0).update();
        if (!success) {
            throw new BusinessException(40000, "数据更新失败", HttpStatus.BAD_REQUEST);
        }
        return Result.ok();
    }

    public static void checkIfAdmin() {
        User user = USER_THREAD_LOCAL.get();
        if (user == null ||! Integer.valueOf(1).equals(user.getRole())) {
            throw new BusinessException(40000, "用户不具备管理员权限", HttpStatus.BAD_REQUEST);
        }
    }
}
