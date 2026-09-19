package org.qiyu.live.im.core.server.handler.ws;

import com.alibaba.fastjson.JSON;
import io.netty.channel.ChannelFuture;
import org.qiyu.live.im.core.server.common.ImContextUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.im.constants.ImConstants;
import org.qiyu.live.im.constants.ImMsgCodeEnum;
import org.qiyu.live.im.core.server.common.ImMsg;
import org.qiyu.live.im.core.server.handler.impl.LoginMsgHandler;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.interfaces.ImTokenRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ws的握手连接处理器
 *
 * @Author idea
 * @Date created in 9:30 下午 2022/12/22
 */
@Component
@ChannelHandler.Sharable
public class WsSharkHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(WsSharkHandler.class);

    //指定监听的端口
    @Value("${qiyu.im.ws.port}")
    private int port;
    @Value("${spring.cloud.nacos.discovery.ip}")
    private String serverIp;
    @DubboReference(check = false)
    private ImTokenRpc imTokenRpc;
    @Resource
    private LoginMsgHandler loginMsgHandler;

    private WebSocketServerHandshaker webSocketServerHandshaker;
    private static Logger logger = LoggerFactory.getLogger(WsSharkHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        //握手接入ws
        // 1. 如果是HTTP请求 → 处理WebSocket握手
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, ((FullHttpRequest) msg));
            return;
        }

        //正常关闭链路
        // 2. 如果是关闭帧 → 正常关闭连接
        if (msg instanceof CloseWebSocketFrame) {
            webSocketServerHandshaker.close(ctx.channel(), (CloseWebSocketFrame) ((WebSocketFrame) msg).retain());
            return;
        }
        //将消息传递给下一个链路处理器去处理
        // 3. 其他消息 → 传递到下一个处理器
        ctx.fireChannelRead(msg);
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest msg) {
        // 新格式（推荐，token 不进 URL，避免泄漏到访问日志）：ws://host:port/{userId}/{code}/{param}
        // 旧格式（兼容保留）：ws://host:port/{token}/{userId}/{code}/{param}
        // 新格式的鉴权延迟到 1001 登录包（LoginMsgHandler 校验 body token），握手本身不再做鉴权
        String webSocketUrl = "ws://" + serverIp + ":" + port;
        // 构造握手响应返回
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(webSocketUrl, null, false);
        String uri = msg.uri();
        String[] paramArr = uri.split("/");
        String token = null;
        Long userId;
        if (paramArr.length >= 5) {
            // 旧格式：{token}/{userId}/{code}/{param}
            token = paramArr[1];
            userId = Long.valueOf(paramArr[2]);
        } else {
            // 新格式：{userId}/{code}/{param}
            userId = Long.valueOf(paramArr[1]);
        }
        Long queryUserId = token == null ? userId : imTokenRpc.getUserIdByToken(token);
        Integer appId = null;
        if (token != null) {
            //token的尾部就是appId
            appId = Integer.valueOf(token.substring(token.lastIndexOf("%") + 1));
        }
        if (queryUserId == null || !queryUserId.equals(userId)) {
            LOGGER.error("[WsSharkHandler] token 校验不通过！");
            //校验不通过，不允许建立连接
            ctx.close();
            return;
        }
        //建立ws的握手连接
        webSocketServerHandshaker = wsFactory.newHandshaker(msg);

        if (webSocketServerHandshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            return;
        }

        ChannelFuture channelFuture = webSocketServerHandshaker.handshake(ctx.channel(), msg);
        //首次握手建立ws连接后，返回一定的内容给到客户端
        if (channelFuture.isSuccess()) {
            Integer code = Integer.valueOf(paramArr[paramArr.length - 2]);
            Integer roomId = null;
            if (code == ParamCodeEnum.LIVING_ROOM_LOGIN.getCode()) {
                roomId = Integer.valueOf(paramArr[paramArr.length - 1]);
            }
            if (token != null) {
                // 旧格式：握手即完成鉴权+注册（保持原行为）
                loginMsgHandler.loginSuccessHandler(ctx, userId, appId, roomId);
            } else if (roomId != null) {
                // 新格式：仅暂存 roomId（URL userId 不作为凭据），鉴权与注册由
                // 1001 登录包（LoginMsgHandler 校验 body token）完成后进行
                ImContextUtils.setRoomId(ctx, roomId);
            }
            logger.info("[WebsocketSharkHandler] channel is connect!");
        }
    }

    enum ParamCodeEnum {
        LIVING_ROOM_LOGIN(1001, "直播间登录");

        int code;
        String desc;

        ParamCodeEnum(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public int getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }
    }
}
