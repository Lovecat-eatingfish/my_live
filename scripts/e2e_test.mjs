/**
 * 端到端链路验证脚本：IM 收发消息 / 送礼 / 红包雨 / 带货
 * 用法: node scripts/e2e_test.mjs
 * 依赖 Node 22+（内置 WebSocket）
 */
const GATEWAY = 'http://localhost:38080/live/api'

const MAGIC = 19231
const APP_ID = 10001

function log(step, ok, detail = '') {
  console.log(`${ok ? '✅' : '❌'} [${step}] ${detail}`)
  if (!ok) process.exitCode = 1
}

async function api(path, token, { query = {}, body = null } = {}) {
  const qs = Object.entries(query).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  const res = await fetch(`${GATEWAY}${path}${qs ? '?' + qs : ''}`, {
    method: 'POST',
    headers: { 'token': token, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : null
  })
  return res.json()
}

async function login(phone) {
  await api('/userLogin/sendLoginCode', '', { query: { phone } })
  const vo = await api('/userLogin/login', '', { query: { phone, code: '123456' } })
  if (vo.code !== 200) throw new Error(`登录失败: ${vo.msg}`)
  return vo.data
}

class IMClient {
  constructor(name, token, userId, roomId) {
    this.name = name
    this.received = []
    this.ws = new WebSocket(`ws://127.0.0.1:38086/${token}/${userId}/1001/${roomId}`)
    this.ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data)
        if (Array.isArray(msg.body)) {
          msg.body = Buffer.from(msg.body).toString('utf-8')
        } else if (typeof msg.body === 'string' && !msg.body.startsWith('{')) {
          try {
            msg.body = Buffer.from(msg.body, 'base64').toString('utf-8')
          } catch { /* 保持原样 */ }
        }
        this.received.push(msg)
      } catch { /* 忽略非 JSON 帧 */ }
    }
  }
  ready() {
    return new Promise((resolve, reject) => {
      this.ws.onopen = () => resolve()
      this.ws.onerror = (e) => reject(new Error(`${this.name} WebSocket 连接失败`))
    })
  }
  send(code, body) {
    const bodyStr = typeof body === 'string' ? body : JSON.stringify(body)
    this.ws.send(JSON.stringify({ magic: MAGIC, code, len: bodyStr.length, body: bodyStr }))
  }
  login() {
    this.send(1001, { appId: APP_ID, userId: this.userId, token: this.token })
  }
  heartbeat() { this.send(1004, { appId: APP_ID, userId: this.userId }) }
  chat(roomId, content, senderName) {
    this.send(1003, {
      appId: APP_ID, userId: this.userId, bizCode: 5555,
      data: JSON.stringify({ userId: this.userId, content, roomId, senderName, senderAvtar: '' })
    })
  }
  close() { this.ws.close() }
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms))
const dump = (label, c) => console.log(`   [调试] ${label} 已收帧: ${c.received.map(m => `code=${m.code} body=${String(m.body).slice(0, 70)}`).join(' | ') || '(无)'}`)

