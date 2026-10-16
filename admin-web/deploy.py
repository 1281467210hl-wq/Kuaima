# -*- coding: utf-8 -*-
"""
admin-web 项目部署脚本
使用 Python paramiko 通过 SSH 将 dist 目录上传到远程服务器 /root/html
"""

import os
import posixpath
import paramiko

# ========== 服务器配置 ==========
HOST = '8.148.144.146'
PORT = 22
USERNAME = 'root'
PASSWORD = 'Abc223344'

# ========== 路径配置 ==========
LOCAL_DIST = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'dist')
REMOTE_DIR = '/root/html'


def create_ssh_client():
    """创建 SSH 客户端并连接"""
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f'🔌 正在连接 {HOST}:{PORT} ...')
    client.connect(
        hostname=HOST,
        port=PORT,
        username=USERNAME,
        password=PASSWORD,
        timeout=30,
        banner_timeout=60,
        auth_timeout=60,
        look_for_keys=False,
        allow_agent=False,
    )
    print('✅ SSH 连接成功\n')
    return client


def exec_cmd(client, cmd):
    """执行远程命令并返回 (stdout, stderr, exit_code)"""
    stdin, stdout, stderr = client.exec_command(cmd)
    return stdout.read().decode('utf-8', errors='replace'), \
           stderr.read().decode('utf-8', errors='replace'), \
           stdout.channel.recv_exit_status()


def ensure_remote_dir(sftp, remote_dir):
    """确保远程目录存在，递归创建"""
    dirs_to_create = []
    current = remote_dir
    while current != '/' and current != '':
        try:
            sftp.stat(current)
            break
        except FileNotFoundError:
            dirs_to_create.append(current)
            current = posixpath.dirname(current)
    for d in reversed(dirs_to_create):
        print(f'   📁 创建远程目录: {d}')
        sftp.mkdir(d)


def clear_remote_dir(client, remote_dir):
    """清空远程目录内容"""
    stdout, _, _ = exec_cmd(client, f'ls -A "{remote_dir}" 2>/dev/null')
    for item in stdout.strip().split('\n'):
        item = item.strip()
        if not item:
            continue
        remote_path = posixpath.join(remote_dir, item)
        exec_cmd(client, f'rm -rf "{remote_path}"')


def upload_dir(sftp, local_dir, remote_dir):
    """递归上传本地目录到远程目录"""
    for item in os.listdir(local_dir):
        local_path = os.path.join(local_dir, item)
        remote_path = posixpath.join(remote_dir, item)

        if os.path.isdir(local_path):
            try:
                sftp.stat(remote_path)
            except FileNotFoundError:
                sftp.mkdir(remote_path)
                print(f'   📁 {remote_path}')
            upload_dir(sftp, local_path, remote_path)
        else:
            sftp.put(local_path, remote_path)
            file_size = os.path.getsize(local_path)
            size_str = f'{file_size / 1024:.1f} KB' if file_size < 1024 * 1024 else f'{file_size / 1024 / 1024:.1f} MB'
            print(f'   ⬆️  {item} ({size_str})')


def deploy():
    if not os.path.isdir(LOCAL_DIST):
        print(f'❌ 本地 dist 目录不存在: {LOCAL_DIST}')
        print('   请先运行: npm run build')
        return False

    client = None
    sftp = None

    try:
        # 1. 建立 SSH 连接
        client = create_ssh_client()
        sftp = client.open_sftp()

        # 2. 确保远程目录存在
        print(f'📁 确保远程目录存在: {REMOTE_DIR}')
        ensure_remote_dir(sftp, REMOTE_DIR)
        print('   ✅ 远程目录就绪\n')

        # 3. 清空旧文件
        print('🗑️  清空远程目录旧文件 ...')
        clear_remote_dir(client, REMOTE_DIR)
        print('   ✅ 清理完成\n')

        # 4. 上传 dist
        print(f'⬆️  上传本地 dist -> 远程 {REMOTE_DIR}')
        upload_dir(sftp, LOCAL_DIST, REMOTE_DIR)
        print('   ✅ 上传完成\n')

        # 5. 列出远程目录确认
        entries = sftp.listdir_attr(REMOTE_DIR)
        print(f'📋 远程目录 {REMOTE_DIR} 内容 ({len(entries)} 项):')
        for e in entries:
            icon = '📁' if e.st_mode & 0o40000 else '📄'
            print(f'   {icon} {e.filename}')

        # 6. 重载 nginx + 验证
        print('\n🔍 服务器检查 ...')
        stdout, _, _ = exec_cmd(client, 'systemctl reload nginx 2>/dev/null || nginx -s reload 2>/dev/null; echo OK')
        print(f'   nginx 重载: {stdout.strip()}')

        stdout, _, _ = exec_cmd(client, 'curl -s -o /dev/null -w "%{http_code}" http://localhost/')
        print(f'   HTTP 状态码: {stdout.strip()}')

        print(f'\n🎉 部署成功！访问地址: http://{HOST}/')
        return True

    except paramiko.AuthenticationException:
        print('❌ SSH 认证失败，请检查用户名和密码')
    except paramiko.SSHException as e:
        print(f'❌ SSH 连接错误: {e}')
    except Exception as e:
        print(f'❌ 部署失败: {e}')
    finally:
        if sftp:
            try: sftp.close()
            except: pass
        if client:
            try: client.close()
            except: pass
    return False


if __name__ == '__main__':
    success = deploy()
    exit(0 if success else 1)
