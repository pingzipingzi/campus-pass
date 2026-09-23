package com.example.campuspass.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResultVO<T> {
    private Long total;
    private Long current;//当前页码
    private Long size;
    private Long pages;
    private List<T> pageResult;
}
