# 船舶码头船员休息室配套电器房间占用关联登记系统

## 简介

本系统是一个港口后勤管理系统，主要管理休息室的冰箱、热水器等电器设备。核心功能为**房间-船舶双维度关联绑定**。

## 技术栈

- **前端**: Vue3 + Vite + TypeScript + Element Plus
- **后端**: Spring Boot 3.3 + JDK 17 + Maven + JPA + Redis
- **数据库**: MySQL 8.0
- **缓存**: Redis 7.2
- **容器化**: Docker + Docker Compose

## 核心功能

1. **休息室电器基础建档**：设备编号、功率、电器类型管理
2. **船舶+休息室双重绑定**：同步登记设备所属船舶与休息室
3. **双维度关联关系同步调整**：船舶换班、休息室重新分配时同步修改两层关联关系，留存变更记录
4. **双向反向汇总**：按船舶/休息室查询配套电器清单
5. **休息室钥匙领还台账**：钥匙档案绑定所属休息室；一次领取在同一事务内同时落下「钥匙跟哪间房」的占用与「房间今晚停靠哪条船」的快照，并写入同批次领还流水；按船看未还钥匙、按房看持匙人、领还流水三处同源对账；房间已换船后按旧船领取整单退回（占用与快照都不留半写），并明示卡住的钥匙与房间；归还同事务核销占用并回写同批次流水

## 环境要求

- Docker 20.10+
- Docker Compose 2.0+

## 快速开始

### 生产环境

```bash
docker compose up -d --build
```

### 开发环境

```bash
docker compose -f docker-compose.dev.yml up -d --build
```

### 一键启动

```bash
./start.sh
```

## 访问地址

- **前端**: http://localhost:8122
- **后端API**: http://localhost:8132/api

## 端口配置

| 服务 | 端口 |
|------|------|
| 前端 | 8122 |
| 后端 | 8132 |
| MySQL | 3348 |
| Redis | 6420 |

## 配置文件

环境变量配置文件：`.env`

```env
FRONTEND_PORT=8122
BACKEND_PORT=8132
MYSQL_PORT=3348
REDIS_PORT=6420
MYSQL_ROOT_PASSWORD=root
MYSQL_DATABASE=example_db
MYSQL_USER=admin
MYSQL_PASSWORD=password
```

## 项目结构

```
├── backend/              # Spring Boot后端
├── frontend/             # Vue3前端
├── docker-compose.yml    # 生产环境Docker Compose
├── docker-compose.dev.yml # 开发环境Docker Compose
├── .env                  # 环境变量配置
├── .gitignore            # Git忽略配置
├── .dockerignore         # Docker忽略配置
├── start.sh              # 启动脚本
└── README.md             # 项目说明
```

## 开发

### 后端开发

```bash
cd backend
mvn spring-boot:run
```

### 前端开发

```bash
cd frontend
npm install
npm run dev
```

## 构建

### 后端构建

```bash
cd backend
mvn clean package -DskipTests
```

### 前端构建

```bash
cd frontend
npm ci
npm run build
```

## 镜像配置

- **前端**: 使用淘宝npm镜像 (`https://registry.npmmirror.com/`)
- **后端**: 使用阿里云Maven镜像 (`https://maven.aliyun.com/repository/public/`)