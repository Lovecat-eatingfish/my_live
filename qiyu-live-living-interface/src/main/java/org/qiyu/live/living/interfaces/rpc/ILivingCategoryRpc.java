package org.qiyu.live.living.interfaces.rpc;

import org.qiyu.live.living.interfaces.dto.LivingCategoryDTO;

import java.util.List;

/**
 * 直播分区 RPC（运营可配置，id 即房间 type 值）
 */
public interface ILivingCategoryRpc {

    /** 全部分区（含停用，按 sort 升序）；C 端由调用方过滤 status==1 */
    List<LivingCategoryDTO> listCategories();

    /** 新增分区（name 唯一），返回 id；重名返回 null */
    Integer addCategory(String name, String icon, Integer sort);

    /** 更新分区（name/icon/sort/status 传 null 表示保持不变；status 0=停用） */
    Boolean updateCategory(Integer id, String name, String icon, Integer sort, Integer status);
}
