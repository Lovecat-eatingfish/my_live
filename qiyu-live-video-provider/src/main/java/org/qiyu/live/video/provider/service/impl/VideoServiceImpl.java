package org.qiyu.live.video.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;
import org.qiyu.live.common.interfaces.dto.VideoTranscodeMqDTO;
import org.qiyu.live.common.interfaces.topic.VideoProviderTopicNames;
import org.qiyu.live.video.provider.dao.maper.IVideoPlayLogMapper;
import org.qiyu.live.video.provider.dao.po.VideoPlayLogPO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.interfaces.IUserRpc;
import org.qiyu.live.video.dto.VideoCommentDTO;
import org.qiyu.live.video.dto.VideoDTO;
import org.qiyu.live.video.interfaces.rpc.TagDTO;
import org.qiyu.live.video.provider.dao.maper.IVideoCommentMapper;
import org.qiyu.live.video.provider.dao.maper.IVideoInfoMapper;
import org.qiyu.live.video.provider.dao.maper.IVideoTagMapper;
import org.qiyu.live.video.provider.dao.maper.IVideoUserActionMapper;
import org.qiyu.live.video.provider.dao.po.VideoCommentPO;
import org.qiyu.live.video.provider.dao.po.VideoInfoPO;
import org.qiyu.live.video.provider.dao.po.VideoTagPO;
import org.qiyu.live.video.provider.dao.po.VideoUserActionPO;
import org.qiyu.live.video.provider.service.IVideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 视频服务实现
 */
