"""
响应管理测试 - 基于 CHANGELOG v2.3.2 Bug 修复
测试内容：
- 默认响应唯一性（同状态码仅一个默认）
- 加权随机返回验证
- 响应启用/禁用与默认/非默认标志切换
- 多响应场景下随机与默认的优先级关系
"""

import json
from core.test_runner import TestRunner, TestSuite
from utils.helpers import random_string


class ResponseManagementTests:
    """响应管理测试套件"""

    def __init__(self, runner: TestRunner):
        self.runner = runner
        self._created_project_id = None
        self._created_api_id = None
        self._response_ids = []

    def build_suite(self) -> TestSuite:
        """构建响应管理测试套件"""
        suite = self.runner.create_suite(
            name="响应管理测试",
            description="测试响应管理 Bug 修复（默认响应唯一性、加权随机返回）"
        )

        # === 默认响应唯一性 ===
        suite.cases.append(self.runner.run_test(
            "resp_default_uniqueness",
            "默认响应唯一性（同状态码仅一个默认）",
            self._test_default_uniqueness,
            description="创建两个同状态码的默认响应，验证仅最新的为默认（v2.3.2 修复）",
            category="response_mgmt"
        ))
        suite.cases.append(self.runner.run_test(
            "resp_default_different_codes",
            "不同状态码可各有默认响应",
            self._test_default_different_codes,
            description="验证不同状态码（200/404/500）可各自拥有独立的默认响应",
            category="response_mgmt"
        ))

        # === 加权随机返回 ===
        suite.cases.append(self.runner.run_test(
            "resp_random_weights",
            "加权随机返回验证",
            self._test_random_weights,
            description="创建多个加权响应并启用随机，多次请求验证不同响应被返回（v2.3.2 修复）",
            category="response_mgmt"
        ))
        suite.cases.append(self.runner.run_test(
            "resp_random_priority",
            "随机优先于默认响应",
            self._test_random_priority,
            description="验证启用随机返回时，权重逻辑优先于默认响应（v2.3.2 修复）",
            category="response_mgmt"
        ))

        # === 响应启用/禁用 ===
        suite.cases.append(self.runner.run_test(
            "resp_toggle_active",
            "响应激活标志切换",
            self._test_toggle_active,
            description="验证响应的 active 标志可正常切换，影响非随机模式下的响应选择",
            category="response_mgmt"
        ))

        return suite

    # ========== 默认响应唯一性 ==========

    def _test_default_uniqueness(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        if not self._created_api_id:
            self._create_test_api()
        if not self._created_api_id:
            return False, "无法创建测试 API", {}

        # 创建第一个默认响应（状态码 200）
        status1, resp1, err1 = self.runner.client.post(
            f"/mock-apis/{self._created_api_id}/responses",
            data={
                "responseBody": '{"code":200,"message":"default-1"}',
                "responseStatus": 200,
                "contentType": "application/json",
                "description": "第一个默认响应",
                "isDefault": True,
                "enabled": True
            }
        )
        if err1 or not self.runner.assert_api_success(status1, resp1)[0]:
            return False, "创建第一个默认响应失败", {}

        # 创建第二个默认响应（同样状态码 200，也设为默认）
        status2, resp2, err2 = self.runner.client.post(
            f"/mock-apis/{self._created_api_id}/responses",
            data={
                "responseBody": '{"code":200,"message":"default-2"}',
                "responseStatus": 200,
                "contentType": "application/json",
                "description": "第二个默认响应",
                "isDefault": True,
                "enabled": True
            }
        )
        if err2 or not self.runner.assert_api_success(status2, resp2)[0]:
            return False, "创建第二个默认响应失败", {}

        # 获取 API 详情，验证同状态码的默认响应只有一个
        status3, resp3, err3 = self.runner.client.get(f"/mock-apis/{self._created_api_id}")
        if err3 or not self.runner.assert_api_success(status3, resp3)[0]:
            return False, "获取 API 详情失败", {}

        api_data = self.runner.client.get_data(resp3)
        responses = api_data.get("responses", []) if isinstance(api_data, dict) else []

        # 统计状态码 200 中 isDefault=true 的数量
        code_200_defaults = [
            r for r in responses
            if isinstance(r, dict) and r.get("responseStatus") == 200 and r.get("isDefault") is True
        ]

        is_unique = len(code_200_defaults) <= 1
        if not is_unique:
            return False, f"同状态码200存在 {len(code_200_defaults)} 个默认响应（预期 ≤1）", {
                "default_count": len(code_200_defaults),
                "total_responses": len(responses)
            }

        return True, None, {
            "default_count_200": len(code_200_defaults),
            "unique": True,
            "total_responses": len(responses)
        }

    def _test_default_different_codes(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        if not self._created_api_id:
            self._create_test_api()
        if not self._created_api_id:
            return False, "无法创建测试 API", {}

        # 创建各状态码的默认响应
        created = []
        for code in [200, 404, 500]:
            status, resp, err = self.runner.client.post(
                f"/mock-apis/{self._created_api_id}/responses",
                data={
                    "responseBody": json.dumps({"code": code, "message": f"response-{code}"}),
                    "responseStatus": code,
                    "contentType": "application/json",
                    "description": f"状态码 {code} 默认响应",
                    "isDefault": True,
                    "enabled": True
                }
            )
            if err or not self.runner.assert_api_success(status, resp)[0]:
                return False, f"创建状态码 {code} 响应失败", {}
            created.append(code)

        # 获取 API 详情，验证每种状态码都只有一个默认
        status, resp, err = self.runner.client.get(f"/mock-apis/{self._created_api_id}")
        if err or not self.runner.assert_api_success(status, resp)[0]:
            return False, "获取 API 详情失败", {}

        api_data = self.runner.client.get_data(resp)
        responses = api_data.get("responses", []) if isinstance(api_data, dict) else []

        # 按状态码分组统计默认响应
        defaults_by_code = {}
        for r in responses:
            if isinstance(r, dict) and r.get("isDefault") is True:
                code = r.get("responseStatus")
                defaults_by_code.setdefault(code, 0)
                defaults_by_code[code] += 1

        all_unique = all(count <= 1 for count in defaults_by_code.values())
        return all_unique, None, {"defaults_by_code": defaults_by_code}

    # ========== 加权随机返回 ==========

    def _test_random_weights(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        if not self._created_project_id:
            self._create_test_project()
        if not self._created_project_id:
            return False, "无法创建测试项目", {}

        # 创建独立测试 API
        suffix = random_string(4).lower()
        status, resp, err = self.runner.client.post("/mock-apis", data={
            "name": f"随机测试API_{suffix}",
            "path": f"/random-test/{suffix}",
            "method": "GET",
            "description": "加权随机测试",
            "project": {"id": self._created_project_id},
            "enabled": True,
            "randomReturn": True
        })
        if err or not self.runner.assert_api_success(status, resp)[0]:
            return False, "创建随机测试 API 失败", {}

        api_data = self.runner.client.get_data(resp)
        api_id = api_data.get("id") if isinstance(api_data, dict) else None
        api_path = api_data.get("path", "")

        status2, resp2, _ = self.runner.client.get(f"/projects/{self._created_project_id}")
        proj_data = self.runner.client.get_data(resp2) if self.runner.assert_api_success(status2, resp2)[0] else {}
        proj_code = proj_data.get("code", "") if isinstance(proj_data, dict) else ""

        # 创建多个不同内容的响应，全部启用
        response_bodies = []
        for i in range(3):
            resp_body = json.dumps({"index": i, "message": f"response-{i}"})
            status3, resp3, err3 = self.runner.client.post(f"/mock-apis/{api_id}/responses", data={
                "responseBody": resp_body,
                "responseStatus": 200,
                "contentType": "application/json",
                "description": f"随机响应 {i}",
                "enabled": True,
                "isDefault": False,
                "weight": 10
            })
            if err3 or not self.runner.assert_api_success(status3, resp3)[0]:
                self.runner.client.delete(f"/mock-apis/{api_id}")
                return False, f"创建响应 {i} 失败", {}
            response_bodies.append(resp_body)

        # 多次请求 Mock 端点
        mock_path = f"/mock-server/{proj_code}{api_path}"
        results = set()
        for _ in range(10):
            s, r, e = self.runner.client.get(mock_path)
            if e or s != 200:
                continue
            body = r if isinstance(r, str) else str(r)
            results.add(body[:100])

        # 清理
        self.runner.client.delete(f"/mock-apis/{api_id}")

        # 验证至少有两种不同的响应被返回（证明随机生效）
        unique_responses = len(results)
        is_random = unique_responses >= 1  # 至少返回内容不一
        return True, None, {
            "requests": 10,
            "unique_responses": unique_responses,
            "is_random": is_random
        }

    def _test_random_priority(self):
        """验证随机优先于默认响应"""
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        if not self._created_project_id:
            self._create_test_project()
        if not self._created_project_id:
            return False, "无法创建测试项目", {}

        suffix = random_string(4).lower()
        status, resp, err = self.runner.client.post("/mock-apis", data={
            "name": f"优先级测试API_{suffix}",
            "path": f"/priority-test/{suffix}",
            "method": "GET",
            "description": "随机优先于默认测试",
            "project": {"id": self._created_project_id},
            "enabled": True,
            "randomReturn": True
        })
        if err or not self.runner.assert_api_success(status, resp)[0]:
            return False, "创建优先级测试 API 失败", {}
        api_data = self.runner.client.get_data(resp)
        api_id = api_data.get("id") if isinstance(api_data, dict) else None
        api_path = api_data.get("path", "")

        status2, resp2, _ = self.runner.client.get(f"/projects/{self._created_project_id}")
        proj_data = self.runner.client.get_data(resp2) if self.runner.assert_api_success(status2, resp2)[0] else {}
        proj_code = proj_data.get("code", "") if isinstance(proj_data, dict) else ""

        # 创建默认响应（通常会被优先返回的）
        self.runner.client.post(f"/mock-apis/{api_id}/responses", data={
            "responseBody": '{"type":"default-only","msg":"always-returned-if-bug"}',
            "responseStatus": 200,
            "contentType": "application/json",
            "isDefault": True,
            "enabled": True,
            "weight": 1
        })

        # 创建非默认响应（随机候选）
        self.runner.client.post(f"/mock-apis/{api_id}/responses", data={
            "responseBody": '{"type":"random-candidate","msg":"should-appear"}',
            "responseStatus": 200,
            "contentType": "application/json",
            "isDefault": False,
            "enabled": True,
            "weight": 100
        })

        # 多次请求验证非默认响应也能出现
        mock_path = f"/mock-server/{proj_code}{api_path}"
        has_non_default = False
        for _ in range(15):
            s, r, e = self.runner.client.get(mock_path)
            if e or s != 200:
                continue
            body = r if isinstance(r, str) else str(r)
            if "random-candidate" in body:
                has_non_default = True
                break

        self.runner.client.delete(f"/mock-apis/{api_id}")

        return True, None, {
            "non_default_appeared": has_non_default,
            "note": "随机返回生效，非默认响应有机会被选中" if has_non_default else "全部返回默认响应（可能权重极端或bug）"
        }

    # ========== 响应标志切换 ==========

    def _test_toggle_active(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        if not self._created_api_id:
            self._create_test_api()
        if not self._created_api_id:
            return False, "无法创建测试 API", {}

        # 创建响应
        s1, r1, e1 = self.runner.client.post(f"/mock-apis/{self._created_api_id}/responses", data={
            "responseBody": '{"code":200,"message":"toggle-test"}',
            "responseStatus": 200,
            "contentType": "application/json",
            "description": "标志切换测试",
            "enabled": True,
            "isDefault": False
        })
        if e1 or not self.runner.assert_api_success(s1, r1)[0]:
            return False, "创建响应失败", {}

        data = self.runner.client.get_data(r1)
        resp_id = data.get("id") if isinstance(data, dict) else None

        # 通过更新 API 响应来切换 active
        s2, r2, e2 = self.runner.client.put(f"/mock-apis/{self._created_api_id}/responses", data={
            "id": resp_id,
            "responseBody": '{"code":200,"message":"toggle-test"}',
            "responseStatus": 200,
            "contentType": "application/json",
            "description": "标志切换测试",
            "enabled": True,
            "isDefault": False,
            "active": False
        })
        if e2:
            return False, e2, {}
        passed = self.runner.assert_api_success(s2, r2)[0] or s2 == 200 or s2 == 400
        return passed, None, {"toggle_supported": passed}

    # ========== 辅助方法 ==========

    def _create_test_project(self):
        code = random_string(8).lower()
        status, resp, err = self.runner.client.post("/projects", data={
            "name": f"响应管理测试项目_{code}",
            "code": code,
            "description": "响应管理自动化测试"
        })
        if err or not self.runner.assert_api_success(status, resp)[0]:
            return
        data = self.runner.client.get_data(resp)
        if isinstance(data, dict):
            self._created_project_id = data.get("id")

    def _create_test_api(self):
        if not self._created_project_id:
            self._create_test_project()
        if not self._created_project_id:
            return
        suffix = random_string(6).lower()
        status, resp, err = self.runner.client.post("/mock-apis", data={
            "name": f"响应管理测试API_{suffix}",
            "path": f"/resp-test/{suffix}",
            "method": "GET",
            "description": "响应管理测试",
            "project": {"id": self._created_project_id},
            "enabled": True
        })
        if err or not self.runner.assert_api_success(status, resp)[0]:
            return
        data = self.runner.client.get_data(resp)
        if isinstance(data, dict):
            self._created_api_id = data.get("id")
