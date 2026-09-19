package org.qiyu.live.api.service.impl;

import io.minio.PutObjectArgs;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.api.config.VideoMinioConfig;
import org.qiyu.live.api.error.ApiErrorEnum;
import org.qiyu.live.api.service.IVideoApiService;
import org.qiyu.live.api.vo.req.VideoPublishReqVO;
import org.qiyu.live.api.vo.resp.VideoCommentRespVO;
import org.qiyu.live.api.vo.resp.VideoDetailRespVO;
import org.qiyu.live.api.vo.resp.VideoItemRespVO;
import org.qiyu.live.api.vo.resp.VideoTagRespVO;
import org.qiyu.live.common.interfaces.constants.RiskConstants;
import org.qiyu.live.web.starter.error.QiyuErrorException;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.common.interfaces.dto.RiskCheckReqDTO;
import org.qiyu.live.common.interfaces.dto.RiskCheckRespDTO;
import org.qiyu.live.common.interfaces.rpc.IRiskRpc;
import org.qiyu.live.video.dto.VideoCommentDTO;
import org.qiyu.live.video.dto.VideoDTO;
import org.qiyu.live.video.interfaces.rpc.IVideoRpc;
import org.qiyu.live.video.interfaces.rpc.TagDTO;
import org.qiyu.live.web.starter.context.QiyuRequestContext;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 视频服务实现（api 层）
 */
