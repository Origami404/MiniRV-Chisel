# 流水线高级控制：冒险, 前递与分支预测

> 统一假设为 IF/ID/EXE/MEM/WB 五级流水线

本文从几个例子出发, 分析构造一条合格的流水线所需要考虑的高级控制问题以及它们所需要引入的硬件. 
以便于在实际构造流水线硬件时, 能更加地从容不迫. 

## 1. 数据冒险与前递

### 1.1 RAW (Read After Write)

考虑两条指令序列：

```assembly
def x1 ...
use1 x1 ...
use2 x1 ...
use3 x1
```

流水线状态如下所示：

| t  | IF  | ID  | EXE | MEM | WB  | pc |
| -  | --- | --- | --- | --- | --- | -- |
| 0  | def |     |     |     |     | 0  |
| 1  | use | def |     |     |     | 4  |
| 2* |use2 |use1 | def |     |     | 8  |
| 3* |     |use2 |use1 | def |     | 12 |
| 4  |     |use3 |use2 |use1 | def | 16 |

注意看 `t = 2`, 
此时 use1 指令在 ID 阶段需要获得 def 定义的寄存器的值, 
但是它还没有到 WB 阶段, 所以不能从 RF 中获取. 
我们可以直接从 ALU 的 result 拉线出来作为 use1 指令的某个操作数, 
这就产生了前递. 

然后注意看 `t = 3`, 
此时 use2 指令在 ID 阶段需要获得 def 定义的寄存器的值, 
但是同样 def 还没有到 WB 阶段, 所以不能从 RF 中获取. 
因此我们需要从 EXE/MEM 中拉线出来作为 use2 指令的某个操作数, 
这也是前递的一种可能. 

当 `t = 4` 时, def 指令到达 WB 阶段, 此时它的值已经在 RF 中了, 
因此 use3 指令可以直接从 RF 中获取 def 的值, 不再需要前递. 

所以要做前递, 首先需要在 ID/EXE 和 EXE/MEM 中保存解码出的 rd 的信息,
然后依次判断:

1. ID/EXE 中的 rd 是否与 ID 阶段的 rs1/rs2 相同, 如果相同则前递 EXE 中的 alu_result
2. EXE/MEM 中的 rd 是否与 ID 阶段的 rs1/rs2 相同, 如果相同则前递 EXE/MEM 中的 alu_result
3. 否则, 从 RF 中获取 rs1/rs2 

注意, 我们必须优先考虑 ID/EXE 中的 rd, 因为它是最新的,
当两条 def 指令同时写相同的寄存器时, 
我们必须保证我们前递的是后面的 def 的结果, 而不是前面的. 

这也许可以算一种 WAW 冲突.

### 1.2 WAR (Write After Read)

由于我们在 ID 中便获取 rs1/rs2, 而到 WB 才写回,
中间相差 3 个周期, 不可能出现 WAR 的情况.

### 1.3 WAW (Write After Write)

在 RAW 中已经考虑完全了.

## 2. 访存导致的停顿

考虑指令序列如下：

```assembly
ld x1 ...
use1 x1 ...
use2 x1 ...
use3 x1 ...
use4 x1 ...
```

流水线状态如下所示：

| t  | IF  | ID  | EXE | MEM | WB  | pc |
| -  | --- | --- | --- | --- | --- | -- |
| 0  | ld  |     |     |     |     | 0  |
| 1  |use1 | ld  |     |     |     | 4  |
| 2* |!use2|!use1|%ld  |     |     | 8  |
| 3* | use2| use1| nop | ld  |     | 8  |
| 4  | use3| use2| use1| nop | ld  | 12 |

注意看 `t = 2`,
此时我们发现 use1 指令在 ID 阶段需要获得 ld 定义的寄存器的值,
但是我们发现 ld 指令还没有到达 WB 阶段, 所以不能从 RF 中获取.
更糟糕的是, 由于 ld 指令需要访存, 我们并不能直接像普通运算一样前递 ALU 算出的值,
所以我们不得不停顿流水线, 并插入一个气泡, 等待 ld 至少到达 MEM 阶段.
一旦 ld 到达了 MEM 阶段, 我们就可以前递 MEM 的输出到 ID 阶段供 ID/EXE 接收.

为此, 我们需要做几件事:

1. 让 IF/ID 和 PC 拥有 "下周期保持当前周期值" 的功能
2. 让 ID/EXE 输出 "nop" 的功能
3. MEM -> ID 的前递

其中, 1 可以通过更改流水线寄存器的中寄存器的写入实现:

```scala
when (!io.pipe.stall) {
  reg := io.in
}
```

而 2 可以通过修改 ID/EXE 的输出实现:

