#!/usr/bin/env python3
"""
快马日结项目部署脚本
功能：
1. 通过 SSH 登录服务器，上传并执行 SQL 脚本
2. 部署 Java 后端（jar 放 /root/webapp/，启动 start-with-sms.sh）
3. 构建并部署 Web 管理前台到 /root/html/
"""

import os
import sys
import time
import paramiko
from pathlib import Path

# ==================== 配置 ====================
SERVER_HOST = "8.148.144.146"
SERVER_USER = "root"
SERVER_PASSWORD = "Abc223344"

MYSQL_USER = "root"
MYSQL_PASSWORD = "11112222"
MYSQL_DATABASE = "kuaima"

LOCAL_PROJECT_ROOT = r"E:\Kuaima"
BACKEND_PATH = os.path.join(LOCAL_PROJECT_ROOT, "kuaima_backend")
ADMIN_WEB_PATH = os.path.join(LOCAL_PROJECT_ROOT, "admin-web")

REMOTE_WEBAPP_DIR = "/root/webapp"
REMOTE_HTML_DIR = "/root/html"
REMOTE_START_SCRIPT = f"{REMOTE_WEBAPP_DIR}/start-with-sms.sh"

SQL_SCRIPTS = [
    "db/payroll_approve_schema.sql",
    "db/boss_transfer_schema.sql",
    "db/boss_balance_schema.sql",
    "db/enterprise_schema.sql",
    "db/boss_payroll_employees_schema.sql",
    "db/payroll_fix_actor_name.sql",
]


def execute_sql_via_ssh():
    """通过 SSH 登录服务器，上传并执行 SQL 脚本"""
    print("=" * 60)
    print("开始执行 SQL 脚本（通过 SSH）")
    print("=" * 60)

    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(SERVER_HOST, username=SERVER_USER, password=SERVER_PASSWORD)
    sftp = ssh.open_sftp()

    try:
        for sql_file in SQL_SCRIPTS:
            local_path = os.path.join(LOCAL_PROJECT_ROOT, sql_file)
            if not os.path.exists(local_path):
                print(f"[跳过] 文件不存在: {sql_file}")
                continue

            print(f"\n[执行] {sql_file}")
            remote_path = f"/tmp/{os.path.basename(sql_file)}"
            sftp.put(local_path, remote_path)

            cmd = (
                f"mysql -u{MYSQL_USER} -p{MYSQL_PASSWORD} {MYSQL_DATABASE} < {remote_path} 2>&1 || "
                f"mysql -u{MYSQL_USER} -p{MYSQL_PASSWORD} -e 'CREATE DATABASE IF NOT EXISTS {MYSQL_DATABASE};' && "
                f"mysql -u{MYSQL_USER} -p{MYSQL_PASSWORD} {MYSQL_DATABASE} < {remote_path} 2>&1"
            )
            _, stdout, stderr = ssh.exec_command(cmd, timeout=120)
            out = stdout.read().decode().strip()
            err = stderr.read().decode().strip()

            if out:
                for line in out.split("\n"):
                    if line.strip():
                        print(f"  {line}")
            if err and "WARNING" not in err and "Using a password" not in err:
                for line in err.split("\n"):
                    if line.strip():
                        print(f"  [stderr] {line}")

            ssh.exec_command(f"rm -f {remote_path}")
            print(f"  [完成] {sql_file}")

        print("\n[SUCCESS] 所有 SQL 脚本执行完成")
        return True

    except Exception as e:
        print(f"\n[FAILED] SQL 执行失败: {e}")
        return False
    finally:
        sftp.close()
        ssh.close()


