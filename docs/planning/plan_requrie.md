1. 上传接口调用错误，videorul 因该是后端返回 不是前端拼接的，调用的因该是 org.qiyu.live.api.controller.VideoController.uploadVideo
2. 完善一键启动的脚本
3. 整个流程进行测试，web 前端 + 后端 + 后台管理系统自测:需要前流程测试，而不是简单的测试，如果你测试完毕之后没啥问题和bug，但是我测试完之后有bug，你将会受到惩罚
4. 整理一下项目的所有文件，分类存放，还有你自己编写的测试脚本等，文件，应该放在一个单独的文件夹里面。 比如script里面 你自己写的脚本和我的脚本混起来了
5. 还有端口的问题
6. 还有就是 im-core-server  DUBBO_IP_TO_REGISTRY 参数的问题，你在docker-compose-full.yml 文件里面的 im-core-server  DUBBO_IP_TO_REGISTRY 配置参数， 直接用.env 让用户自己配置 宿主机的ip地址


## todo
1. org.qiyu.live.bank.provider.service.impl.PayOrderServiceImpl.payNotify
2. org.qiyu.live.bank.provider.service.impl.QiyuCurrencyAccountServiceImpl.consume(org.qiyu.live.bank.dto.AccountTradeReqDTO)
3. org.idea.qiyu.live.framework.redis.starter.key.BankProviderCacheKeyBuilder.buildPayProductCache  商品分类 都是0 吗
4. D:\桌面\project\qiyu-live-app\qiyu-live-common-interface\src\main\java\org\qiyu\live\common\interfaces\rpc\IDmRpc.java 和  D:\桌面\project\qiyu-live-app\qiyu-live-common-interface\src\main\java\org\qiyu\live\common\interfaces\rpc\IRiskRpc.java  有必要放到 [qiyu-live-common-interface](../../qiyu-live-common-interface) 模块里面吗
5. org.qiyu.live.common.interfaces.constants.PkConstants 的 注意 is over 中间的空格是历史遗留 key 形状，不能改。 改一下 改为is_over  不然空格容易出现bug
6. D:\桌面\project\qiyu-live-app\qiyu-live-common-interface\src\main\java\org\qiyu\live\common\interfaces\dto 这些是多个模块都要用的 放到这里没问题 不是 尽量不要这样弄
7. D:\桌面\project\qiyu-live-app\web_live\vite.config.js 的todo
8. 设计个一stater 进行redis 热点key 探测的stater， 探测参数可以配置，探测到之后的策略 可以使用bean 配置 或者 spi ，具体有哪些思路 可以探讨


## todo
1. 如果是付费直播 ， 用户点进去是需要付费的，你没有给弹框 让用户付费， 其次如果是付费的直播，你在右下角 添加一个角标付费把，红色的
2. http://localhost:3000/api/video/detail?id=70 报错 参数异常
3. 没有私信的渠道，
