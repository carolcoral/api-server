#!/usr/bin/env python3
"""
Mock Server 自动化测试工具 - 主入口
=====================================

用法:
    python main.py                      # 运行全部测试
    python main.py --test page_access   # 运行页面访问测试
    python main.py --test rbac          # 运行 RBAC 测试
    python main.py --test ai            # 运行 AI 测试
    python main.py --test security      # 运行安全测试
    python main.py --test page_features # 运行页面功能测试
    python main.py --skip-ai            # 跳过 AI 测试
    python main.py --skip-rbac          # 跳过 RBAC 测试
    python main.py --list               # 列出所有测试套件
"""

import os
import sys
import time
import argparse
from typing import List

# 添加项目根目录到 Python 路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from core.config_loader import ConfigLoader
from core.http_client import HttpClient
from core.auth_manager import AuthManager
from core.test_runner import TestRunner, TestReport
from core.report_generator import ReportGenerator
from models.ai_model_manager import AIModelManager

from tests.page_access_tests import PageAccessTests
from tests.page_feature_tests import PageFeatureTests
from tests.rbac_tests import RBACTests
from tests.ai_tests import AITests
from tests.security_tests import SecurityTests
from tests.swagger_import_tests import SwaggerImportTests
from tests.template_engine_tests import TemplateEngineTests
from tests.project_export_import_tests import ProjectExportImportTests
from tests.response_management_tests import ResponseManagementTests


