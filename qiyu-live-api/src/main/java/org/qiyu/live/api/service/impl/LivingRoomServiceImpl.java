package org.qiyu.live.api.service.impl;

import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.api.error.ApiErrorEnum;
import org.qiyu.live.api.service.ILivingRoomService;

import org.qiyu.live.api.vo.LivingRoomInitVO;
import org.qiyu.live.api.vo.req.LivingRoomReqVO;
import org.qiyu.live.api.vo.req.OnlinePkReqVO;
import org.qiyu.live.api.vo.resp.LivingRoomPageRespVO;
import org.qiyu.live.api.vo.resp.LivingRoomRespVO;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.common.interfaces.constants.RiskConstants;
import org.qiyu.live.common.interfaces.dto.OpenLivingPushMqDTO;
import org.qiyu.live.common.interfaces.topic.UserProviderTopicNames;
import org.qiyu.live.common.interfaces.dto.RiskCheckReqDTO;
import org.qiyu.live.common.interfaces.dto.RiskCheckRespDTO;
import org.qiyu.live.common.interfaces.rpc.IRiskRpc;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.living.interfaces.constants.LivingRoomTypeEnum;
import org.qiyu.live.living.interfaces.dto.LivingPkRespDTO;
import org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO;
import org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO;
import org.qiyu.live.living.interfaces.rpc.ILivingRoomRpc;
import org.qiyu.live.stream.interfaces.rpc.ILivingStreamRpc;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.interfaces.IUserRpc;
import org.qiyu.live.web.starter.context.QiyuRequestContext;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.qiyu.live.web.starter.error.QiyuBaseError;
import org.qiyu.live.web.starter.error.QiyuErrorException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author idea
 * @Date: Created in 21:15 2023/7/19
 * @Description
 */
@Service
public class LivingRoomServiceImpl implements ILivingRoomService {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(LivingRoomServiceImpl.class);

    @DubboReference(check = false)
    private IUserRpc userRpc;
    @DubboReference(check = false)
    private IRiskRpc riskRpc;
    @DubboReference(check = false)
    private org.qiyu.live.gift.interfaces.IAnchorShopRpc anchorShopRpc;
    @jakarta.annotation.Resource
    private org.apache.rocketmq.client.producer.MQProducer mqProducer;
    @DubboReference(check = false)
    private ILivingRoomRpc livingRoomRpc;
    // stream-provider 未启动时降级，不阻塞 api 启动
    @DubboReference(check = false)
    private ILivingStreamRpc livingStreamRpc;

    @Override
    public LivingRoomPageRespVO list(LivingRoomReqVO livingRoomReqVO) {
        PageWrapper<LivingRoomRespDTO>  resultPage = livingRoomRpc.list(ConvertBeanUtils.convert(livingRoomReqVO,LivingRoomReqDTO.class));
        LivingRoomPageRespVO livingRoomPageRespVO = new LivingRoomPageRespVO();
        livingRoomPageRespVO.setList(ConvertBeanUtils.convertList(resultPage.getList(), LivingRoomRespVO.class));
        livingRoomPageRespVO.setHasNext(resultPage.isHasNext());
        return livingRoomPageRespVO;
    }