```scala
when (io.pipe.nop) {
    out.rfw_en := Controls.rfw_en.no
    out.memw_en := Controls.memw_en.no
} .otherwise {
    out.rfw_en := reg.rfw_en
    out.memw_en := reg.memw_en
}
```

而 3 则需要修改我们的前递逻辑为:

1. EXE/MEM 中的 rd 是否与 ID 阶段的 rs1/rs2 相同, 如果相同:
   1. 当前指令是否为 ld, 如果是, 则前递 MEM 中的 memr_data
   2. 否则, 前递 EXE/MEM 中的 alu_result
2. ID/EXE 中的 rd 是否与 ID 阶段的 rs1/rs2 相同, 如果相同则前递 EXE 中的 alu_result
3. 否则, 从 RF 中获取 rs1/rs2 

并且还需要在 ID/EXE 和 EXE/MEM 中保存解码出的 is_ld 信号.

### 3. 直接跳转导致的停顿

回忆一下 jal 与 jalr 的作用:

```
jal:  rd = PC+4; PC += imm
jalr: rd = PC+4; PC = rs1 + imm
```

它们都是会更改 pc 的指令,
因此我们可以将它们看作简单的跳转指令. 
在考虑条件跳转之前, 先考虑一下它们是有益的.

下边我们使用 E 表示不发生跳转时接着执行的指令,
用 T 表示发生跳转时接着执行的指令,
它们分别是 Then 和 Else 的意思.
注意在汇编层面, 紧紧跟着跳转指令的是 E1, E2, ..., (Else 系列)
与高级语言中的 `if` 语句看起来正好相反.

同时, 我们假设发生跳转后 pc 的值总为 420.

#### 3.1 jal

| t  | IF  | ID  | EXE | MEM | WB  | pc |
| -  | --- | --- | --- | --- | --- | -- |
| 0  | jal |     |     |     |     | 0  |
| 1  | E1  | jal |     |     |     | 4  |
| 2* | T1  | E1  |%jal |     |     | 420|
| 3* | T2  | T1  | nop | jal |     | 424|

当我们在在 ID/EXE 检测到 jal 时, 就拉高 ID/EXE 的 nop. 

之所以不是要求 IF/ID 进行 nop, 
主要是考虑到如果同一个流水线寄存器既要能产生 nop 又要能保持当前值, 
有点难写, 所以就要求 ID/EXE 进行 nop.

#### 3.2 jalr

对 jalr 而言, 我们需要复用 pc 的那个加法器, 
否则我们就需要用 ALU 的那个, 后者需要等到 EXE 才能改 pc,
那么会引入两个气泡, 非常亏.

| t  | IF  | ID  | EXE | MEM | WB  | pc |
| -  | --- | --- | --- | --- | --- | -- |
| 0  |jalr |     |     |     |     | 0  |
| 1  | E1  |jalr |     |     |     | 4  |
| 2* | T1  | E1  |%jalr|     |     | 420|
| 3* | T2  | T1  | nop | jalr|     | 424|

其他需求与 jal 类似. 
我们需要引入一个新的控制信号 `pc_lhs_sel`, 
用于选择到底 pc 的那个加法器的第一个参数应该是 pc 还是从 ID 直接接过来的 `Reg[rs1]`.
<!-- TODO: 这里的时序会不会有问题?? 感觉有点奇怪 -->

### 4. 分支预测与跳转导致的停顿

分支预测有两个部分, 一是预测, 二是检查.
前者在 ID 阶段做, 后者在 EXE 阶段做.

我们分别来考虑四种情况:

1. 预测不, 实际不
2. 预测不, 实际跳
3. 预测跳, 实际跳
4. 预测跳, 实际不

### 4.1 预测不, 实际不

这是最简单的情况:

| t  | IF  | ID  | EXE | MEM | WB  | pc |
| -  | --- | --- | --- | --- | --- | -- |
| 0  | beq |     |     |     |     | 0  |
| 1* | E1  | beq |     |     |     | 4  |
| 2* | E2  | E1  | beq |     |     | 8  |
| 3  | E3  | E2  | E1  | beq |     | 12 |

在 `t = 1` 时, 我们检测到了一条 b 指令, 并且预测器给出了 "不跳转" 的预测. 
因此 `t = 2` 时 pc 继续加 4, 正常取出 E2

在 `t = 2` 时, 我们开始检查预测是否正确, 发现预测正确, 所以什么都不需要做. 

从这个情况我们可以得到, 
我们需要在 ID/EXE 中保存一个 is_b 的信号和此次分支跳转的预测结果.

### 4.2 预测不, 实际跳