class AutoTestTool:
    """自动化测试工具主类"""

    def __init__(self, config_path: str = None):
        print("\n" + "=" * 70)
        print("  Mock Server 自动化测试工具 v1.1")
        print("=" * 70)

        # 加载配置
        self.config = ConfigLoader(config_path)
        self._print_config_summary()

        # 验证配置
        warnings = self.config.validate()
        if warnings:
            print("\n⚠️  配置警告:")
            for w in warnings:
                print(f"   - {w}")

        # 初始化核心组件
        self.client = HttpClient(self.config)
        self.auth = AuthManager(self.client, self.config)
        self.runner = TestRunner(self.config, self.client, self.auth)
        self.ai_manager = AIModelManager(self.config) if self.config.test_ai_features else None

        # 初始化测试模块
        self.test_modules = {
            "page_access": PageAccessTests(self.runner),
            "page_features": PageFeatureTests(self.runner),
            "rbac": RBACTests(self.runner),
            "ai": AITests(self.runner, self.ai_manager) if self.ai_manager else None,
            "security": SecurityTests(self.runner),
            "swagger_import": SwaggerImportTests(self.runner),
            "template_engine": TemplateEngineTests(self.runner),
            "export_import": ProjectExportImportTests(self.runner),
            "response_mgmt": ResponseManagementTests(self.runner),
        }

    def _print_config_summary(self):
        """打印配置摘要"""
        print(f"\n📋 配置信息:")
        print(f"   系统地址: {self.config.base_url}")
        print(f"   管理员账号: {self.config.admin_username}")
        print(f"   测试范围: ", end="")
        scopes = []
        if self.config.test_public_pages: scopes.append("公开页面")
        if self.config.test_authenticated_pages: scopes.append("认证页面")
        if self.config.test_page_features: scopes.append("页面功能")
        if self.config.test_rbac: scopes.append("RBAC权限")
        if self.config.test_ai_features: scopes.append("AI功能")
        if self.config.test_security_features: scopes.append("安全特性")
        print(", ".join(scopes))
        if self.config.test_ai_features:
            print(f"   AI 模型: {', '.join(self.config.ai_models)}")

    def run_all(self, skip: List[str] = None) -> TestReport:
        """运行全部测试"""
        skip = skip or []
        self.runner.report.start_time = time.time()
        self.runner.report.config_warnings = self.config.validate()

        # 检查系统连通性
        print("\n🔍 检查系统连通性...")
        if not self._check_connectivity():
            print("❌ 无法连接到目标系统，请确保 Mock Server 已启动")
            self.runner.report.end_time = time.time()
            return self.runner.report

        # AI 模型预检测
        if self.config.test_ai_features and self.ai_manager and "ai" not in skip:
            print("\n🤖 检测 AI 模型可用性...")
            results = self.ai_manager.test_all_models()
            available = [r for r in results if r[1]]
            failed = [r for r in results if not r[1]]
            print(f"   可用模型: {len(available)}/{len(results)}")
            for r in available:
                print(f"     ✓ {r[0]}")
            for r in failed:
                print(f"     ✗ {r[0]}: {r[2][:80]}")
            if len(available) == 0:
                print("   ⚠️ 所有 AI 模型均不可用！AI 测试将跳过。")
                skip.append("ai")

        # 管理员登录
        print("\n🔐 管理员登录...")
        ok, err = self.auth.login_as_admin()
        if not ok:
            print(f"   ❌ 管理员登录失败: {err}")
            self.runner.report.end_time = time.time()
            return self.runner.report
        print(f"   ✓ 管理员登录成功 (权限数: {len(self.auth.get_current_permissions())})")

        # 运行各测试套件
        test_order = [
            ("page_access", "页面访问测试"),
            ("page_features", "页面功能测试"),
            ("template_engine", "模板引擎测试"),
            ("export_import", "项目导入导出测试"),
            ("response_mgmt", "响应管理测试"),
            ("rbac", "RBAC 权限控制测试"),
            ("ai", "AI 功能测试"),
            ("security", "安全特性测试"),
            ("swagger_import", "Swagger 导入测试"),
        ]

        for module_name, display_name in test_order:
            if module_name in skip:
                print(f"\n⏭️  跳过: {display_name}")
                continue

            if not self._is_module_enabled(module_name):
                print(f"\n⏭️  跳过: {display_name} (配置中已禁用)")
                continue

            module = self.test_modules.get(module_name)
            if module is None:
                continue

            print(f"\n{'='*70}")
            print(f"  运行: {display_name}")
            print(f"{'='*70}")

            suite = module.build_suite()
            self.runner.run_suite(suite)
            self.runner.add_suite(suite)

            # 恢复管理员登录状态
            if module_name in ("rbac", "security"):
                self.auth.login_as_admin()

        # 清理测试账号
        if self.config.cleanup_after_test and self.config.auto_create_accounts:
            print("\n🧹 清理测试账号...")
            self.auth.cleanup_test_users()
            print("   ✓ 清理完成")

        # 完成报告
        report = self.runner.finalize_report()

        # 生成报告文件
        print("\n📝 生成测试报告...")
        generator = ReportGenerator(self.config, report)
        files = generator.generate()
        for f in files:
            print(f"   ✓ {f}")

        # 打印汇总
        self._print_summary(report)

        return report

    def run_single(self, module_name: str) -> TestReport:
        """运行单个测试模块"""
        self.runner.report.start_time = time.time()
        self.runner.report.config_warnings = self.config.validate()

        if not self._check_connectivity():
            print("❌ 无法连接到目标系统")
            return self.runner.report

        if module_name == "ai" and self.ai_manager:
            print("\n🤖 检测 AI 模型可用性...")
            results = self.ai_manager.test_all_models()
            available = [r for r in results if r[1]]
            if len(available) == 0:
                print("   ⚠️ 所有 AI 模型均不可用！")
                return self.runner.report

        ok, err = self.auth.login_as_admin()
        if not ok:
            print(f"❌ 管理员登录失败: {err}")
            return self.runner.report

        module = self.test_modules.get(module_name)
        if module is None:
            print(f"❌ 未知测试模块: {module_name}")
            return self.runner.report

        suite = module.build_suite()
        self.runner.run_suite(suite)
        self.runner.add_suite(suite)

        report = self.runner.finalize_report()
        generator = ReportGenerator(self.config, report)
        files = generator.generate()
        for f in files:
            print(f"   ✓ {f}")

        self._print_summary(report)
        return report

    def _check_connectivity(self) -> bool:
        """检查系统连通性"""
        status, err = self.client.visit_page("/")
        if err or status == 0:
            print(f"   ❌ 无法连接: {err}")
            return False
        print(f"   ✓ 系统可达 (HTTP {status})")
        return True

    def _is_module_enabled(self, module_name: str) -> bool:
        """检查测试模块是否在配置中启用"""
        mapping = {
            "page_access": self.config.test_public_pages or self.config.test_authenticated_pages,
            "page_features": self.config.test_page_features,
            "rbac": self.config.test_rbac,
            "ai": self.config.test_ai_features,
            "security": self.config.test_security_features,
            "template_engine": self.config.test_page_features,
            "export_import": self.config.test_page_features,
            "response_mgmt": self.config.test_page_features,
            "swagger_import": True,
        }
        return mapping.get(module_name, True)

    def _print_summary(self, report: TestReport):
        """打印测试汇总"""
        print("\n" + "=" * 70)
        print("  测试结果汇总")
        print("=" * 70)
        print(f"  总用例: {report.total_cases}")
        print(f"  通过:   {report.total_passed} ✓")
        print(f"  失败:   {report.total_failed} ✗")
        print(f"  错误:   {report.total_errors} !")
        print(f"  通过率: {report.pass_rate:.1f}%")
        print(f"  总耗时: {report.duration_seconds:.2f}s")

        # 按套件汇总
        print(f"\n  各套件详情:")
        for suite in report.suites:
            sr = (suite.passed / suite.total * 100) if suite.total > 0 else 0
            icon = "✅" if sr >= 90 else ("⚠️" if sr >= 70 else "❌")
            print(f"    {icon} {suite.name}: {suite.passed}/{suite.total} 通过 ({sr:.1f}%)")

        if report.total_failed > 0 or report.total_errors > 0:
            print(f"\n  失败/错误详情:")
            for suite in report.suites:
                for case in suite.cases:
                    if case.status.value in ("FAIL", "ERROR"):
                        print(f"    ❌ [{suite.name}] {case.name}")
                        if case.error_message:
                            print(f"       {case.error_message[:200]}")

        print("\n" + "=" * 70)

    def list_modules(self):
        """列出所有测试模块"""
        print("\n可用的测试模块:")
        for name, module in self.test_modules.items():
            if module:
                desc = {
                    "page_access": "页面访问测试 - 测试所有 19 个路由的可访问性",
                    "page_features": "页面功能测试 - 测试各模块 CRUD 操作（含模板引擎/导入导出/系统公告）",
                    "rbac": "RBAC 权限控制测试 - 测试角色权限边界（30+ 项权限含 v2.3.2 新增）",
                    "ai": "AI 功能测试 - 测试模型、对话、生成",
                    "security": "安全特性测试 - JWT/密码/注入/CORS/Swagger",
                    "swagger_import": "Swagger 导入测试 - 记录性测试（全部跳过）",
                    "template_engine": "模板引擎测试 - {{name()}}/{{email()}}/{{uuid()}} 渲染与权限（v2.3.2 新功能）",
                    "export_import": "项目导入导出测试 - JSON/Swagger 2.0/3.0 导出导入（v2.3.2 新功能）",
                    "response_mgmt": "响应管理测试 - 默认响应唯一性/加权随机返回（v2.3.2 Bug 修复）",
                }.get(name, "")
                enabled = self._is_module_enabled(name)
                status = "启用" if enabled else "禁用"
                print(f"  {name:20s} [{status}] {desc}")

    def cleanup(self):
        """清理资源"""
        self.client.close()


