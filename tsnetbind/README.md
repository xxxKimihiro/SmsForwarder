# tsnetbind

KoSync 内置 Tailscale（userspace / tsnet）：不创建 TUN，不申请 VpnService。

本机提供 `127.0.0.1:<socks>` SOCKS5，Peer HTTP 走这条代理；同时在 tailnet 上监听 HttpServer 端口并转到本机。

用独立 `libtsnetbind.so` + JNI，**不用 gomobile**，避免和已有 `frpclib.aar` 的 `libgojni.so` / `go.Seq` 冲突。

## 编译 .so

需要 Go 1.22+、Android NDK。

```bash
export ANDROID_NDK_HOME=/opt/android-sdk/ndk/<version>
./tsnetbind/build-so.sh
```

产物：`app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libtsnetbind.so`（每个 ABI 约十余 MB）。

## Auth key

和多数内嵌 tsnet 的应用一样：从 https://login.tailscale.com/admin/settings/keys 生成个人 Auth key（建议 reusable + 设过期）。
首次入网后节点状态写在 App 私有目录 `files/tsnet/`，之后可不填 key。

关「内置 Tailscale 通道」后，双机同步走手机当前网络（系统 VPN / 路由都行）。
