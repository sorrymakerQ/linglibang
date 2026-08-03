package com.linlibang.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.linlibang.dto.Result;
import com.linlibang.utils.OssUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 文件上传控制器
 * 处理图片上传到阿里云 OSS
 */
@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    @Resource
    private OssUtils ossUtils;

    /** 允许的图片格式 */
    private static final Set<String> ALLOWED_IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    ));

    /** 允许的图片扩展名（小写，含点） */
    private static final Set<String> ALLOWED_IMAGE_EXTS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
    ));

    /**
     * 上传单张图片 — 需登录
     *
     * @param file   图片文件
     * @param folder 存储目录：avatar-头像，help-求助图片（可选，默认 help）
     * @return 图片URL
     */
    @SaCheckLogin
    @PostMapping("/image")
    public Result uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "help") String folder) {

        // 1. 校验文件是否为空
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要上传的文件");
        }

        // 2. 校验文件（类型 + 扩展名 + magic bytes）
        String validateErr = validateImage(file);
        if (validateErr != null) {
            return Result.fail(validateErr);
        }

        // 3. 上传到 OSS
        try {
            // 校验文件大小
            if (ossUtils.isFileSizeExceeded(file)) {
                return Result.fail("文件大小超出限制，最大支持 10MB");
            }

            String url;
            if ("avatar".equals(folder)) {
                url = ossUtils.uploadAvatar(file);
            } else {
                url = ossUtils.uploadHelpImage(file);
            }

            Map<String, String> data = new HashMap<>();
            data.put("url", url);
            return Result.ok("上传成功", data);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            String errMsg = root.getMessage() != null ? root.getMessage() : e.toString();
            log.error("OSS 上传失败: {}", errMsg, e);
            return Result.fail("OSS上传失败: " + errMsg);
        }
    }

    /**
     * 批量上传图片（最多9张） — 需登录
     *
     * @param files 图片文件数组
     * @return 图片URL列表
     */
    @SaCheckLogin
    @PostMapping("/images")
    public Result uploadImages(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return Result.fail("请选择要上传的文件");
        }

        if (files.size() > 9) {
            return Result.fail("最多只能上传9张图片");
        }

        List<String> urls = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            // 校验类型 + 扩展名 + magic bytes
            String validateErr = validateImage(file);
            if (validateErr != null) {
                failedNames.add(file.getOriginalFilename());
                continue;
            }

            try {
                String url = ossUtils.uploadHelpImage(file);
                urls.add(url);
            } catch (Exception e) {
                failedNames.add(file.getOriginalFilename());
                log.error("批量上传单文件失败: {}", file.getOriginalFilename(), e);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("urls", urls);
        data.put("failed", failedNames);
        return Result.ok("上传成功，共" + urls.size() + "张" +
                (failedNames.isEmpty() ? "" : "，失败" + failedNames.size() + "张"), data);
    }

    /**
     * 校验图片：非空 + Content-Type 白名单 + 扩展名白名单 + magic bytes 真实图片签名
     *
     * @return 校验失败返回错误提示，成功返回 null
     */
    private String validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "请选择要上传的文件";
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            return "不支持的文件格式，仅支持 JPG、PNG、GIF、WebP、BMP";
        }
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        if (!ALLOWED_IMAGE_EXTS.contains(extension)) {
            return "不支持的文件扩展名";
        }
        if (!isRealImage(file)) {
            return "文件内容不是合法图片";
        }
        return null;
    }

    /**
     * 读取文件头部字节，校验是否为常见图片 magic bytes
     */
    private boolean isRealImage(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] head = new byte[12];
            int read = is.read(head);
            if (read < 3) {
                return false;
            }
            // JPEG: FF D8 FF
            if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
                return true;
            }
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if (read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) {
                return true;
            }
            // GIF: 47 49 46 38 (GIF8)
            if (read >= 4 && head[0] == 0x47 && head[1] == 0x49 && head[2] == 0x46 && head[3] == 0x38) {
                return true;
            }
            // BMP: 42 4D
            if (read >= 2 && head[0] == 0x42 && head[1] == 0x4D) {
                return true;
            }
            // WebP: RIFF....WEBP
            if (read >= 12 && head[0] == 0x52 && head[1] == 0x49 && head[2] == 0x46 && head[3] == 0x46
                    && head[8] == 0x57 && head[9] == 0x45 && head[10] == 0x42 && head[11] == 0x50) {
                return true;
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
