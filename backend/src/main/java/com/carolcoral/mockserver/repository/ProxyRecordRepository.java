/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.repository;

import com.carolcoral.mockserver.entity.ProxyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 代理请求录制数据访问接口
 *
 * @author carolcoral
 */
@Repository
public interface ProxyRecordRepository extends JpaRepository<ProxyRecord, Long> {
}
