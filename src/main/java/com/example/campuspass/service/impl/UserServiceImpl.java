package com.example.campuspass.service.impl;

import com.example.campuspass.exception.ErrorCode;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.campuspass.dto.Result;
import com.example.campuspass.dto.UserDTO;
import com.example.campuspass.entity.Registration;
import com.example.campuspass.entity.User;
import com.example.campuspass.entity.WaitList;
import com.example.campuspass.mapper.UserMapper;
import com.example.campuspass.service.IUserService;
import com.example.campuspass.service.support.UserLock;
import com.example.campuspass.utils.RegexUtils;
import com.example.campuspass.utils.lua_execute.ExecuteSaveHashMapLua;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.example.campuspass.exception.BusinessException.error;
import static com.example.campuspass.utils.RedisConstants.USER_CACHE_KEY_PREFIX;
import static com.example.campuspass.utils.RedisConstants.USER_CACHE_TTL_SECONDS;
import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final Integer USER_PAGE_SIZE = 10;

    @Autowired
    private ExecuteSaveHashMapLua executeSaveHashMapLua;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Lazy
    @Autowired
    private RegistrationServiceImpl registrationService;

    @Lazy
    @Autowired
    private WaitListServiceImpl waitListService;

    @Autowired
    private UserLock userLock;


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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<Void> disableUserForAdmin(Long userId) {
        checkIfAdmin();
        User user = userLock.lock(userId);
        if (user == null) {
            error(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        if (Integer.valueOf(1).equals(user.getRole())) {
            error(ErrorCode.ADMIN_OPERATION_FORBIDDEN, "不能管理管理员权限用户");
        }
        boolean success = lambdaUpdate().eq(User::getUserId, userId).set(User::getStatus, 1).update();
        if (!success) {
            error(ErrorCode.DATA_OPERATION_FAILED, "数据更新失败");
        }

        List<Registration> list = registrationService.lambdaQuery().eq(Registration::getUserId, userId).eq(Registration::getStatus, 0).list();

        List<WaitList> waitLists = waitListService.lambdaQuery().eq(WaitList::getUserId, userId).
                eq(WaitList::getStatus, 0).list();

        refreshCacheUserAfterCommit(user, 1);
        cleanRegistration(list,user);
        cleanWaitList(waitLists, user);
        return Result.ok();

    }

    public void cleanRegistration(List<Registration> list, User user) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Registration registration : list) {
                    try {
                        if (registration == null ) {
                            log.error("用户id{}禁用时处理报名消息异常", user.getUserId());
                            error(ErrorCode.DATA_INCONSISTENT, "报名记录为空");
                        }
                        registrationService.removeRegistration(registration.getActivityId(), user.getUserId());
                    } catch (Exception e) {
                        if (registration != null) {
                            log.error("报名id{}信息清理失败", registration.getRegistrationId(), e);
                        }else{
                            log.error("用户id{}禁用时处理报名消息异常", user.getUserId(), e);
                        }
                    }
                }
            }
        });
    }

    public void cleanWaitList(List<WaitList> list, User user) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override
            public void afterCommit() {
                for(WaitList waitList : list){
                    try {
                        if (waitList == null ) {
                            log.error("用户id{}禁用时处理候补记录异常", user.getUserId());
                            error(ErrorCode.DATA_INCONSISTENT, "候补记录为空");
                        }
                        waitListService.removeWaitList(waitList.getActivityId(), user.getUserId(), 1);
                    }catch (Exception e){
                        if(waitList != null){
                            log.error("候补记录id{}取消失败", waitList.getWaitId(), e);
                        }else{
                            log.error("用户id{}禁用时处理候补记录异常", user.getUserId(), e);
                        }
                    }
                }
            }
        });
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<Void> enableUserForAdmin(Long userId) {
        checkIfAdmin();
        User user = userLock.lock(userId);
        if (user == null) {
            error(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        if (Integer.valueOf(1).equals(user.getRole())) {
            error(ErrorCode.ADMIN_OPERATION_FORBIDDEN, "不能管理管理员权限用户");
        }
        boolean success = lambdaUpdate().eq(User::getUserId, userId).set(User::getStatus, 0).update();
        if (!success) {
            error(ErrorCode.DATA_OPERATION_FAILED, "数据更新失败");
        }
        refreshCacheUserAfterCommit(user, 0);
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
            error(ErrorCode.USER_NOT_FOUND, "用户不存在");
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
                error(ErrorCode.DATA_OPERATION_FAILED, "更新数据失败");
            }
        } catch (DuplicateKeyException e) {
            error(ErrorCode.RESOURCE_CONFLICT, "用户名重复,请换一个试试呢");
        }
        stringRedisTemplate.delete(USER_CACHE_KEY_PREFIX + userId);
        return Result.ok();
    }

    public static @NonNull Long getUserId() {
        UserDTO userDTO = USER_THREAD_LOCAL.get();
        if (userDTO == null) {
            error(ErrorCode.AUTH_REQUIRED, "用户未登录");
        }
        return userDTO.getUserId();
    }
    public void refreshCacheUserAfterCommit(User user, Integer status) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override
            public void afterCommit() {
                try {
                    user.setStatus(status);
                    String key = USER_CACHE_KEY_PREFIX + user.getUserId();
                    Map<String, Object> map = BeanUtil.beanToMap(BeanUtil.copyProperties(user, UserDTO.class));

                    executeSaveHashMapLua.executeScript(map, key,
                            USER_CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(0L, 30L));
                } catch (Exception e) {
                    log.error("用户id{}缓存更新失败", user.getUserId(), e);
                }
            }
        });
    }

    public static void checkIfAdmin() {
        User user = BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if (user == null ||! Integer.valueOf(1).equals(user.getRole())) {
            error(ErrorCode.FORBIDDEN, "用户不具备管理员权限");
        }
    }
}
