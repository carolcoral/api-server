"""
报告生成器 - 生成 JSON / HTML / Markdown 格式测试报告
"""

import os
import json
import time
from typing import List
from datetime import datetime
from .test_runner import TestReport, TestStatus


class ReportGenerator:
    """测试报告生成器"""

    def __init__(self, config, report: TestReport):
        self.config = config
        self.report = report
        self.output_dir = os.path.abspath(config.output_dir)
        os.makedirs(self.output_dir, exist_ok=True)

    def generate(self) -> List[str]:
        """生成所有格式的报告，返回生成的文件路径列表"""
        files = []
        formats = self.config.report_formats

        for fmt in formats:
            fmt_lower = fmt.lower().strip()
            if fmt_lower == "json":
                files.append(self._generate_json())
            elif fmt_lower == "html":
                files.append(self._generate_html())
            elif fmt_lower == "markdown":
                files.append(self._generate_markdown())

        return files

    def _get_timestamp(self) -> str:
        return datetime.now().strftime("%Y%m%d_%H%M%S")

    # ---------- JSON 报告 ----------

    def _generate_json(self) -> str:
        filename = f"test_report_{self._get_timestamp()}.json"
        filepath = os.path.join(self.output_dir, filename)

        data = {
            "title": "API Server 自动化测试报告",
            "timestamp": datetime.now().isoformat(),
            "summary": {
                "total_cases": self.report.total_cases,
                "passed": self.report.total_passed,
                "failed": self.report.total_failed,
                "errors": self.report.total_errors,
                "pass_rate": f"{self.report.pass_rate:.1f}%",
                "duration_seconds": f"{self.report.duration_seconds:.2f}",
            },
            "config_warnings": self.report.config_warnings,
            "suites": []
        }

        for suite in self.report.suites:
            suite_data = {
                "name": suite.name,
                "description": suite.description,
                "summary": {
                    "total": suite.total,
                    "passed": suite.passed,
                    "failed": suite.failed,
                    "skipped": suite.skipped,
                    "errors": suite.errors,
                    "duration_seconds": f"{suite.duration_seconds:.2f}",
                },
                "cases": []
            }
            for case in suite.cases:
                suite_data["cases"].append({
                    "id": case.id,
                    "name": case.name,
                    "description": case.description,
                    "status": case.status.value,
                    "duration_ms": round(case.duration_ms, 2),
                    "error_message": case.error_message,
                    "details": case.details,
                })
            data["suites"].append(suite_data)

        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        return filepath

    # ---------- HTML 报告 ----------

    def _generate_html(self) -> str:
        filename = f"test_report_{self._get_timestamp()}.html"
        filepath = os.path.join(self.output_dir, filename)

        html = self._render_html()
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(html)

        return filepath

    def _render_html(self) -> str:
        """渲染 HTML 报告"""
        pass_rate = self.report.pass_rate
        rate_color = "#4CAF50" if pass_rate >= 90 else ("#FF9800" if pass_rate >= 70 else "#F44336")

        suites_html = ""
        for suite in self.report.suites:
            # 有效通过率（排除 SKIP 用例）
            effective = suite.passed + suite.failed + suite.errors
            suite_pass_rate = (suite.passed / effective * 100) if effective > 0 else 0
            s_color = "#4CAF50" if suite_pass_rate >= 90 else ("#FF9800" if suite_pass_rate >= 70 else "#F44336")

            cases_html = ""
            for case in suite.cases:
                status_color = {
                    TestStatus.PASS: "#4CAF50",
                    TestStatus.FAIL: "#F44336",
                    TestStatus.ERROR: "#F44336",
                    TestStatus.SKIP: "#9E9E9E",
                    TestStatus.WARN: "#FF9800",
                }.get(case.status, "#9E9E9E")

                error_html = ""
                if case.error_message:
                    error_html = f"""
                    <div style="margin-top:6px;padding:8px;background:#FFF3F3;border-left:3px solid #F44336;
                                font-size:12px;color:#D32F2F;white-space:pre-wrap;border-radius:0 4px 4px 0;">
                        {self._escape_html(case.error_message[:500])}
                    </div>"""

                cases_html += f"""
                <tr>
                    <td style="color:{status_color};font-weight:bold;">{case.status.value}</td>
                    <td>{self._escape_html(case.name)}</td>
                    <td style="font-size:12px;color:#666;">{self._escape_html(case.description)}</td>
                    <td style="text-align:right;">{case.duration_ms:.0f}ms</td>
                    <td>{error_html}</td>
                </tr>"""

            suites_html += f"""
            <div style="margin-bottom:24px;background:white;border-radius:8px;overflow:hidden;
                        box-shadow:0 1px 3px rgba(0,0,0,0.1);">
                <div style="padding:12px 20px;background:#f8f9fa;border-bottom:1px solid #e0e0e0;
                            display:flex;justify-content:space-between;align-items:center;">
                    <div>
                        <strong style="font-size:16px;">{self._escape_html(suite.name)}</strong>
                        <span style="color:#666;margin-left:12px;font-size:13px;">{self._escape_html(suite.description)}</span>
                    </div>
                    <div style="text-align:right;">
                        <span style="color:{s_color};font-weight:bold;font-size:18px;">{suite_pass_rate:.1f}%</span>
                        <span style="color:#666;margin-left:8px;font-size:12px;">
                            通过{suite.passed}/{suite.total} | 失败{suite.failed} | 错误{suite.errors}
                        </span>
                    </div>
                </div>
                <table style="width:100%;border-collapse:collapse;">
                    <thead>
                        <tr style="background:#f5f5f5;">
                            <th style="padding:8px 16px;text-align:left;width:60px;">状态</th>
                            <th style="padding:8px 16px;text-align:left;">测试用例</th>
                            <th style="padding:8px 16px;text-align:left;width:200px;">说明</th>
                            <th style="padding:8px 16px;text-align:right;width:80px;">耗时</th>
                            <th style="padding:8px 16px;text-align:left;width:300px;">错误详情</th>
                        </tr>
                    </thead>
                    <tbody>{cases_html}</tbody>
                </table>
            </div>"""

        # 配置警告
        warnings_html = ""
        if self.report.config_warnings:
            warnings_items = "".join(
                f'<li style="color:#F44336;">{self._escape_html(w)}</li>'
                for w in self.report.config_warnings
            )
            warnings_html = f"""
            <div style="margin-bottom:24px;padding:16px;background:#FFF3E0;border-radius:8px;
                        border-left:4px solid #FF9800;">
                <strong style="color:#E65100;">配置警告</strong>
                <ul style="margin:8px 0 0 20px;">{warnings_items}</ul>
            </div>"""

        return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>API Server 自动化测试报告</title>
    <style>
        * {{ margin:0; padding:0; box-sizing:border-box; }}
        body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                background: #f0f2f5; color: #333; padding: 24px; }}
        .container {{ max-width: 1200px; margin: 0 auto; }}
        .header {{ background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                   color: white; padding: 32px; border-radius: 12px; margin-bottom: 24px; }}
        .header h1 {{ font-size: 28px; margin-bottom: 8px; }}
        .header .subtitle {{ opacity: 0.85; font-size: 14px; }}
        .summary {{ display: flex; gap: 16px; margin-bottom: 24px; }}
        .summary-card {{ flex:1; background:white; padding:20px; border-radius:8px;
                         box-shadow:0 1px 3px rgba(0,0,0,0.1); text-align:center; }}
        .summary-card .number {{ font-size:32px; font-weight:bold; color:#667eea; }}
        .summary-card .label {{ font-size:13px; color:#999; margin-top:4px; }}
        .pass-rate {{ background:white; padding:16px 20px; border-radius:8px;
                      box-shadow:0 1px 3px rgba(0,0,0,0.1); margin-bottom:24px;
                      display:flex; align-items:center; gap:16px; }}
        .pass-rate .big {{ font-size:48px; font-weight:bold; color:{rate_color}; }}
        .footer {{ text-align:center; color:#999; font-size:12px; margin-top:32px; padding:16px; }}
        tr:hover {{ background:#f8f9fa; }}
    </style>
</head>
<body>
<div class="container">
    <div class="header">
        <h1>API Server 自动化测试报告</h1>
        <div class="subtitle">生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')} |
                          总耗时: {self.report.duration_seconds:.2f}s</div>
    </div>
    <div class="summary">
        <div class="summary-card">
            <div class="number">{self.report.total_cases}</div>
            <div class="label">测试用例总数</div>
        </div>
        <div class="summary-card">
            <div class="number" style="color:#4CAF50;">{self.report.total_passed}</div>
            <div class="label">通过</div>
        </div>
        <div class="summary-card">
            <div class="number" style="color:#F44336;">{self.report.total_failed}</div>
            <div class="label">失败</div>
        </div>
        <div class="summary-card">
            <div class="number" style="color:#FF9800;">{self.report.total_errors}</div>
            <div class="label">错误</div>
        </div>
    </div>
    <div class="pass-rate">
        <div class="big">{pass_rate:.1f}%</div>
        <div style="color:#666;font-size:14px;">整体通过率</div>
    </div>
    {warnings_html}
    {suites_html}
    <div class="footer">
        API Server Auto Test Tool · Generated at {datetime.now().isoformat()}
    </div>
</div>
</body>
</html>"""

    # ---------- Markdown 报告 ----------

    def _generate_markdown(self) -> str:
        filename = f"test_report_{self._get_timestamp()}.md"
        filepath = os.path.join(self.output_dir, filename)

        lines = [
            "# API Server 自动化测试报告",
            "",
            f"**生成时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
            f"**总耗时**: {self.report.duration_seconds:.2f}s",
            "",
            "## 测试概览",
            "",
            f"| 指标 | 数值 |",
            f"|------|------|",
            f"| 测试用例总数 | {self.report.total_cases} |",
            f"| 通过 | {self.report.total_passed} |",
            f"| 失败 | {self.report.total_failed} |",
            f"| 错误 | {self.report.total_errors} |",
            f"| **通过率** | **{self.report.pass_rate:.1f}%** |",
            "",
        ]

        if self.report.config_warnings:
            lines.append("## ⚠️ 配置警告")
            lines.append("")
            for w in self.report.config_warnings:
                lines.append(f"- ❗ {w}")
            lines.append("")

        for suite in self.report.suites:
            # 有效通过率（排除 SKIP 用例）
            effective = suite.passed + suite.failed + suite.errors
            suite_pass_rate = (suite.passed / effective * 100) if effective > 0 else 0
            lines.append(f"## {suite.name}")
            lines.append("")
            if suite.description:
                lines.append(f"*{suite.description}*")
                lines.append("")
            lines.append(f"- **通过率**: {suite_pass_rate:.1f}% ({suite.passed}/{suite.total})")
            lines.append(f"- **耗时**: {suite.duration_seconds:.2f}s")
            lines.append("")

            lines.append("| 状态 | 测试用例 | 说明 | 耗时 |")
            lines.append("|------|----------|------|------|")
            for case in suite.cases:
                status_icon = {
                    TestStatus.PASS: "✅",
                    TestStatus.FAIL: "❌",
                    TestStatus.ERROR: "💥",
                    TestStatus.SKIP: "⏭️",
                    TestStatus.WARN: "⚠️",
                }.get(case.status, "❓")
                lines.append(
                    f"| {status_icon} {case.status.value} | {case.name} | "
                    f"{case.description} | {case.duration_ms:.0f}ms |"
                )
                if case.error_message:
                    lines.append(f"| | | **错误**: {case.error_message[:200]} | |")
            lines.append("")

        with open(filepath, "w", encoding="utf-8") as f:
            f.write("\n".join(lines))

        return filepath

    @staticmethod
    def _escape_html(text: str) -> str:
        """转义 HTML 特殊字符"""
        return (text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace('"', "&quot;")
                .replace("'", "&#39;"))
