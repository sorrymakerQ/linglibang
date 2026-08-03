package com.linlibang.service.impl;

import com.linlibang.entity.Category;
import com.linlibang.mapper.CategoryMapper;
import com.linlibang.service.CategoryService;
import com.linlibang.utils.RedisUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private RedisUtils redisUtils;

    private static final String CATEGORY_CACHE_KEY = "category:list";
    private static final long CATEGORY_CACHE_TTL = 60; // 分类数据很少变化，缓存60分钟

    @Override
    public List<Category> listAll() {
        // 先查 Redis 缓存（分类数据很少变化，适合缓存）
        String cached = redisUtils.get(CATEGORY_CACHE_KEY);
        if (cached != null) {
            return cn.hutool.json.JSONUtil.toList(cached, Category.class);
        }
        List<Category> list = categoryMapper.selectAll();
        redisUtils.set(CATEGORY_CACHE_KEY,
                cn.hutool.json.JSONUtil.toJsonStr(list),
                CATEGORY_CACHE_TTL, TimeUnit.MINUTES);
        return list;
    }
}
