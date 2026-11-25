package com.eventuath.gexrdp_kvm.core.net

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Maps transport/protocol failures to short Chinese messages for the UI. */
fun Throwable.toUserMessage(): String = when (this) {
    is CertificatePinException -> "证书指纹不匹配，服务器可能已更换或连接被劫持"
    is GexException -> when (code) {
        "AUTH_FAILED" -> "密码错误"
        "AUTH_RATE_LIMITED" -> "尝试过于频繁，请稍后再试"
        "TOKEN_INVALID" -> "登录已过期，请重新连接"
        "BAD_REQUEST" -> message ?: "请求参数错误"
        "VM_NOT_RUNNING" -> "虚拟机未运行"
        "VM_NOT_FOUND" -> "虚拟机不存在"
        "NO_DISPLAY" -> "虚拟机没有图形显示设备"
        "SESSION_LIMIT" -> "该虚拟机已有会话"
        "CAPTURE_FAILED" -> "服务端抓帧失败"
        "LIBVIRT_UNAVAILABLE" -> "宿主机 libvirt 不可用"
        "UNSUPPORTED" -> message ?: "服务端不支持该操作"
        else -> message ?: code
    }

    is SocketTimeoutException -> "连接超时，请检查网络"
    is UnknownHostException -> "找不到服务器地址"
    is ConnectException -> "无法连接服务器"
    is IOException -> message ?: "网络错误"
    else -> message ?: javaClass.simpleName
}
