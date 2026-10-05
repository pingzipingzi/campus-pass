package com.example.campuspass.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.campuspass.entity.Request;
import com.example.campuspass.mapper.RequestMapper;
import com.example.campuspass.service.IRequestService;
import org.springframework.stereotype.Service;

@Service
public class RequestServiceImpl extends ServiceImpl<RequestMapper, Request> implements IRequestService {
}
