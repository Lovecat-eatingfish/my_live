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

export class IMConnection {
  constructor({ wsUrl, userId, appId, onMessage, onOpen, onClose, onError }) {
    this.wsUrl = wsUrl
    this.userId = userId
    this.appId = appId || 20001
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
            // 登录响应
            if (msg.code === 1001 && msg.body) {
              const body = JSON.parse(msg.body)
              if (body.status === 1) {
                console.log('[IM] 登录成功')
              }
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

  /** 发送聊天消息 */
  sendChat(content, roomId) {
    const body = {
      appId: this.appId,
      userId: this.userId,
      bizCode: 5555,
      data: JSON.stringify({ userId: this.userId, content, roomId })
    }
    this._send(1003, body)
  }

  /** 发送心跳 */
  _sendHeartbeat() {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._send(1004, { appId: this.appId, userId: this.userId })
    }
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
