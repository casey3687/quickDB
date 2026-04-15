# quickDB

`quickDB` 是一个基于 Java 实现的轻量级关系型数据库内核项目。项目覆盖了事务管理、数据页管理、MVCC 版本控制、B+ 树索引、SQL 解析执行、崩溃恢复以及 C/S 通信等关键能力。

## 项目定位

功能：

- 从磁盘文件组织数据
- 支持事务生命周期管理：`begin / commit / abort`
- 支持两种隔离级别：`read committed` 和 `repeatable read`
- 基于版本可见性规则实现并发读写控制
- 基于日志实现崩溃恢复，包含 `redo` 和 `undo`
- 基于 B+ 树实现索引和范围查询
- 提供服务端与客户端，支持通过 SQL 子集进行交互

## 核心能力

- 事务管理：通过 `.xid` 文件持久化事务状态，维护事务编号与提交/回滚状态
- 数据管理：通过页缓存、日志和数据项封装管理底层数据读写
- 版本管理：通过 `xmin/xmax` 维护记录版本，提供 MVCC 可见性判断
- 索引管理：基于 B+ 树支持等值与范围查找
- 表管理：维护表结构、字段定义、索引信息和数据读写入口
- SQL 执行：支持创建表、删表、插入、删除、更新、查询、显示表结构
- 网络通信：提供服务端监听与客户端命令行交互
- 崩溃恢复：基于日志回放实现已完成事务重做和未完成事务撤销

## 整体架构

项目整体执行链路如下：

`Client -> Transport -> Server -> Executor -> Parser -> TableManager -> VersionManager / DataManager / TransactionManager / Index`

各层职责：
- `client`：命令行输入 SQL，并通过 socket 发给服务端
- `transport`：负责数据包编码、解码和网络收发
- `server`：维护连接、线程池和请求分发
- `parser`：把 SQL 文本解析成结构化语句对象
- `executor`：根据语句类型调度事务与表操作
- `tbm`：负责表结构、字段、索引以及记录增删改查
- `vm`：负责事务可见性、版本跳跃检查和删除标记
- `dm`：负责页、日志、缓存、数据项和恢复逻辑
- `tm`：负责事务状态持久化
- `im`：负责 B+ 树索引读写

## 模块拆解

### 1. TM: Transaction Manager

事务管理模块位于 `src/main/java/com/db/backend/tm`，核心职责是管理事务 ID 和事务状态。

- 使用 `.xid` 文件存储事务状态
- 文件头记录当前事务计数器
- 每个事务用 1 个字节表示状态：
  - `0`：active
  - `1`：committed
  - `2`：aborted
- 对外提供：
  - `begin()`
  - `commit(xid)`
  - `abort(xid)`
  - `isActive(xid)`
  - `isCommitted(xid)`
  - `isAborted(xid)`

事务状态可持久化，数据库重启后仍可据此参与恢复流程。

### 2. DM: Data Manager

数据管理模块位于 `src/main/java/com/db/backend/dm`，负责和磁盘页、日志、缓存打交道。

核心组成包括：

- `page`：页抽象与页内布局
- `pagecache`：页缓存
- `dataItem`：数据项封装，支持修改前后状态维护
- `logger`：日志读写
- `Recover`：崩溃恢复逻辑

数据管理层负责把记录真正落到磁盘文件中，并在修改数据前写日志，为后续恢复提供依据。

### 3. VM: Version Manager

版本管理模块位于 `src/main/java/com/db/backend/vm`，是这个项目里比较核心的一层。

- 为每条记录维护版本信息
- 根据事务隔离级别做可见性判断
- 检测并发更新冲突，必要时自动回滚事务

记录以 `Entry` 的形式组织，版本管理通过 `xmin/xmax` 描述创建者和删除者事务，再结合 `Visibility` 规则判断一条记录在当前事务中是否可见。

### 4. IM: Index Manager

索引模块位于 `src/main/java/com/db/backend/im`，使用 B+ 树组织索引。

- 索引创建
- 单值查找
- 范围查找
- 节点插入与分裂
- 根节点更新

