package cn.edu.fudan.spacewise.platform.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 对应 seat 表（V3__create_seat.sql）。created_at / updated_at 由数据库维护，不映射。 */
@TableName("seat")
public class SeatEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private String code;
    private SpaceStatus status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public SpaceStatus getStatus() {
        return status;
    }

    public void setStatus(SpaceStatus status) {
        this.status = status;
    }
}
