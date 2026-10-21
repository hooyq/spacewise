package cn.edu.fudan.spacewise.platform.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 对应 building 表（V1__create_building.sql）。created_at / updated_at 由数据库维护，不映射。 */
@TableName("building")
public class BuildingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private SpaceStatus status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SpaceStatus getStatus() {
        return status;
    }

    public void setStatus(SpaceStatus status) {
        this.status = status;
    }
}