| t  | IF  | ID  | EXE | MEM | WB  | pc |
| -  | --- | --- | --- | --- | --- | -- |
| 0  | beq |     |     |     |     | 0  |
| 1* | E1  | beq |     |     |     | 4  |
| 2* | E2  | E1  |%beq |     |     | 8  |
| 3* | T1  | E2  |%nop | beq |     | 420|
| 4  | T2  | T1  | nop | nop | beq | 424|

这里我们发生了一次预测错误,
由于只能在 EXE 发现错误, 因此无论如何都是要向流水线中引入两条错误指令的.
为此我们需要一种能保持 ID/EXE 的 nop 状态的功能. 可以通过引入一个 `next_nop` 信号实现:

```scala
when (io.pipe.next_nop) {
    reg.rfw_en := Controls.rfw_en.no
    reg.memw_en := Controls.memw_en.no
} .otherwise {
    reg.rfw_en := in.rfw_en
    reg.memw_en := in.memw_en
}
```

`next_nop` 与之前的 `nop` 不同, 它修改 reg 而不是 out, 从而得以延迟一个周期.

### 4.3 预测跳, 实际跳

| t  | IF  | ID  | EXE | MEM | WB  | pc |
| -  | --- | --- | --- | --- | --- | -- |
| 0  | beq |     |     |     |     | 0  |
| 1* | E1  | beq |     |     |     | 4  |
| 2* | T1  | E1  |%beq |     |     | 420|
| 3  | T2  | T1  | nop | beq |     | 424|

我们必须卡住在 beq 到达 ID 后, 新进来的那个 E1 指令.
为此需要在 `t = 1` 时拉高 ID/EXE next_nop.

### 4.4 预测跳, 实际不

这是最复杂的一种情况.

| t  | IF  | ID  | EXE | MEM | WB  | pc |
| -  | --- | --- | --- | --- | --- | -- |
| 0  | beq |     |     |     |     | 0  |
| 1* | E1  | beq |     |     |     | 4  |
| 2* | T1  | E1  |%beq |     |     | 420|
| 3* | E1  | T1  |%nop | beq |     | 4  |
| 4* | E2  | E1  | nop | nop | beq | 8  |
| 5  | E3  | E2  | E1  | nop | nop | 12 |

在预测跳转时, 我们需要像 `4.3` 一样卡住 E1, 因此会在 `t = 1` 拉高 ID/EXE next_nop.
同时, 因为我们在 EXE 阶段检查到了预测错误, 因此我们需要在 `t = 2` 时拉高 IF/ID nop 和 next_nop.
唯有该种情况下, 在 `t = 2`, ID/EXE 的 `next_nop` 和 `nop` 同时成立.

### 分支预测: 总结

那么, 代价是什么呢?

- 我们需要向 ID/EXE 中引入新的控制信号 `is_b` 和 `b_pred` (预测结果)
- 我们需要 ID/EXE 支持 next_nop

我们获得了什么? (下表是气泡数量)

| 实际/预测 | 不跳转 | 跳转   |
| -------- | ------ | ------ |
| 不跳转    | 0      | 2      |
| 跳转      | 2      | 1      |

只考虑分支预测与跳转的话, ID/EXE 的 nop 和 next_nop 信号怎么连?

- `nop`: 预测错误
- `next_nop`: 预测跳转

## 总结

为了解决冒险, 我们需要引入前递模块与气泡功能, 后者需要更多的控制信号.

我们需要加入这些控制信号:

- PC: pc_lhs_sel
- ID/EXE: rd, is_ld
- EXE/MEM: rd, is_ld

我们需要加入这些功能:

- 前递模块: 新的控制与两个选择器
- 分支预测模块: 给出预测值, 接收 alu flag 并给出预测是否正确
- PC 和 IF/ID 的当前周期停顿功能: stall
- ID/EXE 的 nop 与 next_nop 功能: nop, next_nop

前递模块的逻辑如下:

1. ID/EXE 中的 rd 是否与 ID 阶段的 rs1/rs2 相同, 如果相同则前递 EXE 中的 alu_result
2. EXE/MEM 中的 rd 是否与 ID 阶段的 rs1/rs2 相同, 如果相同:
   1. 当前指令是否为 ld, 如果是, 则前递 MEM 中的 memr_data
   2. 否则, 前递 EXE/MEM 中的 alu_result
3. 否则, 从 RF 中获取 rs1/rs2 

停顿与 nop 的逻辑如下:

- `stall`: (ID/EXE 的 is_ld) 且 (ID/EXE.rd 与 ID 阶段的 rs1 或 rs2 相同)
- nop: stall 或 分支预测错误
- next_nop: (ID/EXE 检测到 jal/jalr) 或 分支预测跳转

