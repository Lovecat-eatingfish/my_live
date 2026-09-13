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
    private org.qiyu.live.gift.interfaces.IAnchorShopRpc anchorShopRpc;
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
    public Integer startingLiving(Integer type, String roomName, String covertImg) {
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
        livingRoomReqDTO.setCovertImg(StringUtils.hasText(covertImg)
                ? covertImg : userDTO.getAvatar());
        livingRoomReqDTO.setType(type);
        return livingRoomRpc.startLivingRoom(livingRoomReqDTO);
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

}
