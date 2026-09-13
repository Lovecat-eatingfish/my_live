package org.qiyu.live.msg.provider.service.risk;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.idea.qiyu.live.framework.redis.starter.key.MsgProviderCacheKeyBuilder;
import org.qiyu.live.common.interfaces.constants.RiskConstants;
import org.qiyu.live.common.interfaces.dto.RiskCheckRespDTO;
import org.qiyu.live.msg.provider.dao.mapper.RiskSensitiveWordMapper;
import org.qiyu.live.msg.provider.dao.po.RiskSensitiveWordPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 内容风控核心：DFA 自动机敏感词检测 + 弹幕频率风控
 * <p>
 * 词库热更新：Redis 版本号懒重建——每次检测先 GET 版本（一次 redis 读），
 * 版本变化（后台增删词时 INCR）才从 DB 重新加载构建自动机，无需重启服务。
 */
@Component
public class RiskCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RiskCheckService.class);

    @Resource
    private RiskSensitiveWordMapper riskSensitiveWordMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private MsgProviderCacheKeyBuilder msgProviderCacheKeyBuilder;

    /** scene -> DFA 根节点（scene=0 的全场景词合并进每个场景树） */
    private volatile Map<Integer, Node> sceneRoots = new HashMap<>();
    private volatile String cachedVersion;
    private final Object reloadLock = new Object();

    /**
     * 敏感词检测：任一命中 level=1 → blocked；命中 level=2 → 返回替换文本；level=3 仅记录后放行
     */
    public RiskCheckRespDTO checkText(String text, int scene) {
        if (text == null || text.isEmpty()) {
            return RiskCheckRespDTO.pass();
        }
        Node root = ensureLoaded().get(scene);
        if (root == null) {
            return RiskCheckRespDTO.pass();
        }
        List<String> hitWords = new ArrayList<>();
        List<int[]> replaceRanges = new ArrayList<>(); // [start, end)
        List<String> replaceWords = new ArrayList<>();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            Node node = root;
            int j = i;
            Node longest = null;
            int longestEnd = -1;
            while (j < len && node.children.containsKey(text.charAt(j))) {
                node = node.children.get(text.charAt(j));
                j++;
                if (node.level > 0) {
                    if (node.level == RiskConstants.LEVEL_BLOCK) {
                        // 路径上出现任一拦截级命中即拦截（比更长路径上的替换级优先）
                        hitWords.add(text.substring(i, j));
                        return RiskCheckRespDTO.blocked(hitWords);
                    }
                    if (j - i > longestEnd - i) {
                        longest = node;
                        longestEnd = j;
                    }
                }
            }
            if (longest != null) {
                hitWords.add(text.substring(i, longestEnd));
                if (longest.level == RiskConstants.LEVEL_REPLACE) {
                    replaceRanges.add(new int[]{i, longestEnd});
                    replaceWords.add(text.substring(i, longestEnd));
                }
                i = longestEnd - 1; // 命中段整体跳过
            }
        }
        if (!hitWords.isEmpty()) {
            LOGGER.info("[checkText] scene={}, hitWords={}", scene, hitWords);
        }
        if (replaceRanges.isEmpty()) {
            return RiskCheckRespDTO.pass();
        }
        StringBuilder sb = new StringBuilder(text);
        for (int k = replaceRanges.size() - 1; k >= 0; k--) {
            int[] range = replaceRanges.get(k);
            for (int p = range[0]; p < range[1]; p++) {
                sb.setCharAt(p, '*');
            }
        }
        return RiskCheckRespDTO.replaced(sb.toString(), hitWords);
    }

    /**
     * 弹幕频率风控：固定窗口计数（默认 5s 内最多 10 条），超限返回 false 由调用方静默丢弃
     */
    public boolean checkDanmuFreq(Long userId) {
        String key = msgProviderCacheKeyBuilder.buildDanmuFreqKey(userId);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1) {
                redisTemplate.expire(key, RiskConstants.DANMU_FREQ_WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            if (count > RiskConstants.DANMU_FREQ_LIMIT) {
                LOGGER.warn("[checkDanmuFreq] userId={} freq={} over limit, drop", userId, count);
                return false;
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("[checkDanmuFreq] redis error, userId={}", userId, e);
            return true; // redis 故障时放行，不阻塞正常聊天
        }
    }

    /** 词库变更后使缓存失效（下次检测时重建） */
    public void invalidate() {
        cachedVersion = null;
    }

    /** 词库变更：版本号 INCR + 缓存失效 */
    public void bumpVersion() {
        try {
            redisTemplate.opsForValue().increment(msgProviderCacheKeyBuilder.buildRiskWordVersionKey());
        } catch (Exception e) {
            LOGGER.error("[bumpVersion] redis error", e);
        }
        invalidate();
    }

    private Map<Integer, Node> ensureLoaded() {
        String version;
        try {
            Object v = redisTemplate.opsForValue().get(msgProviderCacheKeyBuilder.buildRiskWordVersionKey());
            version = v == null ? "0" : String.valueOf(v);
        } catch (Exception e) {
            LOGGER.error("[ensureLoaded] redis error, use cached words", e);
            return sceneRoots;
        }
        if (version.equals(cachedVersion) && !sceneRoots.isEmpty()) {
            return sceneRoots;
        }
        synchronized (reloadLock) {
            if (version.equals(cachedVersion) && !sceneRoots.isEmpty()) {
                return sceneRoots;
            }
            try {
                LambdaQueryWrapper<RiskSensitiveWordPO> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(RiskSensitiveWordPO::getStatus, 1);
                List<RiskSensitiveWordPO> words = riskSensitiveWordMapper.selectList(wrapper);
                Map<Integer, Node> roots = buildSceneRoots(words);
                sceneRoots = roots;
                cachedVersion = version;
                LOGGER.info("[ensureLoaded] risk words reloaded, version={}, count={}", version, words.size());
            } catch (Exception e) {
                LOGGER.error("[ensureLoaded] load risk words failed, keep old cache", e);
            }
        }
        return sceneRoots;
    }

    private Map<Integer, Node> buildSceneRoots(List<RiskSensitiveWordPO> words) {
        Map<Integer, Node> roots = new HashMap<>();
        if (CollectionUtils.isEmpty(words)) {
            return roots;
        }
        for (RiskSensitiveWordPO word : words) {
            int[] scenes = word.getScene() == RiskConstants.SCENE_ALL
                    ? new int[]{RiskConstants.SCENE_DANMU, RiskConstants.SCENE_NICKNAME,
                       RiskConstants.SCENE_VIDEO_TITLE, RiskConstants.SCENE_COMMENT, RiskConstants.SCENE_ROOM_NAME}
                    : new int[]{word.getScene()};
            for (int scene : scenes) {
                Node root = roots.computeIfAbsent(scene, k -> new Node());
                insertWord(root, word.getWord(), word.getLevel() == null ? RiskConstants.LEVEL_BLOCK : word.getLevel());
            }
        }
        return roots;
    }

    private void insertWord(Node root, String word, int level) {
        Node node = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            node = node.children.computeIfAbsent(c, k -> new Node());
        }
        // 同一词多场景时取最严级别（1拦截 > 2替换 > 3记录）
        node.level = node.level == 0 ? level : Math.min(node.level, level);
    }

    private static class Node {
        private final Map<Character, Node> children = new HashMap<>();
        /** 0=非词尾，否则为该词级别 */
        private int level;
    }
}
