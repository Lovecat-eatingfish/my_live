package org.qiyu.live.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.api.service.ImService;
import org.qiyu.live.api.vo.resp.ImConfigVO;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.interfaces.ImTokenRpc;
import org.qiyu.live.web.starter.context.QiyuRequestContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * @Author idea
 * @Date: Created in 10:49 2023/7/26
 * @Description
 */
@Service
public class ImServiceImpl implements ImService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImServiceImpl.class);

    @DubboReference(check = false)
    private ImTokenRpc imTokenRpc;
    @Resource
    private DiscoveryClient discoveryClient;
    @Value("${qiyu.im.ws-port}")
    private int wsImServerPort;
    @Value("${qiyu.im.tcp-port}")
    private int tcpImServerPort;

    @Override
    public ImConfigVO getImConfig() {
        ImConfigVO imConfigVO = new ImConfigVO();
        // 生成 IM 登录凭证： 只有有这个token 才能登录 IM 服务器
        imConfigVO.setToken(imTokenRpc.createImLoginToken(QiyuRequestContext.getUserId(), AppIdEnum.QIYU_LIVE_BIZ.getCode()));
        buildImServerAddress(imConfigVO);
        return imConfigVO;
    }

    private void buildImServerAddress(ImConfigVO imConfigVO) {
        List<ServiceInstance> serviceInstanceList = discoveryClient.getInstances("qiyu-live-im-core-server");
        if (serviceInstanceList == null || serviceInstanceList.isEmpty()) {
            LOGGER.error("[buildImServerAddress] qiyu-live-im-core-server 未注册到 nacos，无法下发 IM 服务器地址");
            return;
        }
        // 随机选择一个 IM 服务器： 算法可以拓展为轮询、权重等策略
        Collections.shuffle(serviceInstanceList);
        ServiceInstance aimInstance = serviceInstanceList.get(0);
        // 端口从配置读取（与 im-core-server 的 qiyu.im.ws.port / qiyu.im.tcp.port 保持一致）
        // Web 端只能走 WS，原生 App 两种都能连，但 TCP 更省，所以 App 走 tcp
        imConfigVO.setWsImServerAddress("127.0.0.1" + ":" + wsImServerPort);
        imConfigVO.setTcpImServerAddress("127.0.0.1" + ":" + tcpImServerPort);
    }
}