    @Override
    public Integer startingLiving(Integer type, String roomName, String covertImg, Integer payType, Integer ticketPrice) {
        Long userId = QiyuRequestContext.getUserId();
        //带货类型开播前必须已配置商品（小黄车空房间没有意义）
        if (type != null && type == 4) {
            java.util.List<org.qiyu.live.gift.dto.AnchorShopInfoDTO> shopList = anchorShopRpc.listByAnchorId(userId);
            ErrorAssert.isTure(shopList != null && !shopList.isEmpty(), ApiErrorEnum.SHOP_CONFIG_REQUIRED);
        }
        UserDTO userDTO = userRpc.getByUserId(userId);
        LivingRoomReqDTO livingRoomReqDTO = new LivingRoomReqDTO();
        livingRoomReqDTO.setAnchorId(userId);
        //主播自定义直播间名称与封面；未填时降级为默认名 / 用户头像
        livingRoomReqDTO.setRoomName(StringUtils.hasText(roomName)
                ? roomName : ("主播-" + userId + "的直播间"));
        //房间名敏感词校验（只校验自定义名，默认名不含用户输入）
        if (StringUtils.hasText(roomName)) {
            RiskCheckRespDTO riskResp = riskRpc.checkText(
                    RiskCheckReqDTO.of(roomName, RiskConstants.SCENE_ROOM_NAME, userId));
            ErrorAssert.isTure(!riskResp.isBlocked(), ApiErrorEnum.CONTENT_BLOCKED);
        }
        livingRoomReqDTO.setCovertImg(StringUtils.hasText(covertImg)
                ? covertImg : userDTO.getAvatar());
        livingRoomReqDTO.setType(type);
        // 付费直播间：门票模式时校验价格合法
        if (payType != null && payType == 1) {
            ErrorAssert.isTure(ticketPrice != null && ticketPrice > 0,
                    new QiyuErrorException(-1, "门票价格必须大于0"));
            livingRoomReqDTO.setPayType(1);
            livingRoomReqDTO.setTicketPrice(ticketPrice);
        }
        Integer roomId = livingRoomRpc.startLivingRoom(livingRoomReqDTO);
        if (roomId != null) {
            // 开播成功 → 通知 user-provider 给粉丝推 5567 + 站内通知
            try {
                OpenLivingPushMqDTO pushDTO = OpenLivingPushMqDTO.of(userId,
                        userDTO.getNickName(), roomId, livingRoomReqDTO.getRoomName(), livingRoomReqDTO.getCovertImg());
                org.apache.rocketmq.common.message.Message message = new org.apache.rocketmq.common.message.Message(
                        UserProviderTopicNames.OPEN_LIVING_PUSH_TOPIC, com.alibaba.fastjson.JSON.toJSONBytes(pushDTO));
                mqProducer.send(message);
            } catch (Exception e) {
                LOGGER.error("[startingLiving] send open living push error, userId={}, roomId={}", userId, roomId, e);
            }
        }
        return roomId;
    }

    @Override
    public boolean onlinePk(OnlinePkReqVO onlinePkReqVO) {
        LivingRoomReqDTO reqDTO = ConvertBeanUtils.convert(onlinePkReqVO,LivingRoomReqDTO.class);
        reqDTO.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
        reqDTO.setPkObjId(QiyuRequestContext.getUserId());
        LivingPkRespDTO tryOnlineStatus = livingRoomRpc.onlinePk(reqDTO);
        ErrorAssert.isTure(tryOnlineStatus.isOnlineStatus(), new QiyuErrorException(-1,tryOnlineStatus.getMsg()));
        return true;
    }

    @Override
    public boolean closeLiving(Integer roomId) {
        LivingRoomReqDTO livingRoomReqDTO = new LivingRoomReqDTO();
        livingRoomReqDTO.setRoomId(roomId);
        livingRoomReqDTO.setAnchorId(QiyuRequestContext.getUserId());
        boolean closeStatus = livingRoomRpc.closeLiving(livingRoomReqDTO);
        if (closeStatus) {
            // 关播成功后联动停止推流（踢掉 SRS 推流客户端、重置流状态），失败不影响关播结果
            try {
                livingStreamRpc.stopStream(roomId);
            } catch (Exception e) {
                LOGGER.warn("[closeLiving] stopStream failed, roomId={}", roomId, e);
            }
        }
        return closeStatus;
    }

    @Override
    public Integer myLivingRoom() {
        org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO room = livingRoomRpc.queryByAnchorId(QiyuRequestContext.getUserId());
        return room == null ? null : room.getId();
    }

    @Override
    public Integer onlineCount(Integer roomId) {
        ErrorAssert.isNotNull(roomId, BizBaseErrorEnum.PARAM_ERROR);
        LivingRoomReqDTO reqDTO = new LivingRoomReqDTO();
        reqDTO.setRoomId(roomId);
        reqDTO.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
        try {
            return livingRoomRpc.queryUserIdByRoomId(reqDTO).size();
        } catch (Exception e) {
            LOGGER.warn("[onlineCount] query failed, roomId={}", roomId, e);
            return 0;
        }
    }

