package com.linlibang.controller;

import com.linlibang.service.CategoryService;
import com.linlibang.dto.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/category")
public class CategoryController {

    @Resource
    private CategoryService categoryService;

    @GetMapping("/list")
    public Result getCategoryList() {
        return Result.ok(categoryService.listAll());
    }
}
