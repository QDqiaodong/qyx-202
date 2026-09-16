# 船舶码头船员休息室配套电器房间占用关联登记系统

## 项目简介

本系统用于港口后勤管理休息室冰箱、热水器等电器，核心功能为**房间-船舶双维度关联绑定**。电器档案同时绑定休息室编号与对应停靠船舶；船舶换班、休息室重新分配时同步修改两层关联关系；支持按船舶编号汇总该船配套全部休息室电器；支持按休息室查看当前绑定船舶信息。

## 技术栈

- **前端**: Vue3 + Vite + TypeScript + Element Plus
- **后端**: Spring Boot 3.3 + JDK 17 + Maven + JPA
- **数据库**: MySQL 8.0
- **缓存**: Redis 7.2 (SortedSet缓存电器功率参数)
- **容器化**: Docker + Docker Compose

## 端口配置

| 服务 | 端口 |
|------|------|
| 前端 | 8122 |
| 后端 | 8132 |
| MySQL | 3348 |
| Redis | 6421 |

## 核心功能模块

1. **休息室电器基础建档**: 设备编号、功率、电器类型
2. **船舶+休息室双重绑定**: 同步登记设备所属船舶与休息室
3. **双维度关联关系同步调整**: 留存变更记录
4. **按船舶/休息室双向反向汇总配套电器清单**

## 项目结构

```
qyx-202/
├── backend/                    # Spring Boot后端
│   ├── src/main/java/com/example/shiproom/
│   │   ├── controller/         # REST API控制层
│   │   ├── service/            # 业务逻辑层
│   │   ├── repository/         # 数据访问层
│   │   ├── entity/             # JPA实体类
│   │   ├── dto/                # 数据传输对象
│   │   └── config/             # 配置类
│   ├── src/main/resources/     # 资源文件
│   ├── pom.xml                 # Maven配置
│   ├── Dockerfile              # 生产环境Docker镜像
│   ├── Dockerfile.dev          # 开发环境Docker镜像
│   └── maven_settings.xml      # 网易Maven镜像配置
├── frontend/                   # Vue3前端
│   ├── src/
│   │   ├── views/              # 页面组件
│   │   ├── api/                # API接口定义
│   │   ├── router/             # 路由配置
│   │   ├── App.vue             # 主应用组件
│   │   └── main.ts             # 入口文件
│   ├── vite.config.ts          # Vite配置
│   ├── Dockerfile              # 生产环境Docker镜像
│   ├── Dockerfile.dev          # 开发环境Docker镜像
│   └── nginx.conf              # Nginx配置
├── docker-compose.yml          # 生产环境Docker Compose
├── docker-compose.dev.yml      # 开发环境Docker Compose
└── .env                        # 环境变量配置
```

## 启动方式

### 开发环境

```bash
docker-compose -f docker-compose.dev.yml up -d
```

### 生产环境

```bash
docker-compose up -d
```

## 访问地址

- **前端**: http://localhost:8122
- **后端API**: http://localhost:8132/api

## 数据库表结构

### electric_appliance (电器表)
- id, device_code, device_name, power, appliance_type, status, room_id, ship_id, create_time, update_time

### lounge_room (休息室表)
- id, room_code, room_name, floor, capacity, power_capacity, status, create_time, update_time
- `power_capacity`：每间房的用电承载（千瓦），可在房间档案单独调整；老房间启动时回填默认 5.00 kW

### ship (船舶表)
- id, ship_code, ship_name, ship_type, dock_code, status, create_time, update_time

### room_ship_relation (房间船舶关联表)
- id, room_id, ship_id, relation_type, status, create_time, update_time

### relation_change_log (关联变更记录表)
- id, change_type, device_id, device_code, room_id, room_code, ship_id, ship_code, old_room_id, old_room_code, old_ship_id, old_ship_code, operator, remark, change_time