    @Override
    public LivingRoomInitVO anchorConfig(Long userId, Integer roomId) {
        LivingRoomRespDTO respDTO = livingRoomRpc.queryByRoomId(roomId);
        ErrorAssert.isNotNull(respDTO,ApiErrorEnum.LIVING_ROOM_END);
        // 付费直播间门票校验：非主播、未购票 → 特定错误码（前端弹购票窗）
        if (respDTO.getPayType() != null && respDTO.getPayType() == 1
                && !userId.equals(respDTO.getAnchorId())) {
            boolean hasTicket = ticketStringRedisTemplate.hasKey(
                    org.qiyu.live.common.interfaces.constants.TicketConstants.ROOM_TICKET_KEY_PREFIX
                            + roomId + ":" + userId);
            ErrorAssert.isTure(Boolean.TRUE.equals(hasTicket), ApiErrorEnum.TICKET_REQUIRED);
        }
        Map<Long,UserDTO> userDTOMap = userRpc.batchQueryUserInfo(Arrays.asList(respDTO.getAnchorId(),userId).stream().distinct().collect(Collectors.toList()));
        UserDTO anchor = userDTOMap.get(respDTO.getAnchorId());
        UserDTO watcher = userDTOMap.get(userId);
        LivingRoomInitVO respVO = new LivingRoomInitVO();
        respVO.setAnchorNickName(anchor.getNickName());
        respVO.setWatcherNickName(watcher.getNickName());
        respVO.setUserId(userId);
        //给定一个默认的头像
        respVO.setAvatar(StringUtils.isEmpty(anchor.getAvatar())?"https://s1.ax1x.com/2022/12/18/zb6q6f.png":anchor.getAvatar());
        respVO.setWatcherAvatar(watcher.getAvatar());
        if (respDTO == null || respDTO.getAnchorId() == null || userId == null) {
            //这种就是属于直播间已经不存在的情况了
            respVO.setRoomId(-1);
        } else {
            respVO.setRoomId(respDTO.getId());
            respVO.setAnchorId(respDTO.getAnchorId());
            respVO.setAnchor(respDTO.getAnchorId().equals(userId));
        }
        respVO.setRoomName(respDTO.getRoomName());
        //封面优先用主播自定义上传图，没有再落到外链默认图
        respVO.setDefaultBgImg(StringUtils.hasText(respDTO.getCovertImg())
                ? respDTO.getCovertImg() : "https://picst.sunbangyan.cn/2023/08/29/waxzj0.png");
        return respVO;
    }



    @Override
    public Long inviteLinkMic(Integer roomId, Long guestUserId) {
        org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO room = livingRoomRpc.queryByRoomId(roomId);
        ErrorAssert.isNotNull(room, ApiErrorEnum.LIVING_ROOM_END);
        // 只有主播能发起邀请
        ErrorAssert.isTure(room.getAnchorId() != null && room.getAnchorId().equals(QiyuRequestContext.getUserId()),
                new QiyuErrorException(-1, "只有主播能发起连麦邀请"));
        Long linkMicId = livingRoomRpc.inviteLinkMic(roomId, guestUserId);
        if (linkMicId == null) {
            throw new QiyuErrorException(-1, "该观众已在连麦或已有待处理邀请");
        }
        return linkMicId;
    }

    @Override
    public Boolean acceptLinkMic(Long linkMicId) {
        return livingRoomRpc.acceptLinkMic(linkMicId);
    }

    @Override
    public Boolean hangUpLinkMic(Integer roomId) {
        return livingRoomRpc.hangUpLinkMic(roomId);
    }


    @jakarta.annotation.Resource
    private org.springframework.data.redis.core.StringRedisTemplate ticketStringRedisTemplate;

    @Override
    public Boolean pkLike(Integer roomId) {
        return livingRoomRpc.pkLike(roomId, QiyuRequestContext.getUserId());
    }
    @DubboReference(check = false)
    private org.qiyu.live.bank.interfaces.IQiyuCurrencyAccountRpc currencyAccountRpc;

    @Override
    public Boolean buyTicket(Integer roomId) {
        Long userId = QiyuRequestContext.getUserId();
        org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO room = livingRoomRpc.queryByRoomId(roomId);
        ErrorAssert.isNotNull(room, ApiErrorEnum.LIVING_ROOM_END);
        if (room.getPayType() == null || room.getPayType() != 1) {
            return true; // 免费房间无需购票
        }
        String ticketKey = org.qiyu.live.common.interfaces.constants.TicketConstants.ROOM_TICKET_KEY_PREFIX
                + roomId + ":" + userId;
        if (Boolean.TRUE.equals(ticketStringRedisTemplate.hasKey(ticketKey))) {
            return true; // 已购幂等
        }
        int price = room.getTicketPrice() == null ? 0 : room.getTicketPrice();
        ErrorAssert.isTure(price > 0, BizBaseErrorEnum.PARAM_ERROR);
        // 金币直扣（余额不足由 decr 内部抛错）
        currencyAccountRpc.decr(userId, price);
        // 主播收益入账
        currencyAccountRpc.incr(room.getAnchorId(), price);
        ticketStringRedisTemplate.opsForValue().set(ticketKey, "1",
                java.time.Duration.ofHours(org.qiyu.live.common.interfaces.constants.TicketConstants.TICKET_TTL_HOURS));
        LOGGER.info("[buyTicket] roomId={}, userId={}, price={}", roomId, userId, price);
        return true;
    }
}
