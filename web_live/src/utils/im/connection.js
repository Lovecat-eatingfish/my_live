/**
 * IM WebSocket 连接管理器
 * 对接后端 Netty IM 服务器，协议格式：
 *   发送/接收均为 JSON: { magic: 19231, code: int, len: int, body: string }
 *
 * Code 说明：
 *   1001 - 登录
 *   1004 - 心跳
 *   1003 - 业务消息（聊天/礼物等）
 *
 * 业务 Code（body.bizCode）：
 *   5555 - 直播间聊天
 *   5556 - 送礼成功
 *   5557 - 送礼失败
 *   5558 - PK礼物
 *   5559 - PK用户上线
 */

const MAGIC = 19231
const HEARTBEAT_INTERVAL = 30000
// 与后端 AppIdEnum.QIYU_LIVE_BIZ 一致
const APP_ID = 10001

/**
 * 服务端下发的 ImMsg.body 存在三种形态：
 *   1. 字节数组（fastjson2 序列化 byte[] 的结果，如 [123,34,...]）
 *   2. Base64 字符串
 *   3. 直接的 JSON 字符串
 */
function decodeBody(body) {
  if (Array.isArray(body)) {
    try {
      return new TextDecoder('utf-8').decode(new Uint8Array(body))
    } catch (e) {
      return body
    }
  }
  if (typeof body !== 'string') return body
  try {
    JSON.parse(body)
    return body
  } catch (e) {
    try {
      const bin = atob(body)
      const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0))
      return new TextDecoder('utf-8').decode(bytes)
    } catch (e2) {
      return body
    }
  }
}

export class IMConnection {
  constructor({ wsUrl, userId, appId, onMessage, onOpen, onClose, onError }) {
    this.wsUrl = wsUrl
    this.userId = userId
    this.appId = appId || APP_ID
    this.onMessage = onMessage
    this.onOpen = onOpen
    this.onClose = onClose
    this.onError = onError
    this._ws = null
    this._heartbeatTimer = null
    this._connected = false
  }

  /** 建立 WebSocket 连接并发送登录消息 */
  connect() {
    return new Promise((resolve, reject) => {
      try {
        this._ws = new WebSocket(this.wsUrl)

        this._ws.onopen = () => {
          this._connected = true
          // 发送登录消息
          const loginBody = {
            appId: this.appId,
            userId: this.userId,
            token: this._extractToken()
          }
          this._send(1001, loginBody)
          this._startHeartbeat()
          this.onOpen?.()
          resolve()
        }

        this._ws.onmessage = (event) => {
          try {
            const msg = JSON.parse(event.data)
            msg.body = decodeBody(msg.body)
            // 登录响应（后端回包 body 为 ImMsgBody JSON，data="true" 表示成功）
            if (msg.code === 1001 && msg.body) {
              const body = JSON.parse(msg.body)
              if (body.data === 'true') {
                console.log('[IM] 登录成功')
              }
            }
            // 业务消息：回发 ack（code=1005），服务端收到后取消延迟重推，否则 5 秒后会重复推送
            if (msg.code === 1003 && typeof msg.body === 'string') {
              this._ackBusinessMsg(msg.body)
            }
            this.onMessage?.(msg)
          } catch (e) {
            console.error('[IM] 消息解析失败', e)
          }
        }

        this._ws.onerror = (err) => {
          console.error('[IM] WebSocket 错误', err)
          this.onError?.(err)
          // 不在此处 reject：onclose 紧跟 onerror 触发，_connected 已在 onclose 中置 false
          // connect() 的 Promise 失败由构造函数抛错或 onopen 异常处理
        }

        this._ws.onclose = () => {
          this._connected = false
          this._stopHeartbeat()
          this.onClose?.()
        }
      } catch (e) {
        reject(e)
      }
    })
  }

  /** 从 wsUrl 中提取 token */
  _extractToken() {
    // URL 格式: ws://host:8809/{token}/{userId}/1001/{param}
    const parts = this.wsUrl.split('/')
    return parts[3] || ''
  }

  /** 发送消息（JSON 序列化后计算 len） */
  _send(code, body) {
    if (!this._ws || this._ws.readyState !== WebSocket.OPEN) return
    const bodyStr = typeof body === 'string' ? body : JSON.stringify(body)
    const msg = {
      magic: MAGIC,
      code,
      len: bodyStr.length,
      body: bodyStr
    }
    this._ws.send(JSON.stringify(msg))
  }

  /**
   * 发送聊天消息
   * data 结构需对齐后端 MessageDTO（roomId/userId/content/senderName/senderAvtar）
   */
  sendChat(content, roomId, extra = {}) {
    const body = {
      appId: this.appId,
      userId: this.userId,
      bizCode: 5555,
      data: JSON.stringify({
        userId: this.userId,
        content,
        roomId,
        senderName: extra.senderName || '',
        senderAvtar: extra.senderAvtar || ''
      })
    }
    this._send(1003, body)
  }

  /**
   * 发送私信（bizCode=5568 上行）
   * 服务端落库后回显 5569 给双方，UI 只以服务端回显为准追加
   */
  sendDm(toUserId, content) {
    const body = {
      appId: this.appId,
      userId: this.userId,
      bizCode: 5568,
      data: JSON.stringify({
        fromUid: this.userId,
        toUid: toUserId,
        content
      })
    }
    this._send(1003, body)
  }

  /** 发送心跳 */
  _sendHeartbeat() {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._send(1004, { appId: this.appId, userId: this.userId })
    }
  }

  /**
   * 收到业务消息(code=1003)后回发 ack(code=1005)。
   * 服务端 AckImMsgHandler 收到后删除 Redis 重发记录，
   * 5 秒后的延迟检查发现无记录即跳过重推（见 docs/im-duplicate-message-analysis.md）。
   */
  _ackBusinessMsg(bodyStr) {
    let body
    try {
      body = JSON.parse(bodyStr)
    } catch (e) {
      return
    }
    if (!body || !body.msgId) return
    this._send(1005, {
      appId: body.appId ?? this.appId,
      userId: body.userId ?? this.userId,
      msgId: body.msgId,
      data: ''
    })
  }

  /** 启动心跳定时器 */
  _startHeartbeat() {
    this._stopHeartbeat()
    this._heartbeatTimer = setInterval(() => {
      this._sendHeartbeat()
    }, HEARTBEAT_INTERVAL)
  }

  /** 停止心跳定时器 */
  _stopHeartbeat() {
    if (this._heartbeatTimer) {
      clearInterval(this._heartbeatTimer)
      this._heartbeatTimer = null
    }
  }

  /** 断开连接 */
  disconnect() {
    this._stopHeartbeat()
    if (this._ws) {
      this._ws.close()
      this._ws = null
    }
    this._connected = false
  }

  get isConnected() {
    return this._connected
  }
}

export default IMConnection