def main():
    parser = argparse.ArgumentParser(
        description="Mock Server 自动化测试工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python main.py                        # 运行全部测试
  python main.py --test page_access     # 仅运行页面访问测试
  python main.py --test template_engine # 仅运行模板引擎测试（v2.3.2）
  python main.py --test rbac            # 仅运行 RBAC 测试
  python main.py --skip-ai --skip-rbac  # 跳过 AI 和 RBAC 测试
  python main.py --list                 # 列出所有测试模块
  python main.py --config custom.config # 使用自定义配置文件
        """
    )
    parser.add_argument("--config", "-c", default=None, help="配置文件路径")
    parser.add_argument("--test", "-t", default=None, help="运行指定测试模块")
    parser.add_argument("--skip", "-s", nargs="*", default=[], help="跳过的测试模块")
    parser.add_argument("--list", "-l", action="store_true", help="列出所有测试模块")
    parser.add_argument("--skip-ai", action="store_true", help="跳过 AI 测试")
    parser.add_argument("--skip-rbac", action="store_true", help="跳过 RBAC 测试")
    parser.add_argument("--skip-security", action="store_true", help="跳过安全测试")
    parser.add_argument("--skip-features", action="store_true", help="跳过页面功能测试")
    parser.add_argument("--skip-template", action="store_true", help="跳过模板引擎测试")
    parser.add_argument("--skip-export", action="store_true", help="跳过导入导出测试")
    parser.add_argument("--skip-response", action="store_true", help="跳过响应管理测试")

    args = parser.parse_args()

    tool = AutoTestTool(config_path=args.config)

    try:
        if args.list:
            tool.list_modules()
            return

        # 处理快捷跳过参数
        skip_list = list(args.skip) if args.skip else []
        if args.skip_ai: skip_list.append("ai")
        if args.skip_rbac: skip_list.append("rbac")
        if args.skip_security: skip_list.append("security")
        if args.skip_features: skip_list.append("page_features")
        if args.skip_template: skip_list.append("template_engine")
        if args.skip_export: skip_list.append("export_import")
        if args.skip_response: skip_list.append("response_mgmt")

        if args.test:
            # 运行指定模块
            if args.test not in tool.test_modules:
                print(f"❌ 未知测试模块: {args.test}")
                tool.list_modules()
                sys.exit(1)
            tool.run_single(args.test)
        else:
            # 运行全部
            tool.run_all(skip=skip_list)

    except KeyboardInterrupt:
        print("\n\n⚠️  测试被用户中断")
    except Exception as e:
        print(f"\n❌ 测试异常: {e}")
        import traceback
        traceback.print_exc()
    finally:
        tool.cleanup()


if __name__ == "__main__":
    main()
