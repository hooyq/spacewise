package cn.edu.fudan.spacewise.seat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 单表的增删改查由 BaseMapper 提供；复杂查询在这里用注解或 XML 手写 SQL。 */
@Mapper
public interface RoomMapper extends BaseMapper<RoomEntity> {
}
