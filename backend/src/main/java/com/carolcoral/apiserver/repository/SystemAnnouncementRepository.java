/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.SystemAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 系统公告数据访问接口
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Repository
public interface SystemAnnouncementRepository extends JpaRepository<SystemAnnouncement, Long> {

    /**
     * 查找启用的公告（按优先级和创建时间排序）
     *
     * @return 启用的公告
     */
    @Query("SELECT a FROM SystemAnnouncement a WHERE a.enabled = true ORDER BY a.priority DESC, a.createTime DESC")
    Optional<SystemAnnouncement> findEnabledAnnouncement();
}
