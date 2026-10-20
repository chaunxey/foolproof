# 原始需求与约束

> 本文件保存项目负责人写下的原始需求（原样保留，未做改写），用于起草
> [`.specify/memory/constitution.md`](../.specify/memory/constitution.md) 与后续 feature 规格。
> 定稿后本文件可删除或归档。

---

帮我写一份constitution
先说清楚我们做什么，不做什么：
我们做一个纯本地的工具app---主要实现以下功能：
1）压缩图片
2）重复照片清理（对比汉明距离）
3）多张图片合并生成pdf文件
4）本地文件转PDF文件
5）类豆包语音聊天（需要自己输入api配置）
技术栈确定如下，禁止AI 自作主张使用其他替换
-创建名为base 的Android Library的模块
   1）主要实现封装MVI架构，使用channel 实现viewmodel的副作用，使用stateflow实现viewmodel的状态管理。
   2）创建BaseApplication，引入koin注入架构（只有「需要外部提供、容易替换、需要统一生命周期、需要单例 / 多例控制、方便单元测试」的类才注入，例如viewmodel）。
   3）引入retrofit，封装HttpClient，对外提供get和pose请求接口，可配置log日志信息，请求头以及全局处理请求错误。统一管理网络请求。
   4）统一管理日志打印工具类，使用Object LogUtil保证全局单例，只有debug模式下打印日志，打印信息包括写改日志的文件名，行数以及日志信息。
-创建名为conmon 的Android Library的模块
   1）图片压缩只要使用Compressor sdk git地址为：https://github.com/zetbaitsu/Compressor
