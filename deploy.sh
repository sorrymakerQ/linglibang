#!/bin/bash
# ==========================================
# 邻里帮 - Ubuntu 22.04 一键部署脚本
# 使用方法：
#   1. 上传整个项目到服务器 /opt/linlibang/
#   2. chmod +x deploy.sh && sudo ./deploy.sh
# ==========================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log() { echo -e "${GREEN}[INFO]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
err() { echo -e "${RED}[ERROR]${NC} $1"; }

# ==========================================
# 0. 检查是否为 root
# ==========================================
if [ "$(id -u)" -ne 0 ]; then
    err "请用 sudo 运行此脚本"
    exit 1
fi

PROJECT_DIR="/opt/linlibang"
BACKEND_DIR="$PROJECT_DIR/backend"
FRONTEND_DIR="$PROJECT_DIR/frontend"

log "=========================================="
log "  邻里帮 一键部署开始"
log "=========================================="

# ==========================================
# 1. 更新系统 + 安装所有依赖
# ==========================================
log "Step 1/8: 安装系统依赖..."

apt update -y

# Java 8
log "安装 Java 8..."
apt install -y openjdk-8-jdk

# MySQL 8.0
log "安装 MySQL 8.0..."
apt install -y mysql-server

# Redis
log "安装 Redis..."
apt install -y redis-server

# RabbitMQ
log "安装 RabbitMQ..."
apt install -y rabbitmq-server

# Nginx
log "安装 Nginx..."
apt install -y nginx

# Maven
log "安装 Maven..."
apt install -y maven

# Node.js 18 (for frontend build)
log "安装 Node.js 18..."
curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
apt install -y nodejs

log "所有依赖安装完成！"
java -version 2>&1 | head -1
node -v
mvn -v 2>&1 | head -1

# ==========================================
# 2. 配置 MySQL
# ==========================================
log "Step 2/8: 配置 MySQL..."

# 启动 MySQL
systemctl start mysql
systemctl enable mysql

# 创建数据库和用户（密码通过环境变量传入，禁止硬编码进脚本）
if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
    err "请通过环境变量设置 MySQL root 密码，例如：sudo MYSQL_ROOT_PASSWORD='你的密码' ./deploy.sh"
    exit 1
fi
MYSQL_DB="linlibang"

mysql -u root <<SQL
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${MYSQL_ROOT_PASSWORD}';
FLUSH PRIVILEGES;
SQL

# 导入建表脚本
mysql -u root -p"${MYSQL_ROOT_PASSWORD}" < "$BACKEND_DIR/src/main/resources/db/schema.sql"

log "MySQL 配置完成！数据库: $MYSQL_DB"

# ==========================================
# 3. 配置 Redis
# ==========================================
log "Step 3/8: 配置 Redis..."

# 限制 Redis 内存（4GB 总内存，给 Redis 256MB）
sed -i 's/^# maxmemory <bytes>/maxmemory 256mb/' /etc/redis/redis.conf
sed -i 's/^# maxmemory-policy noeviction/maxmemory-policy allkeys-lru/' /etc/redis/redis.conf

systemctl restart redis
systemctl enable redis

log "Redis 配置完成！maxmemory: 256MB"

# ==========================================
# 4. 配置 RabbitMQ
# ==========================================
log "Step 4/8: 配置 RabbitMQ..."

systemctl start rabbitmq-server
systemctl enable rabbitmq-server

# 启用管理界面（可选，访问 http://IP:15672）
rabbitmq-plugins enable rabbitmq_management

# 创建独立用户（生产环境建议，密码通过 RABBITMQ_PASSWORD 环境变量传入）
# if [ -z "${RABBITMQ_PASSWORD:-}" ]; then err "请设置 RABBITMQ_PASSWORD"; exit 1; fi
# rabbitmqctl add_user linlibang "${RABBITMQ_PASSWORD}"
# rabbitmqctl set_user_tags linlibang administrator
# rabbitmqctl set_permissions -p / linlibang ".*" ".*" ".*"

log "RabbitMQ 配置完成！"

# ==========================================
# 5. 构建后端
# ==========================================
log "Step 5/8: 构建后端..."

# MySQL 密码通过 systemd 环境变量注入（见下方 linlibang.service），不再写入 application.yml

cd "$BACKEND_DIR"
mvn clean package -DskipTests -q

