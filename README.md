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
6. **夜班巡检交班**：在现有房间-船舶靠泊关系上补交班；只有本班在岗值班长能交；一次交班必须按楼层从低到高走完该船当时挂靠的全部房间，漏一间或跳层整单退回；接班窗口一过即锁，不能再补勾或改交班；窗口内房间若已改挂别的船，整份交班退回且不留半截勾，并明示卡住的房间、当前停靠船与窗口

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

## 夜班巡检交班（本次新增）

菜单「夜班巡检交班」(`/patrol`)，在现有 `room_ship_relation` 靠泊关系上补交班，不是另起一套勾选。

### 第一件事：值班长按楼层走完全部房间才能交

- 值班人员名册（`patrol_officer`）区分 `LEADER` 值班长 / `MEMBER` 巡检员与在岗状态，**只有本班在岗值班长**能提交交班（后端强制，非值班长返回 403）。
- 一次交班 `POST /api/patrol/handover` 在**单个事务**内：取换班全局行锁（与换班改挂、钥匙领还互斥）→ 锁窗口行与船舶行 → 校验值班长 → 以**锁定后的当前 ACTIVE 靠泊关系**得到该船当时挂靠的全部房间 → 校验走房条目。
- 走房必须**按楼层从低到高**（数字楼层按数值，兼容中文/字符串楼层，同层按房间编号）且**一间不漏**：漏房 `MISSING_ROOM`、跳层 `FLOOR_SKIP`、乱序/重复 `BAD_ENTRY` 一律 409 整单退回。
- 校验通过才同事务写交班流水 `patrol_handover`（值班长、窗口、走房顺序、走房数）与每间房的走房勾 `patrol_check`（同批次、带顺序序号与楼层快照）。

### 第二件事：窗口一过即锁；房间脱挂整份退回不留半截

- 接班窗口 `patrol_window` 起止时间建立后**不可修改**，只能提前关闭；窗口未开始 / 已截止 / 已关闭时提交返回 **423 Locked**，既不能补勾也不能改交班。
- 窗口内若走房条目含**已不再停靠本船**的房间（`ROOM_DETACHED`），整份交班 409 退回，**不写任何 `patrol_check`**；只落一条 `BLOCKED` 退回说明（含卡住房间、楼层、该房间当前停靠船舶与窗口），明示是哪间房、哪个窗口卡住。
- 两人同时给同船同窗口交班：全局行锁串行 + `patrol_handover.handed_unique_key`（`shipId#windowId`）唯一约束兜底，最多一份 `HANDED`，重复者 409。
- 状态持久化在 MySQL，`GET /api/patrol/state?windowId=&shipId=` 关掉页面重开仍还原为：未交 `NOT_HANDED` / 卡在漏房跳层脱挂 `BLOCKED` / 窗口外已锁 `LOCKED` / 已交 `HANDED`；`GET /api/patrol/records` 查交班流水（值班长、窗口、走房顺序、卡住房间）。

## 镜像配置

- **前端**: 使用淘宝npm镜像 (`https://registry.npmmirror.com/`)
- **后端**: 使用阿里云Maven镜像 (`https://maven.aliyun.com/repository/public/`)