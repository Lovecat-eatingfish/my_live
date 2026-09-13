package org.qiyu.live.common.interfaces.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 敏感词检测结果
 * pass=true 直接放行；blocked=true 拦截；否则若 replacedText 非空则为替换后放行文本
 */
public class RiskCheckRespDTO implements Serializable {

    private boolean pass;
    private boolean blocked;
    /** 命中 level=2 时返回替换后的文本（命中词替换为 *） */
    private String replacedText;
    /** 命中的敏感词（审计用） */
    private List<String> hitWords;

    public static RiskCheckRespDTO pass() {
        return new RiskCheckRespDTO(true, false, null, null);
    }

    public static RiskCheckRespDTO blocked(List<String> hitWords) {
        return new RiskCheckRespDTO(false, true, null, hitWords);
    }

    public static RiskCheckRespDTO replaced(String replacedText, List<String> hitWords) {
        return new RiskCheckRespDTO(true, false, replacedText, hitWords);
    }

    public RiskCheckRespDTO() {
    }

    public RiskCheckRespDTO(boolean pass, boolean blocked, String replacedText, List<String> hitWords) {
        this.pass = pass;
        this.blocked = blocked;
        this.replacedText = replacedText;
        this.hitWords = hitWords;
    }

    public boolean isPass() {
        return pass;
    }

    public void setPass(boolean pass) {
        this.pass = pass;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public String getReplacedText() {
        return replacedText;
    }

    public void setReplacedText(String replacedText) {
        this.replacedText = replacedText;
    }

    public List<String> getHitWords() {
        return hitWords;
    }

    public void setHitWords(List<String> hitWords) {
        this.hitWords = hitWords;
    }
}