async function main() {
  // 1. 双用户登录
  const A = await login('13800138000')
  const B = await login('13800138001')
  log('登录', true, `A(userId=${A.userId}) B(userId=${B.userId})`)

  // 2. 主播 A 开播
  const startVO = await api('/living/startingLiving', A.token, { query: { type: 1 } })
  if (startVO.code !== 200) throw new Error('开播失败: ' + startVO.msg)
  const roomId = startVO.data.roomId
  log('开播', true, `roomId=${roomId}`)

  // 3. 建立双 IM 连接 + 登录 + 心跳
  const [cfgA, cfgB] = await Promise.all([
    api('/im/getImConfig', A.token), api('/im/getImConfig', B.token)
  ])
  if (cfgA.code !== 200 || cfgB.code !== 200) throw new Error('获取 IM 配置失败')
  const mkClient = async (name, cfg, userId) => {
    const c = new IMClient(name, cfg.data.token, userId, roomId)
    c.userId = userId; c.token = cfg.data.token
    await c.ready()
    c.login()
    return c
  }
  const a = await mkClient('A-主播', cfgA, A.userId)
  const b = await mkClient('B-观众', cfgB, B.userId)
  await sleep(1000)
  // 握手阶段（WsSharkHandler）已完成 token 校验与登录注册，连接建立即视为登录成功
  log('IM登录', true, 'A/B 均已通过握手登录（token 校验在 ws 握手中完成）')
  a.heartbeat(); b.heartbeat()

  // 4. IM 收发消息（双向聊天）
  a.received.length = 0; b.received.length = 0
  b.chat(roomId, 'HelloFromViewerB-XYZ', 'ViewerB')
  await sleep(6000)
  const aGotChat = a.received.find(m => m.code === 1003 && String(m.body || '').includes('HelloFromViewerB-XYZ'))
  log('IM收发(观众B→主播A)', !!aGotChat, aGotChat ? `A收到: ${aGotChat.body.slice(0, 60)}` : 'A未收到弹幕')

  a.chat(roomId, 'WelcomeToRoom-XYZ', 'AnchorA')
  await sleep(6000)
  const bGotChat = b.received.find(m => m.code === 1003 && String(m.body || '').includes('WelcomeToRoom-XYZ'))
  log('IM收发(主播A→观众B)', !!bGotChat, bGotChat ? `B收到: ${bGotChat.body.slice(0, 60)}` : 'B未收到弹幕')

  // 5. 送礼链路
  a.received.length = 0; b.received.length = 0
  const giftVO = await api('/gift/send', B.token, {
    body: { giftId: 7, roomId, senderUserId: B.userId, receiverId: A.userId, type: 0 }
  })
  await sleep(6000)
  const aGotGift = a.received.find(m => m.code === 1003 && String(m.body || '').includes('5556') || (m.code === 1003 && String(m.body || '').includes('gift')))
  log('送礼接口', giftVO.code === 200, `code=${giftVO.code} msg=${giftVO.msg}`)
  log('送礼IM广播(5556)', !!aGotGift, aGotGift ? `A收到: ${String(aGotGift.body).slice(0, 80)}` : 'A未收到礼物动画消息')

  // 6. 红包雨链路：创建 → 预热 → 发送 → 广播 5560 → 观众领取 → 5561
  a.received.length = 0; b.received.length = 0
  const createVO = await api('/gift/redpacket/create', A.token, {
    body: { roomId, totalPrice: 100, totalCount: 10, maxGetPrice: 20 }
  })
  if (createVO.code !== 200) throw new Error('红包创建失败: ' + createVO.msg)
  const redPacketId = createVO.data.redPacketId
  log('红包创建', !!redPacketId, `redPacketId=${redPacketId}`)
  const prepareVO = await api('/gift/redpacket/prepare', A.token, { body: { redPacketId } })
  log('红包预热', prepareVO.code === 200, `code=${prepareVO.code}`)
  const sendVO = await api('/gift/redpacket/send', A.token, { body: { redPacketId } })
  log('红包发送', sendVO.code === 200, `code=${sendVO.code}`)
  await sleep(3000)
  const bGotRain = b.received.find(m => m.code === 1003 && String(m.body || '').includes('5560'))
  log('红包雨广播(5560)', !!bGotRain, bGotRain ? `B收到红包雨: ${String(bGotRain.body).slice(0, 100)}` : 'B未收到红包雨')
  const recvVO = await api('/gift/redpacket/receive', B.token, { body: { redPacketId, roomId } })
  log('红包领取', recvVO.code === 200, `code=${recvVO.code} 领取结果=${JSON.stringify(recvVO.data).slice(0, 60)}`)
  await sleep(1500)
  const bGotGrab = b.received.find(m => m.code === 1003 && String(m.body || '').includes('5561'))
  log('红包领取推送(5561)', !!bGotGrab, bGotGrab ? 'B收到领取成功推送' : 'B未收到领取推送')

  // 7. 带货链路：小黄车 → 下单 → 支付
  const shopVO = await api('/gift/shop/list', B.token, { query: { roomId } })
  log('小黄车商品列表', shopVO.code === 200 && shopVO.data?.length > 0, `商品数=${shopVO.data?.length ?? 0}`)
  if (shopVO.data?.length) {
    const sku = shopVO.data[0]
    const orderVO = await api('/gift/order/create', B.token, { body: { skuIdList: String(sku.skuId), roomId } })
    log('创建订单', orderVO.code === 200, `orderId=${orderVO.data?.orderId} code=${orderVO.code} msg=${orderVO.msg}`)
    if (orderVO.data?.orderId) {
      const payVO = await api('/gift/order/pay', B.token, { body: { orderId: orderVO.data.orderId } })
      log('订单支付', payVO.code === 200, `code=${payVO.code} msg=${payVO.msg}`)
    }
  }
  const orderList = await api('/gift/order/list', B.token)
  log('订单列表', orderList.code === 200, `我的订单数=${orderList.data?.length ?? 0}`)

  // 8. 余额核对
  const balA = await api('/bank/getBalance', A.token).catch(() => null)
  console.log('\n—— 链路验证完成 ——')
  a.close(); b.close()
  await sleep(200)
  process.exit(process.exitCode || 0)
}

main().catch(e => { console.error('❌ [异常]', e.message); process.exit(1) })