在表查询中，`where` 条件最终会转化为索引范围，再通过 B+ 树查到目标记录 UID。

### 5. TBM: Table Manager

表管理模块位于 `src/main/java/com/db/backend/tbm`，是 SQL 层和底层存储层之间的桥梁。

职责包括：

- 维护表元数据和字段定义
- 管理索引字段
- 执行记录的插入、删除、更新、读取
- 维护表链表和表缓存

当前实现中，`TableManagerImpl` 会在启动时加载已有表；`Table` 负责具体记录编码/解码、索引更新和条件解析；`Field` 负责字段类型转换、打印和索引计算。

### 6. Parser

SQL 解析模块位于 `src/main/java/com/db/backend/parser`。

先做词法切分，再根据命令类型构造语句对象

- `Begin`
- `Commit`
- `Abort`
- `Create`
- `Drop`
- `Select`
- `Insert`
- `Delete`
- `Update`
- `Show`

### 7. Server / Client

- 服务端入口：`src/main/java/com/db/backend/Launcher.java`
- 客户端入口：`src/main/java/com/db/client/Launcher.java`

服务端默认监听 `9999` 端口，使用线程池处理连接。客户端通过命令行读取 SQL，发给服务端并输出执行结果。

### 事务控制

```sql
begin
begin isolation level read committed
begin isolation level repeatable read
commit
abort
```

### 表操作

```sql
create table user id int32, name string, age int32 (index id)
drop table user
show
```

说明：

- 支持字段类型：`int32`、`int64`、`string`
- 建表时必须显式声明索引字段

### 数据操作

```sql
insert into user values 1 alice 20
select * from user where id = 1
select name, age from user where id > 10 and id < 20
update user set age = 21 where id = 1
delete from user where id = 1
```

### 条件表达式

当前 `where` 支持：

- 比较运算：`=`、`>`、`<`
- 逻辑运算：`and`、`or`

## 项目目录

```text
src/main/java/com/db
├── backend
│   ├── dm        # 数据管理、页、日志、恢复
│   ├── im        # B+ 树索引
│   ├── parser    # SQL 解析
│   ├── server    # 服务端和执行器
│   ├── tbm       # 表管理
│   ├── tm        # 事务管理
│   ├── vm        # 版本管理 / MVCC
│   └── Launcher  # 服务端启动入口
├── client        # 客户端命令行入口
├── transport     # 通信协议封装
└── common        # 通用异常与公共定义
```

## 本地运行

### 环境要求

- JDK 8+
- Maven 3+

### 1. 编译项目

```bash
mvn clean test
```

### 2. 创建数据库文件

```bash
mvn exec:java -Dexec.mainClass="com.db.backend.Launcher" -Dexec.args="-create ./tmp/mydb"
```

执行后会生成类似文件：

- `./tmp/mydb.db`
- `./tmp/mydb.log`
- `./tmp/mydb.xid`

### 3. 启动服务端

```bash
mvn exec:java -Dexec.mainClass="com.db.backend.Launcher" -Dexec.args="-open ./tmp/mydb -mem 64MB"
```

默认监听端口：

```text
9999
```

### 4. 启动客户端

```bash
mvn exec:java -Dexec.mainClass="com.db.client.Launcher"
```

## 演示 SQL

下面这一组命令适合用于演示：

```sql
create table user id int32, name string, age int32 (index id)
show
insert into user values 1 alice 20
insert into user values 2 bob 23
select * from user where id = 1
update user set age = 21 where id = 1
select * from user where id > 0 and id < 10
delete from user where id = 2
select * from user where id > 0 and id < 10
drop table user
show
```

事务：

```sql
begin isolation level repeatable read
insert into user values 3 carol 25
commit
```

## 测试情况

项目已包含多组单元测试，主要覆盖：

- `TransactionManagerTest`
  - 多线程事务状态正确性
- `DataManagerTest`
  - 单线程 / 多线程数据操作
  - 恢复流程验证
- `BPlusTreeTest`
  - B+ 树索引插入与查询
- `LockTableTest`
  - 锁表相关并发控制逻辑
- `ExecutorDropTest`
  - SQL `drop table` 执行链路
