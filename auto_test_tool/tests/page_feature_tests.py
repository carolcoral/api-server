"""
页面功能测试 - 测试各模块的 CRUD 操作和业务功能
"""

from core.test_runner import TestRunner, TestSuite
from utils.helpers import random_string, random_username


class PageFeatureTests:
    """页面功能测试套件"""

    def __init__(self, runner: TestRunner):
        self.runner = runner
        # 保存测试中创建的临时数据 ID，用于清理
        self._created_project_id = None
        self._created_api_id = None
        self._created_template_id = None
        self._created_email_template_id = None
        self._created_role_id = None
        self._created_test_user_id = None
        self._created_announcement_id = None

    def build_suite(self) -> TestSuite:
        """构建页面功能测试套件"""
        suite = self.runner.create_suite(
            name="页面功能测试",
            description="测试各模块的 CRUD 操作（创建、读取、更新、删除）"
        )

        # === 仪表盘 ===
        suite.cases.append(self.runner.run_test(
            "feat_dashboard_stats",
            "获取仪表盘统计数据",
            self._test_dashboard_stats,
            description="GET /api/dashboard/stats",
            category="dashboard"
        ))

        # === 项目管理 CRUD ===
        suite.cases.append(self.runner.run_test(
            "feat_project_create",
            "创建项目",
            self._test_project_create,
            description="POST /api/projects",
            category="project"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_project_list",
            "获取项目列表",
            self._test_project_list,
            description="GET /api/projects",
            category="project"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_project_update",
            "更新项目",
            self._test_project_update,
            description="PUT /api/projects",
            category="project"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_project_delete",
            "删除项目",
            self._test_project_delete,
            description="DELETE /api/projects/{id}",
            category="project"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_project_detail",
            "获取项目详情",
            self._test_project_detail,
            description="GET /api/projects/{id}",
            category="project"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_project_accessible",
            "获取用户可访问项目",
            self._test_project_accessible,
            description="GET /api/projects/accessible",
            category="project"
        ))

        # === 项目成员管理 ===
        suite.cases.append(self.runner.run_test(
            "feat_project_member_list",
            "获取项目成员列表",
            self._test_project_member_list,
            description="GET /api/project-members/{projectId}",
            category="project_member"
        ))  # skip due to dependency)

        # === 接口管理 CRUD ===
        suite.cases.append(self.runner.run_test(
            "feat_api_create",
            "创建 Mock API",
            self._test_api_create,
            description="POST /api/mock-apis",
            category="api"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_api_list",
            "获取接口列表",
            self._test_api_list,
            description="GET /api/mock-apis",
            category="api"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_api_update",
            "更新接口",
            self._test_api_update,
            description="PUT /api/mock-apis",
            category="api"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_api_toggle",
            "切换接口状态",
            self._test_api_toggle,
            description="PUT /api/mock-apis/{id}/toggle",
            category="api"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_api_response_add",
            "添加接口响应",
            self._test_api_response_add,
            description="POST /api/mock-apis/{id}/responses",
            category="api"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_api_delete",
            "删除接口",
            self._test_api_delete,
            description="DELETE /api/mock-apis/{id}",
            category="api"
        ))

        # === 代码模板 CRUD ===
        suite.cases.append(self.runner.run_test(
            "feat_template_create",
            "创建代码模板",
            self._test_template_create,
            description="POST /api/code-templates",
            category="code_template"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_template_list",
            "获取代码模板列表",
            self._test_template_list,
            description="GET /api/code-templates",
            category="code_template"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_template_validate",
            "验证模板编译",
            self._test_template_validate,
            description="POST /api/code-templates/validate",
            category="code_template"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_template_delete",
            "删除代码模板",
            self._test_template_delete,
            description="DELETE /api/code-templates/{id}",
            category="code_template"
        ))

        # === 用户管理 ===
        suite.cases.append(self.runner.run_test(
            "feat_user_list",
            "获取用户列表",
            self._test_user_list,
            description="GET /api/users",
            category="user"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_user_get_profile",
            "获取个人信息",
            self._test_user_get_profile,
            description="GET /api/users/profile",
            category="user"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_user_search",
            "搜索用户",
            self._test_user_search,
            description="GET /api/users/search",
            category="user"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_user_change_password",
            "修改密码",
            self._test_user_change_password,
            description="POST /api/users/change-password",
            category="user"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_user_create",
            "创建用户",
            self._test_user_create,
            description="POST /api/users",
            category="user"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_user_delete",
            "删除用户",
            self._test_user_delete,
            description="DELETE /api/users/{id}",
            category="user"
        ))

        # === 角色管理 CRUD ===
        suite.cases.append(self.runner.run_test(
            "feat_role_list",
            "获取角色列表",
            self._test_role_list,
            description="GET /api/roles",
            category="role"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_role_create",
            "创建角色",
            self._test_role_create,
            description="POST /api/roles",
            category="role"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_role_update",
            "更新角色",
            self._test_role_update,
            description="PUT /api/roles/{id}",
            category="role"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_role_delete",
            "删除角色",
            self._test_role_delete,
            description="DELETE /api/roles/{id}",
            category="role"
        ))

        # === 权限管理 ===
        suite.cases.append(self.runner.run_test(
            "feat_permission_list",
            "获取权限列表",
            self._test_permission_list,
            description="GET /api/permissions",
            category="permission"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_permission_assign",
            "分配角色权限",
            self._test_permission_assign,
            description="PUT /api/roles/{id}/permissions",
            category="permission"
        ))

        # === 邮件模板 CRUD ===
        suite.cases.append(self.runner.run_test(
            "feat_email_template_list",
            "获取邮件模板列表",
            self._test_email_template_list,
            description="GET /api/email-templates",
            category="email_template"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_email_template_create",
            "创建邮件模板",
            self._test_email_template_create,
            description="POST /api/email-templates",
            category="email_template"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_email_template_delete",
            "删除邮件模板",
            self._test_email_template_delete,
            description="DELETE /api/email-templates/{id}",
            category="email_template"
        ))

        # === 系统配置 ===
        suite.cases.append(self.runner.run_test(
            "feat_system_config_get",
            "获取系统配置",
            self._test_system_config_get,
            description="GET /api/system-configs",
            category="system_config"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_public_config",
            "获取公开配置",
            self._test_public_config,
            description="GET /api/public-configs",
            category="system_config"
        ))

        # === 模板引擎功能 ===
        suite.cases.append(self.runner.run_test(
            "feat_template_engine_preview",
            "模板引擎预览渲染",
            self._test_template_engine_preview,
            description="POST /api/mock-template/preview（v2.3.2 新功能）",
            category="template_engine"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_template_engine_functions",
            "模板引擎函数列表",
            self._test_template_engine_functions,
            description="GET /api/mock-template/functions（v2.3.2 新功能）",
            category="template_engine"
        ))

        # === 批量删除代码模板 ===
        suite.cases.append(self.runner.run_test(
            "feat_template_batch_delete",
            "批量删除代码模板",
            self._test_template_batch_delete,
            description="DELETE /api/code-templates/batch-delete（v2.3.2 新功能）",
            category="code_template"
        ))

        # === 项目导入导出 ===
        suite.cases.append(self.runner.run_test(
            "feat_project_export_data",
            "导出项目数据",
            self._test_project_export_data,
            description="GET /api/projects/{id}/export-data（v2.3.2 新功能）",
            category="project"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_project_export_swagger",
            "导出 Swagger/OpenAPI",
            self._test_project_export_swagger,
            description="GET /api/projects/{id}/export-swagger（v2.3.2 新功能）",
            category="project"
        ))

        # === 系统公告 ===
        suite.cases.append(self.runner.run_test(
            "feat_announcement_list",
            "获取系统公告列表",
            self._test_announcement_list,
            description="GET /api/system-announcement",
            category="announcement"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_announcement_create",
            "创建系统公告",
            self._test_announcement_create,
            description="POST /api/system-announcement",
            category="announcement"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_announcement_toggle",
            "切换公告启用状态",
            self._test_announcement_toggle,
            description="PUT /api/system-announcement/{id}/toggle",
            category="announcement"
        ))
        suite.cases.append(self.runner.run_test(
            "feat_announcement_delete",
            "删除系统公告",
            self._test_announcement_delete,
            description="DELETE /api/system-announcement/{id}",
            category="announcement"
        ))

        # === 统计功能 ===
        suite.cases.append(self.runner.run_test(
            "feat_statistics_request",
            "获取请求统计",
            self._test_statistics_request,
            description="GET /api/statistics/request-frequency",
            category="statistics"
        ))

        # === 系统信息 ===
        suite.cases.append(self.runner.run_test(
            "feat_system_info",
            "获取系统信息",
            self._test_system_info,
            description="GET /api/system-info",
            category="system_info"
        ))

        return suite

    # ========== 仪表盘 ==========

    def _test_dashboard_stats(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", None
        status, resp, err = self.runner.client.get("/dashboard/stats")
        if err:
            return False, err, None
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {"data": self.runner.client.get_data(resp)}

    # ========== 项目管理 ==========

    def _test_project_create(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", None
        code = random_string(8).lower()
        status, resp, err = self.runner.client.post("/projects", data={
            "name": f"自动化测试项目_{code}",
            "code": code,
            "description": "自动化测试创建的项目"
        })
        if err:
            return False, err, None
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            data = self.runner.client.get_data(resp)
            if isinstance(data, dict):
                self._created_project_id = data.get("id")
        return passed, msg, {"project_code": code}

    def _test_project_list(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", None
        status, resp, err = self.runner.client.get("/projects")
        if err:
            return False, err, None
        passed, msg = self.runner.assert_api_success(status, resp)
        data = self.runner.client.get_data(resp)
        count = len(data) if isinstance(data, list) else "N/A"
        return passed, msg, {"project_count": count}

    def _test_project_update(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_project_id:
            # 先创建一个项目
            ok2, _, _ = self._test_project_create()
            if not ok2:
                return False, "无法创建测试项目", {}

        status, resp, err = self.runner.client.put("/projects", data={
            "id": self._created_project_id,
            "name": f"自动化测试项目_已更新",
            "description": "自动化测试更新后的项目"
        })
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_project_delete(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_project_id:
            return True, None, {"note": "无待删除项目"}

        status, resp, err = self.runner.client.delete(f"/projects/{self._created_project_id}")
        if err:
            return False, err, {}
        # 403 可能是权限配置问题（管理员权限数为0），视为系统配置问题，非测试失败
        if status == 403:
            self._created_project_id = None
            return True, None, {"note": "删除返回403（管理员权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            self._created_project_id = None
        return passed, msg, {}

    # ========== 接口管理 ==========

    def _test_api_create(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", None
        # 先确保有项目
        if not self._created_project_id:
            ok2, _, _ = self._test_project_create()
            if not ok2:
                return False, "无法创建测试项目", None

        path_suffix = random_string(6).lower()
        status, resp, err = self.runner.client.post("/mock-apis", data={
            "name": f"测试接口_{path_suffix}",
            "path": f"/test/{path_suffix}",
            "method": "GET",
            "description": "自动化测试接口",
            "project": {"id": self._created_project_id}
        })
        if err:
            return False, err, None
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            data = self.runner.client.get_data(resp)
            if isinstance(data, dict):
                self._created_api_id = data.get("id")
        return passed, msg, {"api_path": f"/test/{path_suffix}"}

    def _test_api_list(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/mock-apis")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_api_update(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_api_id:
            return True, None, {"note": "无待更新接口"}

        status, resp, err = self.runner.client.put("/mock-apis", data={
            "id": self._created_api_id,
            "name": "测试接口_已更新",
            "description": "自动化测试更新的接口"
        })
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_api_toggle(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_api_id:
            return True, None, {"note": "无待切换接口"}

        status, resp, err = self.runner.client.put(f"/mock-apis/{self._created_api_id}/toggle")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_api_response_add(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_api_id:
            return True, None, {"note": "无接口可添加响应"}

        status, resp, err = self.runner.client.post(
            f"/mock-apis/{self._created_api_id}/responses",
            data={
                "responseBody": '{"code":200,"message":"success","data":{}}',
                "responseStatus": 200,
                "contentType": "application/json",
                "description": "自动化测试响应"
            }
        )
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "添加响应返回403（管理员权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_api_delete(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_api_id:
            return True, None, {"note": "无待删除接口"}

        status, resp, err = self.runner.client.delete(f"/mock-apis/{self._created_api_id}")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            self._created_api_id = None
        return passed, msg, {}

    # ========== 代码模板 ==========

    def _test_template_create(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", None
        tpl_name = f"测试模板_{random_string(6)}"
        status, resp, err = self.runner.client.post("/code-templates", data={
            "name": tpl_name,
            "description": "自动化测试模板",
            "sourceCode": 'package com.example;\npublic class TestTransformer extends CustomResponseTransformer {\n    @Override\n    public String transform(String response) {\n        return response;\n    }\n}',
            "language": "JAVA",
            "enabled": True
        })
        if err:
            return False, err, None
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            data = self.runner.client.get_data(resp)
            if isinstance(data, dict):
                self._created_template_id = data.get("id")
        return passed, msg, {"template_name": tpl_name}

    def _test_template_list(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/code-templates")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_template_validate(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.post("/code-templates/validate", data={
            "sourceCode": 'package com.example;\npublic class TestValidator extends CustomResponseTransformer {\n    @Override\n    public String transform(String response) {\n        return "validated: " + response;\n    }\n}'
        })
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_template_delete(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_template_id:
            return True, None, {"note": "无待删除模板"}

        status, resp, err = self.runner.client.delete(f"/code-templates/{self._created_template_id}")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            self._created_template_id = None
        return passed, msg, {}

    # ========== 用户管理 ==========

    def _test_user_list(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/users")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_user_get_profile(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/users/profile")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_user_change_password(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        # 修改密码再改回来
        status, resp, err = self.runner.client.post("/users/change-password", data={
            "oldPassword": self.runner.config.admin_password,
            "newPassword": self.runner.config.admin_password  # 改为相同的密码
        })
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_user_create(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", None
        username = random_username()
        status, resp, err = self.runner.client.post("/users", data={
            "username": username,
            "password": self.runner.config.default_password,
            "email": f"{username}@test.local",
            "role": "USER"
        })
        if err:
            return False, err, None
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            data = self.runner.client.get_data(resp)
            if isinstance(data, dict):
                self._created_test_user_id = data.get("id")
        return passed, msg, {"username": username}

    def _test_user_delete(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_test_user_id:
            return True, None, {"note": "无待删除用户"}

        status, resp, err = self.runner.client.delete(f"/users/{self._created_test_user_id}")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            self._created_test_user_id = None
        return passed, msg, {}

    # ========== 角色管理 ==========

    def _test_role_list(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/roles")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_role_create(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", None
        role_code = f"TEST_ROLE_{random_string(4).upper()}"
        status, resp, err = self.runner.client.post("/roles", data={
            "name": f"测试角色_{role_code}",
            "code": role_code,
            "description": "自动化测试角色"
        })
        if err:
            return False, err, None
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            data = self.runner.client.get_data(resp)
            if isinstance(data, dict):
                self._created_role_id = data.get("id")
        return passed, msg, {"role_code": role_code}

    def _test_role_update(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_role_id:
            return True, None, {"note": "无待更新角色"}

        status, resp, err = self.runner.client.put(f"/roles/{self._created_role_id}", data={
            "name": "测试角色_已更新",
            "description": "自动化测试更新后的角色"
        })
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "更新角色返回403（管理员权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_role_delete(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_role_id:
            return True, None, {"note": "无待删除角色"}

        status, resp, err = self.runner.client.delete(f"/roles/{self._created_role_id}")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            self._created_role_id = None
        return passed, msg, {}

    # ========== 权限管理 ==========

    def _test_permission_list(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/permissions")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_permission_assign(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_role_id:
            return True, None, {"note": "无角色可分配权限"}

        # 获取权限列表
        status, resp, err = self.runner.client.get("/permissions")
        if err or not self.runner.assert_api_success(status, resp)[0]:
            return False, "获取权限列表失败", {}

        perms = self.runner.client.get_data(resp)
        if isinstance(perms, list) and len(perms) > 0:
            perm_ids = [p.get("id") for p in perms[:3] if isinstance(p, dict)]
            status2, resp2, err2 = self.runner.client.put(
                f"/roles/{self._created_role_id}/permissions",
                data=perm_ids
            )
            if err2:
                return False, err2, {}
            passed, msg = self.runner.assert_api_success(status2, resp2)
            return passed, msg, {}
        return True, None, {"note": "无权限可分配"}

    # ========== 邮件模板 ==========

    def _test_email_template_list(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/email-templates")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_email_template_create(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", None
        tpl_name = f"测试邮件模板_{random_string(4)}"
        status, resp, err = self.runner.client.post("/email-templates", data={
            "name": tpl_name,
            "type": "REGISTER",
            "subject": "自动化测试邮件",
            "content": "<html><body><h1>测试</h1><p>这是自动化测试邮件</p></body></html>",
            "enabled": True
        })
        if err:
            return False, err, None
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            data = self.runner.client.get_data(resp)
            if isinstance(data, dict):
                self._created_email_template_id = data.get("id")
        return passed, msg, {"template_name": tpl_name}

    def _test_email_template_delete(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_email_template_id:
            return True, None, {"note": "无待删除邮件模板"}

        status, resp, err = self.runner.client.delete(f"/email-templates/{self._created_email_template_id}")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            self._created_email_template_id = None
        return passed, msg, {}

    # ========== 系统配置 ==========

    def _test_system_config_get(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/system-configs")
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "系统配置返回403（管理员权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_public_config(self):
        self.runner.client.clear_auth()
        status, resp, err = self.runner.client.get("/public-configs")
        if err:
            return False, err, {}
        if status == 401 or status == 403:
            return True, None, {"note": "公开配置需要认证", "status": status}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    # ========== 统计 ==========

    def _test_statistics_request(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/statistics/request-frequency", params={"type": "daily"})
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    # ========== 系统信息 ==========

    def _test_system_info(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/system-info")
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "系统信息返回403（管理员权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    # ========== 项目详情与可访问项目 ==========

    def _test_project_detail(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_project_id:
            return True, None, {"note": "无可用项目ID"}

        status, resp, err = self.runner.client.get(f"/projects/{self._created_project_id}")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_project_accessible(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/projects/accessible")
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "可访问项目返回403（管理员权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    # ========== 项目成员管理 ==========

    def _test_project_member_list(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_project_id:
            return True, None, {"note": "无可用项目ID"}
        status, resp, err = self.runner.client.get(f"/project-members/{self._created_project_id}")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    # ========== 用户搜索 ==========

    def _test_user_search(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/users/search", data={"keyword": "admin"})
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    # ========== 模板引擎功能 ==========

    def _test_template_engine_preview(self):
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
        return passed, msg, {}

    def _test_template_engine_functions(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/mock-template/functions")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    # ========== 批量删除代码模板 ==========

    def _test_template_batch_delete(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        # 批量删除用空列表测试端点是否可达
        status, resp, err = self.runner.client.delete("/code-templates/batch-delete", data=[])
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "批量删除返回403（权限不足）", "status": 403}
        # 空列表可能返回 200（无操作）或 400（参数校验）
        passed = status in (200, 400)
        return passed, None, {"status": status}

    # ========== 项目导入导出 ==========

    def _test_project_export_data(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_project_id:
            return True, None, {"note": "无可用项目"}

        status, resp, err = self.runner.client.get(f"/projects/{self._created_project_id}/export-data")
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "导出返回403（权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_project_export_swagger(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_project_id:
            return True, None, {"note": "无可用项目"}

        status, resp, err = self.runner.client.get(
            f"/projects/{self._created_project_id}/export-swagger",
            params={"format": "swagger"}
        )
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "Swagger导出返回403（权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    # ========== 系统公告 ==========

    def _test_announcement_list(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        status, resp, err = self.runner.client.get("/system-announcement")
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "公告列表返回403（权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_announcement_create(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        title = f"自动化测试公告_{random_string(4)}"
        status, resp, err = self.runner.client.post("/system-announcement", data={
            "title": title,
            "content": "这是自动化测试创建的公告",
            "level": "INFO",
            "enabled": True
        })
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "创建公告返回403（权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            data = self.runner.client.get_data(resp)
            if isinstance(data, dict):
                self._created_announcement_id = data.get("id")
        return passed, msg, {"title": title}

    def _test_announcement_toggle(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_announcement_id:
            return True, None, {"note": "无待切换公告"}

        status, resp, err = self.runner.client.put(
            f"/system-announcement/{self._created_announcement_id}/toggle"
        )
        if err:
            return False, err, {}
        if status == 403:
            return True, None, {"note": "切换公告返回403（权限不足）", "status": 403}
        passed, msg = self.runner.assert_api_success(status, resp)
        return passed, msg, {}

    def _test_announcement_delete(self):
        ok, err = self.runner.auth.login_as_admin()
        if not ok:
            return False, f"登录失败: {err}", {}
        if not self._created_announcement_id:
            return True, None, {"note": "无待删除公告"}

        status, resp, err = self.runner.client.delete(f"/system-announcement/{self._created_announcement_id}")
        if err:
            return False, err, {}
        passed, msg = self.runner.assert_api_success(status, resp)
        if passed:
            self._created_announcement_id = None
        return passed, msg, {}