@Service
public class VideoApiServiceImpl implements IVideoApiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoApiServiceImpl.class);

    /** 视频文件大小上限 300MB */
    private static final long MAX_VIDEO_SIZE = 300L * 1024 * 1024;
    private static final Set<String> ALLOWED_VIDEO_EXT = Set.of("mp4", "webm", "mov", "m4v");

    @DubboReference(check = false)
    private IVideoRpc videoRpc;
    @DubboReference(check = false)
    private IRiskRpc riskRpc;
    @Resource
    private VideoMinioConfig videoMinioConfig;
    @Resource
    private org.apache.rocketmq.client.producer.MQProducer mqProducer;

    /** 敏感词拦截校验（标题/评论等提交类内容） */
    private void assertNotBlocked(String text, int scene, Long userId) {
        RiskCheckRespDTO riskResp = riskRpc.checkText(RiskCheckReqDTO.of(text, scene, userId));
        ErrorAssert.isTure(!riskResp.isBlocked(), ApiErrorEnum.CONTENT_BLOCKED);
    }

    @Override
    public String uploadVideo(MultipartFile file, Long userId) {
        ErrorAssert.isTure(file != null && !file.isEmpty(), BizBaseErrorEnum.PARAM_ERROR);
        ErrorAssert.isTure(file.getSize() <= MAX_VIDEO_SIZE, BizBaseErrorEnum.PARAM_ERROR);
        String ext = resolveExt(file.getOriginalFilename());
        String datePath = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String objectName = "videos/" + datePath + "/" + userId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "." + ext;
        // 流式写入，避免大文件整体读入堆内存
        try (var in = file.getInputStream()) {
            videoMinioConfig.getMinioClient().putObject(PutObjectArgs.builder()
                    .bucket(videoMinioConfig.getVideoBucket())
                    .object(objectName)
                    .contentType("video/" + ("mov".equals(ext) ? "quicktime" : ext))
                    .stream(in, file.getSize(), -1)
                    .build());
        } catch (Exception e) {
            LOGGER.error("[uploadVideo] failed, userId={}, size={}", userId, file.getSize(), e);
            throw new RuntimeException("视频上传失败", e);
        }
        return videoMinioConfig.publicUrl(objectName);
    }

    @Override
    public Long publish(VideoPublishReqVO reqVO, Long userId) {
        ErrorAssert.isTure(reqVO != null && StringUtils.hasText(reqVO.getTitle()), BizBaseErrorEnum.PARAM_ERROR);
        ErrorAssert.isTure(StringUtils.hasText(reqVO.getVideoUrl()), BizBaseErrorEnum.PARAM_ERROR);
        assertNotBlocked(reqVO.getTitle(), RiskConstants.SCENE_VIDEO_TITLE, userId);

        VideoDTO dto = new VideoDTO();
        dto.setUserId(userId);
        dto.setTitle(reqVO.getTitle());
        dto.setDescription(reqVO.getDescription() == null ? "" : reqVO.getDescription());
        dto.setVideoUrl(reqVO.getVideoUrl());
        dto.setCoverUrl(reqVO.getCoverUrl() == null ? "" : reqVO.getCoverUrl());
        dto.setTagId(reqVO.getTagId() == null ? 0 : reqVO.getTagId());
        dto.setDuration(reqVO.getDuration() == null ? 0 : reqVO.getDuration());
        dto.setSize(reqVO.getSize() == null ? 0 : reqVO.getSize());
        VideoDTO published = videoRpc.publish(dto);
        if (published != null && published.getId() != null) {
            // 发视频经验 +50（MQ 交 user-provider 单点结算）
            try {
                org.qiyu.live.common.interfaces.dto.UserExpChangeMqDTO expDTO =
                        org.qiyu.live.common.interfaces.dto.UserExpChangeMqDTO.of(userId, 50,
                                org.qiyu.live.common.interfaces.constants.UserLevelConstants.EXP_SCENE_VIDEO, null);
                mqProducer.send(new org.apache.rocketmq.common.message.Message(
                        org.qiyu.live.common.interfaces.topic.UserProviderTopicNames.USER_EXP_CHANGE_TOPIC,
                        com.alibaba.fastjson.JSON.toJSONBytes(expDTO)));
            } catch (Exception e) {
                LOGGER.error("[publish] send video exp error, userId={}", userId, e);
            }
        }
        return published != null ? published.getId() : null;
    }

    @Override
    public List<VideoItemRespVO> feed(Long lastId, int size) {
        return toItems(videoRpc.feed(lastId, QiyuRequestContext.getUserId(), size));
    }

    @Override
    public List<VideoItemRespVO> related(Long videoId, int size) {
        return toItems(videoRpc.listRelated(videoId, QiyuRequestContext.getUserId(), size));
    }

    @Override
    public void playReport(Long videoId, int watchedSeconds, int duration) {
        videoRpc.playReport(videoId, QiyuRequestContext.getUserId(), watchedSeconds, duration);
    }

    @Override
    public List<VideoItemRespVO> search(String keyword, int page, int pageSize) {
        return toItems(videoRpc.searchVideos(keyword, QiyuRequestContext.getUserId(), page, pageSize));
    }

    @Override
    public List<VideoItemRespVO> listByUser(Long targetUserId, int page, int pageSize) {
        return toItems(videoRpc.listByUser(targetUserId, page, pageSize));
    }

    @Override
    public List<VideoItemRespVO> listVideos(Integer tagId, int page, int pageSize) {
        PageWrapper<VideoDTO> wrapper = videoRpc.listVideos(tagId, QiyuRequestContext.getUserId(), page, pageSize);
        List<VideoItemRespVO> list = new ArrayList<>();
        for (VideoDTO dto : wrapper.getList()) {
            list.add(toItemVO(dto));
        }
        return list;
    }

    @Override
    public VideoDetailRespVO detail(Long videoId) {
        ErrorAssert.isNotNull(videoId, BizBaseErrorEnum.PARAM_ERROR);
        VideoDTO dto = videoRpc.detail(videoId, QiyuRequestContext.getUserId());
        // null = 视频不存在/转码未完成/审核中仅作者可见 —— 属正常业务态，不能报"参数异常"误导用户
        if (dto == null) {
            throw new QiyuErrorException(ApiErrorEnum.VIDEO_NOT_EXIST);
        }
        // 打开详情即计一次播放
        videoRpc.incPlayCount(videoId);
        dto.setPlayCount(dto.getPlayCount() == null ? 1 : dto.getPlayCount() + 1);

        VideoDetailRespVO vo = new VideoDetailRespVO();
        vo.setItem(toItemVO(dto));
        return vo;
    }

    @Override
    public Boolean like(Long videoId, boolean isLike) {
        ErrorAssert.isNotNull(videoId, BizBaseErrorEnum.PARAM_ERROR);
        return videoRpc.like(videoId, QiyuRequestContext.getUserId(), isLike);
    }

    @Override
    public Boolean favorite(Long videoId, boolean isFavorite) {
        ErrorAssert.isNotNull(videoId, BizBaseErrorEnum.PARAM_ERROR);
        return videoRpc.favorite(videoId, QiyuRequestContext.getUserId(), isFavorite);
    }

    @Override
    public void recordHistory(Long videoId) {
        ErrorAssert.isNotNull(videoId, BizBaseErrorEnum.PARAM_ERROR);
        videoRpc.recordHistory(QiyuRequestContext.getUserId(), videoId);
    }

    @Override
    public List<VideoItemRespVO> listHistory(int page, int pageSize) {
        return toItems(videoRpc.listHistory(QiyuRequestContext.getUserId(), page, pageSize));
    }

    @Override
    public List<VideoItemRespVO> listMyVideos(int page, int pageSize) {
        return toItems(videoRpc.listByUser(QiyuRequestContext.getUserId(), page, pageSize));
    }

    @Override
    public List<VideoItemRespVO> listMyFavorites(int page, int pageSize) {
        return toItems(videoRpc.listByAction(QiyuRequestContext.getUserId(), 2, page, pageSize));
    }

    @Override
    public List<VideoItemRespVO> listMyLikes(int page, int pageSize) {
        return toItems(videoRpc.listByAction(QiyuRequestContext.getUserId(), 1, page, pageSize));
    }

    private List<VideoItemRespVO> toItems(PageWrapper<VideoDTO> wrapper) {
        List<VideoItemRespVO> list = new ArrayList<>();
        for (VideoDTO dto : wrapper.getList()) {
            list.add(toItemVO(dto));
        }
        return list;
    }

    @Override
    public void share(Long videoId) {
        ErrorAssert.isNotNull(videoId, BizBaseErrorEnum.PARAM_ERROR);
        videoRpc.incShareCount(videoId);
    }

    @Override
    public List<VideoTagRespVO> listTags() {
        List<VideoTagRespVO> list = new ArrayList<>();
        for (TagDTO tag : videoRpc.listTags()) {
            VideoTagRespVO vo = new VideoTagRespVO();
            vo.setId(tag.getId());
            vo.setTagName(tag.getTagName());
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<VideoCommentRespVO> listComments(Long videoId, int page, int pageSize) {
        List<VideoCommentRespVO> list = new ArrayList<>();
        for (VideoCommentDTO dto : videoRpc.listComments(videoId, page, pageSize)) {
            VideoCommentRespVO vo = new VideoCommentRespVO();
            vo.setId(dto.getId());
            vo.setVideoId(dto.getVideoId());
            vo.setUserId(dto.getUserId());
            vo.setNickName(dto.getNickName());
            vo.setAvatar(dto.getAvatar());
            vo.setContent(dto.getContent());
            vo.setCreateTime(dto.getCreateTime() == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(dto.getCreateTime()));
            list.add(vo);
        }
        return list;
    }

    @Override
    public VideoCommentRespVO addComment(Long videoId, String content) {
        ErrorAssert.isNotNull(videoId, BizBaseErrorEnum.PARAM_ERROR);
        ErrorAssert.isTure(StringUtils.hasText(content), BizBaseErrorEnum.PARAM_ERROR);
        assertNotBlocked(content, RiskConstants.SCENE_COMMENT, QiyuRequestContext.getUserId());
        VideoCommentDTO dto = new VideoCommentDTO();
        dto.setVideoId(videoId);
        dto.setUserId(QiyuRequestContext.getUserId());
        dto.setContent(content);
        VideoCommentDTO added = videoRpc.addComment(dto);
        VideoCommentRespVO vo = new VideoCommentRespVO();
        vo.setId(added.getId());
        vo.setVideoId(added.getVideoId());
        vo.setUserId(added.getUserId());
        vo.setNickName(added.getNickName());
        vo.setAvatar(added.getAvatar());
        vo.setContent(added.getContent());
        vo.setCreateTime(added.getCreateTime() == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(added.getCreateTime()));
        return vo;
    }

    @Override
    public boolean deleteComment(Long commentId) {
        ErrorAssert.isNotNull(commentId, BizBaseErrorEnum.PARAM_ERROR);
        return videoRpc.deleteComment(commentId, QiyuRequestContext.getUserId());
    }

    private VideoItemRespVO toItemVO(VideoDTO dto) {
        VideoItemRespVO vo = new VideoItemRespVO();
        vo.setId(dto.getId());
        vo.setTranscodeStatus(dto.getTranscodeStatus());
        vo.setSize(dto.getSize());
        vo.setStatus(dto.getStatus());
        vo.setUserId(dto.getUserId());
        vo.setNickName(dto.getNickName());
        vo.setAvatar(dto.getAvatar());
        vo.setTitle(dto.getTitle());
        vo.setDescription(dto.getDescription());
        vo.setVideoUrl(dto.getVideoUrl());
        vo.setCoverUrl(dto.getCoverUrl());
        vo.setTagId(dto.getTagId());
        vo.setTagName(dto.getTagName());
        vo.setDuration(dto.getDuration());
        vo.setPlayCount(dto.getPlayCount());
        vo.setLikeCount(dto.getLikeCount());
        vo.setFavoriteCount(dto.getFavoriteCount());
        vo.setShareCount(dto.getShareCount());
        vo.setCommentCount(dto.getCommentCount());
        vo.setLiked(dto.getLiked());
        vo.setFavorited(dto.getFavorited());
        vo.setCreateTime(dto.getCreateTime() == null ? "" : new SimpleDateFormat("yyyy-MM-dd").format(dto.getCreateTime()));
        return vo;
    }

    private String resolveExt(String filename) {
        String ext = null;
        if (StringUtils.hasText(filename) && filename.contains(".")) {
            ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        ErrorAssert.isTure(ext != null && ALLOWED_VIDEO_EXT.contains(ext), BizBaseErrorEnum.PARAM_ERROR);
        return ext;
    }
}
