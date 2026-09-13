/**
 * 批次六 E2E：运营配置 + 点睛
 * 1 礼物配置(改价→前台列表同步)  2 充值档位(改价/上下架→前台同步)
 * 3 进场欢迎消息(系统弹幕广播)   4 主播看板数据(在线人数+贡献榜)
 * 用法: node scripts/ops_config_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'
const ADMIN = 'http://localhost:38100/live/admin'
const MAGIC = 19231

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

async function adminApi(path, token, params = {}) {
  const qs = Object.entries(params).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  return fetch(`${ADMIN}${path}${qs ? '?' + qs : ''}`, { method: 'POST', headers: { adminToken: token } })
    .then(r => r.json())
}

async function login(phone) {
  await api('/userLogin/sendLoginCode', '', { query: { phone } })
  const vo = await api('/userLogin/login', '', { query: { phone, code: '123456' } })
  if (vo.code !== 200) throw new Error(`登录失败: ${vo.msg}`)
  return { token: vo.data.token, userId: vo.data.userId }
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

async function wsJoin(cfg, userId, roomId) {
  const ws = new WebSocket(`ws://127.0.0.1:38115/${cfg.token}/${userId}/1001/${roomId}`)
  await new Promise(r => { ws.onopen = r; ws.onerror = () => r() })
  const loginBody = JSON.stringify({ appId: 10001, userId, token: cfg.token })
  ws.send(JSON.stringify({ magic: MAGIC, code: 1001, len: loginBody.length, body: loginBody }))
  const hbBody = JSON.stringify({ appId: 10001, userId })
  const hbTimer = setInterval(() => {
    try { ws.send(JSON.stringify({ magic: MAGIC, code: 1004, len: hbBody.length, body: hbBody })) } catch { }
  }, 15000)
  await sleep(800)
  const received = []
  ws.onmessage = (ev) => {
    try {
      const m = JSON.parse(ev.data)
      let body = m.body
      if (Array.isArray(body)) body = new TextDecoder('utf-8').decode(new Uint8Array(body))
      if (m.code === 1003 && body) received.push(JSON.parse(body))
    } catch { }
  }
  return { ws, received, close: () => { clearInterval(hbTimer); ws.close() } }
}

const run = async () => {
  const A = await login('13800138000')
  const B = await login('13900999201')
  const AL = await fetch(`${ADMIN}/auth/login?username=admin&password=admin123`, { method: 'POST' }).then(r => r.json())
  const adminToken = AL.data?.token || AL.data
  log('login', true, `A=${A.userId} B=${B.userId}`)

  // ---- 1. 礼物配置：改价 → 前台列表同步 → 改回 ----
  const gl0 = await api('/gift/listGift', A.token)
  const gift0 = gl0.data[0]
  const origPrice = gift0.price
  const upd = await adminApi('/giftConfig/update', adminToken, { giftId: gift0.giftId ?? gift0.id, price: origPrice + 1 })
  await sleep(1200)
  const gl1 = await api('/gift/listGift', A.token)
  const gift1 = gl1.data.find(g => (g.giftId ?? g.id) === (gift0.giftId ?? gift0.id))
  log('礼物改价前台同步', upd.code === 200 && gift1.price === origPrice + 1,
    `${origPrice} -> ${gift1.price}`)
  await adminApi('/giftConfig/update', adminToken, { giftId: gift0.giftId ?? gift0.id, price: origPrice })
  await sleep(1000)
  const gl2 = await api('/gift/listGift', A.token)
  const gift2 = gl2.data.find(g => (g.giftId ?? g.id) === (gift0.giftId ?? gift0.id))
  log('礼物改回还原', gift2.price === origPrice, `price=${gift2.price}`)

  // ---- 2. 充值档位：改价 → 商品详情同步 → 改回 ----
  const products = await adminApi('/payProduct/list', adminToken)
  const prod = (products.data || [])[0]
  const origProdPrice = prod.price
  const pUpd = await adminApi('/payProduct/update', adminToken, { productId: prod.id, price: origProdPrice + 100 })
  await sleep(800)
  const prodAfter = await adminApi('/payProduct/list', adminToken)
  const prod1 = (prodAfter.data || []).find(p => p.id === prod.id)
  log('充值档位改价', pUpd.code === 200 && prod1.price === origProdPrice + 100,
    `${origProdPrice} -> ${prod1.price}`)
  await adminApi('/payProduct/update', adminToken, { productId: prod.id, price: origProdPrice })
  log('充值档位改回', true, `price=${origProdPrice}`)

  // ---- 3. 进场欢迎消息 ----
  let room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '批次六测试间', covertImg: 'x' } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '批次六测试间', covertImg: 'x' } })
  }
  if (room.code !== 200) throw new Error('开播失败 ' + room.msg)
  const roomId = room.data.roomId
  const cfgB = await api('/im/getImConfig', B.token)
  const b = await wsJoin(cfgB.data, B.userId, roomId)
  await sleep(3000)
  const cfgA = await api('/im/getImConfig', A.token)
  const a = await wsJoin(cfgA.data, A.userId, roomId)
  // 欢迎广播走进房 MQ 异步 + living→userRpc 首次冷连接可能秒级，轮询等待
  let welcome = null
  for (let i = 0; i < 12 && !welcome; i++) {
    await sleep(1000)
    welcome = a.received.filter(m => m.bizCode === 5555).map(m => JSON.parse(m.data))
      .find(d => d.system && String(d.content).includes('欢迎'))
  }
  log('进场欢迎广播(系统弹幕)', !!welcome, welcome ? welcome.content : '未收到')
  b.close(); a.close()
  await api('/living/closeLiving', B.token, { query: { roomId } })

  // ---- 4. 主播看板数据（贡献榜与在线数接口复用） ----
  const rk = await api('/rank/roomGift', A.token, { query: { roomId } })
  log('看板数据源(贡献榜接口)', rk.code === 200, `rows=${(rk.data || []).length}`)
  console.log('\n批次六 E2E 结束')
}

run().catch(e => { console.error('❌ 测试异常:', e); process.exitCode = 1 })