def deploy_java_backend():
    """部署 Java 后端到 /root/webapp/ 并启动 start-with-sms.sh"""
    print("\n" + "=" * 60)
    print("开始部署 Java 后端")
    print("=" * 60)

    print("\n[1/3] 打包 jar...")
    os.chdir(BACKEND_PATH)
    ret = os.system('cmd /c "mvnw.cmd clean package -Dmaven.test.skip=true -q"')
    if ret != 0:
        print("[FAILED] 打包失败")
        return False

    jar_files = list(Path(BACKEND_PATH, "target").glob("*.jar"))
    if not jar_files:
        print("[FAILED] 未找到 jar 文件")
        return False
    jar_path = str(jar_files[0])
    jar_name = os.path.basename(jar_path)

    print(f"\n[2/3] 上传 jar 到 {REMOTE_WEBAPP_DIR}/...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(SERVER_HOST, username=SERVER_USER, password=SERVER_PASSWORD)

    sftp = ssh.open_sftp()
    remote_jar = f"{REMOTE_WEBAPP_DIR}/{jar_name}"
    sftp.put(jar_path, remote_jar)
    sftp.close()
    print(f"  [完成] 上传 -> {remote_jar}")

    print("\n[3/3] 启动后端服务...")
    commands = [
        f"chmod +x {REMOTE_START_SCRIPT}",
        f"cd {REMOTE_WEBAPP_DIR} && bash {REMOTE_START_SCRIPT}",
        "sleep 3",
        "ps aux | grep java | grep -v grep",
    ]
    for cmd in commands:
        _, stdout, stderr = ssh.exec_command(cmd, timeout=60)
        out = stdout.read().decode().strip()
        err = stderr.read().decode().strip()
        if out:
            print(f"  {out}")
        if err:
            print(f"  [stderr] {err}")

    ssh.close()
    print("\n[SUCCESS] Java 后端部署完成")
    return True


def deploy_admin_web():
    """构建并部署 Web 管理后台到 /root/html/"""
    print("\n" + "=" * 60)
    print("开始部署 Web 管理后台")
    print("=" * 60)

    print("\n[1/2] 构建...")
    os.chdir(ADMIN_WEB_PATH)
    ret = os.system("npm run build")
    if ret != 0:
        print("[FAILED] Web 管理后台构建失败")
        return False

    print(f"\n[2/2] 部署到 {REMOTE_HTML_DIR}/...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(SERVER_HOST, username=SERVER_USER, password=SERVER_PASSWORD)
    sftp = ssh.open_sftp()

    dist_dir = os.path.join(ADMIN_WEB_PATH, "dist")
    if not os.path.exists(dist_dir):
        print("[FAILED] 未找到 dist 目录")
        return False

    # 清空旧文件并上传新文件
    ssh.exec_command(f"rm -rf {REMOTE_HTML_DIR}/*")
    for item in os.listdir(dist_dir):
        local_item = os.path.join(dist_dir, item)
        remote_item = f"{REMOTE_HTML_DIR}/{item}"
        if os.path.isdir(local_item):
            put_dir(sftp, local_item, remote_item)
        else:
            sftp.put(local_item, remote_item)

    sftp.close()
    ssh.close()
    print(f"\n[SUCCESS] Web 管理后台部署完成 -> {REMOTE_HTML_DIR}/")
    return True


def put_dir(sftp, local_dir, remote_dir):
    """递归上传目录"""
    try:
        sftp.mkdir(remote_dir)
    except Exception:
        pass
    for item in os.listdir(local_dir):
        local_item = os.path.join(local_dir, item)
        remote_item = f"{remote_dir}/{item}"
        if os.path.isdir(local_item):
            put_dir(sftp, local_item, remote_item)
        else:
            sftp.put(local_item, remote_item)


def main():
    print("快马日结项目部署")
    print(f"服务器: {SERVER_HOST}")
    print(f"后端目录: {REMOTE_WEBAPP_DIR}/")
    print(f"前端目录: {REMOTE_HTML_DIR}/")

    if not execute_sql_via_ssh():
        print("\n[ABORTED] SQL 执行失败")
        sys.exit(1)

    if not deploy_java_backend():
        print("\n[ABORTED] Java 后端部署失败")
        sys.exit(1)

    deploy_admin_web()

    print("\n" + "=" * 60)
    print("部署完成!")
    print("=" * 60)


if __name__ == "__main__":
    main()
