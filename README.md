# MiniRV-Chisel

> 为了防止实验指导书年年更新, 当年的实验指导书仓库 fork [在此]()

本项目为本人的 [2023 年学校 CPU 设计课程](https://hitsz-cslab.gitee.io/cpu/) 的实验, 实现了一个简单的 RV32I CPU, 功能如下:

- 使用 Chisel 开发, 集成了 [学校提供的 verilator 测试框架](https://gitee.com/hitsz-cslab/cdp-tests)
- 五级流水线: IF, ID, EX, MEM, WB
- 简单的分支预测 (2-bit) 与前递
- 支持 rv32i 除 ecall/ebreak 之外的所有指令
  - sub-word load/store
- 具有适合于本校 FPGA 板子的 SoC 设备
  - 本校板子: XC7A100TFGG484-1
- 通过答辩的形式展示了 Chisel 的优势与本项目的设计
  - [Slides](pre/新时代%20RTL%20级硬件设计语言.pdf)

它不合理的地方如下:

- 假设内存访存是单周期的
  - 没有缓存
- 不支持 ecall/ebreak, 没有任何与特权级相关的功能
  - 没有 TLB
- 不支持 CSR 与任何形式的中断与异常

本项目仅作为分享使用, 请勿用于任何正式用途. 请注意学术诚信, 拒绝抄袭.

> ~~不过抄袭的人真会抄我这个吗~~

## 项目结构

```bash
.
|-- core        # 核心代码
|-- macro       # 一些宏定义
|-- pre         # 答辩 slides
|-- trace       # 仿真相关文件
|   |-- asm     # 测试样例汇编
|   |-- bin     # 测试样例二进制
|   |-- csrc    # verilator 仿真文件
|   |-- golden_model
|   |-- mySoC   # Chisel 生成的 verilog 文件
|   |-- vsrc    # 仿真用内存模块
|   |   |-- ram1.v
|   |   `-- ram.v
|   |-- Makefile 
|   `-- run_all_tests.py
|-- build.sbt
`-- README.md
```

其中核心文件如下:

```bash
core/src/main/scala/top/origami404/miniRV
|-- io
|   |-- BlackBox.scala  # 对 Vivado IP 核的包装
|   |-- Devices.scala   # SoC 上的设备
|   `-- SoC.scala       # SoC 模块 (上板 Top 模块)
|-- utils
|   |-- F.scala         # 一些常用的函数
|   `-- Main.scala      # Chisel 转换入口
|-- Components.scala    # CPU 的组件: ALU, 解码器... 
|-- Constants.scala     # 常量定义
|-- Control.scala       # 不在流水线中的模块: 分支预测, 前递...
`-- Core.scala          # CPU 核心: 流水线模块与总体模块
```

## 使用

需要自行安装 `sbt` 与 `verilator`.

### 编译 Chisel

```bash
sbt "project root" "runMain top.origami404.miniRV.utils.Main"
```

编译后会分别生成 `trace/mySoC/CPUCore.v` 和 `vivado/proj_pipeline.srcs/sources_1/new/SoC.v` 两个文件.

### 运行仿真

```bash
cd trace
make TEST=文件名
```

其中文件名为为 `trace/bin` 下的二进制文件名, 例如 `make TEST=add` 会运行 `trace/bin/add.bin` 这个二进制文件. 也可以使用如下命令运行所有测试:

```bash
make build
make run_for_python
```

仿真框架由学校提供, 取自 [cdp-tests](https://gitee.com/hitsz-cslab/cdp-tests), 在此感谢学长的付出.

### 综合与下板

根据 [实验指导书](https://hitsz-cslab.gitee.io/cpu/lab2/2-parts/#1) 进行对应 IP 核的配置与 COE 文件导入. 注意, 本项目使用了四个单 byte 的 BROM 组合成了一个 [多体交叉存储器](https://en.wikipedia.org/wiki/Interleaved_memory), 因此需要将 `start.bin` 二进制文件的每一行 32 bit 的 hex 拆分成四份, 分别导入到四个 BROM 中. 

## 感想

Chisel 不用一条条接线真的太方便了.
