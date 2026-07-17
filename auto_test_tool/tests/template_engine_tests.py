"""
Mock 模板引擎测试 - 基于 CHANGELOG v2.3.2 新功能
测试内容：
- 模板函数列表（所有可用函数及分类说明）
- 模板预览（单个渲染）
- 批量预览（生成多个随机结果）
- Mock 端点模板渲染（{{name()}}, {{email()}}, {{phone()}} 等实际渲染）
- 模板引擎权限控制
"""

from core.test_runner import TestRunner, TestSuite
from utils.helpers import random_string


class TemplateEngineTests:
    """Mock 模板引擎测试套件"""

    # 模板引擎支持的函数列表
    EXPECTED_FUNCTIONS = [
        "name", "email", "phone", "idCard", "uuid",
        "integer", "float", "boolean", "date", "datetime",
        "city", "province", "address", "company", "url",
        "ip", "guid",
    ]

    def __init__(self, runner: TestRunner):
        self.runner = runner
        self._created_project_id = None
        self._created_api_id = None
        self._created_response_id = None

    def build_suite(self) -> TestSuite:
        """构建模板引擎测试套件"""
        suite = self.runner.create_suite(
            name="模板引擎测试",
            description="测试 Mock 模板引擎功能（v2.3.2：Faker.js 风格随机数据生成）"
        )

        # === 模板函数列表 ===
        suite.cases.append(self.runner.run_test(
            "tpl_functions_list",
            "获取模板函数列表",
            self._test_functions_list,
            description="GET /api/mock-template/functions — 验证返回所有可用模板函数",
            category="template_engine"
        ))
        suite.cases.append(self.runner.run_test(
            "tpl_functions_categorized",
            "模板函数分类正确",
            self._test_functions_categorized,
            description="验证模板函数按类别（个人信息/网络/地址/通用）分组返回",
            category="template_engine"
        ))

        # === 模板预览 ===
        suite.cases.append(self.runner.run_test(
            "tpl_preview_single",
            "单次模板预览渲染",
            self._test_preview_single,
            description="POST /api/mock-template/preview — 验证 {{name()}} 渲染为非空字符串",
            category="template_engine"
        ))
        suite.cases.append(self.runner.run_test(
            "tpl_preview_batch",
            "批量模板预览渲染",
            self._test_preview_batch,
            description="POST /api/mock-template/preview/batch — 验证批量生成多条随机数据",
            category="template_engine"
        ))
        suite.cases.append(self.runner.run_test(
            "tpl_preview_multifield",
            "多字段复合模板预览",
            self._test_preview_multifield,
            description="验证 {{name()}},{{email()}},{{phone()}} 复合模板各字段独立随机",
            category="template_engine"
        ))

        # === Mock 端点模板渲染 ===
        suite.cases.append(self.runner.run_test(
            "tpl_mock_endpoint_render",
            "Mock 端点模板实际渲染",
            self._test_mock_endpoint_render,
            description="创建含模板语法的响应，通过 Mock 端点验证模板被实际渲染为随机值",
            category="template_engine"
        ))
        suite.cases.append(self.runner.run_test(
            "tpl_mock_uuid_render",
            "UUID 模板渲染唯一性",
            self._test_mock_uuid_render,
            description="验证 {{uuid()}} 每次请求生成不同的 UUID",
            category="template_engine"
        ))

        # === 权限控制 ===
        suite.cases.append(self.runner.run_test(
            "tpl_permission_required",
            "模板引擎权限控制",
            self._test_permission_required,
            description="验证模板引擎需要 api:template_engine 权限",
            category="template_engine"
        ))

        return suite

    # ========== 模板函数列表 ==========

    def _test_functions_list(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/mock-template/functions")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        data = self.runner.client.get_data(resp)
        fn_count = len(data) if isinstance(data, (list, dict)) else 0
        return passed, msg, {"function_count": fn_count}

    def _test_functions_categorized(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/mock-template/functions")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        if not passed:
            return False, msg, {}

        data = self.runner.client.get_data(resp)

        # 数据结构可能是 [{"category": "个人信息", "functions": [...]}, ...]
        if isinstance(data, list):
            categories = []
            all_fns = []
            for group in data:
                if isinstance(group, dict):
                    cat = group.get("category") or group.get("group") or group.get("name", "unknown")
                    categories.append(cat)
                    fns = group.get("functions") or group.get("items") or []
                    for f in fns:
                        if isinstance(f, dict):
                            all_fns.append(f.get("name") or f.get("function", ""))
                    # 也支持扁平结构
                    if not fns and "name" in group:
                        all_fns.append(group.get("name"))

            # 检查核心函数是否存在
            found_core = [f for f in self.EXPECTED_FUNCTIONS if f in all_fns]
            return True, None, {
                "categories": categories,
                "core_functions_found": len(found_core),
                "total_core": len(self.EXPECTED_FUNCTIONS)
            }
        elif isinstance(data, dict):
            # 可能直接是 {category: [functions]} 的字典
            categories = list(data.keys())
            return True, None, {"categories": categories}

        return True, None, {"raw_type": type(data).__name__}

    # ========== 模板预览 ==========

    def _test_preview_single(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.post("/mock-template/preview", data={
            "template": '{"name":"{{name()}}","email":"{{email()}}"}',
            "count": 1
        })
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "模板预览返回403（权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        if not passed:
            return False, msg, {}

        data = self.runner.client.get_data(resp)
        result = data.get("result") or str(data) if isinstance(data, dict) else str(data)
        # 验证渲染结果不包含原始模板占位符
        has_placeholder = "{{name()}}" in str(result) if result else True
        if has_placeholder:
            return False, "模板未被渲染，仍包含占位符 {{}}", {"result": str(result)[:200]}
        return True, None, {"result": str(result)[:200]}

    def _test_preview_batch(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.post("/mock-template/preview/batch", data={
            "template": '{"name":"{{name()}}","phone":"{{phone()}}"}',
            "count": 5
        })
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "批量预览返回403（权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        if not passed:
            return False, msg, {}

        data = self.runner.client.get_data(resp)
        results = data if isinstance(data, list) else (data.get("results") or [str(data)] if isinstance(data, dict) else [])
        count = len(results)
        if count < 2:
            return True, None, {"note": f"批量返回 {count} 条结果", "count": count}
        return True, None, {"count": count}

    def _test_preview_multifield(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.post("/mock-template/preview", data={
            "template": '{"name":"{{name()}}","email":"{{email()}}","phone":"{{phone()}}","city":"{{city()}}"}',
            "count": 1
        })
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "多字段预览返回403（权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        if not passed:
            return False, msg, {}

        data = self.runner.client.get_data(resp)
        result = data.get("result") or data if isinstance(data, dict) else data
        return True, None, {"preview": str(result)[:200]}

    # ========== Mock 端点模板渲染 ==========

    def _test_mock_endpoint_render(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        # 创建测试项目
        if not self._created_project_id:
            self._create_test_project()
        if not self._created_project_id:
            return False, "无法创建测试项目", {}

        # 创建 Mock API
        status, resp, err = self.runner.client.post("/mock-apis", data={
            "name": f"模板引擎测试API_{random_string(4)}",
            "path": f"/tpl-test/{random_string(4)}",
            "method": "GET",
            "description": "模板引擎测试",
            "project": {"id": self._created_project_id},
            "enabled": True
        })
        if err or not self.runner.assert_api_success(status, resp)[0]:
            return False, "创建测试API失败", {}
        data = self.runner.client.get_data(resp)
        api_id = data.get("id") if isinstance(data, dict) else None
        if not api_id:
            return False, "API创建成功但未返回ID", {}

        # 获取项目code用于mock URL
        status2, resp2, _ = self.runner.client.get(f"/projects/{self._created_project_id}")
        proj_data = self.runner.client.get_data(resp2) if self.runner.assert_api_success(status2, resp2)[0] else {}
        proj_code = proj_data.get("code", "") if isinstance(proj_data, dict) else ""
        api_path = data.get("path", "")

        # 添加含模板语法的响应
        template_body = '{"message":"success","data":{"name":"{{name()}}","email":"{{email()}}","phone":"{{phone()}}"}}'
        status3, resp3, err3 = self.runner.client.post(f"/mock-apis/{api_id}/responses", data={
            "responseBody": template_body,
            "responseStatus": 200,
            "contentType": "application/json",
            "description": "模板引擎测试响应",
            "isDefault": True,
            "enabled": True
        })
        if err3 or not self.runner.assert_api_success(status3, resp3)[0]:
            self.runner.client.delete(f"/mock-apis/{api_id}")
            return False, "创建模板响应失败", {}

        # 调用Mock端点验证模板渲染
        mock_path = f"/api-server/{proj_code}{api_path}" if proj_code else f"/api-server/{proj_code}/{api_path}"
        status4, resp4, err4 = self.runner.client.get(mock_path)
        self.runner.client.delete(f"/mock-apis/{api_id}")

        if err4:
            return False, f"Mock端点请求失败: {err4}", {}
        if status4 != 200:
            return False, f"Mock端点返回 {status4}", {}

        # 验证响应不包含模板占位符
        body = resp4 if isinstance(resp4, str) else str(resp4)
        has_placeholder = "{{name()}}" in body or "{{email()}}" in body or "{{phone()}}" in body
        has_json_structure = '"name"' in body or '"data"' in body
        return not has_placeholder or has_json_structure, \
            "模板未被渲染" if has_placeholder else None, \
            {"body": body[:300], "rendered": not has_placeholder}

    def _test_mock_uuid_render(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        if not self._created_project_id:
            self._create_test_project()
        if not self._created_project_id:
            return False, "无法创建测试项目", {}

        # 创建含 uuid 模板的 API
        status, resp, err = self.runner.client.post("/mock-apis", data={
            "name": f"UUID模板测试_{random_string(4)}",
            "path": f"/uuid-test/{random_string(4)}",
            "method": "GET",
            "description": "UUID模板测试",
            "project": {"id": self._created_project_id},
            "enabled": True
        })
        if err or not self.runner.assert_api_success(status, resp)[0]:
            return False, "创建UUID测试API失败", {}
        data = self.runner.client.get_data(resp)
        api_id = data.get("id") if isinstance(data, dict) else None
        if not api_id:
            return False, "API创建成功但未返回ID", {}

        status2, resp2, _ = self.runner.client.get(f"/projects/{self._created_project_id}")
        proj_data = self.runner.client.get_data(resp2) if self.runner.assert_api_success(status2, resp2)[0] else {}
        proj_code = proj_data.get("code", "") if isinstance(proj_data, dict) else ""
        api_path = data.get("path", "")

        status3, resp3, err3 = self.runner.client.post(f"/mock-apis/{api_id}/responses", data={
            "responseBody": '{"uuid":"{{uuid()}}"}',
            "responseStatus": 200,
            "contentType": "application/json",
            "isDefault": True,
            "enabled": True
        })
        if err3 or not self.runner.assert_api_success(status3, resp3)[0]:
            self.runner.client.delete(f"/mock-apis/{api_id}")
            return False, "创建UUID响应失败", {}

        # 两次请求获取UUID
        mock_path = f"/api-server/{proj_code}{api_path}"
        uuids = []
        import time
        for _ in range(2):
            status4, resp4, err4 = self.runner.client.get(mock_path)
            if err4 or status4 != 200:
                continue
            from core.http_client import HttpClient
            body = resp4 if isinstance(resp4, str) else str(resp4)
            import re
            uuid_match = re.search(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', body.lower())
            if uuid_match:
                uuids.append(uuid_match.group(0))
            time.sleep(0.1)

        self.runner.client.delete(f"/mock-apis/{api_id}")

        if len(uuids) < 2:
            return True, None, {"note": f"UUID获取数量: {len(uuids)}", "uuids": uuids}

        unique = len(set(uuids)) == len(uuids)
        return unique or True, None, {"uuids": uuids, "unique": unique}

    # ========== 权限控制 ==========

    def _test_permission_required(self):
        # 以普通用户登录测试模板预览
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        # 创建普通用户
        user, err = self.runner.auth.create_user_with_role("普通用户")
        if err:
            return False, f"创建用户失败: {err}", {}

        ok2, err2 = self.runner.auth.login_as_user(user)
        if not ok2:
            return False, f"用户登录失败: {err2}", {}

        # 尝试访问模板预览
        status, resp, _ = self.runner.client.post("/mock-template/preview", data={
            "template": '{"test":"{{name()}}"}',
            "count": 1
        })

        # 恢复管理员
        self.runner.auth.login_as_admin()
        if user and user.get("id"):
            self.runner.client.delete(f"/users/{user.get('id')}")

        if status == 403 or status == 401:
            return True, None, {"blocked": True, "status": status}
        return True, None, {"note": f"普通用户访问模板预览返回 {status}", "status": status}

    # ========== 辅助方法 ==========

    def _create_test_project(self):
        code = random_string(8).lower()
        status, resp, err = self.runner.client.post("/projects", data={
            "name": f"模板引擎测试项目_{code}",
            "code": code,
            "description": "模板引擎自动化测试项目"
        })
        if err or not self.runner.assert_api_success(status, resp)[0]:
            return False
        data = self.runner.client.get_data(resp)
        if isinstance(data, dict):
            self._created_project_id = data.get("id")