# 部署 jar 包
mkdir -p /opt/linlibang/app
cp target/linlibang-1.0.0.jar /opt/linlibang/app/linlibang.jar

log "后端构建完成！"

# ==========================================
# 6. 构建前端
# ==========================================
log "Step 6/8: 构建前端..."

cd "$FRONTEND_DIR"
npm install --registry=https://registry.npmmirror.com
npm run build

# 部署静态文件
mkdir -p /var/www/linlibang
cp -r dist/* /var/www/linlibang/

log "前端构建完成！"

# ==========================================
# 7. 配置 Nginx
# ==========================================
log "Step 7/8: 配置 Nginx..."

# 备份默认配置
if [ -f /etc/nginx/sites-enabled/default ]; then
    rm /etc/nginx/sites-enabled/default
fi

# 写入 Nginx 配置
cat > /etc/nginx/sites-available/linlibang <<'NGINX'
server {
    listen 80;
    server_name _;  # 替换为你的域名或 IP

    # 前端静态文件
    root /var/www/linlibang;
    index index.html;

    # 日志
    access_log /var/log/nginx/linlibang_access.log;
    error_log /var/log/nginx/linlibang_error.log;

    # 静态资源缓存
    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # 后端 API 反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 30s;
        proxy_connect_timeout 5s;
    }

    # WebSocket 代理（STOMP）
    location /ws/ {
        proxy_pass http://127.0.0.1:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 86400s;
    }

    # Vue Router history 模式：所有非 API 路径返回 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }
}
NGINX

# 启用站点
ln -sf /etc/nginx/sites-available/linlibang /etc/nginx/sites-enabled/linlibang

# 检查配置
nginx -t

# 重启 Nginx
systemctl restart nginx
systemctl enable nginx

log "Nginx 配置完成！"

# ==========================================
# 8. 创建后端 systemd 服务
# ==========================================
log "Step 8/8: 创建后端服务..."

cat > /etc/systemd/system/linlibang.service <<SYSTEMD
[Unit]
Description=邻里帮 Spring Boot 后端服务
After=network.target mysql.service redis.service rabbitmq-server.service
Wants=mysql.service redis.service rabbitmq-server.service

[Service]
User=root
WorkingDirectory=/opt/linlibang/app
ExecStart=/usr/bin/java \\
    -Xms256m -Xmx1024m \\
    -XX:+UseG1GC \\
    -XX:MaxGCPauseMillis=200 \\
    -Dfile.encoding=UTF-8 \\
    -jar /opt/linlibang/app/linlibang.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

# 环境变量（生产环境请修改！）
Environment="MYSQL_PASSWORD=${MYSQL_ROOT_PASSWORD}"
Environment="SA_TOKEN_JWT_SECRET_KEY=请替换为随机生成的32位密钥"
Environment="ALIYUN_OSS_ACCESS_KEY_ID=你的OSS_Key_ID"
Environment="ALIYUN_OSS_ACCESS_KEY_SECRET=你的OSS_Key_Secret"

[Install]
WantedBy=multi-user.target
SYSTEMD

systemctl daemon-reload
systemctl enable linlibang
systemctl start linlibang

log "=========================================="
log "  部署完成！"
log "=========================================="
log ""
log "访问地址: http://$(curl -s ifconfig.me 2>/dev/null || echo 'YOUR_SERVER_IP')"
log ""
log "常用命令："
log "  systemctl status linlibang   # 查看后端状态"
log "  journalctl -u linlibang -f   # 查看后端日志"
log "  systemctl restart nginx      # 重启 Nginx"
log "  systemctl status mysql       # 查看 MySQL 状态"
log "  rabbitmqctl status           # 查看 RabbitMQ 状态"
log "  redis-cli ping               # 测试 Redis"
log ""
log "⚠️  部署后请做以下安全检查："
log "  1. 修改 MySQL root 密码"
log "  2. 修改 JWT 密钥 (SA_TOKEN_JWT_SECRET_KEY)"
log "  3. 配置腾讯云安全组：开放 80(HTTP) + 22(SSH)，关闭 8080/6379/5672/3306"
log "  4. 替换 OSS AccessKey 为真实值"
log ""
log "RabbitMQ 管理界面: http://IP:15672 (生产环境请创建独立账号并关闭公网访问)"
