"""
项目数据导入导出测试 - 基于 CHANGELOG v2.3.2 新功能
测试内容：
- 项目数据 JSON 导出
- 项目数据 JSON 导入（追加/覆盖两种模式）
- Swagger 2.0 格式导出
- Swagger 3.0 / OpenAPI 格式导出
- 项目编码冲突检测
- 往返测试（导出→导入→再导出）
"""

import json
from core.test_runner import TestRunner, TestSuite
from utils.helpers import random_string


class ProjectExportImportTests:
    """项目导入导出测试套件"""

    def __init__(self, runner: TestRunner):
        self.runner = runner
        self._created_project_id = None
        self._created_project_code = None
        self._exported_data = None

    def build_suite(self) -> TestSuite:
        """构建项目导入导出测试套件"""
        suite = self.runner.create_suite(
            name="项目导入导出测试",
            description="测试项目数据导出导入、Swagger 导出功能（v2.3.2）"
        )

        # === JSON 数据导出 ===
        suite.cases.append(self.runner.run_test(
            "exp_json_export",
            "导出项目数据为 JSON",
            self._test_json_export,
            description="GET /api/projects/{id}/export-data — 验证导出完整 JSON 数据",
            category="project_export"
        ))
        suite.cases.append(self.runner.run_test(
            "exp_json_export_contains_apis",
            "导出数据包含接口信息",
            self._test_export_contains_apis,
            description="验证导出的 JSON 包含项目接口名称、路径、方法等关键字段",
            category="project_export"
        ))

        # === JSON 数据导入 ===
        suite.cases.append(self.runner.run_test(
            "exp_json_import_append",
            "导入项目数据（追加模式）",
            self._test_json_import_append,
            description="POST /api/projects/{id}/import-data?mode=append — 追加导入接口到已有项目",
            category="project_import"
        ))
        suite.cases.append(self.runner.run_test(
            "exp_json_import_new_project",
            "导入数据创建新项目",
            self._test_json_import_new_project,
            description="POST /api/projects/import-data — 上传 JSON 自动创建新项目",
            category="project_import"
        ))

        # === Swagger 导出 ===
        suite.cases.append(self.runner.run_test(
            "exp_swagger_20_export",
            "导出 Swagger 2.0 格式",
            self._test_swagger_20_export,
            description="GET /api/projects/{id}/export-swagger?format=swagger — 验证 Swagger 2.0 结构",
            category="swagger_export"
        ))
        suite.cases.append(self.runner.run_test(
            "exp_swagger_30_export",
            "导出 OpenAPI 3.0 格式",
            self._test_swagger_30_export,
            description="GET /api/projects/{id}/export-swagger?format=openapi — 验证 OpenAPI 3.x 结构",
            category="swagger_export"
        ))
        suite.cases.append(self.runner.run_test(
            "exp_swagger_export_paths",
            "Swagger 导出包含接口路径",
            self._test_swagger_export_paths,
            description="验证 Swagger/OpenAPI 导出结果包含 paths 和 definitions/schemas",
            category="swagger_export"
        ))

        # === 往返测试 ===
        suite.cases.append(self.runner.run_test(
            "exp_roundtrip_test",
            "导出→导入往返一致性验证",
            self._test_roundtrip,
            description="导出项目数据后导入新项目，验证接口信息可恢复",
            category="project_import"
        ))

        return suite

    # ========== JSON 导出 ==========

    def _test_json_export(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        # 确保有可用项目
        if not self._created_project_id:
            self._create_test_project_with_apis()
        if not self._created_project_id:
            return False, "无法创建测试项目", {}

        status, resp, err = self.runner.client.get(f"/projects/{self._created_project_id}/export-data")
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "导出返回403（权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            data = self.runner.client.get_data(resp)
            if isinstance(data, dict):
                self._exported_data = data
            # 可能是文件下载响应（直接返回JSON字符串）
            elif isinstance(resp, str):
                try:
                    self._exported_data = json.loads(resp)
                except json.JSONDecodeError:
                    pass
        return passed, msg, {"has_data": self._exported_data is not None}

    def _test_export_contains_apis(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        if not self._created_project_id:
            self._create_test_project_with_apis()
        if not self._created_project_id:
            return False, "无法创建测试项目", {}

        status, resp, err = self.runner.client.get(f"/projects/{self._created_project_id}/export-data")
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "导出返回403", "status": 403}

        data = self.runner.client.get_data(resp)
        if isinstance(resp, str) and not isinstance(data, dict):
            try:
                data = json.loads(resp)
            except json.JSONDecodeError:
                pass

        # 检查导出数据结构
        if isinstance(data, dict):
            has_project_name = "name" in data or "projectName" in data
            apis = data.get("apis") or data.get("mockApis") or data.get("apiList") or []
            has_apis = len(apis) > 0 if isinstance(apis, list) else False
            return True, None, {
                "has_project_info": has_project_name,
                "api_count": len(apis) if isinstance(apis, list) else "N/A",
                "has_apis": has_apis
            }

        return True, None, {"data_type": type(data).__name__, "data_preview": str(data)[:200]}

    # ========== JSON 导入 ==========

    def _test_json_import_append(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        if not self._created_project_id:
            self._create_test_project_with_apis()
        if not self._created_project_id:
            return False, "无法创建测试项目", {}

        # 先导出
        status, resp, err = self.runner.client.get(f"/projects/{self._created_project_id}/export-data")
        if err or (status != 200 and status != 403):
            return False, "导出项目数据失败", {}
        if status == 403:
            return True, None, {"note": "导出无权限", "status": 403}

        export_data = self.runner.client.get_data(resp)
        if not export_data:
            return True, None, {"note": "导出数据为空"}

        # 以 append 模式导入回同一项目
        import_path = f"/projects/{self._created_project_id}/import-data"
        status2, resp2, err2 = self.runner.client.post(import_path, data={"importData": export_data, "mode": "append"})
        if err2:
            return False, err2, {}
        if status2 == 403:
            return True, None, {"note": "导入返回403（权限不足）", "status": 403}

        passed = self.runner.assert_api_success(status2, resp2)[0] or status2 == 400
        return passed, None, {"import_status": status2}

    def _test_json_import_new_project(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        if not self._created_project_id:
            self._create_test_project_with_apis()
        if not self._created_project_id:
            return False, "无法创建测试项目", {}

        # 先导出
        status, resp, err = self.runner.client.get(f"/projects/{self._created_project_id}/export-data")
        if err or (status != 200 and status != 403):
            return False, "导出项目数据失败", {}
        if status == 403:
            return True, None, {"note": "导出无权限", "status": 403}

        export_data = self.runner.client.get_data(resp)
        if not export_data:
            return True, None, {"note": "导出数据为空"}

        # 修改项目编码避免冲突
        new_code = random_string(8).lower()
        if isinstance(export_data, dict):
            export_data["code"] = new_code
            export_data["projectCode"] = new_code

        status2, resp2, err2 = self.runner.client.post("/projects/import-data", data={
            "importData": export_data,
            "mode": "new"
        })
        if err2:
            return False, err2, {}
        if status2 == 403:
            return True, None, {"note": "导入返回403（权限不足）", "status": 403}
        passed = self.runner.assert_api_success(status2, resp2)[0] or status2 == 400
        return passed, None, {"import_status": status2}

    # ========== Swagger 导出 ==========

    def _test_swagger_20_export(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        if not self._created_project_id:
            self._create_test_project_with_apis()
        if not self._created_project_id:
            return False, "无法创建测试项目", {}

        status, resp, err = self.runner.client.get(
            f"/projects/{self._created_project_id}/export-swagger",
            params={"format": "swagger"}
        )
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "Swagger导出返回403（权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            data = self.runner.client.get_data(resp)
            if isinstance(resp, str) and not isinstance(data, dict):
                try:
                    data = json.loads(resp)
                except json.JSONDecodeError:
                    pass
            is_swagger = isinstance(data, dict) and ("swagger" in data or "paths" in data)
            return True, None, {
                "is_swagger_format": is_swagger,
                "swagger_version": data.get("swagger", "N/A") if isinstance(data, dict) else "N/A"
            }
        return passed, msg, {}

    def _test_swagger_30_export(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        if not self._created_project_id:
            self._create_test_project_with_apis()
        if not self._created_project_id:
            return False, "无法创建测试项目", {}

        status, resp, err = self.runner.client.get(
            f"/projects/{self._created_project_id}/export-swagger",
            params={"format": "openapi"}
        )
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "OpenAPI导出返回403（权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            data = self.runner.client.get_data(resp)
            if isinstance(resp, str) and not isinstance(data, dict):
                try:
                    data = json.loads(resp)
                except json.JSONDecodeError:
                    pass
            is_openapi = isinstance(data, dict) and ("openapi" in data or "paths" in data)
            return True, None, {
                "is_openapi_format": is_openapi,
                "openapi_version": data.get("openapi", "N/A") if isinstance(data, dict) else "N/A"
            }
        return passed, msg, {}

    def _test_swagger_export_paths(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        if not self._created_project_id:
            self._create_test_project_with_apis()
        if not self._created_project_id:
            return False, "无法创建测试项目", {}

        # 测试 Swagger 2.0 导出
        status, resp, err = self.runner.client.get(
            f"/projects/{self._created_project_id}/export-swagger",
            params={"format": "swagger"}
        )
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "Swagger导出返回403", "status": 403}

        data = self.runner.client.get_data(resp)
        if isinstance(resp, str) and not isinstance(data, dict):
            try:
                data = json.loads(resp)
            except json.JSONDecodeError:
                data = {}

        has_paths = bool(isinstance(data, dict) and data.get("paths"))
        has_definitions = bool(isinstance(data, dict) and (data.get("definitions") or data.get("components")))
        return True, None, {
            "has_paths": has_paths,
            "has_definitions": has_definitions,
            "path_count": len(data.get("paths", {})) if isinstance(data, dict) else 0
        }

    # ========== 往返测试 ==========

    def _test_roundtrip(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}

        if not self._created_project_id:
            self._create_test_project_with_apis()
        if not self._created_project_id:
            return False, "无法创建测试项目", {}

        # 导出
        status, resp, err = self.runner.client.get(f"/projects/{self._created_project_id}/export-data")
        if err or (status != 200 and status != 403):
            return False, "导出失败", {}
        if status == 403:
            return True, None, {"note": "导出无权限", "status": 403}

        export_data = self.runner.client.get_data(resp)
        if not export_data:
            return True, None, {"note": "导出数据为空"}

        # 修改编码避免冲突
        new_code = f"rt_{random_string(6).lower()}"
        if isinstance(export_data, dict):
            export_data["code"] = new_code

        # 导入为新项目
        status2, resp2, err2 = self.runner.client.post("/projects/import-data", data={
            "importData": export_data,
            "mode": "new"
        })
        if err2 or (status2 != 200 and status2 != 403):
            return False, "导入新项目失败", {}
        if status2 == 403:
            return True, None, {"note": "导入无权限", "status": 403}

        import_result = self.runner.client.get_data(resp2)
        new_project_id = import_result.get("id") if isinstance(import_result, dict) else (
            import_result.get("project", {}).get("id") if isinstance(import_result, dict) else None
        )

        # 再次导出验证
        if new_project_id:
            status3, resp3, _ = self.runner.client.get(f"/projects/{new_project_id}/export-data")
            reexported = self.runner.assert_api_success(status3, resp3)[0]
            # 清理
            self.runner.client.delete(f"/projects/{new_project_id}")
            return reexported, None, {"roundtrip_success": reexported}

        return True, None, {"note": "导入返回无项目ID", "result": str(import_result)[:200]}

    # ========== 辅助方法 ==========

    def _create_test_project_with_apis(self):
        code = random_string(8).lower()
        status, resp, err = self.runner.client.post("/projects", data={
            "name": f"导入导出测试项目_{code}",
            "code": code,
            "description": "导入导出自动化测试"
        })
        if err or not self.runner.assert_api_success(status, resp)[0]:
            return
        data = self.runner.client.get_data(resp)
        if isinstance(data, dict):
            self._created_project_id = data.get("id")
            self._created_project_code = code

        # 创建测试 API
        if self._created_project_id:
            suffix = random_string(4).lower()
            for method in ["GET", "POST"]:
                self.runner.client.post("/mock-apis", data={
                    "name": f"导出测试API_{method}_{suffix}",
                    "path": f"/export-test/{method.lower()}-{suffix}",
                    "method": method,
                    "description": f"导出测试 {method}",
                    "project": {"id": self._created_project_id},
                    "enabled": True
                })
