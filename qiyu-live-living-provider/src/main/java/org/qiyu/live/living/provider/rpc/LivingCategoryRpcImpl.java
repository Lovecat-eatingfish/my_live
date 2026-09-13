package org.qiyu.live.living.provider.rpc;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.living.interfaces.dto.LivingCategoryDTO;
import org.qiyu.live.living.interfaces.rpc.ILivingCategoryRpc;
import org.qiyu.live.living.provider.dao.mapper.LivingCategoryMapper;
import org.qiyu.live.living.provider.dao.po.LivingCategoryPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 直播分区 RPC 实现
 */
@DubboService
public class LivingCategoryRpcImpl implements ILivingCategoryRpc {

    @Resource
    private LivingCategoryMapper livingCategoryMapper;

    @Override
    public List<LivingCategoryDTO> listCategories() {
        List<LivingCategoryPO> pos = livingCategoryMapper.selectList(
                new LambdaQueryWrapper<LivingCategoryPO>()
                        .orderByAsc(LivingCategoryPO::getSort)
                        .orderByAsc(LivingCategoryPO::getId));
        return pos.stream().map(this::convert).collect(Collectors.toList());
    }

    @Override
    public Integer addCategory(String name, String icon, Integer sort) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        Long exists = livingCategoryMapper.selectCount(
                new LambdaQueryWrapper<LivingCategoryPO>().eq(LivingCategoryPO::getName, name.trim()));
        if (exists > 0) {
            return null;
        }
        LivingCategoryPO po = new LivingCategoryPO();
        po.setName(name.trim());
        po.setIcon(icon == null ? "" : icon);
        po.setSort(sort == null ? 0 : sort);
        po.setStatus(1);
        livingCategoryMapper.insert(po);
        return po.getId();
    }

    @Override
    public Boolean updateCategory(Integer id, String name, String icon, Integer sort, Integer status) {
        if (id == null) {
            return false;
        }
        LambdaUpdateWrapper<LivingCategoryPO> uw = new LambdaUpdateWrapper<LivingCategoryPO>()
                .eq(LivingCategoryPO::getId, id)
                .set(name != null, LivingCategoryPO::getName, name)
                .set(icon != null, LivingCategoryPO::getIcon, icon)
                .set(sort != null, LivingCategoryPO::getSort, sort)
                .set(status != null, LivingCategoryPO::getStatus, status);
        return livingCategoryMapper.update(null, uw) > 0;
    }

    private LivingCategoryDTO convert(LivingCategoryPO po) {
        LivingCategoryDTO dto = new LivingCategoryDTO();
        dto.setId(po.getId());
        dto.setName(po.getName());
        dto.setIcon(po.getIcon());
        dto.setSort(po.getSort());
        dto.setStatus(po.getStatus());
        return dto;
    }
}
