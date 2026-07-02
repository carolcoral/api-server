# 自动化测试工具

Mock Server 系统全功能自动化测试工具，支持页面访问测试、功能测试、模板引擎测试、导入导出测试、响应管理测试、RBAC 权限测试、AI 功能测试等，覆盖 9 大领域 90+ 用例。

## 目录结构

```
auto_test_tool/
├── config/
│   └── auto_test.config      # 统一配置文件
├── core/
│   ├── __init__.py
│   ├── config_loader.py      # 配置加载器
│   ├── http_client.py        # HTTP 客户端封装
│   ├── auth_manager.py       # 认证管理器（JWT Token 管理）
│   ├── test_runner.py        # 测试运行器
│   └── report_generator.py   # 报告生成器
├── tests/
│   ├── __init__.py
│   ├── page_access_tests.py       # 19 页面路由可访问性测试
│   ├── page_feature_tests.py      # 44 CRUD 操作测试（含模板引擎/导入导出/系统公告）
│   ├── rbac_tests.py              # 10 RBAC 权限边界测试（30+ 权限）
│   ├── ai_tests.py                # 11 AI 模型/对话/生成测试
│   ├── security_tests.py          # 14 安全特性测试
│   ├── swagger_import_tests.py    # 5 Swagger 导入记录性测试
│   ├── template_engine_tests.py   # 8 模板引擎测试（v2.3.2 新功能）
│   ├── project_export_import_tests.py  # 8 项目导入导出测试（v2.3.2 新功能）
│   └── response_management_tests.py    # 5 响应管理测试（v2.3.2 Bug 修复）
├── models/
│   ├── __init__.py
│   └── ai_model_manager.py   # AI 模型管理器（自动切换、告警）
├── utils/
│   ├── __init__.py
│   └── helpers.py            # 工具函数
├── reports/                  # 测试报告输出目录
├── main.py                   # 主入口
├── requirements.txt          # Python 依赖
└── README.md                 # 说明文档
```

## 快速开始

```bash
# 安装依赖
pip install -r requirements.txt

# 确保 Mock Server 已启动
cd /workspace && ./run.sh

# 运行全部测试
python main.py

# 运行指定测试
python main.py --test page_access       # 页面访问测试
python main.py --test page_features     # 页面功能测试
python main.py --test template_engine   # 模板引擎测试（v2.3.2）
python main.py --test export_import     # 导入导出测试（v2.3.2）
python main.py --test response_mgmt     # 响应管理测试（v2.3.2）
python main.py --test rbac              # RBAC 权限测试
python main.py --test ai                # AI 功能测试
python main.py --test security          # 安全特性测试

# 跳过某些测试
python main.py --skip-ai --skip-rbac
python main.py --skip-template --skip-export

# 列出所有测试模块
python main.py --list
```