@Service
public class VideoServiceImpl implements IVideoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoServiceImpl.class);

    private static final int STATUS_ONLINE = 1;
    /** 审核中（发布默认态，admin 通过置 1 / 驳回置 3） */
    private static final int STATUS_REVIEW = 2;
    private static final int STATUS_DELETED = 0;
    private static final int PAGE_SIZE_MAX = 50;

    @Resource
    private IVideoInfoMapper videoInfoMapper;

    @Resource
    private MQProducer mqProducer;
    @Resource
    private IVideoPlayLogMapper videoPlayLogMapper;
    @Resource
    private IVideoTagMapper videoTagMapper;
    @Resource
    private IVideoUserActionMapper videoUserActionMapper;
    @Resource
    private IVideoCommentMapper videoCommentMapper;
    @Resource
    private org.qiyu.live.video.provider.dao.maper.IVideoWatchHistoryMapper watchHistoryMapper;

    @DubboReference(check = false)
    private IUserRpc userRpc;

    @DubboReference(check = false)
    private org.qiyu.live.user.interfaces.rpc.INotifyRpc notifyRpc;

    @Override
    public VideoDTO publish(VideoDTO videoDTO) {
        VideoInfoPO po = new VideoInfoPO();
        po.setUserId(videoDTO.getUserId());
        po.setTitle(videoDTO.getTitle());
        po.setDescription(videoDTO.getDescription() == null ? "" : videoDTO.getDescription());
        po.setVideoUrl(videoDTO.getVideoUrl());
        po.setCoverUrl(videoDTO.getCoverUrl() == null ? "" : videoDTO.getCoverUrl());
        po.setTagId(videoDTO.getTagId() == null ? 0 : videoDTO.getTagId());
        po.setDuration(videoDTO.getDuration() == null ? 0 : videoDTO.getDuration());
        po.setSize(videoDTO.getSize() == null ? 0 : videoDTO.getSize());
        // 审核流：发布默认进入"审核中"(2)，admin 审核通过置 1 上线 / 驳回置 3
        po.setStatus(STATUS_REVIEW);
        // 发布即置"处理中"，转码完成(1)/失败(2)后可见；发布立即返回
        po.setTranscodeStatus(0);
        videoInfoMapper.insert(po);
        try {
            VideoTranscodeMqDTO mqDTO = VideoTranscodeMqDTO.of(po.getId(), videoDTO.getUserId());
            mqProducer.send(new Message(VideoProviderTopicNames.VIDEO_TRANSCODE_TOPIC,
                    com.alibaba.fastjson.JSON.toJSONBytes(mqDTO)));
        } catch (Exception e) {
            // 转码任务投递失败不阻塞发布：置失败态回退播原文件
            org.slf4j.LoggerFactory.getLogger(VideoServiceImpl.class)
                    .error("[publish] send transcode mq error, videoId={}", po.getId(), e);
            VideoInfoPO failed = new VideoInfoPO();
            failed.setId(po.getId());
            failed.setTranscodeStatus(2);
            videoInfoMapper.updateById(failed);
        }
        // 不走 detail（detail 排除"处理中"），直接转 DTO 返回
        List<VideoDTO> dtoList = enrich(Collections.singletonList(po), videoDTO.getUserId());
        return dtoList.isEmpty() ? null : dtoList.get(0);
    }

    @Override
    public PageWrapper<VideoDTO> listVideos(Integer tagId, Long viewerUserId, int page, int pageSize) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), PAGE_SIZE_MAX);
        LambdaQueryWrapper<VideoInfoPO> qw = new LambdaQueryWrapper<VideoInfoPO>()
                .eq(VideoInfoPO::getStatus, STATUS_ONLINE)
                .ne(VideoInfoPO::getTranscodeStatus, 0)
                .eq(tagId != null && tagId > 0, VideoInfoPO::getTagId, tagId)
                .orderByDesc(VideoInfoPO::getId);
        Page<VideoInfoPO> poPage = videoInfoMapper.selectPage(new Page<>(page, pageSize), qw);

        PageWrapper<VideoDTO> wrapper = new PageWrapper<>();
        wrapper.setList(enrich(poPage.getRecords(), viewerUserId));
        wrapper.setHasNext(poPage.getCurrent() * poPage.getSize() < poPage.getTotal());
        return wrapper;
    }

    @Override
    public PageWrapper<VideoDTO> searchVideos(String keyword, Long viewerUserId, int page, int pageSize) {
        PageWrapper<VideoDTO> wrapper = new PageWrapper<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            wrapper.setList(java.util.Collections.emptyList());
            return wrapper;
        }
        LambdaQueryWrapper<VideoInfoPO> qw = new LambdaQueryWrapper<VideoInfoPO>()
                .eq(VideoInfoPO::getStatus, STATUS_ONLINE)
                .ne(VideoInfoPO::getTranscodeStatus, 0)
                .like(VideoInfoPO::getTitle, keyword.trim())
                .orderByDesc(VideoInfoPO::getId);
        Page<VideoInfoPO> poPage = videoInfoMapper.selectPage(new Page<>(page, pageSize), qw);
        wrapper.setList(enrich(poPage.getRecords(), viewerUserId));
        wrapper.setHasNext(poPage.getRecords().size() == pageSize);
        return wrapper;
    }

    @Override
    public PageWrapper<VideoDTO> feed(Long lastId, Long viewerUserId, int size) {
        size = Math.min(Math.max(size, 1), 20);
        PageWrapper<VideoDTO> wrapper = new PageWrapper<>();
        QueryWrapper<VideoInfoPO> qw = new QueryWrapper<>();
        qw.eq("status", STATUS_ONLINE).ne("transcode_status", 0);
        if (lastId != null && lastId > 0) {
            // 游标：以上一页最后一条的热度分为界（同分看 id）；热度物化在 heat_score 冗余列，可走索引
            VideoInfoPO last = videoInfoMapper.selectById(lastId);
            if (last != null) {
                double lastScore = heatScore(last);
                qw.apply("((heat_score < {0}" +
                        " OR (heat_score = {0} AND id < {1})))", lastScore, lastId);
            }
        }
        qw.orderByDesc("heat_score", "id");
        Page<VideoInfoPO> poPage = videoInfoMapper.selectPage(new Page<>(1, size), qw);
        wrapper.setList(enrich(poPage.getRecords(), viewerUserId));
        wrapper.setHasNext(poPage.getRecords().size() == size);
        return wrapper;
    }

    private double heatScore(VideoInfoPO po) {
        // 读取物化列；历史数据/异常时回退公式
        if (po.getHeatScore() != null) {
            return po.getHeatScore();
        }
        long play = po.getPlayCount() == null ? 0 : po.getPlayCount();
        long like = po.getLikeCount() == null ? 0 : po.getLikeCount();
        return play * 0.4 + like * 0.3;
    }

    @Override
    public PageWrapper<VideoDTO> listRelated(Long videoId, Long viewerUserId, int size) {
        size = Math.min(Math.max(size, 1), 20);
        PageWrapper<VideoDTO> wrapper = new PageWrapper<>();
        VideoInfoPO po = videoInfoMapper.selectById(videoId);
        if (po == null) {
            wrapper.setList(Collections.emptyList());
            return wrapper;
        }
        // 同标签上架视频（无标签则同作者），排除自身，id 倒序
        LambdaQueryWrapper<VideoInfoPO> qw = new LambdaQueryWrapper<VideoInfoPO>()
                .eq(VideoInfoPO::getStatus, STATUS_ONLINE)
                .ne(VideoInfoPO::getTranscodeStatus, 0)
                .ne(VideoInfoPO::getId, videoId)
                .eq(po.getTagId() != null && po.getTagId() > 0,
                        VideoInfoPO::getTagId, po.getTagId() == null ? 0 : po.getTagId())
                .eq(po.getTagId() == null || po.getTagId() <= 0, VideoInfoPO::getUserId, po.getUserId())
                .orderByDesc(VideoInfoPO::getId);
        qw.last("LIMIT " + size);
        wrapper.setList(enrich(videoInfoMapper.selectList(qw), viewerUserId));
        return wrapper;
    }

    @Override
    public void playReport(Long videoId, Long userId, int watchedSeconds, int duration) {
        VideoPlayLogPO log = new VideoPlayLogPO();
        log.setVideoId(videoId);
        log.setUserId(userId);
        log.setWatchedSeconds(Math.max(watchedSeconds, 0));
        log.setDuration(Math.max(duration, 0));
        log.setIsComplete(duration > 0 && watchedSeconds >= duration * 0.9 ? 1 : 0);
        log.setCreateTime(new java.util.Date());
        videoPlayLogMapper.insert(log);
    }

    @Override
    public VideoDTO detail(Long videoId, Long viewerUserId) {
        VideoInfoPO po = videoInfoMapper.selectById(videoId);
        if (po == null || Integer.valueOf(0).equals(po.getTranscodeStatus())) {
            return null;
        }
        // 上架视频所有人可见；审核中/驳回/下架仅作者本人可见
        if (po.getStatus() != STATUS_ONLINE && !po.getUserId().equals(viewerUserId)) {
            return null;
        }
        List<VideoDTO> dtoList = enrich(Collections.singletonList(po), viewerUserId);
        return dtoList.get(0);
    }

    @Override
    public boolean incPlayCount(Long videoId) {
        return videoInfoMapper.update(null, new LambdaUpdateWrapper<VideoInfoPO>()
                .eq(VideoInfoPO::getId, videoId)
                .setSql("play_count = play_count + 1, heat_score = heat_score + 0.4")) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean like(Long videoId, Long userId, boolean isLike) {
        boolean result = toggleAction(videoId, userId, VideoUserActionPO.ACTION_LIKE, "like_count", isLike);
        // 点赞/取消点赞同步维护热度冗余列（favorite 不参与热度公式，不在此调整）
        if (result) {
            videoInfoMapper.update(null, new LambdaUpdateWrapper<VideoInfoPO>()
                    .eq(VideoInfoPO::getId, videoId)
                    .setSql(isLike ? "heat_score = heat_score + 0.3"
                            : "heat_score = GREATEST(heat_score - 0.3, 0)"));
        }
        // 互动通知：首次点赞时告诉视频作者（失败不影响点赞）
        if (result && isLike) {
            try {
                VideoInfoPO video = videoInfoMapper.selectById(videoId);
                if (video != null && video.getUserId() != null && !video.getUserId().equals(userId)) {
                    org.qiyu.live.user.dto.UserDTO liker = userRpc.getByUserId(userId);
                    notifyRpc.sendNotify(org.qiyu.live.user.dto.UserNotifyDTO.of(video.getUserId(), 2,
                            "收到新的点赞",
                            (liker == null || liker.getNickName() == null ? "用户" + userId : liker.getNickName())
                                    + " 赞了你的视频《" + video.getTitle() + "》",
                            "/video/" + videoId));
                }
            } catch (Exception e) {
                LOGGER.error("[like] send notify error, videoId={}, userId={}", videoId, userId, e);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean favorite(Long videoId, Long userId, boolean isFavorite) {
        return toggleAction(videoId, userId, VideoUserActionPO.ACTION_FAVORITE, "favorite_count", isFavorite);
    }

    private boolean toggleAction(Long videoId, Long userId, int actionType, String countColumn, boolean enable) {
        boolean exists = videoUserActionMapper.selectCount(new LambdaQueryWrapper<VideoUserActionPO>()
                .eq(VideoUserActionPO::getVideoId, videoId)
                .eq(VideoUserActionPO::getUserId, userId)
                .eq(VideoUserActionPO::getActionType, actionType)) > 0;
        if (enable && !exists) {
            VideoUserActionPO po = new VideoUserActionPO();
            po.setVideoId(videoId);
            po.setUserId(userId);
            po.setActionType(actionType);
            po.setCreateTime(new Date());
            try {
                videoUserActionMapper.insert(po);
            } catch (Exception e) {
                // 唯一键冲突视为已点过，按幂等处理
                LOGGER.warn("[toggleAction] duplicate action, videoId={}, userId={}, type={}", videoId, userId, actionType);
                return true;
            }
            videoInfoMapper.update(null, new LambdaUpdateWrapper<VideoInfoPO>()
                    .eq(VideoInfoPO::getId, videoId)
                    .setSql(countColumn + " = " + countColumn + " + 1"));
            return true;
        }
        if (!enable && exists) {
            videoUserActionMapper.delete(new LambdaQueryWrapper<VideoUserActionPO>()
                    .eq(VideoUserActionPO::getVideoId, videoId)
                    .eq(VideoUserActionPO::getUserId, userId)
                    .eq(VideoUserActionPO::getActionType, actionType));
            videoInfoMapper.update(null, new LambdaUpdateWrapper<VideoInfoPO>()
                    .eq(VideoInfoPO::getId, videoId)
                    .setSql(countColumn + " = GREATEST(" + countColumn + " - 1, 0)"));
            return false;
        }
        return exists;
    }

    @Override
    public boolean incShareCount(Long videoId) {
        return videoInfoMapper.update(null, new LambdaUpdateWrapper<VideoInfoPO>()
                .eq(VideoInfoPO::getId, videoId)
                .setSql("share_count = share_count + 1")) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoCommentDTO addComment(VideoCommentDTO commentDTO) {
        VideoCommentPO po = new VideoCommentPO();
        po.setVideoId(commentDTO.getVideoId());
        po.setUserId(commentDTO.getUserId());
        po.setContent(commentDTO.getContent());
        po.setStatus(STATUS_ONLINE);
        po.setCreateTime(new Date());
        videoCommentMapper.insert(po);
        videoInfoMapper.update(null, new LambdaUpdateWrapper<VideoInfoPO>()
                .eq(VideoInfoPO::getId, po.getVideoId())
                .setSql("comment_count = comment_count + 1"));
        VideoCommentDTO dto = new VideoCommentDTO();
        BeanUtils.copyProperties(po, dto);
        UserDTO user = userRpc.getByUserId(po.getUserId());
        if (user != null) {
            dto.setNickName(user.getNickName());
            dto.setAvatar(user.getAvatar());
        }
        return dto;
    }

    @Override
    public List<VideoCommentDTO> listComments(Long videoId, int page, int pageSize) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), PAGE_SIZE_MAX);
        List<VideoCommentPO> poList = videoCommentMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<VideoCommentPO>()
                        .eq(VideoCommentPO::getVideoId, videoId)
                        .eq(VideoCommentPO::getStatus, STATUS_ONLINE)
                        .orderByDesc(VideoCommentPO::getId)).getRecords();
        if (poList.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量补齐评论人昵称头像
        List<Long> userIds = poList.stream().map(VideoCommentPO::getUserId).distinct().collect(Collectors.toList());
        Map<Long, UserDTO> userMap = userRpc.batchQueryUserInfo(userIds);
        return poList.stream().map(po -> {
            VideoCommentDTO dto = new VideoCommentDTO();
            BeanUtils.copyProperties(po, dto);
            UserDTO user = userMap.get(po.getUserId());
            if (user != null) {
                dto.setNickName(user.getNickName());
                dto.setAvatar(user.getAvatar());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteComment(Long commentId, Long userId) {
        VideoCommentPO po = videoCommentMapper.selectById(commentId);
        if (po == null || po.getStatus() == STATUS_DELETED) {
            return false;
        }
        // 只有本人能删自己的评论
        if (!Objects.equals(po.getUserId(), userId)) {
            return false;
        }
        videoCommentMapper.update(null, new LambdaUpdateWrapper<VideoCommentPO>()
                .eq(VideoCommentPO::getId, commentId)
                .set(VideoCommentPO::getStatus, STATUS_DELETED));
        videoInfoMapper.update(null, new LambdaUpdateWrapper<VideoInfoPO>()
                .eq(VideoInfoPO::getId, po.getVideoId())
                .setSql("comment_count = GREATEST(comment_count - 1, 0)"));
        return true;
    }

    @Override
    public List<TagDTO> listTags() {
        return videoTagMapper.selectList(new LambdaQueryWrapper<VideoTagPO>()
                        .eq(VideoTagPO::getStatus, STATUS_ONLINE)
                        .orderByAsc(VideoTagPO::getSort))
                .stream()
                .map(po -> new TagDTO(po.getId(), po.getTagName()))
                .collect(Collectors.toList());
    }

    @Override
    public PageWrapper<VideoDTO> adminListVideos(int page, int pageSize) {
        Page<VideoInfoPO> poPage = videoInfoMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(pageSize, 1), PAGE_SIZE_MAX)),
                new LambdaQueryWrapper<VideoInfoPO>().ne(VideoInfoPO::getStatus, STATUS_DELETED)
                        .orderByDesc(VideoInfoPO::getId));
        PageWrapper<VideoDTO> wrapper = new PageWrapper<>();
        wrapper.setList(enrich(poPage.getRecords(), null));
        wrapper.setHasNext(poPage.getCurrent() * poPage.getSize() < poPage.getTotal());
        return wrapper;
    }

    @Override
    public PageWrapper<VideoDTO> adminReviewList(int page, int pageSize) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), PAGE_SIZE_MAX);
        LambdaQueryWrapper<VideoInfoPO> qw = new LambdaQueryWrapper<VideoInfoPO>()
                .eq(VideoInfoPO::getStatus, STATUS_REVIEW)
                .orderByAsc(VideoInfoPO::getId);
        Page<VideoInfoPO> poPage = videoInfoMapper.selectPage(new Page<>(page, pageSize), qw);
        PageWrapper<VideoDTO> wrapper = new PageWrapper<>();
        wrapper.setList(enrich(poPage.getRecords(), null));
        wrapper.setHasNext(poPage.getRecords().size() == pageSize);
        return wrapper;
    }

    public boolean setVideoStatus(Long videoId, int status) {
        VideoInfoPO po = new VideoInfoPO();
        po.setId(videoId);
        po.setStatus(status);
        return videoInfoMapper.updateById(po) > 0;
    }

    @Override
    public Integer addTag(String tagName) {
        VideoTagPO po = new VideoTagPO();
        po.setTagName(tagName);
        po.setSort(99);
        po.setStatus(STATUS_ONLINE);
        videoTagMapper.insert(po);
        return po.getId();
    }

    @Override
    public boolean renameTag(Integer tagId, String tagName) {
        VideoTagPO po = new VideoTagPO();
        po.setId(tagId);
        po.setTagName(tagName);
        return videoTagMapper.updateById(po) > 0;
    }

    @Override
    public boolean deleteTag(Integer tagId) {
        VideoTagPO po = new VideoTagPO();
        po.setId(tagId);
        po.setStatus(STATUS_DELETED);
        return videoTagMapper.updateById(po) > 0;
    }

    @Override
    public void recordHistory(Long userId, Long videoId) {
        org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO exist = watchHistoryMapper.selectOne(
                new LambdaQueryWrapper<org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO>()
                        .eq(org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO::getUserId, userId)
                        .eq(org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO::getVideoId, videoId)
                        .last("limit 1"));
        if (exist != null) {
            org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO update = new org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO();
            update.setId(exist.getId());
            update.setWatchTime(new java.util.Date());
            watchHistoryMapper.updateById(update);
        } else {
            org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO po = new org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO();
            po.setUserId(userId);
            po.setVideoId(videoId);
            watchHistoryMapper.insert(po);
        }
    }

    @Override
    public PageWrapper<VideoDTO> listHistory(Long userId, int page, int pageSize) {
        // 按最近观看时间排序的 id 分页，再按原顺序装载视频
        Page<org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO> historyPage = watchHistoryMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(pageSize, 1), PAGE_SIZE_MAX)),
                new LambdaQueryWrapper<org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO>()
                        .eq(org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO::getUserId, userId)
                        .orderByDesc(org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO::getWatchTime));
        List<Long> videoIds = historyPage.getRecords().stream()
                .map(org.qiyu.live.video.provider.dao.po.VideoWatchHistoryPO::getVideoId).collect(Collectors.toList());
        return wrapOrdered(videoIds, userId, historyPage);
    }

    @Override
    public PageWrapper<VideoDTO> listByUser(Long userId, int page, int pageSize) {
        LambdaQueryWrapper<VideoInfoPO> qw = new LambdaQueryWrapper<VideoInfoPO>()
                .eq(VideoInfoPO::getUserId, userId)
                .ne(VideoInfoPO::getStatus, STATUS_DELETED)
                .orderByDesc(VideoInfoPO::getId);
        Page<VideoInfoPO> poPage = videoInfoMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(pageSize, 1), PAGE_SIZE_MAX)), qw);
        PageWrapper<VideoDTO> wrapper = new PageWrapper<>();
        wrapper.setList(enrich(poPage.getRecords(), userId));
        wrapper.setHasNext(poPage.getCurrent() * poPage.getSize() < poPage.getTotal());
        return wrapper;
    }

    @Override
    public PageWrapper<VideoDTO> listByAction(Long userId, int actionType, int page, int pageSize) {
        Page<VideoUserActionPO> actionPage = videoUserActionMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(pageSize, 1), PAGE_SIZE_MAX)),
                new LambdaQueryWrapper<VideoUserActionPO>()
                        .eq(VideoUserActionPO::getUserId, userId)
                        .eq(VideoUserActionPO::getActionType, actionType)
                        .orderByDesc(VideoUserActionPO::getId));
        List<Long> videoIds = actionPage.getRecords().stream()
                .map(VideoUserActionPO::getVideoId).collect(Collectors.toList());
        return wrapOrdered(videoIds, userId, actionPage);
    }

    /** 按给定 id 顺序装载视频（跳过已删除），带作者/标签/点赞收藏状态 */
    private PageWrapper<VideoDTO> wrapOrdered(List<Long> videoIds, Long viewerUserId, com.baomidou.mybatisplus.extension.plugins.pagination.Page<?> page) {
        PageWrapper<VideoDTO> wrapper = new PageWrapper<>();
        if (videoIds.isEmpty()) {
            wrapper.setList(Collections.emptyList());
            wrapper.setHasNext(false);
            return wrapper;
        }
        Map<Long, VideoInfoPO> poMap = videoInfoMapper.selectBatchIds(videoIds).stream()
                .filter(po -> po.getStatus() == STATUS_ONLINE)
                .collect(Collectors.toMap(VideoInfoPO::getId, po -> po, (a, b) -> a));
        List<VideoInfoPO> ordered = videoIds.stream().map(poMap::get).filter(Objects::nonNull).collect(Collectors.toList());
        wrapper.setList(enrich(ordered, viewerUserId));
        wrapper.setHasNext(page.getCurrent() * page.getSize() < page.getTotal());
        return wrapper;
    }

    /**
     * PO 列表转 DTO 并补齐：作者昵称/头像、标签名、当前用户点赞/收藏状态
     */
    private List<VideoDTO> enrich(List<VideoInfoPO> poList, Long viewerUserId) {
        if (poList == null || poList.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = poList.stream().map(VideoInfoPO::getUserId).distinct().collect(Collectors.toList());
        Map<Long, UserDTO> userMap = userRpc.batchQueryUserInfo(userIds);

        Set<Integer> tagIds = poList.stream().map(VideoInfoPO::getTagId)
                .filter(id -> id != null && id > 0).collect(Collectors.toSet());
        Map<Integer, String> tagNameMap = tagIds.isEmpty() ? Collections.emptyMap()
                : videoTagMapper.selectList(new LambdaQueryWrapper<VideoTagPO>()
                        .in(VideoTagPO::getId, tagIds))
                .stream()
                .collect(Collectors.toMap(VideoTagPO::getId, VideoTagPO::getTagName, (a, b) -> a));

        Map<Long, Set<Integer>> viewerActions = Collections.emptyMap();
        if (viewerUserId != null) {
            viewerActions = videoUserActionMapper.selectList(new LambdaQueryWrapper<VideoUserActionPO>()
                            .eq(VideoUserActionPO::getUserId, viewerUserId)
                            .in(VideoUserActionPO::getVideoId, poList.stream().map(VideoInfoPO::getId).collect(Collectors.toList())))
                    .stream()
                    .collect(Collectors.groupingBy(VideoUserActionPO::getVideoId,
                            Collectors.mapping(VideoUserActionPO::getActionType, Collectors.toSet())));
        }

        List<VideoDTO> dtoList = new ArrayList<>(poList.size());
        for (VideoInfoPO po : poList) {
            VideoDTO dto = new VideoDTO();
            BeanUtils.copyProperties(po, dto);
            UserDTO user = userMap.get(po.getUserId());
            if (user != null) {
                dto.setNickName(user.getNickName());
                dto.setAvatar(user.getAvatar());
            }
            if (po.getTagId() != null && tagNameMap.containsKey(po.getTagId())) {
                dto.setTagName(tagNameMap.get(po.getTagId()));
            }
            Set<Integer> actions = viewerActions.getOrDefault(po.getId(), Collections.emptySet());
            dto.setLiked(actions.contains(VideoUserActionPO.ACTION_LIKE));
            dto.setFavorited(actions.contains(VideoUserActionPO.ACTION_FAVORITE));
            dtoList.add(dto);
        }
        return dtoList;
    }
    @Override
    public boolean retryTranscode(Long videoId) {
        VideoInfoPO po = videoInfoMapper.selectById(videoId);
        // 不存在、或转码已完成(1)的视频无需重试
        if (po == null || po.getTranscodeStatus() != null && po.getTranscodeStatus() == 1) {
            return false;
        }
        try {
            VideoTranscodeMqDTO mqDTO = VideoTranscodeMqDTO.of(po.getId(), po.getUserId());
            mqProducer.send(new Message(VideoProviderTopicNames.VIDEO_TRANSCODE_TOPIC,
                    com.alibaba.fastjson.JSON.toJSONBytes(mqDTO)));
        } catch (Exception e) {
            LOGGER.error("[retryTranscode] send mq error, videoId={}", videoId, e);
            return false;
        }
        // 投递成功：状态回"处理中"，前端立即可见；失败仍由消费者 markFailed 兜底
        VideoInfoPO update = new VideoInfoPO();
        update.setId(videoId);
        update.setTranscodeStatus(0);
        videoInfoMapper.updateById(update);
        LOGGER.info("[retryTranscode] requeued, videoId={}", videoId);
        return true;
    }
}
